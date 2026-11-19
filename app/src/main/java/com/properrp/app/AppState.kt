package com.properrp.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.properrp.core.story.PassageLength
import java.util.Locale

/** Settings, the language and the story. Heavy calls: off the main thread. */
class AppState(val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ language

    /**
     * The app's language: [Languages.SYSTEM] (the phone's, the default) or one of [Languages.ALL]'s tags. It is
     * the interface's language and the one the story is written in, unless the player writes in another.
     */
    var languageTag by mutableStateOf(prefs.getString("language", Languages.SYSTEM) ?: Languages.SYSTEM)
        private set

    fun chooseLanguage(tag: String) {
        if (tag == languageTag) return
        languageTag = tag
        prefs.edit().putString("language", tag).apply()
        localized = null
        story.settingsChanged()
    }

    /** The locale everything is in now: the chosen language, else the phone's. */
    val locale: Locale get() = Languages.resolve(languageTag)

    /** The story's language as the model is told it ("Russian", "Portuguese (Brazil)"). */
    val storyLanguage: String get() = Languages.englishName(locale)

    @Volatile private var localized: Pair<Locale, Context>? = null

    /** [context] in the app's language: for text outside Compose (progress lines, errors, the notification). */
    fun localizedContext(): Context {
        val l = locale
        localized?.let { (at, c) -> if (at == l) return c }
        return Languages.wrap(context, l).also { localized = l to it }
    }

    fun text(id: Int, vararg args: Any): String = localizedContext().getString(id, *args)

    // ------------------------------------------------------------------ setup and look

    /** Setup steps (0 = welcome); saved so leaving the app resumes the flow. */
    var onboardingStep: Int
        get() = prefs.getInt("onboardingStep", 0)
        set(v) = prefs.edit().putInt("onboardingStep", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboardingDone", false)
        set(v) = prefs.edit().putBoolean("onboardingDone", v).apply()

    /** Style rules as firm prohibitions instead of positive statements. */
    var firmStyleRules: Boolean
        get() = prefs.getBoolean("firmStyleRules", false)
        set(v) = prefs.edit().putBoolean("firmStyleRules", v).apply()

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /** Light or dark: follows the phone unless set (Settings → Theme). Compose state, so a change shows at once. */
    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("themeMode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
        private set

    fun setTheme(m: ThemeMode) { themeMode = m; prefs.edit().putString("themeMode", m.name).apply() }

    /** Colours: the phone's Material You palette (default), or the app's own. */
    var materialYou by mutableStateOf(prefs.getBoolean("materialYou", true))
        private set

    fun chooseMaterialYou(on: Boolean) { materialYou = on; prefs.edit().putBoolean("materialYou", on).apply() }

    /** Which Claude runs the story: false = the API with the user's key, true = Claude Code in Termux (their subscription). */
    var claudeCode by mutableStateOf(prefs.getBoolean("claudeCode", false))
        private set

    fun chooseClaudeCode(on: Boolean) { claudeCode = on; prefs.edit().putBoolean("claudeCode", on).apply(); story.settingsChanged() }

    /** Reading text size, as a multiple of the default. */
    var textScale: Float
        get() = prefs.getFloat("textScale", 1f)
        set(v) = prefs.edit().putFloat("textScale", v).apply()

    var passageLength: PassageLength
        get() = runCatching { PassageLength.valueOf(prefs.getString("passageLength", "NORMAL")!!) }.getOrDefault(PassageLength.NORMAL)
        set(v) = prefs.edit().putString("passageLength", v.name).apply()

    /** Text-to-speech in the story's language (the phone's own engine). */
    val speaker by lazy { com.properrp.app.speech.Speaker(context) { locale } }

    val story by lazy { com.properrp.app.story.StoryController(context, this) }
}

/** The languages the interface is translated into; the story can be in any language the player writes. */
object Languages {
    const val SYSTEM = "system"

    /** (BCP 47 tag, name in that language), in the order the picker shows them. */
    val ALL = listOf(
        "en" to "English", "ru" to "Русский", "uk" to "Українська", "es" to "Español", "pt-BR" to "Português (Brasil)",
        "fr" to "Français", "de" to "Deutsch", "it" to "Italiano", "pl" to "Polski", "tr" to "Türkçe",
        "ja" to "日本語", "zh-CN" to "简体中文", "ko" to "한국어",
    )

    /** The phone's own language (not the app's override: the app may have changed the default). */
    fun system(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    fun resolve(tag: String): Locale = if (tag == SYSTEM) system() else Locale.forLanguageTag(tag)

    /** How the model is told the language: plain for most, with the country or script where that changes the writing. */
    fun englishName(l: Locale): String = when (l.language) {
        "pt", "zh" -> l.getDisplayName(Locale.ENGLISH).ifBlank { l.getDisplayLanguage(Locale.ENGLISH) }
        else -> l.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
    }

    /** The language's own name for itself ("Русский"), for the picker's System line. */
    fun nativeName(l: Locale): String = l.getDisplayLanguage(l).replaceFirstChar { it.titlecase(l) }

    fun wrap(base: Context, locale: Locale): Context {
        val c = Configuration(base.resources.configuration)
        c.setLocales(LocaleList(locale))
        return base.createConfigurationContext(c)
    }
}
