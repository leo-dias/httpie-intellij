# HTTPie IntelliJ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an installable IntelliJ IDEA plugin matching the HTTPie Neovim workflow.

**Architecture:** A single Kotlin plugin separates pure request preparation from cancellable process execution and platform UI actions. Bundle the existing Node.js response handler, use installed HTTPie for transport, and keep session state project-local.

**Tech Stack:** Kotlin, Gradle wrapper, IntelliJ Platform Gradle plugin 2.x, IntelliJ IDEA 2026.2.4 SDK, JUnit, HTTPie CLI, optional Node.js.

**Spec:** `docs/superpowers/specs/2026-11-07-httpie-intellij.md`

## Global Constraints

- Minimum IDE: `262.11879.100` (IntelliJ IDEA 2026.2.4); no maximum build.
- Default timeout: 30,000 ms.
- External HTTPie required; Node.js required only for response handlers.
- No built-in HTTP Client dependency and no automatic execution.
- Retain MIT attribution for reused code; never modify the Neovim project.
- `>>` and `>>!` overwrite; binary/multipart enhancements are out of scope.
- No publishing, remote repository creation, or unsolicited commits.

## Review Focus

- CRLF, empty blocks, and caret positions on separators must select the correct request.
- Missing variables and missing files must stop execution before a subprocess starts.
- Empty bodies, duplicate headers, and header-like text in bodies must not corrupt responses or saved output.
- Timeouts, cancellation, and project disposal must not leave subprocesses or update disposed UI.
- Secrets and handler globals must never be persisted by the plugin or sent to external test endpoints.

## File Structure

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew*`, `gradle/wrapper/*`: reproducible SDK and packaging setup.
- `src/main/kotlin/io/github/leodias/httpie/RequestParser.kt`: request block selection and directive parsing.
- `src/main/kotlin/io/github/leodias/httpie/RequestEnvironment.kt`: environment files, precedence, substitution, file resolution.
- `src/main/kotlin/io/github/leodias/httpie/HttpieRunner.kt`: process lifecycle, response parsing, saving, handler invocation.
- `src/main/kotlin/io/github/leodias/httpie/HttpieSession.kt`: project-local state and request orchestration.
- `src/main/kotlin/io/github/leodias/httpie/HttpieActions.kt`: run/select-env actions.
- `src/main/kotlin/io/github/leodias/httpie/HttpieFileType.kt`: `.httpie` registration.
- `src/main/kotlin/io/github/leodias/httpie/HttpieToolWindow.kt`: reusable accessible result view.
- `src/main/resources/META-INF/plugin.xml`, `src/main/resources/handler.js`: platform registration and original handler.
- `src/test/kotlin/io/github/leodias/httpie/*Test.kt`: behavior and loopback integration tests.
- `README.md`, `LICENSE`, `examples/requests.httpie`, `.gitignore`: usage, attribution, example, build/secrets exclusions.

### Task 1: Request parsing and reproducible test build

**Interfaces:** `RequestParser.parse(text: String, caretLine: Int): Request?`; `Request` contains argv/shell, inline/file body, handler/handler-file, and save path. Caret line is zero-based.

- [ ] Add wrapper/build configuration targeting SDK 2026.2.4, with an optional `localIdePath` Gradle property for the installed SDK. Pin build-tool versions compatible with the SDK/runtime.
- [ ] Write `RequestParserTest`: GET/POST argv, body/no-body flags, URL-only GET, comment-only input, shell continuations, `###` caret boundaries, CRLF, inline/external handlers, `<`/`<@`, and `>>!`.
- [ ] Run `./gradlew test --tests '*RequestParserTest'`; confirm missing parser fails compilation before implementing.
- [ ] Implement `RequestParser` and immutable `Request` in `RequestParser.kt`, preserving request text and avoiding shell use for REST requests.
- [ ] Re-run parser tests; require all pass.

### Task 2: Environment and request preparation

**Interfaces:** `RequestEnvironment.prepare(text: String, caretLine: Int, file: Path, projectRoot: Path, selectedEnv: String?, globals: Map<String,String>, environment: Map<String,String>): PreparedRequest?`; `PreparedRequest` contains resolved argv/shell, stdin, handler code, save path, working directory, exported environment.

- [ ] Write `RequestEnvironmentTest`: env directive overrides selection; globals > env file > process environment; quoted/export values; missing env/file/variable rejects execution; `<` preserves literal variables while `<@` substitutes them; paths resolve relative to request file; temporary secrets are not persisted outside test fixtures.
- [ ] Run `./gradlew test --tests '*RequestEnvironmentTest'`; confirm failure before implementation.
- [ ] Implement file resolution, substitution, validation and preparation using Task 1's parser. Check unresolved variables in request material, not literal `<` body contents.
- [ ] Re-run parser and environment tests; require all pass.

### Task 3: Async-safe execution, responses and handlers

**Interfaces:** `HttpieRunner.run(request: PreparedRequest, globals: Map<String,String>, cancelled: () -> Boolean): RunResult`; `RunResult` includes response text/status/body, duration/exit status, handler report and global updates. Caller supplies project state and cancellation predicate.

- [ ] Copy `handler.js` and MIT license unchanged from the source plugin.
- [ ] Write `HttpieRunnerTest` using a loopback server and temporary fixtures: real GET/POST, body-only response saving, nonzero exit, response duplicate headers and empty bodies, body text containing colons, missing executable, timeout/cancellation termination, JavaScript tests/logs/global set/clear and script failure. Gate external executable tests explicitly when dependencies are unavailable.
- [ ] Run runner tests and observe missing runner failure.
- [ ] Implement subprocess execution off the UI thread, concurrent stream consumption, stdin close, timeout/cancellation cleanup, response parsing and Node handler resource loading. Keep stderr separate from response-body parsing. Do not log secrets outside the requested result view.
- [ ] Run `./gradlew test`; require all tests pass and report skipped executable-dependent tests.

### Task 4: IntelliJ integration and distribution

**Interfaces:** Project service `HttpieSession` owns in-memory globals/environment; `RunHttpieAction` snapshots editor text/caret and schedules background execution; `SelectHttpieEnvironmentAction` selects project/request-directory env files; tool window displays `RunResult` on the UI thread.

- [ ] Write integration/metadata checks for `.httpie` registration, project service disposal, action registration, minimum build, tool window and handler resource packaging; observe checks failing before integration files exist.
- [ ] Implement the project service, actions, file type, and tool window. Use platform background tasks and UI dispatch; reject untrusted projects, disable actions for inappropriate editor context, and check disposal before updating UI. Run-action shortcut: Ctrl+Alt+R, with conflicts documented and remappable in Keymap settings.
- [ ] Register `io.github.leodias.httpie`, platform-only dependency, actions and tool window in `plugin.xml`. Configure exact minimum build and no upper bound.
- [ ] Write README for prerequisites/PATH, build/install ZIP, shortcuts, request syntax, environment precedence, Node handlers, overwrite semantics, trusted-file warning, supported OS behavior, and original plugin attribution. Include safe localhost-only examples, no real secrets.
- [ ] Run `./gradlew test buildPlugin verifyPluginProjectConfiguration`; inspect the ZIP for plugin descriptor, Kotlin classes and bundled handler. Run available Plugin Verifier checks against 2026.2.4 and a later SDK if feasible.
- [ ] Launch `./gradlew runIde` for an interactive smoke test only when feasible; otherwise clearly mark UI behavior unverified. Report artifact path, test results, omissions and compatibility limits.

## Execution / Review

- [x] Compare every approved requirement against tasks and interfaces.
- [x] Attach each Review Focus condition to tests in its owning task.
- [x] User reviews implementation plan and chooses native execution.
- [x] Execute tasks in order; completed-step evidence and review-fix results are recorded in `docs/superpowers/progress.md`.

## Final status

All four tasks executed; detailed step checkboxes above are the original execution brief. The progress ledger records completion, RED→GREEN evidence, 30 passing tests and successful packaging/Plugin Verifier results. Interactive UI/accessibility, Windows, and later-IDE runtime verification were not performed. No publishing or git operations were performed.
