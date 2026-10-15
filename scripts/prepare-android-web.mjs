import { cpSync, copyFileSync, existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const publicDirectory = join(process.cwd(), ".output", "public");
const androidDirectory = join(process.cwd(), "android-dist");
const shellCandidates = [
  join(publicDirectory, "_shell", "index.html"),
  join(publicDirectory, "_shell.html"),
  join(publicDirectory, "index.html"),
];
const shell = shellCandidates.find(existsSync);

if (!shell) {
  throw new Error(
    `Android SPA shell was not generated in ${publicDirectory}. Build with CAPACITOR_ANDROID_BUILD=true.`,
  );
}

mkdirSync(androidDirectory, { recursive: true });
cpSync(publicDirectory, androidDirectory, { recursive: true });
copyFileSync(shell, join(androidDirectory, "index.html"));
