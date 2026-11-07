# Specific-request run configurations and response names

Approved scope: native HTTPie run configurations select one request, automatically create/reuse a configuration on editor/gutter execution, and name the response tab from the selected `###` label.

- [x] Test request location/name selection and saved configuration behavior before implementation. Initial compilation failed on absent types; metadata tests also pin CRLF, separator/body caret positions, reordered labels, ambiguity and empty blocks.
- [x] Add a native type, settings editor, runner and process lifecycle bridge using the existing request execution path. Real platform tests exercise native selection/runner registration, unopened source files and Stop cancellation. Headless tasks can execute synchronously, so the cancellation fixture sends Stop from a separate thread after the loopback server receives the request.
- [x] Automatically remember successful editor submissions without duplicates or persisting request contents/env secrets. XML round-trip and real editor execution assertions cover this.
- [x] Name the existing reusable response tab from the request label, falling back to file/request number. A real Content/view fixture checks renaming on reuse.
- [x] Full tests, final review, ZIP and compatibility verification. 52 tests, 0 failures/skips. Final 0.1.4 ZIP inspected (106,925 bytes), native registrations/classes present. Plugin Verifier compatible on IU-262.11879.100 with no API findings.

Selection policy: unique named blocks resolve by label after reordering; missing/ambiguous saved labels fail rather than silently run another block. Unnamed/duplicate-labelled blocks may use explicit 1-based position. Env selection/globals remain memory-only, and `# env:` continues to take precedence. Only one request executes per configuration; no run-all, debugger, custom executables, or auto-created remote repositories.

Review: independent read-only review found no Critical/Important issues or required minors. Added explicit native configuration clone independence and exactly-one-request/reordered-label execution assertions after review. Native Run-profile state/console/process/Stop are tested through real platform fixtures; complete toolbar/dialog interaction in the user's live IDE is not manually verified.

Compatibility gate initially rejected direct ExecutionManager and RunProfileStarter APIs as internal. Final runner uses the supported public AsyncProgramRunner base with the SDK executeState helper; fresh `test buildPlugin verifyPluginProjectConfiguration verifyPlugin` is green. No user run configurations/settings or installed plugin files were changed by the agent; automatic configuration creation begins after the user installs and executes the update.
