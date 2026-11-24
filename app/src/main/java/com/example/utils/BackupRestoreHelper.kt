package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.CustomFolderEntity
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.data.model.ExpenseEntity
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileOutputStream

/**
 * Handles complete, lossless JSON backup creation and restoration (v2, backward-compatible with v1):
 * - All expenses and custom folders (AppDatabase)
 * - Attached receipt image files (embedded Base64 + automatic relinking into filesDir/receipts)
 * - Active user preferences (FontScaleHelper, installment reminders, expense suggestions, GitHub update config)
 * - Active EntertainmentDatabase puzzle progress (completed levels, best times, saved boards)
 */
object BackupRestoreHelper {

    const val CURRENT_BACKUP_VERSION = 2

    private const val MAX_SINGLE_RECEIPT_BYTES = 3 * 1024 * 1024
    private const val MAX_TOTAL_RECEIPTS_BYTES = 18 * 1024 * 1024

    val BACKED_UP_PREF_FILES = listOf(
        "app_font_scale_prefs",
        "installment_reminders_prefs",
        "expense_suggestions_prefs",
        "github_update_prefs",
        "sticky_notes_prefs"
    )

    private val moshiAnyAdapter = Moshi.Builder().build().adapter(Any::class.java).lenient()

    data class EntertainmentProgressEntry(
        val id: String,
        val savedProgressJson: String,
        val isCompleted: Boolean,
        val bestTimeSeconds: Int,
        val mistakesCount: Int,
        val updatedAtEpochMs: Long
    )

    data class TypedPrefValue(
        val type: String, // "STRING", "BOOLEAN", "INT", "LONG", "FLOAT"
        val value: String
    )

    data class BackupData(
        val version: Int,
        val appName: String,
        val backupDateShamsi: String,
        val timestamp: Long,
        val customFolders: List<CustomFolderEntity>,
        val expenses: List<ExpenseEntity>,
        val receiptImagesBase64: Map<String, String> = emptyMap(),
        val preferences: Map<String, Map<String, TypedPrefValue>> = emptyMap(),
        val entertainmentProgress: List<EntertainmentProgressEntry> = emptyList()
    )

    /**
     * Serializes expenses, custom folders, attached receipt images, user preferences,
     * and entertainment puzzle progress into a v2 JSON backup string.
     */
    fun createBackupJson(
        expenses: List<ExpenseEntity>,
        customFolders: List<CustomFolderEntity>,
        context: Context? = null,
        entertainmentPuzzles: List<EntertainmentPuzzleEntity> = emptyList()
    ): String {
        val now = System.currentTimeMillis()
        val shamsiDate = PersianDateHelper.getShamsiDate(now).format()

        val foldersList = customFolders.map { folder ->
            linkedMapOf<String, Any?>(
                "id" to folder.id,
                "name" to folder.name,
                "colorHex" to folder.colorHex,
                "isAnnualFolder" to folder.isAnnualFolder,
                "createdAt" to folder.createdAt
            )
        }

        // Collect attached receipt images safely (Base64) keyed by filename
        val receiptImagesMap = linkedMapOf<String, String>()
        var totalReceiptBytes = 0
        val expensesList = expenses.map { exp ->
            val rawPath = exp.receiptImagePath?.trim().orEmpty()
            var receiptFileName = ""
            if (rawPath.isNotBlank()) {
                val f = File(rawPath)
                receiptFileName = f.name
                if (f.exists() && f.isFile && f.length() in 1L..MAX_SINGLE_RECEIPT_BYTES.toLong()) {
                    if (!receiptImagesMap.containsKey(receiptFileName) &&
                        totalReceiptBytes + f.length() <= MAX_TOTAL_RECEIPTS_BYTES
                    ) {
                        runCatching {
                            val bytes = f.readBytes()
                            totalReceiptBytes += bytes.size
                            receiptImagesMap[receiptFileName] = encodeBase64Compat(bytes)
                        }
                    }
                }
            }

            linkedMapOf<String, Any?>(
                "id" to exp.id,
                "title" to exp.title,
                "amount" to exp.amount,
                "isAnnual" to exp.isAnnual,
                "customFolder" to (exp.customFolder ?: ""),
                "timestamp" to exp.timestamp,
                "shamsiYear" to exp.shamsiYear,
                "shamsiMonth" to exp.shamsiMonth,
                "shamsiDay" to exp.shamsiDay,
                "shamsiDateString" to exp.shamsiDateString,
                "note" to exp.note,
                "receiptImagePath" to rawPath,
                "receiptFileName" to receiptFileName
            )
        }

        // Collect active user SharedPreferences
        val prefsSnapshot = linkedMapOf<String, Map<String, Map<String, String>>>()
        if (context != null) {
            for (prefName in BACKED_UP_PREF_FILES) {
                runCatching {
                    val allEntries = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).all
                    if (allEntries.isNotEmpty()) {
                        val bucket = linkedMapOf<String, Map<String, String>>()
                        for ((k, v) in allEntries) {
                            when (v) {
                                is String -> bucket[k] = mapOf("type" to "STRING", "value" to v)
                                is Boolean -> bucket[k] = mapOf("type" to "BOOLEAN", "value" to v.toString())
                                is Int -> bucket[k] = mapOf("type" to "INT", "value" to v.toString())
                                is Long -> bucket[k] = mapOf("type" to "LONG", "value" to v.toString())
                                is Float -> bucket[k] = mapOf("type" to "FLOAT", "value" to v.toString())
                            }
                        }
                        if (bucket.isNotEmpty()) {
                            prefsSnapshot[prefName] = bucket
                        }
                    }
                }
            }
        }

        // Collect solved or in-progress EntertainmentDatabase records
        val progressList = entertainmentPuzzles
            .filter { it.isCompleted || it.savedProgressJson.isNotBlank() || it.bestTimeSeconds > 0 }
            .map { p ->
                linkedMapOf<String, Any?>(
                    "id" to p.id,
                    "savedProgressJson" to p.savedProgressJson,
                    "isCompleted" to p.isCompleted,
                    "bestTimeSeconds" to p.bestTimeSeconds,
                    "mistakesCount" to p.mistakesCount,
                    "updatedAtEpochMs" to p.updatedAtEpochMs
                )
            }

        val root = linkedMapOf<String, Any?>(
            "version" to CURRENT_BACKUP_VERSION,
            "appName" to "مدیریت مالی و هزینه‌ها",
            "backupDateShamsi" to shamsiDate,
            "backupTimestamp" to now,
            "customFolders" to foldersList,
            "expenses" to expensesList,
            "receiptImages" to receiptImagesMap,
            "preferences" to prefsSnapshot,
            "entertainmentProgress" to progressList
        )

        return moshiAnyAdapter.indent("  ").toJson(root)
    }

    /**
     * Writes the backup JSON to cache/backups and opens Android share sheet.
     */
    fun shareBackupFile(context: Context, jsonString: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val shamsiClean = PersianDateHelper.getShamsiDate(now).format().replace("/", "-")
            val timeClean = PersianDateHelper.getTimeFormatted(now).replace(":", "-")
            val fileName = "Backup_Hazineh_${shamsiClean}_${timeClean}.json"

            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(backupsDir, fileName)

            FileOutputStream(backupFile).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "پشتیبان کامل برنامه مدیریت هزینه")
                putExtra(Intent.EXTRA_TEXT, "فایل پشتیبان کامل داده‌ها، رسیدها و تنظیمات - تاریخ $shamsiClean")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "ذخیره یا اشتراک‌گذاری فایل پشتیبان (بکاپ)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطا در ایجاد یا اشتراک‌گذاری فایل پشتیبان: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Parses and validates both Version 1 and Version 2 backup JSON strings cleanly.
     */
    fun parseBackupJson(jsonString: String): BackupData? {
        return try {
            val root = moshiAnyAdapter.fromJson(jsonString) as? Map<*, *> ?: return null
            val version = root.optInt("version", 1)
            val appName = root.optString("appName", "مدیریت مالی و هزینه‌ها")
            val backupDateShamsi = root.optString("backupDateShamsi", "")
            val timestamp = root.optLong("backupTimestamp", System.currentTimeMillis())

            val customFoldersList = mutableListOf<CustomFolderEntity>()
            val foldersArray = root["customFolders"] as? List<*>
            if (foldersArray != null) {
                for (item in foldersArray) {
                    val fObj = item as? Map<*, *> ?: continue
                    val name = fObj.optString("name", "").trim()
                    if (name.isNotBlank()) {
                        customFoldersList.add(
                            CustomFolderEntity(
                                id = 0L,
                                name = name,
                                colorHex = fObj.optLong("colorHex", 0xFF10B981),
                                isAnnualFolder = fObj.optBoolean("isAnnualFolder", false),
                                createdAt = fObj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val expensesList = mutableListOf<ExpenseEntity>()
            val expensesArray = root["expenses"] as? List<*>
            if (expensesArray != null) {
                for (item in expensesArray) {
                    val eObj = item as? Map<*, *> ?: continue
                    val title = eObj.optString("title", "").trim()
                    val amount = eObj.optLong("amount", 0L)
                    val dateStr = eObj.optString("shamsiDateString", "")
                    val isAnnual = eObj.optBoolean("isAnnual", false)
                    val customFolderRaw = eObj.optString("customFolder", "").trim()
                    val customFolder = customFolderRaw.takeIf { it.isNotBlank() }
                    val note = eObj.optString("note", "")
                    val receiptPathRaw = eObj.optString("receiptImagePath", "").trim()
                    val receiptFileName = eObj.optString("receiptFileName", "").trim()
                    val resolvedReceipt = when {
                        receiptPathRaw.isNotBlank() -> receiptPathRaw
                        receiptFileName.isNotBlank() -> receiptFileName
                        else -> null
                    }
                    val ts = eObj.optLong("timestamp", System.currentTimeMillis())

                    var y = eObj.optInt("shamsiYear", 0)
                    var m = eObj.optInt("shamsiMonth", 0)
                    var d = eObj.optInt("shamsiDay", 0)

                    if ((y == 0 || m == 0 || d == 0) && dateStr.isNotBlank()) {
                        val parts = PersianDateHelper.toEnglishDigits(dateStr).split("/")
                        y = parts.getOrNull(0)?.toIntOrNull() ?: PersianDateHelper.getShamsiDate().year
                        m = parts.getOrNull(1)?.toIntOrNull() ?: PersianDateHelper.getShamsiDate().month
                        d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                    }

                    if (title.isNotBlank()) {
                        expensesList.add(
                            ExpenseEntity(
                                id = 0L,
                                title = title,
                                amount = amount,
                                isAnnual = isAnnual,
                                customFolder = customFolder,
                                timestamp = ts,
                                shamsiYear = y,
                                shamsiMonth = m,
                                shamsiDay = d,
                                shamsiDateString = dateStr,
                                note = note,
                                receiptImagePath = resolvedReceipt
                            )
                        )
                    }
                }
            }

            // Optional v2 embedded receipt images
            val receiptImages = linkedMapOf<String, String>()
            val rawReceipts = root["receiptImages"] as? Map<*, *>
            if (rawReceipts != null) {
                for ((k, v) in rawReceipts) {
                    val fileName = k?.toString()?.trim().orEmpty()
                    val b64 = v?.toString()?.trim().orEmpty()
                    if (fileName.isNotBlank() && b64.isNotBlank() && !fileName.contains("/") && !fileName.contains("..")) {
                        receiptImages[fileName] = b64
                    }
                }
            }

            // Optional v2 user preferences
            val prefsMap = linkedMapOf<String, Map<String, TypedPrefValue>>()
            val rawPrefs = root["preferences"] as? Map<*, *>
            if (rawPrefs != null) {
                for ((pNameRaw, bucketRaw) in rawPrefs) {
                    val pName = pNameRaw?.toString() ?: continue
                    if (pName !in BACKED_UP_PREF_FILES) continue
                    val bucket = bucketRaw as? Map<*, *> ?: continue
                    val parsedBucket = linkedMapOf<String, TypedPrefValue>()
                    for ((kRaw, vRaw) in bucket) {
                        val k = kRaw?.toString() ?: continue
                        val entry = vRaw as? Map<*, *> ?: continue
                        val t = entry.optString("type", "")
                        val v = entry.optString("value", "")
                        if (t.isNotBlank()) {
                            parsedBucket[k] = TypedPrefValue(type = t, value = v)
                        }
                    }
                    if (parsedBucket.isNotEmpty()) {
                        prefsMap[pName] = parsedBucket
                    }
                }
            }

            // Optional v2 entertainment puzzle progress
            val entertainmentProgress = mutableListOf<EntertainmentProgressEntry>()
            val rawProgress = root["entertainmentProgress"] as? List<*>
            if (rawProgress != null) {
                for (item in rawProgress) {
                    val pObj = item as? Map<*, *> ?: continue
                    val id = pObj.optString("id", "").trim()
                    if (id.isNotBlank()) {
                        entertainmentProgress.add(
                            EntertainmentProgressEntry(
                                id = id,
                                savedProgressJson = pObj.optString("savedProgressJson", ""),
                                isCompleted = pObj.optBoolean("isCompleted", false),
                                bestTimeSeconds = pObj.optInt("bestTimeSeconds", 0),
                                mistakesCount = pObj.optInt("mistakesCount", 0),
                                updatedAtEpochMs = pObj.optLong("updatedAtEpochMs", 0L)
                            )
                        )
                    }
                }
            }

            BackupData(
                version = version,
                appName = appName,
                backupDateShamsi = backupDateShamsi,
                timestamp = timestamp,
                customFolders = customFoldersList,
                expenses = expensesList,
                receiptImagesBase64 = receiptImages,
                preferences = prefsMap,
                entertainmentProgress = entertainmentProgress
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Restores embedded receipt images into filesDir/receipts and re-links expense paths cleanly.
     */
    fun restoreReceiptImagesAndRelink(
        context: Context,
        expenses: List<ExpenseEntity>,
        receiptImagesBase64: Map<String, String>
    ): List<ExpenseEntity> {
        if (receiptImagesBase64.isEmpty()) return expenses
        val receiptsDir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
        val restoredPathByName = HashMap<String, String>()

        for ((fileName, b64) in receiptImagesBase64) {
            if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) continue
            runCatching {
                val bytes = decodeBase64Compat(b64)
                if (bytes.isNotEmpty()) {
                    val destFile = File(receiptsDir, fileName)
                    FileOutputStream(destFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                    restoredPathByName[fileName] = destFile.absolutePath
                }
            }
        }

        return expenses.map { exp ->
            val rawPath = exp.receiptImagePath?.trim().orEmpty()
            if (rawPath.isBlank()) {
                exp
            } else {
                val fileName = File(rawPath).name
                val restoredPath = restoredPathByName[fileName]
                if (restoredPath != null) exp.copy(receiptImagePath = restoredPath) else exp
            }
        }
    }

    /**
     * Restores backed-up user SharedPreferences safely without touching caches or internal tokens.
     */
    fun restorePreferences(
        context: Context,
        preferences: Map<String, Map<String, TypedPrefValue>>
    ) {
        for ((prefName, entries) in preferences) {
            if (prefName !in BACKED_UP_PREF_FILES) continue
            runCatching {
                val editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
                for ((k, typedVal) in entries) {
                    when (typedVal.type) {
                        "STRING" -> editor.putString(k, typedVal.value)
                        "BOOLEAN" -> editor.putBoolean(k, typedVal.value.toBoolean())
                        "INT" -> typedVal.value.toIntOrNull()?.let { editor.putInt(k, it) }
                        "LONG" -> typedVal.value.toLongOrNull()?.let { editor.putLong(k, it) }
                        "FLOAT" -> typedVal.value.toFloatOrNull()?.let { editor.putFloat(k, it) }
                    }
                }
                editor.apply()
            }
        }
    }

    private fun encodeBase64Compat(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun decodeBase64Compat(b64: String): ByteArray {
        return try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(b64)
        }
    }

    private fun Map<*, *>.optString(key: String, default: String): String =
        this[key]?.toString() ?: default

    private fun Map<*, *>.optInt(key: String, default: Int): Int =
        when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<*, *>.optLong(key: String, default: Long): Long =
        when (val v = this[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }

    private fun Map<*, *>.optBoolean(key: String, default: Boolean): Boolean =
        when (val v = this[key]) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull() ?: default
            else -> default
        }
}
