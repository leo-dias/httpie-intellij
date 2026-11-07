# Run-window-only responses — 0.1.5

Approved behavior: all request entry points use the native Run window; retain separate request tabs, reuse a matching open tab on rerun, keep rich JSON/paired rainbow bracket coloring, and stop opening the separate HTTPie tool window.

- [x] Pin native rich-console and per-request reuse behavior with tests before implementing.
- [x] Move the existing viewer into ExecutionConsole and remove duplicate tool-window output/registration.
- [x] Route editor/gutter actions through their saved native run configuration.
- [x] Use native descriptor reuse policy and explicit matching descriptor selection (file + unique request label, otherwise request position), without a stale tab cache.
- [x] Preserve error/cancellation and native Stop behavior; update docs/version; full tests, review and verifier.

No change to HTTP transport, environment behavior, stored request contents, or saved response bytes. Named identity follows the existing run-configuration policy; unnamed/duplicate labels use position. Generic environment/busy messages use native notifications, not a second output window.

Verification: full suite green after converting the response viewer into an `ExecutionConsole` and selecting matching open tabs through the public `contentToReuse`/reuse-policy APIs. Unit-test mode does not register run contents with `RunContentManager`, so the editor integration test disposes viewer editors by component tree; real run-content tests dispose consoles explicitly. Native Stop, reordered saved labels, JSON indentation/colors, and the absence of a second HTTPie tool window are covered by platform tests. Packaging and Plugin Verifier results are recorded below after the fresh build.

Fresh `clean test buildPlugin verifyPluginProjectConfiguration verifyPlugin` on 2026-11-07: 55 tests, 0 failures/skips; Plugin Verifier compatible on IU-262.11879.100 with no internal-API findings; `build/distributions/httpie-intellij-0.1.5.zip` (109,815 bytes). Review note: `RunContentManager.getRunContentDescriptors()` was rejected as internal by the Verifier and replaced with the public `getAllDescriptors()` before the final green build.

Unverified: live toolbar/dialog interaction and a real GUI Run-tab reuse smoke test were not performed here; tab reuse is covered by descriptor selection/policy tests and the platform run-content test.