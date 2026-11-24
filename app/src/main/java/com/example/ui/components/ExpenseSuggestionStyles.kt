package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal data class SuggestionCardStyle(
    val bgStart: Color,
    val bgEnd: Color,
    val border: Color,
    val accent: Color,
    val titleColor: Color,
    val icon: ImageVector
)

internal fun resolveSuggestionCardStyle(
    title: String,
    index: Int,
    isDark: Boolean
): SuggestionCardStyle {
    val normalized = title.trim()

    fun palette(
        darkStart: Long,
        darkEnd: Long,
        darkBorder: Long,
        darkAccent: Long,
        darkTitle: Long,
        lightStart: Long,
        lightEnd: Long,
        lightBorder: Long,
        lightAccent: Long,
        lightTitle: Long,
        icon: ImageVector
    ): SuggestionCardStyle {
        return if (isDark) {
            SuggestionCardStyle(
                bgStart = Color(darkStart),
                bgEnd = Color(darkEnd),
                border = Color(darkBorder),
                accent = Color(darkAccent),
                titleColor = Color(darkTitle),
                icon = icon
            )
        } else {
            SuggestionCardStyle(
                bgStart = Color(lightStart),
                bgEnd = Color(lightEnd),
                border = Color(lightBorder),
                accent = Color(lightAccent),
                titleColor = Color(lightTitle),
                icon = icon
            )
        }
    }

    return when {
        normalized.contains("سوپرمارکت") || normalized.contains("سوپر مارکت") || normalized.contains("لبنیات") ->
            palette(
                0xFF064E3B, 0xFF065F46, 0xFF34D399, 0xFF34D399, 0xFFECFDF5,
                0xFFECFDF5, 0xFFD1FAE5, 0xFF34D399, 0xFF059669, 0xFF064E3B,
                Icons.Default.ShoppingCart
            )

        normalized.contains("نان") ->
            palette(
                0xFF78350F, 0xFF92400E, 0xFFFBBF24, 0xFFFBBF24, 0xFFFEF3C7,
                0xFFFFFBEB, 0xFFFEF3C7, 0xFFF59E0B, 0xFFD97706, 0xFF78350F,
                Icons.Default.BakeryDining
            )

        normalized.contains("تنقلات") || normalized.contains("فست") || normalized.contains("کافه") ->
            palette(
                0xFF831843, 0xFF9D174D, 0xFFF472B6, 0xFFF472B6, 0xFFFCE7F3,
                0xFFFDF2F8, 0xFFFCE7F3, 0xFFF472B6, 0xFFDB2777, 0xFF831843,
                Icons.Default.Fastfood
            )

        normalized.contains("میوه") || normalized.contains("سبزی") ->
            palette(
                0xFF14532D, 0xFF166534, 0xFF4ADE80, 0xFF4ADE80, 0xFFDCFCE7,
                0xFFF0FDF4, 0xFFDCFCE7, 0xFF4ADE80, 0xFF16A34A, 0xFF14532D,
                Icons.Default.Eco
            )

        normalized.contains("رستوران") || normalized.contains("مرغ") || normalized.contains("گوشت") || normalized.contains("غذا") ->
            palette(
                0xFF7C2D12, 0xFF9A3412, 0xFFFB923C, 0xFFFB923C, 0xFFFFEDD5,
                0xFFFFF7ED, 0xFFFFEDD5, 0xFFFB923C, 0xFFEA580C, 0xFF7C2D12,
                Icons.Default.Restaurant
            )

        normalized.contains("اجاره") || normalized.contains("مسکن") || normalized.contains("خانه") ->
            palette(
                0xFF312E81, 0xFF3730A3, 0xFF818CF8, 0xFF818CF8, 0xFFE0E7FF,
                0xFFEEF2FF, 0xFFE0E7FF, 0xFF818CF8, 0xFF4F46E5, 0xFF312E81,
                Icons.Default.Home
            )

        normalized.contains("بنزین") || normalized.contains("سوخت") ->
            palette(
                0xFF7F1D1D, 0xFF991B1B, 0xFFF87171, 0xFFF87171, 0xFFFEE2E2,
                0xFFFEF2F2, 0xFFFEE2E2, 0xFFF87171, 0xFFDC2626, 0xFF7F1D1D,
                Icons.Default.LocalGasStation
            )

        normalized.contains("درمان") || normalized.contains("دارو") || normalized.contains("پزشک") || normalized.contains("دندان") ->
            palette(
                0xFF881337, 0xFF9F1239, 0xFFFB7185, 0xFFFB7185, 0xFFFFE4E6,
                0xFFFFF1F2, 0xFFFFE4E6, 0xFFFB7185, 0xFFE11D48, 0xFF881337,
                Icons.Default.MedicalServices
            )

        normalized.contains("تعمیر") ->
            if (normalized.contains("موتور")) {
                palette(
                    0xFF164E63, 0xFF155E75, 0xFF22D3EE, 0xFF22D3EE, 0xFFCFFAFE,
                    0xFFECFEFF, 0xFFCFFAFE, 0xFF22D3EE, 0xFF0891B2, 0xFF164E63,
                    Icons.Default.Build
                )
            } else {
                palette(
                    0xFF4C1D95, 0xFF5B21B6, 0xFFA78BFA, 0xFFA78BFA, 0xFFEDE9FE,
                    0xFFF5F3FF, 0xFFEDE9FE, 0xFFA78BFA, 0xFF7C3AED, 0xFF4C1D95,
                    Icons.Default.Build
                )
            }

        normalized.contains("بیمه") ->
            if (normalized.contains("موتور")) {
                palette(
                    0xFF0F766E, 0xFF115E59, 0xFF2DD4BF, 0xFF2DD4BF, 0xFFCCFBF1,
                    0xFFF0FDFA, 0xFFCCFBF1, 0xFF2DD4BF, 0xFF0D9488, 0xFF134E4A,
                    Icons.Default.Shield
                )
            } else {
                palette(
                    0xFF1E3A8A, 0xFF1D4ED8, 0xFF60A5FA, 0xFF60A5FA, 0xFFDBEAFE,
                    0xFFEFF6FF, 0xFFDBEAFE, 0xFF60A5FA, 0xFF2563EB, 0xFF1E3A8A,
                    Icons.Default.Shield
                )
            }

        normalized.contains("موتور") ->
            palette(
                0xFF164E63, 0xFF155E75, 0xFF22D3EE, 0xFF22D3EE, 0xFFCFFAFE,
                0xFFECFEFF, 0xFFCFFAFE, 0xFF22D3EE, 0xFF0891B2, 0xFF164E63,
                Icons.Default.TwoWheeler
            )

        normalized.contains("ماشین") || normalized.contains("خودرو") ->
            palette(
                0xFF0C4A6E, 0xFF075985, 0xFF38BDF8, 0xFF38BDF8, 0xFFE0F2FE,
                0xFFF0F9FF, 0xFFE0F2FE, 0xFF38BDF8, 0xFF0284C7, 0xFF0C4A6E,
                Icons.Default.DirectionsCar
            )

        normalized.contains("اینترنت") || normalized.contains("وای") ->
            palette(
                0xFF3B0764, 0xFF581C87, 0xFFC084FC, 0xFFC084FC, 0xFFF3E8FF,
                0xFFFAF5FF, 0xFFF3E8FF, 0xFFC084FC, 0xFF9333EA, 0xFF581C87,
                Icons.Default.Wifi
            )

        normalized.contains("موبایل") || normalized.contains("تلفن") || normalized.contains("شارژ") ->
            palette(
                0xFF1E40AF, 0xFF0369A1, 0xFF38BDF8, 0xFF67E8F9, 0xFFECFEFF,
                0xFFEFF6FF, 0xFFE0F2FE, 0xFF38BDF8, 0xFF0284C7, 0xFF0C4A6E,
                Icons.Default.Smartphone
            )

        normalized.contains("پوشاک") || normalized.contains("لباس") || normalized.contains("کفش") ->
            palette(
                0xFF701A75, 0xFF86198F, 0xFFE879F9, 0xFFE879F9, 0xFFFAE8FF,
                0xFFFDF4FF, 0xFFFAE8FF, 0xFFE879F9, 0xFFC026D3, 0xFF701A75,
                Icons.Default.Checkroom
            )

        normalized.contains("خیریه") || normalized.contains("کمک") || normalized.contains("صدقه") ->
            palette(
                0xFF881337, 0xFFBE185D, 0xFFFDA4AF, 0xFFFDA4AF, 0xFFFFF1F2,
                0xFFFFF1F2, 0xFFFCE7F3, 0xFFFB7185, 0xFFE11D48, 0xFF881337,
                Icons.Default.Favorite
            )

        normalized.contains("فاطمه") ->
            palette(
                0xFF831843, 0xFF9D174D, 0xFFF472B6, 0xFFF472B6, 0xFFFCE7F3,
                0xFFFDF2F8, 0xFFFCE7F3, 0xFFF472B6, 0xFFDB2777, 0xFF831843,
                Icons.Default.Person
            )

        normalized.contains("علی") ->
            palette(
                0xFF0C4A6E, 0xFF0369A1, 0xFF38BDF8, 0xFF38BDF8, 0xFFE0F2FE,
                0xFFF0F9FF, 0xFFE0F2FE, 0xFF38BDF8, 0xFF0284C7, 0xFF0C4A6E,
                Icons.Default.Person
            )

        normalized.contains("قبض") || normalized.contains("قبوض") || normalized.contains("بدهی") ->
            palette(
                0xFF134E4A, 0xFF115E59, 0xFF2DD4BF, 0xFF2DD4BF, 0xFFCCFBF1,
                0xFFF0FDFA, 0xFFCCFBF1, 0xFF2DD4BF, 0xFF0D9488, 0xFF134E4A,
                Icons.Default.Receipt
            )

        normalized.contains("قسط") || normalized.contains("اقساط") ->
            palette(
                0xFF78350F, 0xFFB45309, 0xFFFBBF24, 0xFFFBBF24, 0xFFFEF3C7,
                0xFFFFFBEB, 0xFFFEF3C7, 0xFFF59E0B, 0xFFD97706, 0xFF78350F,
                Icons.Default.Receipt
            )

        else -> {
            val rotating = listOf(
                palette(
                    0xFF4C1D95, 0xFF5B21B6, 0xFFA78BFA, 0xFFA78BFA, 0xFFEDE9FE,
                    0xFFF5F3FF, 0xFFEDE9FE, 0xFFA78BFA, 0xFF7C3AED, 0xFF4C1D95,
                    Icons.Default.LocalOffer
                ),
                palette(
                    0xFF0F766E, 0xFF047857, 0xFF34D399, 0xFF34D399, 0xFFECFDF5,
                    0xFFECFDF5, 0xFFD1FAE5, 0xFF34D399, 0xFF059669, 0xFF064E3B,
                    Icons.Default.LocalOffer
                ),
                palette(
                    0xFF0C4A6E, 0xFF075985, 0xFF38BDF8, 0xFF38BDF8, 0xFFE0F2FE,
                    0xFFF0F9FF, 0xFFE0F2FE, 0xFF38BDF8, 0xFF0284C7, 0xFF0C4A6E,
                    Icons.Default.LocalOffer
                ),
                palette(
                    0xFF7C2D12, 0xFF9A3412, 0xFFFB923C, 0xFFFB923C, 0xFFFFEDD5,
                    0xFFFFF7ED, 0xFFFFEDD5, 0xFFFB923C, 0xFFEA580C, 0xFF7C2D12,
                    Icons.Default.LocalOffer
                ),
                palette(
                    0xFF831843, 0xFF9D174D, 0xFFF472B6, 0xFFF472B6, 0xFFFCE7F3,
                    0xFFFDF2F8, 0xFFFCE7F3, 0xFFF472B6, 0xFFDB2777, 0xFF831843,
                    Icons.Default.LocalOffer
                ),
                palette(
                    0xFF312E81, 0xFF3730A3, 0xFF818CF8, 0xFF818CF8, 0xFFE0E7FF,
                    0xFFEEF2FF, 0xFFE0E7FF, 0xFF818CF8, 0xFF4F46E5, 0xFF312E81,
                    Icons.Default.LocalOffer
                )
            )
            rotating[index % rotating.size]
        }
    }
}
