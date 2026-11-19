package com.properrp.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.AppState
import com.properrp.app.R
import com.properrp.app.story.StoryController.StorySummary
import com.properrp.core.story.StoryEngine.StyleOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- stories

@Composable
fun StoriesScreen(state: AppState, onContinue: () -> Unit, onNewStory: (StorySummary?) -> Unit, onSwitched: () -> Unit) {
    var stories by remember { mutableStateOf<List<StorySummary>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    val busyText = stringResource(R.string.stories_switch_busy)
    val failedText = stringResource(R.string.stories_switch_failed)
    LaunchedEffect(tick) { stories = withContext(Dispatchers.IO) { state.story.stories() } }
    val list = stories ?: return Busy(stringResource(R.string.busy_opening_stories))
    StoriesContent(list, onContinue, onNewStory, problem, writing = state.story.isWorking, onSwitch = { s ->
        if (state.story.switchTo(s.dir)) { problem = null; tick++; onSwitched() }
        else problem = if (state.story.isWorking) busyText else failedText
    })
}

/** Your stories: the one being played as an open book with Continue, the earlier ones as a quiet shelf. */
@Composable
fun StoriesContent(stories: List<StorySummary>, onContinue: () -> Unit,
                   /** A new story: fresh (null), or with the world and people of the one given. */
                   onNewStory: (StorySummary?) -> Unit, problem: String? = null,
                   writing: Boolean = false, onSwitch: (StorySummary) -> Unit) {
    var confirmNew by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf<StorySummary?>(null) }
    val current = stories.firstOrNull { it.current }
    val others = stories.filter { !it.current }
    val cs = MaterialTheme.colorScheme
    val untitled = stringResource(R.string.untitled)
    Page {
        if (problem != null) ErrorNote(problem)
        if (writing) Text(stringResource(R.string.stories_writing), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        if (current != null) PaperCard {
            Text(current.title ?: untitled, style = ReadingText.copy(fontSize = 24.sp, lineHeight = 32.sp), color = cs.onSurface)
            Text(progress(current), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            current.lastLines?.let {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = ReadingText.copy(fontSize = 16.sp, lineHeight = 26.sp), color = cs.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            PrimaryButton(stringResource(if (current.turn == 0) R.string.begin else R.string.continue_story), onContinue, icon = Icons.AutoMirrored.Filled.MenuBook)
        } else {
            // nothing set up: the only thing to do here is start one
            Text(stringResource(R.string.stories_none), style = ReadingText.copy(fontSize = 22.sp, lineHeight = 30.sp))
            PrimaryButton(stringResource(R.string.stories_start), { onNewStory(null) }, icon = Icons.Filled.Add)
        }
        if (current != null) SecondaryButton(stringResource(R.string.stories_new), { confirmNew = true }, icon = Icons.Filled.Add, enabled = !writing)
        if (others.isNotEmpty()) {
            SectionTitle(stringResource(R.string.stories_earlier))
            Column {
                others.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                    ShelfRow(s, enabled = !writing) { confirmSwitch = s }
                }
            }
        } else if (current != null) {
            Text(stringResource(R.string.stories_many), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text(stringResource(R.string.stories_new_q)) },
        text = { Text(stringResource(R.string.stories_new_keeps, current?.title ?: untitled) + "\n\n" + stringResource(R.string.stories_new_choice)) },
        confirmButton = {
            Row {
                TextButton({ confirmNew = false; onNewStory(null) }) { Text(stringResource(R.string.stories_fresh)) }
                TextButton({ confirmNew = false; onNewStory(current) }) { Text(stringResource(R.string.stories_keep_cast)) }
            }
        },
        dismissButton = { TextButton({ confirmNew = false }) { Text(stringResource(R.string.cancel)) } })
    confirmSwitch?.let { s ->
        AlertDialog(onDismissRequest = { confirmSwitch = null }, title = { Text(stringResource(R.string.stories_continue_q, s.title ?: untitled)) },
            text = { Text(stringResource(R.string.stories_continue_where) + (current?.let { " " + stringResource(R.string.stories_stays, it.title ?: untitled) } ?: "")) },
            confirmButton = { TextButton({ confirmSwitch = null; onSwitch(s) }) { Text(stringResource(R.string.stories_continue_it)) } },
            dismissButton = { TextButton({ confirmSwitch = null; onNewStory(s) }) { Text(stringResource(R.string.stories_new_with_cast)) } })
    }
}

/** An earlier story on the shelf: its title in the reading face, where it stands, when you last played it. */
@Composable
private fun ShelfRow(s: StorySummary, enabled: Boolean, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onPick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.title ?: stringResource(R.string.untitled), style = ReadingText.copy(fontSize = 18.sp, lineHeight = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(progress(s), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ago(s.lastActive)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        }
        TextButton(onPick, enabled = enabled) { Text(stringResource(R.string.continue_story)) }
    }
}

@Composable
private fun progress(s: StorySummary): String {
    val where = if (s.turn == 0) stringResource(R.string.not_started) else storyProgress(s.chapter, s.turn, s.place)
    return where + (s.style?.let { " · " + styleTitle(it) } ?: "")
}

@Composable
private fun ago(at: Long): String? {
    val d = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        at <= 0 -> null
        d < 1 -> stringResource(R.string.played_today)
        d < 2 -> stringResource(R.string.played_yesterday)
        d < 30 -> pluralStringResource(R.plurals.played_days_ago, d, d)
        else -> stringResource(R.string.played_on, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM,
            androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(java.util.Date(at)))
    }
}

// ---------------------------------------------------------------- style

/** A built-in style's name in the app's language; a generated style keeps the name it was given. */
@Composable
fun styleTitle(name: String): String = when (name) {
    "Mostly dialogue" -> stringResource(R.string.preset_dialogue)
    "Light and wry" -> stringResource(R.string.preset_wry)
    "Plain storybook" -> stringResource(R.string.preset_storybook)
    "Hard-boiled" -> stringResource(R.string.preset_hardboiled)
    else -> name
}

@Composable
private fun styleBlurb(o: StyleOption): String = when (o.name) {
    "Mostly dialogue" -> stringResource(R.string.preset_dialogue_about)
    "Light and wry" -> stringResource(R.string.preset_wry_about)
    "Plain storybook" -> stringResource(R.string.preset_storybook_about)
    "Hard-boiled" -> stringResource(R.string.preset_hardboiled_about)
    else -> o.line
}

@Composable
fun StyleScreen(state: AppState) {
    val story = state.story
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(story.styleName()) }
    var sample by remember { mutableStateOf(story.styleSample()) }
    var generated by remember { mutableStateOf<List<StyleOption>?>(null) }
    val presets = remember { story.presets() }
    StyleContent(current, sample, presets, generated, story.busy, story.error,
        onPick = { o -> story.pickStyle(o); current = story.styleName(); sample = story.styleSample() },
        onGenerate = { scope.launch { generated = story.styleOptions(fresh = generated != null) } })
}

@Composable
fun StyleContent(currentName: String?, currentSample: String?, presets: List<StyleOption>, generated: List<StyleOption>?,
                 busy: String?, error: String?, onPick: (StyleOption) -> Unit, onGenerate: () -> Unit) {
    Page {
        Text(stringResource(R.string.style_screen_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // the one in use first: you see what you have, then the alternatives
        for (o in presets.sortedByDescending { it.passage.trim() == currentSample?.trim() })
            StylePage(o, o.passage.trim() == currentSample?.trim(), onPick)
        // a style picked earlier from generated ones, not in the list: still shown, so you can see what you have
        if (currentSample != null && (presets + generated.orEmpty()).none { it.passage.trim() == currentSample.trim() })
            StylePage(StyleOption(currentName ?: stringResource(R.string.style_current), "", currentSample), true, onPick)
        SectionTitle(stringResource(R.string.style_other))
        if (generated != null) for (o in generated) StylePage(o, o.passage.trim() == currentSample?.trim(), onPick)
        if (busy != null) Busy(busy)
        else SecondaryButton(stringResource(if (generated == null) R.string.style_write_four else R.string.style_write_more), onGenerate, icon = Icons.Filled.AutoAwesome)
        if (error != null) ErrorNote(error, onGenerate)
    }
}

/** One style as a page of its sample; the one in use carries a border. */
@Composable
private fun StylePage(o: StyleOption, inUse: Boolean, onPick: (StyleOption) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = cs.surfaceContainerLowest,
        border = if (inUse) BorderStroke(2.dp, cs.primary) else null) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(styleTitle(o.name), style = MaterialTheme.typography.titleMedium)
                    val about = styleBlurb(o)
                    if (about.isNotBlank()) Text(about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (inUse) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = cs.primary)
                    Text(stringResource(R.string.style_in_use), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
            var open by remember { mutableStateOf(false) }
            Text(o.passage, style = ReadingText.copy(fontSize = 17.sp, lineHeight = 27.sp), maxLines = if (open) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis)
            if (!open && o.passage.lines().size > 6) TextButton({ open = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.style_read_whole))
            }
            if (!inUse) FilledTonalButton({ onPick(o) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.style_use))
            }
        }
    }
}
