package com.properrp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.AppState
import com.properrp.app.R
import com.properrp.app.story.ShownPassage
import com.properrp.app.story.ShownTurn
import com.properrp.app.story.StoryController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- pieces

/** Your line: set right, on a lighter patch of felt; the story is what's read, your line frames it. */
@Composable
fun PlayerBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 340.dp)) {
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** A passage as reading text (selectable, so a line can be copied). */
@Composable
fun PassageText(p: ShownPassage) {
    SelectionContainer { Text(p.text, style = readingStyle(), color = MaterialTheme.colorScheme.onBackground) }
}

/** One turn: your line, the dice if it was risky, what happened. */
@Composable
fun TurnBlock(player: String?, passage: ShownPassage, body: @Composable () -> Unit = { PassageText(passage) }) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        player?.let { PlayerBubble(it) }
        passage.roll?.let { RollLine(it) }
        body()
    }
}

/** The passage's own tools: quiet text buttons, under the text, so they never compete with replying. */
@Composable
fun PassageTools(p: ShownPassage, enabled: Boolean, onReroll: () -> Unit, onEdit: () -> Unit,
                 onVersion: (Int) -> Unit = {}, listening: Boolean = false, onListen: (() -> Unit)? = null) {
    var notes by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SmallTool(Icons.Filled.Refresh, stringResource(R.string.tool_rewrite), enabled, onReroll)
        SmallTool(Icons.Filled.Edit, stringResource(R.string.tool_edit), enabled, onEdit)
        if (onListen != null) SmallTool(if (listening) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(if (listening) R.string.tool_stop else R.string.tool_listen), true, onListen)
        Spacer(Modifier.weight(1f))
        IconButton({ notes = !notes }, Modifier.size(36.dp)) {
            Icon(Icons.Filled.Casino, stringResource(R.string.tool_behind), Modifier.size(18.dp),
                tint = if (notes) cs.primary else cs.onSurfaceVariant)
        }
    }
    // after a rewrite: the versions, to go back to one you liked better
    if (p.versions > 1) Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onVersion(p.version - 1) }, enabled = enabled && p.version > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.version_previous))
        }
        Text(stringResource(R.string.version_of, p.version + 1, p.versions), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        IconButton({ onVersion(p.version + 1) }, enabled = enabled && p.version < p.versions - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.version_next))
        }
    }
    if (notes) Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(cs.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.behind_title), style = MaterialTheme.typography.titleSmall)
        (p.gmNotes + p.checks).ifEmpty { listOf(stringResource(R.string.behind_nothing)) }
            .forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

@Composable
private fun SmallTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Replying, the one thing this screen is for: the box, and next to it Send (or Go on when the box is empty).
 * Ideas sit at its left edge: when you don't know what to write, the help is where you write.
 */
@Composable
fun ReplyBar(busy: String?, canSend: Boolean, input: String, onInput: (String) -> Unit, onSend: (String) -> Unit,
             ideas: List<String> = emptyList(), thinking: Boolean = false, onIdeas: (() -> Unit)? = null,
             /** The very first turn: say what the box and the button do. */
             firstTurn: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary, trackColor = cs.surfaceContainerHigh)
            Column(Modifier.wrapCentered().fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ideas.isNotEmpty() && input.isBlank() && busy == null) Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (idea in ideas) Text(idea, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(cs.surfaceContainerHigh).clickable { onInput(idea) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                }
                if (busy == null && canSend && input.isBlank() && (onIdeas != null || firstTurn))
                    Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onIdeas != null && ideas.isEmpty()) TextButton(onIdeas, enabled = !thinking,
                            contentPadding = PaddingValues(horizontal = 10.dp)) {
                            if (thinking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                            else Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(if (thinking) R.string.ideas_thinking else R.string.tool_ideas))
                        }
                        if (firstTurn && !thinking) Text(stringResource(R.string.first_turn_hint), Modifier.weight(1f).padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, onInput, Modifier.weight(1f).heightIn(min = 52.dp),
                        placeholder = { Text(stringResource(if (busy != null) R.string.reply_waiting else R.string.reply_hint), maxLines = 1,
                            overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(26.dp), maxLines = 6, textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = cs.surfaceContainerLowest, focusedContainerColor = cs.surfaceContainerLowest,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, focusedBorderColor = cs.primary))
                    Spacer(Modifier.width(8.dp))
                    if (input.isBlank()) {
                        // nothing typed: let the scene go on
                        androidx.compose.material3.FilledTonalButton({ onSend("") }, Modifier.heightIn(min = 52.dp), enabled = canSend && busy == null,
                            contentPadding = PaddingValues(start = 18.dp, end = 12.dp)) {
                            Text(stringResource(R.string.go_on)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                        }
                    } else {
                        FilledIconButton({ onSend(input) }, Modifier.size(52.dp), enabled = canSend && busy == null,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary)) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                        }
                    }
                }
            }
        }
    }
}

/** Keeps a column at [ContentWidth] on wide screens; full width on a phone. */
fun Modifier.wrapCentered(): Modifier = this.then(Modifier.widthIn(max = ContentWidth))

/**
 * A wait with nothing to stream yet: what's happening, as a list of stages (done ones ticked), so a long first
 * wait reads as progress rather than a frozen screen.
 */
@Composable
fun Stages(steps: List<String>, title: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        steps.forEachIndexed { i, s ->
            val now = i == steps.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (now) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                    else Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = cs.tertiary)
                }
                Spacer(Modifier.width(12.dp))
                Text(s.trimEnd('…', '.'), style = MaterialTheme.typography.bodyLarge, color = if (now) cs.onBackground else cs.onSurfaceVariant)
            }
        }
    }
}

/** The passage while it's being written: readable at once, muted until it's checked. */
@Composable
fun DraftPassage(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = readingStyle(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

// ---------------------------------------------------------------- the reading screen, from data

/** The whole reading screen, from data: used by the app and by the screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryContent(
    title: String, history: List<ShownTurn>, current: ShownTurn?, busy: String?, error: String?,
    onReroll: () -> Unit, onEdit: () -> Unit, onSend: (String) -> Unit,
    reply: String = "", onReply: (String) -> Unit = {}, onRetry: (() -> Unit)? = null, onVersion: (Int) -> Unit = {},
    /** Who you are in the story, shown above the first passage. */
    intro: String? = null,
    subtitle: String? = null,
    /** A note above the reply box (no voice for Listen…). */
    notice: String? = null,
    listening: Boolean = false,
    onListen: ((String) -> Unit)? = null,
    /** The passage streaming in while it's written: (phase, text). */
    draft: Pair<String, String>? = null,
    /** The message just sent, shown as a bubble while its turn is written. */
    sending: String? = null,
    /** The stages of what's running (shown while nothing streams). */
    steps: List<String> = emptyList(),
    /** The world is being built: the first wait, said so. */
    creating: Boolean = false,
    /** Nothing to show and nothing running: no story yet. */
    onStartStory: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onIdeas: (() -> Unit)? = null, thinking: Boolean = false, ideas: List<String> = emptyList(),
    /** What the story being made is about (what you typed), shown while it's made. */
    premise: String? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size, current, draft != null) {
        val n = listState.layoutInfo.totalItemsCount
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().imePadding()) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis) }
                }
            },
            navigationIcon = { if (onMenu != null) IconButton(onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.menu)) } },
            actions = { if (onInfo != null && (current != null || history.isNotEmpty())) IconButton(onInfo) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.info_title)) } },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = cs.background),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.wrapCentered().fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)) {
                if (intro != null && history.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(cs.surfaceContainer).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.you_are), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                        Text(intro, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(history) { t -> TurnBlock(t.player, t.passage) }
                if (current != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // a rewrite streams its new version in place of the old one
                        TurnBlock(current.player, current.passage) {
                            if (busy != null && draft != null) DraftPassage(busy, draft.second) else PassageText(current.passage)
                        }
                        PassageTools(current.passage, busy == null, onReroll, onEdit, onVersion, listening,
                            onListen?.let { l -> { l(current.passage.text) } })
                        // sent while this passage is still being recorded: the bubble shows already
                        if (busy != null && sending != null) { Spacer(Modifier.height(16.dp)); PlayerBubble(sending) }
                    }
                } else if (busy != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // your message, at once, above what's being written for it
                        sending?.let { PlayerBubble(it) }
                        if (draft != null) DraftPassage(busy, draft.second)
                        else {
                            if (creating) {
                                Text(stringResource(R.string.creating_title), style = MaterialTheme.typography.headlineSmall)
                                premise?.let { Text(it, style = readingStyle(), color = cs.onSurfaceVariant) }
                            }
                            Stages(steps.ifEmpty { listOf(busy) })
                            if (creating) Text(stringResource(R.string.creating_about), style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant)
                        }
                    }
                } else if (error == null && history.isEmpty() && onStartStory != null) item {
                    EmptyStory(onStartStory)
                }
                if (error != null) item { ErrorNote(error, onRetry) }
            }
        }
        if (notice != null && busy == null) Text(notice, Modifier.fillMaxWidth().background(cs.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        // nothing to answer yet (the world is being built, or there's no story): no box to type into
        if (!creating && (current != null || history.isNotEmpty() || busy != null))
            ReplyBar(busy, current != null, reply, onReply, onSend, ideas, thinking, onIdeas,
                firstTurn = history.isEmpty() && current != null && current.player == null)
    }
}

/** No story on this phone: the one thing to do is start one. */
@Composable
private fun EmptyStory(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DieFace(5, 40.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background)
            DieFace(2, 40.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background)
        }
        Text(stringResource(R.string.stories_none), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.stories_start), onStart)
    }
}

// ---------------------------------------------------------------- the screen

@Composable
fun StoryScreen(state: AppState, go: (Screen) -> Unit, onNewStory: () -> Unit) {
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(MenuInfo()) }
    LaunchedEffect(drawer.currentValue, story.current) { menu = withContext(Dispatchers.IO) { runCatching { menuInfo(state) }.getOrDefault(menu) } }
    var info by remember { mutableStateOf<StoryController.StoryInfo?>(null) }
    var role by remember { mutableStateOf<String?>(null) }
    var infoWanted by remember { mutableStateOf(false) }
    LaunchedEffect(story.current == null) { role = withContext(Dispatchers.IO) { story.role() } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.speaker.stop() } }
    LaunchedEffect(infoWanted) { if (infoWanted) { info = withContext(Dispatchers.IO) { story.storyInfo() }; infoWanted = false } }
    var editing by remember { mutableStateOf<String?>(null) }
    val creating = !story.isSetUp() && story.creationAsked
    // start() is safe to repeat: it creates the story asked for, shows the waiting passage, finishes a half-done
    // turn, or answers an unsent reply
    LaunchedEffect(Unit) { if (story.current == null && !story.isWorking) story.launch { start() } }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        StoryMenu(menu, go = { s -> scope.launch { drawer.close() }; go(s) }, onNewStory = { scope.launch { drawer.close() }; onNewStory() })
    }) {
        StoryContent(
            title = menu.title ?: stringResource(R.string.app_name),
            subtitle = story.title,
            history = story.history, current = story.current,
            busy = story.busy, error = story.error,
            onReroll = { story.launch { reroll() } },
            onEdit = { editing = story.current?.passage?.text },
            onSend = { m -> story.submit(m) },
            reply = story.replyDraft, onReply = { story.replyDraft = it }, onRetry = story.retry,
            onVersion = { story.selectVersion(it) },
            intro = role, onInfo = { infoWanted = true }, onMenu = { scope.launch { drawer.open() } },
            notice = state.speaker.problem()?.let { stringResource(it) },
            listening = state.speaker.speaking,
            draft = story.draft, sending = story.sending, steps = story.steps, creating = creating,
            premise = if (creating) story.seedDraft.trim().ifEmpty { null } else null,
            onStartStory = if (!story.isSetUp() && !creating) onNewStory else null,
            onListen = { text -> if (state.speaker.speaking) state.speaker.stop() else state.speaker.say(text) },
            onIdeas = { story.suggest() }, thinking = story.thinking, ideas = story.ideas,
        )
    }
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
    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        StoryInfoBody(info)
    }
}

@Composable
fun StoryInfoBody(info: StoryController.StoryInfo) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.headlineSmall)
        info.role?.let { sheet ->
            Heading(stringResource(R.string.info_you))
            // the sheet's own headings (What you know, Established in the story) as small labels
            sheet.lines().filter { it.isNotBlank() }.forEach { l ->
                when {
                    l.startsWith("## ") -> Text(l.removePrefix("## "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    l.startsWith("- ") -> Row { Text("•  ", color = cs.primary)
                        Text(l.removePrefix("- ").replace(Regex("^\\(turn -?\\d+\\) "), ""), style = MaterialTheme.typography.bodyMedium) }
                    else -> Text(l, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (info.met.isNotEmpty()) {
            Heading(stringResource(R.string.info_met))
            Text(info.met.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
        if (info.remembers.isNotEmpty()) {
            Heading(stringResource(R.string.info_remember))
            info.remembers.take(30).forEach { (before, text) ->
                Row { Text("•  ", color = cs.primary); Text(if (before) stringResource(R.string.info_before_story, text) else text,
                    style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Heading(stringResource(R.string.info_chapters))
        if (info.chapters.isEmpty()) Text(stringResource(R.string.info_no_chapters), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        info.chapters.forEach { (n, text) ->
            Text(stringResource(R.string.info_chapter, n), style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 10.dp))
