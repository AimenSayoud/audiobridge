# Security policy

## Threat model

AudioBridge is designed for a phone and a Mac owned by the same person, on a
home network or a USB cable.

- **Authentication:** a phone must present a 16-byte random token, taken from
  the pairing QR, before the Mac sends any audio. The token is compared in
  constant time and stored at `~/.audiobridge/token` with mode `0600`.
- **No encryption:** audio travels as plain PCM over TCP. Anyone who can
  observe the traffic on the network can listen to it. Use the USB route on a
  network you do not trust, or a VPN such as Tailscale that encrypts the link.
- **Exposure:** the Mac listens on port 45678 on every interface. Exposing that
  port to the internet with a public tunnel means anyone holding the token can
  listen.

Rotate the token under **Pairing › Generate a new token** if a QR code or link
may have been seen by someone else. Every paired phone then has to scan again.

## Reporting a vulnerability

Please report vulnerabilities privately through
[GitHub security advisories](https://github.com/AimenSayoud/audiobridge/security/advisories/new)
rather than a public issue. Include steps to reproduce and the versions
affected. You should get a response within a week.
