package com.properrp.core

import com.properrp.core.llm.CostLog
import com.properrp.core.llm.FakeLlm
import com.properrp.core.llm.Llm
import com.properrp.core.llm.LlmException
import com.properrp.core.llm.LlmRequest
import com.properrp.core.llm.LlmResponse
import com.properrp.core.story.Dice
import com.properrp.core.story.StoryEngine
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Turns survive restarts and failed calls: nothing is rolled, moved or charged twice. */
class StoryResumeTest {
    private val fake = FakeLlm(mapOf(
        "setup" to """{"world":"港町。","player":"店主。","characters":[{"name":"佐伯","file":"## Goals\nKey."},{"name":"北村","file":"## Goals\nSell."}],"secrets":[],
            "clocks":[],"complications":["荷物が届かない"],"opening":{"place":"倉庫","present":["佐伯"],"situation":"佐伯 asks."}}""",
        "director" to """{"beats":[{"goal":"佐伯 demands the key","place":"倉庫","characters":["佐伯"],"pressure":"bank"}]}""",
        "offscreen" to """{"ops":[{"op":"chronicle","a":"銀行が電話した","b":"","list":["佐伯"],"n":0}]}""",
        "adjudicator" to """{"roll":true,"action":"lie to Saeki","difficulty":2,"stakes":"Saeki stops trusting the player"}""",
        "intent" to """{"notices":"the player","wants":"the key","does":"asks again"}""",
        "judge" to """{"violations":[]}""",
        "scribe" to """{"ops":[]}""", "summary" to "Summary.",
        "writer" to """{"passage":"佐伯は帳簿を閉じた。"}""",
    ))

    /** Fails every call whose purpose is in [failing]. */
    private inner class Flaky : Llm {
        val failing = HashSet<String>()
        override fun call(req: LlmRequest): LlmResponse {
            if (req.purpose in failing) throw LlmException("network: offline")
            return fake.call(req)
        }
    }

    private val dir = Files.createTempDirectory("resume").toFile()
    private val llm = Flaky()
    private val failingSeed = (0..500).first { Dice(Random(it)).roll(2).outcome == Dice.Outcome.FAILURE }

    private fun engine(seed: Int = failingSeed, gap: Long = 3 * 3600_000L) = StoryEngine(dir, llm, CostLog(File(dir, "costs.jsonl")), "s",
        dice = Dice(Random(seed)), settings = StoryEngine.Settings(sessionGapMs = gap))

    private fun ready(): StoryEngine = engine().also { it.setup(); it.startSession() }

    @Test
    fun `a restart shows the same passage and versions`() {
        val e = ready()
        e.playerTurn("嘘をつく。")
        e.reroll()
        e.selectDraft(0)
        e.savePending()

        val again = engine()
        val t = assertNotNull(again.pending)
        assertEquals(2, t.drafts.size)
        assertEquals(0, t.current)
        assertEquals("嘘をつく。", t.player)
        assertEquals(Dice.Outcome.FAILURE, t.roll!!.outcome)
        assertEquals(e.pending!!.packet, t.packet)
        again.commit()
        assertNull(engine().pending)
    }

    @Test
    fun `a failed writer call resumes the same turn - no second roll, no doubled consequence`() {
        val e = ready()
        llm.failing += "writer"
        assertFailsWith<LlmException> { e.playerTurn("嘘をつく。") }
        val consequences = e.state.consequences().size
        assertEquals(1, consequences)
        val adjudications = fake.sent("adjudicator").size

        llm.failing.clear()
        // the app was killed meanwhile: a fresh engine, a different dice seed
        val again = engine(seed = 99)
        assertFalse(again.pending!!.ready)
        val t = again.playerTurn("嘘をつく。")
        assertTrue(t.ready)
        assertEquals(Dice.Outcome.FAILURE, t.roll!!.outcome)
        assertEquals(adjudications, fake.sent("adjudicator").size)
        assertEquals(consequences, again.state.consequences().size)
    }

    @Test
    fun `a waiting passage can't be replaced by a new turn`() {
        val e = ready()
        e.playerTurn(null)
        assertFailsWith<IllegalStateException> { e.playerTurn("もう一回") }
    }

    @Test
    fun `a failed plan doesn't move the world twice`() {
        val e = ready()
        e.playerTurn(null)
        e.commit()
        llm.failing += "director"
        assertFailsWith<Exception> { e.startSession() }
        llm.failing.clear()
        e.startSession()
        assertEquals(1, fake.sent("offscreen").size)
        // and the next session moves it again
        e.playerTurn(null); e.commit()
        e.startSession()
        assertEquals(2, fake.sent("offscreen").size)
    }

    @Test
    fun `sessions start after a long gap, not on every open`() {
        val e = engine(gap = 1000)
        e.setup()
        assertTrue(e.needsNewSession())
        e.startSession()
        assertFalse(e.needsNewSession())
        assertTrue(e.needsNewSession(System.currentTimeMillis() + 5000))
    }

    @Test
    fun `a failed chapter summary is retried before the next turn`() {
        val e = StoryEngine(dir, llm, CostLog(File(dir, "costs.jsonl")), "s",
            settings = StoryEngine.Settings(chapterTurns = 1))
        e.setup(); e.startSession()
        e.playerTurn(null)
        llm.failing += "summary"
        e.commit()
        assertEquals(1, e.state.scene().chapter)
        llm.failing.clear()
        e.playerTurn(null)
        assertEquals(2, e.state.scene().chapter)
        assertTrue(File(dir, "state/chapters/1.md").exists())
    }

    @Test
    fun `a decided new session survives a failed plan`() {
        val e = ready()
        e.playerTurn(null); e.commit()
        e.markSessionDue()
        e.playerTurn(null); e.commit()   // commits touch the clock; the decision stays
        assertTrue(e.needsNewSession())
        llm.failing += "director"
        assertFailsWith<Exception> { e.startSession() }
        llm.failing.clear()
        assertTrue(e.needsNewSession(), "still due after the failure")
        e.startSession()
        assertFalse(e.needsNewSession())
    }

    @Test
    fun `a commit interrupted after logging isn't applied twice`() {
        val e = ready()
        e.playerTurn(null)
        val turn = e.pending!!.turn
        val pendingJson = File(dir, "state/pending.json").readText()
        e.commit()
        // the kill: the turn is logged, but the pending file came back (written before the clear)
        File(dir, "state/pending.json").writeText(pendingJson)
        val again = engine()
        assertNotNull(again.pending)
        val logged = File(dir, "log/turns.jsonl").readLines().count { it.isNotBlank() }
        assertEquals(listOf("already recorded"), again.commit())
        assertEquals(logged, File(dir, "log/turns.jsonl").readLines().count { it.isNotBlank() })
        assertEquals(turn + 1, again.state.scene().turn)
        assertNull(again.pending)
    }

    @Test
    fun `the draft is handed over while writing, and an early scribe isn't repeated at commit`() {
        val e = ready()
        val drafts = ArrayList<Pair<String, String>>()
        e.onDraft = { phase, text, _ -> synchronized(drafts) { drafts += phase to text } }
        e.playerTurn("嘘をつく。")
        assertTrue(drafts.any { it.first == "writing" && it.second == "佐伯は帳簿を閉じた。" }, drafts.toString())
        val scribes = fake.sent("scribe").size
        e.prepareCommit()
        e.prepareCommit()   // same text: not started twice
        e.commit()
        assertEquals(scribes + 1, fake.sent("scribe").size)
    }

    @Test
    fun `a reroll after the early scribe makes commit scribe the new text`() {
        val e = ready()
        e.playerTurn(null)
        e.prepareCommit()
        fake.queue("writer", """{"passage":"北村は笑った。"}""")
        e.reroll()
        e.commit()
        val last = fake.sent("scribe").last().messages.joinToString { it.text }
        assertTrue("北村は笑った。" in last)
    }

    @Test
    fun `each person present decides from their own knowledge only, and the writer follows it`() {
        val e = ready()
        File(dir, "state/player.md").writeText("A demon hiding in the cellar.\n")
        File(dir, "state/player_public.md").writeText("A thin young shop assistant.\n")
        fake.queue("adjudicator", """{"roll":false,"action":"","difficulty":0,"stakes":"","observable":"a muffled yowl from under the floor"}""")
        e.playerTurn("猫の真似をする。にゃー。")
        val intents = fake.sent("intent")
        assertEquals(1, intents.size, "one call per person present (佐伯)")
        val asked = intents.single().messages.joinToString { it.text }
        assertFalse("A thin young shop assistant." in asked, "no description handed to someone who may never have seen them")
        assertFalse("demon" in asked, "the hidden side never reaches them")
        assertTrue("Key." in asked, "their own file is there")
        assertFalse("猫の真似" in asked, "the player's message never reaches them as such")
        assertTrue("a muffled yowl from under the floor" in asked, "only what an onlooker could notice")
        val around = asked.substringAfter("most of it is background)").substringBefore("Decide from")
        assertTrue(around.lines().count { it.startsWith("- ") } >= 3, "background noise around it: $around")
        val packet = fake.sent("writer").last().messages.last().text
        assertTrue("does: asks again" in packet, packet)
        // decided once: a restart or a reroll doesn't ask again
        val again = engine()
        again.reroll()
        assertEquals(1, fake.sent("intent").size)
    }

    @Test
    fun `only someone present can learn something on screen`() {
        val e = ready()
        val ops = e.state.apply(com.properrp.core.story.StateOp.parse(
            """{"ops":[{"op":"learns","a":"佐伯","b":"the cellar door was open","list":[],"n":0},
                       {"op":"learns","a":"北村","b":"the cellar door was open","list":[],"n":0}]}"""))
        assertTrue(ops.any { it.startsWith("佐伯 learned") }, ops.toString())
        assertTrue(ops.any { it.startsWith("refused: 北村") }, ops.toString())
        assertTrue("## Learned" in e.state.character("佐伯")!!)
    }

    @Test
    fun `facts established about the player go into their sheet`() {
        val e = ready()
        val ops = e.state.apply(com.properrp.core.story.StateOp.parse(
            """{"ops":[{"op":"player_fact","a":"spent three weeks searching the forest","b":"","list":[],"n":0}]}"""))
        assertTrue(ops.single().startsWith("about the player"), ops.toString())
        val sheet = File(dir, "state/player.md").readText()
        assertTrue("## Established in the story" in sheet && "three weeks searching the forest" in sheet, sheet)
    }
}
