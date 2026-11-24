#!/usr/bin/env python3
# Poll Chrome / ChatGPT CDP (and AT-SPI if present) and write
# /workspace/.debarm/ime-focus as 1, 0, or ? so the Android app can
# show or hide the system IME when a remote text field is focused.
import base64
import json
import os
import socket
import struct
import time
import urllib.request

OUT = "/workspace/.debarm/ime-focus"
PORTS = (9222, 9223)

EDIT_EXPR = (
    "(function(){"
    "function isEditable(e){"
    "if(!e) return false;"
    "var t=(e.tagName||'').toLowerCase();"
    "if(t==='input'){"
    "var ty=(e.type||'text').toLowerCase();"
    "if(['button','checkbox','radio','file','submit','reset','hidden',"
    "'image','range','color'].indexOf(ty)>=0) return false;"
    "return !e.disabled && !e.readOnly;"
    "}"
    "if(t==='textarea') return !e.disabled && !e.readOnly;"
    "if(t==='select') return !e.disabled;"
    "if(e.isContentEditable) return true;"
    "var role=e.getAttribute && (e.getAttribute('role')||'');"
    "if(role==='textbox'||role==='searchbox'||role==='combobox') return true;"
    "return false;"
    "}"
    "function walk(e){"
    "if(!e) return false;"
    "if(isEditable(e)) return true;"
    "try{var r=e.shadowRoot; if(r && walk(r.activeElement)) return true;}catch(ex){}"
    "try{if(e.contentDocument) return walk(e.contentDocument.activeElement);}catch(ex){}"
    "return false;"
    "}"
    "try{return walk(document.activeElement);}catch(e){return false;}"
    "})()"
)


def atomic_write(path, text):
    folder = os.path.dirname(path)
    if folder and not os.path.isdir(folder):
        os.makedirs(folder)
    tmp = path + ".tmp"
    with open(tmp, "w") as handle:
        handle.write(text)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(tmp, path)


def http_json(url, timeout=0.4):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8", "replace"))
    except Exception:
        return None


def recv_http_headers(sock):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        if len(data) > 65536:
            break
    return data


def ws_connect(url):
    if not url.startswith("ws://"):
        raise RuntimeError("not a ws url")
    rest = url[5:]
    hostport, _, path = rest.partition("/")
    path = "/" + path
    if ":" in hostport:
        host, port_s = hostport.rsplit(":", 1)
        port = int(port_s)
    else:
        host, port = hostport, 80
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    req = (
        "GET %s HTTP/1.1\r\n"
        "Host: %s:%s\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: %s\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n" % (path, host, port, key)
    )
    sock = socket.create_connection((host, port), 0.6)
    sock.settimeout(0.8)
    sock.sendall(req.encode("ascii"))
    hdr = recv_http_headers(sock)
    first = hdr.split(b"\r\n", 1)[0]
    if b" 101 " not in first:
        sock.close()
        raise RuntimeError("ws handshake failed")
    return sock


def ws_send_text(sock, text):
    payload = text.encode("utf-8")
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    n = len(payload)
    if n < 126:
        header = struct.pack("!BB", 0x81, 0x80 | n) + mask
    elif n < 65536:
        header = struct.pack("!BBH", 0x81, 0x80 | 126, n) + mask
    else:
        header = struct.pack("!BBQ", 0x81, 0x80 | 127, n) + mask
    sock.sendall(header + masked)


def ws_recv_text(sock):
    def recvn(n):
        buf = b""
        while len(buf) < n:
            chunk = sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("eof")
            buf += chunk
        return buf

    b1, b2 = recvn(2)
    opcode = b1 & 0x0F
    masked = b2 & 0x80
    n = b2 & 0x7F
    if n == 126:
        n = struct.unpack("!H", recvn(2))[0]
    elif n == 127:
        n = struct.unpack("!Q", recvn(8))[0]
    mask = recvn(4) if masked else b""
    payload = recvn(n)
    if masked:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    if opcode == 0x8:
        return None
    if opcode in (0x1, 0x2):
        return payload.decode("utf-8", "replace")
    return None


def cdp_eval(port, expr):
    tabs = http_json("http://127.0.0.1:%s/json" % port)
    if not tabs:
        return None
    pages = [
        t for t in tabs
        if t.get("webSocketDebuggerUrl")
        and t.get("type") not in ("service_worker", "background_page", "worker")
    ]
    any_true = False
    any_result = False
    for page in pages:
        sock = None
        try:
            sock = ws_connect(page["webSocketDebuggerUrl"])
            msg = json.dumps({
                "id": 1,
                "method": "Runtime.evaluate",
                "params": {
                    "expression": expr,
                    "returnByValue": True,
                    "awaitPromise": False,
                },
            })
            ws_send_text(sock, msg)
            deadline = time.time() + 0.8
            while time.time() < deadline:
                raw = ws_recv_text(sock)
                if raw is None:
                    break
                data = json.loads(raw)
                if data.get("id") == 1:
                    val = data.get("result", {}).get("result", {}).get("value")
                    any_result = True
                    if val is True:
                        any_true = True
                    break
        except Exception:
            pass
        finally:
            if sock is not None:
                try:
                    sock.close()
                except Exception:
                    pass
        if any_true:
            return True
    if any_result:
        return False
    return None


def atspi_editable():
    try:
        import gi
        gi.require_version("Atspi", "2.0")
        from gi.repository import Atspi
        Atspi.init()
        focused = Atspi.get_focus() if hasattr(Atspi, "get_focus") else None
        if focused is None:
            return None
        role = (focused.get_role_name() or "").lower()
        if role in (
            "entry",
            "password text",
            "text",
            "terminal",
            "edit bar",
            "combo box",
            "spin button",
            "paragraph",
        ):
            return True
        if role in (
            "push button",
            "button",
            "check box",
            "radio button",
            "menu item",
            "label",
            "image",
            "filler",
            "panel",
            "frame",
            "window",
            "dialog",
            "page tab",
            "scroll pane",
            "split pane",
            "toolbar",
            "icon",
            "canvas",
            "document web",
            "document frame",
        ):
            return False
        return None
    except Exception:
        return None


def probe():
    seen_false = False
    for port in PORTS:
        value = cdp_eval(port, EDIT_EXPR)
        if value is True:
            return True
        if value is False:
            seen_false = True
    accessibility = atspi_editable()
    if accessibility is True:
        return True
    if accessibility is False:
        return False
    if seen_false:
        return False
    return None


def main():
    last = None
    while True:
        try:
            value = probe()
        except Exception:
            value = None
        if value is True:
            text = "1\n"
        elif value is False:
            text = "0\n"
        else:
            text = "?\n"
        if text != last:
            try:
                atomic_write(OUT, text)
                last = text
            except Exception:
                pass
        time.sleep(0.25)


if __name__ == "__main__":
    main()
