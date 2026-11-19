# Proper RP

A roleplay story on your phone with a proper game master: you play one character, Claude runs the world and
everyone in it. Forked from [Cramtale](https://github.com/svyyyaaat/cramtale) without its language-learning
parts (Anki, dictionary, vocabulary check, review words, mistake checks): what's left is the story engine.

## Language

- **The story answers in the language you write in.** Write in Spanish and the next passage is in Spanish;
  switch to Russian mid-story and it follows. When you just tap *Go on*, it keeps to the language of your last
  message.
- **Settings → Language** (also on the first screen): *System* (the default) uses the phone's language;
  or pick one. It is the interface's language and the one the story starts in (and the one the world, the
  records and the chapter summaries are kept in). Changing it restarts the screen in the new language; a
  *Go on* after the change uses the new language until you write in another one.
- The interface is translated into English, Russian, Ukrainian, Spanish, Brazilian Portuguese, French,
  German, Italian, Polish, Turkish, Japanese, Simplified Chinese and Korean (`app/src/main/res/values-*/`).
  With *System* on a phone in another language, the story is still written in that language; the interface
  falls back to English.

## Your files, and sequels

- **Files:** when you create a story you can add plain text files (lore, character sheets, notes, an old chat log;
  .txt, .md, .json…). The world and cast are built from them (what they say wins), and the game master keeps them
  in its prompt for the whole story (about 24,000 characters; setup reads up to 60,000). They're in
  *Prompts and story files → Your files*, editable. PDFs and other binary files are refused with a note to save
  them as text.
- **A new story with the same people:** *Stories → Start a new story → Keep its people* (or, on an earlier story,
  *New story with its people*). The new story carries over the world, the cast and extras with their current
  stances, the player's character, secrets, open consequences, clocks and everyone's memories. The earlier story
  becomes "the story so far" (its chapter summaries plus a summary of where it stopped), time passes off-screen
  first, and you can say what happens next. The old story stays on the shelf.

## How a turn works

Code owns the state and decides outcomes; each model call does one job. A referee decides whether your move
needs a roll; code rolls 2d6 (a failure is written down as a consequence before anyone writes about it) and
draws the scene's complication; every person present decides what they do from what *they* know (one call
each, so the narrator's knowledge can't leak into them); the writer narrates; a judge checks the passage
against the rules (it never speaks or decides for your character, no explaining, people keep their motives,
nobody knows what they didn't perceive) and a banned-phrase list catches stock prose, with one rewrite; the
scribe updates the records while you read. Between sessions the world moves off-screen and a director plans
the next beats; chapters close with a summary.

Every prompt and config file (`prompts/`, `config/`) is plain text, copied into the story folder and editable in
the app (*Prompts and story files*). Unedited copies follow app updates.

## Layout

| Path | What |
| --- | --- |
| `core/` | Plain Kotlin, unit-tested on the JVM: the story engine (`story/`), the Claude client (`llm/`: official Java SDK, caching, structured output, cost log; or Claude Code through Termux) |
| `app/` | Android app (Compose): onboarding, home, the story screen, stories, styles, settings, costs, files |
| `prompts/`, `config/` | Every prompt and the taste/style/banned-phrase/complication/ambience files |
| `cli/` | Play a story in the terminal, or a scripted smoke run against the API |

## Run it

**Phone:** install the debug APK (CI builds one: *Actions → Android build → proper-rp-debug-apk*). Setup:
welcome (pick a language) → connect Claude (API key, or Claude Code in Termux on your subscription) → create the
story (optionally say what it's about) → pick a style.

**Checks without a phone:** `./gradlew :core:check :app:testDebugUnitTest` (core is also compiled against
Android 8.0's Java library when `platforms;android-26` is installed; Robolectric runs the story through the real
Claude SDK against a local stand-in server). `./gradlew :app:recordPaparazziDebug` renders the screens, in
several languages, to `app/src/test/snapshots/images/`.

**Desktop (JVM 17+):**
```
./gradlew :cli:installDist
ANTHROPIC_API_KEY=... cli/build/install/cli/bin/cli play --dir local/mystory --language Russian --seed "a lighthouse, a debt"
ANTHROPIC_API_KEY=... cli/build/install/cli/bin/cli story-smoke --dir local/smoke
```
