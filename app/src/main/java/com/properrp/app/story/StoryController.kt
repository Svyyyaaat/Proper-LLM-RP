package com.properrp.app.story

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.properrp.app.AppState
import com.properrp.app.R
import com.properrp.core.jsonLines
import com.properrp.core.llm.AnthropicLlm
import com.properrp.core.llm.ClaudeCodeLlm
import com.properrp.core.llm.CostLog
import com.properrp.core.llm.ErrorText
import com.properrp.core.llm.Prices
import com.properrp.core.story.PendingTurn
import com.properrp.core.story.StoryEngine
import com.properrp.core.story.StoryState
import com.properrp.core.story.StylePresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One passage as shown. */
data class ShownPassage(
    val turn: Int,
    val text: String,
    val checks: List<String> = emptyList(),
    /** Dice, the complication, whether the checks rewrote it: "behind the passage". */
    val gmNotes: List<String> = emptyList(),
    /** Which version of this passage is shown, of how many (Reroll and Edit add versions). */
    val version: Int = 0,
    val versions: Int = 1,
)

data class ShownTurn(val player: String?, val passage: ShownPassage)

/** Runs the story on the phone: your message in, a passage out, kept on disk so nothing is lost. */
class StoryController(
    private val context: Context,
    private val app: AppState,
    /** The Claude client for a key (tests pass a scripted one). */
    private val llmFor: (String) -> com.properrp.core.llm.Llm = { AnthropicLlm(it) },
    /** A key that bypasses the keystore (tests). */
    private val fixedKey: String? = null,
) {
    val dir = File(context.filesDir, "story").apply { mkdirs() }
    private val keys = ApiKeyStore(context)
    val costs = CostLog(File(dir, "log/costs.jsonl"), Prices.DEFAULT)
    val session = UUID.randomUUID().toString().take(8)

    /** The passage as it streams in while being written: (phase, text), null otherwise. */
    var draft by mutableStateOf<Pair<String, String>?>(null)
        private set

    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    val history = mutableStateListOf<ShownTurn>()
    var current by mutableStateOf<ShownTurn?>(null)

    /** What to do about a failure, in the app's language, then its causes (so a screenshot tells what went wrong). */
    private fun describe(e: Throwable): String = app.text(errorText(ErrorText.kind(e))) + "\n\n" + ErrorText.details(e)

    /** The reply being typed: kept here so leaving the screen or a failed send doesn't lose it. */
    var replyDraft by mutableStateOf("")
    /** Repeats the action that failed ("Try again"). */
    var retry by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** Story work runs here, not in a screen's scope: leaving the screen doesn't cancel a turn halfway. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = AtomicBoolean(false)
    /** True while an action runs (between the tap and its result). */
    val isWorking get() = running.get()

    fun launch(block: suspend StoryController.() -> Unit) { scope.launch { block() } }

    fun hasKey() = (fixedKey ?: keys.load()) != null
    /** Claude is set up: a key, or Claude Code in Termux (its login can only be checked by a call: [testClaudeCode]). */
    fun canCallClaude() = if (app.claudeCode && fixedKey == null) Termux.ready(context) else hasKey()

    private fun claudeCodeLlm() = ClaudeCodeLlm { request, onText -> Termux.run(context, request, onText) }

    /** A tiny Haiku call through Termux: null when it answered, else what went wrong. Blocking: call off the main thread. */
    fun testClaudeCode(): String? = runCatching {
        claudeCodeLlm().call(com.properrp.core.llm.LlmRequest("test", com.properrp.core.llm.Models.CHECKS, emptyList(),
            listOf(com.properrp.core.llm.Msg(com.properrp.core.llm.Role.USER, "Reply with the single word OK."))))
    }.fold({ r -> if (r.text.isBlank()) app.text(R.string.cc_empty_answer) else null }, { it.message ?: it.toString() })
    fun saveKey(k: String) { keys.save(k); engine = null }

    /** The saved key, shortened for display ("sk-ant-…a1b2"). */
    fun keyHint(): String? = (fixedKey ?: keys.load())?.let { k -> if (k.length > 14) k.take(7) + "…" + k.takeLast(4) else "saved" }

    /** Settings changed: the next action builds the engine with them (the waiting turn is on disk). */
    fun settingsChanged() { if (!isWorking) { engine?.shutdown(); engine = null } }

    private fun clearShown() { history.clear(); current = null; error = null; retry = null; replyDraft = "" }

    /** A new story: this one moves to story-archive/ (nothing is deleted). Your costs stay with you. */
    fun archiveAndReset(): File? {
        if (isWorking) return null
        val archive = File(context.filesDir, "story-archive/" + java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date()))
        archive.parentFile?.mkdirs()
        engine?.shutdown()
        if (!dir.renameTo(archive)) return null
        dir.mkdirs()
        for (k in USER_FILES) File(archive, k).takeIf { it.exists() }?.let { f -> File(dir, k).also { it.parentFile?.mkdirs() }.writeBytes(f.readBytes()) }
        // your taste, picked style and prompt edits are yours, not the story's; the complication table is
        // filled in per story by setup, so it starts fresh
        for (sub in listOf("prompts", "config")) File(archive, sub).listFiles { f -> f.isFile }.orEmpty()
            .filter { it.name != "complications.md" && it.name != "ambience.md" }
            .forEach { f -> File(dir, "$sub/${f.name}").also { it.parentFile?.mkdirs() }.writeBytes(f.readBytes()) }
        engine = null
        clearShown()
        return archive
    }

    // ------------------------------------------------------------------ stories and style

    /** One story on the phone: the one being played, or one in story-archive/. */
    data class StorySummary(val dir: File, val title: String?, val place: String?, val chapter: Int, val turn: Int,
                            val lastActive: Long, val lastLines: String?, val style: String?, val current: Boolean)

    private fun summary(d: File, current: Boolean): StorySummary? {
        val world = File(d, "state/world.md").takeIf { it.exists() } ?: return null
        val sc = runCatching { StoryState(d).scene() }.getOrNull()
        // stories made before titles: the world's first line stands in
        val title = File(d, "state/title.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
            ?: world.readLines().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }?.let { if (it.length > 48) it.take(46) + "…" else it }
        val log = File(d, "log/turns.jsonl")
        val last = log.takeIf { it.exists() }?.readLines()?.lastOrNull { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val active = sc?.lastActive?.takeIf { it > 0 } ?: last?.optLong("at")?.takeIf { it > 0 } ?: world.lastModified()
        return StorySummary(d, title, sc?.place?.ifBlank { null }, sc?.chapter ?: 1, sc?.turn ?: 0, active,
            last?.optString("passage")?.lines()?.filter { it.isNotBlank() }?.takeLast(2)?.joinToString("\n"),
            StylePresets.name(d), current)
    }

    private val archiveRoot get() = File(context.filesDir, "story-archive")

    /** Every story on the phone, the one being played first, then the rest by when they were last played. */
    fun stories(): List<StorySummary> = listOfNotNull(summary(dir, true)) +
        archiveRoot.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { summary(it, false) }.sortedByDescending { it.lastActive }

    /**
     * Picks up an archived story where it was left: the current one goes to the archive and the chosen one
     * takes its place. Your costs stay with you.
     */
    fun switchTo(other: File): Boolean {
        if (isWorking || other.parentFile != archiveRoot || !other.isDirectory) return false
        val mine = USER_FILES.associateWith { k -> File(dir, k).takeIf { it.exists() }?.readBytes() }
        engine?.shutdown(); engine = null
        if (File(dir, "state/world.md").exists()) {
            val parked = File(archiveRoot, java.text.SimpleDateFormat("yyyy-MM-dd_HHmm-ss", java.util.Locale.US).format(java.util.Date()))
            if (!dir.renameTo(parked)) return false
        } else dir.deleteRecursively()   // an empty, never set-up story: nothing to keep
        if (!other.renameTo(dir)) return false
        for ((k, bytes) in mine) if (bytes != null) File(dir, k).also { it.parentFile?.mkdirs() }.writeBytes(bytes)
        clearShown()
        return true
    }

    /** The style's short name ("Light and wry"), or its description for styles picked before names were kept. */
    fun styleName(): String? = StylePresets.name(dir) ?: styleLine()?.let { if (it.length > 40) it.take(38) + "…" else it }

    /** The sample passage the writer imitates now. */
    fun styleSample(): String? = File(dir, "config/style_sample.md").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** The built-in styles. */
    fun presets(): List<StoryEngine.StyleOption> = StylePresets.ALL

    private var engine: StoryEngine? = null

    private fun engine(): StoryEngine {
        engine?.let { return it }
        val llm = if (app.claudeCode && fixedKey == null) claudeCodeLlm()
            else llmFor(fixedKey ?: keys.load() ?: error("Add your Claude API key first"))
        return StoryEngine(dir, llm, costs, session, settings = StoryEngine.Settings(
            firmStyleRules = app.firmStyleRules, length = app.passageLength.text, language = app.storyLanguage)).also { e ->
            engine = e
            e.onCall = { purpose -> if (isWorking) stepLabel(purpose)?.let { busy = it } }
            var last = 0L
            e.onDraft = { phase, text, final ->
                // a few updates a second are plenty for the eye; the final one always shows
                val now = System.currentTimeMillis()
                if (final || now - last > 60 || draft == null) { last = now; draft = phase to text }
            }
        }
    }

    private fun stepLabel(purpose: String): String? = STEP_LABELS[purpose]?.let { app.text(it) }

    /**
     * Runs one action off the main thread: one at a time (a double tap doesn't start a second turn), with
     * a progress line per model call, and on failure the error plus a way to try the same action again.
     */
    private suspend fun <T> work(label: Int, again: (suspend () -> Unit)? = null, block: () -> T): T? {
        if (!running.compareAndSet(false, true)) return null
        val text = app.text(label)
        // keeps the app (and its connection to Claude) alive if you switch apps meanwhile
        TurnService.start(context, text)
        return withContext(Dispatchers.IO) {
            busy = text
            error = null
            retry = null
            engine?.onCall = { purpose -> if (isWorking) busy = stepLabel(purpose) ?: text }
            try {
                block()
            } catch (e: Throwable) {
                error = describe(e)
                retry = again?.let { a -> { scope.launch { a() } } }
                null
            } finally {
                busy = null
                draft = null
                running.set(false)
                TurnService.stop(context)
            }
        }
    }

    fun isSetUp() = File(dir, "state/world.md").exists()
    fun hasStyle() = File(dir, "config/style_sample.md").let { it.exists() && it.readText().isNotBlank() }

    /** The picked style's one-line description, for Settings. */
    fun styleLine(): String? = File(dir, "config/style_line.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** Back out of choosing a style again: the style set aside comes back. False if there was none. */
    fun restoreStyle(): Boolean {
        val prev = File(dir, "config/style_sample.md.previous")
        if (!prev.exists() || prev.readText().isBlank()) return false
        for (n in listOf("style_sample.md", "style_line.txt")) File(dir, "config/$n.previous").takeIf { it.exists() }?.copyTo(File(dir, "config/$n"), overwrite = true)
        engine = null
        return true
    }

    suspend fun setup(seed: String): Unit? = work(R.string.busy_creating, again = { setup(seed) }) {
        val from = sequelOf()
        if (from != null) { engine().carryOver(from, seed); setSequelOf(null) } else engine().setup(seed)
    }

    // ------------------------------------------------------------------ files and sequels

    /** Text files given to the story being created (lore, character sheets, notes). */
    fun attachments(): List<String> = com.properrp.core.story.Sources(dir).all().map { it.name }

    /** Adds the file at [uri]; null when it worked, else what to tell the player. Blocking: call off the main thread. */
    fun attach(uri: android.net.Uri): String? {
        val r = context.contentResolver
        val name = runCatching {
            r.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "notes.txt"
        val bytes = runCatching { r.openInputStream(uri)?.use { input ->
            // readNBytes is Android 13+: read up to the limit by hand
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16_384)
            while (out.size() < MAX_ATTACHMENT) { val n = input.read(buf, 0, minOf(buf.size, MAX_ATTACHMENT - out.size())); if (n < 0) break; out.write(buf, 0, n) }
            out.toByteArray()
        } }.getOrNull()
            ?: return app.text(R.string.story_files_failed, name)
        if (!com.properrp.core.story.Sources.looksLikeText(bytes)) return app.text(R.string.story_files_not_text, name)
        com.properrp.core.story.Sources(dir).add(name, bytes.decodeToString().removePrefix("\uFEFF"))
        return null
    }

    fun detach(name: String) = com.properrp.core.story.Sources(dir).remove(name)

    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The earlier story the one being created continues (its world and people), kept until it's created. */
    fun sequelOf(): File? = prefs.getString("sequelOf", null)?.let(::File)?.takeIf { File(it, "state/world.md").exists() }

    private fun setSequelOf(d: File?) = prefs.edit().putString("sequelOf", d?.path).apply()

    /** The title of the story being continued, for the setup screen. */
    fun sequelTitle(): String? = sequelOf()?.let { summary(it, false)?.title }

    /**
     * A new story: the current one is kept on the shelf. With [from], the new one carries over that story's world,
     * people and memories (the current story, or one on the shelf); without, it starts fresh.
     */
    fun startNewStory(from: StorySummary?): Boolean {
        val archived = archiveAndReset() ?: return false
        setSequelOf(when {
            from == null -> null
            from.current -> archived
            else -> from.dir
        })
        return true
    }

    private val styleCache = File(dir, "state/style_options.json")

    /** Four new samples, kept on disk: a rebuilt screen shows the same ones instead of paying for new ones. */
    suspend fun styleOptions(fresh: Boolean = false): List<StoryEngine.StyleOption>? = work(R.string.step_style_picker) {
        val cached = if (fresh) null else runCatching {
            val a = org.json.JSONArray(styleCache.readText())
            (0 until a.length()).map { a.getJSONObject(it) }.map { StoryEngine.StyleOption(it.getString("name"), it.getString("line"), it.getString("passage")) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        cached ?: engine().styleOptions().also { list ->
            styleCache.parentFile?.mkdirs()
            styleCache.writeText(org.json.JSONArray(list.map { JSONObject().put("name", it.name).put("line", it.line).put("passage", it.passage) }).toString())
        }
    }

    /** Sets the story's style (only files: no model call). The next passage is written in it. */
    fun pickStyle(o: StoryEngine.StyleOption) {
        StylePresets.write(dir, o)
        for (n in listOf("style_sample.md", "style_line.txt")) File(dir, "config/$n.previous").delete()
        settingsChanged()
    }

    // ------------------------------------------------------------------ turns

    /** A reply that was sent but has no passage yet: kept on disk so a crash or failed call doesn't lose it. */
    private val outbox = File(dir, "log/outbox.txt")

    /**
     * Opening the story: the passage that was waiting, or a turn whose writing failed (finished now), or the
     * next passage. A new session (off-screen moves, new beats) only starts after a long break, not on every open.
     */
    suspend fun start(): Unit? = work(R.string.busy_opening, again = { start() }) {
        val e = engine()
        loadHistory()
        val message = outbox.takeIf { it.exists() }?.readText()?.ifBlank { null }
        val waiting = e.pending
        if (waiting != null && waiting.ready && message == null) { show(waiting); return@work }
        if (e.pending == null && e.needsNewSession()) e.startSession()
        // a reply sent while this passage was still waiting (its commit failed) goes back into the box
        if (message != null && e.pending?.ready == true && replyDraft.isBlank()) replyDraft = message
        // a waiting passage comes back as it was; a half-done turn is finished; otherwise the next one
        val t = if (e.pending != null) e.resume() else e.playerTurn(message)
        afterTurn(t)
        // the reply that was waiting is answered: it leaves the box
        t.player?.let { p -> if (replyDraft.trim() == p.trim()) replyDraft = "" }
    }

    /** Your message as a bubble at once, while its turn is being written. */
    var sending by mutableStateOf<String?>(null)
        private set

    /**
     * Send from the reply box: the message shows as a bubble right away and leaves the box; if the turn
     * fails, it goes back into the box (Try again sends it as before).
     */
    fun submit(message: String) {
        if (isWorking) return
        if (message.isNotBlank()) { sending = message; replyDraft = "" }
        launch {
            send(message)
            sending?.let { m -> if (error != null && replyDraft.isBlank()) replyDraft = m }
            sending = null
        }
    }

    suspend fun send(message: String): Unit? = work(R.string.busy_sending, again = { send(message) }) {
        val e = engine()
        // decided (and saved) before this turn touches the clock, so a failed plan is retried, not skipped
        if (e.needsNewSession()) e.markSessionDue()
        val newSession = e.needsNewSession()
        outbox.parentFile?.mkdirs()
        outbox.writeText(message)
        if (current != null && e.pending?.ready == true) commitCurrent()
        if (newSession && e.pending == null) e.startSession()
        val t = if (e.pending != null) e.resume() else e.playerTurn(message.takeIf { it.isNotBlank() })
        afterTurn(t)
        replyDraft = ""
    }

    private fun afterTurn(t: PendingTurn) {
        // the passage arrives with your message: the temporary bubble goes
        sending = null
        // the outbox goes only once this turn answered it (a passage restored as it was hasn't)
        val waiting = outbox.takeIf { it.exists() }?.readText()
        if (waiting == null || waiting.isBlank() || waiting.trim() == t.player?.trim()) outbox.delete()
        show(t)
    }

    suspend fun reroll(): Unit? = work(R.string.busy_rewriting, again = { reroll() }) {
        if (engine().pending?.ready != true) return@work
        engine().reroll()
        show(engine().pending!!)
    }

    suspend fun edit(text: String): Unit? = work(R.string.busy_checking, again = { edit(text) }) {
        if (engine().pending?.ready != true) return@work
        engine().edit(text)
        show(engine().pending!!)
    }

    /** Switches between the versions of this passage (Reroll keeps all of them). */
    fun selectVersion(i: Int) {
        val e = engine
        if (e?.pending?.ready != true || busy != null) return
        e.selectDraft(i)
        show(e.pending!!)
    }

    private fun commitCurrent() {
        val e = engine()
        val shown = current ?: return
        e.pending ?: return
        e.commit()
        history += shown
        current = null
    }

    private fun show(t: PendingTurn) {
        title = place()
        val d = t.draft
        val notes = listOfNotNull(
            t.roll?.let { app.text(R.string.note_roll, it.toString()) },
            t.complication?.let { app.text(R.string.note_complication, it) },
            if (d.rewritten) app.text(R.string.note_rewritten) else null,
        )
        current = ShownTurn(t.player, ShownPassage(t.turn, d.passage,
            d.checks.map { "${it.name}: ${if (it.passed) "ok" else it.detail}" }, notes, t.current, t.drafts.size))
        // the scribe for this version starts now, in the background, while it's read
        runCatching { engine?.prepareCommit() }
    }

    private fun loadHistory() {
        if (history.isNotEmpty()) return
        val f = File(dir, "log/turns.jsonl")
        if (!f.exists()) return
        f.jsonLines().takeLast(20).forEach { o ->
            history += ShownTurn(o.optString("player").ifBlank { null }, ShownPassage(o.getInt("turn"), o.getString("passage")))
        }
    }

    /** The scene's place, for the title bar (kept here: reading the file on every redraw is wasteful). */
    var title by mutableStateOf<String?>(null)
        private set

    fun sessionCost() = costs.total(session)

    private fun scene() = runCatching { StoryState(dir).scene() }.getOrNull()
    fun place(): String? = scene()?.place?.ifBlank { null }
    fun turn(): Int = scene()?.turn ?: 0
    fun chapter(): Int = scene()?.chapter ?: 1

    /** The end of the last passage, for the home screen. */
    fun lastLines(): String? = File(dir, "log/turns.jsonl").takeIf { it.exists() }?.readLines()?.lastOrNull { it.isNotBlank() }
        ?.let { runCatching { JSONObject(it).getString("passage") }.getOrNull() }?.lines()?.filter { it.isNotBlank() }?.takeLast(3)?.joinToString("\n")

    /** What the player's character knows: their role, who they've met, the chapters so far. Never secrets. */
    data class StoryInfo(
        val role: String?, val met: List<String>, val chapters: List<Pair<Int, String>>,
        /** What your character remembers: every event they perceived, from the record (newest first). */
        val remembers: List<Pair<Boolean, String>> = emptyList(),
        /** Dollars on the API (0 with Claude Code): this session and all time. */
        val spentSession: Double = 0.0,
        val spent: Double = 0.0,
    )

    /** Your character in short (the card above the first passage). */
    fun role(): String? = playerSheet()?.substringBefore("\n## ")?.trim()?.ifEmpty { null }

    /** Your character's whole sheet: who they are, what they know, what the story has established about them. */
    fun playerSheet(): String? = File(dir, "state/player.md").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    fun storyInfo(): StoryInfo {
        val role = playerSheet()
        val text = File(dir, "log/turns.jsonl").jsonLines().joinToString("\n") { it.optString("passage") } + "\n" + (current?.passage?.text ?: "")
        val cast = runCatching { StoryState(dir).characterNames() }.getOrDefault(emptyList())
        // met = named in a passage you've read (by full name or any part of it of two characters or more)
        val met = cast.filter { n -> n in text || n.split(' ', '　', '・', '(', ')', '（', '）').any { it.length >= 2 && it in text } }
        val chapters = File(dir, "state/chapters").listFiles { f -> f.extension == "md" }.orEmpty()
            .mapNotNull { f -> f.nameWithoutExtension.toIntOrNull()?.let { it to f.readText().trim() } }.sortedBy { it.first }
        val remembers = runCatching {
            StoryState(dir).events().filter { StoryState.PLAYER in it.witnesses }.reversed().map { e -> (e.turn < 0) to e.text }
        }.getOrDefault(emptyList())
        return StoryInfo(role, met, chapters, remembers, sessionCost(), totalCost())
    }

    /** Every session's costs, from the log file. */
    fun totalCost(): Double = File(dir, "log/costs.jsonl").takeIf { it.exists() }?.readLines()
        ?.mapNotNull { runCatching { JSONObject(it).optDouble("dollars") }.getOrNull() }?.sum() ?: 0.0

    companion object {
        /** What's about you, not a story: follows you to a new or another story. */
        val USER_FILES = listOf("log/costs.jsonl")

        /** The most of one file that's read (bigger files are cut when kept anyway). */
        const val MAX_ATTACHMENT = 400_000

        /** The progress line while each model call runs. */
        val STEP_LABELS = mapOf(
            "adjudicator" to R.string.step_adjudicator, "offscreen" to R.string.step_offscreen,
            "director" to R.string.step_director, "intent" to R.string.step_intent,
            "writer" to R.string.step_writer, "rewrite" to R.string.step_rewrite, "judge" to R.string.step_judge,
            "scribe" to R.string.step_scribe, "summary" to R.string.step_summary, "setup" to R.string.step_setup,
            "style_picker" to R.string.step_style_picker,
        )

        fun errorText(k: ErrorText.Kind): Int = when (k) {
            ErrorText.Kind.NO_NETWORK -> R.string.err_no_network
            ErrorText.Kind.CUT_OFF -> R.string.err_cut_off
            ErrorText.Kind.TIMEOUT -> R.string.err_timeout
            ErrorText.Kind.TLS -> R.string.err_tls
            ErrorText.Kind.BAD_KEY -> R.string.err_bad_key
            ErrorText.Kind.FORBIDDEN -> R.string.err_forbidden
            ErrorText.Kind.NO_CREDIT -> R.string.err_no_credit
            ErrorText.Kind.RATE_LIMITED -> R.string.err_rate_limited
            ErrorText.Kind.OVERLOADED -> R.string.err_overloaded
            ErrorText.Kind.SERVER -> R.string.err_server
            ErrorText.Kind.DECLINED -> R.string.err_declined
            ErrorText.Kind.UNUSABLE -> R.string.err_unusable
            ErrorText.Kind.NO_KEY -> R.string.err_no_key
            ErrorText.Kind.OTHER -> R.string.err_other
        }
    }
}
