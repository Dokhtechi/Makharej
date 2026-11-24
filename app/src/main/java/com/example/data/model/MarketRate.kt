package com.example.data.model

data class MarketRateItem(
    val key: String,
    val title: String,
    val priceTomans: Long,
    val changePercent: Double,
    val changeTomans: Long = 0L,
    val isPositiveChange: Boolean,
    val timeFormatted: String,
    val unitLabel: String = "تومان",
    val displayPriceFormatted: String? = null,
    val displayDiffFormatted: String? = null,
    val sourceLabel: String = "TGJU (tgju.org/coin)"
)

data class MarketRatesState(
    val dollar: MarketRateItem = MarketRateItem(
        key = "price_dollar_rl",
        title = "دلار",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val euro: MarketRateItem = MarketRateItem(
        key = "price_eur",
        title = "یورو",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val sekehEmami: MarketRateItem = MarketRateItem(
        key = "sekee",
        title = "سکه امامی",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val robSekeh: MarketRateItem = MarketRateItem(
        key = "rob",
        title = "ربع سکه",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val goldOunce: MarketRateItem = MarketRateItem(
        key = "ons",
        title = "انس جهانی طلا",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "دلار",
        displayPriceFormatted = null,
        displayDiffFormatted = null,
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val gold18k: MarketRateItem = MarketRateItem(
        key = "geram18",
        title = "طلای 18 عیار",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val bitcoin: MarketRateItem = MarketRateItem(
        key = "crypto-bitcoin",
        title = "بیت‌کوین",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "دلار",
        displayPriceFormatted = null,
        displayDiffFormatted = null,
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val tether: MarketRateItem = MarketRateItem(
        key = "asiya-usdt",
        title = "تتر (USDT)",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "تومان",
        sourceLabel = "Asiya Exchange (asiyaexchange.com)"
    ),
    val crudeOil: MarketRateItem = MarketRateItem(
        key = "oil_brent",
        title = "نفت برنت",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "دلار",
        displayPriceFormatted = null,
        displayDiffFormatted = null,
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val bourse: MarketRateItem = MarketRateItem(
        key = "bourse",
        title = "شاخص کل بورس",
        priceTomans = 0L,
        changePercent = 0.0,
        changeTomans = 0L,
        isPositiveChange = true,
        timeFormatted = "",
        unitLabel = "واحد",
        displayPriceFormatted = null,
        displayDiffFormatted = null,
        sourceLabel = "TGJU (tgju.org/coin)"
    ),
    val lastUpdateTime: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
