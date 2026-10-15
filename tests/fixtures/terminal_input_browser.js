/* Each fresh iframe runs the real bundles and unmodified authored bridge. The only
 * substitute is the native endpoint: it captures INPUT payloads and admits credits.
 * Events are deliberately synthetic; no UA override or private xterm API is used.
 */
"use strict";
const cases = [];
let activeCase = null;
let smoke = null;
(async () => {
  const schedules = [
    ["startup-fit-output", "smoke"],
    ["rapid-stty", "rapid", "stty", rapidExpectation === "fixed" ? "stty" : "sttyttytyy", "stty"],
    ["paced-stty", "paced", "stty", "stty", "stty"],
    ["rapid-repeated", "rapid", "tttt", rapidExpectation === "fixed" ? "tttt" : "tttttttttt", "tttt"],
    ["paced-repeated", "paced", "tttt", "tttt", "tttt"],
    ["single-delete", "delete", "abcd", "\x7f", "\x7f"],
    ["coalesced-delete", "delete-many", "abcd", rapidExpectation === "fixed" ? "\x7f" : "\x7f\x7f\x7f\x7f", "\x7f\x7f\x7f\x7f"],
    // Replacement is a characterization, with its desired edit bytes kept separate.
    ["equal-length-replacement", "replace", "ab", "ax", "\x7fx"],
    ["composition-commit", "commit", "中", "中", "中"],
    ["composition-cancel", "cancel", "中", "", ""],
    ["composition-enter", "enter", "中", "中\r", "中\r"],
    ["key-controls", "controls", "", "\x03\x7f\r", "\x03\x7f\r"],
  ];
  function childTest(config) {
    const wait = () => new Promise(resolve => setTimeout(resolve, 20));
    let nativeSequence = 0, ready = false, readyGeometry = null, readyHeader = null;
    const acknowledgments = [], resizes = [];
    const chunks = [], errors = [];
    const progress = phase => parent.postMessage({ kind: "progress", name: config[0], phase, chunks: chunks.map(chunk => [...chunk]), errors: [...errors], readyGeometry, acknowledgments: [...acknowledgments], resizes: [...resizes] }, "*");
    progress("fixture-started");
    window.addEventListener("error", event => errors.push(event.message));
    window.cloudyTerminal = {
      onmessage: null,
      postMessage(buffer) {
        const view = new DataView(buffer), type = view.getUint8(5);
        if (type === 7) errors.push("authored bridge ERROR frame");
        if (type === 1) {
          ready = true; readyHeader = new Uint8Array(buffer.slice(0, 48));
          readyGeometry = { columns: view.getUint16(48), rows: view.getUint16(50) }; progress("bridge-ready");
        }
        if (type === 3) resizes.push({ columns: view.getUint16(48), rows: view.getUint16(50) });
        if (type === 4) acknowledgments.push({ sequence: view.getUint32(48), length: view.getUint32(52) });
        if (type === 2) { chunks.push(Array.from(new Uint8Array(buffer, 48))); progress("input-captured"); }
        if (type !== 2 && type !== 3) return;
        const reply = new Uint8Array(56); reply.set(new Uint8Array(buffer, 0, 48));
        const v = new DataView(reply.buffer); v.setUint8(5, 6); v.setUint32(40, ++nativeSequence); v.setUint32(44, 8);
        v.setUint32(48, view.getUint32(40)); v.setUint8(52, type);
        queueMicrotask(() => this.onmessage({ data: reply.buffer }));
      }
    };
    window.runInputCase = async () => {
      for (let n = 0; n < 100 && !ready; n++) await wait();
      if (!ready) throw Error("real terminal did not become ready: " + document.getElementById("status").textContent);
      if (config[1] === "smoke") {
        const validGeometry = size => size && size.columns >= 2 && size.columns <= 512 && size.rows >= 1 && size.rows <= 256;
        if (!validGeometry(readyGeometry)) throw Error("READY geometry outside authored bridge bounds");
        const payload = new TextEncoder().encode("smoke\r\n");
        const output = new Uint8Array(48 + payload.length); output.set(readyHeader);
        const view = new DataView(output.buffer), outputSequence = ++nativeSequence;
        view.setUint8(5, 5); view.setUint32(40, outputSequence); view.setUint32(44, payload.length); output.set(payload, 48);
        window.cloudyTerminal.onmessage({ data: output.buffer });
        progress("smoke-output-submitted");
        for (let n = 0; n < 50 && !acknowledgments.length; n++) await wait();
        // The real terminal.write completion ACK proves parser consumption, not paint.
        const outputAck = acknowledgments.find(ack => ack.sequence === outputSequence && ack.length === payload.length);
        if (!outputAck) throw Error("real terminal.write parser ACK not observed");
        parent.postMessage({ name: config[0], scope: "startup/fit/output parser only; resize requires real-device check",
          readyGeometry, outputAck, errors, regressionMatched: errors.length === 0 }, "*");
        return;
      }
      const textarea = document.querySelector(".xterm-helper-textarea");
      if (!textarea) throw Error("xterm input textarea missing");
      textarea.focus();
      progress("input-schedule-started");
      const key = (keyCode, key, extra = {}) => textarea.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, cancelable: true, keyCode, which: keyCode, key, ...extra }));
      const change = (value, inputType = "insertText", data = null) => {
        key(229, "Unidentified");
        textarea.value = value; textarea.setSelectionRange(value.length, value.length);
        textarea.dispatchEvent(new InputEvent("input", { bubbles: true, inputType, data }));
      };
      const composition = (type, data) => textarea.dispatchEvent(new CompositionEvent(type, { bubbles: true, data }));
      const [name, mode, text] = config;
      if (mode === "rapid" || mode === "paced") {
        let value = "";
        for (const char of text) { value += char; change(value, "insertText", char); if (mode === "paced") await wait(); }
      } else if (mode === "delete" || mode === "delete-many" || mode === "replace") {
        // Seed existing textarea state without claiming the seed was emitted remotely.
        textarea.value = text;
        if (mode === "delete") change(text.slice(0, -1), "deleteContentBackward");
        else if (mode === "delete-many") for (let n = text.length - 1; n >= 0; n--) change(text.slice(0, n), "deleteContentBackward");
        else change("ax", "insertReplacementText", "x");
      } else if (["commit", "cancel", "enter"].includes(mode)) {
        composition("compositionstart", "");
        textarea.value = text; composition("compositionupdate", text);
        await wait();
        if (mode === "cancel") textarea.value = "";
        if (mode === "enter") key(13, "Enter");
        composition("compositionend", mode === "cancel" ? "" : text);
      } else { key(67, "c", { ctrlKey: true }); key(8, "Backspace"); key(13, "Enter"); }
      await wait(); await wait();
      const bytes = chunks.flat();
      parent.postMessage({ name, bytes, chunks, errors }, "*");
    };
  }
  function script(source) { return "<script>" + source.replaceAll("</script", "<\\/script") + "</script>"; }
  for (const config of schedules) {
    const frame = document.createElement("iframe");
    frame.style.cssText = "position:fixed;left:0;top:0;width:1000px;height:700px;border:0";
    activeCase = { name: config[0], phase: "iframe-created" };
    const answer = new Promise((resolve, reject) => {
      const listener = event => {
        if (event.source !== frame.contentWindow) return;
        if (event.data.kind === "progress") { activeCase = event.data; return; }
        window.removeEventListener("message", listener); clearTimeout(timeout); resolve(event.data);
      };
      // The old 1s parent deadline could expire before the child's existing 2s
      // readiness window. Give parsing/layout/startup its own bounded allowance;
      // no event ordering or expected input bytes changes with this deadline.
      let timeout = setTimeout(expire, 5000);
      function expire() { window.removeEventListener("message", listener); reject(Error(config[0] + " timed out")); }
      frame.addEventListener("load", () => { clearTimeout(timeout); timeout = setTimeout(expire, 5000); }, { once: true });
      window.addEventListener("message", listener);
    });
    frame.srcdoc = `<!doctype html><meta charset="utf-8"><style>${bundle.css} ${bundle.authoredCss}</style><div id="feedback"><span id="status"></span><button id="dismiss"></button></div><div id="terminal"></div>` +
      script(`document.body.style.width="800px";document.body.style.height="500px";location.hash="00112233-4455-6677-8899-aabbccddeeff/ffeeddcc-bbaa-9988-7766-554433221100/1";(${childTest.toString()})(${JSON.stringify(config)});`) +
      script(bundle.core) + script(bundle.fit) + script(bundle.bridge) + script("runInputCase().catch(error => parent.postMessage({error:String(error)}, '*'));");
    document.body.appendChild(frame);
    let observed;
    try { observed = await answer; } finally { frame.remove(); }
    if (observed.error) throw Error(observed.error);
    if (config[1] === "smoke") { smoke = observed; continue; }
    const expectedBytes = Array.from(new TextEncoder().encode(config[3]));
    const desiredBytes = Array.from(new TextEncoder().encode(config[4]));
    cases.push({ ...observed, expectedBytes, desiredBytes,
      regressionMatched: observed.errors.length === 0 && JSON.stringify(observed.bytes) === JSON.stringify(expectedBytes),
      inputIntegrity: observed.errors.length === 0 && JSON.stringify(observed.bytes) === JSON.stringify(desiredBytes) });
  }
  activeCase = null;
  document.getElementById("result").textContent = JSON.stringify({ smoke, cases, allInputIntegrity: cases.every(item => item.inputIntegrity) });
})().catch(error => { document.getElementById("result").textContent = JSON.stringify({ error: String(error), smoke, cases, activeCase, allInputIntegrity: false }); });
