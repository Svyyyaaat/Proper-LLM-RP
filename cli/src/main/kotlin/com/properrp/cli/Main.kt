package com.properrp.cli

import com.properrp.core.llm.AnthropicLlm
import com.properrp.core.llm.CostLog
import com.properrp.core.story.StoryEngine
import com.properrp.core.story.StylePresets
import java.io.File

private const val USAGE = """Proper RP CLI

  play --dir DIR [--language LANG] [--seed TEXT]
      Plays a story in the terminal (creates it in DIR the first time). Type what your character says or does;
      an empty line lets the scene go on, /reroll writes the passage again, /quit stops.
  story-smoke --dir DIR [--language LANG]
      A real run against the API: setup, the style picker, three turns. Costs about $0.30–0.50.

LANG is the language the story is written in unless you write in another one (English name: "Russian").
ANTHROPIC_API_KEY must be set."""

fun main(args: Array<String>) {
    val o = Opts(args.drop(1))
    when (args.firstOrNull()) {
        "play" -> play(o)
        "story-smoke" -> storySmoke(o)
        else -> println(USAGE)
    }
}

private class Opts(args: List<String>) {
    val named = HashMap<String, String>()

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            require(a.startsWith("--")) { "unexpected argument $a" }
            named[a] = args.getOrNull(i + 1) ?: error("$a needs a value")
            i += 2
        }
    }

    fun need(k: String) = named[k] ?: error("$k is required")
}

private fun engine(o: Opts, dir: File, session: String): Pair<StoryEngine, CostLog> {
    val llm = AnthropicLlm(System.getenv("ANTHROPIC_API_KEY") ?: error("set ANTHROPIC_API_KEY"))
    val costs = CostLog(File(dir, "log/costs.jsonl"))
    val e = StoryEngine(dir, llm, costs, session, settings = StoryEngine.Settings(language = o.named["--language"] ?: "English"))
    e.onCall = { purpose -> System.err.println("· $purpose") }
    return e to costs
}

private fun play(o: Opts) {
    val dir = File(o.need("--dir")).apply { mkdirs() }
    val (e, costs) = engine(o, dir, "cli")
    if (!e.isSetUp()) { e.setup(o.named["--seed"].orEmpty()); println(File(dir, "state/player.md").readText()) }
    if (!e.hasStyle()) e.pickStyle(StylePresets.ALL.first())
    var message: String? = null
    while (true) {
        if (e.pending == null && e.needsNewSession()) e.startSession()
        val t = if (e.pending != null) e.resume() else e.playerTurn(message)
        println("\n" + t.draft.passage + "\n")
        System.err.println("($${"%.3f".format(costs.total("cli"))} this run)")
        print("> ")
        val line = readLine() ?: return
        when (line.trim()) {
            "/quit" -> return
            "/reroll" -> { e.reroll(); continue }
        }
        e.commit()
        message = line.ifBlank { null }
    }
}

private fun storySmoke(o: Opts) {
    val out = File(o.need("--dir"))
    require(!out.exists() || out.list().isNullOrEmpty()) { "--dir must be a new or empty folder" }
    out.mkdirs()
    val (engine, costs) = engine(o, out, "smoke")
    fun step(name: String) = println("\n======== $name  (spent so far $${"%.3f".format(costs.total("smoke"))})")

    step("setup")
    engine.setup()
    println(File(out, "state/world.md").readText())
    println(File(out, "state/player.md").readText())
    engine.state.characterNames().forEach { println("--- $it\n" + engine.state.character(it)) }

    step("style picker")
    val styles = engine.styleOptions()
    styles.forEach { s -> println("--- ${s.name}: ${s.line}\n${s.passage}") }
    engine.pickStyle(styles.first())

    step("session")
    engine.startSession().forEach(::println)

    val replies = listOf<String?>(null, "Please, just a few more days. I'll have the money by Friday.", null)
    for ((i, msg) in replies.withIndex()) {
        step("turn ${i + 1}" + (msg?.let { ": $it" } ?: ""))
        val t = engine.playerTurn(msg)
        t.roll?.let { println("Roll: $it") }
        t.complication?.let { println("Complication: $it") }
        println(t.draft.passage)
        println("rewritten: " + t.draft.rewritten)
        t.draft.checks.forEach { println("  check ${it.name}: ${if (it.passed) "ok" else it.detail}") }
        engine.commit().forEach { println("  state: $it") }
    }
    step("done")
}
