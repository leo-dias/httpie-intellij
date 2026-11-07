# Caret keyboard execution — 0.1.3

- [x] Inspect screenshot, action registration, active user keymap and SDK keymap inheritance.
- Root cause: Ctrl+Option+R invokes ChooseRunConfiguration in Mac OS X 10.5+ (inherited by the user's Eclipse macOS keymap); HTTPie only binds $default and has no context-specific conflict priority.
- [x] Prove macOS keymap binding and action selection fail before changing production code. Both new fixture tests failed before the production fix.
- [x] Register explicit macOS shortcut and an HTTPie-editor-only action promoter. Keep other editor shortcuts intact and respect the existing trust/action guards.
- [x] Verify the resolved keyboard action executes the request at the caret against a local test server, full suite, package and Plugin Verifier. 43 tests, 0 failures/skips; final ZIP inspected (80,746 bytes); Plugin Verifier compatible with IU-262.11879.100.

Scope: shortcut metadata, small context promoter, tests, version and README. No user-keymap edits, global shortcut removals, run configurations, or changes to request execution.

Full suite after fix: 43 tests pass. Tests check both standard macOS keymaps and the real Eclipse macOS keymap, priority against ChooseRunConfiguration, no HTTPie priority in Java files, and actual localhost HTTPie execution from the keyboard-resolved action with the caret in the second request. Eclipse keymap is a test-only bundled dependency, not a shipped dependency.

Independent review: no Critical/Important findings. Deferred minor: fixture checks this promoter against real keymap candidates rather than exercising the full native promoter chain; unknown later promoters may alter priority. No concrete conflicting promoter identified for the reported chooser case. Physical keypresses in the user's running IDE were not manually tested. User keymaps/settings were not modified.
