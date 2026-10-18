/* Each fresh iframe runs the real bundles and unmodified authored bridge. The only
 * substitute is the native endpoint: it captures INPUT payloads and admits credits.
 * Events are deliberately synthetic; no UA override or private xterm API is used.
 * Probe keeps its historical schedules. Product boots the four-part /0 hash and checks
 * only committed insertText, preedit cancellation, control bytes and close refusal.
 */
"use strict";
const cases = [];
let activeCase = null;
let smoke = null;
(async () => {
  const schedules = surface === "probe" ? [
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
  ] : [
    // No keydown is synthesized for insertText: the committed textarea event is the only input.
    ["insert-text-no-keydown", "insert", "ab", "ab"],
    ["unicode-combining-emoji", "insert-once", "e\u0301\u6f22👍", "e\u0301\u6f22👍"],
    ["canceled-preedit-then-commit", "cancel-then-commit", "中", "中"],
    ["android-commit-then-enter", "android-enter", "ab", "ab\r"],
    ["android-commit-then-interrupt", "android-interrupt", "中", "中\x03"],
    ["enter-then-ctrl-c", "controls", "", "\r\x03"],
    ["closed-attachment-no-replay", "closed", "a", "a"],
  ];
  function childTest(config) {
    const wait = () => new Promise(resolve => setTimeout(resolve, 20));
    let nativeSequence = 0, ready = false, readyGeometry = null, readyHeader = null;
    const acknowledgments = [], resizes = [];
    const chunks = [], errors = [];
    const progress = phase => parent.postMessage({ kind: "progress", name: config[0], phase, chunks: chunks.map(chunk => [...chunk]), errors: [...errors], readyGeometry, acknowledgments: [...acknowledgments], resizes: [...resizes] }, "*");
    let firstAnimationFrame = false;
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
      // Virtual timers may advance before an iframe's first compositor frame. Start the
      // readiness allowance only after the actual frame, without changing input schedules.
      await new Promise(resolve => requestAnimationFrame(() => { firstAnimationFrame = true; resolve(); }));
      for (let n = 0; n < 100 && !ready; n++) await wait();
      if (!ready) throw Error("real terminal did not become ready: " + JSON.stringify({
        status: document.getElementById("status").textContent,
        firstAnimationFrame, visibility: document.visibilityState, fonts: document.fonts.status,
        width: document.getElementById("terminal").clientWidth, height: document.getElementById("terminal").clientHeight
      }));
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
      if (surface === "product") {
        const insertOnce = (value, data) => {
          textarea.value = value;
          textarea.setSelectionRange(value.length, value.length);
          textarea.dispatchEvent(new InputEvent("input", { bubbles: true, cancelable: true, inputType: "insertText", data }));
        };
        const [name, mode, text] = config;
        let stages = null;
        if (mode === "insert") {
          // Committed insertText only: no keydown is dispatched before the input event.
          let value = "";
          for (const char of text) { value += char; insertOnce(value, char); }
        } else if (mode === "insert-once") {
          insertOnce(text, text);
        } else if (mode === "android-enter" || mode === "android-interrupt") {
          key(229, "Unidentified");
          textarea.value = text;
          textarea.setSelectionRange(text.length, text.length);
          textarea.dispatchEvent(new InputEvent("input", {
            bubbles: true, composed: true, inputType: "insertText", data: text
          }));
          textarea.dispatchEvent(new KeyboardEvent("keyup", {
            bubbles: true, keyCode: 229, which: 229, key: "Unidentified"
          }));
          // Android may deliver the next key before xterm's deferred 229 fallback runs.
          if (mode === "android-enter") key(13, "Enter");
          else key(67, "c", { ctrlKey: true });
        } else if (mode === "cancel-then-commit") {
          composition("compositionstart", "");
          textarea.value = text; composition("compositionupdate", text);
          await wait();
          textarea.value = "";
          composition("compositionend", "");
          await wait();
          const cancelBytes = chunks.flat();
          composition("compositionstart", "");
          textarea.value = text; composition("compositionupdate", text);
          await wait();
          composition("compositionend", text);
          await wait();
          stages = { cancelBytes, commitBytes: chunks.flat() };
        } else if (mode === "controls") {
          key(13, "Enter");
          key(67, "c", { ctrlKey: true });
        } else if (mode === "closed") {
          insertOnce(text, text);
          await wait();
          const beforeCloseBytes = chunks.flat();
          const close = new Uint8Array(readyHeader);
          const closeView = new DataView(close.buffer);
          closeView.setUint8(5, 8); closeView.setUint16(6, 0);
          closeView.setUint32(40, ++nativeSequence); closeView.setUint32(44, 0);
          window.cloudyTerminal.onmessage({ data: close.buffer });
          await wait(); await wait();
          // Replay and new input after CLOSED must neither resend nor queue bytes.
          insertOnce(text + "b", "b");
          key(13, "Enter");
          await wait(); await wait();
          stages = { beforeCloseBytes, afterCloseBytes: chunks.flat() };
        } else throw Error("unknown product case " + mode);
        await wait(); await wait();
        parent.postMessage({ name, bytes: chunks.flat(), chunks, errors, stages }, "*");
        return;
      }
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
  function productMatched(observed, config, expectedBytes) {
    const stages = observed.stages || {};
    if (observed.errors.length !== 0) return false;
    if (JSON.stringify(observed.bytes) !== JSON.stringify(expectedBytes)) return false;
    if (config[1] === "cancel-then-commit") {
      return Array.isArray(stages.cancelBytes) && stages.cancelBytes.length === 0 &&
        JSON.stringify(stages.commitBytes) === JSON.stringify(expectedBytes);
    }
    if (config[1] === "closed") {
      return JSON.stringify(stages.beforeCloseBytes) === JSON.stringify(expectedBytes) &&
        JSON.stringify(stages.afterCloseBytes) === JSON.stringify(stages.beforeCloseBytes);
    }
    return ["insert", "insert-once", "controls", "android-enter", "android-interrupt"].includes(config[1]);
  }
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
      script(`const surface=${JSON.stringify(surface)};document.body.style.width="800px";document.body.style.height="500px";location.hash=${JSON.stringify(bootHash)};(${childTest.toString()})(${JSON.stringify(config)});`) +
      script(bundle.core) + script(bundle.fit) + script(bundle.bridge) + script("runInputCase().catch(error => parent.postMessage({error:String(error)}, '*'));");
    document.body.appendChild(frame);
    let observed;
    try { observed = await answer; } finally { frame.remove(); }
    if (observed.error) throw Error(observed.error);
    if (config[1] === "smoke") { smoke = observed; continue; }
    const expectedBytes = Array.from(new TextEncoder().encode(config[3]));
    const desiredBytes = Array.from(new TextEncoder().encode(config[4] === undefined ? config[3] : config[4]));
    if (surface === "product") {
      const matched = productMatched(observed, config, expectedBytes);
      cases.push({ ...observed, expectedBytes, desiredBytes, regressionMatched: matched, inputIntegrity: matched });
    } else {
      cases.push({ ...observed, expectedBytes, desiredBytes,
        regressionMatched: observed.errors.length === 0 && JSON.stringify(observed.bytes) === JSON.stringify(expectedBytes),
        inputIntegrity: observed.errors.length === 0 && JSON.stringify(observed.bytes) === JSON.stringify(desiredBytes) });
    }
  }
  activeCase = null;
  const result = surface === "product"
    ? { surface, bootHash, cases, allInputIntegrity: cases.length > 0 && cases.every(item => item.regressionMatched) }
    : { smoke, cases, allInputIntegrity: cases.every(item => item.inputIntegrity) };
  document.getElementById("result").textContent = JSON.stringify(result);
})().catch(error => { document.getElementById("result").textContent = JSON.stringify({ ...(surface === "product" ? { surface, bootHash } : {}), error: String(error), smoke, cases, activeCase, allInputIntegrity: false }); });
