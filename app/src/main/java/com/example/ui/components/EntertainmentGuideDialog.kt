package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EntertainmentGameType
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.utils.PersianDateHelper

data class EntertainmentGameGuideContent(
    val gameType: EntertainmentGameType,
    val shortTabTitle: String,
    val goalFa: String,
    val steps: List<String>,
    val tipFa: String,
    val accentColor: Color
)

object EntertainmentGuideContentProvider {

    val generalOverviewSteps: List<String> = listOf(
        "همه ۵ بازی بخش سرگرمی کاملاً آفلاین هستند و بدون اینترنت هم می‌توانید هر زمان بازی کنید.",
        "روی کارت هر بازی بزنید، سپس یکی از ۴ سطح «مبتدی»، «متوسط»، «پیشرفته» یا «استاد» را انتخاب کنید و دکمه «شروع بازی» را لمس کنید.",
        "اگر وسط بازی خارج شوید، پیشرفت شما به‌صورت خودکار ذخیره می‌شود و دفعه بعد با دکمه «ادامه بازی نیمه‌تمام» از همان‌جا ادامه می‌دهید.",
        "در هر زمان و داخل هر بازی، با لمس آیکون علامت سوال (؟) در بالای صفحه می‌توانید راهنمای همان بازی را ببینید."
    )

    val allGuides: List<EntertainmentGameGuideContent> = listOf(
        EntertainmentGameGuideContent(
            gameType = EntertainmentGameType.CODEBREAKER,
            shortTabTitle = "کدشکن",
            goalFa = "پیدا کردن رمز عددی پنهان گاوصندوق قبل از تمام شدن فرصت‌ها.",
            steps = listOf(
                "با کیبورد عددی پایین صفحه یک رمز حدسی وارد کنید و دکمه «ثبت حدس» را بزنید.",
                "به سرنخ‌های رنگی کنار حدستان دقت کنید: نشانه «سبز (دقیق)» یعنی هم رقم درست است و هم جایش درست است؛ نشانه «طلایی (جابه‌جا)» یعنی رقم در رمز وجود دارد ولی در جای اشتباهی قرار گرفته است.",
                "با هر حدس جدید، رقم‌های اشتباه را کنار بگذارید (می‌توانید از حالت یادداشت/خط‌زدن روی اعداد استفاده کنید) و جای رقم‌های درست را پیدا کنید.",
                "اگر در مرحله‌ای گیر کردید، دکمه «راهنمایی» یک سرنخ کمکی به شما نشان می‌دهد."
            ),
            tipFa = "برای شروع، اعدادی با رقم‌های متنوع وارد کنید تا سریع‌تر بفهمید کدام رقم‌ها در رمز حضور دارند.",
            accentColor = Color(0xFFBE123C)
        ),
        EntertainmentGameGuideContent(
            gameType = EntertainmentGameType.NONOGRAM,
            shortTabTitle = "نونگرام",
            goalFa = "رنگ‌آمیزی خانه‌های درست جدول برای کشف یک تصویر پنهان.",
            steps = listOf(
                "اعداد کنار هر سطر و بالای هر ستون نشان می‌دهند چند خانه پشت‌سرهم باید پر شود. مثلاً «۳  ۱» یعنی اول ۳ خانه پیوسته پر، سپس حداقل یک خانه خالی و بعد ۱ خانه پر.",
                "در پایین صفحه دو ابزار دارید: دکمه «پر کردن خانه» برای رنگ‌کردن خانه‌های اصلی، و دکمه «علامت خالی (×)» برای علامت‌گذاری خانه‌هایی که مطمئن هستید خالی‌اند.",
                "از سطرها یا ستون‌هایی شروع کنید که عدد بزرگ‌تری دارند؛ چون جای خانه‌های پرشان ساده‌تر مشخص می‌شود.",
                "با ترکیب سرنخ سطرها و ستون‌ها، تصویر پنهان کم‌کم کامل می‌شود."
            ),
            tipFa = "علامت‌گذاری خانه‌های خالی با (×) کمک می‌کند اشتباه نکنید و جان‌هایتان را از دست ندهید.",
            accentColor = Color(0xFFD97706)
        ),
        EntertainmentGameGuideContent(
            gameType = EntertainmentGameType.CROSSWORD,
            shortTabTitle = "جدول کلمات",
            goalFa = "پر کردن همه خانه‌های سفید جدول با کلمات فارسی درست بر اساس راهنمای افقی و عمودی.",
            steps = listOf(
                "روی هر خانه سفید جدول یا روی یکی از راهنماهای فهرست «افقی» و «عمودی» ضربه بزنید تا کلمه مربوط به آن انتخاب شود.",
                "اگر خانه‌ای هم در یک کلمه افقی و هم در یک کلمه عمودی مشترک است، با دکمه «تغییر جهت» یا لمس دوباره همان خانه، جهت نوشتن را عوض کنید.",
                "روی دکمه «کیبورد» بزنید و حروف کلمه را تایپ کنید تا خانه‌ها یکی‌یکی پر شوند.",
                "اگر کلمه‌ای را نمی‌دانستید، ابتدا کلمات متقاطع با آن را حل کنید تا حروف مشترک برایتان آشکار شود."
            ),
            tipFa = "در صورت نیاز می‌توانید از دکمه‌های راهنمایی حرف یا کلمه کمک بگیرید؛ پیشرفت جدول خودکار ذخیره می‌شود.",
            accentColor = Color(0xFF4F46E5)
        ),
        EntertainmentGameGuideContent(
            gameType = EntertainmentGameType.SUDOKU,
            shortTabTitle = "سودوکو",
            goalFa = "پر کردن همه خانه‌های خالی جدول با اعداد (۱ تا ۶ یا ۱ تا ۹) بدون هیچ تکراری.",
            steps = listOf(
                "قانون اصلی خیلی ساده است: هر عدد در هر «سطر افقی»، هر «ستون عمودی» و هر «جعبه کوچک» فقط یک بار می‌تواند قرار بگیرد.",
                "روی یک خانه خالی ضربه بزنید و عدد درست را از ردیف اعداد پایین صفحه انتخاب کنید.",
                "اگر بین چند عدد شک دارید، حالت «یادداشت» را روشن کنید تا عددهای احتمالی را به‌صورت ریز داخل خانه بنویسید بدون اینکه خطا ثبت شود.",
                "برای پاک کردن یا برگشت به حرکت قبلی از دکمه‌های «پاک‌کن» و «بازگشت» استفاده کنید."
            ),
            tipFa = "در هر مرحله ۳ جان دارید؛ اول سراغ سطرها، ستون‌ها و جعبه‌هایی بروید که خانه‌های خالی کمتری دارند.",
            accentColor = Color(0xFF0284C7)
        ),
        EntertainmentGameGuideContent(
            gameType = EntertainmentGameType.TRIVIA,
            shortTabTitle = "معما و چیستان",
            goalFa = "پاسخ دادن به چیستان‌ها، معماهای هوش و پرسش‌های اطلاعات عمومی ایران و جهان.",
            steps = listOf(
                "متن سوال یا چیستان را با دقت بخوانید و یکی از ۴ گزینه را لمس کنید.",
                "اگر گزینه‌تان اشتباه بود نگران نباشید؛ می‌توانید دوباره فکر کنید و گزینه درست را انتخاب کنید.",
                "پس از انتخاب پاسخ درست، توضیح کوتاه و جالب جواب نمایش داده می‌شود.",
                "با زدن دکمه «مرحله بعدی» بلافاصله وارد معمای بعدی شوید."
            ),
            tipFa = "برای هر سوال زمان‌سنج فعال است و بهترین رکورد شما در هر مرحله ذخیره می‌شود.",
            accentColor = Color(0xFF059669)
        )
    )

    fun guideFor(gameType: EntertainmentGameType): EntertainmentGameGuideContent =
        allGuides.firstOrNull { it.gameType == gameType } ?: allGuides.first()
}

/**
 * Simple, step-by-step plain-Persian dialog guide for all Entertainment games.
 * Pass [initialGame] = null to open on the General Overview, or a specific [EntertainmentGameType]
 * to jump straight to that game's step-by-step tutorial.
 */
@Composable
fun EntertainmentGuideDialog(
    initialGame: EntertainmentGameType? = null,
    onDismiss: () -> Unit
) {
    var selectedTabKey by rememberSaveable(initialGame) {
        mutableStateOf(initialGame?.key ?: "GENERAL")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ScaledDialogContent {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
                    .testTag("entertainment_guide_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4F46E5).copy(alpha = 0.14f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "راهنمای ساده و گام‌به‌گام سرگرمی‌ها",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "آموزش سریع هر بازی به زبان ساده",
                                    fontSize = 11.sp,
                                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("entertainment_guide_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "بستن راهنما")
                        }
                    }

                    // Filter Tabs (راهنمای کلی + 5 Games)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item(key = "GENERAL") {
                            val isSelected = selectedTabKey == "GENERAL"
                            GuideFilterChip(
                                label = "راهنمای کلی",
                                selected = isSelected,
                                accentColor = Color(0xFF4F46E5),
                                tag = "guide_tab_general",
                                onClick = { selectedTabKey = "GENERAL" }
                            )
                        }
                        items(
                            items = EntertainmentGuideContentProvider.allGuides,
                            key = { it.gameType.key }
                        ) { guide ->
                            val isSelected = selectedTabKey == guide.gameType.key
                            GuideFilterChip(
                                label = guide.shortTabTitle,
                                selected = isSelected,
                                accentColor = guide.accentColor,
                                tag = "guide_tab_${guide.gameType.key.lowercase()}",
                                onClick = { selectedTabKey = guide.gameType.key }
                            )
                        }
                    }

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedTabKey == "GENERAL") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF4F46E5).copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.28f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "باشگاه سرگرمی شامل ۵ بازی فکری متنوع و کاملاً آفلاین است. برای دیدن آموزش اختصاصی هر بازی، روی نام آن در نوار بالا بزنید:",
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            EntertainmentGuideContentProvider.generalOverviewSteps.forEachIndexed { index, stepText ->
                                NumberedGuideStepCard(
                                    stepNumber = index + 1,
                                    text = stepText,
                                    accentColor = Color(0xFF4F46E5)
                                )
                            }
                        } else {
                            val guide = remember(selectedTabKey) {
                                EntertainmentGuideContentProvider.guideFor(
                                    EntertainmentGameType.fromKey(selectedTabKey)
                                )
                            }

                            // Game Title + Goal Box
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = guide.accentColor.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, guide.accentColor.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = guide.accentColor,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "هدف بازی ${guide.gameType.titleFa}:",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = guide.accentColor
                                        )
                                    }
                                    Text(
                                        text = enDigits(guide.goalFa),
                                        fontSize = 12.5.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                            }

                            // Numbered Step-by-Step Instructions
                            guide.steps.forEachIndexed { index, stepText ->
                                NumberedGuideStepCard(
                                    stepNumber = index + 1,
                                    text = stepText,
                                    accentColor = guide.accentColor
                                )
                            }

                            // Practical Tip Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.42f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(11.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(18.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "نکته کاربردی",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                        Text(
                                            text = enDigits(guide.tipFa),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Close / Got It Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("entertainment_guide_confirm_button")
                    ) {
                        Text(
                            text = "متوجه شدم",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * Collapsible inline step-by-step quick guide card displayed inside each game's level selector
 * so players can review how the game works before tapping Play.
 */
@Composable
fun InlineGameQuickGuideCard(
    gameType: EntertainmentGameType,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val guide = remember(gameType) { EntertainmentGuideContentProvider.guideFor(gameType) }
    var isExpanded by rememberSaveable(gameType.key) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inline_game_quick_guide_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("inline_game_quick_guide_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "راهنمای ساده و گام‌به‌گام این بازی",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = appTextColor(MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = if (isExpanded) "برای بستن راهنما لمس کنید" else "برای دیدن روش بازی لمس کنید",
                            fontSize = 11.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "بستن راهنما" else "باز کردن راهنما",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.09f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = enDigits("هدف بازی: ${guide.goalFa}"),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = appTextColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    guide.steps.forEachIndexed { idx, step ->
                        NumberedGuideStepCard(
                            stepNumber = idx + 1,
                            text = step,
                            accentColor = accentColor
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = enDigits("نکته: ${guide.tipFa}"),
                                fontSize = 11.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                color = if (selected) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun NumberedGuideStepCard(
    stepNumber: Int,
    text: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            ) {
                Text(
                    text = PersianDateHelper.toPersianDigits(stepNumber),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Text(
                text = enDigits(text),
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
