/* Focused real-bundle integration regression. Synthetic DOM events in desktop Chrome do
 * not qualify physical keyboards, Android WebView, native IME, SSH, or accessibility.
 * Usage: node tests/test_terminal_input_browser.cjs --assets /absolute/terminal-assets
 *        [--expect-rapid fixed|duplicated] [--chrome /usr/bin/google-chrome]
 * Assets must contain vendor/{xterm.js,addon-fit.js,xterm.css}; the authored bridge is
 * always read from source. No downloads, dependency installs, server or personal profile.
 */
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");
const { spawnSync } = require("node:child_process");
const args = process.argv.slice(2);
function option(name, fallback) {
  const index = args.indexOf(name);
  if (index < 0) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith("--")) throw Error(`Missing ${name} value`);
  return args[index + 1];
}
const assets = option("--assets");
if (!assets || !path.isAbsolute(assets)) throw Error("--assets requires an explicit absolute terminal asset directory");
const rapid = option("--expect-rapid", "fixed");
if (!["fixed", "duplicated"].includes(rapid)) throw Error("--expect-rapid must be fixed or duplicated");
const root = path.resolve(__dirname, "..");
const files = {
  core: path.join(assets, "vendor/xterm.js"), fit: path.join(assets, "vendor/addon-fit.js"),
  css: path.join(assets, "vendor/xterm.css"),
  authoredCss: path.join(root, "qualification/probes/device-baseline/app/src/main/assets/terminal/terminal.css"),
  bridge: path.join(root, "qualification/probes/device-baseline/app/src/main/assets/terminal/terminal.js"),
};
const sources = Object.fromEntries(Object.entries(files).map(([key, file]) => [key, fs.readFileSync(file, "utf8")]));
const hashes = Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file, sha256: crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex") }]));
const fixturePath = path.join(__dirname, "fixtures/terminal_input_browser.js");
const fixture = fs.readFileSync(fixturePath, "utf8");
for (const [key, file] of Object.entries({ runner: __filename, fixture: fixturePath })) {
  hashes[key] = { path: file, sha256: crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex") };
}
const temp = fs.mkdtempSync(path.join(os.tmpdir(), "cloudy-terminal-browser-"));
try {
  const html = `<!doctype html><meta charset="utf-8"><pre id="result">pending</pre><script>const bundle=${JSON.stringify(sources).replaceAll("<", "\\u003c")};const rapidExpectation=${JSON.stringify(rapid)};${fixture.replaceAll("</script", "<\\/script")}</script>`;
  const entry = path.join(temp, "test.html"); fs.writeFileSync(entry, html);
  const run = spawnSync(option("--chrome", "/usr/bin/google-chrome"), ["--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check", `--user-data-dir=${path.join(temp, "profile")}`, "--disable-background-timer-throttling", "--window-size=1200,900", "--virtual-time-budget=45000", "--dump-dom", `file://${entry}`], { encoding: "utf8", timeout: 60000, maxBuffer: 8 * 1024 * 1024, env: { ...process.env, XDG_CONFIG_HOME: path.join(temp, "config"), XDG_CACHE_HOME: path.join(temp, "cache") } });
  if (run.error || run.status !== 0) throw Error(`Chrome failed: ${run.error || run.signal || run.status}\n${run.stderr}`);
  const match = run.stdout.match(/<pre id="result">([^<]*)<\/pre>/);
  if (!match || match[1] === "pending") throw Error(`Browser did not finish\n${run.stderr}\n${run.stdout.slice(-2000)}`);
  const report = JSON.parse(match[1].replaceAll("&quot;", '"').replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&amp;", "&"));
  console.log(JSON.stringify({ scope: "Synthetic DOM -> actual xterm + fit -> authored Cloudy bridge -> test native endpoint; no physical IME/WebView/SSH qualification", chrome: spawnSync(option("--chrome", "/usr/bin/google-chrome"), ["--version"], { encoding: "utf8" }).stdout.trim(), hashes, ...report }, null, 2));
  process.exitCode = report.error || !report.smoke || !report.smoke.regressionMatched || report.cases.some(item => !item.regressionMatched) ? 1 : 0;
} finally { fs.rmSync(temp, { recursive: true, force: true }); }
