/* Focused real-bundle integration regression. Synthetic DOM events in desktop Chrome do
 * not qualify physical keyboards, Android WebView, native IME, SSH, or accessibility.
 * Usage: node tests/test_terminal_input_browser.cjs --assets /absolute/terminal-assets
 *        [--surface probe|product] [--expect-rapid fixed|duplicated] [--chrome /usr/bin/google-chrome]
 * Assets must contain vendor/{xterm.js,addon-fit.js,xterm.css}; authored CSS/JS is read
 * from the selected source surface. Probe is the default for existing consumers. No downloads,
 * dependency installs, server or personal profile.
 */
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");
const { spawn, spawnSync } = require("node:child_process");
const args = process.argv.slice(2);
function option(name, fallback) {
  const index = args.indexOf(name);
  if (index < 0) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith("--")) throw Error(`Missing ${name} value`);
  return args[index + 1];
}
const assets = option("--assets");
if (!assets || !path.isAbsolute(assets)) throw Error("--assets requires an explicit absolute terminal asset directory");
const surface = option("--surface", "probe");
if (!["probe", "product"].includes(surface)) throw Error("--surface must be probe or product");
const rapid = option("--expect-rapid", "fixed");
if (!["fixed", "duplicated"].includes(rapid)) throw Error("--expect-rapid must be fixed or duplicated");
const root = path.resolve(__dirname, "..");
const authoredRoot = surface === "product"
  ? path.join(root, "shared/android/terminal/src/main/assets/terminal")
  : path.join(root, "qualification/probes/device-baseline/app/src/main/assets/terminal");
const files = {
  core: path.join(assets, "vendor/xterm.js"), fit: path.join(assets, "vendor/addon-fit.js"),
  css: path.join(assets, "vendor/xterm.css"),
  authoredCss: path.join(authoredRoot, "terminal.css"),
  bridge: path.join(authoredRoot, "terminal.js"),
};
const bootHash = `00112233-4455-6677-8899-aabbccddeeff/ffeeddcc-bbaa-9988-7766-554433221100/1${surface === "product" ? "/0" : ""}`;
const sources = Object.fromEntries(Object.entries(files).map(([key, file]) => [key, fs.readFileSync(file, "utf8")]));
const hashes = Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file, sha256: crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex") }]));
const fixturePath = path.join(__dirname, "fixtures/terminal_input_browser.js");
const fixture = fs.readFileSync(fixturePath, "utf8");
for (const [key, file] of Object.entries({ runner: __filename, fixture: fixturePath })) {
  hashes[key] = { path: file, sha256: crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex") };
}
const temp = fs.mkdtempSync(path.join(os.tmpdir(), "cloudy-terminal-browser-"));
async function runChrome(entry) {
  // Real-time CDP over inherited pipes: virtual-time --dump-dom can starve iframe RAFs.
  // No debug TCP listener, external dependency or personal browser profile is involved.
  const chrome = spawn(option("--chrome", "/usr/bin/google-chrome"), [
    "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
    `--user-data-dir=${path.join(temp, "profile")}`, "--remote-debugging-pipe",
    "--disable-background-timer-throttling", "--window-size=1200,900", "about:blank",
  ], { stdio: ["ignore", "ignore", "pipe", "pipe", "pipe"],
    env: { ...process.env, XDG_CONFIG_HOME: path.join(temp, "config"), XDG_CACHE_HOME: path.join(temp, "cache") } });
  let serial = 0, received = "", diagnostics = "";
  const pending = new Map();
  const exited = new Promise(resolve => chrome.once("close", resolve));
  const fail = error => { for (const request of pending.values()) request.reject(error); pending.clear(); };
  chrome.stderr.setEncoding("utf8").on("data", chunk => { diagnostics = (diagnostics + chunk).slice(-4000); });
  chrome.on("error", fail);
  chrome.on("close", (code, signal) => fail(Error(`Chrome closed: ${signal || code}\n${diagnostics}`)));
  chrome.stdio[3].on("error", fail);
  chrome.stdio[4].setEncoding("utf8").on("error", fail).on("data", chunk => {
    received += chunk;
    let end;
    while ((end = received.indexOf("\0")) >= 0) {
      const text = received.slice(0, end); received = received.slice(end + 1);
      if (!text) continue;
      const message = JSON.parse(text), request = pending.get(message.id);
      if (!request) continue;
      pending.delete(message.id);
      if (message.error) request.reject(Error(JSON.stringify(message.error)));
      else request.resolve(message.result);
    }
  });
  function send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++serial; pending.set(id, { resolve, reject });
      chrome.stdio[3].write(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }) + "\0");
    });
  }
  const deadline = setTimeout(() => { fail(Error("Chrome fixture exceeded 60 seconds")); chrome.kill(); }, 60000);
  try {
    const target = await send("Target.createTarget", { url: `file://${entry}` });
    const { sessionId } = await send("Target.attachToTarget", { targetId: target.targetId, flatten: true });
    await send("Page.bringToFront", {}, sessionId);
    const evaluation = await send("Runtime.evaluate", {
      expression: `new Promise((resolve, reject) => {
        const deadline = Date.now() + 45000;
        function check() {
          const value = document.getElementById("result")?.textContent;
          if (value && value !== "pending") {
            try { resolve(JSON.parse(value)); } catch (error) { reject(error); }
          } else if (Date.now() >= deadline) reject(Error("Browser fixture did not finish"));
          else setTimeout(check, 20);
        }
        check();
      })`, awaitPromise: true, returnByValue: true,
    }, sessionId);
    if (evaluation.exceptionDetails) throw Error(JSON.stringify(evaluation.exceptionDetails));
    return evaluation.result.value;
  } finally {
    const killDeadline = setTimeout(() => chrome.kill("SIGKILL"), 3000);
    try { await send("Browser.close"); } catch (_) { /* A closing browser may retire its pipe first. */ }
    await exited;
    clearTimeout(killDeadline); clearTimeout(deadline);
  }
}

(async () => { try {
  const html = `<!doctype html><meta charset="utf-8"><pre id="result">pending</pre><script>const bundle=${JSON.stringify(sources).replaceAll("<", "\\u003c")};const rapidExpectation=${JSON.stringify(rapid)};const surface=${JSON.stringify(surface)};const bootHash=${JSON.stringify(bootHash)};${fixture.replaceAll("</script", "<\\/script")}</script>`;
  const entry = path.join(temp, "test.html"); fs.writeFileSync(entry, html);
  const report = await runChrome(entry);
  const scope = surface === "product"
    ? "Synthetic DOM -> generated vendor xterm + fit -> authored product bridge (shared/android/terminal/src/main/assets/terminal) booted with four-part hash ending /0 (screenReaderMode off) -> test native endpoint; exact insertText/preedit/control/closed-attachment bytes only; no physical IME/WebView/SSH/TalkBack qualification"
    : "Synthetic DOM -> actual xterm + fit -> authored Cloudy bridge -> test native endpoint; no physical IME/WebView/SSH qualification";
  console.log(JSON.stringify({ ...(surface === "product" ? { surface } : {}), scope, chrome: spawnSync(option("--chrome", "/usr/bin/google-chrome"), ["--version"], { encoding: "utf8" }).stdout.trim(), hashes, ...report }, null, 2));
  if (surface === "product") {
    const cases = Array.isArray(report.cases) ? report.cases : [];
    process.exitCode = report.error || cases.length === 0 || report.allInputIntegrity !== true ||
      cases.some(item => !item.regressionMatched || !item.inputIntegrity) ? 1 : 0;
  } else {
    process.exitCode = report.error || !report.smoke || !report.smoke.regressionMatched || report.cases.some(item => !item.regressionMatched) ? 1 : 0;
  }
} finally { fs.rmSync(temp, { recursive: true, force: true }); }
})().catch(error => { console.error(error); process.exitCode = 1; });
