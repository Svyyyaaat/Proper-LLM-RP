package com.properrp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.R

data class HomeInfo(
    val place: String?,
    val turn: Int,
    val chapter: Int,
    val lastLines: String?,
    val totalCost: Double,
    val title: String? = null,
    val styleName: String? = null,
    val storyCount: Int = 1,
    /** Claude Code in Termux: nothing is billed per call. */
    val claudeCode: Boolean = false,
    /** The app's language, by its own name ("Русский"). */
    val languageName: String = "English",
)

@Composable
fun HomeContent(info: HomeInfo, go: (Screen) -> Unit) {
    Page {
        // wordmark and the language the story is told in
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(36.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
            StatusPill(Icons.Filled.Language, info.languageName, MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurfaceVariant) { go(Screen.SETTINGS) }
        }

        StoryHero(info) { go(Screen.STORY) }
        // the story's own choices, one tap away but quieter than Continue
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickLink(Icons.AutoMirrored.Filled.LibraryBooks, stringResource(R.string.home_stories),
                if (info.storyCount > 1) pluralStringResource(R.plurals.home_stories_count, info.storyCount, info.storyCount)
                else stringResource(R.string.home_stories_none), Modifier.weight(1f)) { go(Screen.STORIES) }
            QuickLink(Icons.Filled.Brush, stringResource(R.string.home_style), info.styleName?.let { styleTitle(it) }
                ?: stringResource(R.string.home_style_none), Modifier.weight(1f)) { go(Screen.STYLE) }
        }

        SectionTitle(stringResource(R.string.home_more))
        TileGrid(listOf(
            Tile(Icons.Filled.BarChart, stringResource(R.string.home_costs),
                if (info.claudeCode) stringResource(R.string.home_costs_subscription) else "$%.2f".format(info.totalCost)) { go(Screen.COSTS) },
            Tile(Icons.Filled.Description, stringResource(R.string.home_files), stringResource(R.string.home_files_about), Tint.JADE) { go(Screen.FILES) },
            Tile(Icons.Filled.Settings, stringResource(R.string.screen_settings), stringResource(R.string.home_settings_about), Tint.NEUTRAL) { go(Screen.SETTINGS) },
        ))
        Spacer(Modifier.height(12.dp))
    }
}

/** A small tonal link under the hero: icon, name, current value. */
@Composable
fun QuickLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = cs.secondaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer, maxLines = 1)
                Text(value, style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The app's mark, drawn like the launcher draws it: the 108dp adaptive layers scaled so their 72dp visible
 * middle fills the tile. With wallpaper colours it's the themed (one-colour) icon in the scheme's tones.
 */
@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp) {
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

@Composable
fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, container: Color, content: Color, onClick: (() -> Unit)? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .background(container).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = content)
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** The story, first and loudest: where you are, the last lines, Continue. */
@Composable
fun StoryHero(info: HomeInfo, onOpen: () -> Unit) {
    val a = LocalAccents.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(a.heroStart, a.heroEnd)))
            .clickable(onClick = onOpen).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(info.title ?: info.place ?: stringResource(R.string.home_your_story), style = ReadingText.copy(fontSize = 24.sp, lineHeight = 32.sp), color = a.onHero)
        Text(if (info.turn == 0) stringResource(R.string.not_started) else storyProgress(info.chapter, info.turn, info.place.takeIf { info.title != null }),
            style = MaterialTheme.typography.bodyMedium, color = a.onHero.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        info.lastLines?.let {
            Text(it, style = ReadingText.copy(fontSize = 16.sp, lineHeight = 26.sp), color = a.onHero.copy(alpha = 0.85f),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Button(onOpen, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = a.heroButton, contentColor = a.onHeroButton)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (info.turn == 0) R.string.begin else R.string.continue_story), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Chapter 2, turn 14 in the harbour office". */
@Composable
fun storyProgress(chapter: Int, turn: Int, place: String?): String =
    if (place != null) stringResource(R.string.progress_in_place, chapter, turn, place) else stringResource(R.string.progress, chapter, turn)

enum class Tint { INDIGO, JADE, VERMILION, NEUTRAL }

data class Tile(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val value: String?,
                val tint: Tint = Tint.INDIGO, val onClick: () -> Unit)

/** Tools as a two-column grid of tiles: an icon, a name, one number or word. */
@Composable
fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> ToolTile(t, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ToolTile(t: Tile, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (t.tint) {
        Tint.INDIGO -> cs.primaryContainer to cs.onPrimaryContainer
        Tint.JADE -> cs.tertiaryContainer to cs.onTertiaryContainer
        Tint.VERMILION -> cs.accentContainer to cs.onAccentContainer
        Tint.NEUTRAL -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
    Surface(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = t.onClick), shape = MaterialTheme.shapes.medium,
        color = cs.surfaceContainerLowest, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(t.icon, container, content, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                t.value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
