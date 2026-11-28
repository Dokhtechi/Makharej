package com.example.data.entertainment

import com.example.data.model.CodebreakerPuzzleData
import com.example.data.model.EntertainmentDifficulty
import com.example.data.model.EntertainmentGameType
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.data.model.NonogramPuzzleData
import kotlin.random.Random

/**
 * Pure-Kotlin JSON helpers so payload serialization/deserialization works identically
 * in Android runtime AND local JVM unit tests (avoiding Android org.json stubs).
 */
private object BrainGameJsonUtil {
    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val raw = pattern.find(json)?.groupValues?.getOrNull(1) ?: return null
        return raw
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    fun extractInt(json: String, key: String): Int? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun extractLong(json: String, key: String): Long? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    fun extractStringArray(json: String, key: String): List<String>? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val inner = pattern.find(json)?.groupValues?.getOrNull(1) ?: return null
        val itemPattern = Regex(""""((?:[^"\\]|\\.)*)"""")
        return itemPattern.findAll(inner).map { match ->
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }.toList()
    }
}

/**
 * Generator & logical evaluator for «کدشکن منطقی (Mastermind / Bulls & Cows)».
 * 120 deterministic adult deductive puzzles across 4 difficulty tiers.
 */
object CodebreakerGenerator {

    private val vaultThemes = listOf(
        "گاوصندوق بانک مرکزی", "قفل خزانه زمرد", "رمز اتاق معما", "کلید برج ساعت",
        "کد امنیتی آزمایشگاه", "قفل صندوقچه الماس", "رمز دروازه شمالی", "خزانه اسناد سری",
        "مهر رمزنگاری کلاسیک", "کد رصدخانه ستارگان", "گاوصندوق عتیقه", "قفل کتابخانه کهن",
        "رمز سفینه کاوشگر", "کد بندر مروارید", "خزانه سکه طلا", "قفل گالری هنر",
        "رمز قطار سریع‌السیر", "کد اتاق فرمان", "قفل کاخ شیشه‌ای", "خزانه یاقوت سرخ"
    )

    fun generateAllLevels(): List<EntertainmentPuzzleEntity> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<EntertainmentPuzzleEntity>()

        for (diff in EntertainmentDifficulty.entries) {
            for (level in 1..30) {
                val data = buildPuzzleFor(diff, level)
                result.add(
                    EntertainmentPuzzleEntity(
                        id = "codebreaker_${diff.key.lowercase()}_$level",
                        gameType = EntertainmentGameType.CODEBREAKER.key,
                        difficulty = diff.key,
                        levelNumber = level,
                        title = "کدشکن ${diff.titleFa} - مرحله $level",
                        payloadJson = serializePayload(data),
                        updatedAtEpochMs = now
                    )
                )
            }
        }
        return result
    }

    fun buildPuzzleFor(difficulty: EntertainmentDifficulty, level: Int): CodebreakerPuzzleData {
        val seed = difficulty.order * 10_007 + level * 733 + 19_421
        val rng = Random(seed)

        val codeLength: Int
        val minDigit: Int
        val maxDigit: Int
        val allowRepeats: Boolean
        val maxAttempts: Int

        when (difficulty) {
            EntertainmentDifficulty.BEGINNER -> {
                codeLength = 3
                minDigit = 1
                maxDigit = 6
                allowRepeats = false
                maxAttempts = 8
            }
            EntertainmentDifficulty.INTERMEDIATE -> {
                codeLength = 4
                minDigit = 1
                maxDigit = 7
                allowRepeats = false
                maxAttempts = 9
            }
            EntertainmentDifficulty.ADVANCED -> {
                codeLength = 4
                minDigit = 0
                maxDigit = 9
                allowRepeats = false
                maxAttempts = 10
            }
            EntertainmentDifficulty.MASTER -> {
                codeLength = 5
                minDigit = 0
                maxDigit = 9
                allowRepeats = true
                maxAttempts = 10
            }
        }

        val pool = (minDigit..maxDigit).toList()
        val secretDigits: List<Int> = if (!allowRepeats) {
            pool.shuffled(rng).take(codeLength)
        } else {
            // Ensure Master puzzles genuinely include interesting structure (at least 3 distinct digits, often 1 repeat)
            val chosen = MutableList(codeLength) { pool[rng.nextInt(pool.size)] }
            if (chosen.distinct().size < 3) {
                val distinctPool = pool.shuffled(rng)
                chosen[0] = distinctPool[0]
                chosen[1] = distinctPool[1]
                chosen[2] = distinctPool[2]
            }
            chosen
        }

        val secretCode = secretDigits.joinToString("")
        val sum = secretDigits.sum()
        val evenCount = secretDigits.count { it % 2 == 0 }
        val oddCount = codeLength - evenCount
        val hasRepeat = secretDigits.distinct().size < secretDigits.size

        val hintText = when ((level + difficulty.order) % 3) {
            0 -> "مجموع ارقام رمز برابر $sum است."
            1 -> "در این رمز $evenCount رقم زوج و $oddCount رقم فرد وجود دارد."
            else -> if (allowRepeats) {
                if (hasRepeat) "در این رمز حداقل یک رقم تکراری وجود دارد (مجموع ارقام: $sum)."
                else "همه ارقام این رمز غیرتکراری هستند (مجموع ارقام: $sum)."
            } else {
                "کوچک‌ترین رقم رمز ${secretDigits.min()} و بزرگ‌ترین رقم ${secretDigits.max()} است."
            }
        }

        val themeTitle = vaultThemes[(difficulty.order * 7 + level - 1) % vaultThemes.size]

        return CodebreakerPuzzleData(
            codeLength = codeLength,
            minDigit = minDigit,
            maxDigit = maxDigit,
            allowRepeats = allowRepeats,
            maxAttempts = maxAttempts,
            secretCode = secretCode,
            themeTitle = themeTitle,
            hintText = hintText
        )
    }

    /**
     * Evaluates a Mastermind / Bulls & Cows guess accurately, including repeated digits.
     * Returns Pair(exactPositionHits, wrongPositionHits).
     */
    fun evaluateGuess(secret: String, guess: String): Pair<Int, Int> {
        if (secret.length != guess.length || secret.isEmpty()) return 0 to 0
        var exact = 0
        val secretRemainder = IntArray(10)
        val guessRemainder = IntArray(10)

        for (i in secret.indices) {
            val s = secret[i]
            val g = guess[i]
            if (s == g) {
                exact++
            } else {
                if (s in '0'..'9') secretRemainder[s - '0']++
                if (g in '0'..'9') guessRemainder[g - '0']++
            }
        }

        var misplaced = 0
        for (d in 0..9) {
            misplaced += minOf(secretRemainder[d], guessRemainder[d])
        }
        return exact to misplaced
    }

    fun serializePayload(data: CodebreakerPuzzleData): String {
        return buildString {
            append('{')
            append("\"codeLength\":${data.codeLength},")
            append("\"minDigit\":${data.minDigit},")
            append("\"maxDigit\":${data.maxDigit},")
            append("\"allowRepeats\":${data.allowRepeats},")
            append("\"maxAttempts\":${data.maxAttempts},")
            append("\"secretCode\":\"${BrainGameJsonUtil.escape(data.secretCode)}\",")
            append("\"themeTitle\":\"${BrainGameJsonUtil.escape(data.themeTitle)}\",")
            append("\"hintText\":\"${BrainGameJsonUtil.escape(data.hintText)}\"")
            append('}')
        }
    }

    fun parsePayload(json: String): CodebreakerPuzzleData? {
        if (json.isBlank()) return null
        val codeLength = BrainGameJsonUtil.extractInt(json, "codeLength") ?: return null
        val minDigit = BrainGameJsonUtil.extractInt(json, "minDigit") ?: 0
        val maxDigit = BrainGameJsonUtil.extractInt(json, "maxDigit") ?: 9
        val allowRepeats = BrainGameJsonUtil.extractBoolean(json, "allowRepeats") ?: false
        val maxAttempts = BrainGameJsonUtil.extractInt(json, "maxAttempts") ?: 9
        val secretCode = BrainGameJsonUtil.extractString(json, "secretCode") ?: return null
        if (secretCode.length != codeLength) return null
        val themeTitle = BrainGameJsonUtil.extractString(json, "themeTitle").orEmpty().ifBlank { "گاوصندوق منطقی" }
        val hintText = BrainGameJsonUtil.extractString(json, "hintText").orEmpty()
        return CodebreakerPuzzleData(
            codeLength = codeLength,
            minDigit = minDigit,
            maxDigit = maxDigit,
            allowRepeats = allowRepeats,
            maxAttempts = maxAttempts,
            secretCode = secretCode,
            themeTitle = themeTitle,
            hintText = hintText
        )
    }
}

/**
 * Generator & clue calculator for «جدول تصویری نونگرام (Nonogram / Picross)».
 * 120 deterministic adult visual-logic puzzles (5×5, 6×6, 7×7, 8×8, 10×10).
 */
object NonogramGenerator {

    private data class ArtBlueprint(
        val title: String,
        val accentHex: Long,
        val rows: List<String>
    )

    private val curated5x5 = listOf(
        ArtBlueprint("قلب سرخ", 0xFFE11D48, listOf("01010", "11111", "11111", "01110", "00100")),
        ArtBlueprint("تاج شاهی", 0xFFD97706, listOf("10101", "11111", "11111", "10101", "11111")),
        ArtBlueprint("ساعت شنی", 0xFF0284C7, listOf("11111", "01110", "00100", "01110", "11111")),
        ArtBlueprint("الماس درخشان", 0xFF7C3AED, listOf("00100", "01110", "11111", "01110", "00100")),
        ArtBlueprint("کلبه کوهستانی", 0xFF059669, listOf("00100", "01110", "11111", "10101", "11111")),
        ArtBlueprint("فنجان قهوه", 0xFF92400E, listOf("11110", "11111", "11110", "01100", "11111")),
        ArtBlueprint("صلیب هلالی", 0xFF0D9488, listOf("01110", "11011", "10101", "11011", "01110")),
        ArtBlueprint("درخت کاج", 0xFF15803D, listOf("00100", "01110", "11111", "01110", "00100")),
        ArtBlueprint("جام قهرمانی", 0xFFCA8A04, listOf("11111", "11111", "01110", "00100", "01110")),
        ArtBlueprint("پیکان صعودی", 0xFF2563EB, listOf("00100", "01110", "10101", "00100", "00100")),
        ArtBlueprint("ستاره چهارپر", 0xFF9333EA, listOf("00100", "01110", "11011", "01110", "00100")),
        ArtBlueprint("فانوس شب", 0xFFEA580C, listOf("01110", "00100", "01110", "11011", "01110")),
        ArtBlueprint("گل لاله", 0xFFDB2777, listOf("10101", "11111", "01110", "00100", "01110")),
        ArtBlueprint("لنگر کشتی", 0xFF1D4ED8, listOf("00100", "01110", "00100", "10101", "01110")),
        ArtBlueprint("چتر بارانی", 0xFF0891B2, listOf("01110", "11111", "00100", "00100", "01100"))
    )

    private val curated6x6 = listOf(
        ArtBlueprint("پروانه بهاری", 0xFF9333EA, listOf("110011", "111111", "011110", "111111", "110011", "010010")),
        ArtBlueprint("برج قلعه", 0xFF4F46E5, listOf("101101", "111111", "011110", "010010", "011110", "111111")),
        ArtBlueprint("ماهی طلایی", 0xFFEA580C, listOf("001100", "101110", "111111", "111111", "101110", "001100")),
        ArtBlueprint("کشتی بادبانی", 0xFF0284C7, listOf("001000", "001100", "001110", "001111", "111111", "011110")),
        ArtBlueprint("کلید طلایی", 0xFFD97706, listOf("011100", "100010", "011100", "001000", "001111", "001001")),
        ArtBlueprint("هلال ماه", 0xFF4338CA, listOf("001111", "011100", "111000", "111000", "011100", "001111")),
        ArtBlueprint("سپر شوالیه", 0xFF0F766E, listOf("111111", "101101", "111111", "011110", "001100", "001100")),
        ArtBlueprint("قفل دیجیتال", 0xFFE11D48, listOf("011110", "010010", "111111", "110011", "110011", "111111")),
        ArtBlueprint("گل آفتابگردان", 0xFFCA8A04, listOf("011110", "110011", "101101", "101101", "110011", "011110")),
        ArtBlueprint("ساعت دیواری", 0xFF059669, listOf("011110", "100101", "100101", "100111", "100001", "011110"))
    )

    private val curated7x7 = listOf(
        ArtBlueprint("ستاره هشت‌پر", 0xFF7C3AED, listOf("0001000", "0101010", "0011100", "1110111", "0011100", "0101010", "0001000")),
        ArtBlueprint("گنبد فیروزه‌ای", 0xFF0891B2, listOf("0001000", "0011100", "0111110", "0110110", "1111111", "1010101", "1111111")),
        ArtBlueprint("گربه کنجکاو", 0xFFDB2777, listOf("1000001", "1100011", "1111111", "1011101", "1110111", "0111110", "0011100")),
        ArtBlueprint("موشک فضایی", 0xFF2563EB, listOf("0001000", "0011100", "0010100", "0111110", "1111111", "1011101", "0010100")),
        ArtBlueprint("جغد دانا", 0xFF92400E, listOf("1100011", "1111111", "1011101", "1110111", "0111110", "0101010", "0110110")),
        ArtBlueprint("قلب بالدار", 0xFFE11D48, listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000", "0010100")),
        ArtBlueprint("سرو ایرانی", 0xFF15803D, listOf("0001000", "0011100", "0111110", "0111110", "1111111", "0011100", "0111110")),
        ArtBlueprint("شمعدان عتیقه", 0xFFD97706, listOf("1001001", "1001001", "1111111", "0001000", "0001000", "0011100", "0111110"))
    )

    private val curated8x8 = listOf(
        ArtBlueprint("تاج جواهرنشان", 0xFFD97706, listOf(
            "10011001", "11011011", "11111111", "10111101",
            "11111111", "01111110", "01100110", "11111111"
        )),
        ArtBlueprint("فانوس دریایی", 0xFF0284C7, listOf(
            "00011000", "00111100", "01100110", "00111100",
            "00111100", "01111110", "01100110", "11111111"
        )),
        ArtBlueprint("پروانه سلطنتی", 0xFF9333EA, listOf(
            "11000011", "11100111", "11111111", "01111110",
            "01111110", "11111111", "11100111", "11000011"
        )),
        ArtBlueprint("کشتی اقیانوس", 0xFF0F766E, listOf(
            "00010000", "00011000", "00011100", "00011110",
            "00010000", "11111111", "01111110", "00111100"
        )),
        ArtBlueprint("روباه کویر", 0xFFEA580C, listOf(
            "11000011", "11100111", "11111111", "10111101",
            "11111111", "01100110", "00111100", "00011000"
        )),
        ArtBlueprint("الماس تراش‌خورده", 0xFF4F46E5, listOf(
            "00111100", "01111110", "11100111", "11111111",
            "01111110", "00111100", "00011000", "00011000"
        )),
        ArtBlueprint("خورشید تابان", 0xFFCA8A04, listOf(
            "10011001", "01011010", "00111100", "11111111",
            "11111111", "00111100", "01011010", "10011001"
        )),
        ArtBlueprint("دروازه کاخ", 0xFF059669, listOf(
            "11011011", "11111111", "01111110", "01100110",
            "01100110", "01100110", "11100111", "11111111"
        ))
    )

    private val curated10x10 = listOf(
        ArtBlueprint("کاخ افسانه‌ای", 0xFF4F46E5, listOf(
            "1010000101", "1110000111", "1110110111", "1111111111",
            "0111111110", "0111001110", "0111001110", "0111001110",
            "1111001111", "1111111111"
        )),
        ArtBlueprint("سیمرغ اسطوره‌ای", 0xFFE11D48, listOf(
            "0000110000", "0001111000", "1100110011", "1111111111",
            "1111111111", "0111111110", "0011111100", "0001111000",
            "0011001100", "0110000110"
        )),
        ArtBlueprint("ساعت شنی سلطنتی", 0xFF0284C7, listOf(
            "1111111111", "1111111111", "0111111110", "0011111100",
            "0001111000", "0001111000", "0011001100", "0110110110",
            "1111111111", "1111111111"
        )),
        ArtBlueprint("گل رز ماندگار", 0xFFDB2777, listOf(
            "0011111100", "0110110110", "1101111011", "1111001111",
            "0111111110", "0011111100", "0000110000", "0011110000",
            "0000111100", "0000110000"
        )),
        ArtBlueprint("کشتی بادبانی بزرگ", 0xFF0D9488, listOf(
            "0000100000", "0000110000", "0000111000", "0000111100",
            "0000111110", "0000100000", "1111111111", "0111111110",
            "0011111100", "0101010101"
        )),
        ArtBlueprint("تلسکوپ و ستارگان", 0xFF7C3AED, listOf(
            "1000000001", "0000001110", "0000011100", "0000111000",
            "0001110000", "0011100000", "0111010000", "0110010000",
            "0000111000", "0011000110"
        ))
    )

    private val proceduralTitles = listOf(
        "نقش کاشی فیروزه", "گره‌چینی اصیل", "ترنج ایرانی", "موزاییک هندسی",
        "نشان کهن", "تابلوی مینیمال", "سنگ‌نگاره باستانی", "الگوی کریستالی",
        "شمسه خورشیدی", "چلیپای هنری", "نقش قالیچه", "طرح اسلیمی مدرن",
        "پیکسل آرت معماری", "نشان ستاره‌شناسی", "مهر سلطنتی", "آینه‌کاری هندسی"
    )

    private val accentPalette = listOf(
        0xFFE11D48L, 0xFF4F46E5L, 0xFF0284C7L, 0xFF059669L,
        0xFFD97706L, 0xFF7C3AEDL, 0xFF0F766EL, 0xFFEA580CL
    )

    fun generateAllLevels(): List<EntertainmentPuzzleEntity> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<EntertainmentPuzzleEntity>()

        for (diff in EntertainmentDifficulty.entries) {
            for (level in 1..30) {
                val data = buildPuzzleFor(diff, level)
                result.add(
                    EntertainmentPuzzleEntity(
                        id = "nonogram_${diff.key.lowercase()}_$level",
                        gameType = EntertainmentGameType.NONOGRAM.key,
                        difficulty = diff.key,
                        levelNumber = level,
                        title = "نونگرام ${diff.titleFa} - مرحله $level",
                        payloadJson = serializePayload(data),
                        updatedAtEpochMs = now
                    )
                )
            }
        }
        return result
    }

    fun buildPuzzleFor(difficulty: EntertainmentDifficulty, level: Int): NonogramPuzzleData {
        val blueprint: ArtBlueprint? = when (difficulty) {
            EntertainmentDifficulty.BEGINNER -> curated5x5.getOrNull(level - 1)
            EntertainmentDifficulty.INTERMEDIATE -> {
                if (level <= curated6x6.size) curated6x6[level - 1]
                else curated7x7.getOrNull(level - curated6x6.size - 1)
            }
            EntertainmentDifficulty.ADVANCED -> curated8x8.getOrNull(level - 1)
            EntertainmentDifficulty.MASTER -> curated10x10.getOrNull(level - 1)
        }

        if (blueprint != null) {
            return NonogramPuzzleData(
                size = blueprint.rows.size,
                artworkTitle = blueprint.title,
                accentHex = blueprint.accentHex,
                solutionRows = blueprint.rows
            )
        }

        val size = when (difficulty) {
            EntertainmentDifficulty.BEGINNER -> 5
            EntertainmentDifficulty.INTERMEDIATE -> if (level % 2 == 0) 7 else 6
            EntertainmentDifficulty.ADVANCED -> 8
            EntertainmentDifficulty.MASTER -> 10
        }

        val rows = generateStructuredSymmetricGrid(
            size = size,
            seed = difficulty.order * 31_337 + level * 997 + 503
        )
        val title = "${proceduralTitles[(difficulty.order * 5 + level) % proceduralTitles.size]} #$level"
        val accent = accentPalette[(difficulty.order + level) % accentPalette.size]

        return NonogramPuzzleData(
            size = size,
            artworkTitle = title,
            accentHex = accent,
            solutionRows = rows
        )
    }

    /**
     * Generates a visually pleasing, horizontally symmetric (or 4-way symmetric) Nonogram pattern
     * with ~50-62% fill density and GUARANTEED >= 1 filled cell and >= 1 empty cell on nearly all
     * lines (always >= 1 filled cell per row & column so there are never dead/trivial zero lines).
     */
    private fun generateStructuredSymmetricGrid(size: Int, seed: Int): List<String> {
        val rng = Random(seed)
        val grid = Array(size) { BooleanArray(size) }
        val fourWay = seed % 3 == 0
        val halfCol = (size + 1) / 2
        val halfRow = if (fourWay) (size + 1) / 2 else size

        // Seed connected artistic clusters on the left half, then mirror
        val fillProb = if (size <= 6) 0.58 else 0.54
        for (r in 0 until halfRow) {
            for (c in 0 until halfCol) {
                val neighborFilled = (r > 0 && grid[r - 1][c]) || (c > 0 && grid[r][c - 1])
                val p = if (neighborFilled) fillProb + 0.14 else fillProb - 0.10
                val filled = rng.nextDouble() < p
                grid[r][c] = filled
                grid[r][size - 1 - c] = filled
                if (fourWay) {
                    grid[size - 1 - r][c] = filled
                    grid[size - 1 - r][size - 1 - c] = filled
                }
            }
        }

        // Guarantee every row has at least 1 filled cell and isn't trivially all-filled
        for (r in 0 until size) {
            val count = grid[r].count { it }
            if (count == 0) {
                val c = rng.nextInt(halfCol)
                grid[r][c] = true
                grid[r][size - 1 - c] = true
            } else if (count == size && r % 2 == 1) {
                grid[r][0] = false
                grid[r][size - 1] = false
            }
        }

        // Guarantee every column has at least 1 filled cell
        for (c in 0 until size) {
            val count = (0 until size).count { r -> grid[r][c] }
            if (count == 0) {
                val r = rng.nextInt(size)
                grid[r][c] = true
                grid[r][size - 1 - c] = true
            }
        }

        return grid.map { row ->
            buildString(size) {
                for (cell in row) append(if (cell) '1' else '0')
            }
        }
    }

    /**
     * Computes standard Nonogram run-length clues for a single row or column.
     * Example: [true, true, false, true] -> [2, 1]. Empty line -> [0].
     */
    fun computeLineClues(bits: List<Boolean>): List<Int> {
        val clues = mutableListOf<Int>()
        var run = 0
        for (b in bits) {
            if (b) {
                run++
            } else if (run > 0) {
                clues.add(run)
                run = 0
            }
        }
        if (run > 0) clues.add(run)
        return if (clues.isEmpty()) listOf(0) else clues
    }

    fun computeRowClues(solutionRows: List<String>): List<List<Int>> {
        return solutionRows.map { row -> computeLineClues(row.map { it == '1' }) }
    }

    fun computeColClues(solutionRows: List<String>): List<List<Int>> {
        val size = solutionRows.size
        return (0 until size).map { col ->
            computeLineClues((0 until size).map { row -> solutionRows[row].getOrNull(col) == '1' })
        }
    }

    fun serializePayload(data: NonogramPuzzleData): String {
        val rowsJoined = data.solutionRows.joinToString(",") { "\"${BrainGameJsonUtil.escape(it)}\"" }
        return buildString {
            append('{')
            append("\"size\":${data.size},")
            append("\"artworkTitle\":\"${BrainGameJsonUtil.escape(data.artworkTitle)}\",")
            append("\"accentHex\":${data.accentHex},")
            append("\"solutionRows\":[$rowsJoined]")
            append('}')
        }
    }

    fun parsePayload(json: String): NonogramPuzzleData? {
        if (json.isBlank()) return null
        val size = BrainGameJsonUtil.extractInt(json, "size") ?: return null
        val artworkTitle = BrainGameJsonUtil.extractString(json, "artworkTitle").orEmpty().ifBlank { "تصویر پنهان" }
        val accentHex = BrainGameJsonUtil.extractLong(json, "accentHex") ?: 0xFF4F46E5L
        val rows = BrainGameJsonUtil.extractStringArray(json, "solutionRows") ?: return null
        if (rows.size != size || rows.any { it.length != size }) return null
        return NonogramPuzzleData(
            size = size,
            artworkTitle = artworkTitle,
            accentHex = accentHex,
            solutionRows = rows
        )
    }
}
