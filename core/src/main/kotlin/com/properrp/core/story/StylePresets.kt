package com.properrp.core.story

import com.properrp.core.writeAtomic
import java.io.File

/**
 * Built-in writing styles, ready without a model call. Each sample is the same scene (the one the style picker
 * uses), so they can be compared side by side. The samples are in English; the writer imitates how they are
 * written, in whatever language the story is played in.
 */
object StylePresets {
    val ALL = listOf(
        StoryEngine.StyleOption(
            "Mostly dialogue",
            "Mostly spoken lines with quick back-and-forth; narration only as short plain sentences between the lines saying who does what; never more than two sentences of description in a row.",
            """
            "Can you give me a few more days on the rent?"
            "Again? You said the same thing last month."
            The landlord closed his ledger.
            "I get paid next week. I swear."
            "We'll find out next week whether you swear true."
            "So…"
            "Three days. Not one more."
            "Three days…"
            "I've got people to pay too. The bank doesn't wait for anybody."
            He looked out of the window when he said it.
            "Three days from now, evening, you're back here. If you're not, I change the lock."
            """.trimIndent(),
        ),
        StoryEngine.StyleOption(
            "Light and wry",
            "Close third person with a chatty, wry narrator who comments in a casual spoken voice; short paragraphs, lots of dialogue, everyday words, small jokes in the narration; no poetic imagery or mood-painting.",
            """
            Dan knocked three times. The third knock was mostly a prayer.
            "It's open."
            The landlord's voice was the voice of a man who had been in a bad mood since breakfast. Possibly since birth.
            "It's about the rent—"
            "I know. It's written all over your face."
            Apparently it was. Dan touched his face, just in case.
            "Could you wait until next week? I'll pay the moment my wages come in."
            "Next week," the landlord said, and sighed. If sighs were ranked by length, this one took gold.
            "I owe the bank too, you know. Three days. That's the best I can do."
            Three days. Short. But a lot better than zero.
            """.trimIndent(),
        ),
        StoryEngine.StyleOption(
            "Plain storybook",
            "Plain storybook prose, like a young-adult novel: simple past-tense narration of what people do and what is there, concrete everyday words, short sentences, frequent dialogue; no metaphors, no mood-painting, no comments on what things mean.",
            """
            In the evening, Dan stood outside the landlord's door. He still had not paid this month's rent.
            The landlord opened the door. There was a smell of fish frying in the kitchen.
            "Is it the rent?"
            "Yes. Could you wait until next week?"
            The landlord said nothing for a while. Then he picked up a letter from the shoe cupboard. It was from the bank.
            "I have money to pay too."
            "I know."
            "I'll wait three days. Come back in three days, in the evening."
            Dan bowed his head. When the door closed, it was already getting dark outside.
            """.trimIndent(),
        ),
        StoryEngine.StyleOption(
            "Hard-boiled",
            "Terse hard-boiled narration in close third person, past tense: short declarative sentences, dry understatement, concrete detail, sharp dialogue; one wry simile at most per scene; no sentiment.",
            """
            The rent was due Monday. It was Thursday.
            Dan took the stairs to the landlord's flat, because the lift had died in spring and nobody had buried it.
            The landlord opened the door on the chain. "You've got it?"
            "Next week."
            "Next week." He said it like a word in a foreign language. "Everybody's got next week."
            "I'm good for it."
            "So's the bank, and the bank's leaning on me." He looked at Dan for a while, the way a man reads a meter.
            "Three days," he said. "Then the lock gets changed, and you can be good for it on the pavement."
            The door shut. Somewhere below, a dog started barking and didn't stop.
            """.trimIndent(),
        ),
    )

    /**
     * The same styles with their sample in another language (the writer imitates the sample, so a story in that
     * language gets prose written like it, not translated from English). Same names and instruction lines.
     */
    /** Samples in other languages: resources/styles/<language>.txt, the four passages in order, "=====" between them. */
    private fun samples(language: String): List<String>? =
        StylePresets::class.java.getResourceAsStream("/styles/$language.txt")?.use { it.readBytes().decodeToString() }
            ?.split(Regex("(?m)^=====\\s*$"))?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.size == ALL.size }

    /** The built-in styles with their samples in [language] (a language tag such as "ru"); English where there are none. */
    fun forLanguage(language: String): List<StoryEngine.StyleOption> {
        val local = samples(language.substringBefore('-').lowercase()) ?: return ALL
        return ALL.mapIndexed { i, o -> local.getOrNull(i)?.let { o.copy(passage = it) } ?: o }
    }

    /** What each built-in style is like, in a few words for the picker (the full line is the writer's instruction). */
    val BLURBS = mapOf(
        "Mostly dialogue" to "Talk, talk, talk; one short line of action between.",
        "Light and wry" to "Lots of dialogue and a chatty narrator with jokes.",
        "Plain storybook" to "Simple, concrete narration, like a young-adult book.",
        "Hard-boiled" to "Terse and dry, with sharp dialogue.",
    )

    /** Saves [o] as the story's style: the sample the writer imitates, its one-line description, and its name. */
    fun write(dir: File, o: StoryEngine.StyleOption) {
        File(dir, "config").mkdirs()
        File(dir, "config/style_sample.md").writeAtomic(o.passage.trim() + "\n")
        File(dir, "config/style_line.txt").writeAtomic(o.line.trim() + "\n")
        File(dir, "config/style_name.txt").writeAtomic(o.name.trim() + "\n")
    }

    fun name(dir: File): String? = File(dir, "config/style_name.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
}
