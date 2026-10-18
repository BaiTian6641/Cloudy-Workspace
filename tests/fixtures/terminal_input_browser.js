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
    ["android-input-after-fallback-timer", "android-late", "late", "late\r"],
    ["android-consecutive-delayed-commits", "android-late-twice", "中", "中中\r"],
    ["android-pending-change-cancelled-on-blur", "android-late-blur", "a", "a\r"],
    ["android-retained-baseline-before-paste", "android-pending-paste", "中", "中ls -la\r"],
    ["android-committed-change-before-paste", "android-changed-paste", "a", "abls -la\r"],
    ["android-retained-baseline-before-selection", "android-pending-selection", "a", "a\r"],
    ["android-committed-data-preserves-newlines", "android-textarea-replacement", "a\nsecond", "a\nsecond\r"],
    ["android-identical-value-is-a-new-commit", "android-identical-replacement", "same", "same\r"],
    ["android-keypress-and-input-have-one-owner", "android-keypress-owned", "a", "a\r"],
    ["android-new-ime-key-after-held-keypress", "android-after-keypress", "中", "A中\r"],
    ["android-multiline-input-notifications", "beforeinput-multiline", "中文 e\u0301 🙂\nsecond line", "中文 e\u0301 🙂\nsecond line"],
    ["cancelled-composition-before-multiline", "beforeinput-after-cancel", "a\nsecond", "a\nsecond"],
    ["cancelled-beforeinput-never-sends", "beforeinput-cancelled", "accepted", "accepted"],
    ["uncommitted-beforeinput-expires", "beforeinput-uncommitted", "accepted", "accepted"],
    ["unrelated-input-does-not-commit-old-intent", "beforeinput-mismatch", "accepted", "accepted"],
    ["multiline-batch-does-not-own-later-edits", "beforeinput-next", "first\nsecond", "first\nsecondothernext"],
    ["dead-key-commit-preserves-following-enter", "beforeinput-dead-key", "é", "é\r"],
    ["enter-then-ctrl-c", "controls", "", "\r\x03"],
    ["closed-attachment-no-replay", "closed", "a", "a"],
    // Local interaction cases use only synthetic DOM events and the test native endpoint.
    ["paste-safe", "paste", "Cloudy\r\n", "Cloudy\r"],
    ["paste-bracket-off-normalization", "paste-normalize", "a\r\nb\nc\rd", "a\rb\rc\rd"],
    ["paste-bracket-on-sanitizes", "paste-bracket", "a\r\nb\u001b[2J", "\u001b[200~a\rb\u241b[2J\u001b[201~"],
    ["paste-after-close-refused", "paste-closed", "a", ""],
    ["copy-shortcut-no-selection", "copy-shortcut", "", "\u0003"],
    ["right-click-local-menu", "right-click-local", "", ""],
    ["right-click-remote-ownership", "right-click-remote", "", ""],
    ["keyboard-menu-request", "menu-key", "", ""],
    ["find-next-previous-count", "find", "", ""],
    ["find-threshold-honesty", "find-threshold", "", ""],
    ["find-retirement-keeps-new-selection", "find-retire", "", ""],
    ["ctrl-wheel-zoom-bounds", "zoom", "", ""],
    ["output-never-copies", "output-no-copy", "", ""],
    ["browser-clipboard-events-delegated", "clipboard-events", "", ""],
    ["select-all-copy-roundtrip", "select-all-copy", "", ""],
    ["alternate-selection-survives-height-change", "select-all-copy-alt", "", ""],
    ["removed-selection-is-not-copied-after-resize", "resize-selection-refusal", "", ""],
    ["pointer-copy-on-select", "pointer-copy", "", ""],
    ["pointer-copy-programmatic-selection", "pointer-copy-programmatic", "", ""],
    ["pointer-copy-identical-selection", "pointer-copy-identical", "", ""],
    ["paste-transformed-overflow", "paste-overflow", "", ""],
    ["ctrl-v-keeps-terminal-meaning", "ctrl-v", "", "\u0016"],
    ["terminal-control-chords", "ctrl-chords", "", "\u0001\u0018\u001a"],
    ["altgr-not-local-chord", "altgr", "", "\u001b\u0003"],
  ];
  function childTest(config) {
    const wait = () => new Promise(resolve => setTimeout(resolve, 20));
    let nativeSequence = 0, ready = false, readyGeometry = null, readyHeader = null;
    const acknowledgments = [], resizes = [], uiRequests = [];
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
        if (type === 9) {
          const length = view.getUint32(44), action = view.getUint8(48);
          const payload = new Uint8Array(buffer, 49, Math.max(0, length - 1));
          const request = { action, text: Array.from(payload) };
          if (action === 6 && length === 14) {
            const search = new DataView(buffer, 49, 13);
            request.search = { command: search.getUint32(0), index: search.getInt32(4),
              count: search.getInt32(8), flags: search.getUint8(12) };
          }
          uiRequests.push(request);
        }
        if (type !== 2 && type !== 3) return;
        const reply = new Uint8Array(56); reply.set(new Uint8Array(buffer, 0, 48));
        const v = new DataView(reply.buffer); v.setUint8(5, 6); v.setUint32(40, ++nativeSequence); v.setUint32(44, 8);
        v.setUint32(48, view.getUint32(40)); v.setUint8(52, type);
        queueMicrotask(() => this.onmessage({ data: reply.buffer }));
      }
    };
    const sendNative = (type, payload = new Uint8Array(0)) => {
      const bytes = new Uint8Array(48 + payload.length); bytes.set(readyHeader, 0);
      const view = new DataView(bytes.buffer);
      view.setUint8(5, type); view.setUint16(6, 0);
      view.setUint32(40, ++nativeSequence); view.setUint32(44, payload.length);
      bytes.set(payload, 48);
      window.cloudyTerminal.onmessage({ data: bytes.buffer });
      return nativeSequence;
    };
    const sendCommand = (action, text = "") => {
      const body = new TextEncoder().encode(text);
      const payload = new Uint8Array(1 + body.length); payload[0] = action; payload.set(body, 1);
      return sendNative(10, payload);
    };
    const sendOutput = async text => {
      const payload = new TextEncoder().encode(text);
      const sequence = sendNative(5, payload);
      for (let n = 0; n < 250; n++) {
        if (acknowledgments.some(ack => ack.sequence === sequence && ack.length === payload.length)) return sequence;
        await wait();
      }
      throw Error("native output ACK not observed");
    };
    const waitForUi = async (start, predicate) => {
      for (let n = 0; n < 250; n++) {
        for (let i = start; i < uiRequests.length; i++) if (predicate(uiRequests[i])) return uiRequests[i];
        await wait();
      }
      throw Error("expected local UI request not observed: " + JSON.stringify(uiRequests.slice(start)));
    };
    const resizeHeight = async height => {
      const before = resizes.length;
      document.body.style.height = height + "px";
      for (let n = 0; n < 100; n++) {
        if (resizes.length > before) return resizes[resizes.length - 1];
        await wait();
      }
      throw Error("actual viewport height change not observed");
    };
    const keyEvent = (keyCode, keyName, extra = {}) => {
      const textarea = document.querySelector(".xterm-helper-textarea");
      if (!textarea) throw Error("xterm input textarea missing");
      textarea.focus();
      textarea.dispatchEvent(new KeyboardEvent("keydown",
        { bubbles: true, cancelable: true, keyCode, which: keyCode, key: keyName, ...extra }));
    };
    const contextEvent = shiftKey => {
      const element = document.querySelector("#terminal .xterm");
      if (!element) throw Error("xterm element missing");
      element.dispatchEvent(new MouseEvent("contextmenu", { bubbles: true, cancelable: true,
        button: 2, clientX: 120, clientY: 120, shiftKey }));
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
      const keyUp = (keyCode, key, extra = {}) => textarea.dispatchEvent(new KeyboardEvent("keyup",
        { bubbles: true, cancelable: true, keyCode, which: keyCode, key, ...extra }));
      const keyDownRepeat = (keyCode, key, extra = {}) => textarea.dispatchEvent(new KeyboardEvent("keydown",
        { bubbles: true, cancelable: true, keyCode, which: keyCode, key, repeat: true, ...extra }));
      const screenParts = () => {
        const element = document.querySelector("#terminal .xterm");
        const target = document.querySelector("#terminal .xterm-screen");
        if (!element || !target) throw Error("xterm element missing");
        return { element, target, rect: target.getBoundingClientRect() };
      };
      const pointerButton = (kind, button, shiftKey) => {
        const { target, rect } = screenParts();
        const clientX = rect.left + 20, clientY = rect.top + 10;
        const init = { bubbles: true, cancelable: true, button, shiftKey, clientX, clientY,
          buttons: kind === "down" ? (button === 0 ? 1 : 2) : 0,
          detail: 1, pointerId: 1, pointerType: "mouse", isPrimary: true };
        target.dispatchEvent(new PointerEvent(kind === "down" ? "pointerdown" : "pointerup", init));
        target.dispatchEvent(new MouseEvent(kind === "down" ? "mousedown" : "mouseup", init));
      };
      const wheelAt = (ctrlKey, deltaY) => {
        const { target, rect } = screenParts();
        target.dispatchEvent(new WheelEvent("wheel", { bubbles: true, cancelable: true, ctrlKey, deltaY,
          clientX: rect.left + 10, clientY: rect.top + 10 }));
      };
      const dragLine = (startX, endX) => {
        const { element, target, rect } = screenParts();
        const y = rect.top + 2;
        const down = { bubbles: true, cancelable: true, button: 0, buttons: 1, detail: 1, clientX: startX, clientY: y };
        target.dispatchEvent(new PointerEvent("pointerdown", { ...down, pointerId: 1, pointerType: "mouse", isPrimary: true }));
        element.dispatchEvent(new MouseEvent("mousedown", down));
        document.dispatchEvent(new MouseEvent("mousemove", { ...down, clientX: endX }));
        document.dispatchEvent(new PointerEvent("pointerup", { ...down, buttons: 0, clientX: endX,
          pointerId: 1, pointerType: "mouse", isPrimary: true }));
        document.dispatchEvent(new MouseEvent("mouseup", { ...down, buttons: 0, clientX: endX }));
      };
      const dragFirstLine = () => {
        const { rect } = screenParts();
        dragLine(rect.left + 1, rect.right - 2);
      };
      if (surface === "product") {
        const insertOnce = (value, data) => {
          textarea.value = value;
          textarea.setSelectionRange(value.length, value.length);
          textarea.dispatchEvent(new InputEvent("input", { bubbles: true, cancelable: true, inputType: "insertText", data }));
        };
        const [name, mode, text] = config;
        let stages = null, checks = null;
        if (mode === "insert") {
          // Committed insertText only: no keydown is dispatched before the input event.
          let value = "";
          for (const char of text) { value += char; insertOnce(value, char); }
        } else if (mode === "insert-once") {
          insertOnce(text, text);
        } else if (["android-enter", "android-interrupt", "android-late", "android-late-twice"].includes(mode)) {
          for (let index = 0; index < (mode === "android-late-twice" ? 2 : 1); index++) {
            key(229, "Unidentified");
            // Native key and committed-input events need not share one browser task.
            if (mode.startsWith("android-late")) await wait();
            textarea.value = text.repeat(index + 1);
            textarea.setSelectionRange(textarea.value.length, textarea.value.length);
            textarea.dispatchEvent(new InputEvent("input", {
              bubbles: true, composed: true, inputType: "insertText", data: text
            }));
            textarea.dispatchEvent(new KeyboardEvent("keyup", {
              bubbles: true, keyCode: 229, which: 229, key: "Unidentified"
            }));
          }
          // Android may deliver the next key before xterm's deferred 229 fallback runs.
          if (mode !== "android-interrupt") key(13, "Enter");
          else key(67, "c", { ctrlKey: true });
        } else if (mode === "android-late-blur") {
          insertOnce(text, text);
          key(229, "Unidentified");
          await wait();
          textarea.blur();
          textarea.focus();
          key(13, "Enter");
        } else if (mode === "android-pending-paste" || mode === "android-changed-paste") {
          insertOnce(text, text);
          await wait(); // Preserve the native endpoint's queued admission order before a command.
          key(229, "Unidentified");
          if (mode === "android-pending-paste") await wait();
          else {
            textarea.value = text + "b";
            textarea.setSelectionRange(textarea.value.length, textarea.value.length);
          }
          sendCommand(3, "ls -la");
          key(13, "Enter");
        } else if (mode === "android-pending-selection") {
          insertOnce(text, text);
          await wait();
          key(229, "Unidentified");
          await wait();
          await sendOutput("copy only\r\n");
          const start = uiRequests.length;
          dragFirstLine();
          const copy = await waitForUi(start, request => request.action === 1);
          checks = new TextDecoder().decode(new Uint8Array(copy.text)) === "copy only";
          key(13, "Enter");
        } else if (["android-textarea-replacement", "android-identical-replacement",
          "android-keypress-owned", "android-after-keypress"].includes(mode)) {
          if (mode === "android-after-keypress") {
            key(65, "A");
            textarea.dispatchEvent(new KeyboardEvent("keypress", {
              bubbles: true, cancelable: true, key: "A", keyCode: 65, which: 65, charCode: 65
            }));
            textarea.value = "A";
          } else {
            textarea.value = mode === "android-textarea-replacement" ? "\n" :
              mode === "android-identical-replacement" ? text : "";
          }
          textarea.setSelectionRange(0, textarea.value.length);
          key(229, "Unidentified");
          if (mode === "android-keypress-owned") textarea.dispatchEvent(new KeyboardEvent("keypress", {
            bubbles: true, cancelable: true, key: "a", keyCode: 97, which: 97, charCode: 97
          }));
          textarea.value = text;
          textarea.setSelectionRange(text.length, text.length);
          textarea.dispatchEvent(new InputEvent("input", {
            bubbles: true, composed: true, inputType: "insertText", data: text
          }));
          textarea.dispatchEvent(new KeyboardEvent("keyup", {
            bubbles: true, keyCode: 229, which: 229, key: "Unidentified"
          }));
          key(13, "Enter");
        } else if (mode === "beforeinput-dead-key") {
          key(222, "Dead");
          textarea.dispatchEvent(new InputEvent("beforeinput", {
            bubbles: true, cancelable: true, composed: true, inputType: "insertText", data: text
          }));
          insertOnce(text, text);
          key(13, "Enter");
        } else if (mode === "beforeinput-multiline" || mode === "beforeinput-after-cancel") {
          if (mode === "beforeinput-after-cancel") {
            composition("compositionstart", "");
            textarea.value = "uncommitted"; composition("compositionupdate", "uncommitted");
            await wait();
            textarea.value = ""; composition("compositionupdate", "");
            composition("compositionend", "");
          }
          textarea.dispatchEvent(new InputEvent("beforeinput", {
            bubbles: true, cancelable: true, composed: true, inputType: "insertText", data: text
          }));
          textarea.value = text;
          textarea.setSelectionRange(text.length, text.length);
          const parts = text.split("\n");
          for (let index = 0; index < parts.length; index++) {
            if (index) textarea.dispatchEvent(new InputEvent("input", {
              bubbles: true, composed: true, inputType: "insertText", data: null
            }));
            textarea.dispatchEvent(new InputEvent("input", {
              bubbles: true, composed: true, inputType: "insertText", data: parts[index]
            }));
          }
        } else if (["beforeinput-cancelled", "beforeinput-uncommitted", "beforeinput-mismatch"].includes(mode)) {
          if (mode === "beforeinput-cancelled") {
            textarea.addEventListener("beforeinput", event => event.preventDefault(), { once: true });
          }
          textarea.dispatchEvent(new InputEvent("beforeinput", {
            bubbles: true, cancelable: true, composed: true, inputType: "insertText", data: "must-not-send"
          }));
          if (mode === "beforeinput-uncommitted") await wait();
          insertOnce(text, text);
        } else if (mode === "beforeinput-next") {
          textarea.dispatchEvent(new InputEvent("beforeinput", {
            bubbles: true, cancelable: true, composed: true, inputType: "insertText", data: text
          }));
          textarea.value = text;
          textarea.dispatchEvent(new InputEvent("input", {
            bubbles: true, composed: true, inputType: "insertText", data: "first"
          }));
          insertOnce("other", "other");
          textarea.dispatchEvent(new InputEvent("beforeinput", {
            bubbles: true, cancelable: true, composed: true, inputType: "insertText", data: "next"
          }));
          insertOnce("next", "next");
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
        } else if (mode === "paste" || mode === "paste-normalize" || mode === "paste-bracket") {
          if (mode === "paste-bracket") await sendOutput("\u001b[?2004h");
          sendCommand(3, text);
          await wait(); await wait();
          checks = true;
        } else if (mode === "paste-closed") {
          sendNative(8);
          await wait(); await wait();
          const beforeClose = JSON.stringify(chunks.flat());
          sendCommand(3, text);
          keyEvent(67, "v", { ctrlKey: true, shiftKey: true });
          await wait(); await wait();
          checks = JSON.stringify(chunks.flat()) === beforeClose &&
            !uiRequests.some(request => request.action === 2);
        } else if (mode === "copy-shortcut") {
          const start = uiRequests.length;
          const down = new KeyboardEvent("keydown", { bubbles: true, cancelable: true,
            code: "KeyC", key: "C", keyCode: 67, ctrlKey: true, shiftKey: true });
          textarea.dispatchEvent(down);
          // Releasing modifiers while the command key is held must not turn its repeat into
          // terminal text, nor send its eventual keyup to a second recipient.
          const repeat = new KeyboardEvent("keydown", { bubbles: true, cancelable: true,
            code: "KeyC", key: "c", keyCode: 67, repeat: true });
          const press = new KeyboardEvent("keypress", { bubbles: true, cancelable: true,
            code: "KeyC", key: "c", keyCode: 99, charCode: 99 });
          const up = new KeyboardEvent("keyup", { bubbles: true, cancelable: true,
            code: "KeyC", key: "c", keyCode: 67 });
          textarea.dispatchEvent(repeat); textarea.dispatchEvent(press); textarea.dispatchEvent(up);
          const refusal = await waitForUi(start, request => request.action === 8);
          const noCommandLeak = chunks.flat().length === 0;
          keyEvent(67, "c", { ctrlKey: true });
          await wait(); await wait();
          checks = down.defaultPrevented && repeat.defaultPrevented && press.defaultPrevented &&
            up.defaultPrevented && noCommandLeak && refusal.text[0] === 1 &&
            uiRequests.slice(start).filter(request => request.action === 8).length === 1 &&
            chunks.flat().length === 1 && chunks.flat()[0] === 3;
        } else if (mode === "right-click-local") {
          const start = uiRequests.length, chunkStart = chunks.length;
          pointerButton("down", 2, false);
          pointerButton("up", 2, false);
          await wait();
          const noPointerAction = !uiRequests.slice(start).some(request => request.action === 3 || request.action === 4);
          contextEvent(false);
          await waitForUi(start, request => request.action === 3);
          await wait(); await wait();
          checks = noPointerAction && chunks.length === chunkStart &&
            uiRequests.slice(start).filter(request => request.action === 3).length === 1 &&
            !uiRequests.slice(start).some(request => request.action === 4);
        } else if (mode === "right-click-remote") {
          await sendOutput("\u001b[?1000h");
          await wait();
          const start = uiRequests.length, chunkStart = chunks.length;
          // Actual secondary pointer events must generate the remote report, not just contextmenu.
          pointerButton("down", 2, false);
          pointerButton("up", 2, false);
          await wait(); await wait();
          const remoteBytes = chunks.slice(chunkStart).flat();
          const report = new TextDecoder().decode(new Uint8Array(remoteBytes));
          const remoteOwned = remoteBytes.length > 0 && remoteBytes.length % 6 === 0 &&
            report.charCodeAt(3) === 34 &&
            Array.from({ length: remoteBytes.length / 6 }, (_, index) => report.slice(index * 6, index * 6 + 6))
              .every(part => part.startsWith("\u001b[M"));
          // The browser's follow-up contextmenu is still remote-owned.
          contextEvent(false);
          await wait(); await wait();
          const noLocalFromRemote = !uiRequests.slice(start).some(request => request.action === 3 || request.action === 4);
          // Shift is the deliberate local override: no report, then MENU4, never RIGHT_CLICK3.
          const shiftChunkStart = chunks.length;
          pointerButton("down", 2, true);
          pointerButton("up", 2, true);
          await wait(); await wait();
          const noRemoteShift = chunks.length === shiftChunkStart;
          contextEvent(true);
          const menu = await waitForUi(start, request => request.action === 4);
          // Ctrl+wheel is local and must not add remote wheel bytes; a plain wheel stays remote-owned.
          const wheelChunkStart = chunks.length;
          wheelAt(true, -100);
          await wait(); await wait();
          const noRemoteZoom = chunks.length === wheelChunkStart;
          wheelAt(false, -100);
          await wait(); await wait();
          const remoteWheel = chunks.length > wheelChunkStart;
          checks = remoteOwned && noLocalFromRemote && noRemoteShift && noRemoteZoom && remoteWheel &&
            menu.text.length === 0 &&
            uiRequests.slice(start).filter(request => request.action === 4).length === 1 &&
            !uiRequests.slice(start).some(request => request.action === 1 || request.action === 2 ||
              request.action === 3);
        } else if (mode === "menu-key") {
          const start = uiRequests.length;
          keyEvent(93, "ContextMenu");
          await waitForUi(start, request => request.action === 4);
          checks = chunks.flat().length === 0;
        } else if (mode === "find") {
          await sendOutput("alpha beta alpha\r\n");
          const results = [], commands = [];
          let start = uiRequests.length;
          commands.push(sendCommand(5, "alpha"));
          results.push((await waitForUi(start, request => request.action === 6)).search);
          start = uiRequests.length;
          commands.push(sendCommand(5, "alpha"));
          results.push((await waitForUi(start, request => request.action === 6)).search);
          start = uiRequests.length;
          commands.push(sendCommand(6, "alpha"));
          results.push((await waitForUi(start, request => request.action === 6)).search);
          start = uiRequests.length;
          await sendOutput("alpha\r\n");
          const refreshed = (await waitForUi(start, request => request.action === 6 &&
            request.search.command === commands[2] && request.search.count === 3)).search;
          start = uiRequests.length;
          commands.push(sendCommand(5, "missing"));
          const missing = (await waitForUi(start, request => request.action === 6)).search;
          checks = results.every((result, index) => result.command === commands[index]) &&
            missing.command === commands[3] && refreshed.count === 3 &&
            results[0].count === 2 && results[0].index === 0 && (results[0].flags & 1) === 1 &&
            results[1].count === 2 && results[1].index === 1 && (results[1].flags & 1) === 1 &&
            results[2].count === 2 && results[2].index === 0 && (results[2].flags & 1) === 1 &&
            missing.count === 0 && missing.index === -1 && (missing.flags & 1) === 0 &&
            !uiRequests.some(request => request.action === 1);
        } else if (mode === "find-threshold") {
          await sendOutput("x\r\n".repeat(1001));
          const start = uiRequests.length;
          const command = sendCommand(5, "x");
          const result = (await waitForUi(start, request => request.action === 6)).search;
          checks = result.command === command && result.count === 1000 && result.index === 0 &&
            (result.flags & 1) === 1 && (result.flags & 2) === 2;
        } else if (mode === "find-retire") {
          await sendOutput("alpha\r\n");
          const searchStart = uiRequests.length;
          sendCommand(5, "alpha");
          await waitForUi(searchStart, request => request.action === 6);
          // A parsed write arms the real addon's 200ms refresh before Find is closed.
          await sendOutput("beta\r\n");
          sendCommand(7);
          sendCommand(1);
          const baselineStart = uiRequests.length;
          sendCommand(2);
          const baseline = await waitForUi(baselineStart, request => request.action === 1);
          sendCommand(7); // A retired/duplicate clear does not own the new selection.
          await new Promise(resolve => setTimeout(resolve, 300));
          const start = uiRequests.length;
          sendCommand(2);
          const copy = await waitForUi(start, request => request.action === 1 || request.action === 8);
          const reopenStart = uiRequests.length;
          const reopenCommand = sendCommand(5, "beta");
          const reopened = (await waitForUi(reopenStart, request => request.action === 6)).search;
          checks = copy.action === 1 &&
            JSON.stringify(copy.text) === JSON.stringify(baseline.text) &&
            new TextDecoder().decode(new Uint8Array(copy.text)).startsWith("alpha\nbeta\n") &&
            reopened.command === reopenCommand && reopened.count === 1 && reopened.index === 0 &&
            chunks.flat().length === 0;
        } else if (mode === "zoom") {
          const fontPx = () => parseFloat(getComputedStyle(document.querySelector("#terminal .xterm-rows")).fontSize);
          const before = resizes.length;
          const initial = fontPx();
          for (let n = 0; n < 40; n++) wheelAt(true, -100);
          await wait(); await wait();
          const maximum = fontPx(), maxResizes = resizes.length;
          for (let n = 0; n < 10; n++) wheelAt(true, -100);
          await wait(); await wait();
          const stillMaximum = fontPx(), upCapResizes = resizes.length;
          for (let n = 0; n < 40; n++) wheelAt(true, 100);
          await wait(); await wait();
          const minimum = fontPx(), minResizes = resizes.length;
          for (let n = 0; n < 10; n++) wheelAt(true, 100);
          await wait(); await wait();
          const stillMinimum = fontPx(), downCapResizes = resizes.length;
          const valid = size => size && size.columns >= 2 && size.columns <= 512 && size.rows >= 1 && size.rows <= 256;
          checks = initial >= 12 && initial <= 32 && maximum === 32 && stillMaximum === 32 &&
            minimum === 12 && stillMinimum === 12 &&
            maxResizes > before && upCapResizes === maxResizes &&
            minResizes > maxResizes && downCapResizes === minResizes &&
            resizes.every(valid) &&
            !uiRequests.some(request => request.action === 1 || request.action === 2) &&
            chunks.flat().length === 0;
        } else if (mode === "output-no-copy") {
          await sendOutput("secret output\r\n");
          await wait(); await wait();
          checks = !uiRequests.some(request => request.action === 1) && chunks.flat().length === 0;
        } else if (mode === "clipboard-events") {
          const textarea = document.querySelector(".xterm-helper-textarea");
          const copyStart = uiRequests.length;
          textarea.dispatchEvent(new ClipboardEvent("copy", { bubbles: true, cancelable: true }));
          const refusal = await waitForUi(copyStart, request => request.action === 8);
          // A cut cannot edit terminal content and is not a second copy route.
          const cutStart = uiRequests.length;
          const cut = new ClipboardEvent("cut", { bubbles: true, cancelable: true });
          textarea.dispatchEvent(cut);
          await wait(); await wait();
          const cutBlocked = cut.defaultPrevented &&
            !uiRequests.slice(cutStart).some(request => request.action === 1);
          const pasteStart = uiRequests.length;
          const paste = new ClipboardEvent("paste", { bubbles: true, cancelable: true });
          textarea.dispatchEvent(paste);
          await waitForUi(pasteStart, request => request.action === 2);
          checks = refusal.text[0] === 1 && cutBlocked && paste.defaultPrevented &&
            chunks.flat().length === 0;
        } else if (mode === "select-all-copy" || mode === "select-all-copy-alt") {
          if (mode === "select-all-copy-alt") await sendOutput("\u001b[?1049h");
          await sendOutput("copy me\r\n");
          sendCommand(1);
          const baselineStart = uiRequests.length;
          sendCommand(2);
          const baseline = await waitForUi(baselineStart, request => request.action === 1);
          const initialRows = resizes.length ? resizes[resizes.length - 1].rows : readyGeometry.rows;
          const grown = await resizeHeight(650);
          const restored = await resizeHeight(500);
          if (grown.rows <= initialRows || restored.rows !== initialRows) throw Error("unexpected height geometry");
          const start = uiRequests.length;
          sendCommand(2);
          const copy = await waitForUi(start, request => request.action === 1 || request.action === 8);
          const selected = new TextDecoder().decode(new Uint8Array(copy.text));
          await wait(); await wait();
          checks = copy.action === 1 && JSON.stringify(copy.text) === JSON.stringify(baseline.text) &&
            /^copy me\n*$/.test(selected) && chunks.flat().length === 0 &&
            uiRequests.slice(start).filter(request => request.action === 1).length === 1;
        } else if (mode === "resize-selection-refusal") {
          await sendOutput("copy range\r\n");
          sendCommand(1);
          const baselineStart = uiRequests.length;
          sendCommand(2);
          const baseline = await waitForUi(baselineStart, request => request.action === 1);
          const resizeStart = uiRequests.length;
          await resizeHeight(120); // Remove selected blank rows; exact restoration is impossible.
          const start = uiRequests.length;
          sendCommand(2);
          const result = await waitForUi(start, request => request.action === 1 || request.action === 8);
          checks = new TextDecoder().decode(new Uint8Array(baseline.text)).startsWith("copy range\n") &&
            result.action === 8 && result.text[0] === 1 &&
            document.getElementById("feedback").getAttribute("data-warning") === "true" &&
            document.getElementById("feedback").getBoundingClientRect().height > 0 &&
            !uiRequests.slice(resizeStart).some(request => request.action === 1) && chunks.length === 0;
        } else if (mode === "pointer-copy") {
          await sendOutput("alpha beta gamma\r\n");
          const start = uiRequests.length, chunkStart = chunks.length;
          dragFirstLine();
          const copy = await waitForUi(start, request => request.action === 1);
          const selected = new TextDecoder().decode(new Uint8Array(copy.text));
          await wait(); await wait();
          const afterSelection = uiRequests.slice(start).filter(request => request.action === 1).length;
          await resizeHeight(650);
          await resizeHeight(500);
          const afterResize = uiRequests.slice(start).filter(request => request.action === 1).length;
          const resizedStart = uiRequests.length;
          sendCommand(2);
          const resizedCopy = await waitForUi(resizedStart, request => request.action === 1 || request.action === 8);
          // A replaced selection explicitly cancelled by focus loss must not copy.
          const { rect } = screenParts();
          const columns = resizes.length ? resizes[resizes.length - 1].columns : readyGeometry.columns;
          const cellWidth = rect.width / columns;
          dragLine(rect.left + cellWidth + 1, rect.right - 2);
          window.dispatchEvent(new Event("blur"));
          await wait(); await wait();
          const afterBlur = uiRequests.slice(start).filter(request => request.action === 1).length;
          // Attachment loss retires any still-pending pointer copy.
          dragLine(rect.left + cellWidth * 2 + 1, rect.right - 2);
          sendNative(8);
          await wait(); await wait();
          const afterClose = uiRequests.slice(start).filter(request => request.action === 1).length;
          checks = selected === "alpha beta gamma" && afterSelection === 1 && afterResize === 1 &&
            resizedCopy.action === 1 && JSON.stringify(resizedCopy.text) === JSON.stringify(copy.text) &&
            afterBlur === 2 && afterClose === 2 && chunks.length === chunkStart;
        } else if (mode === "pointer-copy-programmatic") {
          await sendOutput("alpha beta alpha\r\n");
          const start = uiRequests.length, chunkStart = chunks.length;
          dragFirstLine();
          // Search selects a match synchronously in the same turn; the pending pointer copy
          // must see the programmatic change and copy nothing.
          const command = sendCommand(5, "alpha");
          const result = (await waitForUi(start, request => request.action === 6)).search;
          await wait(); await wait();
          checks = result.command === command && result.count === 2 && result.index === 0 &&
            !uiRequests.slice(start).some(request => request.action === 1) &&
            chunks.length === chunkStart;
        } else if (mode === "pointer-copy-identical") {
          await sendOutput("alpha beta gamma\r\n");
          const start = uiRequests.length, chunkStart = chunks.length;
          dragFirstLine();
          const first = await waitForUi(start, request => request.action === 1);
          // Another app can replace the shared clipboard between two equal selections.
          const secondStart = uiRequests.length;
          dragFirstLine();
          const second = await waitForUi(secondStart, request => request.action === 1);
          const clickStart = uiRequests.length;
          pointerButton("down", 0, false);
          pointerButton("up", 0, false);
          await wait(); await wait();
          checks = JSON.stringify(first.text) === JSON.stringify(second.text) &&
            new TextDecoder().decode(new Uint8Array(second.text)) === "alpha beta gamma" &&
            uiRequests.slice(start).filter(request => request.action === 1).length === 2 &&
            !uiRequests.slice(clickStart).some(request => request.action === 1) &&
            chunks.length === chunkStart;
        } else if (mode === "paste-overflow") {
          await sendOutput("\u001b[?2004h");
          await wait();
          const start = uiRequests.length, chunkStart = chunks.length;
          // 22,000 ESC bytes pass the 60 KiB source bound but expand past 64 KiB when bracketed.
          sendCommand(3, "\u001b".repeat(22000));
          await wait(); await wait();
          checks = chunks.length === chunkStart &&
            document.getElementById("feedback").dataset.warning === "true" &&
            !document.getElementById("feedback").hidden &&
            document.getElementById("feedback").getBoundingClientRect().height > 0 &&
            !uiRequests.slice(start).some(request => request.action === 1 || request.action === 2);
        } else if (mode === "ctrl-v") {
          const start = uiRequests.length;
          keyEvent(86, "v", { ctrlKey: true, shiftKey: true });
          keyUp(86, "v", { ctrlKey: true, shiftKey: true });
          keyDownRepeat(86, "v", { ctrlKey: true, shiftKey: true });
          await waitForUi(start, request => request.action === 2);
          await wait();
          const pastes = uiRequests.slice(start).filter(request => request.action === 2).length;
          keyEvent(86, "v", { ctrlKey: true });
          await wait(); await wait();
          checks = pastes === 1 && !uiRequests.slice(start).some(request => request.action === 1) &&
            chunks.flat().length === 1 && chunks.flat()[0] === 22;
        } else if (mode === "ctrl-chords") {
          const start = uiRequests.length;
          keyEvent(65, "a", { ctrlKey: true });
          keyEvent(88, "x", { ctrlKey: true });
          keyEvent(90, "z", { ctrlKey: true });
          await wait(); await wait();
          checks = !uiRequests.slice(start).some(request => request.action === 1 || request.action === 2) &&
            chunks.flat().length === 3;
        } else if (mode === "altgr") {
          const start = uiRequests.length;
          // AltGr is a third-level modifier, never the local Ctrl+Shift chord.
          const modifiers = { ctrlKey: true, shiftKey: true, altKey: true, modifierAltGraph: true };
          keyEvent(67, "c", modifiers);
          keyUp(67, "c", modifiers);
          await wait(); await wait();
          checks = !uiRequests.slice(start).some(request => request.action === 1 || request.action === 2) &&
            chunks.flat().length === 2 && chunks.flat()[0] === 27 && chunks.flat()[1] === 3;
        } else throw Error("unknown product case " + mode);
        await wait(); await wait();
        parent.postMessage({ name, bytes: chunks.flat(), chunks, errors, stages, checks,
          uiRequests, resizes }, "*");
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
    // Remote ownership emits geometry-dependent mouse reports; the child verifies their exact
    // structure (and that no local action exists) instead of pinning device pixels here.
    if (config[1] === "right-click-remote") return observed.checks === true;
    if (JSON.stringify(observed.bytes) !== JSON.stringify(expectedBytes)) return false;
    if (config[1] === "cancel-then-commit") {
      return Array.isArray(stages.cancelBytes) && stages.cancelBytes.length === 0 &&
        JSON.stringify(stages.commitBytes) === JSON.stringify(expectedBytes);
    }
    if (config[1] === "closed") {
      return JSON.stringify(stages.beforeCloseBytes) === JSON.stringify(expectedBytes) &&
        JSON.stringify(stages.afterCloseBytes) === JSON.stringify(stages.beforeCloseBytes);
    }
    if (["paste", "paste-normalize", "paste-bracket", "paste-closed", "copy-shortcut", "right-click-local",
      "menu-key", "find", "find-threshold", "find-retire", "zoom", "output-no-copy",
      "clipboard-events", "select-all-copy", "select-all-copy-alt", "resize-selection-refusal",
      "pointer-copy", "pointer-copy-programmatic", "pointer-copy-identical", "android-pending-selection",
      "paste-overflow", "ctrl-v", "ctrl-chords", "altgr"].includes(config[1])) {
      return observed.checks === true;
    }
    return ["insert", "insert-once", "controls", "android-enter", "android-interrupt",
      "android-late", "android-late-twice", "android-late-blur",
      "android-pending-paste", "android-changed-paste", "android-textarea-replacement",
      "android-identical-replacement", "android-keypress-owned", "android-after-keypress",
      "beforeinput-multiline", "beforeinput-after-cancel", "beforeinput-cancelled", "beforeinput-next",
      "beforeinput-uncommitted", "beforeinput-mismatch", "beforeinput-dead-key"].includes(config[1]);
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
    frame.srcdoc = `<!doctype html><meta charset="utf-8"><style>${bundle.css} ${bundle.authoredCss}</style><div id="feedback"${surface === "product" ? " hidden" : ""}><span id="status"></span><button id="dismiss"></button></div><div id="terminal"></div>` +
      script(`const surface=${JSON.stringify(surface)};document.body.style.width="800px";document.body.style.height="500px";location.hash=${JSON.stringify(bootHash)};(${childTest.toString()})(${JSON.stringify(config)});`) +
      script(bundle.core) + (bundle.search ? script(bundle.search) : "") + script(bundle.fit) + script(bundle.bridge) +
      script("runInputCase().catch(error => parent.postMessage({error:String(error)}, '*'));");
    document.body.appendChild(frame);
    let observed;
    try { observed = await answer; } finally { frame.remove(); }
    if (observed.error) throw Error(observed.error);
    if (config[1] === "smoke") { smoke = observed; continue; }
    const expectedBytes = Array.from(new TextEncoder().encode(config[3] === undefined ? "" : config[3]));
    const desiredBytes = Array.from(new TextEncoder().encode(
      config[4] === undefined ? (config[3] === undefined ? "" : config[3]) : config[4]));
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
