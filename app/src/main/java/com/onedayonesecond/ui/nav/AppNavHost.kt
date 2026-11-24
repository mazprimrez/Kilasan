package com.onedayonesecond.ui.nav

import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onedayonesecond.ui.backup.BackupViewModel
import com.onedayonesecond.ui.compile.CompileViewModel
import com.onedayonesecond.ui.settings.BackupScreen
import com.onedayonesecond.ui.settings.ReelTextScreen
import com.onedayonesecond.ui.settings.RecapBackfillScreen
import com.onedayonesecond.ui.settings.SettingsScreen
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.ui.camera.CameraScreen
import com.onedayonesecond.ui.day.DayScreen
import com.onedayonesecond.ui.day.PenScreen
import com.onedayonesecond.ui.day.VoiceScreen
import com.onedayonesecond.ui.day.WriteScreen
import com.onedayonesecond.ui.gallery.GalleryDayScreen
import com.onedayonesecond.ui.home.HomeScreen
import com.onedayonesecond.ui.home.RecentlyDeletedScreen
import com.onedayonesecond.ui.recaps.PixelsScreen
import com.onedayonesecond.ui.patterns.PatternsScreen
import com.onedayonesecond.ui.recaps.RecapScreen
import com.onedayonesecond.ui.recaps.RecapsScreen
import com.onedayonesecond.ui.reel.ReelScreen
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.about.AboutScreen
import com.onedayonesecond.ui.books.AddBookScreen
import com.onedayonesecond.ui.books.BookScreen
import com.onedayonesecond.ui.books.BookSummaryScreen
import com.onedayonesecond.ui.books.BooksScreen
import com.onedayonesecond.ui.books.ThoughtScreen
import com.onedayonesecond.ui.theme.MessageAction
import com.onedayonesecond.ui.theme.MessageBar
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.trim.TrimScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@UnstableApi
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val container = (LocalContext.current.applicationContext as OneDayApp).container
    val message by container.messages.current.collectAsStateWithLifecycle()

    // Typing out and letters go through the backend, which only answers its owner. The
    // first time, Google has to ask before it tells the backend who is using the app.
    val consent by container.backendAuth.consent.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (container.backendAuth.onConsentResult(result.data)) {
            container.messages.show("Allowed. Tap “Try again” on anything that was not typed out.")
        }
    }
    LaunchedEffect(consent) {
        val pending = consent ?: return@LaunchedEffect
        container.messages.show(
            "Typing out and letters need to confirm your Google account once.",
            MessageAction("Allow", run = {
                consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }),
        )
    }
    // The reel and the backup are each shown on more than one screen (the Reel tab, their
    // own page, a line in Settings), so their view models belong to the activity.
    val activity = checkNotNull(LocalViewModelStoreOwner.current)
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = Palette.Paper,
        contentColor = Palette.Ink,
        bottomBar = { if (route in Routes.TABS) TabBar(route, navController) },
        snackbarHost = {
            // Not over the camera, where it would sit on the record button. A message
            // still waiting is shown again, with its Undo, on the next screen.
            message?.takeIf { route != Routes.CAMERA_PATTERN }?.let { shown ->
                // Where a screen has a floating button, the message sits above it rather than on it.
                val lift = when (route) {
                    Routes.DAYS, Routes.BOOKS, Routes.REEL -> 72.dp
                    Routes.DAY_PATTERN, Routes.BOOK_PATTERN -> 80.dp
                    else -> 0.dp
                }
                MessageBar(
                    modifier = Modifier.padding(bottom = lift),
                    message = shown,
                    onAction = { action ->
                        container.messages.dismiss(shown.id)
                        // On the app's scope, so an Undo still finishes if the screen changes under it.
                        container.appScope.launch {
                            action.run?.invoke()
                            action.route?.let { navController.navigate(it) }
                        }
                    },
                    onDismiss = { container.messages.dismiss(shown.id) },
                )
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.DAYS) {
            destinations(navController, padding, activity)
        }
    }
}

/**
 * A screen that sits between the system bars (and above the tab bar, when it shows).
 * The padding is applied by each screen rather than by the host, so that while one
 * screen fades into another, each keeps its own: the camera, which draws under the
 * bars, does not drag the screen it is replacing up with it. It is consumed as well
 * as applied, so a screen that pads for the keyboard does not add the system bar's
 * height a second time.
 */
private fun androidx.navigation.NavGraphBuilder.screen(
    route: String,
    padding: PaddingValues,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content(entry) }
    }
}

@UnstableApi
private fun androidx.navigation.NavGraphBuilder.destinations(
    nav: NavHostController,
    padding: PaddingValues,
    activity: ViewModelStoreOwner,
) {
    val dateArg = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
    fun androidx.navigation.NavBackStackEntry.date(): LocalDate =
        LocalDate.ofEpochDay(arguments!!.getLong(Routes.ARG_DATE))

    val bookArg = listOf(navArgument(Routes.ARG_BOOK) { type = NavType.LongType })
    fun androidx.navigation.NavBackStackEntry.bookId(): Long = arguments!!.getLong(Routes.ARG_BOOK)

    /**
     * After a thought is saved: show the book it was saved to. If "Log a thought" was
     * opened from that book's page, this is simply going back to it.
     */
    fun showBook(bookId: Long) {
        nav.popBackStack()
        val under = nav.currentBackStackEntry
        val alreadyThere = under?.destination?.route == Routes.BOOK_PATTERN &&
            under.arguments?.getLong(Routes.ARG_BOOK) == bookId
        if (!alreadyThere) nav.navigate(Routes.book(bookId))
    }

    /** After something is added to a day: show that day, with the calendar one step behind it. */
    fun showDay(date: LocalDate) {
        nav.popBackStack(Routes.DAYS, inclusive = false)
        nav.navigate(Routes.day(date))
    }

    screen(Routes.DAYS, padding) {
        HomeScreen(
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onRecord = { nav.navigate(Routes.camera(it)) },
            onWrite = { nav.navigate(Routes.write(it)) },
            onVoice = { nav.navigate(Routes.voice(it)) },
            onDraw = { nav.navigate(Routes.pen(it)) },
            onSettings = { nav.navigate(Routes.SETTINGS) },
        )
    }

    screen(Routes.RECAPS, padding) {
        RecapsScreen(
            onOpenRecap = { nav.navigate(Routes.recap(it)) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
            onOpenPatterns = { nav.navigate(Routes.patterns(it)) },
            onSettings = { nav.navigate(Routes.SETTINGS) },
        )
    }

    screen(Routes.BOOKS, padding) {
        BooksScreen(
            onAdd = { nav.navigate(Routes.ADD_BOOK) },
            onOpenBook = { nav.navigate(Routes.book(it)) },
            onLogThought = { nav.navigate(Routes.thought(it)) },
            onSettings = { nav.navigate(Routes.SETTINGS) },
        )
    }

    screen(Routes.ADD_BOOK, padding) {
        AddBookScreen(
            onBack = { nav.popBackStack() },
            // Back on the shelf, where the new book now is and the "added" message is shown.
            onAdded = { nav.popBackStack() },
            onOpenBook = { bookId ->
                nav.popBackStack()
                nav.navigate(Routes.book(bookId))
            },
        )
    }

    screen(Routes.BOOK_PATTERN, padding, bookArg) { entry ->
        val bookId = entry.bookId()
        BookScreen(
            bookId = bookId,
            onBack = { nav.popBackStack() },
            onAddThought = { nav.navigate(Routes.thought(bookId)) },
            onSummary = { nav.navigate(Routes.bookSummary(bookId)) },
        )
    }

    screen(Routes.THOUGHT_PATTERN, padding, bookArg) { entry ->
        ThoughtScreen(
            initialBookId = entry.bookId(),
            onSaved = ::showBook,
            onClose = { nav.popBackStack() },
        )
    }

    screen(Routes.BOOK_SUMMARY_PATTERN, padding, bookArg) { entry ->
        val bookId = entry.bookId()
        BookSummaryScreen(
            bookId = bookId,
            onBack = { nav.popBackStack() },
            onAddThought = { nav.navigate(Routes.thought(bookId)) },
            // The review is a diary entry like any other: it opens in today's write screen.
            onStartReview = { nav.navigate(Routes.write(LocalDate.now())) },
        )
    }

    screen(Routes.REEL, padding) {
        ReelScreen(
            onSettings = { nav.navigate(Routes.SETTINGS) },
            onTextSettings = { nav.navigate(Routes.REEL_TEXT) },
            onBackup = { nav.navigate(Routes.BACKUP) },
            compile = viewModel(activity, factory = CompileViewModel.Factory),
            backup = viewModel(activity, factory = BackupViewModel.Factory),
        )
    }

    screen(Routes.SETTINGS, padding) {
        SettingsScreen(
            onBack = { nav.popBackStack() },
            onTextSettings = { nav.navigate(Routes.REEL_TEXT) },
            onBackup = { nav.navigate(Routes.BACKUP) },
            onRecapBackfill = { nav.navigate(Routes.RECAP_BACKFILL) },
            onRecentlyDeleted = { nav.navigate(Routes.DELETED) },
            onAbout = { nav.navigate(Routes.ABOUT) },
            compile = viewModel(activity, factory = CompileViewModel.Factory),
            backup = viewModel(activity, factory = BackupViewModel.Factory),
        )
    }

    screen(Routes.RECAP_BACKFILL, padding) {
        RecapBackfillScreen(onBack = { nav.popBackStack() })
    }

    screen(Routes.REEL_TEXT, padding) {
        ReelTextScreen(
            onBack = { nav.popBackStack() },
            compile = viewModel(activity, factory = CompileViewModel.Factory),
        )
    }

    screen(Routes.BACKUP, padding) {
        BackupScreen(
            onBack = { nav.popBackStack() },
            backup = viewModel(activity, factory = BackupViewModel.Factory),
        )
    }

    screen(Routes.ABOUT, padding) { AboutScreen(onBack = { nav.popBackStack() }) }

    screen(Routes.DELETED, padding) {
        RecentlyDeletedScreen(onBack = { nav.popBackStack() })
    }

    screen(Routes.DAY_PATTERN, padding, dateArg) {
        DayScreen(
            onBack = { nav.popBackStack() },
            onRecord = { nav.navigate(Routes.camera(it)) },
            onPickFromGallery = { nav.navigate(Routes.galleryDay(it)) },
            onWrite = { date, entryId -> nav.navigate(Routes.write(date, entryId)) },
            onVoice = { nav.navigate(Routes.voice(it)) },
            onDraw = { nav.navigate(Routes.pen(it)) },
            onMoved = ::showDay,
            onOpenBook = { nav.navigate(Routes.book(it)) },
        )
    }

    screen(Routes.GALLERY_DAY_PATTERN, padding, dateArg) { entry ->
        val date = entry.date()
        GalleryDayScreen(
            // Back from the trim screen lands here again, so a different video
            // from the same day can be tried.
            onPicked = { file -> nav.navigate(Routes.trim(date, file)) },
            onRecordInstead = { nav.navigate(Routes.camera(date)) },
            onBack = { nav.popBackStack() },
        )
    }

    // The camera draws under the system bars and pads its own controls.
    composable(Routes.CAMERA_PATTERN, arguments = dateArg) { entry ->
        val date = entry.date()
        CameraScreen(
            date = date,
            onCaptured = { file -> nav.navigate(Routes.trim(date, file)) },
            onBack = { nav.popBackStack() },
        )
    }

    screen(
        Routes.TRIM_PATTERN,
        padding,
        dateArg + navArgument(Routes.ARG_PATH) { type = NavType.StringType },
    ) { entry ->
        TrimScreen(
            // Saving lands on the day's page rather than back at the camera, so the
            // finished day and the "saved" message are what the user sees next.
            onSaved = { showDay(entry.date()) },
            onBack = { nav.popBackStack() },
        )
    }

    screen(
        Routes.WRITE_PATTERN,
        padding,
        dateArg + navArgument(Routes.ARG_ENTRY) {
            type = NavType.LongType
            defaultValue = Routes.NO_ENTRY
        },
    ) { entry ->
        val container = (LocalContext.current.applicationContext as OneDayApp).container
        WriteScreen(
            date = entry.date(),
            entryId = entry.arguments!!.getLong(Routes.ARG_ENTRY),
            onDone = { showDay(entry.date()) },
            onBack = { nav.popBackStack() },
            // Set by "Start my review" on a book's summary; taken once, by the screen it opens.
            seed = remember { container.takeWriteSeed().orEmpty() },
        )
    }

    screen(Routes.VOICE_PATTERN, padding, dateArg) { entry ->
        VoiceScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    screen(Routes.PEN_PATTERN, padding, dateArg) { entry ->
        PenScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    screen(
        Routes.RECAP_PATTERN,
        padding,
        listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
    ) {
        RecapScreen(
            onBack = { nav.popBackStack() },
            onOpenRecap = { period ->
                // Stepping to the previous or next period replaces this page, so Back
                // returns to the list rather than walking through every period seen.
                nav.popBackStack()
                nav.navigate(Routes.recap(period))
            },
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
            onWrite = { nav.navigate(Routes.write(it)) },
            onOpenPatterns = { nav.navigate(Routes.patterns(it)) },
        )
    }

    screen(
        Routes.PATTERNS_PATTERN,
        padding,
        listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
    ) {
        PatternsScreen(
            onBack = { nav.popBackStack() },
            onOpenPeriod = { period ->
                // Like the letters: stepping between periods replaces the page, so Back leaves Patterns.
                nav.popBackStack()
                nav.navigate(Routes.patterns(period))
            },
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
        )
    }

    screen(
        Routes.PIXELS_PATTERN,
        padding,
        listOf(navArgument(Routes.ARG_YEAR) { type = NavType.IntType }),
    ) {
        PixelsScreen(onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate(Routes.day(it)) })
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.DAYS, "Days", LineIcons.Days),
    Tab(Routes.RECAPS, "Recaps", LineIcons.Recaps),
    Tab(Routes.BOOKS, "Books", LineIcons.Books),
    Tab(Routes.REEL, "Reel", LineIcons.Reel),
)

/** The four tabs, on a tinted bar. The tab that is open sits on a blush pill. */
@Composable
private fun TabBar(current: String?, nav: NavHostController) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.BlushTint)
            .navigationBarsPadding()
            .height(80.dp)
            .selectableGroup(),
    ) {
        Tabs.forEach { tab ->
            val selected = tab.route == current
            val color = if (selected) Palette.Ink else Palette.Muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) {
                        if (!selected) {
                            nav.navigate(tab.route) {
                                // One copy of each tab, and each keeps its own state
                                // (the month shown, the year shown) while away.
                                popUpTo(Routes.DAYS) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 64.dp, height = 32.dp)
                        .clip(CircleShape)
                        .background(if (selected) Palette.Blush else Palette.BlushTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
                Text(tab.label, style = AppType.Tab, color = color, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
