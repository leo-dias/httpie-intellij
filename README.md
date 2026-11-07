# HTTPie for IntelliJ IDEA

Write and run HTTP requests from `.httpie` files without leaving IntelliJ IDEA. The format and workflow follow [httpie.nvim](https://github.com/leo-dias/httpie.nvim), using the real [HTTPie CLI](https://httpie.io/cli) rather than IntelliJ's built-in HTTP Client.

## Requirements

- IntelliJ IDEA **2026.2.4** (build `262.11879.100`) or later. Future IDE versions are allowed by the descriptor but must still be tested for API compatibility.
- `http` on the IDE's PATH (`brew install httpie` or `pipx install httpie`).
- Optional: `node` on the IDE's PATH, for JavaScript response handlers.
- **JDK 25** to build the plugin. End users use the IDE's bundled runtime.

REST-style requests work wherever HTTPie is available. Plain shell-style requests require macOS/Linux (`/bin/sh`).

## Build and install

```sh
./gradlew test buildPlugin
```

The default build downloads the IDEA 2026.2.4 SDK. To build against an existing installation instead:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration \
  -PlocalIdePath="$HOME/Applications/IntelliJ IDEA.app"
```

Install `build/distributions/httpie-intellij-0.1.7.zip` using **Settings → Plugins → gear menu → Install Plugin from Disk…**. Restart the IDE if prompted. If upgrading from an earlier version, install this ZIP over it and reopen your `.httpie` files after restarting.

To launch a separate development IDE or check binary API compatibility:

```sh
./gradlew runIde -PlocalIdePath="$HOME/Applications/IntelliJ IDEA.app"
./gradlew verifyPlugin -PlocalIdePath="$HOME/Applications/IntelliJ IDEA.app"
```

No Marketplace publishing or signing is configured. The Gradle wrapper and dependency versions are checked in.

## Run a request

Open a `.httpie` file and put the caret inside the request. Press **Ctrl+Option+R** on macOS (**Ctrl+Alt+R** on Windows/Linux) to run it directly, without a run-configuration popup. The shortcut takes priority over the IDE's configuration chooser only in `.httpie` editors, including Eclipse-derived macOS keymaps; shortcuts in other file types are unchanged. You can remap **Run HTTPie Request** under **Settings → Keymap → Plug-ins → HTTPie**, or search for the action name.

You can also click the **green Run triangle in the gutter**, use right-click → **Run HTTPie Request**, or search for the action in **Find Action**.

Request methods, URLs (including HTTPie's `:8081` localhost shorthand), comments, headers, `{{variables}}`, strings and directives use your IDE theme's syntax colors. The gutter button runs its own request even when the caret is in another block.

Requests run in the background; cancel them through the IDE's background-task progress UI or the **Stop** button. One request runs per project at a time. Responses open in the IDE **Run** tool window, showing timing, status, headers, body, handler results, stderr and failures. Text is selectable/copyable; available JSONC/XML file types provide syntax coloring. Requests and response handlers each time out after 30 seconds.

JSON responses have a richer light/dark-aware palette: blue keys, green strings, orange numbers, purple booleans and red/pink null values. Matching `{}` and `[]` pairs use rainbow colors by nesting depth (six colors, repeating). No Rainbow Brackets plugin is required. This is display-only: copying or saving the response keeps its original text, and XML/plain-text responses keep their normal highlighting.

Each request keeps its own **Run** tab, and running it again updates and selects that same tab. Tabs are identified by source file plus the selected `###` label (or request position when unnamed/duplicated), so same-named requests in different files stay separate. The tab title comes from the auto-created run configuration; the response header repeats the selected request name. Error and cancellation messages stay in that request's tab.

## Saved run configurations

**HTTPie** is available in **Run → Edit Configurations → + → HTTPie**. Each configuration runs **one specific request**, never all requests in the file.

Running a request with the editor shortcut, context menu or gutter automatically creates/selects a project-local run configuration. Repeating the same selection reuses it. You can then select it in the IDE toolbar and click **Run**, even with another editor open. Its tab shows the same rich response display, including JSON colors and rainbow brackets. The IDE's **Stop** action cancels the request.

Configuration fields:

- **HTTPie file:** browse to an existing `.httpie` file (a relative path is resolved from the project root).
- **Request number:** 1-based position among nonempty request blocks.
- **Request name:** optional exact `###` label without the `###`; when present, it takes precedence over the number. A uniquely named request follows its label if blocks are reordered. Missing or ambiguous labels fail clearly instead of silently running a different block.

Auto-created configurations use a unique label when possible. Unnamed or duplicate-labelled requests use their position, so update those configurations if you reorder blocks. Run configurations store the file reference and label/number only—not request bodies, tokens, globals or environment values. They use the current project environment selection, unless the file supplies `# env:`. Give requests unique names for stable saved selections.

Only **Run** is supported; no debugger is attached to HTTP requests or response scripts.

```http
# env: local.env

### Get users
GET {{url}}/users
Authorization: Bearer {{token}}

### Create a user
POST {{url}}/users
Content-Type: application/json

{
  "name": "Ada"
}

> {%
  client.test("created", () => client.assert(response.status === 201));
  client.global.set("userId", response.body.id);
%}

### Fetch it back
GET {{url}}/users/{{userId}}
```

- Requests are separated by `###`. A caret on the separator selects the following request.
- Supported methods: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS. A bare HTTP(S) URL means GET.
- Put headers after the request line, then a blank line before the body.
- Use `#` or `//` comments before a REST request or in its header block.
- Unsaved editor changes are used; the request file itself needs a local filesystem path.

## Environments

Example `local.env`:

```dotenv
url=http://127.0.0.1:8080
token=replace-me
export OTHER_VALUE="a value"
```

Pick an `.env` file from the **Environment** selector at the top of every `.httpie` editor. It lists env files beside the request file and in the project root; `(none)` is the default and clears the selection. A `# env: local.env` directive anywhere in the request file overrides the selection; relative env paths are checked beside the request file, then in the project root. Absolute paths and `~/` are accepted.

Variables use `{{name}}`. Resolution order: handler globals → env-file values → IDE process/shell environment. Missing variables or unreadable input files stop the request before launching HTTPie. Values from env files and globals are also exported to the HTTPie process. Env files are reread on every run.

Selection and globals are **project-local and memory-only**, and are cleared on project close. Add real env files to your own project's `.gitignore`.

## File bodies, handlers and saved responses

```http
POST http://127.0.0.1:8080/users
Content-Type: application/json

<@ ./payload.json
> ./check.js
>>! ./last-response.json
```

- `< ./payload.json`: sends UTF-8 file contents literally, including any `{{braces}}`.
- `<@ ./payload.json`: substitutes variables in the file before sending.
- `> ./check.js`: executes an external JavaScript response handler.
- `> {% ... %}`: executes an inline handler, on one line or multiple lines.
- `>>! ./last-response.json`: saves only the response body when HTTPie exits successfully, including HTTP 4xx/5xx unless HTTPie was given `--check-status`.

**Both `>>` and `>>!` overwrite existing files.** Paths are relative to the request file. Parent directories must already exist. Response saving happens before handlers, like the Neovim plugin.

Handlers require Node.js and receive:

- `response.status`, `response.body` (JSON parsed when possible)
- `response.headers.valueOf(name)` and `.valuesOf(name)` (case-insensitive names)
- `response.contentType.mimeType` and `.charset`
- `client.test(name, fn)`, `client.assert(condition, message)`, `client.log(...values)`
- `client.global.set(name, value)`, `.get(name)`, `.clear(name)`, `.clearAll()`, `.isEmpty()`

Globals are strings and can be reused in later requests. Test failures and script errors appear in the response view without discarding the HTTP response.

## Plain HTTPie commands

```sh
# env: local.env
http GET {{url}}/users \
  Authorization:"Bearer $token"
```

These run through `/bin/sh -c`, with the leading `http` optional. Formatting/status-header flags and `--ignore-stdin` are added as in the Neovim plugin. Explicit `--pretty`, `--print`/`-p`, `--verbose`/`-v`, `--headers`/`-h`, and `--body`/`-b` options are respected. Use REST-style requests when you do not need shell syntax.

## Security and limitations

- **Only run files you trust.** Shell requests and JavaScript handlers can execute local code with your account's permissions. The plugin refuses requests in untrusted projects and never runs on file/project open.
- Cancellation terminates observed child processes. Commands that deliberately daemonize/detach are not sandboxed and may outlive cancellation.
- URLs, headers, commands and responses can contain secrets and are visible in the Run tool window. Do not include secrets in screenshots or shared output. The plugin does not persist its own response history, environment selection or globals; IntelliJ/HTTPie/Node and invoked commands have their own behavior (e.g. HTTPie sessions or shell redirects can write files).
- Binary file bodies, multipart `<` bodies, pre-request scripts and `client.exit()` are not supported. Use HTTPie's own CLI options where needed.
- No request completion, automatic env discovery outside the request directory/project root, or built-in HTTP Client integration. Syntax coloring is lightweight, not full embedded JSON/JavaScript language support.
- If an executable cannot be found, ensure `http --version` / `node --version` works in the IDE's environment and restart the IDE after PATH changes.

## Tests and attribution

`./gradlew test` runs parser/environment tests, localhost-only HTTPie/Node integration tests, and IntelliJ Platform registration/lifecycle tests. CLI integration tests are explicitly skipped if their executables are missing. No public endpoint is used.

The request behavior and bundled JavaScript handler originate from [leo-dias/httpie.nvim](https://github.com/leo-dias/httpie.nvim). MIT license; see [LICENSE](LICENSE).
