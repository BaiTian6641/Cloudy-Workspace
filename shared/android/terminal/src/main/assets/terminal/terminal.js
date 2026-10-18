/* Authored terminal-only bridge. SSH configuration, trust and credentials never enter this page. */
"use strict";
(function (root) {
  const HEADER = 48, MAGIC = 0x43575452, MAX_INPUT = 65536, MAX_OUTPUT = 16384;
  const MAX_UI_TEXT = 61440, MAX_SEARCH_UNITS = 256, MAX_SEARCH_BYTES = 1024, HIGHLIGHT_LIMIT = 1000;
  // Logical CSS font size; the Android font scale is applied multiplicatively on top.
  const BASE_FONT_SIZE = 14, MIN_FONT_SIZE = 12, MAX_FONT_SIZE = 32;
  const TYPE = Object.freeze({ READY: 1, INPUT: 2, RESIZE: 3, ACK: 4, OUTPUT: 5, ADMISSION: 6, ERROR: 7, CLOSED: 8,
    UI_REQUEST: 9, UI_COMMAND: 10 });
  const ADMISSION = Object.freeze({ ACCEPTED: 0, FULL: 1, STALE: 2, STOPPED: 3 });
  // Mirrors TerminalUiRequestAction in TerminalInteraction.kt. Values never leave this attachment.
  const REQUEST = Object.freeze({ COPY_SELECTION: 1, PASTE: 2, RIGHT_CLICK: 3, MENU: 4, FIND: 5,
    SEARCH_RESULT: 6, MOUSE_TRACKING: 7, NOTICE: 8 });
  // Mirrors TerminalUiCommandAction in TerminalInteraction.kt.
  const COMMAND = Object.freeze({ SELECT_ALL: 1, COPY: 2, PASTE: 3, FOCUS: 4, FIND_NEXT: 5,
    FIND_PREVIOUS: 6, SEARCH_CLEAR: 7 });
  const NOTICE = Object.freeze({ NO_SELECTION: 1, SELECTION_TOO_LARGE: 2, SELECTION_INVALID: 3 });
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
      type === TYPE.ERROR ? n === 1 && payload[0] === 1 : type === TYPE.CLOSED ? n === 0 :
      type === TYPE.UI_REQUEST || type === TYPE.UI_COMMAND ? n >= 1 && n <= MAX_UI_TEXT + 1 : false;
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
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let pointerSelectionActive = false, pointerSelectionChanged = false, selectionChangeListener = null;
  let selectionEpoch = 0, pointerEpoch = 0, copyTimer = 0;
  let lastMouseTracking = null, search = null, searchResultsListener = null, searchAfterListener = null;
  let searchActive = false, searchFinding = false, searchResults = null, searchToken = 0, searchCommand = 0;
  let androidScale = 1, logicalFontSize = BASE_FONT_SIZE;
  let fitViewportRequest = null, zoomWheelHandler = null, clipboardHandler = null;
  const localKeys = new Set();
  function notice(text, important = false) {
    if (warning && !important) return;
    warning = important; status.textContent = text; feedback.dataset.warning = String(important); dismiss.hidden = !important;
    // The native host owns normal connection status; keep terminal space for output.
    feedback.hidden = !important && ready && !stopped;
  }
  dismiss.addEventListener("click", () => { warning = false; notice(stopped ? "Terminal attachment stopped. Reconnect using native controls." : "Local terminal ready. Connection and trust are controlled above."); });
  function next() { if (tx >= 2147483647) throw Error("sequence-exhausted"); return ++tx; }
  function post(type, sequence, payload) { root.cloudyTerminal.postMessage(encode(identity, type, sequence, payload)); }
  function fail() {
    if (stopped) return;
    stopped = true; inputs.clear(); stopInteraction();
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
  function postRaw(payload) {
    if (stopped || !ready || !identity) return false;
    try { post(TYPE.UI_REQUEST, next(), payload); return true; } catch (_) { fail(); return false; }
  }
  function postUi(action, bytes) {
    if (stopped || !ready) return false;
    const text = bytes === undefined ? new Uint8Array(0) : bytes;
    if (text.byteLength > MAX_UI_TEXT) return false;
    const payload = new Uint8Array(1 + text.byteLength);
    payload[0] = action; payload.set(text, 1);
    return postRaw(payload);
  }
  function noticeCode(code) { postUi(REQUEST.NOTICE, Uint8Array.of(code)); }
  function decodeText(bytes) { try { return decoder.decode(bytes); } catch (_) { return null; } }
  function utf8Length(text) {
    let length = 0;
    for (const character of text) {
      const point = character.codePointAt(0);
      length += point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
      if (length > MAX_UI_TEXT) return length;
    }
    return length;
  }
  // A pending pointer-copy callback must not outlive cancellation, focus loss or a remote
  // selection change performed by search/select-all/clear.
  function cancelPendingCopy() {
    if (copyTimer) { root.clearTimeout(copyTimer); copyTimer = 0; }
  }
  function cancelPointerSelection() {
    pointerSelectionActive = false; pointerSelectionChanged = false; cancelPendingCopy();
  }
  function hiddenCancel() {
    if (root.document && root.document.visibilityState !== "visible") cancelPointerSelection();
  }
  function programmaticSelection() { selectionEpoch++; }
  // Counts the UTF-8 bytes xterm.paste will emit after its declared CR/LF normalization and
  // optional bracket wrapper (ESC becomes U+241B plus the two six-byte delimiters), without
  // allocating the transformed string. Returns null for malformed UTF-16.
  function transformedPasteSize(text, bracketed) {
    let bytes = bracketed ? 12 : 0;
    for (let i = 0; i < text.length; i++) {
      let unit = text.charCodeAt(i);
      if (unit === 10) unit = 13;
      else if (unit === 13 && i + 1 < text.length && text.charCodeAt(i + 1) === 10) i++;
      if (bracketed && unit === 27) unit = 0x241b;
      if (unit < 0x80) bytes += 1;
      else if (unit < 0x800) bytes += 2;
      else if (unit >= 0xd800 && unit <= 0xdbff) {
        const low = text.charCodeAt(i + 1);
        if (!(low >= 0xdc00 && low <= 0xdfff)) return null;
        bytes += 4; i++;
      } else if (unit >= 0xdc00 && unit <= 0xdfff) return null;
      else bytes += 3;
    }
    return bytes;
  }
  function requestCopy() {
    if (stopped || !ready || !terminal) return;
    if (!terminal.hasSelection()) { noticeCode(NOTICE.NO_SELECTION); return; }
    const text = terminal.getSelection();
    if (!text) { noticeCode(NOTICE.NO_SELECTION); return; }
    if (utf8Length(text) > MAX_UI_TEXT) { noticeCode(NOTICE.SELECTION_TOO_LARGE); return; }
    postUi(REQUEST.COPY_SELECTION, new TextEncoder().encode(text));
  }
  function requestPaste() { if (!stopped && ready) postUi(REQUEST.PASTE); }
  function requestMenu() { if (!stopped && ready) postUi(REQUEST.MENU); }
  function requestFind() { if (!stopped && ready) postUi(REQUEST.FIND); }
  function reportMouseTracking() {
    if (stopped || !ready || !terminal) return;
    const active = terminal.modes.mouseTrackingMode !== "none";
    if (active === lastMouseTracking) return;
    lastMouseTracking = active;
    postUi(REQUEST.MOUSE_TRACKING, Uint8Array.of(active ? 1 : 0));
  }
  function sendSearchResult(command, found, result) {
    if (!command || stopped) return;
    const payload = new Uint8Array(14), view = new DataView(payload.buffer);
    payload[0] = REQUEST.SEARCH_RESULT;
    const count = result ? result.resultCount : 0;
    view.setUint32(1, command); view.setInt32(5, result ? result.resultIndex : -1);
    view.setInt32(9, count); view.setUint8(13, (found ? 1 : 0) | (count >= HIGHLIGHT_LIMIT ? 2 : 0));
    postRaw(payload);
  }
  function searchOptions() {
    return { regex: false, wholeWord: false, caseSensitive: false, decorations: {
      matchBackground: "#33515F", matchBorder: "#79959E", matchOverviewRuler: "#79959E",
      activeMatchBackground: "#33515F", activeMatchBorder: "#BAC8FF", activeMatchColorOverviewRuler: "#BAC8FF" } };
  }
  function ensureSearch() {
    if (search) return;
    search = new root.SearchAddon.SearchAddon({ highlightLimit: HIGHLIGHT_LIMIT });
    terminal.loadAddon(search);
    searchAfterListener = search.onAfterSearch(() => programmaticSelection());
    searchResultsListener = search.onDidChangeResults(event => {
      if (!searchActive || stopped) return;
      searchResults = event;
      if (!searchFinding) sendSearchResult(searchCommand, event.resultCount > 0, event);
    });
  }
  function disposeSearch() {
    const after = searchAfterListener, results = searchResultsListener, instance = search;
    searchAfterListener = null; searchResultsListener = null; search = null;
    // Retire every public resource even if one disposal throws; no private timeout access.
    try { if (after) after.dispose(); }
    finally {
      try { if (results) results.dispose(); }
      finally { if (instance) instance.dispose(); }
    }
  }
  function findInScrollback(direction, command, term) {
    if (stopped || !ready) return;
    const token = ++searchToken;
    searchCommand = command;
    searchResults = null; searchActive = true; searchFinding = true;
    // The addon selects the match while searching; a pending pointer copy must not claim it.
    programmaticSelection();
    let found = false;
    try {
      ensureSearch();
      const options = searchOptions();
      found = direction > 0 ? search.findNext(term, options) : search.findPrevious(term, options);
    } catch (_) { searchFinding = false; searchActive = false; fail(); return; }
    searchFinding = false;
    if (stopped || token !== searchToken) return;
    sendSearchResult(command, found, searchResults);
  }
  function clearSearch() {
    const ownedSelection = searchActive;
    searchActive = false; searchFinding = false; searchResults = null; searchCommand = 0; searchToken++;
    if (ownedSelection) programmaticSelection();
    // clearDecorations does not cancel the pinned addon's delayed output/resize refresh.
    // Dispose it fully and lazily load a new instance for the next explicit Find.
    disposeSearch();
    if (ownedSelection && terminal) terminal.clearSelection();
  }
  // xterm.paste normalizes CRLF/LF to CR, brackets only while DECSET 2004 is active,
  // replaces ESC with U+241B inside brackets, and appends no Enter (documented upstream).
  function pasteIntoTerminal(bytes) {
    if (stopped || !ready || !terminal) return;
    const text = decodeText(bytes);
    if (text === null || text.length === 0) return;
    // A <=60 KiB source can still expand past the 64 KiB INPUT frame once bracketed ESC
    // becomes U+241B. Refuse the whole action before xterm allocates or admits any of it;
    // never truncate, split or append Enter.
    const transformed = transformedPasteSize(text, terminal.modes.bracketedPasteMode);
    if (transformed === null || transformed > MAX_INPUT) {
      notice("Paste rejected: the line-ending and bracketed transformation would exceed 64 KiB. No part was sent.", true);
      return;
    }
    try { terminal.paste(text); } catch (_) { fail(); }
  }
  function pointerDown(event) {
    cancelPointerSelection();
    if (stopped || !terminal || event.button !== 0) return;
    if (terminal.modes.mouseTrackingMode !== "none" && !event.shiftKey) return;
    pointerSelectionActive = true;
    pointerEpoch = selectionEpoch;
  }
  function pointerUp(event) {
    if (event && event.button !== 0) return;
    if (stopped || !pointerSelectionActive) { cancelPointerSelection(); return; }
    const epoch = pointerEpoch;
    cancelPendingCopy();
    copyTimer = root.setTimeout(() => {
      copyTimer = 0;
      const changed = pointerSelectionChanged;
      pointerSelectionActive = false; pointerSelectionChanged = false;
      // A search/select-all/clear selection in the same turn must not be copied by a pointer action.
      if (stopped || !terminal || selectionEpoch !== epoch) return;
      const current = terminal.getSelection();
      // A repeated deliberate selection copies again even if another app changed the clipboard.
      if (current && changed) requestCopy();
    }, 0);
  }
  function contextMenu(event) {
    if (stopped || !terminal || !terminal.element || !terminal.element.contains(event.target)) return;
    const remote = terminal.modes.mouseTrackingMode !== "none";
    event.preventDefault(); event.stopPropagation();
    // Ordinary remote secondary input belongs to the remote application. Shift is the
    // deliberate local override and opens the local menu (never a second remote click).
    if (remote && !event.shiftKey) return;
    postUi(remote ? REQUEST.MENU : REQUEST.RIGHT_CLICK);
  }
  // Capture-phase ancestor handler: runs before xterm's own wheel listeners so a deliberate
  // local zoom never also reaches a remote application that owns mouse reporting.
  function zoomWheel(event) {
    if (stopped || !terminal || !event.ctrlKey || event.altKey || event.metaKey) return;
    if (typeof event.getModifierState === "function" && event.getModifierState("AltGraph")) return;
    if (!terminal.element || !terminal.element.contains(event.target)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (!event.deltaY) return;
    const next = Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE,
      logicalFontSize + (event.deltaY < 0 ? 1 : -1)));
    if (next === logicalFontSize) return;
    logicalFontSize = next;
    // Multiply the Android font scale, never replace it, and reuse the coalesced fit/resize path.
    terminal.options.fontSize = logicalFontSize * androidScale;
    if (fitViewportRequest) fitViewportRequest();
  }
  function keyCommand(event) {
    const composing = event.isComposing || event.keyCode === 229 || event.key === "Process" ||
      (typeof event.getModifierState === "function" && event.getModifierState("AltGraph"));
    if (composing) return null;
    const ctrlShift = event.ctrlKey && event.shiftKey && !event.altKey && !event.metaKey;
    const plainShift = event.shiftKey && !event.ctrlKey && !event.altKey && !event.metaKey;
    if (ctrlShift && (event.code === "KeyC" || event.key === "C" || event.key === "c")) return "copy";
    if (ctrlShift && (event.code === "KeyV" || event.key === "V" || event.key === "v")) return "paste";
    if (ctrlShift && (event.code === "KeyF" || event.key === "F" || event.key === "f")) return "find";
    if (plainShift && (event.code === "Insert" || event.key === "Insert")) return "paste";
    if (!event.ctrlKey && !event.altKey && !event.metaKey &&
        (event.key === "ContextMenu" || (event.shiftKey && event.key === "F10"))) return "menu";
    return null;
  }
  function resizeViewport(columns, rows) {
    const position = columns === terminal.cols && rows !== terminal.rows ?
      terminal.getSelectionPosition() : undefined;
    if (!position) { terminal.resize(columns, rows); return; }
    // The pinned core clears selection on any row-count change (#5300). Native local controls
    // can change IME insets without changing columns. Preserve only the exact bounded text,
    // using public markers for normal-buffer trimming; never retain a stale copy fallback.
    const text = terminal.getSelection(), buffer = terminal.buffer.active;
    const bounded = text.length > 0 && utf8Length(text) <= MAX_UI_TEXT;
    const normal = buffer.type === "normal", cursor = buffer.baseY + buffer.cursorY;
    const first = bounded && normal ? terminal.registerMarker(position.start.y - cursor) : undefined;
    const last = bounded && normal ? terminal.registerMarker(position.end.y - cursor) : undefined;
    try {
      terminal.resize(columns, rows);
      const start = normal ? first?.line : position.start.y;
      const end = normal ? last?.line : position.end.y;
      let restored = false;
      if (bounded && terminal.buffer.active.type === buffer.type &&
          (!normal || (first && last && !first.isDisposed && !last.isDisposed)) &&
          start >= 0 && end >= start && end < buffer.length) {
        terminal.select(position.start.x, start, (end - start) * columns + position.end.x - position.start.x);
        restored = terminal.getSelection() === text;
      }
      if (!restored) {
        programmaticSelection();
        terminal.clearSelection();
        notice("Selection could not be preserved after resizing. Select the text again before Copy.", true);
      }
    } finally {
      try { if (first) first.dispose(); }
      finally { if (last) last.dispose(); }
    }
  }
  function applyCommand(frame) {
    const payload = frame.payload, action = payload[0], text = payload.subarray(1);
    const empty = text.byteLength === 0;
    if (action === COMMAND.SELECT_ALL) {
      if (!empty) throw Error("ui-command");
      programmaticSelection();
      terminal.selectAll();
    } else if (action === COMMAND.COPY) {
      if (!empty) throw Error("ui-command");
      requestCopy();
    } else if (action === COMMAND.PASTE) {
      if (empty || text.byteLength > MAX_UI_TEXT) throw Error("ui-command");
      pasteIntoTerminal(text);
    } else if (action === COMMAND.FOCUS) {
      if (!empty) throw Error("ui-command");
      terminal.focus();
    } else if (action === COMMAND.FIND_NEXT || action === COMMAND.FIND_PREVIOUS) {
      if (empty || text.byteLength > MAX_SEARCH_BYTES) throw Error("search-command");
      const term = decodeText(text);
      if (term === null || !term.length || term.length > MAX_SEARCH_UNITS) throw Error("search-command");
      findInScrollback(action === COMMAND.FIND_NEXT ? 1 : -1, frame.sequence, term);
    } else if (action === COMMAND.SEARCH_CLEAR) {
      if (!empty) throw Error("ui-command");
      clearSearch();
    } else throw Error("ui-command");
  }
  function stopInteraction() {
    searchActive = false; searchResults = null; searchCommand = 0; searchToken++;
    localKeys.clear();
    cancelPointerSelection();
    if (selectionChangeListener) {
      try { selectionChangeListener.dispose(); } catch (_) { /* remaining resources still retire */ }
      selectionChangeListener = null;
    }
    try { disposeSearch(); } catch (_) { /* all public search resources were attempted */ }
    if (root.document) {
      root.document.removeEventListener("pointerup", pointerUp, true);
      root.document.removeEventListener("pointercancel", cancelPointerSelection, true);
      root.document.removeEventListener("visibilitychange", hiddenCancel, true);
      root.document.removeEventListener("contextmenu", contextMenu, true);
      if (zoomWheelHandler) root.document.removeEventListener("wheel", zoomWheelHandler, true);
      if (clipboardHandler) {
        root.document.removeEventListener("copy", clipboardHandler, true);
        root.document.removeEventListener("cut", clipboardHandler, true);
        root.document.removeEventListener("paste", clipboardHandler, true);
      }
    }
    if (root.removeEventListener) root.removeEventListener("blur", cancelPointerSelection);
    if (terminal && terminal.element) {
      terminal.element.removeEventListener("pointerdown", pointerDown, true);
    }
    zoomWheelHandler = null; clipboardHandler = null; fitViewportRequest = null;
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
        stopped = true; inputs.clear(); stopInteraction(); terminal.options.disableStdin = true;
        notice("Terminal attachment stopped. Reconnect using native controls.", true);
      } else if (frame.type === TYPE.UI_COMMAND) {
        applyCommand(frame);
      } else throw Error("native-operation");
    } catch (_) { fail(); }
  }
  try {
    const parts = root.location.hash.slice(1).split("/");
    if (parts.length !== 4 || !/^[01]$/.test(parts[3])) throw Error("configuration");
    identity = { sessionId: parts[0], attachmentId: parts[1] }; identityBytes(identity);
    const scale = Number(parts[2]);
    if (!Number.isFinite(scale) || scale <= 0 || scale > 10) throw Error("font-scale");
    androidScale = scale;
    root.document.documentElement.style.setProperty("--font-scale", String(scale));
    if (!root.cloudyTerminal || !root.Terminal || !root.FitAddon || !root.SearchAddon || !root.SearchAddon.SearchAddon) {
      throw Error("assets-or-api-missing");
    }
    root.cloudyTerminal.onmessage = receive;
    terminal = new root.Terminal({
      fontFamily: '"IBM Plex Mono", ui-monospace, monospace', fontSize: BASE_FONT_SIZE * scale, lineHeight: 1.5,
      screenReaderMode: parts[3] === "1", scrollback: 2000, allowProposedApi: false, convertEol: false, logLevel: "off",
      linkHandler: { activate: () => notice("Terminal links are disabled in this qualification surface.", true) },
      theme: { background: "#0C1519", foreground: "#DCE9ED", cursor: "#DCE9ED", selectionBackground: "#33515F",
        black: "#7E939C", red: "#FF8A85", green: "#8ED6A8", yellow: "#EBC577", blue: "#8DB4FF", magenta: "#D6A1EE", cyan: "#80D7DE", white: "#DCE8EC",
        brightBlack: "#9AB0B8", brightRed: "#FFB0A8", brightGreen: "#B2EDC4", brightYellow: "#F7DEA1", brightBlue: "#B8CEFF", brightMagenta: "#E8C1FA", brightCyan: "#ADF0F2", brightWhite: "#FFFFFF" }
    });
    // Native controls use only public xterm APIs; input remains owned by xterm's single path.
    root.cloudyTerminalUi = Object.freeze({
      focus() { if (!stopped) terminal.focus(); },
      screenReaderMode(enabled) {
        if (!stopped && typeof enabled === "boolean") terminal.options.screenReaderMode = enabled;
      }
    });
    const fit = new root.FitAddon.FitAddon(); terminal.loadAddon(fit);
    // Consume remote clipboard and hyperlink controls locally, without Android/browser clipboard access.
    terminal.parser.registerOscHandler(52, () => true);
    terminal.parser.registerOscHandler(8, () => true);
    terminal.open(root.document.getElementById("terminal"));
    terminal.onData(text => submit(encodeText(text)));
    terminal.onBinary(text => submit(encodeBinary(text)));
    terminal.onResize(size => {
      if (ready) scheduleResize({ columns: size.cols, rows: size.rows });
      reportMouseTracking();
    });
    terminal.onWriteParsed(reportMouseTracking);
    selectionChangeListener = terminal.onSelectionChange(() => {
      // Compatibility mouseup follows pointerup; retain ownership until the copy callback.
      if (pointerSelectionActive) pointerSelectionChanged = true;
    });
    terminal.attachCustomKeyEventHandler(event => {
      const keyId = event.code || (event.key && event.key.length === 1 ?
        "Key" + event.key.toUpperCase() : event.key) || String(event.keyCode);
      if (localKeys.has(keyId)) {
        if (event.type === "keyup") {
          localKeys.delete(keyId);
          event.preventDefault(); event.stopPropagation();
          return false;
        }
        if (event.type !== "keydown" || event.repeat) {
          event.preventDefault(); event.stopPropagation();
          return false;
        }
        // A fresh DOWN after a lost UP starts a new gesture, never a replay of the old command.
        localKeys.delete(keyId);
      }
      const action = keyCommand(event);
      if (!action) return true;
      // Consume the complete shortcut, including repeats and keypress, without a remote action.
      event.preventDefault(); event.stopPropagation();
      if (event.type !== "keydown" || event.repeat) return false;
      localKeys.add(keyId);
      if (action === "copy") requestCopy();
      else if (action === "paste") requestPaste();
      else if (action === "find") requestFind();
      else requestMenu();
      return false;
    });
    terminal.element.addEventListener("pointerdown", pointerDown, true);
    root.document.addEventListener("pointerup", pointerUp, true);
    root.document.addEventListener("pointercancel", cancelPointerSelection, true);
    root.document.addEventListener("visibilitychange", hiddenCancel, true);
    root.addEventListener("blur", cancelPointerSelection);
    // Document capture runs before xterm's own right-click handler, so the local route never
    // populates xterm's textarea as a browser copy source.
    root.document.addEventListener("contextmenu", contextMenu, true);
    zoomWheelHandler = zoomWheel;
    root.document.addEventListener("wheel", zoomWheelHandler, { capture: true, passive: false });
    // Native owns the clipboard: a document-capture guard runs before xterm's textarea or
    // element handlers, so no browser Clipboard API path can read or write terminal text.
    // A browser cut would mutate browser-owned textarea state; terminal text is read-only,
    // so it stays a local no-op rather than a second copy request.
    clipboardHandler = event => {
      if (stopped || !terminal || !terminal.element.contains(event.target)) return;
      event.preventDefault(); event.stopPropagation();
      if (event.type === "paste") requestPaste();
      else if (event.type === "copy") requestCopy();
      else notice("Cut is unavailable for read-only terminal output. Use Copy instead.", true);
    };
    root.document.addEventListener("copy", clipboardHandler, true);
    root.document.addEventListener("cut", clipboardHandler, true);
    root.document.addEventListener("paste", clipboardHandler, true);
    let fitScheduled = false;
    fitViewportRequest = fitViewport;
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
          resizeViewport(size.cols, size.rows);
          if (!ready) {
            ready = true; post(TYPE.READY, next(), sizePayload(terminal.cols, terminal.rows));
            notice("Local terminal ready. Connection and trust are controlled above.");
            reportMouseTracking();
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
