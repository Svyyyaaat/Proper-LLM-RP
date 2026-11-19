package com.properrp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.properrp.app.story.ShownPassage
import com.properrp.app.story.ShownTurn
import com.properrp.app.story.StoryController
import com.properrp.app.ui.AppMark
import com.properrp.app.ui.AppTheme
import com.properrp.app.ui.CostsContent
import com.properrp.app.ui.CostsData
import com.properrp.app.ui.HomeContent
import com.properrp.app.ui.HomeInfo
import com.properrp.app.ui.KeyStepContent
import com.properrp.app.ui.LanguageDialog
import com.properrp.app.ui.LocalDynamicColor
import com.properrp.app.ui.SettingsContent
import com.properrp.app.ui.SettingsValues
import com.properrp.app.ui.StoriesContent
import com.properrp.app.ui.StoryContent
import com.properrp.app.ui.StoryInfoBody
import com.properrp.app.ui.StoryStepContent
import com.properrp.app.ui.StyleContent
import com.properrp.app.ui.StyleStepContent
import com.properrp.app.ui.WelcomeContent
import com.properrp.core.story.PassageLength
import com.properrp.core.story.StoryEngine
import com.properrp.core.story.StylePresets
import org.junit.Rule
import org.junit.Test

/**
 * Screens rendered from sample data: ./gradlew :app:recordPaparazziDebug → app/src/test/snapshots/images. In English only:
 * Paparazzi's renderer ignores the locale (the translations are checked in StoryControllerTest).
 */
class Shots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun shot(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT))
        paparazzi.snapshot {
            AppTheme(dark = dark) { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    // ---------------------------------------------------------------- setup

    @Test fun a1_welcome() = shot { WelcomeContent("English", {}) {} }

    @Test fun a2_language_dialog() = shot { LanguageDialog("system", "English", {}) {} }

    @Test fun a3_key() = shot { KeyStepContent("sk-ant-api03-xxxx", {}, {}, {}) }

    @Test fun a4_story_setup() = shot { StoryStepContent("", {}, null, null, {}, {}, language = "English") }

    @Test fun a5_styles() = shot { StyleStepContent(StylePresets.ALL.take(2), null, null, {}, {}, {}) }

    // ---------------------------------------------------------------- home, stories, style

    private val home = HomeInfo("the harbour office", 12, 1, "Murai closed her bag and waited, without getting up.", 1.42,
        title = "The Well Keeper", styleName = "Light and wry", storyCount = 3)

    @Test fun c1_home() = shot { HomeContent(home) {} }
    @Test fun c2_home_dark() = shot(dark = true) { HomeContent(home) {} }

    private fun summary(title: String, current: Boolean, turn: Int, style: String?) = StoryController.StorySummary(
        java.io.File(title), title, "the village square", 2, turn, System.currentTimeMillis() - 3 * 86_400_000L,
        "\"Is this expensive?\"\nMina put the bottle back on the shelf.", style, current)

    @Test fun f1_stories() = shot {
        StoriesContent(listOf(summary("The Well Keeper", true, 14, "Light and wry"),
            summary("Demon in the Frost", false, 41, "Mostly dialogue"), summary("Night Bus", false, 3, null)), {}, {}, onSwitch = {})
    }

    @Test fun f1b_stories_first() = shot { StoriesContent(listOf(summary("The Well Keeper", true, 0, "Light and wry")), {}, {}, onSwitch = {}) }

    @Test fun f2_style() = shot {
        StyleContent("Light and wry", StylePresets.ALL[1].passage, StylePresets.ALL, null, null, null, {}, {})
    }

    // ---------------------------------------------------------------- the story

    private val passage = "Murai from the credit union looked as neat as ever, early as it was. She set a cloth bag on the desk and took out a thin folder.\n" +
        "\"The repayment plan you promised me last month. Can I have it today?\""

    private fun storyShot(dark: Boolean, error: String? = null, versions: Int = 1,
                          text: String = passage, player: String = "Could you give me a few more days?") = shot(dark) {
        val p = ShownPassage(3, text, version = versions - 1, versions = versions)
        val prev = ShownPassage(2, "Eight in the morning. The office heating hadn't come on yet.")
        StoryContent("Nishida Works", history = listOf(ShownTurn(null, prev)),
            current = if (error != null) null else ShownTurn(player, p),
            busy = null, error = error, onRetry = if (error != null) ({}) else null,
            reply = if (error != null) player else "",
            onBack = {}, onReroll = {}, onEdit = {}, onSend = {}, onListen = {})
    }

    @Test fun d1_story() = storyShot(false)
    @Test fun d2_story_dark() = storyShot(true)
    @Test fun d1b_story_versions() = storyShot(false, versions = 3)
    @Test fun d1c_story_error() = storyShot(false, error = "Can't reach Claude: the phone couldn't look up api.anthropic.com. Check the internet connection " +
        "(and that this app may use mobile data), then try again.\n\nLlmException: network: offline\n← UnknownHostException: api.anthropic.com")
    // the player writes in Spanish, so the story answers in Spanish (the interface stays in the app's language)
    @Test fun d1d_story_other_language() = storyShot(false, text = "Murai no levantó la vista de la carpeta.\n\"Unos días. ¿Cuántos, exactamente?\"",
        player = "¿Podría darme unos días más?")

    @Test fun d5_story_start() = shot {
        StoryContent("The well", history = emptyList(), current = ShownTurn(null, ShownPassage(0, passage)), busy = null, error = null,
            intro = "An apprentice apothecary in a village by the forest. You owe the old man for last winter's medicine.",
            onBack = {}, onReroll = {}, onEdit = {}, onSend = {})
    }

    @Test fun d6_story_info() = shot {
        StoryInfoBody(StoryController.StoryInfo(
            "An apprentice apothecary in a village by the forest.\n## What you know\nThe old man keeps the well shut at night.",
            listOf("Mina", "The old man"), listOf(1 to "Mina asked for medicine for her brother; the price was too high."),
            listOf(true to "You came to the village two winters ago.", false to "Mina asked you about the well."), 0.21, 1.42))
    }

    @Test fun d7_story_streaming() = shot {
        StoryContent("Foxfire Hall", history = emptyList(), current = null, busy = "Writing…", error = null,
            onBack = {}, onReroll = {}, onEdit = {}, onSend = {},
            draft = "writing" to "Sakaki didn't take the papers. \"You can sign after the festival,\" she said.\nKogane's tail",
            sending = "I hide the mana so it makes no sound.")
    }

    // ---------------------------------------------------------------- settings and costs

    private val settings = SettingsValues(1f, PassageLength.NORMAL, false, "sk-ant-…a1b2", "Light and wry")

    @Test fun e1_settings() = shot { SettingsContent(settings, {}, {}, {}, {}) }

    @Test fun e2_costs() = shot {
        CostsContent(CostsData(1.42, 0.21, mapOf("writer" to 0.12, "intent" to 0.04, "scribe" to 0.03, "judge" to 0.01, "adjudicator" to 0.004)))
    }

    // ---------------------------------------------------------------- the icon

    /** Like a launcher: the middle 72dp of the 108dp layers is what shows, under the launcher's mask. */
    @Composable
    private fun Launcher(size: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape, themed: Boolean) {
        androidx.compose.foundation.layout.Box(Modifier.size(size).clip(shape).background(if (themed) Color(0xFFDCE1F7) else Color.Transparent),
            contentAlignment = Alignment.Center) {
            val layer = Modifier.requiredSize(size * 1.5f)
            if (themed) androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_monochrome), null, layer,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF3A4255)))
            else {
                androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_background), null, layer)
                androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_foreground), null, layer)
            }
        }
    }

    @Test fun z1_icon() = shot {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Launcher(150.dp, CircleShape, false)
                Launcher(150.dp, CircleShape, true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Launcher(150.dp, RoundedCornerShape(36.dp), false)
                Launcher(56.dp, CircleShape, false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppMark(64.dp)
                CompositionLocalProvider(LocalDynamicColor provides true) { AppMark(64.dp) }
            }
        }
    }
}
