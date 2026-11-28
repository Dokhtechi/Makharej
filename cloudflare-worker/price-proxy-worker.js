/**
 * Cloudflare Worker: price-proxy (https://price-proxy.axha2-1975.workers.dev)
 * Supports: technolife, torob (plus optional digikala, basalam, and aggregated "all").
 *
 * Endpoint:
 *   GET /?source=<technolife|torob|digikala|basalam|all>&q=<query>
 *
 * Unified JSON Response:
 *   {
 *     "source": "technolife",
 *     "query": "...",
 *     "results": [
 *       {
 *         "title": "...",
 *         "price": 12500000,
 *         "image": "https://...",
 *         "url": "https://...",
 *         "source": "technolife",
 *         "shopName": "تکنولایف",
 *         "availability": "موجود",
 *         "specs": ["برند: سامسونگ", "رم: ۸ گیگابایت", "حافظه: ۲۵۶ گیگابایت"]
 *       }
 *     ],
 *     "errors": [{ "source": "...", "message": "..." }]
 *   }
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Accept",
  "Content-Type": "application/json; charset=utf-8",
};

const BROWSER_HEADERS = {
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  Accept: "application/json, text/html, */*;q=0.8",
  "Accept-Language": "fa-IR,fa;q=0.9,en-US;q=0.8",
};

const VALID_SOURCES = ["technolife", "torob", "digikala", "basalam"];

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const query = (url.searchParams.get("q") || "").trim();
    const rawSource = (url.searchParams.get("source") || "all").trim().toLowerCase();

    if (!query) {
      return jsonResponse({ error: "پارامتر جستجو (q) الزامی است.", results: [], errors: [] }, 400);
    }

    const requestedSources =
      rawSource === "all"
        ? ["technolife", "torob"]
        : rawSource
            .split(",")
            .map((s) => s.trim())
            .filter((s) => VALID_SOURCES.includes(s));

    if (requestedSources.length === 0) {
      return jsonResponse(
        {
          error: `منبع «${rawSource}» پشتیبانی نمی‌شود. منابع مجاز: ${VALID_SOURCES.join(", ")} یا all`,
          results: [],
          errors: [],
        },
        400
      );
    }

    const settled = await Promise.allSettled(
      requestedSources.map((src) => fetchBySource(src, query))
    );

    const results = [];
    const errors = [];

    settled.forEach((outcome, index) => {
      const src = requestedSources[index];
      if (outcome.status === "fulfilled") {
        results.push(...outcome.value);
      } else {
        errors.push({
          source: src,
          message: outcome.reason?.message || `خطا در دریافت اطلاعات از ${src}`,
        });
      }
    });

    results.sort((a, b) => (a.price || Number.MAX_SAFE_INTEGER) - (b.price || Number.MAX_SAFE_INTEGER));

    return jsonResponse({
      source: rawSource,
      query,
      results,
      errors,
    });
  },
};

async function fetchBySource(source, query) {
  switch (source) {
    case "technolife":
      return fetchTechnolife(query);
    case "torob":
      return fetchTorob(query);
    case "digikala":
      return fetchDigikala(query);
    case "basalam":
      return fetchBasalam(query);
    default:
      return [];
  }
}

/* ==================== 1. TECHNOLIFE ==================== */
async function fetchTechnolife(query) {
  // Primary: Technolife public GraphQL / search API
  const gqlBody = JSON.stringify({
    operationName: "get_menu_search",
    variables: {
      query,
      limit: 24,
      offset: 0,
    },
    query:
      "query get_menu_search($query: String!, $limit: Int, $offset: Int) { get_menu_search(query: $query, limit: $limit, offset: $offset) { products { results { _id name code image price discounted_price available deadline } } } }",
  });

  const gqlRes = await fetchWithTimeout("https://www.technolife.ir/shop_plp", {
    method: "POST",
    headers: {
      ...BROWSER_HEADERS,
      "Content-Type": "application/json",
      Accept: "application/json",
      Origin: "https://www.technolife.ir",
      Referer: "https://www.technolife.ir/",
    },
    body: gqlBody,
  }).catch(() => null);

  if (gqlRes && gqlRes.ok) {
    const json = await gqlRes.json().catch(() => null);
    const items = json?.data?.get_menu_search?.products?.results || [];
    if (Array.isArray(items) && items.length > 0) {
      return items
        .map((p) => {
          const title = cleanText(p.name || p.title || "");
          const price = toNumber(p.discounted_price || p.price);
          if (!title || price <= 0) return null;

          const code = p.code || p._id || "";
          const productUrl = code
            ? `https://www.technolife.ir/product-${code}/`
            : `https://www.technolife.ir/product/list/search?keywords=${encodeURIComponent(query)}`;

          return {
            title,
            price,
            image: normalizeUrl(p.image || "", "https://www.technolife.ir"),
            url: productUrl,
            source: "technolife",
            shopName: "تکنولایف",
            availability: p.available === false ? "ناموجود" : "موجود",
            specs: dedupeSpecs(extractTitleSpecs(title)),
          };
        })
        .filter(Boolean);
    }
  }

  // Fallback: Technolife search HTML (__NEXT_DATA__ or JSON-LD)
  const searchUrl = `https://www.technolife.ir/product/list/search?keywords=${encodeURIComponent(query)}`;
  const htmlRes = await fetchWithTimeout(searchUrl, {
    headers: {
      ...BROWSER_HEADERS,
      Referer: "https://www.technolife.ir/",
    },
  });
  if (!htmlRes.ok) {
    throw new Error(`تکنولایف خطای HTTP ${htmlRes.status} برگرداند.`);
  }
  const html = await htmlRes.text();

  const nextDataMatch = html.match(/<script[^>]+id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i);
  if (nextDataMatch) {
    try {
      const nextJson = JSON.parse(nextDataMatch[1]);
      const found = collectTechnolifeProducts(nextJson);
      if (found.length > 0) return found.slice(0, 24);
    } catch (_) {}
  }

  return extractProductsFromJsonLd(html, "technolife", "https://www.technolife.ir", "تکنولایف");
}

function collectTechnolifeProducts(node, out = []) {
  if (!node || out.length >= 24) return out;
  if (Array.isArray(node)) {
    for (const item of node) collectTechnolifeProducts(item, out);
    return out;
  }
  if (typeof node === "object") {
    const title = cleanText(node.name || node.title || "");
    const rawPrice = toNumber(node.discounted_price || node.price || node.selling_price);
    if (title.length >= 4 && rawPrice >= 10000 && (node.code || node.image || node.slug)) {
      const code = node.code || node._id || "";
      out.push({
        title,
        price: rawPrice,
        image: normalizeUrl(String(node.image || ""), "https://www.technolife.ir"),
        url: code
          ? `https://www.technolife.ir/product-${code}/`
          : "https://www.technolife.ir",
        source: "technolife",
        shopName: "تکنولایف",
        availability: node.available === false ? "ناموجود" : "موجود",
        specs: dedupeSpecs(extractTitleSpecs(title)),
      });
    }
    for (const value of Object.values(node)) {
      if (value && typeof value === "object") collectTechnolifeProducts(value, out);
    }
  }
  return out;
}

/* ==================== 2. TOROB ==================== */
async function fetchTorob(query) {
  const endpoint = `https://api.torob.com/v4/base-product/search/?q=${encodeURIComponent(
    query
  )}&page=0&size=24&source=next_desktop`;

  const res = await fetchWithTimeout(endpoint, {
    headers: {
      ...BROWSER_HEADERS,
      Accept: "application/json",
      Origin: "https://torob.com",
      Referer: "https://torob.com/",
    },
  });

  if (!res.ok) {
    throw new Error(`ترب خطای HTTP ${res.status} برگرداند.`);
  }

  const data = await res.json();
  const list = Array.isArray(data?.results) ? data.results : [];

  return list
    .map((item) => {
      const title = cleanText(item.name1 || item.title || item.name2 || "");
      const price = toNumber(item.price);
      if (!title || price <= 0) return null;

      const specs = [];
      if (item.name2 && cleanText(item.name2) !== title) {
        specs.push(`مدل: ${cleanText(item.name2)}`);
      }
      if (item.shop_text) specs.push(`فروشندگان: ${cleanText(item.shop_text)}`);
      if (item.price_text) specs.push(`بازه قیمت: ${cleanText(item.price_text)}`);
      specs.push(...extractTitleSpecs(title));

      const rawUrl = item.web_client_absolute_url || item.more_info_url || "";
      const productUrl = rawUrl.startsWith("http")
        ? rawUrl
        : rawUrl.startsWith("/")
        ? `https://torob.com${rawUrl}`
        : `https://torob.com/search/?query=${encodeURIComponent(query)}`;

      return {
        title,
        price,
        image: item.image_url || "",
        url: productUrl,
        source: "torob",
        shopName: cleanText(item.shop_text || ""),
        availability: item.stock_status === "out_of_stock" ? "ناموجود" : "موجود",
        specs: dedupeSpecs(specs),
      };
    })
    .filter(Boolean);
}

/* ==================== 3. DIGIKALA (OPTIONAL PROXY) ==================== */
async function fetchDigikala(query) {
  const endpoint = `https://api.digikala.com/v1/search/?q=${encodeURIComponent(query)}&page=1`;
  const res = await fetchWithTimeout(endpoint, {
    headers: {
      ...BROWSER_HEADERS,
      Accept: "application/json",
      Origin: "https://www.digikala.com",
      Referer: "https://www.digikala.com/",
    },
  });
  if (!res.ok) throw new Error(`دیجی‌کالا خطای HTTP ${res.status} برگرداند.`);
  const json = await res.json();
  const list = Array.isArray(json?.data?.products) ? json.data.products : [];
  return list
    .map((p) => {
      const title = cleanText(p.title_fa || p.title || "");
      const rial = toNumber(p.default_variant?.price?.selling_price);
      const price = rial > 0 ? Math.max(1, Math.round(rial / 10)) : 0;
      if (!title || price <= 0) return null;
      return {
        title,
        price,
        image: p.images?.main?.url?.[0] || "",
        url: p.id ? `https://www.digikala.com/product/dkp-${p.id}/` : "https://www.digikala.com",
        source: "digikala",
        shopName: cleanText(p.default_variant?.seller?.title || "دیجی‌کالا"),
        availability: p.status === "out_of_stock" ? "ناموجود" : "موجود",
        specs: dedupeSpecs(extractTitleSpecs(title)),
      };
    })
    .filter(Boolean);
}

/* ==================== 4. BASALAM (OPTIONAL PROXY) ==================== */
async function fetchBasalam(query) {
  const endpoint = `https://search.basalam.com/ai-engine/api/v2.0/product/search?q=${encodeURIComponent(
    query
  )}&from=0&size=24`;
  const res = await fetchWithTimeout(endpoint, {
    headers: {
      ...BROWSER_HEADERS,
      Accept: "application/json",
      Origin: "https://basalam.com",
      Referer: "https://basalam.com/",
    },
  });
  if (!res.ok) throw new Error(`باسلام خطای HTTP ${res.status} برگرداند.`);
  const json = await res.json();
  const list = Array.isArray(json?.products)
    ? json.products
    : Array.isArray(json?.data?.products)
    ? json.data.products
    : [];
  return list
    .map((p) => {
      const title = cleanText(p.name || p.title || "");
      const rawPrice = toNumber(p.price?.discounted_amount || p.price?.amount || p.price || p.primaryPrice);
      const price = rawPrice > 0 ? Math.max(1, Math.round(rawPrice / 10)) : 0;
      if (!title || price <= 0) return null;
      const vendorId = p.vendor?.identifier || "";
      return {
        title,
        price,
        image: p.photo?.MEDIUM || p.photo?.SMALL || p.photo?.url || "",
        url: vendorId && p.id ? `https://basalam.com/${vendorId}/product/${p.id}` : `https://basalam.com/p/${p.id || ""}`,
        source: "basalam",
        shopName: cleanText(p.vendor?.name || ""),
        availability: p.stock === 0 ? "ناموجود" : "موجود",
        specs: dedupeSpecs(extractTitleSpecs(title)),
      };
    })
    .filter(Boolean);
}

/* ==================== HELPERS ==================== */
function extractProductsFromJsonLd(html, source, baseUrl, defaultShop) {
  const results = [];
  const ldRegex = /<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = ldRegex.exec(html)) !== null) {
    try {
      const parsed = JSON.parse(m[1].trim());
      const nodes = collectProductNodes(parsed);
      for (const node of nodes) {
        const title = cleanText(node.name || node.title || "");
        const offer = Array.isArray(node.offers) ? node.offers[0] : node.offers;
        const rawPrice = toNumber(offer?.price || offer?.lowPrice || node.price);
        const currency = String(offer?.priceCurrency || "").toUpperCase();
        const price = currency === "IRR" ? Math.round(rawPrice / 10) : rawPrice;
        if (!title || price <= 0) continue;
        const img = Array.isArray(node.image) ? node.image[0] : node.image?.url || node.image || "";
        results.push({
          title,
          price,
          image: normalizeUrl(String(img), baseUrl),
          url: normalizeUrl(String(node.url || offer?.url || baseUrl), baseUrl),
          source,
          shopName: cleanText(offer?.seller?.name || defaultShop || ""),
          availability: String(offer?.availability || "").includes("OutOfStock") ? "ناموجود" : "موجود",
          specs: dedupeSpecs(extractTitleSpecs(title)),
        });
      }
    } catch (_) {}
  }
  return results.slice(0, 24);
}

function collectProductNodes(node, out = []) {
  if (!node) return out;
  if (Array.isArray(node)) {
    node.forEach((n) => collectProductNodes(n, out));
    return out;
  }
  if (typeof node === "object") {
    const type = String(node["@type"] || "");
    if (type.includes("Product")) out.push(node);
    if (node.item) collectProductNodes(node.item, out);
    if (node.itemListElement) collectProductNodes(node.itemListElement, out);
    if (node["@graph"]) collectProductNodes(node["@graph"], out);
  }
  return out;
}

function extractTitleSpecs(title) {
  const normalized = toEnglishDigits(title);
  const specs = [];

  const combo = normalized.match(/\b(4|6|8|12|16|24|32)\s*(?:gb|گیگ)?\s*\/\s*(64|128|256|512|1|2)\s*(?:gb|tb|گیگ|ترابایت)?\b/i);
  if (combo) {
    specs.push(`رم: ${combo[1]} گیگابایت`);
    specs.push(`حافظه: ${combo[2]} ${combo[2] === "1" || combo[2] === "2" ? "ترابایت" : "گیگابایت"}`);
  }
  const cpu = normalized.match(/(Core\s*i[3579]|Ryzen\s*[3579]|Snapdragon\s*\w+|Dimensity\s*\w+|Helio\s*\w+|M[1-4](?:\s*Pro|\s*Max)?)/i);
  if (cpu) specs.push(`پردازنده: ${cpu[1].trim()}`);

  const screen = normalized.match(/(\d{1,2}(?:\.\d)?)\s*(?:اینچ|inch|")/i);
  if (screen) specs.push(`صفحه‌نمایش: ${screen[1]} اینچ`);

  return specs;
}

function dedupeSpecs(specs) {
  return [...new Set(specs.map((s) => cleanText(s)).filter((s) => s.length >= 2))].slice(0, 5);
}

function fetchWithTimeout(url, options = {}, timeoutMs = 12000) {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...options, signal: controller.signal }).finally(() => clearTimeout(id));
}

function toEnglishDigits(str) {
  return String(str || "")
    .replace(/[۰-۹]/g, (d) => "۰۱۲۳۴۵۶۷۸۹".indexOf(d))
    .replace(/[٠-٩]/g, (d) => "٠١٢٣٤٥٦٧٨٩".indexOf(d));
}

function toNumber(val) {
  if (typeof val === "number" && Number.isFinite(val)) return Math.round(val);
  const digits = toEnglishDigits(val).replace(/[^\d]/g, "");
  return digits ? parseInt(digits, 10) : 0;
}

function cleanText(str) {
  return String(str || "")
    .replace(/&nbsp;/g, " ")
    .replace(/&zwnj;/g, "\u200C")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

function normalizeUrl(raw, baseUrl) {
  if (!raw) return "";
  if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
  if (raw.startsWith("//")) return `https:${raw}`;
  if (raw.startsWith("/")) return `${baseUrl.replace(/\/$/, "")}${raw}`;
  return `${baseUrl.replace(/\/$/, "")}/${raw}`;
}

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: CORS_HEADERS });
}
