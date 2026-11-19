package com.properrp.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.properrp.app.story.RollUi
import com.properrp.app.story.ShownPassage
import com.properrp.app.story.ShownTurn
import com.properrp.app.story.StoryController
import com.properrp.app.ui.AppTheme
import com.properrp.app.ui.CostsContent
import com.properrp.app.ui.CostsData
import com.properrp.app.ui.KeyStepContent
import com.properrp.app.ui.LanguageDialog
import com.properrp.app.ui.MenuInfo
import com.properrp.app.ui.SettingsContent
import com.properrp.app.ui.SettingsValues
import com.properrp.app.ui.StoriesContent
import com.properrp.app.ui.StoryContent
import com.properrp.app.ui.StoryInfoBody
import com.properrp.app.ui.StoryMenu
import com.properrp.app.ui.StoryStepContent
import com.properrp.app.ui.StyleContent
import com.properrp.app.ui.WelcomeContent
import com.properrp.core.story.Dice
import com.properrp.core.story.PassageLength
import com.properrp.core.story.StylePresets
import org.junit.Rule
import org.junit.Test

/**
 * Screens rendered from sample data: ./gradlew :app:recordPaparazziDebug → app/src/test/snapshots/images. In English
 * (Paparazzi's renderer ignores the locale; the translations are checked in StoryControllerTest). Dark first: the
 * night table is how most people will see it.
 */
class Shots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun shot(dark: Boolean = true, device: DeviceConfig = DeviceConfig.PIXEL_6, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = device.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT))
        paparazzi.snapshot {
            AppTheme(dark = dark) { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    // ---------------------------------------------------------------- first run

    @Test fun a1_welcome() = shot { WelcomeContent("English", {}) {} }
    @Test fun a1b_welcome_light() = shot(dark = false) { WelcomeContent("English", {}) {} }
    @Test fun a2_language_dialog() = shot { LanguageDialog("system", "English", {}) {} }
    @Test fun a3_key() = shot { KeyStepContent("", {}, {}, {}) }
    @Test fun a3b_key_wrong() = shot { KeyStepContent("sk-proj-1234", {}, {}, {}) }
    @Test fun a4_story_setup() = shot {
        StoryStepContent("", {}, {}, {}, language = "English", examples = listOf("A port town where water costs more than wine",
            "A night shift at a hotel where guests don't check out", "Smugglers on a dying space station",
            "A family farm, a debt, and a stranger who wants to buy it", "The last inn before the mountain pass, in winter"))
    }
    @Test fun a4b_story_setup_files() = shot {
        StoryStepContent("A port town where water costs more than wine", {}, {}, {}, language = "English",
            files = listOf("world.md", "my-character.md", "old-session.txt"),
            fileProblem = "“map.pdf” isn't a text file. Save it as .txt or .md and add it again.")
    }
    @Test fun a4c_story_sequel() = shot { StoryStepContent("", {}, {}, {}, language = "English", sequelOf = "Salt and Water") }

    // ---------------------------------------------------------------- the story

    private val passage = "Murai from the credit union looked as neat as ever, early as it was. She set a cloth bag on the desk and took out a thin folder.\n" +
        "\"The repayment plan you promised me last month,\" she said. \"Can I have it today?\"\n" +
        "Behind her the kettle began to rattle. Nobody moved to take it off."
    private val prev = ShownPassage(2, "Eight in the morning. The office heating hadn't come on yet, and the windows were grey with the harbour fog.")

    private fun story(dark: Boolean = true, device: DeviceConfig = DeviceConfig.PIXEL_6, error: String? = null, versions: Int = 1,
                      roll: RollUi? = null, ideas: List<String> = emptyList(), reply: String = "") = shot(dark, device) {
        StoryContent("Salt and Water", history = listOf(ShownTurn(null, prev)),
            current = if (error != null) null else ShownTurn("I ask for a few more days.", ShownPassage(3, passage, version = versions - 1, versions = versions, roll = roll)),
            busy = null, error = error, onRetry = if (error != null) ({}) else null, subtitle = "the harbour office",
            reply = reply, onReroll = {}, onEdit = {}, onSend = {}, onListen = {}, onMenu = {}, onInfo = {}, onIdeas = {}, ideas = ideas)
    }

    @Test fun d1_story() = story()
    @Test fun d1b_story_light() = story(dark = false)
    @Test fun d2_story_roll() = story(roll = RollUi(listOf(4, 3), -1, Dice.Outcome.COST, "stall Murai with a promise"), versions = 2)
    @Test fun d2b_story_roll_failed() = story(roll = RollUi(listOf(1, 3), -1, Dice.Outcome.FAILURE, "lie about the plan"))
    @Test fun d3_story_ideas() = story(ideas = listOf("Ask Murai who else at the union has seen the folder",
        "Hand her last month's ledger instead of a plan", "Take the kettle off and offer her tea, stalling"))
    @Test fun d4_story_error() = story(error = "Can't reach Claude: the phone couldn't look up api.anthropic.com. Check the internet connection " +
        "(and that this app may use mobile data), then try again.\n\nLlmException: network: offline\n← UnknownHostException: api.anthropic.com")
    @Test fun d5_story_tablet() = story(device = DeviceConfig.PIXEL_C, roll = RollUi(listOf(6, 5), 0, Dice.Outcome.SUCCESS, "slip out through the yard"))

    @Test fun d6_creating() = shot {
        StoryContent("Proper RP", history = emptyList(), current = null, busy = "Planning the next scenes…", error = null, creating = true,
            steps = listOf("The place and the people…", "Planning the next scenes…"), onReroll = {}, onEdit = {}, onSend = {}, onMenu = {},
            premise = "A port town where water costs more than wine")
    }
    @Test fun d7_streaming() = shot {
        StoryContent("Salt and Water", history = listOf(ShownTurn(null, prev)), current = null, busy = "Writing…", error = null,
            onReroll = {}, onEdit = {}, onSend = {}, onMenu = {}, sending = "I hide the barrels under the nets.",
            draft = "writing" to "Hart looked at the nets, then at her. \"That won't fool Oss,\" he said. \"Nothing fools Oss when it's")
    }
    @Test fun d8_first_passage() = shot {
        StoryContent("Salt and Water", history = emptyList(), current = ShownTurn(null, ShownPassage(0, passage)), busy = null, error = null,
            intro = "A clerk at the salt works who skims the ledgers, and owes Tobias forty silver.",
            onReroll = {}, onEdit = {}, onSend = {}, onMenu = {}, onIdeas = {})
    }
    @Test fun d9_empty() = shot {
        StoryContent("Proper RP", history = emptyList(), current = null, busy = null, error = null, onStartStory = {},
            onReroll = {}, onEdit = {}, onSend = {}, onMenu = {})
    }
    @Test fun e0_menu() = shot { StoryMenu(MenuInfo("Salt and Water", 2, 14, "the harbour office", "Mostly dialogue", 3, 1.42), {}, {}) }
    @Test fun e0b_info() = shot {
        StoryInfoBody(StoryController.StoryInfo(
            "A clerk at the salt works who skims the ledgers.\n## Established in the story\n- was seen at the third pier after curfew",
            listOf("Tobias", "Captain Ser", "Hart"), listOf(1 to "The water caravan is nine days late; Tobias wants his forty silver."),
            listOf(true to "You found the guild's double ledgers a year ago.", false to "Captain Ser asked you about Hart."), 0.21, 1.42))
    }

    // ---------------------------------------------------------------- the rest

    private fun summary(title: String, current: Boolean, turn: Int, style: String?) = StoryController.StorySummary(
        java.io.File(title), title, "the village square", 2, turn, System.currentTimeMillis() - 3 * 86_400_000L,
        "\"Is this expensive?\"\nMina put the bottle back on the shelf.", style, current)

    @Test fun f1_stories() = shot {
        StoriesContent(listOf(summary("Salt and Water", true, 14, "Mostly dialogue"),
            summary("Demon in the Frost", false, 41, "Light and wry"), summary("Night Bus", false, 3, null)), {}, {}, onSwitch = {})
    }
    @Test fun f2_style() = shot { StyleContent("Mostly dialogue", StylePresets.ALL[0].passage, StylePresets.ALL, null, null, null, {}, {}) }
    @Test fun g1_settings() = shot { SettingsContent(SettingsValues(1f, PassageLength.NORMAL, false, "sk-ant-…a1b2", "Mostly dialogue"), {}, {}, {}, {}) }
    @Test fun z1_icon() = shot {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.properrp.app.ui.AppMark(140.dp)
            androidx.compose.runtime.CompositionLocalProvider(com.properrp.app.ui.LocalDynamicColor provides true) { com.properrp.app.ui.AppMark(140.dp) }
        }
    }

    @Test fun g2_costs() = shot {
        CostsContent(CostsData(1.42, 0.21, mapOf("writer" to 0.12, "intent" to 0.04, "scribe" to 0.03, "judge" to 0.01, "adjudicator" to 0.004)))
    }
}
