package com.wusper.debarm.terminal

import android.content.Context

/**
 * Builds a self-contained terminal page with xterm.js inlined.
 *
 * 1.1.0–1.1.4 loaded scripts via relative URLs from
 * file:///android_asset/, which fails on some WebViews (notably Xiaomi)
 * when allowFileAccessFromFileURLs is false. Inlining means there is no
 * secondary file request at all.
 */
object TerminalPage {

    const val PAGE_HOST = "appassets.androidplatform.net"
    const val PAGE_URL = "https://appassets.androidplatform.net/assets/terminal/index.html"

    /**
     * Serve the inlined page (and any relative assets) from a real https
     * origin. Modern Chromium WebView does not inject @JavascriptInterface
     * into the opaque origin that loadDataWithBaseURL often gets, so
     * window.DebArm stays undefined and onReady never runs.
     */
    fun intercept(context: Context, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
        val url = request.url ?: return null
        if (url.host != PAGE_HOST) return null
        val path = url.path ?: return null
        val isIndex = path.endsWith("/terminal/index.html") ||
            path.endsWith("/terminal/") ||
            path.endsWith("/terminal")
        return try {
            if (isIndex) {
                stream("text/html", html(context).toByteArray(Charsets.UTF_8))
            } else {
                val asset = path.removePrefix("/assets/").trimStart('/')
                val mime = when {
                    asset.endsWith(".js") -> "application/javascript"
                    asset.endsWith(".css") -> "text/css"
                    asset.endsWith(".html") -> "text/html"
                    else -> "application/octet-stream"
                }
                val bytes = context.assets.open(asset).use { it.readBytes() }
                stream(mime, bytes)
            }
        } catch (_: Exception) {
            android.webkit.WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                mapOf("Access-Control-Allow-Origin" to "https://$PAGE_HOST"),
                java.io.ByteArrayInputStream(ByteArray(0)),
            )
        }
    }

    private fun stream(mime: String, bytes: ByteArray): android.webkit.WebResourceResponse {
        return android.webkit.WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Access-Control-Allow-Origin" to "https://$PAGE_HOST",
            ),
            java.io.ByteArrayInputStream(bytes),
        )
    }

    fun html(context: Context): String {
        val css = readAsset(context, "terminal/xterm.css")
        val xterm = sanitize(readAsset(context, "terminal/xterm.js"))
        val fit = sanitize(readAsset(context, "terminal/addon-fit.js"))
        return """<!DOCTYPE html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<style>
html, body { height: 100%; margin: 0; background: #14120F; overflow: hidden; }
#term { position: absolute; inset: 0; padding: 6px 8px 2px; }
$css
.xterm .xterm-helper-textarea {
  left: 0 !important; top: 0 !important;
  width: 1px !important; height: 1px !important;
  opacity: 0 !important; pointer-events: none !important;
  z-index: -1 !important;
}
</style>
</head>
<body>
<div id="term"></div>
<script>$xterm</script>
<script>$fit</script>
<script>
(function () {
  function report(err) {
    try {
      document.body.style.color = "#E07A6A";
      document.body.style.padding = "12px";
      document.body.style.fontFamily = "monospace";
      document.body.textContent = "終端畫面載入失敗：" + err;
    } catch (e) {}
    if (window.DebArm && window.DebArm.onError) window.DebArm.onError(String(err));
  }
  try {
    const Term = (window.Terminal && window.Terminal.Terminal) || window.Terminal;
    if (typeof Term !== "function") throw new Error("xterm.js 未載入");
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
      try { if (fit) fit.fit(); } catch (e) {}
      if (window.DebArm && window.DebArm.onSize) {
        window.DebArm.onSize(term.cols, term.rows);
      }
    }
    window.addEventListener("resize", doFit);
    if (window.visualViewport) window.visualViewport.addEventListener("resize", doFit);

    window.DebArmTerm = {
      writeBase64: function (b64) {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        try { term.write(new TextDecoder("utf-8").decode(bytes)); }
        catch (e) { term.write(bytes); }
      },
      fit: doFit,
      cols: function () { return term.cols; },
      rows: function () { return term.rows; }
    };
    function notifyReady() {
      doFit();
      if (window.DebArm && window.DebArm.onReady) {
        window.DebArm.onReady();
        return true;
      }
      return false;
    }
    var tries = 0;
    (function wait() {
      if (notifyReady()) return;
      if (++tries < 80) setTimeout(wait, 50);
    })();
  } catch (err) {
    report(err && err.message ? err.message : err);
  }
})();
</script>
</body>
</html>
"""
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun sanitize(js: String): String =
        js.replace("</script", "<\\/script").replace("</style", "<\\/style")
}
