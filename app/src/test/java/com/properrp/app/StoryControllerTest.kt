package com.properrp.app

import android.os.Looper
import com.properrp.app.story.StoryController
import com.properrp.core.llm.FakeLlm
import com.properrp.core.llm.Llm
import com.properrp.core.llm.LlmException
import com.properrp.core.llm.LlmRequest
import com.properrp.core.llm.LlmResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The story screen's controller on Android: restarts, failed calls and Try again, with Claude scripted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryControllerTest {
    private val fake = FakeLlm(mapOf(
        "setup" to """{"title":"The Well","world":"森の村。","player":"見習いの薬屋。","characters":[{"name":"ミナ","file":"## Goals\nFind her brother."},
            {"name":"老人","file":"## Goals\nKeep the well shut."}],"secrets":[],"clocks":[],"complications":["雨が止まない"],
            "opening":{"place":"村の広場","present":["ミナ"],"situation":"ミナ asks for medicine."}}""",
        "style_picker" to """{"styles":[""" + (1..4).joinToString(",") {
            """{"name":"S$it","line":"Style $it.","passage":"${"ミナは小さな瓶を棚に戻した。".repeat(5)}"}"""
        } + "]}",
        "director" to """{"beats":[{"goal":"ミナ asks","place":"村の広場","characters":["ミナ"],"pressure":"rain"}]}""",
        "offscreen" to """{"ops":[]}""",
        "adjudicator" to """{"roll":true,"action":"calm Mina","difficulty":0,"stakes":"Mina leaves"}""",
        "intent" to """{"notices":"the player","wants":"the key","does":"asks again"}""",
        "judge" to """{"violations":[]}""",
        "scribe" to """{"ops":[]}""", "summary" to "Summary.",
        "writer" to """{"passage":"ミナは瓶を見た。「これ、高いの？」"}""",
    ))
    private val failing = HashSet<String>()
    private val llm = object : Llm {
        override fun call(req: LlmRequest): LlmResponse {
            if (req.purpose in failing) throw LlmException("network: offline", retryable = true, cause = java.net.UnknownHostException("api.anthropic.com"))
            return fake.call(req)
        }
    }
    private val context get() = RuntimeEnvironment.getApplication()

    private fun controller() = StoryController(context, AppState(context), llmFor = { llm }, fixedKey = "test")

    /** Runs the main looper until the controller's background work is done. */
    private fun settle(c: StoryController) {
        val until = System.currentTimeMillis() + 10_000
        val started = System.currentTimeMillis() + 2_000
        while (!c.isWorking && c.busy == null && System.currentTimeMillis() < started) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        do { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) } while (c.isWorking && System.currentTimeMillis() < until)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Before fun clean() { File(context.filesDir, "story").deleteRecursively(); File(context.filesDir, "story-archive").deleteRecursively() }

    @Test
    fun `restart shows the waiting passage without writing a new one`() = runBlocking {
        val c = controller()
        c.setup("a cute fantasy")
        assertTrue(c.error ?: "", c.styleOptions() != null)
        // the four samples are kept: asking again (a rebuilt screen) costs nothing
        c.styleOptions()
        assertEquals(1, fake.sent("style_picker").size)
        c.start()
        val shown = c.current!!.passage.text
        val writes = fake.sent("writer").size

        val again = controller()
        again.start()
        assertEquals(shown, again.current!!.passage.text)
        assertEquals(writes, fake.sent("writer").size)
        assertEquals("one session, not one per open", 1, fake.sent("director").size)
    }

    @Test
    fun `a failed send keeps the reply, Try again finishes the same turn`() = runBlocking {
        val c = controller()
        c.setup("")
        c.start()
        c.replyDraft = "薬をあげる。"
        failing += "writer"
        c.send("薬をあげる。")
        assertNull(c.current)
        assertTrue(c.error!!, c.error!!.startsWith("Can't reach Claude"))
        assertEquals("薬をあげる。", c.replyDraft)
        val adjudications = fake.sent("adjudicator").size

        failing.clear()
        c.retry!!.invoke()
        settle(c)
        assertNull(c.error)
        assertEquals("薬をあげる。", c.current!!.player)
        assertEquals("", c.replyDraft)
        assertEquals("the roll isn't redone", adjudications, fake.sent("adjudicator").size)
        assertEquals("the earlier passage is in the history", 1, c.history.size)
    }

    @Test
    fun `a reply sent just before a crash is answered after the restart`() = runBlocking {
        val c = controller()
        c.setup("")
        c.start()
        failing += "adjudicator"   // fails before the turn exists
        c.send("こんにちは。")
        failing.clear()

        val again = controller()
        again.start()
        assertEquals("こんにちは。", again.current!!.player)
    }

    @Test
    fun `reroll keeps versions you can switch between, and they survive a restart`() = runBlocking {
        val c = controller()
        c.setup("")
        c.start()
        fake.queue("writer", """{"passage":"老人は井戸の蓋を閉めた。"}""")
        c.reroll()
        assertEquals(2, c.current!!.passage.versions)
        assertEquals("老人は井戸の蓋を閉めた。", c.current!!.passage.text)
        c.selectVersion(0)
        assertEquals(0, c.current!!.passage.version)

        val again = controller()
        again.start()
        assertEquals(2, again.current!!.passage.versions)
        assertEquals(0, again.current!!.passage.version)
    }

    @Test
    fun `a new story archives the old one and keeps your style and taste`() = runBlocking {
        val c = controller()
        c.setup("")
        c.pickStyle(c.styleOptions()!!.first())
        c.start()
        File(c.dir, "config/plot.md").writeText("my taste")
        val archive = c.archiveAndReset()!!
        assertTrue(File(archive, "state/world.md").exists())
        assertTrue("the story itself is gone", !c.isSetUp())
        assertTrue("the picked style stays", c.hasStyle())
        assertEquals("my taste", File(c.dir, "config/plot.md").readText())
        assertNull(c.current)
        assertTrue(c.history.isEmpty())

    }

    @Test
    fun `a reply whose send failed at the commit survives a restart`() = runBlocking {
        val c = controller()
        c.setup("")
        // the scribe fails throughout (the early background run as well), so the send fails at the commit
        failing += "scribe"
        c.start()
        val shown = c.current!!.passage.text
        c.send("ありがとう。")
        failing.clear()

        val again = controller()
        again.start()
        assertEquals("the same passage is still waiting", shown, again.current!!.passage.text)
        assertEquals("ありがとう。", again.replyDraft)
        again.send(again.replyDraft)
        assertEquals("ありがとう。", again.current!!.player)
        assertTrue(!File(again.dir, "log/outbox.txt").exists())
    }

    @Test
    fun `a sent message shows as a bubble at once and comes back to the box if the turn fails`() = runBlocking {
        val c = controller()
        c.setup("")
        c.start()
        failing += "writer"
        c.replyDraft = "音もしない。"
        c.submit("音もしない。")
        assertEquals("音もしない。", c.sending)
        assertEquals("", c.replyDraft)
        settle(c)
        // the failed turn hands the text back on the main thread: give it the moment it needs under load
        val until = System.currentTimeMillis() + 5_000
        while (c.sending != null && System.currentTimeMillis() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertNull(c.sending)
        assertEquals("the text is back in the box", "音もしない。", c.replyDraft)

        failing.clear()
        c.submit(c.replyDraft)
        settle(c)
        assertNull(c.sending)
        assertEquals("音もしない。", c.current!!.player)
        assertEquals("", c.replyDraft)
    }

    @Test
    fun `a turn runs in a foreground service that stops when it's done`() = runBlocking {
        val c = controller()
        c.setup("")
        val app = shadowOf(RuntimeEnvironment.getApplication())
        while (app.nextStartedService != null) {}   // drop what setup started and stopped
        c.start()
        val started = generateSequence { app.nextStartedService }.toList()
        assertTrue(started.toString(), started.any { it.component?.className == com.properrp.app.story.TurnService::class.java.name && it.action == null })
        assertEquals("stop", started.last().action)
    }

    @Test
    fun `stories can be picked up again, and what's yours follows you`() = runBlocking {
        val c = controller()
        c.setup("")
        c.pickStyle(com.properrp.core.story.StylePresets.ALL[1])
        c.start(); settle(c)
        File(c.dir, "state/title.txt").writeText("First\n")
        c.archiveAndReset()!!
        c.setup("")
        File(c.dir, "state/title.txt").writeText("Second\n")
        File(c.dir, "log/costs.jsonl").writeText("{\"dollars\":0.5}\n")
        val all = c.stories()
        assertEquals(listOf("Second", "First"), all.map { it.title })
        assertTrue(all[0].current)
        assertEquals("Light and wry", all[1].style)
        assertTrue(c.switchTo(all[1].dir))
        assertEquals("First", c.stories().first { it.current }.title)
        assertEquals(listOf("First", "Second"), c.stories().map { it.title })
        assertEquals("your costs come along", 0.5, c.totalCost(), 1e-9)
        assertEquals("Light and wry", c.styleName())
        assertTrue("the first story's turn is still there", c.turn() >= 0 && File(c.dir, "log/turns.jsonl").exists() || File(c.dir, "state/pending.json").exists())
        // nothing outside the archive can be switched to
        assertTrue(!c.switchTo(File(context.filesDir, "elsewhere").apply { mkdirs() }))
    }

    @Test
    fun `the language setting reaches the story, and the phone's is the default`() = runBlocking {
        val app = AppState(context)
        assertEquals(Languages.SYSTEM, app.languageTag)
        val c = StoryController(context, app, llmFor = { llm }, fixedKey = "test")
        app.chooseLanguage("ru")
        assertEquals("Russian", app.storyLanguage)
        assertTrue("the interface follows", app.text(R.string.begin) != "Begin")
        // every translated language loads (the picker's list)
        for ((tag, _) in Languages.ALL.drop(1)) {
            app.chooseLanguage(tag)
            assertTrue(tag, app.text(R.string.begin) != "Begin")
        }
        app.chooseLanguage("ru")
        c.setup("")
        assertTrue("Write every text field in Russian" in fake.sent("setup").last().messages.single().text)
        c.start()
        assertTrue("write this passage in Russian." in fake.sent("writer").last().messages.last().text)
        app.chooseLanguage(Languages.SYSTEM)
    }
}
