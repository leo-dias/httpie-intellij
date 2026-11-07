# SDD ledger — plan: docs/superpowers/plans/2026-11-07-httpie-intellij.md

- User approved design and native execution.
- Ruling: use the new standalone directory as isolation, without git initialization/worktrees or commit-based helper scripts — no repository exists and the plan prohibits unsolicited commits — cost: progress is documented here rather than in git history.
- Pre-flight: Task 1 Request -> Task 2 PreparedRequest -> Task 3 RunResult -> Task 4 service/UI; all producers and consumers agree. Nullable request means no executable block.
- Ruling: pin Java/JVM 25 — installed SDK requires Java 25 — cost: contributors need a JDK 25 to build.
- Task 1: in progress (build setup and parser tests).
- Task 1: complete — missing parser failed compilation first; `./gradlew test -PlocalIdePath=…` passed all 9 parser tests.
- [x] Task 1: parser
- Task 2: complete — missing preparation types failed compilation first; full suite passed (15 tests).
- [x] Task 2: environment preparation
- Task 3: complete — runner tests failed on missing implementation, then full suite passed (24 tests, including real HTTPie/Node, no skipped integrations).
- Task 4: implementation and documentation present; platform tests first failed on missing HttpieSession, then all 27 tests passed, including actual plugin registration and state disposal. Distribution/Verifier running; final independent review dispatched.
- Packaging verification: `test buildPlugin verifyPluginProjectConfiguration verifyPlugin -PlocalIdePath=…` BUILD SUCCESSFUL; Verifier compatible on IU-262.11879.100, with 4 deprecated and 6 experimental tool-window default-method references. Artifact inspected: 60,276-byte ZIP, exact minimum/no maximum, runner class, unchanged handler; 27 tests, 0 failures/skips.
- Final review: two Important process-lifecycle findings and one parser finding. Regraded the parser delimiter issue Important because valid JavaScript behavior differs from the original plugin. Three added regression tests failed as expected (30 tests, 3 failures) before fixes.
- Final fix pass: retain observed descendant handles after their parent exits; poll stream futures with cancellation/deadline checks; match inline handler terminators at end-of-line like the source plugin. Compile using JVM default methods rather than generated tool-window default delegation bridges.
- Final: fixed cancellation during stream draining — `cancellation during pipe drain is prompt and kills reparented child` RED→GREEN.
- Final: fixed lost descendants after shell parent exit — `timeout kills child observed before shell parent exits` RED→GREEN.
- Final: fixed premature inline-handler termination — `delimiter inside JavaScript string does not end handler` RED→GREEN.
- Final fix-pass suite: 30/30 tests, 0 skips; ZIP rebuilt and metadata/resource contents verified.
- Final: Ruling: preserve native Java process handling rather than add OS-specific process-group launchers — it covers observed descendants across parent exit, but deliberately detached/unobserved daemon processes can outlive cancellation; limitation documented — cost: cancellation is not a containment sandbox.
- Final review declined-to-judge items: interactive UI/accessibility, packaging/Verifier elsewhere, Windows/later IDEs. Packaging has been independently verified here; interactive UI, Windows and newer IDE runtime checks remain explicitly unverified.
- [x] Task 3: execution and handlers
- [x] Task 4: IDE integration and ZIP
- [x] Final review and verification
- Final Plugin Verifier: Compatible on IU-262.11879.100, no deprecated/experimental API findings. JVM default-method mode removed all ten generated bridge findings. SDK layout warnings concern the installed IDE's optional commercial modules, not this plugin's dependencies.
- Task 4: complete. No interactive IDE smoke test or additional SDK download performed; no publish, commit, git initialization, or existing-project modifications. Native execution artifacts remain in the standalone project; there is no git-backed scratch workspace to delete.
- Final fresh verification: `./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPlugin -PlocalIdePath=…` BUILD SUCCESSFUL in 52s; all 30 tests pass, no skips, final ZIP compatible with IU-262.11879.100. No unresolved review findings or deferred minors.

- 0.1.5: moved responses into the native Run window, one reusable tab per request. Run contents are not registered in unit-test mode, so the editor integration test disposes viewer editors via the component tree. Final `clean test buildPlugin verifyPluginProjectConfiguration verifyPlugin`: 55 tests, 0 skips, Verifier compatible on IU-262.11879.100, ZIP 109,815 bytes. Live toolbar/GUI reuse remains manually unverified.

- 0.1.6: added a 40x40 light/dark plugin logo (`META-INF/pluginIcon.svg`, `pluginIcon_dark.svg`) with a web/API request-response motif: a globe inside two directional arrows on a blue-cyan tile. SVG only, 32x32 visible tile with 4px transparent padding, ~1.1kB each.

- 0.1.7: added a 16x16 light/dark `.httpie` file-type icon (blue-cyan page with white request/response arrows) under `icons/`, loaded through the public IconLoader. Platform test pins that the file type no longer uses the default text icon and that the SVG is packaged.

- 0.1.8: added an **Environment** combobox to a slim bar at the top of every local `.httpie` editor (public `editorNotificationProvider` API). It lists `.env` files beside the request file and in the project root, defaults to `(none)`, and writes the project-local memory-only `HttpieSession.selectedEnvironment`; `# env:` still wins. Replaced the old `Select HTTPie Environment` context-menu action so there is one selector and no stale state. Tests cover provider filtering, default-none, discovery ordering, session updates and restoring a selection (falling back to none when the file disappears).
