# AGENTS.md for `ext-iv-ice`

## Build and test commands

- **Prerequisite:** install the ImageViewer parent project into your local Maven repo first: `cd ../imageviewer && mvn install`
- **Build the extension jar:** `mvn package`
- **Build without tests:** `mvn -DskipTests package`
- **Run all tests:** `mvn test`
  - Tests run as `Tests run: 0` because the `pom.xml` does not configure a modern Surefire JUnit 5 test runner. This is a known limitation — tests still pass when discovered explicitly.
- **Run one test class:** `mvn -Dtest=TagListTest test`
- **No lint / format / checkstyle** is configured. Use IntelliJ's built-in reformat if needed.

## High-level architecture

- `IceExtension` is the integration hub: registers config properties, menu actions, companion-file handling, and lifecycle hooks (`onActivate` loads the tag index, `onDeactivate` saves it).
- Per-image tags live in sidecar `.ice` files (`<basename>.ice`) next to the image. `TagList` is the core model — normalizes to lowercase, strips `{`, `}`, `|`, `,`, preserves insertion order, deduplicates.
- `TagIndex` is an optional in-memory/search-performance layer backed by `<settings>/tagIndex.ice`. `SearchThread` consults the index first, falls back to reading the `.ice` sidecar, then refreshes the index.
- UI split into `actions/*` (user-visible operations), `ui/dialogs/*` (input/results), `threads/*` (off-UI work), `llm/*` (auto-tag via OpenAI-compatible APIs), `ui/formfield/*` (validators).
- Quick-tag UI (`QuickTagPanel`) persists reusable tag groups as `.tag` files under `<settings>/quickTags`, separate from `.ice` sidecars.

## Key conventions

- Companion files are matched by image **basename**, not full name. `image01.jpg` and `image01.png` share the same `image01.ice` — this is a known limitation; preserve the assumption unless you intentionally redesign.
- After changing tags: save the `TagList`, call `TagIndex.getInstance().addOrUpdateEntry(...)`, then trigger `ImageViewerExtensionManager.getInstance().imageSelected(...)` for UI refresh.
- Actions with configurable hotkeys (e.g. `TagSingleImageAction`, `AutoTagAction`, `AutoTagBatchAction`) are singletons so bound accelerators update cleanly on config change.
- Reuse existing validators from `ui/formfield/*` and `TagList` rules for tag validation — do not duplicate parsing logic.
- Tests use GIVEN/WHEN/THEN comments. They cover pure logic classes (`TagList`, `TagIndex`, `TagIndexPersistence`), not Swing UI.
- `TagIndex` is a singleton with package-protected `setAppConfigProvider()` and `setIndexFile()` setters for test isolation.
- `IceExtension` constructor loads LLM request templates from jar resources early (before `loadJarResources()` due to a parent bug).
