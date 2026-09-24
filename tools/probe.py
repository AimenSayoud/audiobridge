#!/usr/bin/env python3
"""Independent AudioBridge client: checks a running server without a phone.

Standard library only; --play additionally needs `pip install sounddevice`.

    python3 probe.py                      # read ~/.audiobridge/token, connect to 127.0.0.1
    python3 probe.py 'audiobridge://p?..' # exactly what the QR encodes
    python3 probe.py --seconds 30         # listen longer (default 8)
    python3 probe.py --play               # also play the stream locally

Exits 0 when the delivered sample rate matches the handshake and no audio
frames were lost, 1 otherwise.
"""
from __future__ import annotations

import argparse
import json
import socket
import struct
import sys
import time
import urllib.parse
from pathlib import Path

HEADER = struct.Struct("!BBHI")
T_AUDIO, T_PING, T_PONG, T_STATS, T_BYE = 0, 1, 2, 3, 4


def parse_uri(uri: str):
    u = urllib.parse.urlparse(uri)
    q = urllib.parse.parse_qs(u.query)
    return {"hosts": q.get("h", ["127.0.0.1"]), "port": int(q.get("p", ["45678"])[0]),
            "token": q.get("t", [""])[0], "name": q.get("n", ["?"])[0]}


def recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("server closed")
        buf += chunk
    return bytes(buf)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("uri", nargs="?")
    ap.add_argument("--host"); ap.add_argument("--port", type=int, default=45678)
    ap.add_argument("--token"); ap.add_argument("--seconds", type=float, default=8)
    ap.add_argument("--play", action="store_true", help="play the stream on this Mac")
    a = ap.parse_args()

    if a.uri:
        cfg = parse_uri(a.uri)
        host, port, token = cfg["hosts"][0], cfg["port"], cfg["token"]
    else:
        host = a.host or "127.0.0.1"
        port = a.port
        token = a.token or (Path.home() / ".audiobridge/token").read_text().strip()

    print(f"[probe] connecting to {host}:{port}")
    s = socket.create_connection((host, port), timeout=10)
    s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    t_connect = time.monotonic()

    hello = {"v": 1, "role": "sink", "token": token, "name": "probe.py", "caps": ["pcm_s16le"]}
    s.sendall((json.dumps(hello) + "\n").encode())

    line = b""
    while not line.endswith(b"\n"):
        line += s.recv(1)
    welcome = json.loads(line)
    print(f"[probe] handshake {(time.monotonic() - t_connect) * 1000:.0f} ms -> {welcome}")
    if not welcome.get("ok"):
        return 1

    rate, ch = welcome["rate"], welcome["ch"]
    sink = None
    if a.play:
        import sounddevice as sd
        sink = sd.RawOutputStream(channels=ch, samplerate=rate, dtype="int16", latency="low")
        sink.start()

    s.settimeout(5)
    audio_bytes = frames = pings = gaps = peak = 0
    expect_seq = None
    t0 = time.monotonic()
    try:
        while time.monotonic() - t0 < a.seconds:
            head = recv_exact(s, HEADER.size)
            ftype, _flags, length, seq = HEADER.unpack(head)
            payload = recv_exact(s, length) if length else b""
            if ftype == T_AUDIO:
                if expect_seq is not None and seq != expect_seq:
                    gaps += (seq - expect_seq) & 0xFFFFFFFF
                expect_seq = (seq + 1) & 0xFFFFFFFF
                audio_bytes += len(payload); frames += 1
                sm = struct.unpack_from("<%dh" % (len(payload) // 2), payload)
                peak = max(peak, max(abs(x) for x in sm))
                if sink:
                    sink.write(payload)
            elif ftype == T_PING:
                s.sendall(HEADER.pack(T_PONG, 0, len(payload), seq) + payload)
                pings += 1
                stats = json.dumps({"bufMs": 0, "under": 0, "lost": gaps, "speed": 1.0}).encode()
                s.sendall(HEADER.pack(T_STATS, 0, len(stats), 0) + stats)
    except (ConnectionError, socket.timeout) as e:
        print(f"[probe] stream ended: {e}")
    finally:
        if sink:
            sink.stop(); sink.close()
        s.close()

    dur = time.monotonic() - t0
    fps = audio_bytes / (2 * ch) / dur
    print(f"[probe] {frames} audio frames, {audio_bytes} B in {dur:.1f}s")
    print(f"[probe] {fps:.0f} samples/s (expect {rate}), {audio_bytes * 8 / dur / 1000:.0f} kbit/s")
    print(f"[probe] pings answered: {pings}, seq gaps: {gaps}")
    print(f"[probe] peak amplitude: {peak} ({'AUDIO PRESENT' if peak > 100 else 'SILENCE'})")
    ok = abs(fps - rate) / rate < 0.05 and gaps == 0
    print(f"[probe] {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
