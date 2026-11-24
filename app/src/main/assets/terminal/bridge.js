(function () {
  const Term = (window.Terminal && window.Terminal.Terminal) || window.Terminal;
  const FitCtor = window.FitAddon && (window.FitAddon.FitAddon || window.FitAddon);
  const term = new Term({
    cursorBlink: true,
    fontFamily: "Menlo, Consolas, 'Droid Sans Mono', 'Courier New', monospace",
    fontSize: 14,
    scrollback: 4000,
    theme: {
      background: "#14120F",
      foreground: "#F4EFE6",
      cursor: "#E8C07A",
      cursorAccent: "#14120F",
      selectionBackground: "#4A3F2F",
      black: "#1E1B16",
      red: "#E07A6A",
      green: "#8FBF88",
      yellow: "#E8C07A",
      blue: "#8AA4C8",
      magenta: "#C49BC7",
      cyan: "#7EB8B0",
      white: "#F4EFE6",
      brightBlack: "#7A7368",
      brightRed: "#F09A8C",
      brightGreen: "#A8D4A2",
      brightYellow: "#F3D39A",
      brightBlue: "#A8BEE0",
      brightMagenta: "#D8B5DB",
      brightCyan: "#9ED0C9",
      brightWhite: "#FFFFFF"
    }
  });
  const fit = FitCtor ? new FitCtor() : null;
  if (fit) term.loadAddon(fit);
  term.open(document.getElementById("term"));

  function toB64(str) {
    const enc = new TextEncoder().encode(str);
    let s = "";
    for (let i = 0; i < enc.length; i++) s += String.fromCharCode(enc[i]);
    return btoa(s);
  }

  function doFit() {
    if (fit) fit.fit();
    if (window.DebArm && window.DebArm.onSize) {
      window.DebArm.onSize(term.cols, term.rows);
    }
  }

  window.addEventListener("resize", doFit);
  function focusTerm() { try { term.focus(); } catch (e) {} }
  document.addEventListener("touchstart", focusTerm, {passive: true});
  document.addEventListener("click", focusTerm);
  term.onData(function (data) {
    if (window.DebArm && window.DebArm.onInput) {
      window.DebArm.onInput(toB64(data));
    }
  });

  window.DebArmTerm = {
    writeBase64: function (b64) {
      const bin = atob(b64);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      term.write(bytes);
    },
    fit: doFit,
    focus: function () { term.focus(); }
  };

  setTimeout(function () {
    doFit();
    term.focus();
    if (window.DebArm && window.DebArm.onReady) window.DebArm.onReady();
  }, 30);
})();
