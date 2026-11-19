package com.properrp.app

import com.properrp.core.llm.AnthropicLlm
import com.properrp.core.llm.CostLog
import com.properrp.core.llm.Llm
import com.properrp.core.llm.LlmRequest
import com.properrp.core.llm.LlmResponse
import com.properrp.core.story.Prompts
import com.properrp.core.story.StoryEngine
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The story flow as the phone runs it: Android's own libraries (Robolectric), the real Claude SDK
 * client, and a local server standing in for the API with scripted answers. Catches code that works on
 * desktop Java but not on Android (e.g. org.json methods Android doesn't have).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class OnAndroidTest {
    private val server = MockWebServer()
    @Volatile private var purpose = ""
    private val sent = ArrayList<JSONObject>()

    private val replies = mapOf(
        "setup" to """{"world":"森の村。","player":"見習いの薬屋。","characters":[{"name":"ミナ","file":"## Goals\nFind her brother."},
            {"name":"老人","file":"## Goals\nKeep the well shut."}],"secrets":[{"text":"The well hums at night","knows":["老人"]}],
            "clocks":[{"name":"井戸の歌","size":6,"when_full":"the children go missing"}],"complications":["[村] 雨が止まない"],
            "opening":{"place":"村の広場","present":["ミナ"],"situation":"ミナ asks for medicine."}}""",
        "style_picker" to """{"styles":[""" + (1..4).joinToString(",") {
            """{"name":"S$it","line":"Style $it.","passage":"${"ミナは小さな瓶を棚に戻した。".repeat(5)}"}"""
        } + "]}",
        "director" to """{"beats":[{"goal":"ミナ asks","place":"村の広場","characters":["ミナ"],"pressure":"rain"},
            {"goal":"the well","place":"井戸","characters":["老人"],"pressure":"night"},{"goal":"quiet","place":"店","characters":[],"pressure":""}]}""",
        "offscreen" to """{"ops":[]}""",
        "adjudicator" to """{"roll":true,"action":"calm Mina","difficulty":0,"stakes":"Mina leaves"}""",
        "intent" to """{"notices":"the player","wants":"the key","does":"asks again"}""",
        "judge" to """{"violations":[]}""",
        "scribe" to """{"ops":[{"op":"chronicle","a":"薬の話","b":"","list":["ミナ"],"n":0}]}""",
        "summary" to "Mina wants medicine.\n雨の場面。",
        "writer" to """{"passage":"ミナは瓶を見た。「これ、高いの？」"}""",
        "rewrite" to """{"passage":"ミナは瓶を見た。"}""",
    )

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                synchronized(sent) { sent += body }
                val text = replies[purpose] ?: error("nothing scripted for $purpose")
                val reply = JSONObject().put("id", "msg_1").put("type", "message").put("role", "assistant")
                    .put("model", body.getString("model"))
                    .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                    .put("stop_reason", "end_turn").put("stop_sequence", JSONObject.NULL)
                    .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 5)
                        .put("cache_read_input_tokens", 0).put("cache_creation_input_tokens", 0))
                if (body.optBoolean("stream")) {
                    // the real API streams server-sent events: send the text in two pieces
                    fun ev(type: String, data: JSONObject) = "event: $type\ndata: $data\n\n"
                    val half = text.length / 2
                    fun delta(s: String) = JSONObject().put("type", "content_block_delta").put("index", 0)
                        .put("delta", JSONObject().put("type", "text_delta").put("text", s))
                    val start = JSONObject(reply.toString()).put("content", org.json.JSONArray()).put("stop_reason", JSONObject.NULL)
                    val sse = ev("message_start", JSONObject().put("type", "message_start").put("message", start)) +
                        ev("content_block_start", JSONObject().put("type", "content_block_start").put("index", 0)
                            .put("content_block", JSONObject().put("type", "text").put("text", ""))) +
                        ev("content_block_delta", delta(text.substring(0, half))) + ev("content_block_delta", delta(text.substring(half))) +
                        ev("content_block_stop", JSONObject().put("type", "content_block_stop").put("index", 0)) +
                        ev("message_delta", JSONObject().put("type", "message_delta")
                            .put("delta", JSONObject().put("stop_reason", "end_turn").put("stop_sequence", JSONObject.NULL))
                            .put("usage", JSONObject().put("output_tokens", 5))) +
                        ev("message_stop", JSONObject().put("type", "message_stop"))
                    return MockResponse().setHeader("content-type", "text/event-stream").setBody(sse)
                }
                return MockResponse().setHeader("content-type", "application/json").setBody(reply.toString())
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    @Test
    fun `setup, styles, a session, turns, reroll, edit and chapter run on Android`() {
        val real = AnthropicLlm("test-key", baseUrl = server.url("/").toString().trimEnd('/'))
        val llm = object : Llm {
            // one at a time: the stand-in server tells calls apart by the purpose set here
            private val lock = Any()
            override fun call(req: LlmRequest): LlmResponse = synchronized(lock) { purpose = req.purpose; real.call(req) }
            override fun stream(req: LlmRequest, onText: (String) -> Unit): LlmResponse = synchronized(lock) { purpose = req.purpose; real.stream(req, onText) }
        }
        val dir = Files.createTempDirectory("story").toFile()
        Prompts(dir).installDefaults(Prompts.PROMPTS + Prompts.CONFIG)
        val e = StoryEngine(dir, llm, CostLog(File(dir, "log/costs.jsonl")), "s1",
            settings = StoryEngine.Settings(chapterTurns = 2, language = "Japanese"))

        e.setup("a cute fantasy with sinister undertones")
        assertEquals(setOf("ミナ", "老人"), e.state.characterNames().toSet())
        val styles = e.styleOptions()
        assertEquals(4, styles.size)
        e.pickStyle(styles[1])

        e.startSession()
        val t = e.playerTurn(null)
        assertTrue(t.draft.passage.startsWith("ミナ"))
        e.reroll()
        e.edit("ミナは瓶を棚に置いた。")
        e.commit()
        e.playerTurn("森に行きます。")
        val ops = e.commit()
        assertTrue(ops.toString(), ops.any { it.contains("chapter 2") })

        // structured output reached the API as JSON schema objects, not strings
        val withSchema = sent.first { it.has("output_config") && it.getJSONObject("output_config").has("format") }
        assertEquals("object", withSchema.getJSONObject("output_config").getJSONObject("format").getJSONObject("schema").getString("type"))

    }
}
