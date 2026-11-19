package com.properrp.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.properrp.app.R
import com.properrp.core.story.Dice

/** What the side menu shows: the story you're in and the values next to each place. */
data class MenuInfo(
    val title: String? = null,
    val chapter: Int = 1,
    val turn: Int = 0,
    val place: String? = null,
    val styleName: String? = null,
    val storyCount: Int = 1,
    val totalCost: Double = 0.0,
    val claudeCode: Boolean = false,
)

/**
 * The side menu, by how often each thing is used: your stories (switch, start, back up), the writing style,
 * then what's looked at now and then (costs, the files Claude reads) and settings.
 */
@Composable
fun StoryMenu(info: MenuInfo, go: (Screen) -> Unit, onNewStory: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppMark(40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.title ?: stringResource(R.string.home_your_story), style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (info.turn == 0) stringResource(R.string.not_started) else storyProgress(info.chapter, info.turn, info.place),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            MenuRow(Icons.AutoMirrored.Filled.LibraryBooks, stringResource(R.string.screen_stories),
                if (info.storyCount > 1) pluralStringResource(R.plurals.home_stories_count, info.storyCount, info.storyCount) else null) { go(Screen.STORIES) }
            MenuRow(Icons.Filled.Add, stringResource(R.string.stories_new), null, onClick = onNewStory)
            MenuRow(Icons.Filled.Brush, stringResource(R.string.screen_style), info.styleName?.let { styleTitle(it) }) { go(Screen.STYLE) }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Filled.BarChart, stringResource(R.string.screen_costs),
                if (info.claudeCode) stringResource(R.string.home_costs_subscription) else "$%.2f".format(info.totalCost)) { go(Screen.COSTS) }
            MenuRow(Icons.Filled.Settings, stringResource(R.string.screen_settings), null) { go(Screen.SETTINGS) }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** "Chapter 2, turn 14 · the harbour office". */
@Composable
fun storyProgress(chapter: Int, turn: Int, place: String?): String =
    if (place != null) stringResource(R.string.progress_in_place, chapter, turn, place) else stringResource(R.string.progress, chapter, turn)

/**
 * The app's mark, drawn like the launcher draws it: the 108dp adaptive layers scaled so their 72dp visible
 * middle fills the tile. With wallpaper colours it's the themed (one-colour) icon in the scheme's tones.
 */
@Composable
fun AppMark(size: Dp) {
    val cs = MaterialTheme.colorScheme
    val themed = LocalDynamicColor.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(if (themed) cs.primaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center) {
        val layer = Modifier.requiredSize(size * 1.5f)
        if (themed) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null, layer,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(cs.onPrimaryContainer))
        else {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_background), null, layer)
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, layer)
        }
    }
}

// ---------------------------------------------------------------- dice

/** One six-sided die, its pips drawn. */
@Composable
fun DieFace(value: Int, size: Dp, face: Color, pip: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(face, cornerRadius = CornerRadius(s * 0.22f))
        drawRoundRect(pip.copy(alpha = 0.35f), cornerRadius = CornerRadius(s * 0.22f), style = Stroke(width = s * 0.04f))
        val r = s * 0.085f
        val a = s * 0.27f; val m = s * 0.5f; val b = s * 0.73f
        val spots = when (value) {
            1 -> listOf(m to m)
            2 -> listOf(a to a, b to b)
            3 -> listOf(a to a, m to m, b to b)
            4 -> listOf(a to a, b to a, a to b, b to b)
            5 -> listOf(a to a, b to a, m to m, a to b, b to b)
            else -> listOf(a to a, b to a, a to m, b to m, a to b, b to b)
        }
        for ((x, y) in spots) drawCircle(pip, r, Offset(x, y))
    }
}

/** A roll as it lands in the story: the two dice, the total, what it means, and what was tried. */
@Composable
fun RollLine(roll: com.properrp.app.story.RollUi) {
    val cs = MaterialTheme.colorScheme
    val (tone, label) = when (roll.outcome) {
        Dice.Outcome.SUCCESS -> cs.tertiary to stringResource(R.string.roll_success)
        Dice.Outcome.COST -> cs.accent to stringResource(R.string.roll_cost)
        Dice.Outcome.FAILURE -> cs.error to stringResource(R.string.roll_failure)
    }
    var explain by remember { mutableStateOf(false) }
    Row(Modifier.clip(MaterialTheme.shapes.small).clickable { explain = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            roll.dice.forEach { DieFace(it, 26.dp, cs.onBackground.copy(alpha = 0.92f), cs.background) }
        }
        Column(Modifier.weight(1f)) {
            // the dice show the numbers; the words say what they mean
            Text(label, style = MaterialTheme.typography.labelLarge, color = tone)
            roll.action?.let { Text(stringResource(R.string.roll_tried, it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (explain) androidx.compose.material3.AlertDialog(onDismissRequest = { explain = false },
        title = { Text(stringResource(R.string.roll_explain_title)) }, text = { Text(stringResource(R.string.roll_explain)) },
        confirmButton = { androidx.compose.material3.TextButton({ explain = false }) { Text(stringResource(R.string.done)) } })
}

/** The menu's numbers: reads files, call off the main thread. */
fun menuInfo(state: com.properrp.app.AppState): MenuInfo {
    val s = state.story
    val stories = s.stories()
    return MenuInfo(stories.firstOrNull { it.current }?.title, s.chapter(), s.turn(), s.place(), s.styleName(), stories.size,
        s.totalCost(), state.claudeCode)
}

/** Spacer for the bottom of a scrolling page, so the last thing isn't under the gesture bar. */
@Composable
fun BottomGap() = Spacer(Modifier.height(24.dp))
