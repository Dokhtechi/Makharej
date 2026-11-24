package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NewsArticle
import com.example.data.model.NewsCategory
import com.example.ui.theme.LocalAppFontScale
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.utils.NewsSpeechState
import com.example.utils.PersianDateHelper
import java.util.Locale

@Composable
internal fun ActiveNewsSpeechBar(
    speechState: NewsSpeechState,
    onCycleSpeed: () -> Unit,
    onToggleVoice: () -> Unit,
    onStop: () -> Unit
) {
    val speedLabel = String.format(Locale.US, "%.2gx", speechState.speechSpeed)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_news_speech_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF0F766E))
                    )
                )
                .padding(horizontal = scaledDp(12.dp), vertical = scaledDp(10.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(scaledDp(36.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (speechState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "در حال خوانش صوتی خبر",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                speechState.isLoading -> "آماده‌سازی صدای تهرانی..."
                                else -> "در حال خوانش خبر"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFA7F3D0),
                            maxLines = 1
                        )
                        if (speechState.totalChunks > 0 && speechState.currentChunkIndex > 0) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "${speechState.currentChunkIndex}/${speechState.totalChunks}",
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Text(
                        text = enDigits(speechState.activeArticleTitle),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Voice Persona toggle chip (اندرو / فرید / آوا / دلارا)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .testTag("news_speech_voice_button")
                        .clickable { onToggleVoice() }
                ) {
                    Text(
                        text = speechState.voicePersona.shortLabelFa,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Speed toggle chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .testTag("news_speech_speed_button")
                        .clickable { onCycleSpeed() }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = speedLabel,
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                // Stop button (48dp touch target)
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("stop_news_speech_button")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "توقف خوانش خبر",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    isSpeaking: Boolean,
    isSpeechLoading: Boolean,
    onToggleSpeak: () -> Unit,
    onReadFull: () -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val srcColor = Color(article.source.colorHex)
    val isVoiceActive = isSpeaking || isSpeechLoading
    val uiScale = LocalAppFontScale.current

    val categoryColor = when (article.category) {
        NewsCategory.ACCIDENTS -> Color(0xFFB91C1C)
        NewsCategory.ECONOMY -> Color(0xFF059669)
        NewsCategory.POLITICS -> Color(0xFF4338CA)
        NewsCategory.TECHNOLOGY -> Color(0xFF7C3AED)
        NewsCategory.SCIENCE -> Color(0xFF0284C7)
        NewsCategory.CULTURAL -> Color(0xFFD97706)
        NewsCategory.SOCIAL -> Color(0xFFE11D48)
        NewsCategory.HEALTH -> Color(0xFF0D9488)
        NewsCategory.ALL -> MaterialTheme.colorScheme.primary
    }

    val categoryBg = when (article.category) {
        NewsCategory.ACCIDENTS -> Color(0xFFFEF2F2)
        NewsCategory.ECONOMY -> Color(0xFFECFDF5)
        NewsCategory.POLITICS -> Color(0xFFEEF2FF)
        NewsCategory.TECHNOLOGY -> Color(0xFFF5F3FF)
        NewsCategory.SCIENCE -> Color(0xFFF0F9FF)
        NewsCategory.CULTURAL -> Color(0xFFFFFBEB)
        NewsCategory.SOCIAL -> Color(0xFFFFF1F2)
        NewsCategory.HEALTH -> Color(0xFFF0FDFA)
        NewsCategory.ALL -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("news_card_${article.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onReadFull() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isVoiceActive) {
                Color(0xFFECFDF5).copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isVoiceActive) 4.dp else 2.dp),
        border = BorderStroke(
            width = if (isVoiceActive) 2.dp else 1.4.dp,
            color = if (isVoiceActive) Color(0xFF059669) else srcColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaledDp(14.dp))
        ) {
            // Line 1: Source badge + Category badge + Fetch Time (ساعت اخذ خبر)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = srcColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, srcColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(srcColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = article.source.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = srcColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryBg
                    ) {
                        Text(
                            text = article.category.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (!article.imageUrl.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "دارای تصویر",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    if (!article.videoUrl.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "دارای ویدیو",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "تاریخ و ساعت دقیق خبر",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = PersianDateHelper.toEnglishDigits(article.fetchTime),
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(scaledDp(10.dp)))

            // Line 2: Headline / Title
            Text(
                text = enDigits(article.title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(scaledDp(7.dp)))

            // Line 3: Expanded Summary under headline
            val cardSummary = remember(article.id, article.summary, article.fullContent) {
                val cleanedFull = article.fullContent
                    .replace(article.title, "")
                    .replace(Regex("""منبع\s*:\s*.*$""", RegexOption.MULTILINE), "")
                    .trim()
                if (cleanedFull.length > article.summary.length) cleanedFull else article.summary
            }
            Text(
                text = enDigits(cardSummary),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 14,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Line 4: Action row with Persian Voice Reader, "شرح خبر", "اصل خبر", and Share
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVoiceActive) Color(0xFF059669) else Color(0xFFECFDF5),
                            border = BorderStroke(
                                1.2.dp,
                                if (isVoiceActive) Color(0xFF047857) else Color(0xFF10B981)
                            ),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .testTag("news_speak_button_${article.id}")
                                .clickable { onToggleSpeak() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = scaledDp(8.dp), vertical = scaledDp(5.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSpeechLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (isSpeaking) "توقف خوانش صوتی" else "خوانش صوتی خبر",
                                        tint = if (isVoiceActive) Color.White else Color(0xFF059669),
                                        modifier = Modifier.size(scaledDp(15.dp))
                                    )
                                }
                                Text(
                                    text = when {
                                        isSpeechLoading -> "صبر..."
                                        isSpeaking -> "توقف"
                                        else -> "شنیدن"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isVoiceActive) Color.White else Color(0xFF047857),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onReadFull() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = scaledDp(8.dp), vertical = scaledDp(5.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "مشاهده شرح خبر",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(scaledDp(14.dp))
                                )
                                Text(
                                    text = "شرح خبر",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (uiScale < 1.18f) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenLink(resolveDirectArticleUrl(article)) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = scaledDp(8.dp), vertical = scaledDp(5.dp)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "مشاهده اصل خبر",
                                        tint = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                        modifier = Modifier.size(scaledDp(13.dp))
                                    )
                                    Text(
                                        text = "اصل خبر",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(scaledDp(34.dp))
                            .clip(CircleShape)
                            .clickable { onShare() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "اشتراک‌گذاری خبر",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (uiScale >= 1.18f) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenLink(resolveDirectArticleUrl(article)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = scaledDp(10.dp), vertical = scaledDp(6.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "مشاهده اصل خبر",
                                tint = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.size(scaledDp(13.dp))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "مشاهده اصل خبر در سایت منبع",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves the direct URL to the specific news article on the source website.
 */
fun resolveDirectArticleUrl(article: NewsArticle): String {
    val raw = article.url.trim()
    val isGenericRoot = raw.equals("https://www.zoomit.ir", ignoreCase = true) ||
            raw.equals("https://www.zoomit.ir/", ignoreCase = true) ||
            raw.equals("https://www.khabaronline.ir", ignoreCase = true) ||
            raw.equals("https://www.khabaronline.ir/", ignoreCase = true) ||
            raw.equals("https://www.tabnak.ir", ignoreCase = true) ||
            raw.equals("https://www.tabnak.ir/", ignoreCase = true) ||
            raw.equals("https://fararu.com", ignoreCase = true) ||
            raw.equals("https://fararu.com/", ignoreCase = true) ||
            raw.equals("https://www.fararu.ir", ignoreCase = true) ||
            raw.equals("https://www.fararu.ir/", ignoreCase = true) ||
            raw.equals("https://aftabnews.ir", ignoreCase = true) ||
            raw.equals("https://aftabnews.ir/", ignoreCase = true) ||
            raw.equals("https://www.eghtesadonline.com", ignoreCase = true) ||
            raw.equals("https://www.eghtesadonline.com/", ignoreCase = true) ||
            raw.equals("https://sahamnews.org", ignoreCase = true) ||
            raw.equals("https://sahamnews.org/", ignoreCase = true) ||
            raw.equals(article.source.websiteUrl, ignoreCase = true) ||
            raw.equals("${article.source.websiteUrl}/", ignoreCase = true) ||
            raw.length <= article.source.websiteUrl.length + 3

    val hasDirectArticlePath = raw.contains("/news/") ||
            raw.contains("/fa/news/") ||
            raw.contains("/fa/tiny/") ||
            raw.contains("/article/") ||
            raw.contains("/post/") ||
            raw.contains("/item/") ||
            raw.contains("?p=")

    if (!isGenericRoot && hasDirectArticlePath) {
        return raw
    }

    val searchTarget = "${article.title.trim()} ${article.source.displayName}"
    return "https://www.google.com/search?q=" + Uri.encode(searchTarget)
}
