# Kilasan: design handoff
 
**Kilasan** ("a glimpse") is 1day1sec turned into a diary built around the daily
one-second clip. **Keep every existing feature** (record up to 5 s, trim to 1 s,
import from gallery, captions, compile reel with date/caption style, Google Drive
backup and restore, daily reminder). Add journal entries, mood, place, AI recaps
and a reading log on top. It is a diary only: no to-do lists anywhere.
 
App name: `Kilasan` (`app_name` in strings.xml). Launcher icon: lowercase rose
"k" with a small flash on blush, files in `docs/kilasan-icon.zip`.
 
Screens and flows were designed in a Claude canvas; exported images live in
`docs/design/` (add them there). This file is the source of truth for code.
 
---
 
## Look and feel

**The Contact Sheet.** Every day is a numbered frame on white paper, the way frames
are numbered on a strip of film: a 3:4 rectangle with 3 dp corners and its number in
mono underneath. The month is a sheet of them six across, a week is a strip of seven,
the reel's timeline is a strip numbered by day of the year. A missed day is a dashed
frame, a day to come a faint one, today has a rose outline. The frame, its sheet and
the shared components live in `ui/theme/` (`ContactFrame.kt`, `Components.kt`,
`Theme.kt`). The sections further down describe what each screen holds; where they
describe how it looks and this section disagrees, this section is right.

### Colors (`Palette` in `ui/theme/Theme.kt`, light theme only for now)

| Role | Hex | Use |
|---|---|---|
| Paper | `#FFFFFF` | Every background |
| Ink | `#2E2329` | Text, icons, the message bar |
| Muted | `#6E6168` | Dates, secondary text, inactive tabs, field outlines |
| Rose | `#AE4A6B` | Actions, selection, today's outline, progress, mood faces |
| Blush | `#FBCCDD` | Fills: tab indicator, tonal buttons, selected segment, progress track |
| Blush tint | `#FDF2F6` | Tab bar, tinted cards, search bar, circles behind list icons |
| Hairline | `#F1E6EA` | 1 dp dividers, frames of days to come |
| Frame line | `#E8C2D1` | Outlined buttons and chips, dashed frames of missed days |
| Error | `#B3261E` | Things going wrong, actions that delete |

Mood scale (darker = harder day): great `#F9D3E0`, good `#F7BDD0`, neutral `#E58AAB`,
bad `#B8527A`, rough `#6E2445`. Used by the year in pixels and its export, the thin
mood bar under each day of a week or month recap, and the weekday row of a month or
year recap. Everywhere else a mood is its face and its word; where the scale is used,
the face, the word or the screen reader's label is there too, so it is never a colour
alone.

Rules: one rose button per screen (on the tabs and the day and book pages it is the
floating button, bottom right). Surfaces are flat; only the floating button, the
message bar and book covers cast a shadow. No black, no grey. Sections are separated
by space and hairlines.

Corners: frame 3 dp, field 4 dp, chip 8 dp, preview and alert 12 dp, card and floating
button 16 dp, buttons 24 dp (48 dp high), search bar 28 dp, sheet top 28 dp. Screen
margin 24 dp.

### Type (`AppType` in `ui/theme/Theme.kt`)

- **Roboto Flex** for every word (one variable file in `res/font`).
- **Spline Sans Mono** (500) only for measurements: frame numbers, timecodes, page
  numbers, counts.
- Sizes: 36 sp the day number, 28 sp tab titles and recap headlines, 24 sp titles
  inside a screen or sheet, 22 sp top bar titles, 16 sp headings (600) and body,
  14 sp meta and buttons (600), 12 sp small print, 11 sp frame numbers.
- Words quoted back to the person who wrote them are set at 22 sp with the font's
  slant (−10). Nothing else is slanted.

### Icons and mood faces
 
Thin line icons, 1.4–2 dp stroke, round caps. Mood is shown as a line-drawn
face in Rose, never as an emoji. Five moods: rough, bad, neutral, good, great.
 
### Media
 
Photos and videos are mostly vertical. Clip tiles, previews and photo tiles are
portrait (9:16 for clips, 3:4 for photos).
 
---
 
## Navigation
 
Bottom bar with 4 tabs: **Days**, **Recaps**, **Books**, **Reel**.
 
```
Days (calendar)
 ├─ tap a kept day ─────────▶ Day page ──⋯──▶ Day options sheet
 │                                          ├─ Replace this second ─▶ Camera
 │                                          ├─ Move to another day ─▶ Move dialog
 │                                          ├─ Edit caption / Change place
 │                                          └─ Delete this second (undo + Recently deleted)
 └─ Add to today ──▶ Add sheet
                      ├─ Record today's second ─▶ Camera ─▶ Trim ─▶ Today page (saved toast + Undo)
                      └─ Write / Voice note / Photo / Draw with S Pen
Recaps (per year, swipe between years)
 ├─ Year recap ─▶ Year page
 ├─ Year in pixels ─▶ Pixels page ─▶ Download PNG
 └─ Month rows (expand to weeks) ─▶ Month recap / Week recap
Books
 ├─ + ─▶ Add a book (search / scan barcode / add by hand) ─▶ added toast + Undo
 ├─ Reading now row ─▶ Book page ──▶ Summary and review ideas
 │                       └─ Add a thought ─▶ Log a thought
 ├─ "Log a thought" link on each reading row ─▶ Log a thought (that book)
 └─ Finished this year (grid) ─▶ Book page
Reel
 ├─ Range: Year / 30 days / Week (for this reel; the one it starts with is in Settings)
 ├─ Text on each second ─▶ its settings page
 ├─ Google Drive backup ─▶ its page
 └─ Make my reel
Settings (the gear on every tab)
 ├─ Letters: What letters call you ─▶ name dialog · When letters arrive
 ├─ Reminder: Daily reminder (switch) · Remind me at ─▶ time dialog
 ├─ Reel: Text on each second ─▶ Show (date, caption, place), Font, Position,
 │        Reel length to start with (Year / 30 days / Week) · Saves to
 ├─ Places: Tag entries with a place (switch)
 ├─ Backup: Google Drive ─▶ Back up now, Restore from Drive
 └─ Your data: Recently deleted · About Kilasan
```
 
---
 
## Screens
 
### Days tab (`ui/home/HomeScreen.kt`)
- Title = month name, under it "13 of 16 days kept" (Muted). Prev/next month arrows,
  then the Settings gear (the gear is at the end of every tab's header).
- Calendar grid, Monday first, portrait tiles (~60 dp tall), day number bottom-left.
  - Kept: Blush fill. Missed: hairline outline. Today: 2 dp Rose ring. Future: number only.
  - Accessibility label is the full date: "Saturday, 14 November 2026".
- One Rose button "Add to today" above the tab bar.
### Add to today (bottom sheet or screen)
- Title "Add to today", meta "Monday, 16 November, Kemang".
- Rose button "Record today's second" → existing camera flow.
- Rows: Write, Voice note, Photo ("Notebook pages are typed out for you"), Draw with S Pen.
### Camera and Trim (existing, restyle only)
- Camera: up-to-5-seconds progress, front/back switch, "Gallery" import, place under the date.
- Trim: portrait looping preview, 1-second window on a filmstrip, optional caption,
  "Retake" / "Use this second". After saving go to the day page with the saved toast.
### Day page (new)
- Top bar: back, "+" (add to this day), "⋯" (options).
- Header row: portrait clip on the left (~210 × 373 dp). Right column, top to bottom:
  mood face (tap to change), big day number, month, weekday (Muted), caption,
  place (Muted, bottom-aligned with the clip).
- Entries below as plain rows separated by hairlines, each with a Muted line
  "09:40, written" / "13:05, voice note" / "18:20, from my notebook" / "21:30, S Pen" / "2 photos".
- Today with no mood yet: dashed circle with "+" instead of the face, and a
  "How was today?" row of the five faces.
### Entry processing states
- Voice note: playable immediately; text shows "Writing out what you said…" until transcribed.
- Notebook photo offline: "No internet right now. The photo is saved on your phone.
  Its text will be typed out as soon as you're back online." + "Try now". Retry automatically.
- Messages use a polite live region and never take focus.
### Saved / deleted messages (snackbar)
- "Today's second is saved." with **Undo** and **Write a few words**.
- "Second for 14 November deleted." with **Undo** and **Record a new one**.
- At least 10 s, stays while touched or focused, has a close button.
### Day options sheet
Replace this second, Move to another day, Edit caption, Change place, then
separated at the bottom: Delete this second ("Your writing and photos stay. You can
undo, and it stays in Recently deleted for 30 days."). Close button at the bottom.
 
### Move to another day
- Text field "New day" (type "15 Nov 2026") plus a month calendar.
- Dots on days that already have a second. Days after today are not selectable,
  with the reason shown.
- If the target day has a second: inline warning naming it ("15 November already has
  a second, 'coffee before work'. Moving here replaces it, and that video can't be
  brought back."). Primary button becomes "Move and replace 15 Nov"; Cancel is the
  default focus. (`ClipMoveRules` / `ClipAlreadyExistsException` already cover the logic.)
### Recaps tab (new)
- Big year number (84 sp) with an arrow and swipe to earlier years.
- The year's recap (title + quote), then "Year in pixels", then "Patterns" (this
  year: opens on the week under way; an earlier year: opens on that year).
- "Months and weeks": one row per month (tap → month recap) with an expand toggle
  "4 weeks" that shows week chips ("9 to 15 Nov", tap → week recap). The current
  month says "Letter arrives on 1 December"; the current week chip says "on Sunday".
### Week / Month / Year recap (one shared layout, recap layout 2)
White, one rose action, hairlines between chapters, no cards. Top to bottom
(`ui/recaps/RecapScreen.kt`, chapters in `RecapLetterChapters.kt`):
1. Back bar with a ⋯ menu ("Write this recap again"), Week / Month / Year tabs, the
   period with previous and next arrows, the title (28 sp).
2. The quote, word for word from an entry, slanted, + "You, on Saturday".
3. One picture of the days: week = 7 portrait frames, month = calendar of small frames,
   each with a thin mood bar under it ("The bar under each day is your mood. Darker is
   a harder day."); the week's frames keep the small face too. Year = small pixel
   preview linking to Year in pixels. Then "Play this week, 7 seconds".
4. **How you felt**: 1–2 sentences on a mood pattern tied to days, with the days as
   chips. Only a pattern that holds on 2+ days, otherwise "steady". Month and year add
   the usual mood of each weekday as 7 mood-coloured cells, worked out on the phone.
5. **On your mind**: 2–4 threads that ran across days, each a title, a line and the
   days as chips ("Mon · Tue · Fri"); a chip opens that day. An event with a before
   and an after is one thread. A folded "Day by day" row opens the old one line per
   day (week and month only).
6. **Wins**: 2–3 concrete things, each with its day (tap opens the day).
7. **Still open**: things that sounded unfinished, each linking to its day. Left out
   when there are none. No checkboxes.
8. **A note for you**: "Dear Eza," + one short paragraph naming at least two specific
   things and saying something true, not only comforting.
9. **A question for next week/month/year**: one open question built from an entry.
10. **Since last week/month/year**: counted on the phone (`SinceLast`): "went up from
    3 to 7", "went down from 7 to 5", "stayed at 7" with the same/up/down icon; mood as
    a face and a word ("Your average mood was good, up from neutral"; never "mostly"). With no previous period with days kept: "Your
    first week in Kilasan." (or "Nothing was kept last week to compare with." after a
    gap) and this period's totals.
11. Year: **Where you were**.
12. **Patterns** (every scale): one sentence ("Public speaking and Work came up most,
    on 4 days each."), the top 3 topics as small bars with "4 days" in mono (every
    value tied for the top in Rose, the rest deeper blush), and "Open Patterns", which
    opens Patterns for the same period. Counted live from the tags, so a letter
    written before tags existed still gets it. Before anything has come up on 2 days:
    "Patterns show up after a few days." and the link only. (This replaced "Who and
    what came up most"; old letters' `topPeople`/`topTopics` are ignored.)
13. Footer: "A reflection from your own entries, not therapy."

Under the title, a Muted line when the letter no longer matches the entries ("You've
added 2 entries since this was written.", or "Your entries changed since this was
written.") or is in the old layout ("Written in the old layout."), with a "Write it
again" text link. Old-layout recaps are still shown the old way (What happened,
note, changes, Something to try).

**Write this recap again**: a dialog says "This replaces the current letter for 16 to
22 November with a new one written from your entries as they are now." with Write it
again / Cancel (Cancel has focus). While it is written the old letter stays, with
"Writing a new version…" and a busy line at the top. On success the message bar says
"New letter for 16 to 22 November." with Undo (the replaced version comes back). On
failure the old letter stays and an alert says why (offline, server, too few days now)
with Try again.

States: still writing (placeholder lines + "Takes about a minute"), not enough
entries ("You've written on 1 day this week. Write on 3 or more…" + "Write about
today"), couldn't be written ("Your phone was offline on Sunday night. Your entries
are safe" + "Write the letter now"), rewriting (old letter visible), rewrite failed
(old letter kept + reason + Try again).

### Patterns (`ui/patterns/`, new)
Descriptive counts of what a week, month or year was about, when, with whom and how
those days felt. Styled by `docs/KILASAN_DESIGN_SYSTEM.md`; screenshots in
`docs/screenshots/patterns.png` (seeded 16–22 Nov 2026). Everything is counted on the
phone by `PatternsCalculator` (pure, `journal/PatternsCalculator.kt`) from entries,
moods, places and entry tags; every heading and sentence comes from templates in
`journal/PatternsText.kt`, never from the model. Route `patterns/{periodKey}`.

Top to bottom:
1. Back bar "Patterns", Week / Month / Year tabs, the period with arrows (next is
   off until that period has begun). Dates: "9–15 November 2026", "1–16 November
   2026" (a month under way stops at today), "January–16 November 2026".
2. A summary line, Title Large with 30 sp line height, from the numbers ("Work came
   up most, on 6 of your 11 days with entries. Your average mood was good.").
3. Muted: "From 9 to 15 November: 6 days with entries, 14 entries. Counted on your
   phone." While older entries are still being tagged: "Still tagging your entries:
   40 of 120 done. Counts may grow."
4. The quote from the stored letter of the same period, slanted on Blush Tint, with
   the Rose link "You, Saturday 14 November ›" inside the card (opens the day). Never
   generated here; left out when there is no letter.
5. "The week in short" / "November so far, in short" / "The year so far, in short",
   labelled "**Summary** · written from your own entries.": the letter's `summary`
   paragraph, as written.
6. Sections, hairlines between, laid out as the Patterns boards of the design canvas.
   Each: a Title Large heading that states the finding ("Gym days felt better",
   "Work Tuesday to Thursday, gym Friday to Sunday"), a label with the section's name
   in Rose and what the chart counts in Muted ("**Habits** · found in your entries
   when you mention them."), the chart, a plain sentence (14 sp Ink) and sometimes a
   footnote (12 sp Muted).
   - **Week**: *Your week* (a table: the 7 frames head 7 columns, then Topics /
     Habits / People rows with name and count, a Rose dot under each day it came up
     and a small dot under the others), *Mood* (no chart: "Your mood climbed from
     Tuesday to Sunday", lowest and highest day in the label, the first habit's
     mood against other days in the sentence), *When* ("9 of 14 entries came after
     17:00"; Morning 05–11, Afternoon 11–17, Evening 17–22, Late night 22–05, each
     with its hours in mono under the name, a bar on a pale track), *Since last week*
     (topics and habits: "Gym went up from 2 to 3 days.", "Work stayed at 3 days.",
     ups first, then the same, then downs, at most 4).
   - **Month**: *Topics* (stacked rows: name and "7 days", a full-width bar, "↑ 3 more
     than 1–16 Oct" and a chevron; a row expands to weekday columns, a sentence and
     day chips "Mon 2"), *Mood and topics*, *Habits* (a strip of all the month's
     days: Rose = did it, Blush Tint = wrote but not it, dashed = no entry, thin
     outline = still to come; ticks 01 08 15 22 29; then "On gym days" / "Other
     days" cards with face, mood and "Average of 5 days"), *People* (strips, then
     "Turn off in Settings"), *Mood by weekday* (7 tinted tiles: face, word, "2d"),
     *When*, *Places*, *Words*.
   - **Year**: *Topics × months* (numbered heat grid, one Rose shading scale shared
     with the people grid; months ahead outlined, "December hasn't happened yet."),
     *Mood and topics*, *Habits* (12 columns per habit), *People × months* (then "Turn
     off in Settings"), *Mood by month* (tiles in 6 columns, then "See every day in
     Year in pixels ›"), *When* (each part's name over Jan–Jun and Jul–Nov bars once
     the year reaches July), *Places*, *Words*.
   - *Places* uses the full place name ("Kemang, Jakarta Selatan") with a pin, its
     days, and the average mood's face and word; *Words* are chips, the top word on
     Blush, the rest on Blush Tint.
7. Footer (12 sp Muted): week "A week is short: patterns here are single days, not
   habits. The month view shows what repeats." and "See November’s patterns ›", month
   "Patterns need at least 2 days to show. Counts update when you edit an entry.", year
   "Patterns are counts from your own entries, not conclusions about you."

**Mood and topics**: topics sorted from the best average mood to the worst; an axis of
five muted faces; each row a track with a rule at the usual mood ("usual: good"), a
line to a dot at the topic's average: filled Rose = above usual, hollow Muted =
below. Wherever a mood sits beside a topic or habit the sentence ends "Showing up on
the same day isn't proof that one caused the other."

Honesty rules (all in the calculator, all unit-tested):
- Every count covers exactly the range in the header.
- Comparisons use the same span of the period before: a whole period against the
  whole one before; a week under way against the same weekdays; a month under way
  against days 1–N of the month before (cut to its last day); a year under way
  against 1 January–the same date last year (29 Feb → 28 Feb).
- Moods are averages of the scores (rough 1 … great 5), rounded half up to a mood;
  above/below compares averages rounded to tenths. Never "mostly".
- Nothing that rests on fewer than 2 days is shown: a topic, habit, person, word or
  place needs 2 days; a weekday, month, place or habit/other-days mood needs 2 days
  with a mood, otherwise "Too few" (dashed circle).
- Top lists: most days first, ties A–Z ignoring case; topics 8, habits 5, people 8,
  places 6, words 12. Names written in other capitals count as one.
- Weekdays, months and parts of the day in the phone's time zone.
- Charts: one series in Rose; top value Rose, the rest deeper blush (`BlushDeep`),
  counts in mono; every chart has a full text description for TalkBack.
- Words leave out English and Indonesian stop words (slang included) and digits; with
  people switched off their names leave the words too.

**Empty** (nothing on 2 days yet): the week as seven small frames (today outlined in
Rose), "Patterns show up after a few days", "You have 1 day with entries so far this
month. Keep adding seconds and entries; topics, habits and people appear once
something comes up on 2 or more days.", and a tonal "See October’s patterns" / "See
last week’s patterns". No counted line and no footer.

### Year in pixels (new)
- One column per month, one row per day, cells colored by mood; no entry = outline;
  future = faint.
- Legend with day counts per mood.
- Rose "Download PNG" → saves a 1080 × 1920 image to the gallery
  ("2026 in pixels", the grid, legend, small 1day1sec mark).
### Reel tab (existing compile + backup, restyled)
- Portrait preview beside the range's numbers; the range is Year / 30 days / Week.
  Changing it here is for this reel only.
- Rows: "Text on each second" and "Google Drive backup", each opening its page
  (see Settings). Their second lines say the current state ("Date and caption,
  bottom left", "Backed up today at 09:12").
- Rose "Make my reel". Reels are saved to Movies/Kilasan.

### Settings (`ui/settings/`)
Reached from the gear on every tab. A back bar, then groups under quiet headings,
each row with its icon in a tinted circle:
- **Letters**: "What letters call you" opens a dialog with the name field, a count
  (n / 40) and how the next letter opens; an empty name is allowed. "When letters
  arrive" only states the schedule.
- **Reminder**: "Daily reminder" switch (asks for notification permission when
  switched on without it), and "Remind me at" with the time in mono, which opens a
  dialog to type another time. The reminder still only comes if the day has no second.
- **Reel**: "Text on each second" opens its page: a frame showing the text on the
  newest second, Show chips (Date, Caption, Place), Font chips, Position (Left /
  Center / Right, Bottom / Top), and "Reel length to start with" (Year / 30 days /
  Week), which is remembered. "Saves to" states the folder.
- **Places**: "Tag entries with a place" switch (asks for location when switched on
  without it). Off means no place is looked up at all.
- **Backup**: "Google Drive" opens its page: when it last ran, how many seconds,
  entries and books are on the phone, Rose "Back up now", outlined "Restore from
  Drive" (only adds what is missing from the phone). A run carries on when the page
  is left.
- **Recaps**: "Write recaps for past weeks and months" opens a page that first lists
  what it would do, with counts: past weeks, months and finished years with enough
  entries and no recap ("12 weeks and 3 months"), an opt-in checkbox to rewrite the
  recaps in the old layout, and an opt-in checkbox for this year so far. Rose "Write
  15 recaps". While it runs: "7 of 15 written", a progress line and Stop (keeps what
  is written); the same with Stop in a notification. Its row in Settings shows the
  progress while it runs.
  "Show people in Patterns" switch (on by default; off hides People sections and
  people's names in Words). "Tags for Patterns": "All 25 entries are tagged", or
  "40 of 120 entries tagged. Needs a connection." with a progress line and Stop /
  Resume ("Stopped at 40 of 120. Nothing is sent until you resume.").
- **Your data**: "Recently deleted" (with its count) and "About Kilasan".
### Books tab (new)
- Title "Books", + at top right opens Add a book.
- **Reading now:** rows with a 2:3 cover (64 × 96 dp), title (tap opens the book),
  author (Muted), 4 dp progress bar, "Page 214 of 379", and a Rose text link
  "Log a thought" with a + icon. Its accessibility label names the book
  ("Log a thought in Laut Bercerita"). No floating button: the action sits on the
  book it belongs to.
- **Finished this year:** 3-column grid of covers with titles, "See all 12".
### Add a book (new)
- Search field (title, author or ISBN) plus a barcode button.
- Results: cover, title, author, edition with page count (editions differ, and
  progress depends on the right one). Tap a result to add it.
- Footer: "Can't find your edition?" → add by hand with a photo of the cover.
- After adding: snackbar "Bumi Manusia added to Reading now." with **Undo** and
  **Log first thought**.
### Book page (new)
- Top bar: back, ⋯ (Mark as finished, Edit book, Change cover, Remove book).
- Header row: cover on the left (~132 × 198 dp). Right column: title, author
  (Muted), then a big current page number (44 sp, 600), "of 379 pages", progress
  bar, "Started 2 November".
- "Your thoughts, by page" + "Newest first". Each row: page number in a 52 dp left
  column (22 sp, 600, no repeated "page" label), then a Muted date line and the
  thought. Voice thoughts get a play button + transcript; photo thoughts get a page
  thumbnail.
- "Show 5 earlier thoughts", then a row "Summary and review ideas / Written from
  your 9 thoughts so far".
- Rose "Add a thought" pinned at the bottom.
### Log a thought (new)
- Close (draft is kept), title "Log a thought", book name as a picker under it.
- Page number: large field (36 sp) with − and + buttons, pre-filled with the last
  page + 1. Help: "You were on page 214. Earlier pages don't move your bookmark."
- Text box "What stayed with you on this page?", buttons "Photo of the page" and
  "Voice note" (same transcription as diary voice notes).
- Checkbox "Also show it in today's diary" (on by default): adds an entry on today's
  day page that links back to the book.
- Rose "Save thought" + Muted "Saving moves your bookmark to page 215" (only if the
  page is higher than the current bookmark).
### Summary and review ideas (new, AI)
- Muted line "Laut Bercerita. Written today from all 9 of your thoughts".
- Title: one line on how I read the book. Then my strongest line, quoted word for
  word from my own thoughts, in Rose serif italic with "You, on page 214".
- **Your reading, in short:** a short paragraph on how my thoughts changed.
- **Points for your review:** 4–5 checkbox rows (title, one line, Muted "From your
  notes on pages 142 and 214"). Suggestions not drawn from my notes (e.g. comparing
  with another book on my shelf) start unticked.
- Dashed box "Something your notes don't cover yet" (e.g. who I'd recommend it to).
- Rose "Start my review with N points" (opens a draft with the ticked points as
  starting lines) and "Copy the points".
- Footer: "Written from your own notes. The points are suggestions; the review is yours."
- Never quote the book itself, never write the review for me. Cache until a new
  thought is added.
### Book states
Empty shelf ("Your shelf is empty" + Add a book) · book with no thoughts ("Log your
first thought") · search with no results (spelling / author / barcode / add by hand)
· search offline ("Try again" or add by hand, cover filled in later) · no cover
found (title printed on a blush cover; tap to use a photo of my copy) · summary with
fewer than 3 thoughts ("needs one more thought") · summary being written (placeholder
lines, retried when back online).
 
---
 
## Data
 
Keep `ClipEntity` as is. Add (Room migration 2 → 3):
 
```kotlin
@Entity(tableName = "day_meta")
data class DayMeta(
    @PrimaryKey val date: LocalDate,
    val mood: Mood?,            // ROUGH, BAD, NEUTRAL, GOOD, GREAT
    val placeName: String?,     // e.g. "Kemang, Jakarta Selatan"
)
 
@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val createdAt: Long,
    val type: EntryType,        // WRITTEN, VOICE, NOTEBOOK_PHOTO, PEN, PHOTO
    val text: String?,          // written text, transcript or extracted text
    val mediaPath: String?,     // audio, photo or pen image file
    val placeName: String?,
    val processing: ProcessingState, // DONE, PENDING, WAITING_FOR_NETWORK, FAILED
)
 
@Entity(tableName = "recaps")
data class Recap(
    @PrimaryKey val key: String, // "week-2026-11-09", "month-2026-10", "year-2026"
    val title: String, val quote: String, val quoteDate: LocalDate,
    val json: String,            // the chapters; layout 1 = RecapContent, layout 2 = RecapLetter
    val createdAt: Long,
    // Added in version 5 (Recap v2):
    val schemaVersion: Int,      // 2 for the current layout; rows written before = 1
    val sourceHash: String,      // fingerprint of the period's entry ids + updatedAt + moods; "" for layout 1
    val entryCount: Int,         // entries in the period when it was written
    val previousJson: String?,   // the version a rewrite replaced, whole row as JSON, for Undo (one deep)
)
```

Version 5 (migration 4 → 5, `JournalSchema.MIGRATION_4_5_SQL`) only adds columns:
`entries.updatedAt` (set to `createdAt` for existing entries; moved whenever the words
change: edited by hand, cleared, or typed out) and the four recap columns above
(existing recaps become layout 1). A recap is out of date when the fingerprint of its
period today (`RecapSource.hash`) differs from `sourceHash`. Layout 2's `json` keeps
the model's words plus the phone's numbers at the time of writing: the stats of the
period and the one before and days per place (a year). From version 6 it also keeps
the model's `summary`; letters written before have none and old `topPeople` /
`topTopics` are ignored.

Version 6 (migration 5 → 6, `JournalSchema.MIGRATION_5_6_SQL`) adds one table:

```kotlin
@Entity(tableName = "entry_tags", indices = [Index("entryId"), Index("kind", "value")])
data class EntryTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val kind: String,        // "topic", "habit", "person", or "none" (tagged, nothing found)
    val value: String,       // "Work", "Gym", "Raka"; "" for none
    val taggedAt: Long,
    val sourceHash: String,  // TagHash.of(text): SHA-256 of the trimmed, space-squashed words
)
```

A tag counts only while its `sourceHash` matches the entry's words as they are now, so
an edited entry's old tags stop counting at once and it is tagged again. Deleting an
entry deletes its tags in the same transaction (`TagDao.deleteEntry`). An entry
needs tags when it has words, is done being typed out, and has no tag row for its
current hash.
 
Books (same migration, or 3 → 4 if the diary part has already shipped):
 
```kotlin
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val backupKey: String,       // as built: its name in the Drive backup, "2026-11-02-laut-bercerita-7k2q"
    val title: String, val author: String?,
    val isbn: String?, val edition: String?,
    val totalPages: Int?,
    val coverPath: String?,      // cached locally; null = blush cover with the title
    val currentPage: Int,        // bookmark, only moves forward
    val status: BookStatus,      // READING, FINISHED
    val startedOn: LocalDate, val finishedOn: LocalDate?,
)
 
@Entity(tableName = "book_thoughts", indices = [Index("bookId")])
data class BookThought(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val createdAt: Long,
    val type: ThoughtType,       // WRITTEN, VOICE, PAGE_PHOTO
    val text: String?,           // written text or transcript
    val mediaPath: String?,
    val showInDiary: Boolean,    // also listed on that day's page
    val processing: ProcessingState,
)
 
@Entity(tableName = "book_summaries")
data class BookSummary(
    @PrimaryKey val bookId: Long,
    val json: String,            // title, quote, quotePage, summary, points, gap
    val thoughtCount: Int,       // regenerate when the book has more thoughts than this
                                 // (as built: when the number of thoughts with words differs,
                                 // so a deleted thought it may be quoting rewrites it too)
    val createdAt: Long,
)
```
 
- **Book search uses free APIs only, called straight from the phone (no backend):**
  Open Library (`https://openlibrary.org/search.json`, no API key) for title/author/ISBN
  search, cover, author and page count. (As built: the plan was the Google Books API,
  but Google now refuses requests without a key and turned out to be missing many
  Indonesian titles and ISBNs, so it was removed and Open Library does the search.
  The title that matches what was typed is shown first. Open Library gives one page
  count for all editions of a book, shown as "about N pages".) If a result
  has no cover, look one up by ISBN
  (`https://covers.openlibrary.org/b/isbn/{ISBN}-L.jpg?default=false`, limit 100
  ISBN lookups per 5 minutes). Download the chosen cover once and cache it. Barcode
  scanning with ML Kit (on-device, free). Credit "Book data from Open Library" on an
  About screen.
- **Location:** fused location once per entry; reverse geocode to a short place
  name. Ask permission on first use; when off, simply hide place lines.
- **Backup:** extend the Drive layout so entries, day_meta, recaps, books, book
  thoughts and covers are backed up with the clips (JSON next to the media). As built
  for version 5: a recap's file carries `schemaVersion`, `sourceHash`, `entryCount`
  and the replaced version (`previous`); an entry carries `updatedAt` only when it was
  edited after it was made (so untouched days keep their bytes). Restore reads files
  with and without these fields: a recap file without `schemaVersion` is layout 1.
  Version 6: each entry in its day file carries its tags while they match its words,
  `"tags": {"hash", "topics", "habits", "people"}` (keyed to the entry, since ids
  change on restore). Restore puts them back when the hash still matches; files
  without `tags`, or tags without a hash, restore untagged and get tagged again.
## AI (Gemini via the backend)
 
- Python FastAPI backend on Cloud Run, Gemini through Vertex AI with the service
  account (no API key in code). Model set by one env var, `GEMINI_MODEL`, currently
  `gemini-3.5-flash-lite`. The app sends the signed-in Google ID token; the backend
  allows only my account.
- Voice note → transcript. Audio is mostly Indonesian mixed with English: transcribe
  verbatim and keep the language mixing as spoken. Keep the transcription provider
  behind one function so it can be swapped. Notebook photo / S Pen page → extracted
  text. Queued with WorkManager so it retries when back online.
- Book summary: `POST /book-recap` with the book, all its thoughts (page, date,
  text/transcript) and other books finished this year → JSON for the "Summary and
  review ideas" screen (title, verbatim quote from my thoughts + page, summary,
  4–5 points each with source pages, one gap). Needs at least 3 thoughts.
- Weekly recap every Sunday night (WorkManager), monthly on the 1st, yearly on
  31 December. Needs at least 3 days with entries in the period (week, month or year).
- Recap (`POST /recap`, layout 2): the app sends every entry of the period (date,
  time, type, text or transcript, place), the mood per day, its own stats for this and
  the previous period, and the previous recap's threads. The model returns title,
  quote {text, date}, howYouFelt {text, days}, threads, dayByDay, wins, stillOpen,
  note, question and summary (one paragraph of what happened, in time order, facts
  only, no numbers; shown by Patterns under "… in short"). Rules: specific over
  generic, no invented facts, no diagnosis, no to-dos, written in English (the quote
  stays in the entry's own words), note and question tied to concrete details. The backend checks the
  answer with Pydantic (one retry on a schema error), requires the quote to be an exact
  piece of an entry on its date (one retry with the error, then picks one itself), and
  drops any day outside the period or without entries. Every number on the page is
  worked out on the phone. Details in docs/JOURNAL_BACKEND.md.
- Tags (`POST /tag`): up to 20 entries {id, date, text} plus the vocabulary already
  in use (topics, habits, people, most used first) → `{tags: [{entryId, topics[],
  habits[], people[]}]}`. Topics are short English nouns (at most 3), habits only
  things actually done, people only names written in the entry (or a vocabulary name
  for the same person). The backend checks with Pydantic (one retry), keeps only ids
  it was sent, spells names as the vocabulary does, and drops people who are neither
  in the text nor the vocabulary. Same `GEMINI_MODEL`.
- Tagging runs in the background (`TagWorker`): when entries change (debounced 5 s)
  and on first start over every older entry, oldest first, 20 per request, 1.5 s
  apart, up to 30 batches per run, only online, with exponential backoff; tags are
  stored only if the entry's words did not change meanwhile. Progress, Stop and
  Resume in Settings → Recaps.
- Re-recap: "Write this recap again" on a recap (old one kept for Undo), and
  Settings → Recaps → past weeks and months, written oldest first, one request at a
  time, 5 s apart, as a WorkManager chain that waits for a connection.
## Must-fix behaviour in existing code
 
1. `ClipRepository.deleteClip` deletes the file immediately. Move the file to a
   `trash/` folder instead, keep a row with a deletion time, offer Undo, list them in
   "Recently deleted", purge after 30 days.
2. Moving with `replaceExisting = true` deletes the target clip. Route it through the
   same trash folder.
## Build order
 
1. Theme, fonts, bottom navigation, restyled Days / Camera / Trim / Reel.
2. Day page + entries (written, photo), mood, place; migration 2 → 3.
3. Voice notes and transcription; notebook photo extraction; offline states.
4. Recaps (week → month → year), Recaps tab, recap states.
5. Year in pixels + PNG export.
6. Safer delete, move dialog, polish.
7. Books: Books tab, Add a book (free APIs + barcode), Book page, Log a thought,
   book states.
8. Book summary and review ideas (`/book-recap`).
