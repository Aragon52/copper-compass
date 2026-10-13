# External Android VPN integration

Status: **contract only**. No Android project, native service, APK, or real tunnel is provided by this web project. `src/lib/native-vpn.ts` defines the external adapter; the current UI uses explicitly labeled simulation. It does not automatically detect or trust a browser-injected bridge.

## Required companion
Implement Android VpnService consent and a foreground service, Xray plus a compatible TUN-to-proxy adapter, IPv4/IPv6 handling, protected upstream sockets to prevent routing loops, UDP forwarding, and orderly shutdown. Package and validate compatible GeoIP/GeoSite assets for the exported rules. Pin and review all native dependencies and licenses.

Pass `nativeConnectRequest(node, settings)` only over a trusted in-app bridge. Do not expose configs through public HTTP endpoints, arbitrary browser globals, logs, analytics, or AI requests. The bridge must validate the caller origin and messages; credentials remain on the device. Apply changed settings via explicit reconnect or an atomic supported reconfiguration, never claim successful application before the service confirms it.

## Protection
- Capture DNS through TUN and route resolver queries through the selected policy; plain resolver IPs do not imply encrypted DNS. Validate custom resolvers and DNS/IP/domain routing interaction.
- Fail closed on core startup failure and reconnection by keeping TUN active and dropping packets until forwarding is ready.
- Android always-on VPN plus “Block connections without VPN” is needed for protection when the service/process stops or after reboot. Report actual system lockdown state; a preference cannot enable or prove system protection.
- Bypass routing through a captured TUN and direct Xray outbound differs from excluding apps or destinations from VpnService. Android lockdown can block excluded traffic. Explain and test this conflict before claiming both bypass and lockdown work.
- DNS/routing JSON exports alone do not configure Android's VPN interface or enforce a Kill Switch.

## Measurements
Emit a unique session ID for each connection. Subscribe to service-confirmed state and session counters. Check `acceptNativeTraffic` before consuming events; unsubscribe and invalidate the session on disconnect. Never fall back to random telemetry in native mode.

Define received/sent bytes from the user's perspective; use consistent Xray session stats or TUN stats without summing both (double counting). Poll local counters, validate monotonic deltas and timestamps, and convert bytes/s to Mbps with `bytes * 8 / 1_000_000`, bytes to displayed MB with `bytes / 1_000_000`. Session resets must reset odometer wheels explicitly. Keep the stats API local and inaccessible to arbitrary web content.

Latency probes must make a bounded end-to-end request through each node from the Android device, with concurrency limits and cancellation. Null means unmeasured/failed; do not substitute server-side or browser HTTP latency. Record measurement age and network identity; clear stale results when networks change.

## Public directory
The web client reads FastNodes directly and parses locally. Location comes only from the source's country-specific feed, is labeled source-reported, and is not proof of exit location. The top feed has unknown locations. Personal star ratings use local device storage; they are not community ratings or endorsements. Public servers are untrusted and are never selected/connected automatically. Source failures do not restore demo nodes. No real latency is currently measured.

Source: https://github.com/rtwo2/FastNodes . Usage terms include attribution, no standalone sale of raw data, lawful use, and no warranty. Terms are retained in `docs/fastnodes-license.txt`. Third-party configurations may have additional rights/risks; aggregation is not a safety guarantee.

## Android acceptance gates (not yet run)
1. Install on a physical Android device; grant VPN consent and confirm a real exit IP via independently operated HTTPS endpoints.
2. Verify DNS, IPv4 and IPv6 leaks; TCP/UDP, network handover and reconnect.
3. Verify global, LAN/domestic bypass and ad-block policies with known destinations and packaged data files.
4. Kill Xray, kill the app, revoke network, reboot with lockdown enabled: prove no unprotected traffic escapes.
5. Validate bytes against known downloads/uploads, session resets, stale events and device network changes.
6. Confirm arbitrary web origins cannot call the native adapter and credentials never enter logs/AI requests.