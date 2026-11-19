package com.properrp.app.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.properrp.app.AppState
import com.properrp.app.Languages
import com.properrp.app.ProperRpApp
import com.properrp.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen(val title: Int) {
    HOME(R.string.app_name), STORY(R.string.screen_story), COSTS(R.string.screen_costs), FILES(R.string.screen_files),
    SETTINGS(R.string.screen_settings), STORIES(R.string.screen_stories), STYLE(R.string.screen_style),
}

class MainActivity : ComponentActivity() {
    private var shownLanguage: String? = null

    /** The whole activity in the app's language (the phone's, unless one is picked in Settings). */
    override fun attachBaseContext(base: Context) {
        val state = (base.applicationContext as ProperRpApp).state
        shownLanguage = state.languageTag
        super.attachBaseContext(Languages.wrap(base, state.locale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val state = (application as ProperRpApp).state
        setContent {
            // a new language: the activity starts again in it (every screen, every string)
            LaunchedEffect(state.languageTag) { if (state.languageTag != shownLanguage) recreate() }
            val dark = when (state.themeMode) {
                AppState.ThemeMode.LIGHT -> false
                AppState.ThemeMode.DARK -> true
                AppState.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            // status and navigation bar icons readable on the theme actually shown (also when it differs from the phone's)
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AppTheme(dark, materialYou = state.materialYou) { App(state) }
        }
    }

    override fun onResume() {
        super.onResume()
        // the phone's language changed while the app was away and the app follows it
        val state = (application as ProperRpApp).state
        if (state.languageTag == Languages.SYSTEM && resources.configuration.locales[0] != state.locale) {
            state.story.settingsChanged()
            recreate()
        }
    }
}

@Composable
fun App(state: AppState) {
    var onboarding by remember { mutableStateOf(!state.onboardingDone) }
    var scale by remember { mutableStateOf(state.textScale) }
    var home by remember { mutableStateOf<HomeInfo?>(null) }
    val stack = remember { mutableStateListOf(Screen.HOME) }
    val screen = stack.last()
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    BackHandler(enabled = !onboarding && stack.size > 1) { back() }
    val newStory: (com.properrp.app.story.StoryController.StorySummary?) -> Unit = { from ->
        if (state.story.startNewStory(from)) {
            // straight to "Your story": the rest of setup is done, and the style carries over
            state.onboardingStep = STEP_STORY; state.onboardingDone = false; onboarding = true
        }
    }
    val restartSetup: () -> Unit = { state.onboardingStep = 0; state.onboardingDone = false; onboarding = true }

    // home data reads files: off the main thread, fresh on each return
    LaunchedEffect(screen, onboarding) {
        if (!onboarding && screen == Screen.HOME) home = withContext(Dispatchers.IO) { runCatching { homeInfo(state) }.getOrNull() } ?: home
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalReadingScale provides scale) {
    // a Surface, so every text gets the theme's content colour (dark mode included)
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        if (onboarding) {
            OnboardingFlow(state) { onboarding = false; stack.clear(); stack.add(Screen.HOME) }
            return@Column
        }
        when (screen) {
            Screen.HOME -> home?.let { HomeContent(it, go = { s -> stack.add(s) }) } ?: Busy(stringResource(R.string.busy_opening_app))
            Screen.STORY -> StoryScreen(state, onBack = back)
            else -> {
                AppBar(stringResource(screen.title), back)
                when (screen) {
                    Screen.COSTS -> CostsScreen(state)
                    Screen.FILES -> FilesScreen(state)
                    Screen.SETTINGS -> SettingsScreen(state, onScale = { scale = it }, onPickStyle = { stack.add(Screen.STYLE) },
                        go = { s -> stack.add(s) }, onRestartSetup = restartSetup)
                    Screen.STORIES -> StoriesScreen(state, onContinue = { stack.add(Screen.STORY) }, onNewStory = newStory,
                        onSwitched = { home = null; stack.clear(); stack.add(Screen.HOME); stack.add(Screen.STORY) })
                    Screen.STYLE -> StyleScreen(state)
                    else -> {}
                }
            }
        }
    }
    }
    }
}

/** Everything the home screen shows. Reads files: call off the main thread. */
fun homeInfo(state: AppState): HomeInfo {
    val s = state.story
    val stories = s.stories()
    return HomeInfo(
        title = stories.firstOrNull { it.current }?.title, styleName = s.styleName(), storyCount = stories.size,
        place = s.place(), turn = s.turn(), chapter = s.chapter(), lastLines = s.lastLines(), totalCost = s.totalCost(),
        claudeCode = state.claudeCode, languageName = languageLabel(state),
    )
}

/** The language as the picker names it: the chosen one's own name, or the phone's for System. */
fun languageLabel(state: AppState): String =
    Languages.ALL.firstOrNull { it.first == state.languageTag }?.second ?: Languages.nativeName(state.locale)
