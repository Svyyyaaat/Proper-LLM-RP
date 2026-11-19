package com.properrp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.properrp.app.AppState
import com.properrp.app.Languages
import com.properrp.app.R
import com.properrp.core.story.PassageLength

/** What the settings screen shows and changes; plain values so screenshots can render it. */
data class SettingsValues(
    val textScale: Float,
    val length: PassageLength,
    val firmStyle: Boolean,
    val keyHint: String?,
    /** The picked style's name. */
    val styleLine: String? = null,
    val theme: AppState.ThemeMode = AppState.ThemeMode.SYSTEM,
    val materialYou: Boolean = false,
    /** Claude Code in Termux instead of the API. */
    val claudeCode: Boolean = false,
    /** [Languages.SYSTEM] or a tag; with the names to show for it. */
    val language: String = Languages.SYSTEM,
    val languageName: String = "English",
    val systemLanguageName: String = "English",
)

val TEXT_SCALES = listOf(0.85f to R.string.size_small, 1f to R.string.size_normal, 1.15f to R.string.size_large, 1.3f to R.string.size_larger)

@Composable
fun SettingsScreen(state: AppState, onScale: (Float) -> Unit, onPickStyle: () -> Unit,
                   go: (Screen) -> Unit = {}, onRestartSetup: () -> Unit = {}) {
    var v by remember {
        mutableStateOf(SettingsValues(state.textScale, state.passageLength, state.firmStyleRules, state.story.keyHint(),
            state.story.styleName(), state.themeMode, state.materialYou, state.claudeCode,
            state.languageTag, languageLabel(state), systemLanguageName()))
    }
    SettingsContent(v,
        onScale = { state.textScale = it; onScale(it); v = v.copy(textScale = it) },
        onLength = { state.passageLength = it; state.story.settingsChanged(); v = v.copy(length = it) },
        onFirm = { state.firmStyleRules = it; state.story.settingsChanged(); v = v.copy(firmStyle = it) },
        onKey = { state.story.saveKey(it.trim()); v = v.copy(keyHint = state.story.keyHint()) },
        onPickStyle = onPickStyle,
        onTheme = { state.setTheme(it); v = v.copy(theme = it) },
        onColors = { state.chooseMaterialYou(it); v = v.copy(materialYou = it) },
        onBackend = { state.chooseClaudeCode(it); v = v.copy(claudeCode = it) },
        // the activity starts again in the new language
        onLanguage = { state.chooseLanguage(it) },
        claudeCodeSetup = { ClaudeCodeSetup(state) },
        advanced = listOf(
            Triple(stringResource(R.string.screen_files), stringResource(R.string.settings_files_about)) { go(Screen.FILES) },
            Triple(stringResource(R.string.settings_setup_again), stringResource(R.string.settings_setup_again_about)) { onRestartSetup() },
        ),
    )
}

@Composable
fun SettingsContent(
    v: SettingsValues, onScale: (Float) -> Unit, onLength: (PassageLength) -> Unit,
    onFirm: (Boolean) -> Unit, onKey: (String) -> Unit, onPickStyle: () -> Unit = {},
    onTheme: (AppState.ThemeMode) -> Unit = {},
    onColors: (Boolean) -> Unit = {},
    onBackend: (Boolean) -> Unit = {},
    onLanguage: (String) -> Unit = {},
    claudeCodeSetup: @Composable () -> Unit = {},
    /** Rarely used screens: (name, what it is, open). */
    advanced: List<Triple<String, String, () -> Unit>> = emptyList(),
) {
    var keyInput by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    Page {
        SectionTitle(stringResource(R.string.settings_language))
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (v.language == Languages.SYSTEM) stringResource(R.string.language_system_named, v.systemLanguageName) else v.languageName,
                        style = MaterialTheme.typography.titleSmall)
                    Hint(stringResource(R.string.settings_language_about))
                }
                TextButton({ picking = true }) { Text(stringResource(R.string.change)) }
            }
        }

        SectionTitle(stringResource(R.string.settings_look))
        PaperCard {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            val modes = AppState.ThemeMode.entries
            Segmented(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                v.theme.ordinal) { onTheme(modes[it]) }
            Hint(stringResource(R.string.theme_about))
            Text(stringResource(R.string.settings_colours), style = MaterialTheme.typography.titleSmall)
            Segmented(listOf(stringResource(R.string.colours_app), stringResource(R.string.colours_wallpaper)), if (v.materialYou) 1 else 0) { onColors(it == 1) }
            if (v.materialYou) Hint(stringResource(if (android.os.Build.VERSION.SDK_INT >= 31) R.string.colours_about else R.string.colours_about_old))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleSmall)
            Segmented(TEXT_SCALES.map { stringResource(it.second) }, TEXT_SCALES.indexOfFirst { it.first == v.textScale }.coerceAtLeast(1)) { onScale(TEXT_SCALES[it].first) }
            androidx.compose.runtime.CompositionLocalProvider(LocalReadingScale provides v.textScale) {
                Text(stringResource(R.string.settings_sample_line), style = readingStyle())
            }
        }

        SectionTitle(stringResource(R.string.settings_story))
        PaperCard {
            Text(stringResource(R.string.settings_length), style = MaterialTheme.typography.titleSmall)
            Segmented(listOf(stringResource(R.string.length_short), stringResource(R.string.length_normal), stringResource(R.string.length_long)),
                v.length.ordinal) { onLength(PassageLength.entries[it]) }
            Hint(stringResource(when (v.length) {
                PassageLength.SHORT -> R.string.length_short_about
                PassageLength.NORMAL -> R.string.length_normal_about
                PassageLength.LONG -> R.string.length_long_about
            }))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.screen_style), style = MaterialTheme.typography.titleSmall)
                    Hint(v.styleLine?.let { styleTitle(it) } ?: stringResource(R.string.style_not_picked))
                }
                TextButton(onPickStyle) { Text(stringResource(R.string.change)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Toggle(stringResource(R.string.settings_firm), stringResource(R.string.settings_firm_about), v.firmStyle, onFirm)
        }

        SectionTitle(stringResource(R.string.settings_claude))
        PaperCard {
            Segmented(listOf(stringResource(R.string.backend_api), stringResource(R.string.backend_cc)), if (v.claudeCode) 1 else 0) { onBackend(it == 1) }
            if (v.claudeCode) claudeCodeSetup()
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.backend_api), style = MaterialTheme.typography.titleSmall)
                    Hint(v.keyHint?.let { stringResource(R.string.key_saved, it) } ?: stringResource(R.string.key_none))
                }
                TextButton({ keyInput = "" }) { Text(stringResource(if (v.keyHint == null) R.string.add else R.string.replace)) }
            }
        }

        if (advanced.isNotEmpty()) {
            SectionTitle(stringResource(R.string.settings_advanced))
            PaperCard {
                advanced.forEachIndexed { i, (name, about, open) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Hint(about)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (picking) LanguageDialog(v.language, v.systemLanguageName, onPick = { picking = false; onLanguage(it) }) { picking = false }
    keyInput?.let { k ->
        AlertDialog(onDismissRequest = { keyInput = null }, title = { Text(stringResource(R.string.key_dialog_title)) },
            text = {
                OutlinedTextField(k, { keyInput = it }, Modifier.fillMaxWidth(), placeholder = { Text("sk-ant-…") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = { TextButton({ onKey(k); keyInput = null }, enabled = k.trim().startsWith("sk-")) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ keyInput = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** System (the phone's language) and every language the interface is translated into, each by its own name. */
@Composable
fun LanguageDialog(selected: String, systemName: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.language_dialog_about), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                // the phone's own language is the System line already: not listed twice
                (listOf(Languages.SYSTEM to stringResource(R.string.language_system_named, systemName)) +
                    Languages.ALL.filter { it.second != systemName || it.first == selected }).forEach { (tag, name) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(tag) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(tag == selected, { onPick(tag) })
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(i == selected, { onSelect(i) }, SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l, maxLines = 1) }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Hint(hint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
