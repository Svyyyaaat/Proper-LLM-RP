package com.properrp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.AppState
import com.properrp.app.R
import com.properrp.core.story.StoryEngine
import kotlinx.coroutines.launch

const val SETUP_STEPS = 3
/** The step a new story starts from (the rest of setup is done already). */
const val STEP_STORY = 2

// ---------------------------------------------------------------- stateless step layouts

@Composable
fun WelcomeContent(language: String, onLanguage: () -> Unit, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Page(Modifier.weight(1f)) {
            Spacer(Modifier.height(24.dp))
            AppMark(64.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
            Text(stringResource(R.string.welcome_tagline), style = ReadingText.copy(fontSize = 22.sp, lineHeight = 30.sp), color = cs.accent)
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Feature(Icons.AutoMirrored.Filled.MenuBook, cs.primaryContainer, cs.onPrimaryContainer,
                stringResource(R.string.welcome_f1_title), stringResource(R.string.welcome_f1_body))
            Feature(Icons.Filled.Casino, cs.tertiaryContainer, cs.onTertiaryContainer,
                stringResource(R.string.welcome_f2_title), stringResource(R.string.welcome_f2_body))
            Feature(Icons.Filled.Translate, cs.accentContainer, cs.onAccentContainer,
                stringResource(R.string.welcome_f3_title), stringResource(R.string.welcome_f3_body))
            // the language first: everything after it is in that language
            SecondaryButton(stringResource(R.string.language_button, language), onLanguage, icon = Icons.Filled.Language)
        }
        StepFooter(stringResource(R.string.welcome_start), onStart)
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, container: androidx.compose.ui.graphics.Color,
                    content: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, container, content, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KeyStepContent(key: String, onKey: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit,
                   claudeCode: Boolean = false, onBackend: (Boolean) -> Unit = {}, claudeCodeReady: Boolean = false,
                   claudeCodeSetup: @Composable () -> Unit = {}) {
    StepFrame(onBack, footer = {
        if (claudeCode) StepFooter(stringResource(R.string.next), onNext, enabled = claudeCodeReady)
        else StepFooter(stringResource(R.string.key_save), onNext, enabled = key.trim().startsWith("sk-ant-"))
    }) {
        StepHeader(1, SETUP_STEPS, stringResource(R.string.key_title),
            stringResource(if (claudeCode) R.string.key_body_cc else R.string.key_body_api))
        Segmented(listOf(stringResource(R.string.backend_api), stringResource(R.string.backend_cc)), if (claudeCode) 1 else 0) { onBackend(it == 1) }
        if (claudeCode) {
            PaperCard { claudeCodeSetup() }
            if (!claudeCodeReady) Text(stringResource(R.string.key_cc_unlocks), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StepFrame
        }
        OutlinedTextField(key, onKey, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backend_api)) }, placeholder = { Text("sk-ant-…") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.medium)
        Text(stringResource(R.string.key_stored), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StoryStepContent(seed: String, onSeed: (String) -> Unit, busy: String?, error: String?, onCreate: () -> Unit, onBack: () -> Unit,
                     language: String = "") {
    StepFrame(onBack, footer = { StepFooter(stringResource(R.string.story_create), onCreate, enabled = busy == null) }) {
        StepHeader(2, SETUP_STEPS, stringResource(R.string.story_title), stringResource(R.string.story_body))
        OutlinedTextField(seed, onSeed, Modifier.fillMaxWidth().heightIn(min = 120.dp), shape = MaterialTheme.shapes.medium,
            label = { Text(stringResource(R.string.story_seed_label)) }, placeholder = { Text(stringResource(R.string.story_seed_hint)) })
        if (language.isNotBlank()) Text(stringResource(R.string.story_language_note, language), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy != null) Busy(busy)
        if (error != null) ErrorNote(error)
    }
}

@Composable
fun StyleStepContent(options: List<StoryEngine.StyleOption>?, busy: String?, error: String?, onPick: (StoryEngine.StyleOption) -> Unit,
                     onMore: () -> Unit, onBack: () -> Unit) {
    StepFrame(onBack, footer = {}) {
        StepHeader(3, SETUP_STEPS, stringResource(R.string.style_step_title), stringResource(R.string.style_step_body))
        if (busy != null) Busy(busy)
        if (error != null) ErrorNote(error, onMore)
        for (o in options.orEmpty()) {
            PaperCard {
                Text(styleTitle(o.name), style = MaterialTheme.typography.titleSmall)
                Text(o.passage, style = ReadingText.copy(fontSize = 17.sp, lineHeight = 27.sp))
                androidx.compose.material3.FilledTonalButton({ onPick(o) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.style_this_one))
                }
            }
        }
        if (options != null && busy == null) SecondaryButton(stringResource(R.string.style_four_more), onMore, icon = Icons.Filled.Refresh)
    }
}

/** A setup step: back arrow, scrolling content, buttons pinned below. */
@Composable
fun StepFrame(onBack: () -> Unit, footer: @Composable () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        AppBar("", onBack = onBack)
        Page(Modifier.weight(1f), content = content)
        footer()
    }
}

// ---------------------------------------------------------------- the flow

/** Welcome, then three steps; progress is saved, so leaving the app resumes where you were. */
@Composable
fun OnboardingFlow(state: AppState, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(state.onboardingStep) }
    fun go(n: Int) { step = n; state.onboardingStep = n }
    val back = { go((step - 1).coerceAtLeast(0)) }
    androidx.activity.compose.BackHandler(enabled = step > 0) { back() }

    when (step) {
        0 -> {
            var picking by remember { mutableStateOf(false) }
            WelcomeContent(languageLabel(state), onLanguage = { picking = true }) { go(1) }
            if (picking) LanguageDialog(state.languageTag, systemLanguageName(), onPick = { state.chooseLanguage(it); picking = false }) { picking = false }
        }
        1 -> {
            if (state.story.canCallClaude()) { LaunchedEffect(Unit) { go(2) }; return }
            var key by remember { mutableStateOf("") }
            var tested by remember { mutableStateOf(false) }
            KeyStepContent(key, { key = it }, onNext = { if (!state.claudeCode) state.story.saveKey(key.trim()); go(2) }, onBack = back,
                claudeCode = state.claudeCode, onBackend = { state.chooseClaudeCode(it) }, claudeCodeReady = tested,
                claudeCodeSetup = { ClaudeCodeSetup(state, onReady = { tested = true }, intro = false) })
        }
        2 -> {
            if (state.story.isSetUp()) { LaunchedEffect(Unit) { go(3) }; return }
            var seed by remember { mutableStateOf("") }
            StoryStepContent(seed, { seed = it }, state.story.busy, state.story.error,
                onCreate = { scope.launch { state.story.setup(seed); if (state.story.isSetUp()) go(3) } }, onBack = back,
                language = state.locale.getDisplayLanguage(state.locale))
        }
        else -> {
            // a new story keeps the style you picked before
            if (state.story.hasStyle()) { LaunchedEffect(Unit) { state.onboardingDone = true; onFinished() }; return }
            // the built-in styles show at once; "four more" asks Claude for new ones, in the story's language
            var options by remember { mutableStateOf<List<StoryEngine.StyleOption>?>(state.story.presets()) }
            StyleStepContent(options, state.story.busy, state.story.error,
                onPick = { o -> state.story.pickStyle(o); state.onboardingDone = true; onFinished() },
                onMore = { scope.launch { state.story.styleOptions(fresh = true)?.let { options = it } } },
                onBack = { if (state.story.restoreStyle()) { state.onboardingDone = true; onFinished() } else back() })
        }
    }
}

/** The phone's own language by its own name, for the picker's System line. */
fun systemLanguageName(): String = com.properrp.app.Languages.nativeName(com.properrp.app.Languages.system())
