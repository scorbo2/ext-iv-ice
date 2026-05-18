# Copilot instructions for `ext-iv-ice`

## Build, test, and lint commands

- **Build the extension jar:** `mvn package`
  - This repository depends on `ca.corbett:imageviewer:3.2`, so install/build the main ImageViewer project into your local Maven repository first. The README explicitly calls this out.
- **Build without tests:** `mvn -DskipTests package`
- **Run the test suite:** `mvn test`
  - There are JUnit 5 tests under `src/test/java`, but the current `pom.xml` does not configure a modern Surefire/JUnit 5 test runner. In this repository's current state, `mvn test` reports `Tests run: 0`.
- **Run one test class:** `mvn -Dtest=TagListTest test`
  - This is the normal Maven single-test pattern, but it currently hits the same Surefire/JUnit 5 discovery limitation as the full test run.
- **Lint:** no dedicated lint/format/checkstyle/spotless command is configured in `pom.xml`.

## High-level architecture

- `IceExtension` is the integration hub. It registers config properties, top-level ICE menu actions, extra UI panels, companion-file handling, and lifecycle hooks (`onActivate` loads the tag index, `onDeactivate` saves it).
- Per-image tags are stored in sidecar files named `<image-basename>.ice` in the same directory as the image. Most user actions eventually read/write those sidecar files through `TagList`.
- `TagList` is the core tag model: it normalizes tags to lowercase, strips forbidden characters, preserves insertion order, de-duplicates automatically, and persists one tag per line when saving to disk.
- `TagIndex` is an optional in-memory/search-performance layer backed by `Version.SETTINGS_DIR/tagIndex.ice`. Searches use it opportunistically:
  - `SearchThread` first asks the index for matches when indexing is enabled and the entry is up to date.
  - If the index misses, the code falls back to reading the `.ice` sidecar directly and then refreshes the index entry.
  - Browsing, editing, batch tagging, hotkey tagging, and file operations all try to keep the index synchronized incrementally.
- UI behavior is split between actions, dialogs, and worker threads:
  - `actions/*` launches user-visible operations.
  - `ui/dialogs/*` collects input or reviews results.
  - `threads/*` performs longer-running filesystem/search/batch-tag work off the UI thread.
- Auto-tagging is isolated under `llm/*`. `IceExtension` loads the JSON request templates from jar resources early in the constructor, `AiConnectionManager` reads AppConfig on demand and appends `/v1/chat/completions`, and `AutoTagAction`/`AutoTagBatchAction` handle UI flow around that service.
- Quick-tag UI is separate from per-image `.ice` files. `QuickTagPanel` persists reusable tag groups as `.tag` files under `Version.SETTINGS_DIR/quickTags`, organized by source name and by panel position (left/right source preference is stored separately).

## Key conventions

- Treat `.ice` files as first-class companion files. `IceExtension` overrides companion-file hooks so copy/move/rename/delete operations keep sidecars and the tag index aligned.
- Companion matching is based on the image **basename**, not the full filename. `image01.jpg` and `image01.png` would both map to `image01.ice`; this edge case is documented in `IceExtension.getMatchingImageFile()`, so preserve that assumption unless you intentionally redesign the format.
- After changing tags, the usual pattern is:
  1. save the `TagList`
  2. call `TagIndex.getInstance().addOrUpdateEntry(...)`
  3. trigger `ImageViewerExtensionManager.getInstance().imageSelected(...)` or equivalent UI refresh
- Actions with configurable keyboard shortcuts are often implemented as singletons (`TagSingleImageAction`, `AutoTagAction`, `AutoTagBatchAction`) so the bound accelerator updates cleanly when properties change.
- Validation logic should reuse the existing validators and `TagList` rules rather than duplicating parsing rules. Tag names/tag lists reject `{`, `}`, `|`, and `,`, and the validators are under `ui/formfield/`.
- Tests are package-private JUnit 5 tests and typically use explicit GIVEN/WHEN/THEN-style comments. Existing tests cover pure logic classes (`TagList`, `TagIndex`, `TagIndexPersistence`) rather than Swing UI classes.
