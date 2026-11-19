package com.properrp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.properrp.app.AppState
import com.properrp.app.R
import java.io.File

/** What the costs screen shows (plain data, so screenshots can render it). */
data class CostsData(val total: Double, val session: Double, val byPurpose: Map<String, Double>, val claudeCode: Boolean = false)

@Composable
fun CostsScreen(state: AppState) {
    val story = state.story
    CostsContent(CostsData(story.totalCost(), story.sessionCost(), story.costs.byPurpose(story.session), state.claudeCode))
}

/** A model call's purpose as people read it ("writer" → "Writing"). */
@Composable
fun purposeName(p: String): String = when (p) {
    "writer" -> stringResource(R.string.purpose_writer)
    "rewrite" -> stringResource(R.string.purpose_rewrite)
    "judge" -> stringResource(R.string.purpose_judge)
    "scribe" -> stringResource(R.string.purpose_scribe)
    "intent" -> stringResource(R.string.purpose_intent)
    "adjudicator" -> stringResource(R.string.purpose_adjudicator)
    "director" -> stringResource(R.string.purpose_director)
    "offscreen" -> stringResource(R.string.purpose_offscreen)
    "summary" -> stringResource(R.string.purpose_summary)
    "setup" -> stringResource(R.string.purpose_setup)
    "style_picker" -> stringResource(R.string.purpose_style_picker)
    else -> p
}

@Composable
fun CostsContent(d: CostsData) {
    val cs = MaterialTheme.colorScheme
    Page {
        if (d.claudeCode) Text(stringResource(R.string.costs_cc), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        PaperCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$%.2f".format(d.total), style = MaterialTheme.typography.displaySmall, color = cs.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.costs_in_all, "$%.2f".format(d.session)), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (d.byPurpose.isEmpty()) Text(stringResource(R.string.costs_none), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            else Text(stringResource(R.string.costs_by_job), style = MaterialTheme.typography.titleSmall)
            val max = d.byPurpose.values.maxOrNull() ?: 0.0
            for ((k, v) in d.byPurpose.entries.sortedByDescending { it.value }) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(purposeName(k), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("$%.3f".format(v), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth((if (max > 0) v / max else 0.0).toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.primary))
                }
            }
        }
    }
}

/** Plain-text editor for prompts/, config/ and state/ in the story folder. */
@Composable
fun FilesScreen(state: AppState) {
    val root = state.story.dir
    var open by remember { mutableStateOf<File?>(null) }
    val f = open
    if (f == null) {
        val titles = mapOf("config" to stringResource(R.string.files_config), "prompts" to stringResource(R.string.files_prompts),
            "state" to stringResource(R.string.files_state), "state/characters" to stringResource(R.string.files_characters),
            "state/sources" to stringResource(R.string.files_sources))
        val groups = remember {
            titles.entries.map { (d, title) -> Triple(d, title, File(root, d).listFiles { x -> x.isFile }.orEmpty().sortedBy { it.name }) }
                .filter { it.third.isNotEmpty() }
        }
        Page {
            Text(stringResource(R.string.files_about), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (groups.isEmpty()) Text(stringResource(R.string.files_none))
            for ((d, title, files) in groups) {
                SectionTitle(title)
                Column {
                    files.forEachIndexed { i, x ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val character = x.parentFile?.name == "characters"
                        val label = if (character) x.useLines { it.firstOrNull() }.orEmpty().removePrefix("#").trim() else x.name
                        ToolRow(if (character) Icons.Filled.Person else if (d == "state") Icons.Filled.Folder else Icons.Filled.Description,
                            label, android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, x.length())) { open = x }
                    }
                }
            }
        }
    } else {
        var text by remember(f) { mutableStateOf(f.readText()) }
        Column(Modifier.fillMaxSize()) {
            Page(Modifier.weight(1f)) {
                Text(f.relativeTo(root).path, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 360.dp), shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            }
            StepFooter(stringResource(R.string.save), { f.writeText(text); open = null }, secondary = stringResource(R.string.cancel), onSecondary = { open = null })
        }
    }
}
