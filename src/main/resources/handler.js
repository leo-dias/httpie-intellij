// Runs a JetBrains-style response handler: reads {script, status, headers, body, globals} as JSON on stdin,
// writes {globals, logs, tests, error} as JSON on stdout.
const input = JSON.parse(require("fs").readFileSync(0, "utf8"));
const globals = { ...input.globals };
const out = { globals: {}, logs: [], tests: [] };
const str = (v) => (typeof v === "string" ? v : JSON.stringify(v));

const client = {
  global: {
    set: (k, v) => {
      globals[k] = out.globals[k] = String(v);
    },
    get: (k) => globals[k],
    clear: (k) => {
      delete globals[k];
      out.globals[k] = null;
    },
    clearAll: () => Object.keys(globals).forEach(client.global.clear),
    isEmpty: () => Object.keys(globals).length === 0,
  },
  log: (...args) => out.logs.push(args.map(str).join(" ")),
  test: (name, fn) => {
    try {
      fn();
      out.tests.push({ name, ok: true });
    } catch (e) {
      out.tests.push({ name, ok: false, msg: String((e && e.message) || e) });
    }
  },
  assert: (cond, msg) => {
    if (!cond) throw new Error(msg || "Assertion failed");
  },
};

const headers = input.headers; // name (lowercase) -> [values]
const mime = (headers["content-type"] || [""])[0].split(";");
let body = input.body;
try {
  body = JSON.parse(body);
} catch {}

const response = {
  status: input.status,
  body,
  headers: {
    valueOf: (n) => (headers[n.toLowerCase()] || [null])[0],
    valuesOf: (n) => headers[n.toLowerCase()] || [],
  },
  contentType: {
    mimeType: mime[0].trim(),
    charset: ((mime.find((p) => p.includes("charset=")) || "").split("=")[1] || "").trim(),
  },
};

try {
  new Function("client", "response", input.script)(client, response);
} catch (e) {
  out.error = String((e && e.stack) || e);
}
process.stdout.write(JSON.stringify(out));
