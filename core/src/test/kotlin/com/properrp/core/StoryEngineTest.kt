package com.properrp.core

import com.properrp.core.llm.CostLog
import com.properrp.core.llm.FakeLlm
import com.properrp.core.llm.Models
import com.properrp.core.llm.Role
import com.properrp.core.story.Dice
import com.properrp.core.story.StoryEngine
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The turn loop against scripted model replies: what gets sent, what code decides, what gets recorded. */
class StoryEngineTest {
    private val setupJson = """{"world":"A port town's warehouse district. The bank won't lend.","player":"Runs a small diner. Owes Saeki eighty thousand.",
        "characters":[{"name":"Saeki","file":"## Goals\nGet the warehouse key.\n## Secrets\nOwes the bank."},{"name":"Kitamura","file":"## Goals\nSell the warehouse."}],
        "secrets":[{"text":"Saeki owes the bank three million","knows":["Saeki"]}],
        "clocks":[{"name":"The bank forecloses","size":6,"when_full":"the bank seizes the warehouse"}],
        "complications":["[warehouse] The delivery doesn't come"],
        "opening":{"place":"warehouse","present":["Saeki"],"situation":"Saeki asks for the key."}}"""

    private fun engine(llm: FakeLlm, dice: Dice = Dice(Random(3)), chapterTurns: Int = 30, language: String = "English"): StoryEngine {
        val dir = Files.createTempDirectory("story").toFile()
        val e = StoryEngine(dir, llm, CostLog(File(dir, "costs.jsonl")), "s1",
            dice = dice, settings = StoryEngine.Settings(chapterTurns = chapterTurns, language = language))
        llm.queue("setup", setupJson)
        e.setup()
        return e
    }

    private fun fake() = FakeLlm(mapOf(
        "director" to """{"beats":[{"goal":"Saeki demands the key","place":"warehouse","characters":["Saeki"],"pressure":"the bank calls"},
            {"goal":"Kitamura offers a deal","place":"diner","characters":["Kitamura"],"pressure":"rent"},{"goal":"quiet","place":"diner","characters":[],"pressure":""}]}""",
        "adjudicator" to """{"roll":true,"action":"stall Saeki","difficulty":2,"stakes":"Saeki stops trusting the player","observable":"someone promises the key for tomorrow"}""",
        "intent" to """{"notices":"the player","wants":"the key","does":"asks again"}""",
        "judge" to """{"violations":[]}""",
        "scribe" to """{"ops":[{"op":"chronicle","a":"The key came up","b":"","list":["warehouse","Saeki"],"n":0},{"op":"reveal","a":"S1","b":"Kitamura","list":[],"n":0}]}""",
        "summary" to "Saeki wants the key.\nThe last scene.",
        "writer" to """{"passage":"Saeki closed the ledger. \"Tomorrow, then.\""}""",
    ))

    @Test
    fun `setup writes the world, cast, secrets, clocks and the opening scene, in the story's language`() {
        val llm = fake()
        val e = engine(llm, language = "Russian")
        assertEquals(setOf("Saeki", "Kitamura"), e.state.characterNames().toSet())
        assertEquals("The bank forecloses", e.state.clocks().single().name)
        assertEquals("warehouse", e.state.scene().place)
        assertTrue(File(e.dir, "config/complications.md").readText().contains("The delivery doesn't come"))
        assertTrue("Write every text field in Russian" in llm.sent("setup").single().messages.single().text)
    }

    @Test
    fun `a turn - a code-decided roll, the writer's packet, and commit`() {
        val llm = fake()
        // with difficulty 2 the modifier is -2: find a seed that fails
        val failing = (0..200).first { Dice(Random(it)).roll(2).outcome == Dice.Outcome.FAILURE }
        val e = engine(llm, Dice(Random(failing)))
        e.startSession()
        val t = e.playerTurn("I'll give you the key tomorrow.")
        assertEquals(Dice.Outcome.FAILURE, t.roll!!.outcome)
        // failure is written down before the writer sees it
        assertTrue(e.state.consequences().any { it.text.startsWith("Failed: stall Saeki") })

        val req = llm.sent("writer").last()
        assertEquals(Models.WRITER, req.model)
        assertTrue(req.system.first().cache, "fixed writer prompt is cached")
        val packet = req.messages.last()
        assertEquals(Role.SYSTEM, packet.role, "per-turn facts come as a mid-conversation system message")
        assertEquals("I'll give you the key tomorrow.", req.messages[req.messages.size - 2].text)
        for (needle in listOf("failure", "Saeki demands the key", "Owes the bank", "the language the player's message above is written in")) {
            assertTrue(needle in packet.text, "packet lacks $needle")
        }
        assertFalse("{{" in packet.text || "{{" in req.system.first().text, "unfilled placeholder")
        // the style line is the last thing the writer reads
        assertTrue(packet.text.trimEnd().endsWith("Plain, concrete prose."), packet.text.takeLast(200))

        val ops = e.commit()
        assertTrue(ops.any { it.startsWith("chronicle") })
        // Kitamura wasn't in the scene: the scribe can't reveal the secret to him
        assertTrue(ops.any { it.startsWith("refused") }, ops.toString())
        assertEquals(1, e.state.scene().turn)
    }

    @Test
    fun `the language follows the player, and a changed setting wins once they let the scene go on`() {
        val llm = fake()
        val e = engine(llm, language = "German")
        e.startSession()
        // the story begins: the setting's language
        e.playerTurn(null)
        assertTrue("write this passage in German." in llm.sent("writer").last().messages.last().text)
        e.commit()
        // a message: its own language
        e.playerTurn("Je te donne la clé demain.")
        assertTrue("the language the player's message above is written in" in llm.sent("writer").last().messages.last().text)
        e.commit()
        // the scene goes on: the language of their last message
        e.playerTurn(null)
        val packet = llm.sent("writer").last().messages.last().text
        assertTrue("last wrote in" in packet && "Je te donne la clé demain." in packet, packet.takeLast(400))
        e.commit()

        // the setting changes after that message: going on now uses the new setting
        val e2 = StoryEngine(e.dir, llm, CostLog(File(e.dir, "costs.jsonl")), "s2", dice = Dice(Random(3)),
            settings = StoryEngine.Settings(language = "Japanese"))
        e2.playerTurn(null)
        assertTrue("write this passage in Japanese." in llm.sent("writer").last().messages.last().text)
    }

    @Test
    fun `the records and the director are kept in the story's language`() {
        val llm = fake()
        val e = engine(llm, language = "Spanish")
        e.startSession()
        assertTrue("Write the beats in Spanish." in llm.sent("director").single().messages.single().text)
        e.playerTurn(null)
        e.commit()
        assertTrue("Write every line in Spanish." in llm.sent("scribe").last().messages.single().text)
    }

    @Test
    fun `a banned phrase gets one rewrite`() {
        val llm = fake()
        llm.queue("writer", """{"passage":"Saeki let out a breath. The rubber band snapped."}""")
        llm.queue("rewrite", """{"passage":"Saeki closed the ledger."}""")
        val e = engine(llm)
        e.startSession()
        val t = e.playerTurn(null)
        assertTrue(t.draft.rewritten)
        assertEquals("Saeki closed the ledger.", t.draft.passage)
        val rewriteReq = llm.sent("rewrite").single()
        assertEquals(Role.USER, rewriteReq.messages.last().role)
        assertTrue("let out a breath" in rewriteReq.messages.last().text)
    }

    @Test
    fun `reroll keeps earlier versions, chapters roll over with a summary`() {
        val e = engine(fake(), chapterTurns = 2)
        e.startSession()
        e.playerTurn(null)
        e.reroll()
        assertEquals(2, e.pending!!.drafts.size)
        e.commit()
        e.playerTurn("Yes.")
        val ops = e.commit()
        assertTrue(ops.any { it.contains("chapter 2") }, ops.toString())
        assertTrue(File(e.dir, "state/chapters/1.md").exists())
    }

    @Test
    fun `the player's files go into the world's creation and stay with the writer`() {
        val dir = Files.createTempDirectory("story").toFile()
        val llm = fake()
        com.properrp.core.story.Sources(dir).add("lore.md", "The town of Vell sits on salt flats. Its mayor is Ilse Brand.")
        val e = StoryEngine(dir, llm, CostLog(File(dir, "costs.jsonl")), "s1", dice = Dice(Random(3)))
        llm.queue("setup", setupJson)
        e.setup()
        val asked = llm.sent("setup").single().messages.single().text
        assertTrue("Material from the player" in asked && "Ilse Brand" in asked, asked.takeLast(400))
        e.startSession()
        e.playerTurn(null)
        assertTrue("Ilse Brand" in llm.sent("writer").last().system.first().text, "the writer keeps it (in the cached prompt)")
        assertFalse(com.properrp.core.story.Sources.looksLikeText(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0, 1, 2)))
        assertTrue(com.properrp.core.story.Sources.looksLikeText("Привет, 世界".toByteArray()))
    }

    @Test
    fun `a new story can carry the world, the cast and their memories over from an earlier one`() {
        val llm = fake()
        val e = engine(llm)
        e.startSession()
        e.playerTurn("I'll give you the key tomorrow.")
        e.commit()
        e.state.apply(com.properrp.core.story.StateOp.parse(
            """{"ops":[{"op":"character","a":"Saeki","b":"Let the matter drop, still wary.","list":[],"n":0}]}"""))
        File(e.dir, "state/title.txt").writeText("The Key\n")

        val dir = Files.createTempDirectory("sequel").toFile()
        val next = StoryEngine(dir, llm, CostLog(File(dir, "costs.jsonl")), "s2", dice = Dice(Random(3)))
        llm.queue("summary", "Saeki was promised the key.")
        llm.queue("offscreen", """{"ops":[]}""")
        next.carryOver(e.dir, seed = "a year later, a flood")
        assertTrue(next.isSetUp())
        assertEquals(setOf("Saeki", "Kitamura"), next.state.characterNames().toSet())
        assertTrue("still wary" in next.state.character("Saeki")!!, "the stance came along")
        assertEquals(e.state.events().size, next.state.events().size, "everyone's memories came along")
        assertEquals("The Key II", File(dir, "state/title.txt").readText().trim())
        assertFalse(File(dir, "log/turns.jsonl").exists(), "a fresh log")
        assertTrue(next.needsNewSession())

        next.startSession()
        assertEquals(1, llm.sent("offscreen").size, "time has passed: the world moves first")
        assertTrue("a year later, a flood" in llm.sent("director").last().messages.single().text)
        next.playerTurn(null)
        val writer = llm.sent("writer").last()
        assertTrue(writer.system.any { "Saeki was promised the key." in it.text }, "the earlier part is the story so far")
        assertEquals(StoryEngine.BEGIN, writer.messages[writer.messages.size - 2].text)
    }
}
