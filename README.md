# Chaos Mind Controller 0.1.0 — experimental test build

Target: Paper 1.21.11, Java 21. This is a **source project**, not a tested server plugin JAR.

## Build
Install JDK 21 and Gradle 8.14+ (or a compatible Gradle 9.x). In this folder run:

    gradle build

The resulting JAR will be in `build/libs/`. Upload it to your server `plugins/` folder and restart the server. Test in a safe area, preferably on a staging server.

## Commands
- `/mindpossess` — aim directly at a mob within 30 blocks, then possess it.
- `/mindrelease` — return to your original position and gamemode.

While possessing, WASD and Space are intended to move/jump the mob; Shift attempts a close-range attack. This is **not yet mouse-look control or left-click attacks**, and is not integrated with `/chaosmind`. Vanilla spectator input/camera restrictions may still prevent it from working; test before integration.

## Notes
- Do not delete or replace the original `chaos_mind.sk`.
- Mob AI is temporarily disabled and restored upon release.
- This test version does not support mind switching, player possession, or through-wall targeting.
- Do not use `/reload` on the server; restart to unload the plugin safely.

## Build without installing Gradle (GitHub Actions)
Upload this project to a GitHub repository, including `.github/workflows/build.yml`.
Open **Actions** > **Build Chaos Mind Controller** > **Run workflow**.
When it finishes, download the `ChaosMindController-jar` artifact and extract the plugin JAR.
The workflow compiles against the Paper 1.21.11 API using Java 21.

This project has not yet been compiled or tested on a Paper server. Do not replace the working Skript file.
