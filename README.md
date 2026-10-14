# Copper Compass

Build the "Ai V2Ray Client" web app reproducing the exact design system and features from the native Jetpack Compose project:

1. Design Language & Theme:
- Brushed-steel and burnished-copper precision instrument aesthetic.
- Color palette:
  - Steel: Base (#2C3035), Mid tray surface (#3F454C), Highlight bevel (#5A626C), Shadow (#0E1013), DeepInk (#06070A)
  - Copper: Front needle & titles (#C87547), Highlight (#E8C99A), Bevel (#7A4A1F), Glow (#E8B274)
  - Iron / Accents: EngraveLight (#9AA1A8), OffLineBorder (#B89A5C), StatusLed (#E53935), OnlineLed (#4CAF50)
- Metallic recessed trays (SteelTray) with vertical chamfers and inner shadows, engraved keycap buttons, and glowing copper indicators.

2. Navigation & Layout (Mobile-first PWA / Capacitor layout with desktop centering):
- Top Header: Status LED indicator, "Ai V2Ray Client" title in burnished copper, status badge ("OFFLINE" in red or "CONNECTED" in glowing copper/green).
- Recessed pill navigation bar with 3 tabs:
  - CONNECTION (Bolt icon)
  - SETTINGS (Settings icon)
  - INFO (Info icon)

3. Connection Tab:
- AI Assist Panel:
  - Header: Glowing copper badge with "AI · Ask Gemini"
  - Purpose input field: "What do you want to use the VPN for? Type platform or paste raw key (vmess://, vless://, trojan://, ss://)"
  - Gemini API key input field
  - Quick action chips: "PUBG Mobile", "Facebook & IG", "YouTube 4K", "Low Latency Gaming", "Anti-Censorship"
  - AI recommendation trigger that analyzes the user's intent and suggests the best server configuration / protocol.
- Active Server Summary Card: Shows currently selected node name, protocol badge, ping, and server location flag.
- Server Management Drawer / Modal:
  - List of servers with latency tags and selection check.
  - "Add Server" button supporting: manual config, paste URI link (`vmess://`, `vless://`, `trojan://`, `ss://`), import subscription URL, or QR code scanner (webcam or file upload).
- Instrument Gauge Dials & Power Controls:
  - Left Dial: 270-degree metallic analog gauge with copper needle for "LATENCY PING" (ms).
  - Center Power Button: Large circular brushed-steel button with embossed copper bolt icon, tactile click effect, and glowing radial aura when active. Toggling simulates connection lifecycle (connecting handshake -> connected with live telemetry).
  - Right Dial: 270-degree analog gauge with copper needle for "SPEED RATE" (Mbps / KBps) with animated telemetry when connected.

4. Settings Tab:
- Device & Network:
  - Kill Switch toggle
  - DNS selector (Cloudflare 1.1.1.1, Google 8.8.8.8, AdGuard, Quad9, Custom)
  - Protocol toggles (VLESS, VMESS, Trojan, Shadowsocks)
  - Routing rules: Global, Bypass LAN, Bypass Mainland/Domestic, Rule-based
  - SOCKS5 / HTTP local proxy port display (10808 / 10809)
- Appearance & Theme display: Brushed Steel & Burnished Copper design tokens.
- Export Xray / V2Ray JSON configuration button and clipboard copy.

5. Information Tab:
- Device & connection diagnostic information: App Version 2.4.1, protocols, simulated client IP & server exit IP, subscription status.
- Zero-Log & Data Safety privacy card: No telemetry, no ad SDKs, local-only configuration parsing.
- Guide on exporting to native Android using Capacitor (`npx cap add android`).

6. Local Config Parser Engine:
- Include the functional local parser that parses `vmess://` (base64 JSON), `vless://`, `trojan://`, and `ss://` URIs directly in TypeScript into valid Xray JSON configurations. Include preset test nodes so the UI is immediately interactive.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/0b12b832-408c-41d2-b1c3-a5260c1d457b).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```

## Android

The Android app keeps the Netcon interface and uses a registered native Capacitor plugin to control Android `VpnService` with the bundled Xray core. Browser-only use does not create a VPN connection. Android packaging and tagged GitHub releases are documented in [docs/android-release.md](docs/android-release.md); device-level VPN validation steps and limitations are documented in [docs/native-vpn-integration.md](docs/native-vpn-integration.md).
