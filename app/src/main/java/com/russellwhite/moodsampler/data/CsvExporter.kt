package com.russellwhite.moodsampler.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptWithSample
import com.russellwhite.moodsampler.scheduling.DeviceClock
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvExporter(
    private val context: Context,
    private val db: AppDatabase,
    private val clock: Clock = DeviceClock,
) {
    /** Write all concluded prompts to a CSV in cacheDir and return a shareable content Uri. */
    suspend fun export(): Uri = withContext(Dispatchers.IO) {
        val rows = db.scheduledPromptDao().allConcludedPrompts()
        val labels = db.categoryDao().getAll().associate { it.id to it.label }
        val csv = toCsv(rows, labels, clock.zone)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "moodsampler-export-${LocalDate.now(clock)}.csv")
        file.writeText(csv)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    companion object {
        private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        /** Column order of the export; `activities` joins several labels with "; ". */
        const val HEADER =
            "scheduled_at_iso,answered_at_iso,status,skip_reason,latency_s,valence,arousal,pain,activities,location,social,note"

        /**
         * Pure CSV formatting (unit-tested). Missed prompts are included with
         * empty measure columns so exported response rates stay honest.
         */
        fun toCsv(rows: List<PromptWithSample>, labels: Map<Long, String>, zone: ZoneId): String {
            val sb = StringBuilder()
            sb.append(HEADER).append("\r\n")
            rows.forEach { row ->
                val p = row.prompt
                val s = row.sample?.sample
                val activityIds = row.sample?.activityIds.orEmpty()
                val latency = p.answeredAtEpochMs?.let { (it - p.scheduledAtEpochMs) / 1000 }
                sb.append(
                    listOf(
                        iso(p.scheduledAtEpochMs, zone),
                        p.answeredAtEpochMs?.let { iso(it, zone) } ?: "",
                        p.status.name,
                        p.skipReason ?: "",
                        latency?.toString() ?: "",
                        s?.valence?.toString() ?: "",
                        s?.arousal?.toString() ?: "",
                        s?.pain?.toString() ?: "",
                        activityIds.mapNotNull { labels[it] }.joinToString("; "),
                        s?.locationId?.let { labels[it] } ?: "",
                        s?.socialId?.let { labels[it] } ?: "",
                        s?.note ?: "",
                    ).joinToString(",") { quote(it) }
                )
                sb.append("\r\n")
            }
            return sb.toString()
        }

        private fun iso(epochMs: Long, zone: ZoneId): String =
            ISO.format(Instant.ofEpochMilli(epochMs).atZone(zone))

        /** RFC 4180: quote fields containing commas, quotes, or newlines; double inner quotes. */
        private fun quote(field: String): String =
            if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"${field.replace("\"", "\"\"")}\""
            } else {
                field
            }
    }
}
