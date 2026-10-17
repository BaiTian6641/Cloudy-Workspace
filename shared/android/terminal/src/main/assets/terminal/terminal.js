/* Authored terminal-only bridge. SSH configuration, trust and credentials never enter this page. */
"use strict";
(function (root) {
  const HEADER = 48, MAGIC = 0x43575452, MAX_INPUT = 65536, MAX_OUTPUT = 16384;
  const TYPE = Object.freeze({ READY: 1, INPUT: 2, RESIZE: 3, ACK: 4, OUTPUT: 5, ADMISSION: 6, ERROR: 7, CLOSED: 8 });
  const ADMISSION = Object.freeze({ ACCEPTED: 0, FULL: 1, STALE: 2, STOPPED: 3 });
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
  function identityBytes(identity) {
    if (!UUID.test(identity.sessionId) || !UUID.test(identity.attachmentId)) throw Error("identity");
    return Uint8Array.from((identity.sessionId + identity.attachmentId).replaceAll("-", "").match(/../g), s => parseInt(s, 16));
  }
  function validate(type, sequence, payload, flags) {
    if (!Number.isInteger(sequence) || sequence < 1 || sequence > 2147483647) throw Error("sequence");
    if (flags !== 0 && !(type === TYPE.OUTPUT && flags === 1)) throw Error("flags");
    const n = payload.byteLength;
    const valid = type === TYPE.READY || type === TYPE.RESIZE ? n === 4 :
      type === TYPE.INPUT ? n > 0 && n <= MAX_INPUT :
      type === TYPE.OUTPUT ? n > 0 && n <= MAX_OUTPUT :
      type === TYPE.ACK || type === TYPE.ADMISSION ? n === 8 :
      type === TYPE.ERROR ? n === 1 && payload[0] === 1 : type === TYPE.CLOSED ? n === 0 : false;
    if (!valid) throw Error("length-or-type");
  }
  function encode(identity, type, sequence, payload, flags = 0) {
    if (!(payload instanceof Uint8Array)) throw Error("payload-type");
    validate(type, sequence, payload, flags);
    const bytes = new Uint8Array(HEADER + payload.byteLength), v = new DataView(bytes.buffer);
    v.setUint32(0, MAGIC); v.setUint8(4, 1); v.setUint8(5, type); v.setUint16(6, flags);
    bytes.set(identityBytes(identity), 8); v.setUint32(40, sequence); v.setUint32(44, payload.byteLength);
    bytes.set(payload, HEADER);
    return bytes.buffer;
  }
  function decode(identity, buffer) {
    if (!(buffer instanceof ArrayBuffer) || buffer.byteLength < HEADER || buffer.byteLength > HEADER + MAX_INPUT) throw Error("envelope");
    const v = new DataView(buffer), bytes = new Uint8Array(buffer), ids = identityBytes(identity);
    if (v.getUint32(0) !== MAGIC || v.getUint8(4) !== 1) throw Error("version");
    if (!ids.every((b, i) => b === bytes[8 + i])) throw Error("identity");
    if (v.getUint32(44) !== buffer.byteLength - HEADER) throw Error("payload-length");
    const result = { type: v.getUint8(5), sequence: v.getUint32(40), flags: v.getUint16(6), payload: bytes.subarray(HEADER) };
    validate(result.type, result.sequence, result.payload, result.flags);
    return result;
  }
  function encodeText(text) {
    if (typeof text !== "string" || text.length > MAX_INPUT) return null;
    const bytes = new TextEncoder().encode(text);
    return bytes.byteLength > 0 && bytes.byteLength <= MAX_INPUT ? bytes : null;
  }
  function encodeBinary(text) {
    if (typeof text !== "string" || !text.length || text.length > MAX_INPUT) return null;
    const bytes = new Uint8Array(text.length);
    for (let i = 0; i < text.length; i++) {
      const value = text.charCodeAt(i);
      if (value > 255) return null;
      bytes[i] = value;
    }
    return bytes;
  }
  function sizePayload(columns, rows) {
    if (!Number.isInteger(columns) || columns < 2 || columns > 512 || !Number.isInteger(rows) || rows < 1 || rows > 256) throw Error("dimensions");
    const b = new Uint8Array(4), v = new DataView(b.buffer);
    v.setUint16(0, columns); v.setUint16(2, rows);
    return b;
  }
  class InputLedger {
    constructor() { this.pending = new Map(); this.bytes = 0; }
    reserve(sequence, length) {
      if (!Number.isInteger(length) || length < 1 || length > MAX_INPUT || this.pending.has(sequence)) throw Error("input-reservation");
      if (this.bytes + length > MAX_INPUT || this.pending.size >= 128) return false;
      this.pending.set(sequence, length); this.bytes += length;
      return true;
    }
    settle(sequence) {
      if (!this.pending.has(sequence)) throw Error("input-admission-order");
      this.bytes -= this.pending.get(sequence); this.pending.delete(sequence);
    }
    clear() { this.pending.clear(); this.bytes = 0; }
  }
  // Export pure byte/credit helpers for Node tests; the production path below uses the same functions.
  const api = { TYPE, ADMISSION, encode, decode, encodeText, encodeBinary, sizePayload, InputLedger };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (!root.document) return;

  const status = root.document.getElementById("status"), feedback = root.document.getElementById("feedback");
  const dismiss = root.document.getElementById("dismiss");
  let terminal, stopped = false, warning = false, ready = false, tx = 0, rx = 0;
  let outputBytes = 0, outputFrames = 0, resizeRequest = null, desiredSize = null, resizeScheduled = false;
  const inputs = new InputLedger();
  let identity;
  function notice(text, important = false) {
    if (warning && !important) return;
    warning = important; status.textContent = text; feedback.dataset.warning = String(important); dismiss.hidden = !important;
  }
  dismiss.addEventListener("click", () => { warning = false; notice(stopped ? "Terminal attachment stopped. Reconnect using native controls." : "Local terminal ready. Connection and trust are controlled above."); });
  function next() { if (tx >= 2147483647) throw Error("sequence-exhausted"); return ++tx; }
  function post(type, sequence, payload) { root.cloudyTerminal.postMessage(encode(identity, type, sequence, payload)); }
  function fail() {
    if (stopped) return;
    stopped = true; inputs.clear();
    if (terminal) terminal.options.disableStdin = true;
    notice("Terminal renderer failed. This attachment cannot accept more input; reconnect using native controls.", true);
    try { if (identity && root.cloudyTerminal) post(TYPE.ERROR, next(), new Uint8Array([1])); } catch (_) { /* Native readiness/ACK deadline independently aborts. */ }
  }
  function submit(bytes) {
    if (stopped || !ready) { notice("Input rejected: terminal attachment is not ready.", true); return; }
    if (!bytes) { notice("Input rejected: the complete action exceeds 64 KiB or is not valid byte input. No part was sent.", true); return; }
    try {
      const sequence = tx + 1;
      if (!inputs.reserve(sequence, bytes.byteLength)) { notice("Input rejected: the local input queue is full. No part of this action was sent.", true); return; }
      post(TYPE.INPUT, next(), bytes);
    } catch (_) { fail(); }
  }
  function sendResize() {
    resizeScheduled = false;
    if (stopped || !ready || resizeRequest !== null || desiredSize === null) return;
    const size = desiredSize; desiredSize = null;
    try { resizeRequest = next(); post(TYPE.RESIZE, resizeRequest, sizePayload(size.columns, size.rows)); } catch (_) { fail(); }
  }
  function scheduleResize(size) {
    desiredSize = size;
    if (!resizeScheduled) { resizeScheduled = true; root.requestAnimationFrame(sendResize); }
  }
  function receive(event) {
    if (stopped) return;
    try {
      const frame = decode(identity, event.data);
      if (frame.sequence !== rx + 1) throw Error("native-order");
      rx = frame.sequence;
      if (frame.type === TYPE.OUTPUT) {
        outputBytes += frame.payload.byteLength; outputFrames++;
        if (outputBytes > 262144 || outputFrames > 64) throw Error("output-credit");
        // Uint8Array preserves split UTF-8 across writes. ACK means parser completion, not pixels.
        terminal.write(frame.payload, () => {
          if (stopped) return;
          try {
            outputBytes -= frame.payload.byteLength; outputFrames--;
            const ack = new Uint8Array(8), v = new DataView(ack.buffer);
            v.setUint32(0, frame.sequence); v.setUint32(4, frame.payload.byteLength);
            post(TYPE.ACK, next(), ack);
          } catch (_) { fail(); }
        });
      } else if (frame.type === TYPE.ADMISSION) {
        const v = new DataView(frame.payload.buffer, frame.payload.byteOffset, 8);
        const request = v.getUint32(0), kind = v.getUint8(4), result = v.getUint8(5);
        if (v.getUint16(6) !== 0 || result > ADMISSION.STOPPED) throw Error("admission-payload");
        if (kind === TYPE.INPUT) inputs.settle(request);
        else if (kind === TYPE.RESIZE && request === resizeRequest) resizeRequest = null;
        else throw Error("admission-request");
        if (result !== ADMISSION.ACCEPTED) notice((kind === TYPE.INPUT ? "Input" : "Resize") + " rejected: " +
          ["", "SSH queue is full", "attachment is stale", "SSH session is not open"][result] + ". It was not accepted or automatically retried.", true);
        if (kind === TYPE.RESIZE && desiredSize !== null) scheduleResize(desiredSize);
      } else if (frame.type === TYPE.CLOSED) {
        stopped = true; inputs.clear(); terminal.options.disableStdin = true;
        notice("Terminal attachment stopped. Reconnect using native controls.", true);
      } else throw Error("native-operation");
    } catch (_) { fail(); }
  }
  try {
    const parts = root.location.hash.slice(1).split("/");
    if (parts.length !== 3) throw Error("configuration");
    identity = { sessionId: parts[0], attachmentId: parts[1] }; identityBytes(identity);
    const scale = Number(parts[2]);
    if (!Number.isFinite(scale) || scale <= 0 || scale > 10) throw Error("font-scale");
    root.document.documentElement.style.setProperty("--font-scale", String(scale));
    if (!root.cloudyTerminal || !root.Terminal || !root.FitAddon) throw Error("assets-or-api-missing");
    root.cloudyTerminal.onmessage = receive;
    terminal = new root.Terminal({
      fontFamily: '"IBM Plex Mono", ui-monospace, monospace', fontSize: 14 * scale, lineHeight: 1.5,
      screenReaderMode: true, scrollback: 2000, allowProposedApi: false, convertEol: false, logLevel: "off",
      linkHandler: { activate: () => notice("Terminal links are disabled in this qualification surface.", true) },
      theme: { background: "#0C1519", foreground: "#DCE9ED", cursor: "#DCE9ED", selectionBackground: "#33515F",
        black: "#7E939C", red: "#FF8A85", green: "#8ED6A8", yellow: "#EBC577", blue: "#8DB4FF", magenta: "#D6A1EE", cyan: "#80D7DE", white: "#DCE8EC",
        brightBlack: "#9AB0B8", brightRed: "#FFB0A8", brightGreen: "#B2EDC4", brightYellow: "#F7DEA1", brightBlue: "#B8CEFF", brightMagenta: "#E8C1FA", brightCyan: "#ADF0F2", brightWhite: "#FFFFFF" }
    });
    const fit = new root.FitAddon.FitAddon(); terminal.loadAddon(fit);
    // Consume remote clipboard and hyperlink controls locally, without Android/browser clipboard access.
    terminal.parser.registerOscHandler(52, () => true);
    terminal.parser.registerOscHandler(8, () => true);
    terminal.open(root.document.getElementById("terminal"));
    terminal.onData(text => submit(encodeText(text)));
    terminal.onBinary(text => submit(encodeBinary(text)));
    terminal.onResize(size => { if (ready) scheduleResize({ columns: size.cols, rows: size.rows }); });
    let fitScheduled = false;
    function fitViewport() {
      if (fitScheduled || stopped) return;
      fitScheduled = true;
      root.requestAnimationFrame(() => {
        fitScheduled = false;
        if (stopped) return;
        try {
          const size = fit.proposeDimensions();
          if (!size) return;
          sizePayload(size.cols, size.rows); // Refuse unsupported geometry instead of lying about cell size.
          terminal.resize(size.cols, size.rows);
          if (!ready) {
            ready = true; post(TYPE.READY, next(), sizePayload(terminal.cols, terminal.rows));
            notice("Local terminal ready. Connection and trust are controlled above.");
            terminal.focus();
          }
        } catch (_) { fail(); }
      });
    }
    new root.ResizeObserver(fitViewport).observe(root.document.getElementById("terminal"));
    root.document.fonts.ready.then(fitViewport).catch(fail);
    fitViewport();
    root.addEventListener("error", fail);
    root.addEventListener("unhandledrejection", fail);
  } catch (_) { fail(); }
})(typeof globalThis !== "undefined" ? globalThis : this);
