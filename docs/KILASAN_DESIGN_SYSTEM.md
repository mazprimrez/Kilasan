---
name: Kilasan
description: A diary one second at a time; every day is a numbered frame on white paper.
colors:
  paper: "#FFFFFF"
  plum-ink: "#2E2329"
  muted-plum: "#6E6168"
  rose: "#AE4A6B"
  blush: "#FBCCDD"
  blush-tint: "#FDF2F6"
  hairline: "#F1E6EA"
  frame-line: "#E8C2D1"
  error: "#B3261E"
  scrim: "rgba(46,35,41,0.32)"
  mood-great: "#F9D3E0"
  mood-good: "#F7BDD0"
  mood-neutral: "#E58AAB"
  mood-bad: "#B8527A"
  mood-rough: "#6E2445"
typography:
  display-s:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "36px"
    fontWeight: 500
    lineHeight: "44px"
    letterSpacing: "-0.5px"
  headline-m:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "28px"
    fontWeight: 500
    lineHeight: "36px"
    letterSpacing: "-0.3px"
  headline-s:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 500
    lineHeight: "32px"
    letterSpacing: "-0.2px"
  title-l:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "22px"
    fontWeight: 500
    lineHeight: "28px"
    letterSpacing: "0px"
  title-m:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "24px"
    letterSpacing: "0.1px"
  title-s:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
  body-l:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.1px"
  body-m:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0.15px"
  body-s:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
    letterSpacing: "0.3px"
  label-l:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
  label-m:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.4px"
  label-s:
    fontFamily: "Roboto Flex, Roboto, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.5px"
  frame-number:
    fontFamily: "Spline Sans Mono, monospace"
    fontSize: "11px"
    fontWeight: 500
    lineHeight: "15px"
    letterSpacing: "0.4px"
    fontFeature: "tnum"
  numeral-m:
    fontFamily: "Spline Sans Mono, monospace"
    fontSize: "18px"
    fontWeight: 500
    lineHeight: "22px"
    letterSpacing: "0.4px"
    fontFeature: "tnum"
  numeral-l:
    fontFamily: "Spline Sans Mono, monospace"
    fontSize: "20px"
    fontWeight: 500
    lineHeight: "24px"
    letterSpacing: "0.4px"
    fontFeature: "tnum"
rounded:
  frame: "3px"
  frame-mini: "2px"
  field: "4px"
  chip: "8px"
  still-hero: "8px"
  alert: "12px"
  card: "16px"
  fab: "16px"
  pill: "24px"
  search: "28px"
  sheet: "28px"
spacing:
  frame-gutter: "4px"
  xs: "8px"
  sm: "12px"
  md: "16px"
  card: "20px"
  margin: "24px"
  section: "32px"
components:
  button-filled:
    backgroundColor: "{colors.rose}"
    textColor: "{colors.paper}"
    typography: "{typography.label-l}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 24px"
  button-tonal:
    backgroundColor: "{colors.blush}"
    textColor: "{colors.plum-ink}"
    typography: "{typography.label-l}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 24px"
  button-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.rose}"
    typography: "{typography.label-l}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 24px"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.rose}"
    typography: "{typography.label-l}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 12px"
  fab-extended:
    backgroundColor: "{colors.rose}"
    textColor: "{colors.paper}"
    typography: "{typography.label-l}"
    rounded: "{rounded.fab}"
    height: "56px"
    padding: "0 20px 0 16px"
  navigation-bar:
    backgroundColor: "{colors.blush-tint}"
    textColor: "{colors.muted-plum}"
    typography: "{typography.label-m}"
    height: "80px"
  navigation-indicator:
    backgroundColor: "{colors.blush}"
    rounded: "{rounded.card}"
    width: "64px"
    height: "32px"
  card-tint:
    backgroundColor: "{colors.blush-tint}"
    textColor: "{colors.plum-ink}"
    rounded: "{rounded.card}"
    padding: "20px"
  chip-assist:
    backgroundColor: "transparent"
    textColor: "{colors.plum-ink}"
    typography: "{typography.label-l}"
    rounded: "{rounded.chip}"
    height: "40px"
    padding: "0 16px 0 10px"
  segmented-selected:
    backgroundColor: "{colors.blush}"
    textColor: "{colors.plum-ink}"
    typography: "{typography.label-l}"
    height: "40px"
  text-field-outlined:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.plum-ink}"
    typography: "{typography.body-l}"
    rounded: "{rounded.field}"
    height: "56px"
    padding: "16px"
  search-bar:
    backgroundColor: "{colors.blush-tint}"
    textColor: "{colors.plum-ink}"
    rounded: "{rounded.search}"
    height: "56px"
  snackbar:
    backgroundColor: "{colors.plum-ink}"
    textColor: "{colors.paper}"
    typography: "{typography.body-m}"
    rounded: "{rounded.field}"
    height: "48px"
    padding: "0 8px 0 16px"
  frame-kept:
    rounded: "{rounded.frame}"
  frame-today:
    textColor: "{colors.rose}"
    rounded: "{rounded.frame}"
  frame-number:
    textColor: "{colors.muted-plum}"
    typography: "{typography.frame-number}"
  bottom-sheet:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.sheet}"
---

# Design System: Kilasan

## Overview

**Creative North Star: "The Contact Sheet"**

Kilasan borrows a photographer's contact sheet. A month is a grid of small portrait frames on white paper, each with its number printed underneath in a mono face. Kept days show their one-second still, missed days are dashed empty frames, and today is a rose outline waiting to be filled. Week strips in Recaps, the Reel timeline and the page numbers on book thoughts all reuse this one move. Everything else is plain Material 3. The app runs in Operate mode: M3 sets structure, navigation and controls, and the world adds only palette, type, density and the frame number.

The palette is pink and minimal on a white ground, a binding brand commitment carried over from the earlier look and from the owner's blog. Plum ink replaces black so the pink never turns cold. Density is tight inside a sheet (4 dp gutters) and roomy around it (24 dp screen margins). Surfaces are flat and get their depth from tone. Only the FAB, the snackbar and book covers cast a shadow.

All stills on the boards are CSS gradient placeholders and all content is synthetic sample content. No raster ships with this system.

**Key Characteristics:**
- Portrait frames (3:4) with a mono frame number under each, 6 across on the month sheet.
- White paper, plum ink, one rose accent; blush fills media wells and selected states.
- Roboto Flex for every UI role on the M3 scale; Spline Sans Mono only for measurement.
- Mood is always shown with a line-face glyph or a dashed or empty frame, never with colour alone.
- Flat, tonal surfaces; shadows only where something floats.

## Colors

One warm accent on white paper, softened by blush and plum instead of grey and black.

### Primary
- **Rose** ({colors.rose}): Actions and selection. Filled buttons, the extended FAB, text and outlined button labels, links, the focus ring (2 dp, offset 2), the caret, today's frame outline, the selected frame, progress fill, mood line-faces, switch track. M3 `primary`; `onPrimary` = Paper.

### Secondary
- **Blush** ({colors.blush}): Fills, never text. The nav-bar active indicator, the tonal button, the selected segment, the selected mood chip, text selection, the progress track. M3 `secondaryContainer` (and `primaryContainer`); `onSecondaryContainer` = Plum Ink. It also serves as `inversePrimary`, the snackbar action colour on Plum Ink.

### Neutral
- **Paper** ({colors.paper}): Screen ground, bottom sheets, text-field notch. M3 `background`, `surface`.
- **Plum Ink** ({colors.plum-ink}): All primary text and icons. M3 `onBackground`, `onSurface`; also `inverseSurface` (snackbar).
- **Muted Plum** ({colors.muted-plum}): Secondary text, supporting lines, inactive nav labels and icons, frame numbers on missed and future days, the text-field and segmented-button outline. M3 `onSurfaceVariant`, `outline`.
- **Blush Tint** ({colors.blush-tint}): The one container tone. Navigation bar, tinted cards (Year card, book summary link, pull quotes), search bar, list-item leading circles, empty week-strip cells, offline alert. M3 `surfaceContainerLowest` through `surfaceContainerHigh` collapse to this one value in the light scheme.
- **Hairline** ({colors.hairline}): Dividers between recap chapters and book thoughts, tab-row underline, future-day frame outlines, progress track on the record sheet. M3 `outlineVariant`.
- **Frame Line** ({colors.frame-line}): The stronger hairline for controls and absence. Outlined button and assist chip borders, dashed missed-day frames, the sheet drag handle. Extended colour `kilasanFrameLine` (no M3 slot).
- **Error** ({colors.error}): Destructive text buttons, recording dot, restore warning icon. M3 `error`.
- **Scrim** ({colors.scrim}): Behind bottom sheets. M3 `scrim` at 32%.

### Mood scale (data only)
- **Great → Rough** ({colors.mood-great}, {colors.mood-good}, {colors.mood-neutral}, {colors.mood-bad}, {colors.mood-rough}): An ordered blush-to-plum lightness ramp, used only in the Year in pixels heatmap, its PNG export, and the mini-pixel glyph on the Recaps tab. Darker means a harder day. Extended colours, not M3 roles.

### Named Rules
**The One Accent Rule.** Rose is the only saturated colour on the interface. Blush is a fill and never carries text or meaning by itself.

**The Faces, Not Fills Rule.** On frames, mood is a 5-step line-face glyph in Rose on a white 92–94% badge. The colour ramp appears only in the pixels heatmap, and there it always comes with the legend (with counts), a full-date label on each cell, and a dashed frame for "no entry".

**The Placeholder Rule.** Gradient stills in blush and plum stand in for clips until real media exists. They are not a palette and never decorate a non-media surface.

## Typography

**Body Font:** Roboto Flex (with Roboto, sans-serif), every UI role.
**Label/Mono Font:** Spline Sans Mono (500, tabular figures), measurement only.

**Character:** A quiet M3 sans for everything you read, plus a small technical mono for counting, like the edge print on film. Roboto Flex's slant axis (`slnt -10`) is the system's italic voice and is kept for pull quotes.

### Hierarchy (M3 TextStyle → sp)
- **displaySmall** = {typography.display-s}: Rare; large numerals on recap heroes.
- **headlineMedium** = {typography.headline-m}: Medium top app bar titles ("November 2026", "Recaps", "Reel").
- **headlineSmall** = {typography.headline-s}: Detail-page titles (book title, recap headings).
- **titleLarge** = {typography.title-l}: Small top app bar title, card headlines, pull quotes (with `slnt -10`).
- **titleMedium** = {typography.title-m}: Section headers, list titles. Weight 600, M3 default is 500.
- **titleSmall** = {typography.title-s}: Tab labels. Weight 600.
- **bodyLarge / bodyMedium / bodySmall** = {typography.body-l} / {typography.body-m} / {typography.body-s}: Entries and thoughts / supporting lines / meta and dates.
- **labelLarge / labelMedium / labelSmall** = {typography.label-l} / {typography.label-m} / {typography.label-s}: Buttons and chips / nav labels and trailing meta / overlay captions and weekday initials. Weight 600 throughout, M3 default is 500.
- **Mono, custom styles**: `frameNumber` {typography.frame-number} (also 12 sp for timecodes like `LOOP 00:02.0`), `numeralMedium` {typography.numeral-m} (page numbers on thoughts), `numeralLarge` {typography.numeral-l} (Reel stats, `p.214`).

In Compose, set the font sizes in sp and keep the line heights given. The 1.3× font-scale pass has not been run yet.

### Named Rules
**The Measurement Rule.** Mono is only for things you count: frame numbers, timecodes, page numbers, kept/missed/length stats. Dates, weekdays, captions and labels stay in Roboto Flex.

**The Slant Voice Rule.** Quoted words (her own lines lifted into recaps or book summaries) use Roboto Flex at `slnt -10`. Nothing else is italic.

## Layout

The boards use a 390 dp wide compact phone. Screen margins are 24 dp ({spacing.margin}). List items follow M3 insets (16 dp leading, 24 dp trailing, 56/72 dp min height). Recap chapters are separated by 32 dp plus a Hairline divider ({spacing.section}). Bottom content gets 120 dp of padding so it clears the FAB and the 80 dp navigation bar. The FAB sits 16 dp from the right and 96 dp from the bottom.

The contact sheet is a 6-column grid inside 358 dp: 4 dp column gutter ({spacing.frame-gutter}), 10 dp row gap (6 dp in compact sheets), 3:4 portrait frames, a 16 dp caption row with a 4 dp gap above it. Six columns, like one 35 mm strip, keep the whole month above the FAB and nav bar. Week strips use 7 columns. The Reel timeline is a horizontal row of 40×54 dp frames numbered by day of the year.

Hero media (day clip, reel preview) is a vertical still at 150×267 dp (9:16), set beside its metadata, never full-bleed.

### Named Rules
**The Tight Sheet, Wide Page Rule.** 4 dp between frames, 24 dp around everything else. Never loosen the sheet to fill space.

## Elevation & Depth

The system is flat and tonal. Depth comes from Paper against Blush Tint, not from shadows. App bars, cards, the nav bar and list items have no shadow. Three things float, and only those three cast a shadow.

### Shadow Vocabulary
- **FAB lift** (`0 2px 6px rgba(46,35,41,0.18), 0 6px 16px rgba(174,74,107,0.18)`): The extended FAB, with a rose-tinted second layer. In Compose, use the default FAB elevation (level 3) with the shadow colour tinted.
- **Snackbar** (`0 4px 12px rgba(46,35,41,0.24)`): Inverse snackbar.
- **Cover** (`0 1px 2px rgba(46,35,41,0.16)`): Book covers, so a white cover separates from white paper.

### Named Rules
**The Flat Paper Rule.** If a surface does not float above scrolling content, it gets no shadow. Use Blush Tint to set it apart.

## Shapes

Frames are almost square-cornered (3 dp, 2 dp in mini strips), like prints. Everything you touch follows M3 shapes: pill buttons (full, 48 dp high), 16 dp FAB and cards, 8 dp chips, 4 dp text fields and snackbar, 28 dp search bar and top of bottom sheets, 12 dp inline alerts. Hero stills use 8 dp (day) or 12 dp (reel preview). The circular mood badge and play buttons are the only circles besides icon buttons and avatars.

Absence is drawn with line style: a dashed 1 dp Frame Line border is a missed day, a solid 1 dp Hairline border is a future day, and a 2 dp Rose border is today or the current selection.

## Components

### Buttons
- **Shape:** full pill ({rounded.pill}), 48 dp high, Label Large.
- **Filled:** Rose on Paper text, 24 dp side padding (16 dp leading when an 18 dp icon leads). One per screen.
- **Tonal:** Blush with Plum Ink text.
- **Outlined:** transparent, 1 dp Frame Line border, Rose text.
- **Text / Danger:** 12 dp side padding, Rose or Error text.
- **Icon buttons:** 48 dp target, 24 dp outline icon (stroke 1.75, 2 when active).
- **Focus:** 2 dp Rose ring, 2 dp offset.

### Extended FAB
Rose, 56 dp high, 16 dp corners, 24 dp Paper icon plus Label Large ("Add today", "Make my reel"). Uses FAB lift. One per tab screen.

### Navigation bar
M3 NavigationBar on Blush Tint, 80 dp: Days, Recaps, Books, Reel. Active item gets a 64×32 Blush indicator, a Plum Ink icon at stroke 2, and a Plum Ink label; inactive items are Muted Plum.

### Cards / Containers
- **Corner Style:** 16 dp.
- **Background:** Blush Tint, no border, no shadow.
- **Internal Padding:** 20 dp (16 dp for link rows with a leading icon).
- The one bordered container is the Year visual link: 1 dp Hairline, 18 dp corners.

### Chips and Segmented button
- **Assist chip:** 40 dp, 8 dp corners, 1 dp Frame Line border, 18 dp Rose leading icon, Plum Ink Label Large.
- **Segmented button:** 40 dp, 1 dp Muted Plum outline, full radius. The selected segment gets Blush with a leading check. Keep to three segments at most (Reel: Year / 30 days / All).
- **Mood picker:** five line-faces at 30 dp. Selected gets a Blush pill and a Rose face; unselected faces are Muted Plum.

### Inputs / Fields
- **Text field:** M3 outlined, 1 dp Muted Plum, 4 dp corners, 56 dp min, floating label in Body Small, caret Rose.
- **Search bar:** Blush Tint, 56 dp, 28 dp corners.
- **Switch:** Rose track, Paper thumb with a Rose check.

### Feedback
- **Snackbar:** Plum Ink, 4 dp, Body Medium Paper text, one Blush text action ("Undo"). Sits above the nav bar.
- **Progress:** 4 dp linear, Blush track, Rose fill.
- **Inline alert:** 12 dp, Blush Tint (offline) with a 20 dp icon.
- **Bottom sheet:** Paper, 28 dp top corners, 32×4 Frame Line handle, 32% scrim.

### Contact-sheet frame (signature)
The one reusable signature. A portrait frame (3:4, 3 dp corners) with its number printed below in `frameNumber`, zero-padded (`01`–`31`, or day of the year on the Reel).
- **Kept:** the still fills the frame; a 22 dp white badge in the bottom-right corner holds an 18 dp Rose mood face. Number in Plum Ink.
- **Missed:** empty frame with a dashed 1 dp Frame Line border; number in Muted Plum; tappable to add.
- **Today:** 2 dp Rose border with an 18 dp Rose plus; number in Rose.
- **Selected:** 2 dp Rose outline at 1 dp offset around the still; number in Rose.
- **Future / blocked:** 1 dp Hairline border, not interactive.
- **Press:** scale 0.96 over 160 ms, `cubic-bezier(0.2, 0, 0, 1)`; no motion under reduced motion.
- Every frame has a full-date accessibility label ("Saturday, 14 November 2026, mood good").
- The same frame scales down for week strips (64 dp tall, 14 dp face), the Reel timeline (40×54), and mini strips (26 dp, no number).

### Settings
- **Entry:** a 24 dp settings icon button at the end of every tab's top app bar opens Settings (small top app bar with Back).
- **Sections:** Title Small in Muted Plum, 16 dp above and 4 dp below, Hairline divider between sections. Order: Letters, Reminder, Reel, Places, Backup, Your data.
- **Rows:** the standard list item (40 dp Blush Tint leading circle, Body Large title, Body Medium supporting line). Navigation rows end with a Muted Plum chevron; switch rows end with the M3 switch (off: Blush Tint track, 2 dp Muted Plum outline). Counts and times in mono (`3`, `20:00`).
- **Dialog:** M3 basic dialog, Paper, 28 dp corners, Headline Small title, one outlined field, text buttons Cancel / Save. The name dialog previews the letter's opening ("Dear Eza,") in the slanted voice on Blush Tint.
- **Reel text preview:** a 150×267 hero still with the caption and date drawn as the reel draws them (white, bold, soft shadow), numbered `318` below. Font chips render each label in its own family.

### Patterns (monthly descriptive stats)
- **Where:** a short "Patterns" chapter near the end of the monthly letter (one sentence, the top 3 topics as mini bars, "Open Patterns") opens a full Patterns screen with a period navigator.
- **Counted in the app, never by the model.** The model only tags topics, habits and names per entry. The header states the range and totals ("1 to 16 November: 14 days with entries, 41 entries").
- **Sections, in order:** What you wrote about (rows with a count and a bar; one row expands to weekday bars and day chips), Habits (a 30-cell month strip plus an average mood on habit days vs other days, with a "not proof" line), People you mentioned (one 30-cell strip per name, switchable off in Settings), Mood by weekday (average face per weekday with its day count; fewer than 2 days shows "Too few"), When you write (4 time-of-day bars), Where you were (places with average mood), Words you used most (chips with counts, stop words removed).
- **Chart rules:** one series in Rose, the top value Rose and the rest deeper Blush, counts in mono beside every bar, every chart has a full text label. Missed days stay dashed. Comparisons use the same span of the previous month ("than 1–16 Oct"). Moods are averages and say so; nothing claims "mostly" from an average.
- **Empty state:** until something comes up on 2 or more days, the screen shows the week strip and "Patterns show up after a few days".
- **Three scales, one template.** Week, Month and Year tabs sit under the top app bar, then the period navigator ("9–15 November 2026", "1–16 November 2026", "January–16 November 2026"). Each screen opens with a 1–2 sentence summary in Title Large (30 sp leading) and a muted counted line ("From … : N days with entries, N entries. Counted on your phone."). Every section heading states the finding; a Rose label names what the section is ("Topics · days each topic came up"); the chart is the evidence; one plain sentence follows.
- **Week:** a 7-frame header (stills with mood faces, missed day dashed) over a grid of rows (topics, habits, people) with a Rose dot on each day it came up; then mood, when, and "since last week" as arrows. A note says a week shows single days, not habits.
- **Year:** topics × months as a numbered heat grid (rows sorted by total, one shading scale for every grid on the board, December outlined as not yet), gym days per month as bars, people × months, average mood per month with its day count, time of day as Jan–Jun vs Jul–Nov bar pairs with counts, places, words.
- **Quote and summary (all three scales):** right after the counted line, a quote from her own entries, word for word, in the slanted voice on Blush Tint, with a Rose attribution link to that day ("You, Saturday 14 November"). Then a "… in short" section (label "Summary") with one paragraph of what happened, in time order, naming days, people and events that the patterns below back up.
- **Mood and topics (month, year):** right after Topics. One row per topic, sorted from lightest to heaviest: name, day count and average mood word on the left; on the right a rough-to-great track (five muted faces as the axis) with a dot at the average mood on days the topic came up and a line back to your usual mood, a vertical rule labelled "usual: …". Filled Rose dot = above usual, hollow Muted dot = below, so it never relies on colour alone. A note says co-occurring is not causing. The week skips it (too few days per topic).
- **Letters:** each week, month and year letter ends with a short Patterns chapter (one sentence, the top 3 as mini bars, every tied top value in Rose) that opens the same period's Patterns screen.

### Book thought row
A two-column row: on the left (56 dp), the page number in Rose `numeralMedium` over a Label Small unit; on the right, the date meta in Body Small Muted Plum and her words in Body Large. Rows are separated by Hairline dividers.

## Do's and Don'ts

### Do:
- **Do** map colours to the M3 light scheme as listed in Colors; leave unused containers at Blush Tint rather than inventing tones.
- **Do** print a mono frame number under every frame that stands for a day, week day or reel second.
- **Do** show mood with the line-face glyph on frames, and draw missed days with a dashed frame, so state never depends on colour.
- **Do** keep one filled action per screen (the FAB on tab screens), with 120 dp bottom padding so content clears it.
- **Do** keep contact sheets at 6 columns with 4 dp gutters on compact phones.
- **Do** give every frame and cell a full-date accessibility label, and keep touch targets at 48 dp.
- **Do** label synthetic stills and sample content as sample until real media replaces them.

### Don't:
- **Don't** set dates, weekdays, captions or labels in Spline Sans Mono; mono is only for measurements.
- **Don't** use the mood colour ramp outside the Year in pixels heatmap and its export.
- **Don't** add shadows to cards, app bars or the nav bar; set surfaces apart with Blush Tint.
- **Don't** put a small label above a title on cards or headers; the title stands alone.
- **Don't** put more than three options in a segmented button, or more than one action in a snackbar.
- **Don't** use black or grey; text is Plum Ink and Muted Plum.
- **Don't** treat the gradient stills as decoration or as brand colour.

### Open (not yet designed or verified)
- Dark colour scheme: not designed; only the light scheme exists.
- 1.3× font-scale pass: not run.
- Emulator screenshots: none; the boards are HTML mocks only.
- App icon board: not reviewed.
- Fonts not checked in renders: the sandbox could not load Google Fonts, so the screenshots fell back to other fonts.
