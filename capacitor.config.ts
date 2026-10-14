import type { CapacitorConfig } from "@capacitor/cli";

const appUrl = process.env.ANDROID_APP_URL;

if (process.env.CI === "true" && !appUrl) {
  throw new Error("Set the ANDROID_APP_URL repository variable before building Android releases.");
}

let server: CapacitorConfig["server"];

if (appUrl) {
  const url = new URL(appUrl);
  if (url.protocol !== "https:") {
    throw new Error("ANDROID_APP_URL must use HTTPS.");
  }
  if (url.pathname !== "/" || url.search || url.hash) {
    throw new Error("ANDROID_APP_URL must be an HTTPS origin without a path, query, or fragment.");
  }
  server = { url: url.origin, cleartext: false, allowNavigation: [url.host] };
}

const config: CapacitorConfig = {
  appId: "com.artesys.netcon",
  appName: "Netcon",
  webDir: "android-web",
  server,
};

export default config;