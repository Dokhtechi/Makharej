package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NewsArticle
import com.example.data.model.NewsCategory
import com.example.data.model.NewsSource
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.utils.NewsVoicePersona
import com.example.utils.PersianDateHelper

@Composable
fun NewsScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allNews by viewModel.newsList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isNewsLoading.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.newsLastUpdated.collectAsStateWithLifecycle()
    val newsError by viewModel.newsErrorMessage.collectAsStateWithLifecycle()
    val speechState by viewModel.newsSpeechState.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(NewsCategory.ALL) }
    var selectedSource by remember { mutableStateOf<NewsSource?>(null) }
    var selectedArticleForDetail by remember { mutableStateOf<NewsArticle?>(null) }

    // Refresh live news only once per app launch when first entering the News tab (not on every tab switch)
    LaunchedEffect(Unit) {
        viewModel.ensureNewsRefreshedOnAppOpen()
    }

    LaunchedEffect(speechState.statusMessage) {
        val msg = speechState.statusMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearNewsSpeechStatus()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopSpeakingNews()
        }
    }

    // Filter news based on category and source
    val filteredNews = remember(allNews, selectedCategory, selectedSource) {
        allNews.filter { article ->
            val matchCategory = selectedCategory == NewsCategory.ALL || article.category == selectedCategory
            val matchSource = selectedSource == null || article.source == selectedSource
            matchCategory && matchSource
        }
    }

    // In-App News Detail Dialog (auto-fetches full multi-paragraph article content for rich شرح خبر)
    selectedArticleForDetail?.let { article ->
        var enrichedArticle by remember(article.id) { mutableStateOf(article) }
        var isLoadingFullArticle by remember(article.id) { mutableStateOf(false) }

        LaunchedEffect(article.id) {
            isLoadingFullArticle = true
            viewModel.loadFullNewsArticle(article) { loaded ->
                enrichedArticle = loaded
                isLoadingFullArticle = false
            }
        }

        NewsDetailDialog(
            article = enrichedArticle,
            isLoadingFullContent = isLoadingFullArticle,
            speechState = speechState,
            onToggleSpeak = { viewModel.toggleSpeakNews(enrichedArticle) },
            onCycleSpeechSpeed = { viewModel.cycleNewsSpeechSpeed() },
            onDismiss = { selectedArticleForDetail = null },
            onOpenBrowser = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "مرورگری برای باز کردن لینک یافت نشد", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                try {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${enrichedArticle.title}\n\n${enrichedArticle.fullContent.ifBlank { enrichedArticle.summary }}\n\nمنبع: ${enrichedArticle.source.displayName}\n${enrichedArticle.url}"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری خبر"))
                } catch (_: Exception) {}
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = scaledDp(16.dp))
            .testTag("news_screen"),
        verticalArrangement = Arrangement.spacedBy(scaledDp(12.dp))
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Compact Header Card with Live Status & Refresh
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = scaledDp(12.dp), vertical = scaledDp(10.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(scaledDp(36.dp))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Newspaper,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(scaledDp(20.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(scaledDp(10.dp)))
                            Column {
                                Text(
                                    text = "اخبار برگزیده روز",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isLoading) "در حال دریافت..." else "به‌روزرسانی:",
                                        fontSize = 11.sp,
                                        color = appTextColor(MaterialTheme.colorScheme.onPrimaryContainer),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (!isLoading && lastUpdated.isNotBlank()) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = PersianDateHelper.toEnglishDigits(lastUpdated),
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Refresh button with animation
                        val infiniteTransition = rememberInfiniteTransition(label = "spin")
                        val angle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing)
                            ),
                            label = "spin_angle"
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("news_refresh_button")
                                .clickable(enabled = !isLoading) {
                                    viewModel.refreshNews()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isLoading) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "در حال بارگذاری",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(angle)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "به‌روزرسانی اخبار",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Distinct Tehran-Iranian Male & Female Voice Persona Picker (Online Streaming)
                    if (allNews.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "انتخاب گوینده خبر",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "گوینده:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = appTextColor(MaterialTheme.colorScheme.onPrimaryContainer),
                                maxLines = 1,
                                softWrap = false
                            )
                            NewsVoicePersona.entries.forEach { persona ->
                                val selected = speechState.voicePersona == persona
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .testTag("voice_persona_${persona.key}")
                                        .clickable { viewModel.selectNewsVoicePersona(persona) }
                                ) {
                                    Text(
                                        text = persona.shortLabelFa,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                        color = if (selected) Color.White else appTextColor(MaterialTheme.colorScheme.onSurface),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!newsError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !isLoading) { viewModel.refreshNews() }
                        ) {
                            Text(
                                text = newsError!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1.5 Active Persian Voice News Player Bar
        if (speechState.activeArticleId != null) {
            item(key = "active_news_speech_bar") {
                ActiveNewsSpeechBar(
                    speechState = speechState,
                    onCycleSpeed = { viewModel.cycleNewsSpeechSpeed() },
                    onToggleVoice = { viewModel.toggleNewsVoicePersona() },
                    onStop = { viewModel.stopSpeakingNews() }
                )
            }
        }

        // 3. Category Filter Chips (همه، حوادث، اقتصادی، سیاسی، تکنولوژی، علمی، فرهنگی، اجتماعی، سلامت)
        item {
            val categoryCounts = remember(allNews) {
                allNews.groupingBy { it.category }.eachCount()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NewsCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    val count = if (category == NewsCategory.ALL) allNews.size else (categoryCounts[category] ?: 0)
                    val chipText = enDigits(
                        if (allNews.isNotEmpty()) "${category.displayName} ($count)" else category.displayName
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCategory = category }
                    ) {
                        Text(
                            text = chipText,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color.White else appTextColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.padding(horizontal = scaledDp(12.dp), vertical = scaledDp(7.dp))
                        )
                    }
                }
            }
        }

        // 4. Source Filter Chips (همه منابع، فرارو، خبرآنلاین، اقتصاد آنلاین، تابناک، زومیت، آفتاب، سهام نیوز)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All sources
                val isAllSelected = selectedSource == null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAllSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isAllSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedSource = null }
                ) {
                    Text(
                        text = enDigits("همه منابع (8 پایگاه خبری و حوادث)"),
                        fontSize = 11.5.sp,
                        fontWeight = if (isAllSelected) FontWeight.Black else FontWeight.Bold,
                        color = if (isAllSelected) appTextColor(MaterialTheme.colorScheme.onSecondaryContainer) else appTextColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(horizontal = scaledDp(10.dp), vertical = scaledDp(5.dp))
                    )
                }

                // Sources chips
                NewsSource.values().forEach { src ->
                    val isSelected = selectedSource == src
                    val srcColor = Color(src.colorHex)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) srcColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) srcColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedSource = src }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = scaledDp(10.dp), vertical = scaledDp(5.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(srcColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = src.displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) srcColor else appTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                }
            }
        }

        // 5. Results Count Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = enDigits("نمایش ${filteredNews.size} خبر از پایگاه‌های منتخب"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                )

                if (selectedCategory != NewsCategory.ALL || selectedSource != null) {
                    Text(
                        text = "پاکسازی فیلترها",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedCategory = NewsCategory.ALL
                                selectedSource = null
                            }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Loading or Empty state if nothing matches
        if (filteredNews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoading && allNews.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "در حال دریافت تازه‌ترین اخبار...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Newspaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (allNews.isEmpty()) "هنوز خبری دریافت نشده است" else "خبری با این مشخصات یافت نشد",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (allNews.isEmpty()) {
                                    "برای دریافت تازه‌ترین اخبار روز دکمه زیر را لمس کنید."
                                } else {
                                    "لطفاً فیلتر دسته‌بندی یا منبع خبری را تغییر دهید."
                                },
                                fontSize = 12.sp,
                                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                textAlign = TextAlign.Center
                            )
                            if (allNews.isEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.refreshNews() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("دریافت تازه‌ترین اخبار", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. News Articles Cards
        items(
            items = filteredNews,
            key = { it.id }
        ) { article ->
            val isSpeakingThis = speechState.activeArticleId == article.id && speechState.isPlaying
            val isLoadingSpeechThis = speechState.activeArticleId == article.id && speechState.isLoading
            NewsArticleCard(
                article = article,
                isSpeaking = isSpeakingThis,
                isSpeechLoading = isLoadingSpeechThis,
                onToggleSpeak = { viewModel.toggleSpeakNews(article) },
                onReadFull = { selectedArticleForDetail = article },
                onOpenLink = { _ ->
                    val targetUrl = resolveDirectArticleUrl(article)
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "مرورگری برای باز کردن لینک یافت نشد", Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    try {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${article.title}\n\n${article.fullContent.ifBlank { article.summary }}\n\nمنبع: ${article.source.displayName}\n${article.url}"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری خبر"))
                    } catch (_: Exception) {}
                }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
