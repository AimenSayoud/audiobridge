# AudioBridge wire protocol v1

One TCP connection per sink. The server (macOS) pushes audio; the client
(Android) only sends a handshake and pong frames. All multi-byte integers are
**big-endian** (network order).

## 1. Handshake

Two UTF-8 JSON documents, each terminated by `\n`. Max 8 KiB each.

**Client → server (HELLO)**

```json
{"v":1,"role":"sink","token":"<base64url>","name":"Pixel 9","caps":["pcm_s16le"]}
```

**Server → client (WELCOME)**

```json
{"v":1,"ok":true,"rate":48000,"ch":2,"fmt":"pcm_s16le","block":480,"name":"Studio Mac"}
```

On failure the server replies `{"v":1,"ok":false,"err":"invalid token"}` and
closes. The token is compared with a constant-time comparison. A client that
sends no HELLO within 5 s is dropped.

`block` is advisory: the frame header carries the real length.

## 2. Frames

After WELCOME, the stream is a sequence of frames:

```
 0        1        2                4                              8
 +--------+--------+----------------+------------------------------+
 |  type  | flags  |     length     |            seq               |
 +--------+--------+----------------+------------------------------+
 |                        payload (length bytes)                   |
 +-----------------------------------------------------------------+
```

| Field    | Size | Meaning                                        |
|----------|------|------------------------------------------------|
| `type`   | u8   | frame type (below)                             |
| `flags`  | u8   | reserved, must be 0                            |
| `length` | u16  | payload length, 0..65535                       |
| `seq`    | u32  | wraps at 2^32; increments per frame, per type  |

| type | name  | direction | payload                                       |
|------|-------|-----------|-----------------------------------------------|
| 0    | AUDIO | S → C     | interleaved PCM s16le, `ch` channels          |
| 1    | PING  | S → C     | u64 server monotonic microseconds             |
| 2    | PONG  | C → S     | the PING payload, echoed verbatim             |
| 3    | STATS | C → S     | UTF-8 JSON, client telemetry (best effort)    |
| 4    | BYE   | both      | optional UTF-8 reason                         |

Note that **AUDIO payload is little-endian** PCM (it is raw sample data
straight from CoreAudio, not a protocol integer); only the header is
big-endian. A gap in `seq` on AUDIO frames means frames were dropped in the
server's send queue — the client reports this as packet loss.

Server sends PING once per second. RTT = now − echoed timestamp, measured on
the server; the client also reports its own view in STATS.

## 3. Pairing payload (QR code)

```
audiobridge://p?h=<ipv4>&h=<ipv4>&p=<port>&t=<token>&r=<rate>&c=<channels>&n=<name>
```

`h` repeats once per reachable interface address; the client races connections
to all of them in parallel and keeps the first that completes the handshake.
`n` is percent-encoded. `r`, `c` and `n` are hints for the UI before the
handshake lands — the WELCOME values always win.

A bare `host:port` string is also accepted (no token, for manual/USB entry).

## 4. Transports

| Mode | Server bind | Client target            | Setup                              |
|------|-------------|--------------------------|------------------------------------|
| USB  | 127.0.0.1   | 127.0.0.1:port           | `adb reverse tcp:port tcp:port`    |
| LAN  | 0.0.0.0     | one of the QR's `h` hosts| same Wi-Fi                         |

USB is lower latency and immune to Wi-Fi congestion; LAN is cable-free. The
client treats them identically once connected.

## 5. Security

The token is 16 random bytes, base64url, persisted at `~/.audiobridge/token`
so pairing survives restarts. It authenticates the client to the server only —
the audio itself is **not encrypted**. On an untrusted network, use USB mode.
Rotate it in the macOS app under **Pairing › Generate a new token**; every
paired client then has to scan again.
