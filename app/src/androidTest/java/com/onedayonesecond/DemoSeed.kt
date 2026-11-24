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
import com.onedayonesecond.journal.DatedNote
import com.onedayonesecond.journal.PeriodStats
import com.onedayonesecond.journal.RecapLetter
import com.onedayonesecond.journal.RecapThread
import com.onedayonesecond.journal.TagHash
import com.onedayonesecond.journal.TagSet
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
 * offered; the week of 9 November gets a recap in the old layout; and the week of
 * 16 November gets a letter in the new layout, with its summary. Every entry is given
 * the tags /tag would give it, so Patterns has something to count without a server.
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
                val text = OCTOBER[i % OCTOBER.size]
                val put = container.journal.putEntry(
                    Entry(
                        date = date,
                        createdAt = date.atTime(LocalTime.of(21, 0)).atZone(zone).toInstant().toEpochMilli(),
                        type = EntryType.WRITTEN,
                        text = text,
                    ),
                )
                tag(container, put)
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

            // This week's letter in the new layout, as the server would write it.
            container.journal.saveRecap(
                Recap(
                    key = "week-2026-11-16",
                    title = "The week of the Tech Cantik talk",
                    quote = "Honestly I'm scared, ngomong di depan 100 orang itu bukan aku banget.",
                    quoteDate = LocalDate.of(2026, 11, 18),
                    json = THIS_WEEK.encode(),
                    createdAt = LocalDate.of(2026, 11, 22).atTime(21, 5).atZone(zone).toInstant().toEpochMilli(),
                    schemaVersion = Recap.LAYOUT_V2,
                ),
            )
        }
    }

    /** The tags /tag would give this entry, stored against its words as they are. */
    private suspend fun tag(container: AppContainer, entry: Entry) {
        val text = entry.text ?: return
        val lower = text.lowercase()
        fun has(vararg words: String) = words.any { it in lower }
        val topics = buildList {
            if (has("kerja", "refactor", "pr ", "bug", "meeting", "deadline", "deck", "sprint", "ngoding", "inbox", "klien")) add("Work")
            if (has("talk", "tech cantik", "rehearsal", "speaker", "slide", "presentasi")) add("Public speaking")
            if (has("ibu")) add("Family")
            if (has("tidur")) add("Sleep")
            if (has("comfort")) add("Comfort vs adrenaline")
            if (has("blog")) add("Writing")
        }.take(3)
        val habits = buildList {
            if (has("gym") && !has("skip gym")) add("Gym")
            if (has("jalan sore")) add("Walking")
            if (has("lari")) add("Running")
            if (has("blog")) add("Writing")
        }
        val people = listOf("Raka", "Ibu", "Dinda").filter { it in text }
        container.journal.setTags(entry.id, TagSet(topics, habits, people), TagHash.of(text)!!)
    }

    private suspend fun put(container: AppContainer, day: Day) {
        val date = LocalDate.parse(day.date)
        for ((time, text) in day.entries) {
            val put = container.journal.putEntry(
                Entry(
                    date = date,
                    createdAt = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli(),
                    type = EntryType.WRITTEN,
                    text = text,
                    placeName = day.place,
                ),
            )
            tag(container, put)
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
        private fun d(day: Int) = LocalDate.of(2026, 11, day)

        val THIS_WEEK = RecapLetter(
            howYouFelt = "The two gym days (Mon, Tue) were your best start; Wednesday, after four hours of sleep, was the lowest.",
            howYouFeltDays = listOf(d(16), d(17), d(18)),
            threads = listOf(
                RecapThread("Tech Cantik", "Slides until 1am on Wednesday, a rehearsal with Dinda on Thursday, the talk on Saturday.", listOf(d(18), d(19), d(21), d(22))),
                RecapThread("Raka's PR", "Asked for on Tuesday, still pushed back on Thursday.", listOf(d(17), d(19))),
                RecapThread("Comfort or adrenaline", "Friday's walk at GBK, and again on Sunday with the speaker offer.", listOf(d(20), d(22))),
            ),
            wins = listOf(DatedNote("Deadlift PR at 80 kg", d(17)), DatedNote("Gave the talk at Tech Cantik", d(21))),
            stillOpen = listOf(DatedNote("The checkout bug still has no known cause", d(18))),
            note = "You were scared of the stage on Wednesday and on it by Saturday, hands shaking for two minutes and then fine. " +
                "Raka's review kept sliding while the talk took the room.",
            question = "You wrote about comfort and adrenaline twice. Which one is the speaker offer?",
            summary = "The week opened with gym on Monday and a deadlift PR on Tuesday, and Raka asked for a review of the checkout PR before Friday. " +
                "That night you had sate with Ibu, who talked about her arisan and her tomatoes. " +
                "On Wednesday you barely slept after working on the Tech Cantik slides, and the checkout bug came back in production. " +
                "Dinda heard a rehearsal on Thursday and said the opening was too long. " +
                "You walked at GBK on Friday instead of the gym, gave the talk on Saturday, and wrote it up on your blog on Sunday.",
            stats = PeriodStats(daysKept = 7, daysWritten = 7, words = 260, averageMood = 26.0 / 7),
            previousStats = PeriodStats(daysKept = 4, daysWritten = 4, words = 55, averageMood = 3.5),
            hadEarlier = true,
        )

        val OCTOBER = listOf(
            "Sprint planning, terus ngoding sampai sore.",
            "Ke Bandung sama anak-anak kantor lama. Dingin tapi seru.",
            "Demam, izin kerja, tidur seharian.",
            "Balik kerja, inbox numpuk. Satu-satu aja.",
            "Presentasi ke klien lancar. Bos bilang good job.",
        )
    }
}
