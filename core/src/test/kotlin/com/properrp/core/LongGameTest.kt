package com.properrp.core

import com.properrp.core.llm.CostLog
import com.properrp.core.llm.Llm
import com.properrp.core.llm.LlmRequest
import com.properrp.core.llm.LlmResponse
import com.properrp.core.llm.Usage
import com.properrp.core.story.Dice
import com.properrp.core.story.StoryEngine
import com.properrp.core.story.StoryState
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A game of hundreds of hours, compressed: hundreds of turns whose every passage adds to the record (events,
 * things people learn, facts about the player, debts nobody settles, new faces, people who come to matter).
 * What each call is sent must stop growing; what's old must survive in the long memory.
 */
class LongGameTest {
    private var turn = 0

    /** Each scribe call makes the record grow the way a busy story does. */
    private inner class Busy : Llm {
        val sent = ArrayList<LlmRequest>()
        override fun call(req: LlmRequest): LlmResponse {
            synchronized(sent) { sent += req }
            val text = when (req.purpose) {
                "setup" -> """{"title":"Salt","world":"A salt town. The works pay badly.","player":"A clerk at the salt works who skims.",
                    "history":[{"text":"The clerk took the job two winters ago","witnesses":["PLAYER","Ada"]}],"player_public":"The works' clerk.",
                    "characters":[{"name":"Ada","file":"## Goals\nRun the works.\n## Knows\nThe clerk is reliable.\n## Attitude\nCool."},
                                  {"name":"Bram","file":"## Goals\nOpen a rival works.\n## Knows\nThe clerk drinks at the Anchor.\n## Attitude\nWarm."}],
                    "extras":[],"secrets":[],"clocks":[{"name":"The audit","size":8,"when_full":"the books are opened"}],
                    "complications":["A cart breaks"],"ambience":[],"opening":{"place":"the works","present":["Ada","Bram"],"situation":"Pay day."}}"""
                "director" -> """{"beats":[{"goal":"pay day","place":"the works","characters":["Ada"],"pressure":"money"},
                    {"goal":"the rival","place":"the Anchor","characters":["Bram"],"pressure":"time"},{"goal":"quiet","place":"home","characters":[],"pressure":""}]}"""
                "scribe" -> scribe()
                "adjudicator" -> """{"roll":false,"action":"","difficulty":0,"stakes":"","observable":"the clerk says something"}"""
                "intent" -> """{"notices":"the clerk","wants":"their pay","does":"asks"}"""
                "judge" -> """{"violations":[]}"""
                "summary" -> "Chapter summary: pay day came and went; Ada counted; Bram waited."
                "saga" -> "## What happened\nThe clerk skimmed the works for a long time.\n## Where things stand\nAda suspects nothing yet."
                "remember" -> """{"remembers":"I have known the clerk a long time; pay days, the audit, Bram's offers."}"""
                "condense" -> "- skims the works\n- drinks at the Anchor"
                "offscreen" -> """{"ops":[]}"""
                "writer" -> """{"passage":"Ada counted the coins twice. \"Again short,\" she said."}"""
                else -> error("unscripted ${req.purpose}")
            }
            return LlmResponse(text, Usage(1000, 100, 0, 0), "end_turn", req.model)
        }

        private fun scribe(): String {
            val t = turn
            val ops = mutableListOf(
                """{"op":"event","a":"On turn $t the clerk and Ada argued over the ledger, page $t","b":"","list":["PLAYER","Ada","Bram"],"n":0}""",
                """{"op":"event","a":"Bram mentioned buyer number $t","b":"","list":["PLAYER","Bram"],"n":0}""",
                """{"op":"learns","a":"Ada","b":"fact $t about the ledger","list":[],"n":0}""",
                """{"op":"player_fact","a":"the clerk was seen at the docks on turn $t","b":"","list":[],"n":0}""",
                """{"op":"consequence","a":"Debt number $t to the Anchor","b":"","list":[],"n":0}""",
                """{"op":"new_secret","a":"Secret number $t","b":"","list":["Person$t"],"n":0}""",
                """{"op":"extra","a":"Person$t","b":"a dock worker who saw the clerk on turn $t","list":[],"n":0}""",
                """{"op":"chronicle","a":"Outcome $t","b":"","list":["the works"],"n":0}""",
            )
            // every fifth turn someone comes to matter
            if (t % 5 == 0 && t > 0) ops += """{"op":"promote","a":"Person${t - 1}","b":"## Goals\nGet paid ($t).\n## Knows\nSaw the clerk.\n## Attitude\nWary.","list":[],"n":0}"""
            return """{"ops":[${ops.joinToString(",")}]}"""
        }

        fun last(purpose: String) = synchronized(sent) { sent.last { it.purpose == purpose } }
        fun count(purpose: String) = synchronized(sent) { sent.count { it.purpose == purpose } }
        fun size(r: LlmRequest) = (r.system + r.messages).sumOf { it.text.length }
    }

    @Test
    fun `hundreds of turns - prompts stay the same size, the past stays remembered`() {
        val dir = Files.createTempDirectory("long").toFile()
        val llm = Busy()
        val e = StoryEngine(dir, llm, CostLog(File(dir, "log/costs.jsonl")), "s", dice = Dice(Random(1)),
            settings = StoryEngine.Settings(chapterTurns = 10, sessionGapMs = Long.MAX_VALUE / 4))
        e.setup()
        e.startSession()
        val early = HashMap<String, Int>()
        val late = HashMap<String, Int>()
        val total = 400
        for (i in 0 until total) {
            turn = i
            e.playerTurn(if (i % 3 == 0) null else "I count the coins again.")
            e.commit()
            // the same point in a chapter (its last turn: the writer's history is longest there)
            val sample = when (i) { 69 -> early; total - 1 -> late; else -> null }
            sample?.let { m -> for (p in listOf("writer", "intent", "scribe", "judge")) m[p] = llm.size(llm.last(p)) }
        }
        e.startSession()
        late["director"] = llm.size(llm.last("director"))
        early["director"] = llm.size(llm.sent.first { it.purpose == "director" }) + 20_000  // the first plan had almost no story yet

        for ((p, n) in late) {
            val before = early.getValue(p)
            println("$p: turn 69 = $before chars, turn $total = $n chars")
            assertTrue(n <= before * 1.5 + 2000, "$p grew from $before to $n characters")
            assertTrue(n < 120_000, "$p is $n characters")
        }

        // every closed chapter went into the long memory
        val chapters = e.state.scene().chapter - 1
        assertEquals(chapters, llm.count("saga"))
        assertTrue(e.saga()!!.isNotBlank())
        assertTrue("story so far, from the beginning" in llm.last("writer").system.drop(1).joinToString { it.text })
        // people's files hold a long memory instead of an endless list of what they learned
        val ada = e.state.character("Ada")!!
        assertTrue("## Remembers" in ada, ada)
        assertTrue(ada.length < 2000, "Ada's file is ${ada.length} characters")
        val sheet = File(dir, "state/player.md").readText()
        assertTrue(StoryState.section(sheet, "Established in the story")!!.lines().size < 40, sheet)
        // debts nobody settles don't pile up forever in what the game master reads
        assertTrue(e.state.consequences().count { it.open } <= 40)
        // the whole record is still there: nothing is deleted
        assertTrue(e.state.events().size >= total * 2)
    }
}
