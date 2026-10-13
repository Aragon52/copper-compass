import { makeConfig, type ClientSettings, type ServerNode } from './v2ray';

// Contract for an external Android companion. No native implementation is bundled.
export type NativeSession = { sessionId: string; state: 'connecting' | 'connected' | 'disconnected' | 'blocked'; lockdownEnabled: boolean };
export type NativeTraffic = { sessionId: string; elapsedSeconds: number; receivedBytes: number; sentBytes: number; downloadBytesPerSecond: number; uploadBytesPerSecond: number; latencyMs: number | null };
export interface NativeVpnAdapter {
  requestConsent(): Promise<boolean>;
  connect(request: { config: ReturnType<typeof makeConfig>; killSwitch: boolean }): Promise<NativeSession>;
  disconnect(): Promise<void>;
  probe(node: ServerNode): Promise<{ latencyMs: number | null; testedAt: string }>;
  subscribe(listener: (event: NativeSession | NativeTraffic) => void): Promise<() => void>;
}
export function nativeConnectRequest(node: ServerNode, settings: ClientSettings) {
  return { config: makeConfig(node, settings), killSwitch: settings.killSwitch };
}
export const unavailableNativeVpn = {
  available: false as const,
  async connect(): Promise<never> { throw new Error('Native Android VPN service is not installed. No device traffic is protected.'); },
  async probe(): Promise<never> { throw new Error('Latency measurement requires the native Android VPN service.'); },
};
export function acceptNativeTraffic(event: NativeTraffic, currentSessionId: string | null, connected: boolean): boolean {
  return connected && currentSessionId === event.sessionId && [event.elapsedSeconds, event.receivedBytes, event.sentBytes, event.downloadBytesPerSecond, event.uploadBytesPerSecond].every(n => Number.isFinite(n) && n >= 0) && (event.latencyMs === null || Number.isFinite(event.latencyMs) && event.latencyMs >= 0);
}