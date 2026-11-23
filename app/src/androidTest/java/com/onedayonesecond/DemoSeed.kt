package com.onedayonesecond

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Not a test: fills an emulator with a made-up diary so the recap screens can be seen
 * and photographed. Skipped unless asked for by name:
 *
 * ```
 * adb shell am instrument -w -e seed true -e class com.onedayonesecond.DemoSeed \
 *   com.onedayonesecond.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * The week of 16 to 22 November 2026 gets a second, a mood and entries every day; the
 * week before gets four days; October gets enough scattered days for past recaps to be
 * offered; and the week of 9 November gets a recap in the old layout.
 */
@RunWith(AndroidJUnit4::class)
class DemoSeed {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val zone = ZoneId.systemDefault()

    private data class Day(
        val date: String,
        val mood: Mood,
        val place: String,
        val caption: String,
        val color: Int,
        /** Time of day to words. */
        val entries: List<Pair<String, String>>,
    )

    private val week = listOf(
        Day(
            "2026-11-16", Mood.GOOD, "Senayan, Jakarta Selatan", "gym pagi", 0xFFB8527A.toInt(),
            listOf(
                "06:40" to "Gym pagi jam 6, leg day. Badan pegel tapi kepala jernih banget.",
                "21:10" to "Kerja lancar hari ini, akhirnya nemu flow lagi buat refactor payment module. " +
                    "Feels good to be in the zone again.",
            ),
        ),
        Day(
            "2026-11-17", Mood.GREAT, "Senayan, Jakarta Selatan", "PR deadlift", 0xFFAE4A6B.toInt(),
            listOf(
                "07:05" to "Gym lagi, PR deadlift 80kg! Nggak nyangka bisa.",
                "22:00" to "Standup tadi Raka minta review PR checkout sebelum Jumat. Malamnya makan sate sama Ibu, " +
                    "dia cerita soal arisan dan kebun tomatnya.",
            ),
        ),
        Day(
            "2026-11-18", Mood.ROUGH, "Kuningan, Jakarta Selatan", "slide jam 1 pagi", 0xFF6E2445.toInt(),
            listOf(
                "01:20" to "Cuma tidur 4 jam gara-gara mikirin slide Tech Cantik. Prepare talk sampai jam 1 pagi. " +
                    "Honestly I'm scared, ngomong di depan 100 orang itu bukan aku banget.",
                "19:45" to "Bug di checkout muncul lagi di production, belum ketemu penyebabnya. Capek.",
            ),
        ),
        Day(
            "2026-11-19", Mood.NEUTRAL, "Kuningan, Jakarta Selatan", "rehearsal", 0xFFE58AAB.toInt(),
            listOf(
                "20:30" to "Rehearsal talk sama Dinda via Zoom. Dia bilang opening-ku terlalu panjang. " +
                    "Review PR Raka belum aku kerjain, I keep pushing it.",
            ),
        ),
        Day(
            "2026-11-20", Mood.GOOD, "Senayan, Jakarta Selatan", "jalan sore GBK", 0xFFF7BDD0.toInt(),
            listOf(
                "18:15" to "Skip gym, jalan sore di GBK aja. Aku sadar aku lebih suka comfort: rumah, kopi, playlist lama. " +
                    "Tapi besok harus adrenaline mode.",
            ),
        ),
        Day(
            "2026-11-21", Mood.GREAT, "Kemang, Jakarta Selatan", "Tech Cantik!", 0xFFAE4A6B.toInt(),
            listOf(
                "16:50" to "Akhirnya ngomong di Tech Cantik! Tangan gemeteran di 2 menit pertama, terus lancar. " +
                    "Ada 3 orang yang nyamperin habis talk dan nanya soal design system kita.",
            ),
        ),
        Day(
            "2026-11-22", Mood.GOOD, "Kemang, Jakarta Selatan", "nulis blog", 0xFFF9D3E0.toInt(),
            listOf(
                "10:30" to "Nulis recap talk kemarin di blog. Pelajaran: opening pendek itu penting.",
                "21:40" to "Belum mutusin mau ambil tawaran jadi speaker lagi bulan depan atau nggak. " +
                    "Comfort atau adrenaline, lagi-lagi.",
            ),
        ),
    )

    private val weekBefore = listOf(
        Day(
            "2026-11-09", Mood.BAD, "Kuningan, Jakarta Selatan", "deadline", 0xFFB8527A.toInt(),
            listOf("21:00" to "Meeting dari pagi sampai sore, terus deadline dimajuin ke Rabu."),
        ),
        Day(
            "2026-11-11", Mood.NEUTRAL, "Kuningan, Jakarta Selatan", "", 0xFFE58AAB.toInt(),
            listOf("22:10" to "Deck akhirnya ke-submit jam 9 malam. Lega sih, tapi rasanya kosong aja."),
        ),
        Day(
            "2026-11-13", Mood.GOOD, "Kemang, Jakarta Selatan", "makan malam sama Ibu", 0xFFF7BDD0.toInt(),
            listOf("20:45" to "Makan malam sama Ibu di Kemang. For the first time this week I wasn't thinking about work."),
        ),
        Day(
            "2026-11-14", Mood.GREAT, "Senayan, Jakarta Selatan", "lari pagi", 0xFFF9D3E0.toInt(),
            listOf("08:00" to "Lari pagi 5K di GBK, terus ngopi. It was the first quiet morning in weeks."),
        ),
    )

    @Test
    fun seed() {
        assumeTrue("Run with -e seed true", InstrumentationRegistry.getArguments().getString("seed") == "true")
        val container = (context.applicationContext as OneDayApp).container
        runBlocking {
            container.journalPrefs.setName("Eza")
            for (day in week + weekBefore) put(container, day)

            // October: scattered days, enough for a few past weeks and the month to be offered.
            val october = listOf(5, 6, 8, 12, 14, 15, 20, 21, 23, 27).map { LocalDate.of(2026, 10, it) }
            october.forEachIndexed { i, date ->
                container.journal.putEntry(
                    Entry(
                        date = date,
                        createdAt = date.atTime(LocalTime.of(21, 0)).atZone(zone).toInstant().toEpochMilli(),
                        type = EntryType.WRITTEN,
                        text = OCTOBER[i % OCTOBER.size],
                    ),
                )
                container.journal.setMood(date, Mood.entries[(i * 3) % 5])
            }

            // A recap written before this version, in the old layout.
            container.journal.saveRecap(
                Recap(
                    key = "week-2026-11-09",
                    title = "Dari deadline ke pagi yang tenang",
                    quote = "It was the first quiet morning in weeks.",
                    quoteDate = LocalDate.of(2026, 11, 14),
                    json = """{"whatHappened":[{"date":"2026-11-09","text":"Meeting seharian; deadline dimajukan ke Rabu."},
                        {"date":"2026-11-14","text":"Lari pagi 5K di GBK, lalu ngopi."}],
                        "note":"Capek itu valid, pelan-pelan aja.","changes":[],"tries":["Tidur lebih awal."]}""",
                    createdAt = LocalDate.of(2026, 11, 15).atTime(21, 5).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
        }
    }

    private suspend fun put(container: AppContainer, day: Day) {
        val date = LocalDate.parse(day.date)
        for ((time, text) in day.entries) {
            container.journal.putEntry(
                Entry(
                    date = date,
                    createdAt = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli(),
                    type = EntryType.WRITTEN,
                    text = text,
                    placeName = day.place,
                ),
            )
        }
        container.journal.setMood(date, day.mood)
        container.journal.setPlace(date, day.place)
        if (container.repository.clipFor(date) == null) {
            container.repository.saveClip(date, clip(date, day), 1000, day.caption)
        }
    }

    /** A one-second portrait video of a coloured card with the day of the month on it. */
    private fun clip(date: LocalDate, day: Day): File {
        val image = File(context.cacheDir, "seed-${day.date}.png")
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 720f, 1280f, day.color, Color.WHITE, Shader.TileMode.CLAMP)
            drawRect(0f, 0f, 720f, 1280f, paint)
            paint.shader = null
            paint.color = Color.WHITE
            paint.textSize = 220f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            drawText(date.dayOfMonth.toString(), 360f, 700f, paint)
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val output = File(context.cacheDir, "seed-${day.date}.mp4")
        output.delete()
        val done = CountDownLatch(1)
        var error: Exception? = null
        instrumentation.runOnMainSync {
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        error = exportException
                        done.countDown()
                    }
                })
                .build()
            val item = MediaItem.Builder().setUri(Uri.fromFile(image)).setImageDurationMs(1000).build()
            transformer.start(EditedMediaItem.Builder(item).setFrameRate(30).build(), output.absolutePath)
        }
        check(done.await(60, TimeUnit.SECONDS)) { "Making the clip for ${day.date} timed out" }
        error?.let { throw it }
        return output
    }

    private companion object {
        val OCTOBER = listOf(
            "Sprint planning, terus ngoding sampai sore.",
            "Ke Bandung sama anak-anak kantor lama. Dingin tapi seru.",
            "Demam, izin kerja, tidur seharian.",
            "Balik kerja, inbox numpuk. Satu-satu aja.",
            "Presentasi ke klien lancar. Bos bilang good job.",
        )
    }
}
