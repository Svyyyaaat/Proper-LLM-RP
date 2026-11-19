package com.properrp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.AppState
import com.properrp.app.R
import com.properrp.app.story.ShownPassage
import com.properrp.app.story.ShownTurn
import com.properrp.app.story.StoryController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- stateless pieces

@Composable
fun PlayerBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // tonal, not filled: your line frames the passage, the passage is what you read
        Surface(color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun Ornament() {
    // a short rule with an accent dot between passages
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.padding(horizontal = 10.dp).size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.accent))
        Box(Modifier.width(36.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** The passage as reading text (selectable, so a line can be copied). */
@Composable
fun PassageText(p: ShownPassage) {
    SelectionContainer { Text(p.text, style = readingStyle(), color = MaterialTheme.colorScheme.onBackground) }
}

@Composable
fun PassageTools(p: ShownPassage, enabled: Boolean, onReroll: () -> Unit, onEdit: () -> Unit,
                 onVersion: (Int) -> Unit = {}, listening: Boolean = false, onListen: (() -> Unit)? = null) {
    var notes by remember { mutableStateOf(false) }
    // a quiet row of round tools; the passage is what matters
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onListen != null) Tool(if (listening) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(if (listening) R.string.tool_stop else R.string.tool_listen), active = listening, onClick = onListen)
        Tool(Icons.Filled.Refresh, stringResource(R.string.tool_rewrite), enabled = enabled, onClick = onReroll)
        Tool(Icons.Filled.Edit, stringResource(R.string.tool_edit), enabled = enabled, onClick = onEdit)
        Tool(Icons.Filled.Casino, stringResource(R.string.tool_behind), active = notes) { notes = !notes }
        Spacer(Modifier.weight(1f))
        if (p.versions > 1) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onVersion(p.version - 1) }, enabled = enabled && p.version > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.version_previous))
            }
            Text(stringResource(R.string.version_of, p.version + 1, p.versions), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton({ onVersion(p.version + 1) }, enabled = enabled && p.version < p.versions - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.version_next))
            }
        }
    }
    if (notes) PaperCard {
        Text(stringResource(R.string.behind_title), style = MaterialTheme.typography.titleSmall)
        (p.gmNotes + p.checks).ifEmpty { listOf(stringResource(R.string.behind_nothing)) }
            .forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ReplyBar(busy: String?, canSend: Boolean, input: String, onInput: (String) -> Unit, onSend: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding()) {
            if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, onInput, Modifier.weight(1f).heightIn(min = 52.dp),
                    placeholder = { Text(stringResource(if (busy != null) R.string.reply_waiting else R.string.reply_hint), maxLines = 1) },
                    shape = RoundedCornerShape(26.dp), maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
                if (input.isBlank()) {
                    // nothing typed: let the scene go on
                    FilledTonalButton({ onSend("") }, Modifier.heightIn(min = 52.dp), enabled = canSend && busy == null,
                        contentPadding = PaddingValues(start = 18.dp, end = 14.dp)) {
                        Text(stringResource(R.string.go_on)); Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                    }
                } else {
                    FilledIconButton({ onSend(input) }, Modifier.size(52.dp), enabled = canSend && busy == null,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.accent, contentColor = MaterialTheme.colorScheme.onAccent)) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

/** The whole reading screen, from data: used by the app and by the screenshot tests. */
@Composable
fun StoryContent(
    title: String, history: List<ShownTurn>, current: ShownTurn?, busy: String?, error: String?,
    onBack: () -> Unit, onReroll: () -> Unit, onEdit: () -> Unit, onSend: (String) -> Unit,
    reply: String = "", onReply: (String) -> Unit = {}, onRetry: (() -> Unit)? = null, onVersion: (Int) -> Unit = {},
    /** Who you are in the story, shown above the first passage. */
    intro: String? = null,
    /** A note above the reply box (no voice for Listen…). */
    notice: String? = null,
    listening: Boolean = false,
    onListen: ((String) -> Unit)? = null,
    /** The passage streaming in while it's written: (phase, text). */
    draft: Pair<String, String>? = null,
    /** The message just sent, shown as a bubble while its turn is written. */
    sending: String? = null,
    onInfo: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size, current) {
        val n = history.size + (if (current != null) 1 else 0) + (if (intro != null && history.isEmpty()) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        AppBar(title, onBack = onBack) {
            if (onInfo != null) IconButton(onInfo) { Icon(Icons.Outlined.Info, stringResource(R.string.info_title)) }
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (intro != null && history.isEmpty()) item {
                PaperCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(Icons.Filled.Person, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, 32.dp)
                        Text(stringResource(R.string.you_are), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(intro, style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(history) { t ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    t.player?.let { PlayerBubble(it) }
                    PassageText(t.passage)
                    Ornament()
                }
            }
            if (current != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    current.player?.let { PlayerBubble(it) }
                    // a reroll streams its new version in place of the old one
                    if (busy != null && draft != null) DraftPassage(busy, draft.second)
                    else PassageText(current.passage)
                    PassageTools(current.passage, busy == null, onReroll, onEdit, onVersion, listening,
                        onListen?.let { l -> { l(current.passage.text) } })
                    // sent while this passage is still being recorded: the bubble shows already
                    if (busy != null && sending != null) PlayerBubble(sending)
                }
            } else if (busy != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // your message, at once, above what's being written for it
                    sending?.let { PlayerBubble(it) }
                    if (draft != null) DraftPassage(busy, draft.second) else Busy(busy)
                }
            }
            if (error != null) item { ErrorNote(error, onRetry) }
        }
        if (notice != null && busy == null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ReplyBar(busy, current != null, reply, onReply, onSend)
    }
}

// ---------------------------------------------------------------- the screen

@Composable
fun StoryScreen(state: AppState, onBack: () -> Unit) {
    val story = state.story
    // once: the notification shown while a passage is written in the background (Android 13+ asks)
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotify", false) &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("askedNotify", true).apply()
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var info by remember { mutableStateOf<StoryController.StoryInfo?>(null) }
    var role by remember { mutableStateOf<String?>(null) }
    var infoWanted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { role = withContext(Dispatchers.IO) { story.role() } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.speaker.stop() } }
    LaunchedEffect(infoWanted) { if (infoWanted) { info = withContext(Dispatchers.IO) { story.storyInfo() }; infoWanted = false } }
    var editing by remember { mutableStateOf<String?>(null) }
    // start() is safe to repeat: it shows the waiting passage, finishes a half-done turn, or answers an
    // unsent reply; an error left over from elsewhere (setup, styles) must not keep the story from opening
    LaunchedEffect(Unit) { if (story.current == null && !story.isWorking) story.launch { start() } }

    StoryContent(
        title = story.title ?: stringResource(R.string.screen_story), history = story.history, current = story.current,
        busy = story.busy, error = story.error,
        onBack = onBack,
        onReroll = { story.launch { reroll() } },
        onEdit = { editing = story.current?.passage?.text },
        onSend = { m -> story.submit(m) },
        reply = story.replyDraft, onReply = { story.replyDraft = it }, onRetry = story.retry,
        onVersion = { story.selectVersion(it) },
        intro = role, onInfo = { infoWanted = true },
        notice = state.speaker.problem()?.let { stringResource(it) },
        listening = state.speaker.speaking,
        draft = story.draft, sending = story.sending,
        onListen = { text -> if (state.speaker.speaking) state.speaker.stop() else state.speaker.say(text) },
    )
    info?.let { i -> StoryInfoSheet(i) { info = null } }
    editing?.let { text ->
        var t by remember { mutableStateOf(text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(stringResource(R.string.edit_title)) },
            text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp), textStyle = ReadingText.copy(fontSize = 16.sp, lineHeight = 24.sp)) },
            confirmButton = { TextButton({ editing = null; story.launch { edit(t) } }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryInfoSheet(info: StoryController.StoryInfo, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        StoryInfoBody(info)
    }
}

@Composable
fun StoryInfoBody(info: StoryController.StoryInfo) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge)
        info.role?.let { sheet ->
            Text(stringResource(R.string.info_you), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accent)
            // the sheet's own headings (What you know, Established in the story) as small labels
            sheet.lines().filter { it.isNotBlank() }.forEach { l ->
                if (l.startsWith("## ")) Text(l.removePrefix("## "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                else Text(l, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (info.remembers.isNotEmpty()) {
            Text(stringResource(R.string.info_remember), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accent)
            info.remembers.take(30).forEach { (before, text) ->
                Text("· " + if (before) stringResource(R.string.info_before_story, text) else text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (info.met.isNotEmpty()) {
            Text(stringResource(R.string.info_met), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accent)
            Text(info.met.joinToString(", "), style = readingStyle().copy(fontSize = 18.sp, lineHeight = 28.sp))
        }
        if (info.spent > 0) Text(stringResource(R.string.info_spent, "$%.2f".format(info.spentSession), "$%.2f".format(info.spent)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.info_chapters), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accent)
        if (info.chapters.isEmpty()) Text(stringResource(R.string.info_no_chapters),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        info.chapters.forEach { (n, text) ->
            Text(stringResource(R.string.info_chapter, n), style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The passage while it's being written: readable at once, muted until it's checked. */
@Composable
fun DraftPassage(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = readingStyle(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
    }
}

/** A round tool button under the passage. */
@Composable
private fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    // icon and its name: nobody has to guess what a die or a pencil does here
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.FilledTonalIconButton(onClick, enabled = enabled,
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Icon(icon, null, Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f))
    }
}
