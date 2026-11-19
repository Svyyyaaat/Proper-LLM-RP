package com.properrp.core

import com.properrp.core.story.StylePresets
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StylePresetsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `every language's samples are all there, and a language without them gets English`() {
        val files = File(root, "core/src/main/resources/styles").listFiles { f -> f.extension == "txt" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val lang = f.nameWithoutExtension
            val local = StylePresets.forLanguage(lang)
            assertEquals(StylePresets.ALL.map { it.name }, local.map { it.name }, lang)
            local.zip(StylePresets.ALL).forEach { (l, en) -> assertNotEquals(en.passage, l.passage, "$lang ${l.name} is still English") }
        }
        assertEquals(StylePresets.ALL, StylePresets.forLanguage("xx"))
        assertEquals(StylePresets.forLanguage("ru"), StylePresets.forLanguage("ru-RU"))
    }

    @Test
    fun `no sample is told by the player's character (the narrator never speaks as them)`() {
        // a first-person narrator in a sample teaches the writer to speak as the player
        val first = Regex("(?m)^(I |Я |Ja |Ich |Yo |Eu |Je |Io )")
        for (lang in listOf("en") + File(root, "core/src/main/resources/styles").list().orEmpty().map { it.removeSuffix(".txt") }) {
            StylePresets.forLanguage(lang).forEach { assertTrue(first.find(it.passage) == null, "$lang ${it.name}: ${first.find(it.passage)?.value}") }
        }
    }
}
