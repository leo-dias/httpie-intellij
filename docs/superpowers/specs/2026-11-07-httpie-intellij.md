# HTTPie for IntelliJ IDEA

## Approved scope

Create a standalone Kotlin/Gradle plugin at `~/Projects/httpie-intellij` for IntelliJ IDEA 2026.2.4 and above. Preserve the `.httpie` file format and behavior of `~/Projects/httpie.nvim`, without depending on the IDE's built-in HTTP Client.

Support requests under the caret, `###` separators, REST-style requests and plain HTTPie shell commands, environment files and selection, `# env:` directives, and `{{variable}}` substitution. Support inline/file bodies, response saving, and inline/external JavaScript response handlers. Execute asynchronously and display timing, status, headers, body, logs, assertions, and failures in an HTTPie tool window. Provide editor actions and a keyboard shortcut, tests, installation documentation, and a distributable ZIP.

HTTPie is an external requirement. Node.js is required only for response handlers. Shell requests and response scripts execute local code: users must trust request files. Never execute automatically when a file or project opens.

## Implementation decisions

- Use the installed IDEA 2026.2.4 SDK (build `262.11879.100`) for local verification; default downloadable SDK is version `2026.2.4`.
- Declare minimum build `262.11879.100` and no maximum build. This allows later IDE versions to install, but does not guarantee compatibility with untested future APIs.
- Single Gradle project, Kotlin, IntelliJ Platform Gradle plugin 2.x, and a checked-in Gradle wrapper. Do not create or publish a GitHub repository.
- Separate pure request/environment logic from process execution and IntelliJ UI integration so behavior tests do not require a running IDE.
- Reuse the original MIT-licensed `handler.js`, bundled as a resource. Retain its license.
- Project-local environment selection and handler globals are memory-only. Globals override env-file values, which override process environment values.
- Resolve relative body/handler/output paths from the request file's directory; use the project root in place of Neovim's working directory for environment-file fallback.
- Default timeout is 30,000 ms. Close process stdin when no body is supplied; pass `--ignore-stdin` for bodyless HTTP requests. Cancelled/timed-out processes must be terminated, including child processes where supported.
- Reuse a response tool-window tab with read-only selectable text. Use platform UI components, accessible action labels, and editor highlighting where available. No browser UI or additional settings framework.
- Plain shell commands use `/bin/sh -c` on macOS/Linux, matching the original plugin. Explicitly document that this mode is unsupported on Windows; REST-style argv execution remains portable.
- Saved responses contain only the response body. Both `>>` and `>>!` overwrite, matching the existing plugin; document this prominently. Do not save on process failure.
- Request files are UTF-8 text; binary upload and multipart enhancements are out of scope, matching the original limitations.

## Verification

Unit-test parsing, precedence, missing variables, file inputs, response parsing, and handler behavior. Use a loopback HTTP server for execution tests; do not send secrets to public endpoints. Compile and package against the requested IDE SDK, validate plugin metadata, and run Plugin Verifier when available. Report explicitly if interactive IDE testing or newer-IDE verification cannot be performed.
