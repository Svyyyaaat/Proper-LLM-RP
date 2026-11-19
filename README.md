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

## Long games (hundreds of hours)

Every call is sent the same amount however long the story has run: `LongGameTest` plays 400 turns in which
every passage adds events, learned facts, facts about the player, unsettled debts, secrets, new faces and
people who come to matter, and checks that no prompt grows. What keeps it that way:

- **The story so far** (`state/saga.md`): rewritten at every chapter close from the old one and the new chapter
  (old events compressed harder); the writer, director and off-screen moves read it. The writer also gets the
  previous chapter's summary and the chapter in progress in full.
- **Each person's long memory**: at a chapter close, everyone who perceived something in it gets the "Remembers"
  section of their file rewritten from what they perceived (their "Learned" notes folded in), from inside their
  head, so what they act on stays theirs. On a turn they still get their recent memories and older ones that
  match the scene.
- **Your sheet**: "Established in the story" is merged when it passes 25 lines.
- **Only who's in play in full**: people seen in the last 60 turns, in the scene or in a planned beat get their
  files; the rest one line each (the oldest by name only). Minor people, secrets and undated debts are capped to
  the relevant and latest; debts 40 turns overdue (or the oldest past 30 undated) are closed.
- **Nothing is deleted**: the record (`state/events.jsonl`) and the full log stay; only what's *sent* is bounded.
  A chapter close whose long-memory update failed is caught up at the next turn.
- **On the phone**: each chapter's turns are in their own file (`log/chapters/`), the record is parsed once and
  then only what was added, logs are read from the end, the debug log is capped at ~20 MB.
- **Backups**: *Stories → Save a backup* writes a story as a .zip; *Open a backup* puts one back on the shelf
  (on this phone or another).

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

**Phone:** install the APK from the releases (or CI's debug build). The app opens on the story itself: read,
type what your character says or does (or tap *Go on*), and *Ideas* suggests three moves when you're stuck. Risky
moves show the dice and what they meant (tap them for how rolls work). Everything else is in the menu (☰): your
stories (switch, start a new one, or a new one with the same people; back up or open a backup), the writing style,
costs, settings. First run: welcome (language) → connect Claude (an API key, with how to get one, or Claude Code in
Termux) → your story (a line about it, who you play, your own files; all optional) → the story screen builds the
world and shows each stage.

**Where things are kept:** each story is plain files in the app's private storage (`files/story/`, earlier ones in
`files/story-archive/`); the reply you're typing, a new story's description and the files you've picked are saved as
you type. Backups are .zip files you save wherever you like. The API key is encrypted with a key in the Android
Keystore and never leaves the phone except to Anthropic.

**Look:** a game master's table at night: felt green, bone-coloured text in Literata (a book face made for screens),
brass for the main action and the dice, Golos Text for the interface (both under the Open Font License, in
`app/src/main/assets/licenses/`). A daylight version follows the phone's light mode; wallpaper colours are an option.

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
