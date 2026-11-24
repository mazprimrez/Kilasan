package com.onedayonesecond.ui.theme

import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icons: thin lines with round ends, drawn on a 24 x 24 grid. Material's
 * own icon sets are filled or heavier, so these are drawn here to match the mood faces.
 */
object LineIcons {

    private fun icon(name: String, width: Float = 1.75f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** A dot: a line too short to see, which the round ends turn into a circle. */
    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y)
        lineTo(x + 0.01f, y)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, c: Float) {
        moveTo(l + c, t)
        lineTo(r - c, t)
        arcTo(c, c, 0f, false, true, r, t + c)
        lineTo(r, b - c)
        arcTo(c, c, 0f, false, true, r - c, b)
        lineTo(l + c, b)
        arcTo(c, c, 0f, false, true, l, b - c)
        lineTo(l, t + c)
        arcTo(c, c, 0f, false, true, l + c, t)
        close()
    }

    val Back = icon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
    val ChevronLeft = icon("ChevronLeft") { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) }
    val ChevronRight = icon("ChevronRight") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
    val ChevronDown = icon("ChevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
    val ChevronUp = icon("ChevronUp") { moveTo(6f, 14.5f); lineTo(12f, 8.5f); lineTo(18f, 14.5f) }
    val Plus = icon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val More = icon("More", width = 2.4f) { dot(5f, 12f); dot(12f, 12f); dot(19f, 12f) }
    val Close = icon("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    /** Days tab: a calendar page. */
    val Days = icon("Days") {
        roundRect(4f, 5.5f, 20f, 20f, 3f)
        moveTo(4f, 10f); lineTo(20f, 10f)
        moveTo(8.5f, 3.5f); lineTo(8.5f, 7f)
        moveTo(15.5f, 3.5f); lineTo(15.5f, 7f)
    }

    /** Recaps tab: a letter. */
    val Recaps = icon("Recaps") {
        roundRect(3.5f, 6f, 20.5f, 18.5f, 2.5f)
        moveTo(4.5f, 7.5f); lineTo(12f, 13.5f); lineTo(19.5f, 7.5f)
    }

    /** Books tab: an open book. */
    val Books = icon("Books") {
        moveTo(12f, 6.5f); curveTo(10f, 5f, 6.5f, 4.8f, 4f, 5.5f)
        lineTo(4f, 17.5f); curveTo(6.5f, 16.8f, 10f, 17f, 12f, 18.5f)
        moveTo(12f, 6.5f); curveTo(14f, 5f, 17.5f, 4.8f, 20f, 5.5f)
        lineTo(20f, 17.5f); curveTo(17.5f, 16.8f, 14f, 17f, 12f, 18.5f)
        moveTo(12f, 6.5f); lineTo(12f, 18.5f)
    }

    /** The barcode on a book's back cover. */
    val Barcode = icon("Barcode") {
        moveTo(4.5f, 6f); lineTo(4.5f, 18f)
        moveTo(7.5f, 6f); lineTo(7.5f, 18f)
        moveTo(10f, 6f); lineTo(10f, 15.5f)
        moveTo(13.5f, 6f); lineTo(13.5f, 15.5f)
        moveTo(16f, 6f); lineTo(16f, 18f)
        moveTo(19.5f, 6f); lineTo(19.5f, 18f)
    }
    val Minus = icon("Minus") { moveTo(5f, 12f); lineTo(19f, 12f) }
    val Check = icon("Check", width = 2f) { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }

    /** Reel tab: a frame with a play triangle. */
    val Reel = icon("Reel") {
        roundRect(4f, 4f, 20f, 20f, 3.5f)
        moveTo(10.5f, 9f); lineTo(15f, 12f); lineTo(10.5f, 15f); close()
    }
    val Video = icon("Video") {
        roundRect(3.5f, 7f, 15f, 17f, 2.5f)
        moveTo(15f, 11f); lineTo(20.5f, 8f); lineTo(20.5f, 16f); lineTo(15f, 13f)
    }
    val Flip = icon("Flip") {
        moveTo(20f, 11f); arcTo(8f, 8f, 0f, false, false, 5.5f, 7.5f)
        moveTo(5f, 4f); lineTo(5.5f, 7.5f); lineTo(9f, 7.5f)
        moveTo(4f, 13f); arcTo(8f, 8f, 0f, false, false, 18.5f, 16.5f)
        moveTo(19f, 20f); lineTo(18.5f, 16.5f); lineTo(15f, 16.5f)
    }
    val Image = icon("Image") {
        roundRect(4f, 4.5f, 20f, 19.5f, 3f)
        circle(9f, 9.5f, 1.4f)
        moveTo(4.5f, 17f); lineTo(9.5f, 12.5f); lineTo(13f, 15.5f); lineTo(15.5f, 13.5f); lineTo(19.5f, 17f)
    }
    val Write = icon("Write") {
        moveTo(4.5f, 19.5f); lineTo(5.5f, 15.5f); lineTo(16f, 5f); lineTo(19f, 8f); lineTo(8.5f, 18.5f); close()
        moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
    }
    val Mic = icon("Mic") {
        roundRect(9f, 3.5f, 15f, 14f, 3f)
        moveTo(5.5f, 11.5f); arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11.5f)
        moveTo(12f, 18f); lineTo(12f, 21f)
    }

    /** Draw with S Pen: a pen tip leaving a line. */
    val Pen = icon("Pen") {
        moveTo(14f, 4f); lineTo(20f, 10f); lineTo(11f, 15f); lineTo(9f, 13f); close()
        moveTo(9.8f, 14.2f); lineTo(6f, 18f)
        moveTo(4f, 20.5f); curveTo(7f, 18.5f, 9f, 22f, 12f, 20f); curveTo(14f, 18.7f, 16f, 20.5f, 19f, 20f)
    }
    val Play = icon("Play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }

    /** The play triangle filled in, for a line of words that starts something playing. */
    val PlayFilled: ImageVector = ImageVector.Builder("PlayFilled", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }
    }.build()
    val Pause = icon("Pause", width = 2f) {
        moveTo(8.5f, 6f); lineTo(8.5f, 18f)
        moveTo(15.5f, 6f); lineTo(15.5f, 18f)
    }
    val ArrowUp = icon("ArrowUp") {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(6.5f, 10.5f); lineTo(12f, 5f); lineTo(17.5f, 10.5f)
    }
    val ArrowDown = icon("ArrowDown") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(6.5f, 13.5f); lineTo(12f, 19f); lineTo(17.5f, 13.5f)
    }

    /** "About the same": a level line with an arrow head. */
    val ArrowSame = icon("ArrowSame") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(14f, 7f); lineTo(19f, 12f); lineTo(14f, 17f)
    }
    val Place = icon("Place") {
        moveTo(12f, 21f)
        curveTo(8f, 16.5f, 5.5f, 13.5f, 5.5f, 10f)
        arcTo(6.5f, 6.5f, 0f, false, true, 18.5f, 10f)
        curveTo(18.5f, 13.5f, 16f, 16.5f, 12f, 21f)
        close()
        circle(12f, 10f, 2.2f)
    }
    val Trash = icon("Trash") {
        moveTo(5f, 7f); lineTo(19f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 7f)
    }

    /** The options of a page: three dots, one above the other. */
    val MoreVertical = icon("MoreVertical", width = 2.4f) { dot(12f, 5f); dot(12f, 12f); dot(12f, 19f) }
    val Camera = icon("Camera") {
        moveTo(3.5f, 7.5f); lineTo(7.5f, 7.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(16.5f, 7.5f)
        lineTo(20.5f, 7.5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
        circle(12f, 13f, 3f)
    }
    val Search = icon("Search") {
        circle(11f, 11f, 6.5f)
        moveTo(16f, 16f); lineTo(20f, 20f)
    }

    /** Something written for you from your own notes: a small burst of rays. */
    val Sparkle = icon("Sparkle") {
        moveTo(12f, 3f); lineTo(12f, 7f)
        moveTo(12f, 17f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(7f, 12f)
        moveTo(17f, 12f); lineTo(21f, 12f)
        moveTo(6f, 6f); lineTo(8.5f, 8.5f)
        moveTo(15.5f, 15.5f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(15.5f, 8.5f)
        moveTo(8.5f, 15.5f); lineTo(6f, 18f)
    }
    val Copy = icon("Copy") {
        roundRect(8f, 8f, 20f, 20f, 2f)
        moveTo(4f, 16f); lineTo(4f, 5f); arcTo(1f, 1f, 0f, false, true, 5f, 4f); lineTo(16f, 4f)
    }

    /** A backup kept somewhere else. */
    val Cloud = icon("Cloud") {
        moveTo(7f, 18f); lineTo(17f, 18f)
        arcTo(4f, 4f, 0f, false, false, 17.5f, 10f)
        arcTo(6f, 6f, 0f, false, false, 6f, 9f)
        arcTo(4.5f, 4.5f, 0f, false, false, 7f, 18f)
    }

    /** No internet: the cloud with a line through it. */
    val Offline = icon("Offline") {
        moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        moveTo(8.5f, 8.6f); arcTo(6f, 6f, 0f, false, false, 6f, 9f)
        arcTo(4.5f, 4.5f, 0f, false, false, 7f, 18f); lineTo(17f, 18f)
        moveTo(17.5f, 10f); arcTo(6f, 6f, 0f, false, false, 11.5f, 5.2f)
        moveTo(17.5f, 10f); arcTo(4f, 4f, 0f, false, true, 20.3f, 16f)
    }

    /** Words printed on a clip: a capital T. */
    val Text = icon("Text") {
        moveTo(5f, 6f); lineTo(19f, 6f)
        moveTo(12f, 6f); lineTo(12f, 19f)
    }

    /** A caption: a card with two lines of writing. */
    val Caption = icon("Caption") {
        roundRect(4f, 6f, 20f, 18f, 2f)
        moveTo(8f, 14f); lineTo(16f, 14f)
        moveTo(8f, 10f); lineTo(11f, 10f)
    }

    /** Do it again, or put something else in its place: an arrow going round. */
    val Replace = icon("Replace") {
        moveTo(4f, 12f); arcTo(8f, 8f, 0f, true, false, 6.3f, 6.3f)
        moveTo(4f, 4f); lineTo(4f, 8f); lineTo(8f, 8f)
    }

    /** Settings: a gear. Too many small arcs to draw by hand, so it is read from its path. */
    val Settings: ImageVector = ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    /** The daily reminder: a bell. */
    val Bell = icon("Bell") {
        moveTo(6f, 16f); lineTo(6f, 11f)
        arcToRelative(6f, 6f, 0f, false, true, 12f, 0f)
        lineTo(18f, 16f); lineTo(20f, 18f); lineTo(4f, 18f); close()
        moveTo(10f, 20f)
        arcToRelative(2f, 2f, 0f, false, false, 4f, 0f)
    }

    /** A time of day: a clock face. */
    val Clock = icon("Clock") {
        circle(12f, 12f, 9f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f)
    }

    /** Bring back from a backup: an arrow turning back on itself. */
    val Restore = icon("Restore") {
        moveTo(4f, 12f)
        arcToRelative(8f, 8f, 0f, true, false, 2.3f, -5.7f)
        moveTo(4f, 4f); lineTo(4f, 8f); lineTo(8f, 8f)
    }

    /** A letter: a page with lines on it. */
    val Person = icon("Person") {
        circle(12f, 8f, 3.5f)
        moveTo(5f, 20f)
        curveTo(5.5f, 15.5f, 8.5f, 13.5f, 12f, 13.5f)
        curveTo(15.5f, 13.5f, 18.5f, 15.5f, 19f, 20f)
    }

    /** Three bars, for Patterns and the tags it counts from. */
    val Bars = icon("Bars") {
        moveTo(6f, 20f); lineTo(6f, 13f)
        moveTo(12f, 20f); lineTo(12f, 5f)
        moveTo(18f, 20f); lineTo(18f, 10f)
    }

    val Letter = icon("Letter") {
        moveTo(6f, 3f); lineTo(15f, 3f); lineTo(19f, 7f); lineTo(19f, 21f); lineTo(6f, 21f); close()
        moveTo(9f, 11f); lineTo(16f, 11f)
        moveTo(9f, 15f); lineTo(16f, 15f)
    }

    /** About the app: a circled letter i. */
    val Info = icon("Info") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 11f); lineTo(12f, 16f)
        dot(12f, 8f)
    }

    /** Save a copy to the phone: an arrow down onto a line. */
    val Download = icon("Download") {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }

    /** Something to be careful about: a triangle with an exclamation mark. */
    val Warning = icon("Warning") {
        moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
        moveTo(12f, 10f); lineTo(12f, 14f)
        dot(12f, 16.8f)
    }
}
