package com.example.data.model

enum class PriceSource(
    val id: String,
    val displayNameFa: String,
    val englishName: String,
    val badgeColorHex: Long,
    val websiteBaseUrl: String
) {
    BASALAM(
        id = "basalam",
        displayNameFa = "باسلام",
        englishName = "Basalam",
        badgeColorHex = 0xFFEA580C,
        websiteBaseUrl = "https://basalam.com"
    )
}

enum class PriceSortOrder(val labelFa: String, val shortLabelFa: String) {
    CHEAPEST_FIRST("ارزان‌ترین به گران‌ترین", "ارزان‌ترین"),
    EXPENSIVE_FIRST("گران‌ترین به ارزان‌ترین", "گران‌ترین")
}

data class PriceProductItem(
    val id: String,
    val title: String,
    val priceToman: Long,
    val imageUrl: String?,
    val productUrl: String,
    val source: PriceSource = PriceSource.BASALAM,
    val shopName: String? = null,
    val availability: String? = null,
    val specs: List<String> = emptyList()
)

data class PriceSearchResponse(
    val query: String,
    val items: List<PriceProductItem>,
    val errorMessage: String? = null
)
