package com.properrp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.properrp.app.AppState
import com.properrp.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SETUP_STEPS = 2
/** The step a new story starts from (the rest of setup is done already). */
const val STEP_STORY = 2

// ---------------------------------------------------------------- stateless step layouts

/** What this is in three lines, the language within reach, and one way on. */
@Composable
fun WelcomeContent(language: String, onLanguage: () -> Unit, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onLanguage) { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(language) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DieFace(6, 44.dp, cs.onBackground, cs.background)
                DieFace(3, 44.dp, cs.primary, cs.onPrimary)
            }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.welcome_tagline), style = readingStyle().copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                color = cs.primary)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            for ((t, b) in listOf(R.string.welcome_f1_title to R.string.welcome_f1_body, R.string.welcome_f2_title to R.string.welcome_f2_body,
                R.string.welcome_f3_title to R.string.welcome_f3_body)) {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(t), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        StepFooter(stringResource(R.string.welcome_start), onStart)
    }
}

@Composable
fun KeyStepContent(key: String, onKey: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit,
                   claudeCode: Boolean = false, onBackend: (Boolean) -> Unit = {}, claudeCodeReady: Boolean = false,
                   claudeCodeSetup: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val looksRight = key.trim().startsWith("sk-ant-")
    var shown by remember { mutableStateOf(false) }
    StepFrame(onBack, footer = {
        if (claudeCode) StepFooter(stringResource(R.string.next), onNext, enabled = claudeCodeReady)
        else StepFooter(stringResource(R.string.key_save), onNext, enabled = looksRight)
    }) {
        StepHeader(1, SETUP_STEPS, stringResource(R.string.key_title),
            stringResource(if (claudeCode) R.string.key_body_cc else R.string.key_body_api))
        if (claudeCode) {
            PaperCard { claudeCodeSetup() }
            if (!claudeCodeReady) Text(stringResource(R.string.key_cc_unlocks), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            QuietButton(stringResource(R.string.key_use_api), { onBackend(false) })
            return@StepFrame
        }
        // a Claude subscription is the other way in: right under the heading, not after the key steps
        SecondaryButton(stringResource(R.string.key_use_cc), { onBackend(true) })
        // getting a key is the real task for someone new: the three steps, then the place to paste it
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.key_step1, R.string.key_step2, R.string.key_step3).forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.surfaceContainerHigh),
                        contentAlignment = Alignment.Center) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = cs.primary) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(step), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        SecondaryButton(stringResource(R.string.key_get), { runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys")))
        } })
        OutlinedTextField(key, onKey, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backend_api)) }, placeholder = { Text("sk-ant-…") },
            singleLine = true, shape = MaterialTheme.shapes.medium,
            // a key in the wrong format is shown: you can see what was pasted (a missing piece, a word in front)
            visualTransformation = if (shown || (key.isNotBlank() && !looksRight)) androidx.compose.ui.text.input.VisualTransformation.None
                else PasswordVisualTransformation(),
            isError = key.isNotBlank() && !looksRight,
            supportingText = { Text(stringResource(if (key.isNotBlank() && !looksRight) R.string.key_looks_wrong else R.string.key_stored)) },
            trailingIcon = {
                Row {
                    if (key.isNotBlank()) IconButton({ shown = !shown }) {
                        Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            stringResource(if (shown) R.string.key_hide else R.string.key_show))
                    } else TextButton({ clipboard.getText()?.text?.trim()?.let(onKey) }) { Text(stringResource(R.string.key_paste)) }
                }
            })
    }
}

/**
 * What the story is about: one line (or nothing, and the story surprises you), a few starting points to tap,
 * and your own files for those who have them. The button creates it; the story screen shows it being built.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryStepContent(seed: String, onSeed: (String) -> Unit, onCreate: () -> Unit, onBack: () -> Unit,
                     /** Who the player plays (optional): the most important thing in a roleplay, asked for up front. */
                     hero: String = "", onHero: (String) -> Unit = {},
                     language: String = "",
                     /** The text files given to the story, and how to add or drop one. */
                     files: List<String> = emptyList(), onAddFiles: () -> Unit = {}, onRemoveFile: (String) -> Unit = {},
                     fileProblem: String? = null,
                     /** The earlier story this one continues (its world and people), if any. */
                     sequelOf: String? = null,
                     /** Starting points to tap (filled into the box). */
                     examples: List<String> = emptyList()) {
    val cs = MaterialTheme.colorScheme
    StepFrame(onBack, footer = { StepFooter(stringResource(when {
        sequelOf != null -> R.string.story_continue
        seed.isBlank() && hero.isBlank() && files.isEmpty() -> R.string.story_create_surprise
        else -> R.string.story_create
    }), onCreate) }) {
        StepHeader(2, SETUP_STEPS, stringResource(if (sequelOf != null) R.string.story_title_sequel else R.string.story_title),
            if (sequelOf != null) stringResource(R.string.story_sequel_note, sequelOf) else stringResource(R.string.story_body))
        OutlinedTextField(seed, onSeed, Modifier.fillMaxWidth().heightIn(min = 112.dp), shape = MaterialTheme.shapes.medium,
            label = { Text(stringResource(if (sequelOf != null) R.string.story_sequel_seed_label else R.string.story_seed_label)) },
            placeholder = { Text(stringResource(if (sequelOf != null) R.string.story_sequel_seed_hint else R.string.story_seed_hint)) })
        // one scrolling row that stays: try one, then another, without clearing the box first
        if (sequelOf == null && examples.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in examples) Text(e, Modifier.clip(MaterialTheme.shapes.small)
                .background(if (e == seed) cs.primaryContainer else cs.surfaceContainerHigh).clickable { onSeed(e) }
                .padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (e == seed) cs.onPrimaryContainer else cs.onSurface, maxLines = 1)
        }
        if (sequelOf == null) OutlinedTextField(hero, onHero, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            label = { Text(stringResource(R.string.story_hero_label)) }, placeholder = { Text(stringResource(R.string.story_hero_hint)) })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.story_files_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(if (sequelOf != null) R.string.story_files_sequel_about else R.string.story_files_about),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        for (f in files) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, Modifier.size(18.dp), tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Text(f, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton({ onRemoveFile(f) }) { Icon(Icons.Filled.Close, stringResource(R.string.story_files_remove, f), Modifier.size(18.dp)) }
        }
        if (fileProblem != null) Text(fileProblem, style = MaterialTheme.typography.bodySmall, color = cs.error)
        QuietButton(stringResource(R.string.story_files_add), onAddFiles, icon = Icons.Filled.AttachFile)
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

/** Welcome, then two steps (Claude, the story); progress is saved, so leaving the app resumes where you were. */
@Composable
fun OnboardingFlow(state: AppState, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(state.onboardingStep.coerceIn(0, 2)) }
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
        else -> {
            val story = state.story
            // a second run of setup with a story already there: nothing to create
            val starting = remember { !story.isSetUp() || newStoryAsked(state) }
            if (!starting) { LaunchedEffect(Unit) { state.onboardingDone = true; onFinished() }; return }
            var files by remember { mutableStateOf(story.attachments()) }
            var fileProblem by remember { mutableStateOf<String?>(null) }
            val sequel = remember { story.sequelTitle() }
            // several at once; the type filter is loose (phones label .md and .json oddly): text is checked on reading
            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                scope.launch {
                    val problems = withContext(Dispatchers.IO) { uris.mapNotNull { story.attach(it) } }
                    fileProblem = problems.joinToString("\n").ifEmpty { null }
                    files = story.attachments()
                }
            }
            StoryStepContent(story.seedDraft, { story.seedDraft = it }, hero = story.heroDraft, onHero = { story.heroDraft = it },
                onCreate = {
                    // created on the story screen, which shows it being built
                    if (story.commitNewStory()) { state.onboardingDone = true; onFinished() }
                },
                // from "New story": back to the story you were in; on the first run: back to connecting Claude
                onBack = { if (story.isSetUp()) { story.cancelNewStory(); state.onboardingDone = true; onFinished() } else back() },
                language = state.locale.getDisplayLanguage(state.locale),
                files = files, onAddFiles = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*")) },
                onRemoveFile = { story.detach(it); files = story.attachments() }, fileProblem = fileProblem,
                sequelOf = sequel, examples = seedExamples())
        }
    }
}

/** A new story was asked for from inside the app (not the first run). */
private fun newStoryAsked(state: AppState) = state.context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).contains("newFrom")

@Composable
private fun seedExamples(): List<String> = listOf(R.string.seed_1, R.string.seed_2, R.string.seed_3, R.string.seed_4, R.string.seed_5).map { stringResource(it) }

/** The phone's own language by its own name, for the picker's System line. */
fun systemLanguageName(): String = com.properrp.app.Languages.nativeName(com.properrp.app.Languages.system())
