# Voclet: product

What the app does today, screen by screen. When behaviour changes, this file changes in the same
commit. What is intended but not built is in `roadmap.md`.

Checked against the code on 2026-09-27.

## Principles

- No account and no Voclet server. Word lists, results and settings live on the device.
- Every pair has **word1**, the prompt in the language the learner knows, and **word2**, the
  answer in the language being learned. Practice asks word1 and expects word2.
- Most practice needs no typing; Fill the blank and Spell it practise spelling.
- AI is optional: translation suggestions while editing, and reading pairs from a photo. Cloud AI
  (the user's own key) is used when set up and online, otherwise the on-device model, otherwise
  the feature says it is unavailable.
- The UI follows the system language: English, German, French, Spanish, Portuguese (Portugal),
  Italian, Dutch, Polish, Swedish, Norwegian Bokmål, Danish, Finnish, Hungarian. All but German
  are machine translations.

## Home

- **Layout.** On wide or short windows, the lists are on the left and the practice panel on the
  right. On a narrow phone the panel is a bottom sheet: hidden while nothing is selected, opened
  when the first list is selected.
- **Word lists**, newest first. Each card shows its word count, and a star count and a "hard"
  count (sad face) when those are not zero. A selected card is tinted. "Select all" selects every
  list.
- **Top bar.** With lists selected: Share and Export. With none selected: Import. Settings always.
- **Add** (the floating button) opens the list editor for a new list.
- **Practice panel.** The header reads "Practicing on N lists (M words)". Below it, a filter:
  **All** (default), **Hard words**, **Starred**. The word count follows the filter. Then the four
  modes in two columns, sorted by level: Connect (1, Understand), Flashcard (2, Remember), Fill the
  blank and Spell it (3, Spell). With nothing selected, or no words left after filtering, the
  panel is dimmed and the modes are disabled. The filter is not remembered.
- **Hard words** are pairs with at least one wrong answer among their last three results, in any
  mode. A pair that was never practised is not hard.
- **First-use hint.** When the first list is created, a one-time snackbar says AI translation and
  camera import are available, with "Set up AI" opening Settings at the AI section.

## List editor

The same screen serves a new list and an existing one.

- **Title.** A new list focuses it, and the pair rows appear only once there is a name.
- **Languages.** Two pickers, "I know" and "I'm learning", with flags: English, Deutsch, Français,
  Español, Português, Italiano, Nederlands, Polski, Svenska, Norsk bokmål, Dansk, Suomi, Magyar, or
  none. All Latin script, so camera import can read them. The user interface is additionally available in Chinese. A **swap** button exchanges the languages and word1/word2 in every pair.
- **Pairs.** Two fields per row, side by side on wide screens, stacked on phones. A star toggle and
  a delete button per row. An empty row is always kept at the end for the next pair.
- **Saving.** Changes stay in memory until **Save**, which is enabled only when something changed
  and the list has a name. Closing with unsaved changes asks "Stay" or "Discard". An empty new list
  is discarded silently. The overflow menu deletes the whole list, after confirmation.
- **Translation suggestions.** When focus moves from word1 to word2, and word1 changed, and both
  languages are set, and some AI is available: a dropdown under word2 shows "Loading
  suggestions…", then a main suggestion, alternatives and optional usage notes. Tapping one fills
  word2; typing in word2 hides them. The on-device model shows its answer as it streams in.
  Suggestions are cached per word and language pair.
- **Camera scan** (camera icon):
  1. Camera permission is asked the first time.
  2. A live preview with a shutter button.
  3. The photo with four draggable corners ("Drag the corners around the words to scan"); outside
     the selection is dimmed. "New photo" or "Scan".
  4. The selection is straightened and read: by cloud AI ("Extracting word pairs…") or by the
     on-device page reader (a ring filling with "X of Y lines").
  5. On failure the photo stays, with "Retry" (same crop), "New photo" and "Cancel".
  6. The pairs are **added to the editor, not saved**; each scanned row has a coloured stripe
     until Save. A snackbar asks "Added N pairs. Wrong way round?" with "Swap these", which swaps
     only that batch. Cloud AI also fills in an empty title and empty languages; the on-device
     reader does not, and if the languages are still unset a banner asks for them.
- **CSV import** (upload icon): pick a file, then map columns with a preview of the first rows, a
  "First row is header" option and two column pickers ("I know", "I'm learning"). The pairs are
  added to the editor and still need Save.

## Import, export, share

- **Export** (Home, lists selected): a save dialog for `<list name>.voclet.json`, or
  `Voclet_export_<timestamp>.voclet.json` for several lists. The file holds each list's name,
  languages and pairs with their stars; no practice results.
- **Share** (Home, lists selected): the same file through Android's share menu.
- **Import** (Home, nothing selected): pick a `.voclet.json`, then a preview with one checkbox per
  list (all checked), its word and star counts, and "Import (n)". Opening a `.voclet.json` from
  another app starts the same import. Imported lists are always added as new lists, stars kept,
  results empty. Other files are refused ("This file is not a Voclet word list").

## Practice

### Common to all modes

- The pairs are those of the selected lists after the filter, shuffled.
- The title shows the progress and the mode name.
- **Audio.** Each mode reads word2 aloud in the list's second language, using the regional variant
  chosen in Settings. On by default (a Settings switch), with a toggle in each screen's top bar. If
  the engine or the language is missing, audio turns off and a dialog offers the fix.
- **Results.** Every answer is stored per pair, with the mode and time. The results screen shows the
  percentage, a message (80% and above "Great job!", 60% and above "Good effort!", otherwise "Keep
  practicing!"), an animated fox above 50%, the correct, incorrect and total counts, and "Practice
  again" or "Back to home".
- With no words, the screen says there is nothing to practise (Connect stays blank).

### Connect (level 1)

- word1 cards (`tertiaryContainer`) and word2 cards (`primaryContainer`) sit at random positions,
  one per cell of a grid sized to the screen, each tilted up to ±10°, at least 16 dp from the edge.
  At most 14 cards, 7 pairs.
- Drag a line from any card to another. A hovered card gets a thin border.
- **Match:** both cards turn green over 1 s, fade out over 0.5 s, and two new cards fade in; word2
  is read aloud.
- **No match:** the cards turn red over 1 s, input is blocked meanwhile, then they fade back over
  1 s.
- The cards come from one stack shuffled at the start. A word2 card can come up to about two pairs
  after its word1, and only on grids of 9 cards or more; on smaller grids every refill is a
  complete pair.
- A match records a correct answer for that pair. A wrong match records a wrong answer for the pair
  whose word1 was involved (nothing if both were word2 cards). The results show matches as correct
  and wrong attempts as incorrect.
- Rotating the screen rebuilds the grid and keeps the cards in play.

### Flashcard (level 2)

- A card shows word1 (`tertiaryContainer`). **Flip** turns it over (0.5 s, 3D) to word2
  (`primaryContainer`) and reads word2 aloud. The card itself does not react to taps.
- After flipping: **Correct** or **Incorrect**, the learner's own judgement. The answer is stored,
  the card flips back, and the next one follows.
- "Practice again" reshuffles the same words without reloading them.

### Fill the blank (level 3)

- **Prompt:** word1 in a coloured band at the top (a fixed band in portrait).
- **Slots:** word2, one slot per character, in centred rows that wrap. Pre-filled letters are cards
  (`primaryContainer`); blanks show an underscore. Words of up to 5 characters get length − 1
  blanks, longer words 5, at random positions.
- **Letters:** below (or to the right, on wide screens), the missing letters plus half as many
  random wrong letters (at least one), scattered and tilted.
- **Dragging** a letter enlarges it and centres it under the finger; the nearest empty slot within
  reach is highlighted.
- **Right letter:** the slot turns green and the letter leaves the tray. Matching is exact and
  case-sensitive.
- **Wrong letter:** it shows red in the slot for 0.5 s and goes back to the tray; the prompt band
  turns red for the rest of the word.
- **Word done:** the band turns green if there was no mistake, word2 is read aloud, and the next word
  comes after about 1.5 s. A word counts as correct only without mistakes.
- **Skip** fills in the solution, shows it for 3 s and counts the word as incorrect.

### Spell it (level 3)

- **Prompt:** word1 as large text. Below it a text field (keyboard opens, no autocorrect) and a
  button: "Skip" while empty, "Check" once there is text. The keyboard's Done key checks too.
- **Checking** is forgiving about form, strict about spelling:
  - ignored: text in `(…)` and `[…]`, curly vs. straight quotes, dash types, extra spaces, a
    trailing `.`, `!` or `?`, and the case of the first letter;
  - several answers separated by `/`, `,`, `;` or `|` in word2: any of them, or several, are
    accepted;
  - not forgiven: accents, articles, "to " before verbs, typos.
- **Right:** a green box with word2, read aloud; the next word follows after 1.5 s.
- **Wrong:** a red box with a letter-by-letter comparison against the closest accepted answer
  (wrong letters struck through, missing letters underlined), word2 below, read aloud, and
  **Next**.
- **Skip** counts as wrong and shows the comparison with every letter missing.

## Settings

Sections: Interface, Text-to-Speech, AI Assistant, Data, About.

- **Theme:** System (default), Light, Dark.
- **Text-to-Speech:** the "read aloud by default" switch; **Language variants**, a regional voice per
  language that has several (English US/UK/AU/IN, German DE/AT/CH, French FR/CA/BE, Spanish
  ES/MX/AR/CO, Portuguese PT/BR, Dutch NL/BE), or Default; **System text-to-speech**, naming the active engine, linking to the free eSpeak NG and
  RHVoice voices, and opening Android's settings.
- **AI Assistant:** an info dialog, then two rows with their status:
  - **Cloud AI:** provider (Google Gemini by default, Groq, OpenRouter, Mistral, or Custom with a
    base URL), API key (hidden unless shown; stored only on this device and left out of Android backup), and
    model (blank uses the provider's default; camera import needs a model that reads images).
    Changing provider clears key, URL and model.
  - **On-device AI:** the device's RAM; translation models, one card each with download size, RAM
    need, a "Recommended" badge and Download / Cancel / Delete; only one is kept, and replacing
    it, a download of 1 GB or more, or a model needing more RAM than the device has asks first.
    The page reader for camera import is a single download.
- **Data:** delete all practice results, after confirmation; the row shows how many are stored.
- **About:** logo, name, version, description, open-source and donation notes, the privacy policy
  (opens in the browser) and acknowledgements (OpenMoji flags, fonts).
