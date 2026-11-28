package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("مخارج", appName)
  }

  @Test
  fun `verify Basalam direct API parser and Digikala Torob quick browser URL builders`() {
    val sources = com.example.data.model.PriceSource.entries
    assertEquals(1, sources.size)
    assertEquals(listOf("basalam"), sources.map { it.id })

    // Quick browser search URLs (non-empty query vs empty fallback homepage)
    assertEquals(
      "https://www.digikala.com",
      com.example.data.repository.PriceSearchService.buildDigikalaSearchUrl("   ")
    )
    assertEquals(
      "https://torob.com",
      com.example.data.repository.PriceSearchService.buildTorobSearchUrl("")
    )
    assertTrue(
      com.example.data.repository.PriceSearchService.buildDigikalaSearchUrl("گوشی A55")
        .startsWith("https://www.digikala.com/search/?q=")
    )
    assertTrue(
      com.example.data.repository.PriceSearchService.buildTorobSearchUrl("گوشی A55")
        .startsWith("https://torob.com/search/?query=")
    )

    val basalamSampleJson = """
      {
        "products": [
          {
            "id": 987654,
            "name": "گوشی موبایل شیائومی Redmi Note 13 8/256 گیگابایت دو سیم‌کارت",
            "price": 125000000,
            "stock": 5,
            "photo": {
              "MEDIUM": "https://statics.basalam.com/public/photo/note13.jpg"
            },
            "vendor": {
              "name": "غرفه دیجیتال پارس",
              "identifier": "digital-pars",
              "city": { "title": "تهران" }
            },
            "rating": { "average": 4.8, "count": 21 }
          }
        ]
      }
    """.trimIndent()

    val basalamParsed = com.example.data.repository.PriceSearchService.parseBasalamResponse(
      basalamSampleJson,
      "گوشی شیائومی"
    )
    assertEquals(1, basalamParsed.size)
    assertEquals(com.example.data.model.PriceSource.BASALAM, basalamParsed[0].source)
    assertEquals(12_500_000L, basalamParsed[0].priceToman)
    assertEquals("غرفه دیجیتال پارس", basalamParsed[0].shopName)
    assertEquals("https://basalam.com/digital-pars/product/987654", basalamParsed[0].productUrl)
    assertEquals("https://statics.basalam.com/public/photo/note13.jpg", basalamParsed[0].imageUrl)
    assertTrue(basalamParsed[0].specs.any { it.contains("رم") })
    assertTrue(basalamParsed[0].specs.any { it.contains("حافظه") })
    assertTrue(basalamParsed[0].specs.any { it.contains("تهران") })
  }

  @Test
  fun `verify newly entered expense item is added to suggested items list and persists across reloads`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences(
      com.example.ui.components.ExpenseSuggestionsStore.PREFS_NAME,
      Context.MODE_PRIVATE
    ).edit().clear().commit()

    val initial = com.example.ui.components.ExpenseSuggestionsStore.loadSuggestions(context)
    assertTrue(initial.isNotEmpty())

    // Adding a brand-new expense title appends and persists it
    val afterAdd = com.example.ui.components.ExpenseSuggestionsStore.addSuggestionIfMissing(
      context = context,
      rawTitle = "  شارژ ساختمان  ",
      currentItems = initial
    )
    assertTrue(afterAdd.contains("شارژ ساختمان"))
    assertEquals(initial.size + 1, afterAdd.size)

    // Reloading from SharedPreferences retains the new item
    val reloaded = com.example.ui.components.ExpenseSuggestionsStore.loadSuggestions(context)
    assertTrue(reloaded.contains("شارژ ساختمان"))

    // Adding the same title again does not duplicate it
    val afterDuplicate = com.example.ui.components.ExpenseSuggestionsStore.addSuggestionIfMissing(
      context = context,
      rawTitle = "شارژ ساختمان",
      currentItems = reloaded
    )
    assertEquals(reloaded.size, afterDuplicate.size)
  }
}
