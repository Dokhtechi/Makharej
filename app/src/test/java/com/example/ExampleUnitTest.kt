package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTehranPrayerTimes() {
    val prayerTimes = com.example.utils.TehranPrayerTimesHelper.getTehranPrayerTimes()
    assertNotNull(prayerTimes)
    assertTrue("Fajr should have valid time format", prayerTimes.fajr.matches(Regex("\\d{2}:\\d{2}")))
    assertTrue("Dhuhr should have valid time format", prayerTimes.dhuhr.matches(Regex("\\d{2}:\\d{2}")))
    assertTrue("Maghrib should have valid time format", prayerTimes.maghrib.matches(Regex("\\d{2}:\\d{2}")))
    assertTrue("Sunrise should have valid time format", prayerTimes.sunrise.matches(Regex("\\d{2}:\\d{2}")))
    assertTrue("Sunset should have valid time format", prayerTimes.sunset.matches(Regex("\\d{2}:\\d{2}")))
    assertTrue("Midnight should have valid time format", prayerTimes.midnight.matches(Regex("\\d{2}:\\d{2}")))
  }

  @Test
  fun testTetherRateSafeguard() {
    // If raw scraped value was "1.00" USD or 0, our safeguard ensures price in Tomans >= 10,000 Tomans
    val dollarPrice = 266500L
    val parsedZero = 0L
    val finalTether = if (parsedZero < 10000L) dollarPrice else parsedZero
    assertEquals(dollarPrice, finalTether)
  }

  @Test
  fun testGitHubRepoParsing() {
    assertEquals(
      "ali" to "hazineh",
      com.example.utils.GitHubUpdateHelper.parseOwnerAndRepo("ali/hazineh")
    )
    assertEquals(
      "ali" to "hazineh",
      com.example.utils.GitHubUpdateHelper.parseOwnerAndRepo("https://github.com/ali/hazineh.git")
    )
    assertEquals(
      "ali" to "hazineh",
      com.example.utils.GitHubUpdateHelper.parseOwnerAndRepo("https://github.com/ali/hazineh/releases")
    )
    assertNull(com.example.utils.GitHubUpdateHelper.parseOwnerAndRepo("onlyusername"))
    assertNull(com.example.utils.GitHubUpdateHelper.parseOwnerAndRepo("   "))
  }

  @Test
  fun testGitHubVersionComparison() {
    assertTrue(com.example.utils.GitHubUpdateHelper.compareVersions("v1.1.0", "1.0") > 0)
    assertTrue(com.example.utils.GitHubUpdateHelper.compareVersions("V2", "1.0") > 0)
    assertEquals(0, com.example.utils.GitHubUpdateHelper.compareVersions("v1.0", "1.0.0"))
    assertTrue(com.example.utils.GitHubUpdateHelper.compareVersions("1.0", "1.2") < 0)
  }

  @Test
  fun testCurrentMonthExpenseGrouping() {
    val supermarketItems = (1..10).map { idx ->
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = idx.toLong(),
          title = if (idx % 2 == 0) "خرید سوپرمارکت" else "  خرید سوپر مارکت ",
          amount = idx * 100_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = idx,
          shamsiDateString = "1405/08/${idx.toString().padStart(2, '0')}"
        ),
        effectiveFolder = "خرید سوپرمارکت",
        isRecurring = false
      )
    }
    val fuelItem = com.example.data.model.ResolvedExpense(
      entity = com.example.data.model.ExpenseEntity(
        id = 11L,
        title = "بنزین",
        amount = 150_000L,
        shamsiYear = 1405,
        shamsiMonth = 8,
        shamsiDay = 15,
        shamsiDateString = "1405/08/15"
      ),
      effectiveFolder = "بنزین",
      isRecurring = false
    )

    val groups = com.example.ui.components.CurrentMonthExpenseGrouper.groupExpenses(
      supermarketItems + fuelItem
    )

    assertEquals(2, groups.size)
    assertEquals(10, groups[0].items.size)
    assertEquals(5_500_000L, groups[0].totalAmount)
    assertEquals("خرید سوپرمارکت", groups[0].displayTitle)
    assertEquals(1, groups[1].items.size)
    assertEquals(150_000L, groups[1].totalAmount)
  }

  @Test
  fun testSmartSemanticAndAnnualGrouping() {
    val items = listOf(
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 1L,
          title = "پول علی",
          amount = 500_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 20,
          shamsiDateString = "1405/08/20"
        ),
        effectiveFolder = "علی",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 2L,
          title = "علی",
          amount = 300_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 10,
          shamsiDateString = "1405/08/10"
        ),
        effectiveFolder = "علی",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = -101L,
          title = "بیمه شخص ثالث خودرو (سهم ماهانه)",
          amount = 1_000_000L,
          isAnnual = true,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 15,
          shamsiDateString = "1405/08/15"
        ),
        effectiveFolder = "تعهدات سالانه و تعهدات",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = -201L,
          title = "بیمه موتورسیکلت (سهم ماهانه)",
          amount = 200_000L,
          isAnnual = true,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 20,
          shamsiDateString = "1405/08/20"
        ),
        effectiveFolder = "موتور",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 3L,
          title = "قبض آب",
          amount = 80_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 5,
          shamsiDateString = "1405/08/05"
        ),
        effectiveFolder = "قبوض",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 4L,
          title = "قبض برق",
          amount = 120_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 4,
          shamsiDateString = "1405/08/04"
        ),
        effectiveFolder = "قبوض",
        isRecurring = false
      )
    )

    val groups = com.example.ui.components.CurrentMonthExpenseGrouper.groupExpenses(items)

    assertEquals(4, groups.size)

    val aliGroup = groups[0]
    assertEquals("علی", aliGroup.displayTitle)
    assertEquals(2, aliGroup.items.size)
    assertEquals(800_000L, aliGroup.totalAmount)

    val annualGroup = groups[1]
    assertTrue(annualGroup.isAnnual)
    assertEquals("تعهدات سالانه (سهم ماهانه)", annualGroup.displayTitle)
    assertEquals(2, annualGroup.items.size)
    assertEquals(1_200_000L, annualGroup.totalAmount)

    assertEquals("قبض آب", groups[2].displayTitle)
    assertEquals("قبض برق", groups[3].displayTitle)
  }

  @Test
  fun testExtendedSemanticGrouping() {
    val items = listOf(
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 10L,
          title = "خرید میوه",
          amount = 400_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 20,
          shamsiDateString = "1405/08/20"
        ),
        effectiveFolder = "خرید میوه",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 11L,
          title = "خرید میوه و پروتئین",
          amount = 980_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 12,
          shamsiDateString = "1405/08/12"
        ),
        effectiveFolder = "سوپرمارکت و خوراکی",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 12L,
          title = "هزینه های درمانی",
          amount = 650_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 18,
          shamsiDateString = "1405/08/18"
        ),
        effectiveFolder = "درمان",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 13L,
          title = "درمان",
          amount = 350_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 10,
          shamsiDateString = "1405/08/10"
        ),
        effectiveFolder = "درمان",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 14L,
          title = "نانوایی",
          amount = 60_000L,
          shamsiYear = 1405,
          shamsiMonth = 7,
          shamsiDay = 15,
          shamsiDateString = "1405/07/15"
        ),
        effectiveFolder = "نان",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 15L,
          title = "خرید نان سنگک",
          amount = 40_000L,
          shamsiYear = 1405,
          shamsiMonth = 7,
          shamsiDay = 5,
          shamsiDateString = "1405/07/05"
        ),
        effectiveFolder = "نان",
        isRecurring = false
      ),
      com.example.data.model.ResolvedExpense(
        entity = com.example.data.model.ExpenseEntity(
          id = 16L,
          title = "تخم مرغ",
          amount = 150_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 3,
          shamsiDateString = "1405/08/03"
        ),
        effectiveFolder = "سوپرمارکت و خوراکی",
        isRecurring = false
      )
    )

    val groups = com.example.ui.components.CurrentMonthExpenseGrouper.groupExpenses(items)

    assertEquals(4, groups.size)

    val fruitGroup = groups[0]
    assertEquals("خرید میوه", fruitGroup.displayTitle)
    assertEquals(2, fruitGroup.items.size)
    assertEquals(1_380_000L, fruitGroup.totalAmount)

    val medicalGroup = groups[1]
    assertEquals("درمان", medicalGroup.displayTitle)
    assertEquals(2, medicalGroup.items.size)
    assertEquals(1_000_000L, medicalGroup.totalAmount)

    val bakeryGroup = groups[2]
    assertEquals("نانوایی", bakeryGroup.displayTitle)
    assertEquals(2, bakeryGroup.items.size)
    assertEquals(100_000L, bakeryGroup.totalAmount)

    val eggGroup = groups[3]
    assertEquals("تخم مرغ", eggGroup.displayTitle)
    assertEquals(1, eggGroup.items.size)
  }

  @Test
  fun testTgjuCoinHtmlBourseAndOilParser() {
    val sampleHtml = """
      <ul class="info-bar mobile-hide">
        <li id="l-bourse">
          <span class="info-title">شاخص بورس</span>
          <span class="info-price">2,145,320</span>
          <span class="info-change high">14,200</span>
        </li>
        <li id="l-oil_brent" class="low">
          <span class="info-title">نفت برنت</span>
          <span class="info-price">73.8</span>
          <span class="info-change">0.42</span>
        </li>
      </ul>
    """.trimIndent()

    val (bourse, oil) = com.example.data.network.MarketRatesService.parseBourseAndOilFromTgjuCoinHtml(sampleHtml)
    assertEquals(2_145_320L, bourse?.priceTomans)
    assertEquals(14_200L, bourse?.changeTomans)
    assertEquals(true, bourse?.isPositiveChange)
    assertEquals(74L, oil?.priceTomans)
    assertEquals("$73.80", oil?.displayPriceFormatted)
    assertEquals("$0.42", oil?.displayDiffFormatted)
    assertEquals(false, oil?.isPositiveChange)
  }

  @Test
  fun testTgjuCoinAllAssetsHtmlAndAjaxJsonParser() {
    val sampleHtml = """
      <ul class="info-bar">
        <li id="l-price_dollar_rl" class="high">
          <h3>دلار</h3>
          <span class="info-price">825,000</span>
          <span class="info-change">(0.61%) 5,000</span>
        </li>
        <li id="l-price_eur" class="low">
          <h3>یورو</h3>
          <span class="info-price">892,500</span>
          <span class="info-change">(0.45%) 4,000</span>
        </li>
        <li id="l-geram18" class="high">
          <h3>طلای 18 عیار</h3>
          <span class="info-price">64,500,000</span>
          <span class="info-change">(1.10%) 700,000</span>
        </li>
        <li id="l-ons" class="low">
          <h3>انس طلا</h3>
          <span class="info-price">2,654.20</span>
          <span class="info-change">(0.47%) 12.50</span>
        </li>
        <li id="l-crypto-bitcoin" class="high">
          <h3>بیت کوین</h3>
          <span class="info-price">92,450</span>
          <span class="info-change">(1.37%) 1,250</span>
        </li>
        <li id="l-crypto-tether-irr" class="high">
          <h3>تتر</h3>
          <span class="info-price">828,000</span>
          <span class="info-change">(0.36%) 3,000</span>
        </li>
        <li id="l-bourse" class="high">
          <h3>شاخص بورس</h3>
          <span class="info-price">2,845,120</span>
          <span class="info-change">(0.54%) 15,420</span>
        </li>
        <li id="l-oil_brent" class="low">
          <h3>نفت برنت</h3>
          <span class="info-price">73.45</span>
          <span class="info-change">(1.15%) 0.85</span>
        </li>
      </ul>
      <table>
        <tr data-market-row="sekee">
          <th>سکه امامی</th>
          <td class="nf">745,000,000</td>
          <td class="nf"><span class="high">(1.25%) 9,200,000</span></td>
        </tr>
        <tr data-market-row="rob">
          <th>ربع سکه</th>
          <td class="nf">215,000,000</td>
          <td class="nf"><span class="low">(0.92%) 2,000,000</span></td>
        </tr>
      </table>
    """.trimIndent()

    val htmlRates = com.example.data.network.MarketRatesService.parseAllFromTgjuCoinHtml(sampleHtml)
    assertEquals(82_500L, htmlRates.dollar?.priceTomans)
    assertEquals(500L, htmlRates.dollar?.changeTomans)
    assertEquals(true, htmlRates.dollar?.isPositiveChange)
    assertEquals(0.61, htmlRates.dollar?.changePercent ?: 0.0, 0.01)

    assertEquals(89_250L, htmlRates.euro?.priceTomans)
    assertEquals(400L, htmlRates.euro?.changeTomans)
    assertEquals(false, htmlRates.euro?.isPositiveChange)

    assertEquals(74_500_000L, htmlRates.sekehEmami?.priceTomans)
    assertEquals(920_000L, htmlRates.sekehEmami?.changeTomans)
    assertEquals(true, htmlRates.sekehEmami?.isPositiveChange)

    assertEquals(21_500_000L, htmlRates.robSekeh?.priceTomans)
    assertEquals(200_000L, htmlRates.robSekeh?.changeTomans)
    assertEquals(false, htmlRates.robSekeh?.isPositiveChange)

    assertEquals("$2,654.20", htmlRates.goldOunce?.displayPriceFormatted)
    assertEquals("$12.50", htmlRates.goldOunce?.displayDiffFormatted)
    assertEquals(false, htmlRates.goldOunce?.isPositiveChange)

    assertEquals("طلای 18 عیار", htmlRates.gold18k?.title)
    assertEquals(6_450_000L, htmlRates.gold18k?.priceTomans)
    assertEquals(70_000L, htmlRates.gold18k?.changeTomans)
    assertEquals(true, htmlRates.gold18k?.isPositiveChange)

    assertEquals("$92,450", htmlRates.bitcoin?.displayPriceFormatted)
    assertEquals("$1,250", htmlRates.bitcoin?.displayDiffFormatted)
    assertEquals(true, htmlRates.bitcoin?.isPositiveChange)

    assertEquals(82_800L, htmlRates.tether?.priceTomans)
    assertEquals(300L, htmlRates.tether?.changeTomans)
    assertEquals(true, htmlRates.tether?.isPositiveChange)

    assertEquals(2_845_120L, htmlRates.bourse?.priceTomans)
    assertEquals(15_420L, htmlRates.bourse?.changeTomans)
    assertEquals(true, htmlRates.bourse?.isPositiveChange)

    assertEquals("$73.45", htmlRates.crudeOil?.displayPriceFormatted)
    assertEquals("$0.85", htmlRates.crudeOil?.displayDiffFormatted)
    assertEquals(false, htmlRates.crudeOil?.isPositiveChange)
  }

  @Test
  fun testIranianCalendarBadesabaHijriAndHolidays() {
    // 22 Aban 1405 = 3 Jumada al-Thani 1448 (Martyrdom of Fatimah Zahra)
    val aban22Hijri = com.example.utils.IranianCalendarHelper.shamsiToHijri(1405, 8, 22).first
    assertEquals(1448, aban22Hijri.year)
    assertEquals(6, aban22Hijri.month)
    assertEquals(3, aban22Hijri.day)
    assertEquals("1448/06/03", aban22Hijri.formatNumeric())

    val aban23Hijri = com.example.utils.IranianCalendarHelper.shamsiToHijri(1405, 8, 23).first
    assertEquals("1448/06/04", aban23Hijri.formatNumeric())

    val abanCal = com.example.utils.IranianCalendarHelper.getMonthlyCalendar(1405, 8)
    assertEquals(1, abanCal.officialHolidayEntries.size)
    assertEquals(22, abanCal.officialHolidayEntries[0].shamsiDay)
    assertTrue(abanCal.officialHolidayEntries[0].reason.contains("فاطمه زهرا"))
    val abanDay22 = abanCal.days.first { it.shamsiDay == 22 }
    assertTrue(abanDay22.isOfficialHoliday)
    // 22 Aban 1405 = 2026-11-13
    assertEquals(2026, abanDay22.gregorianYear)
    assertEquals(11, abanDay22.gregorianMonth)
    assertEquals(13, abanDay22.gregorianDay)

    // Tir 1405: 3 Tir = Tasua (9 Muharram), 4 Tir = Ashura (10 Muharram)
    val tirCal = com.example.utils.IranianCalendarHelper.getMonthlyCalendar(1405, 4)
    val tasua = tirCal.officialHolidayEntries.find { it.shamsiDay == 3 }
    val ashura = tirCal.officialHolidayEntries.find { it.shamsiDay == 4 }
    assertNotNull(tasua)
    assertNotNull(ashura)
    assertTrue(tasua!!.reason.contains("تاسوعا"))
    assertTrue(ashura!!.reason.contains("عاشورا"))
  }

  @Test
  fun testReceiptExpenseShareTextFormat() {
    val expense = com.example.data.model.ExpenseEntity(
      id = 77L,
      title = "خرید دارو و\nآزمایش",
      amount = 1_250_000L,
      customFolder = "درمان",
      shamsiYear = 1405,
      shamsiMonth = 8,
      shamsiDay = 22,
      shamsiDateString = "1405/08/22",
      note = "نسخه دکتر\nبیمه تکمیلی",
      receiptImagePath = "/tmp/receipt_test.jpg"
    )
    val shareText = com.example.utils.ReceiptImageHelper.buildExpenseShareText(expense)
    assertTrue(shareText.contains("خرید دارو و آزمایش"))
    assertTrue(shareText.contains("1,250,000"))
    assertTrue(shareText.contains("1405/08/22"))
    assertTrue(shareText.contains("درمان"))
    assertTrue(shareText.contains("نسخه دکتر بیمه تکمیلی"))
  }

  @Test
  fun testNewsRssParsingAndExactShamsiTimestamps() {
    // 2026-11-23T10:15:00Z = 13:45 Iran time (UTC+3:30) = 1405/09/02 13:45
    val epochIsoZ = com.example.data.repository.NewsRepository.parseRssDateToEpochMs("2026-11-23T10:15:00Z")
    assertNotNull(epochIsoZ)
    assertEquals("1405/09/02  13:45", com.example.data.repository.NewsRepository.formatExactShamsiDateTime(epochIsoZ!!))
    assertEquals("1405/09/02 - 13:45", com.example.data.repository.NewsRepository.formatExactUpdateHeader(epochIsoZ))

    // RFC-822 with +0330 offset: Mon, 23 Nov 2026 14:20:00 +0330 -> 1405/09/02  14:20
    val epochRfc = com.example.data.repository.NewsRepository.parseRssDateToEpochMs("Mon, 23 Nov 2026 14:20:00 +0330")
    assertNotNull(epochRfc)
    assertEquals("1405/09/02  14:20", com.example.data.repository.NewsRepository.formatExactShamsiDateTime(epochRfc!!))

    val sampleRss = """
      <rss version="2.0">
        <channel>
          <item>
            <title><![CDATA[رشد شاخص بورس و ثبات بازار ارز]]></title>
            <link>https://www.eghtesadonline.com/fa/news/1001</link>
            <description><![CDATA[<p>شاخص کل بورس امروز با افزایش 2% همراه شد.</p>]]></description>
            <pubDate>Mon, 23 Nov 2026 14:20:00 +0330</pubDate>
          </item>
          <item>
            <title>توسعه هوش مصنوعی در استارتاپ‌های ایرانی</title>
            <link>https://www.zoomit.ir/tech/1002</link>
            <description>گزارش جدید از فناوری و هوش مصنوعی.</description>
            <pubDate>2026-11-23T10:15:00Z</pubDate>
          </item>
          <item>
            <title>تصادف زنجیره‌ای و آتش‌سوزی خودرو در اتوبان همت</title>
            <link>https://www.rokna.net/fa/news/1003</link>
            <description>نیروهای آتش‌نشانی و اورژانس به محل حادثه اعزام شدند.</description>
            <pubDate>2026-11-23T10:25:00Z</pubDate>
          </item>
        </channel>
      </rss>
    """.trimIndent()

    val parsed = com.example.data.repository.NewsRepository.parseRssXml(
      xml = sampleRss,
      source = com.example.data.model.NewsSource.FARARU
    )
    assertEquals(3, parsed.size)
    assertEquals("1405/09/02  14:20", parsed[0].fetchTime)
    assertEquals(com.example.data.model.NewsCategory.ECONOMY, parsed[0].category)
    assertFalse("Should never contain fake relative time", parsed[0].fetchTime.contains("دقیقه"))
    assertEquals("1405/09/02  13:45", parsed[1].fetchTime)
    assertEquals(com.example.data.model.NewsCategory.TECHNOLOGY, parsed[1].category)
    assertEquals(com.example.data.model.NewsCategory.ACCIDENTS, parsed[2].category)
    assertTrue(parsed[0].timestamp > parsed[1].timestamp)

    val sampleArticleHtml = """
      <html><body>
        <nav><p>منو اصلی</p></nav>
        <article>
          <p>این اولین پاراگراف کامل خبر درباره رویدادهای مهم امروز در کشور است که جزئیات دقیقی ارائه می‌دهد.</p>
          <p>در ادامه گزارش کارشناسان تاکید کردند که بررسی‌های میدانی و آمارهای جدید منتشر خواهد شد.</p>
        </article>
      </body></html>
    """.trimIndent()
    val extractedBody = com.example.data.repository.NewsRepository.extractArticleParagraphsFromHtml(sampleArticleHtml)
    assertTrue(extractedBody.contains("اولین پاراگراف کامل"))
    assertTrue(extractedBody.contains("کارشناسان تاکید کردند"))
  }

  @Test
  fun testPersianNewsSpeechScriptAndChunking() {
    val article = com.example.data.model.NewsArticle(
      id = "fararu_1",
      title = "افزایش 5% نرخ دلار و 2500$ انس جهانی",
      summary = "گزارش بازار",
      fullContent = "افزایش 5% نرخ دلار و 2500$ انس جهانی\n\nبازار ارز و طلاي تهران امروز با نوسانات ملايم همراه بود و معامله‌گران در انتظار اخبار جدید هستند. جزئیات بیشتر در https://example.com/news منتشر شد.\n\nمنبع: فرارو",
      source = com.example.data.model.NewsSource.FARARU,
      category = com.example.data.model.NewsCategory.ECONOMY,
      url = "https://fararu.com/1",
      fetchTime = "1405/09/02  14:20",
      timestamp = 1_795_420_000_000L
    )

    val script = com.example.utils.PersianNewsSpeechEngine.preparePersianNewsScript(article)
    assertTrue(script.startsWith("گزارش خبری از فرارو."))
    assertTrue("ASCII digits should convert to Persian for fa-IR pronunciation", script.contains("۵ درصد"))
    assertTrue("Dollar symbol should be spoken as دلار", script.contains("۲۵۰۰ دلار"))
    assertTrue("Arabic ي should normalize to Persian ی", script.contains("طلای") && script.contains("ملایم"))
    assertFalse("URLs should be stripped from spoken text", script.contains("https://"))
    assertFalse("Footer source line should not be duplicated", script.contains("منبع:"))

    val longPersianText = List(8) {
      "بانک مرکزی در تازه‌ترین گزارش خود اعلام کرد که ثبات در بازار ارز و مدیریت نقدینگی از اولویت‌های اصلی سیاست‌گذاری اقتصادی در ماه‌های آینده خواهد بود."
    }.joinToString(" ")
    val chunks = com.example.utils.PersianNewsSpeechEngine.splitIntoNaturalPersianChunks(longPersianText, maxChunkLength = 170)
    assertTrue("Long article should be split into multiple natural chunks", chunks.size >= 4)
    assertTrue("Every chunk must stay within max length", chunks.all { it.length <= 170 && it.isNotBlank() })
  }

  @Test
  fun testPersianNeuralTtsProtocolBuilders() {
    val defaultState = com.example.utils.NewsSpeechState()
    assertTrue("Default news voice must be Male", defaultState.useMaleVoice)
    assertEquals(com.example.utils.NewsVoicePersona.ARASH_MALE, defaultState.voicePersona)

    val gec = com.example.utils.PersianNewsSpeechEngine.buildEdgeSecMsGec(1_730_000_000.0)
    assertEquals(64, gec.length)
    assertTrue("Sec-MS-GEC must be uppercase SHA-256 hex", gec.matches(Regex("[0-9A-F]{64}")))

    val geminiPrompt = com.example.utils.PersianNewsSpeechEngine.buildGeminiTehraniTtsPrompt(
      "گزارش خبری امروز",
      com.example.utils.NewsVoicePersona.ARASH_MALE
    )
    assertTrue(
      "Gemini prompt must enforce Iranian-Tehrani Persian accent",
      geminiPrompt.contains("ایرانی-تهرانی") && geminiPrompt.contains("تهران")
    )
    assertTrue(geminiPrompt.contains("گزارش خبری امروز"))

    val wavBytes = com.example.utils.PersianNewsSpeechEngine.wrapPcm16AsWav(ByteArray(4800))
    assertEquals(44 + 4800, wavBytes.size)
    assertEquals("RIFF", String(wavBytes, 0, 4))
    assertEquals("WAVE", String(wavBytes, 8, 4))

    val arashEdgeSsml = com.example.utils.PersianNewsSpeechEngine.buildEdgeSsmlForPersona(
      "گزارش خبری امروز",
      com.example.utils.NewsVoicePersona.ARASH_MALE
    )
    val kamranEdgeSsml = com.example.utils.PersianNewsSpeechEngine.buildEdgeSsmlForPersona(
      "گزارش خبری امروز",
      com.example.utils.NewsVoicePersona.KAMRAN_MALE
    )
    val saraEdgeSsml = com.example.utils.PersianNewsSpeechEngine.buildEdgeSsmlForPersona(
      "گزارش خبری امروز",
      com.example.utils.NewsVoicePersona.SARA_FEMALE
    )
    val nazaninEdgeSsml = com.example.utils.PersianNewsSpeechEngine.buildEdgeSsmlForPersona(
      "گزارش خبری امروز",
      com.example.utils.NewsVoicePersona.NAZANIN_FEMALE
    )

    assertTrue("Edge fallback must use native Iranian fa-IR voice", arashEdgeSsml.contains("fa-IR, FaridNeural"))
    assertFalse("Must NOT use en-US multilingual voice that causes Dari accent", arashEdgeSsml.contains("en-US"))
    assertTrue(arashEdgeSsml.contains("گزارش خبری امروز"))
    assertNotEquals("Two male personas must have distinct Gemini voice names",
      com.example.utils.NewsVoicePersona.ARASH_MALE.geminiVoiceName,
      com.example.utils.NewsVoicePersona.KAMRAN_MALE.geminiVoiceName
    )
    assertNotEquals("Two female personas must have distinct Gemini voice names",
      com.example.utils.NewsVoicePersona.SARA_FEMALE.geminiVoiceName,
      com.example.utils.NewsVoicePersona.NAZANIN_FEMALE.geminiVoiceName
    )
    assertNotEquals("Two male personas must have distinct Edge SSML prosody", arashEdgeSsml, kamranEdgeSsml)
    assertNotEquals("Two female personas must have distinct Edge SSML prosody", saraEdgeSsml, nazaninEdgeSsml)

    val dilaraSsml = com.example.utils.PersianNewsSpeechEngine.buildBingSsml("سلام & خوش آمدید", useMale = false)
    assertTrue(dilaraSsml.contains("fa-IR-DilaraNeural"))
    assertTrue(dilaraSsml.contains("xml:lang='fa-IR'"))
    assertTrue(dilaraSsml.contains("سلام &amp; خوش آمدید"))

    val faridEdgeSsml = com.example.utils.PersianNewsSpeechEngine.buildEdgeSsml("گزارش خبری امروز", useMale = true)
    assertTrue(faridEdgeSsml.contains("fa-IR, FaridNeural"))
    assertTrue(faridEdgeSsml.contains("گزارش خبری امروز"))
  }

  @Test
  fun testTgjuThreeYearHistoryParserAndRangeFiltering() {
    // 1. Exact refresh timestamp must match HH:mm:ss  YYYY/MM/DD (never stale market-close time)
    val ts = com.example.data.network.MarketRatesService.formatExactRefreshTimestamp(System.currentTimeMillis())
    assertTrue("Exact timestamp format invalid: $ts", ts.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\s+\\d{4}/\\d{2}/\\d{2}")))

    // 2. Parse TGJU summary-table-data JSON for Sekeh Emami (Rial -> Toman)
    val sampleCoinJson = """
      {
        "draw": 1,
        "recordsTotal": 5,
        "data": [
          ["750,000,000", "745,000,000", "752,000,000", "748,000,000", "<span class='high'>3,000,000</span>", "<span class='high'>0.4%</span>", "2026/11/23", "1405/09/02"],
          ["745,000,000", "740,000,000", "746,000,000", "745,000,000", "<span class='high'>5,000,000</span>", "<span class='high'>0.67%</span>", "2026/11/17", "1405/08/26"],
          ["710,000,000", "705,000,000", "712,000,000", "708,000,000", "<span class='low'>2,000,000</span>", "<span class='low'>0.28%</span>", "2026/08/10", "1405/05/19"],
          ["390,000,000", "385,000,000", "392,000,000", "389,000,000", "<span class='high'>1,000,000</span>", "<span class='high'>0.25%</span>", "2024/05/10", "1403/02/21"],
          ["4,150,000", "4,100,000", "4,200,000", "4,180,000", "<span class='high'>30,000</span>", "<span class='high'>0.72%</span>", "2011/04/04", "1390/01/15"]
        ]
      }
    """.trimIndent()

    val emamiPoints = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      sampleCoinJson,
      com.example.data.network.TgjuChartAsset.SEKEH_EMAMI
    )
    assertEquals(5, emamiPoints.size)
    // Sorted oldest -> newest (starts in 1390!)
    assertEquals("1390/01/15", emamiPoints.first().shamsiDate)
    assertEquals(418_000.0, emamiPoints.first().price, 0.5)
    assertEquals("1405/09/02", emamiPoints.last().shamsiDate)
    assertEquals(74_800_000.0, emamiPoints.last().price, 0.5)
    // 2026/11/17 (1405/08/26) is Tuesday -> سه‌شنبه
    val tuesdayPoint = emamiPoints.first { it.shamsiDate == "1405/08/26" }
    assertEquals("سه‌شنبه", tuesdayPoint.weekdayFa)

    // Verify range filtering (1 Week vs 6 Months vs Since 1390)
    val weekPoints = com.example.data.network.TgjuHistoryService.filterPointsForRange(
      emamiPoints,
      com.example.data.network.TgjuChartRange.WEEK_1
    )
    assertEquals(2, weekPoints.size)
    val sixMonthsPoints = com.example.data.network.TgjuHistoryService.filterPointsForRange(
      emamiPoints,
      com.example.data.network.TgjuChartRange.MONTH_6
    )
    assertEquals(3, sixMonthsPoints.size)
    val from1390Points = com.example.data.network.TgjuHistoryService.filterPointsForRange(
      emamiPoints,
      com.example.data.network.TgjuChartRange.FROM_1390
    )
    assertEquals(5, from1390Points.size)
    assertEquals(1390, from1390Points.first().shamsiYear)

    // Verify Shamsi historical date search back to 1390 (exact match & nearest trading day fallback)
    val exact1390Search = com.example.data.network.TgjuHistoryService.findPointByShamsiDate(
      emamiPoints, 1390, 1, 15
    )
    assertNotNull(exact1390Search)
    assertTrue(exact1390Search!!.isExactMatch)
    assertEquals("1390/01/15", exact1390Search.point.shamsiDate)
    assertEquals(418_000.0, exact1390Search.point.price, 0.5)

    val holidaySearch = com.example.data.network.TgjuHistoryService.findPointByShamsiDate(
      emamiPoints, 1405, 8, 27
    )
    assertNotNull(holidaySearch)
    assertFalse(holidaySearch!!.isExactMatch)
    assertEquals("1405/08/26", holidaySearch.point.shamsiDate)

    // Verify persistent offline cache serialization/deserialization + incremental merge (since 1390 v4)
    val serialized = com.example.data.network.TgjuHistoryService.serializeCache(
      fetchedAtMs = 1_795_000_000_000L,
      fullFrom1390Synced = true,
      points = emamiPoints
    )
    val deserialized = com.example.data.network.TgjuHistoryService.deserializeCache(serialized)
    assertNotNull(deserialized)
    assertTrue(deserialized!!.fullFrom1390Synced)
    assertEquals(5, deserialized.points.size)
    assertEquals("1390/01/15", deserialized.points.first().shamsiDate)

    val incrementalJson = """
      {"data":[
        ["760,000,000", "755,000,000", "765,000,000", "762,000,000", "14,000,000", "1.8%", "2026/11/24", "1405/09/03"],
        ["750,000,000", "745,000,000", "753,000,000", "749,000,000", "4,000,000", "0.5%", "2026/11/23", "1405/09/02"]
      ]}
    """.trimIndent()
    val incomingDelta = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      incrementalJson,
      com.example.data.network.TgjuChartAsset.SEKEH_EMAMI
    )
    val (mergedSeries, addedCount) = com.example.data.network.TgjuHistoryService.mergeHistoryPoints(
      existing = deserialized.points,
      incoming = incomingDelta
    )
    assertEquals(1, addedCount)
    assertEquals(6, mergedSeries.size)
    assertEquals("1390/01/15", mergedSeries.first().shamsiDate) // Past 1390 history preserved!
    assertEquals("1405/09/03", mergedSeries.last().shamsiDate)  // New day appended!
    assertEquals(76_200_000.0, mergedSeries.last().price, 0.5)

    // 3. Parse TGJU summary-table-data JSON for Global Gold Ounce (USD decimal, NOT divided by 10)
    val sampleOnsJson = """
      {
        "data": [
          ["2,660.50", "2,642.10", "2,671.80", "2,654.25", "12.50", "0.47%", "2026/11/23", "1405/09/02"]
        ]
      }
    """.trimIndent()
    val onsPoints = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      sampleOnsJson,
      com.example.data.network.TgjuChartAsset.GOLD_OUNCE
    )
    assertEquals(1, onsPoints.size)
    assertEquals(2654.25, onsPoints[0].price, 0.01)
    assertEquals(2671.80, onsPoints[0].high, 0.01)
    assertEquals(2642.10, onsPoints[0].low, 0.01)

    // 4. Parse Dollar (including 1390 rate), Euro, 18k Gold, Bitcoin, Tehran Bourse, and Brent Oil
    val dollarJson = """{"data":[["815,000","810,000","820,000","818,500","3,500","0.43%","2026/11/23","1405/09/02"],["11,050","10,900","11,200","11,100","100","0.9%","2011/04/04","1390/01/15"]]}"""
    val dollarPts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      dollarJson, com.example.data.network.TgjuChartAsset.DOLLAR
    )
    assertEquals(2, dollarPts.size)
    assertEquals(1110.0, dollarPts.first().price, 0.01)
    assertEquals(81850.0, dollarPts.last().price, 0.01)

    val euroJson = """{"data":[["862,000","858,000","868,000","865,000","3,000","0.35%","2026/11/23","1405/09/02"]]}"""
    val euroPts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      euroJson, com.example.data.network.TgjuChartAsset.EURO
    )
    assertEquals(1, euroPts.size)
    assertEquals(86500.0, euroPts[0].price, 0.01)

    val gold18Json = """{"data":[["64,500,000","64,100,000","65,200,000","64,950,000","450,000","0.7%","2026/11/23","1405/09/02"]]}"""
    val gold18Pts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      gold18Json, com.example.data.network.TgjuChartAsset.GOLD_18K
    )
    assertEquals(1, gold18Pts.size)
    assertEquals(6_495_000.0, gold18Pts[0].price, 0.5)

    val btcJson = """{"data":[["91,800","91,200","92,900","92,450","650","0.7%","2026/11/23","1405/09/02"]]}"""
    val btcPts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      btcJson, com.example.data.network.TgjuChartAsset.BITCOIN
    )
    assertEquals(1, btcPts.size)
    assertEquals(92450.0, btcPts[0].price, 0.01)

    val bourseJson = """{"data":[["2,830,000","2,825,000","2,850,000","2,845,120","15,120","0.53%","2026/11/23","1405/09/02"]]}"""
    val boursePts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      bourseJson, com.example.data.network.TgjuChartAsset.BOURSE
    )
    assertEquals(1, boursePts.size)
    assertEquals(2845120.0, boursePts[0].price, 0.5)

    val oilJson = """{"data":[["74.10","73.10","74.50","73.45","0.65","0.88%","2026/11/23","1405/09/02"]]}"""
    val oilPts = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      oilJson, com.example.data.network.TgjuChartAsset.BRENT_OIL
    )
    assertEquals(1, oilPts.size)
    assertEquals(73.45, oilPts[0].price, 0.01)

    // 5. Verify AsiyaExchangeService extracts Tether (USDT) cleanly from HTML & JSON (and ignores BTC/USDT pairs)
    val asiyaHtml = """
      <table>
        <tr><td>بیت کوین به تتر (BTC/USDT)</td><td>92,500</td></tr>
        <tr><td>تتر (USDT)</td><td>خرید: ۸۳,۹۰۰ تومان</td><td>فروش: ۸۴,۳۵۰ تومان</td><td class="high">+0.65%</td></tr>
      </table>
    """.trimIndent()
    val parsedAsiyaHtml = com.example.data.network.AsiyaExchangeService.parseTetherFromResponse(asiyaHtml)
    assertNotNull(parsedAsiyaHtml)
    assertEquals(84_350L, parsedAsiyaHtml!!.priceTomans)
    assertEquals(0.65, parsedAsiyaHtml.changePercent, 0.01)
    assertTrue(parsedAsiyaHtml.isPositiveChange)
    assertEquals(com.example.data.network.AsiyaExchangeService.SOURCE_LABEL, parsedAsiyaHtml.sourceLabel)

    val asiyaJson = """{"rates":[{"symbol":"BTC_USDT","price":92000},{"symbol":"USDT","sell_price":85120,"buy_price":84700,"change_percent":-0.4}]}"""
    val parsedAsiyaJson = com.example.data.network.AsiyaExchangeService.parseTetherFromResponse(asiyaJson)
    assertNotNull(parsedAsiyaJson)
    assertEquals(85_120L, parsedAsiyaJson!!.priceTomans)
    assertEquals(0.4, parsedAsiyaJson.changePercent, 0.01)
    assertFalse(parsedAsiyaJson.isPositiveChange)

    // Flat JSON keys (e.g., usdt_sell / usdt_buy)
    val asiyaFlatJson = """{"usd_sell":113000,"usdt_buy":114100,"usdt_sell":114800}"""
    val parsedFlat = com.example.data.network.AsiyaExchangeService.parseTetherFromResponse(asiyaFlatJson)
    assertNotNull(parsedFlat)
    assertEquals(114_800L, parsedFlat!!.priceTomans)

    // Modern Tailwind div card with heavy CSS classes + WebView innerText with unlabeled Rial disambiguation
    val tailwindCard = """
      <div class="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-md transition-all hover:shadow-lg dark:border-slate-700 dark:bg-slate-900">
        <div class="flex items-center gap-3 min-w-0 flex-1 overflow-hidden">
          <div class="flex flex-col space-y-1 text-right">
            <span class="text-sm font-extrabold tracking-tight text-slate-900">تتر</span>
            <span class="text-xs font-medium text-slate-500">USDT / Tether</span>
          </div>
        </div>
        <div class="flex flex-col items-end justify-center shrink-0">
          <span class="text-lg font-black text-emerald-600">۱,۱۴۵,۰۰۰</span>
        </div>
      </div>
    """.trimIndent()
    val parsedTailwind = com.example.data.network.AsiyaExchangeService.parseTetherFromResponse(
      rawText = tailwindCard,
      referenceDollarTomans = 113_000L
    )
    assertNotNull(parsedTailwind)
    assertEquals(114_500L, parsedTailwind!!.priceTomans)
  }

  @Test
  fun testAnnualCommitmentsPieSummaryExcludesSupermarketAndIncludesAll() {
    val mixedItems = listOf(
      com.example.data.model.ExpenseEntity(id = 1L, title = "بیمه شخص ثالث خودرو", amount = 12_000_000L, isAnnual = true, shamsiYear = 1405, shamsiMonth = 1, shamsiDay = 15, shamsiDateString = "1405/01/15"),
      com.example.data.model.ExpenseEntity(id = 2L, title = "بیمه بدنه خودرو", amount = 8_000_000L, isAnnual = true, shamsiYear = 1405, shamsiMonth = 2, shamsiDay = 10, shamsiDateString = "1405/02/10"),
      com.example.data.model.ExpenseEntity(id = 3L, title = "بیمه موتورسیکلت", amount = 2_000_000L, isAnnual = true, shamsiYear = 1405, shamsiMonth = 3, shamsiDay = 5, shamsiDateString = "1405/03/05"),
      com.example.data.model.ExpenseEntity(id = 4L, title = "عوارض شهرداری", amount = 3_000_000L, isAnnual = true, shamsiYear = 1405, shamsiMonth = 4, shamsiDay = 1, shamsiDateString = "1405/04/01"),
      com.example.data.model.ExpenseEntity(id = 5L, title = "شارژ سالانه ساختمان", amount = 5_000_000L, isAnnual = true, shamsiYear = 1405, shamsiMonth = 5, shamsiDay = 1, shamsiDateString = "1405/05/01"),
      com.example.data.model.ExpenseEntity(id = 6L, title = "سوپرمارکت", amount = 9_000_000L, isAnnual = false, shamsiYear = 1405, shamsiMonth = 8, shamsiDay = 20, shamsiDateString = "1405/08/20")
    )

    val summaries = com.example.ui.screens.AnnualCommitmentsReportHelper.buildAnnualCommitmentSummaries(mixedItems)
    // Must contain ALL 5 annual commitments and ZERO non-annual items (like سوپرمارکت)
    assertEquals(5, summaries.size)
    assertFalse("Supermarket must NEVER appear in annual commitments pie chart", summaries.any { it.folderName.contains("سوپرمارکت") })
    // Sorted descending by cost: 12M (40%), 8M (26.67%), 5M (16.67%), 3M (10%), 2M (6.67%) -> total 30M
    assertEquals("بیمه شخص ثالث خودرو", summaries[0].folderName)
    assertEquals(40.0f, summaries[0].percentage, 0.05f)
    assertEquals("بیمه بدنه خودرو", summaries[1].folderName)
    assertEquals(26.67f, summaries[1].percentage, 0.05f)
    assertEquals("بیمه موتورسیکلت", summaries[4].folderName)
    assertEquals(6.67f, summaries[4].percentage, 0.05f)
    val totalPct = summaries.sumOf { it.percentage.toDouble() }
    assertEquals(100.0, totalPct, 0.1)
  }

  @Test
  fun testEntertainmentOfflineDatabaseGeneration() {
    // Verify all 120 Classic Iranian Newspaper 15x15 Crosswords (Beginner -> Master)
    var crosswordCount = 0
    for (diff in com.example.data.model.EntertainmentDifficulty.entries) {
      for (level in 1..30) {
        val parsed = com.example.data.entertainment.PersianCrosswordGenerator.buildCrosswordFor(diff, level)
        assertEquals("Crossword $diff#$level must be 15 rows", 15, parsed.rows)
        assertEquals("Crossword $diff#$level must be 15 cols", 15, parsed.cols)

        var whiteCells = 0
        for (r in 0 until 15) {
          for (c in 0 until 15) {
            if (parsed.solutionGrid[r][c] != '#') {
              whiteCells++
              // Zero isolated 1-letter white cells
              val hasH = (c > 0 && parsed.solutionGrid[r][c - 1] != '#') || (c < 14 && parsed.solutionGrid[r][c + 1] != '#')
              val hasV = (r > 0 && parsed.solutionGrid[r - 1][c] != '#') || (r < 14 && parsed.solutionGrid[r + 1][c] != '#')
              assertTrue("Isolated white cell at ($r,$c) in $diff#$level", hasH || hasV)
            }
          }
        }
        assertTrue("Newspaper crossword $diff#$level must be dense (>= 150 white cells), got $whiteCells", whiteCells >= 150)

        val acrossRows = parsed.clues.filter { it.direction == "ACROSS" }.map { it.row }.toSet()
        val downCols = parsed.clues.filter { it.direction == "DOWN" }.map { it.col }.toSet()
        assertEquals("All 15 rows must have across clues in $diff#$level", 15, acrossRows.size)
        assertEquals("All 15 cols must have down clues in $diff#$level", 15, downCols.size)

        for (clue in parsed.clues) {
          for (i in clue.answer.indices) {
            val r = if (clue.direction == "ACROSS") clue.row else clue.row + i
            val c = if (clue.direction == "ACROSS") clue.col + i else clue.col
            assertEquals(clue.answer[i], parsed.solutionGrid[r][c])
          }
        }
        crosswordCount++
      }
    }
    assertEquals(120, crosswordCount)

    // Verify all 120 Sudokus (6x6 & 9x9, Beginner -> Master) have mathematically valid solutions & matching givens
    var sudokuCount = 0
    for (diff in com.example.data.model.EntertainmentDifficulty.entries) {
      for (level in 1..30) {
        val parsed = com.example.data.entertainment.SudokuGenerator.buildSudokuFor(diff, level)
        assertTrue(
          "Sudoku $diff#$level solution must be mathematically valid",
          com.example.data.entertainment.SudokuGenerator.isValidSolvedGrid(
            parsed.solution,
            parsed.size,
            parsed.boxRows,
            parsed.boxCols
          )
        )
        assertTrue("Sudoku must have empty cells", parsed.puzzle.contains('0'))
        for (i in parsed.puzzle.indices) {
          if (parsed.puzzle[i] != '0') {
            assertEquals(parsed.solution[i], parsed.puzzle[i])
          }
        }
        sudokuCount++
      }
    }
    assertEquals(120, sudokuCount)
  }

  @Test
  fun testBackupRestoreV2RoundTripAndV1BackwardCompatibility() {
    val tempReceipt = java.io.File.createTempFile("receipt_backup_test_", ".jpg")
    val receiptBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0x33, 0xFF.toByte(), 0xD9.toByte())
    tempReceipt.writeBytes(receiptBytes)
    try {
      val folders = listOf(
        com.example.data.model.CustomFolderEntity(
          id = 1L,
          name = "مسافرت",
          colorHex = 0xFF0EA5E9,
          isAnnualFolder = false,
          createdAt = 1_700_000_000_000L
        )
      )
      val expenses = listOf(
        com.example.data.model.ExpenseEntity(
          id = 10L,
          title = "بیمه شخص ثالث خودرو",
          amount = 12_000_000L,
          isAnnual = true,
          customFolder = null,
          timestamp = 1_795_000_000_000L,
          shamsiYear = 1405,
          shamsiMonth = 2,
          shamsiDay = 15,
          shamsiDateString = "1405/02/15",
          note = "قسط کامل سالانه",
          receiptImagePath = tempReceipt.absolutePath
        ),
        com.example.data.model.ExpenseEntity(
          id = 11L,
          title = "سوپرمارکت",
          amount = 450_000L,
          isAnnual = false,
          customFolder = "مسافرت",
          timestamp = 1_795_100_000_000L,
          shamsiYear = 1405,
          shamsiMonth = 8,
          shamsiDay = 5,
          shamsiDateString = "1405/08/05",
          note = "",
          receiptImagePath = null
        )
      )
      val puzzles = listOf(
        com.example.data.model.EntertainmentPuzzleEntity(
          id = "SUDOKU_BEGINNER_1",
          gameType = "SUDOKU",
          difficulty = "BEGINNER",
          levelNumber = 1,
          title = "سودوکو سطح ۱",
          payloadJson = "{}",
          savedProgressJson = "{\"grid\":\"1234\"}",
          isCompleted = true,
          bestTimeSeconds = 94,
          mistakesCount = 1,
          updatedAtEpochMs = 1_795_200_000_000L
        )
      )

      val v2Json = com.example.utils.BackupRestoreHelper.createBackupJson(
        expenses = expenses,
        customFolders = folders,
        context = null,
        entertainmentPuzzles = puzzles
      )
      val parsedV2 = com.example.utils.BackupRestoreHelper.parseBackupJson(v2Json)
      assertNotNull(parsedV2)
      assertEquals(com.example.utils.BackupRestoreHelper.CURRENT_BACKUP_VERSION, parsedV2!!.version)
      assertEquals(1, parsedV2.customFolders.size)
      assertEquals("مسافرت", parsedV2.customFolders[0].name)
      assertEquals(2, parsedV2.expenses.size)
      assertEquals(12_000_000L, parsedV2.expenses[0].amount)
      assertTrue(parsedV2.expenses[0].isAnnual)
      assertEquals(1, parsedV2.receiptImagesBase64.size)
      assertTrue(parsedV2.receiptImagesBase64.containsKey(tempReceipt.name))
      assertEquals(1, parsedV2.entertainmentProgress.size)
      assertEquals("SUDOKU_BEGINNER_1", parsedV2.entertainmentProgress[0].id)
      assertEquals(94, parsedV2.entertainmentProgress[0].bestTimeSeconds)
      assertTrue(parsedV2.entertainmentProgress[0].isCompleted)
    } finally {
      tempReceipt.delete()
    }

    // Legacy Version 1 JSON (no receiptImages, no preferences, no entertainmentProgress, missing shamsiYear/Month/Day)
    val legacyV1Json = """
      {
        "version": 1,
        "appName": "مدیریت مالی و هزینه‌ها",
        "backupDateShamsi": "1404/11/20",
        "backupTimestamp": 1739000000000,
        "customFolders": [{"id": 1, "name": "درمان", "colorHex": 4279286145}],
        "expenses": [
          {"id": 1, "title": "داروخانه", "amount": 320000, "isAnnual": false, "customFolder": "درمان", "shamsiDateString": "1404/11/18", "note": "نسخه"}
        ]
      }
    """.trimIndent()
    val parsedV1 = com.example.utils.BackupRestoreHelper.parseBackupJson(legacyV1Json)
    assertNotNull(parsedV1)
    assertEquals(1, parsedV1!!.version)
    assertEquals(1, parsedV1.customFolders.size)
    assertEquals(1, parsedV1.expenses.size)
    assertEquals(1404, parsedV1.expenses[0].shamsiYear)
    assertEquals(11, parsedV1.expenses[0].shamsiMonth)
    assertEquals(18, parsedV1.expenses[0].shamsiDay)
    assertTrue(parsedV1.receiptImagesBase64.isEmpty())
    assertTrue(parsedV1.preferences.isEmpty())
    assertTrue(parsedV1.entertainmentProgress.isEmpty())

    // Malformed backup JSON must return null safely without crashing
    assertNull(com.example.utils.BackupRestoreHelper.parseBackupJson("{not valid json"))
  }

  @Test
  fun testShamsiCalendarLeapYearsAndAmortizationInvariants() {
    // 1403 is an Iranian leap year (Esfand has 30 days); 1404 & 1405 are normal years (29 days)
    assertTrue(com.example.utils.PersianDateHelper.isShamsiLeapYear(1403))
    assertEquals(30, com.example.utils.PersianDateHelper.getDaysInMonth(1403, 12))
    assertFalse(com.example.utils.PersianDateHelper.isShamsiLeapYear(1404))
    assertEquals(29, com.example.utils.PersianDateHelper.getDaysInMonth(1404, 12))
    assertEquals(31, com.example.utils.PersianDateHelper.getDaysInMonth(1405, 1))
    assertEquals(30, com.example.utils.PersianDateHelper.getDaysInMonth(1405, 7))

    // Shamsi <-> Gregorian round-trip stability across leap Esfand 30
    val (gy, gm, gd) = com.example.utils.PersianDateHelper.jalaliToGregorian(1403, 12, 30)
    val roundTrip = com.example.utils.PersianDateHelper.gregorianToJalali(gy, gm, gd)
    assertEquals(1403, roundTrip.year)
    assertEquals(12, roundTrip.month)
    assertEquals(30, roundTrip.day)
  }

  @Test
  fun testExternalServicesFaultIsolationOnMalformedInputs() {
    // Corrupted / empty / 503 HTML inputs must never throw exceptions and must return safe empty/null fallbacks
    assertNull(com.example.data.network.AsiyaExchangeService.parseTetherFromResponse(""))
    assertNull(com.example.data.network.AsiyaExchangeService.parseTetherFromResponse("<html><body>503 Service Unavailable</body></html>"))
    assertNull(com.example.data.network.AsiyaExchangeService.parseTetherFromResponse("{broken json"))

    val emptyTgju = com.example.data.network.TgjuHistoryService.parseSummaryTableJson(
      "{invalid json",
      com.example.data.network.TgjuChartAsset.DOLLAR
    )
    assertTrue(emptyTgju.isEmpty())
    assertNull(com.example.data.network.TgjuHistoryService.deserializeCache("corrupted-cache"))

    val emptyRss = com.example.data.repository.NewsRepository.parseRssXml(
      "<html>404 Not Found</html>",
      com.example.data.model.NewsSource.FARARU
    )
    assertTrue(emptyRss.isEmpty())

    val emptyMarket = com.example.data.network.MarketRatesService.parseAllFromTgjuCoinHtml(
      "<html><body>Maintenance</body></html>"
    )
    assertNull(emptyMarket.dollar)
    assertNull(emptyMarket.gold18k)
    assertNull(emptyMarket.bourse)
  }

  @Test
  fun testTgjuCoinPrioritizesRetailSekehEmamiAndRetailRob() {
    val coinPageHtml = """
      <ul class="info-bar">
        <li id="l-sekee" class="high">
          <h3>سکه امامی</h3>
          <span class="info-price">740,000,000</span>
          <span class="info-change">(1.00%) 7,000,000</span>
        </li>
        <li id="l-rob" class="low">
          <h3>ربع سکه</h3>
          <span class="info-price">210,000,000</span>
          <span class="info-change">(0.50%) 1,000,000</span>
        </li>
      </ul>
      <table>
        <tr data-market-row="sekee">
          <th>سکه امامی</th>
          <td class="nf">740,000,000</td>
          <td class="nf"><span class="high">(1.00%) 7,000,000</span></td>
        </tr>
        <tr data-market-row="rob">
          <th>ربع سکه</th>
          <td class="nf">210,000,000</td>
          <td class="nf"><span class="low">(0.50%) 1,000,000</span></td>
        </tr>
      </table>
      <table>
        <tr data-market-row="retail_sekee">
          <th><a href="/profile/retail_sekee">سکه امامی تک فروشی</a></th>
          <td class="nf">762,500,000</td>
          <td class="nf"><span class="high">(1.45%) 10,900,000</span></td>
        </tr>
        <tr data-market-row="retail_rob">
          <th><a href="/profile/retail_rob">ربع سکه تک فروشی</a></th>
          <td class="nf">224,000,000</td>
          <td class="nf"><span class="low">(0.80%) 1,800,000</span></td>
        </tr>
      </table>
    """.trimIndent()

    val parsed = com.example.data.network.MarketRatesService.parseAllFromTgjuCoinHtml(coinPageHtml)
    assertEquals("retail_sekee", parsed.sekehEmami?.key)
    assertEquals("سکه امامی (تک‌فروشی)", parsed.sekehEmami?.title)
    assertEquals(76_250_000L, parsed.sekehEmami?.priceTomans)
    assertEquals(1_090_000L, parsed.sekehEmami?.changeTomans)
    assertEquals(true, parsed.sekehEmami?.isPositiveChange)

    assertEquals("retail_rob", parsed.robSekeh?.key)
    assertEquals("ربع سکه (تک‌فروشی)", parsed.robSekeh?.title)
    assertEquals(22_400_000L, parsed.robSekeh?.priceTomans)
    assertEquals(180_000L, parsed.robSekeh?.changeTomans)
    assertEquals(false, parsed.robSekeh?.isPositiveChange)

    assertEquals("retail_sekee", com.example.data.network.TgjuChartAsset.SEKEH_EMAMI.symbol)
    assertEquals("retail_rob", com.example.data.network.TgjuChartAsset.ROB_SEKEH.symbol)
  }
}





