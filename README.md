# Markview

**English | [简体中文](README.zh-CN.md)**

Markview is an offline Markdown viewer and editor for Windows, Android, and the browser. It combines a distraction-free reading view, side-by-side editing and preview, local drafts, and remote Markdown editing over SFTP.

The browser edition is a single, self-contained `index.html`. Styles, icons, the Markdown parser, and the sanitizer are embedded, so no server, installation, account, or network connection is required for local documents.

## Download

- [Windows x64 installer](https://github.com/shoswaller/mdView/releases/download/v1.0.0/Markview-Setup-1.0.0.exe)
- [Android APK](https://github.com/shoswaller/mdView/releases/download/v1.0.0/Markview-1.0.apk)
- [All releases](https://github.com/shoswaller/mdView/releases)

## Features

- **Read:** a clean, single-column rendered document view.
- **Edit:** Markdown source on the left and a live, read-only preview on the right. Drag the separator to resize the panes.
- **Open:** open `.md`, `.markdown`, `.mdown`, `.mkd`, or `.txt` files as UTF-8, or drop them into the window.
- **Export:** save the current document as Markdown without silently overwriting the original local file.
- **Themes:** switch between light and dark themes.
- **Local drafts:** automatically persist drafts in browser or application storage. Clearing application data, using private browsing, or changing the browser or file path may prevent recovery.
- **Markdown support:** headings, tables, read-only task lists, blockquotes, code blocks, links, images, and other common syntax. Rendered HTML is sanitized.

Keyboard shortcuts: `Ctrl / Cmd + O` opens a document and `Ctrl / Cmd + S` exports or saves it. In the editor, `Tab` indents and `Shift + Tab` outdents. Arrow keys switch tabs and adjust the pane separator when it is focused.

## SFTP Remote Files

The Windows and Android applications include an **SFTP** button. Enter a host, SSH port (22 by default), username, password, and initial directory to browse remote folders and open supported Markdown or text files.

- A server profile is saved automatically after its first successful connection. Profiles store the name, host, port, username, password, and initial directory.
- A matching host, port, and username updates the existing profile instead of creating a duplicate.
- Saved servers can be connected with one click, edited, or deleted. Leaving the password blank while editing preserves the saved password.
- On Windows, passwords are encrypted with Electron `safeStorage` and profiles are stored in the current user's application data directory.
- On Android, passwords are encrypted with AES-GCM using an Android Keystore key, and profiles are stored in private application storage.
- Passwords are never returned to the web layer and are never written to web drafts or `localStorage`.
- Android cloud backup and device transfer exclude SFTP profiles. Uninstalling the app removes its profiles and encryption key.
- If system encryption is unavailable, only non-sensitive connection details are saved and the application explains that the password was not saved.
- After opening a remote document, **Export** becomes **Save Remote**. Clicking it or pressing `Ctrl / Cmd + S` writes UTF-8 content back to the original remote path.
- Closing the application or choosing **Disconnect** ends the session but does not delete the saved server profile.
- Remote files are limited to 10 MB. The browser-only edition has no native SFTP backend and therefore hides this feature.

Only connect to servers you trust. Remote images and SFTP operations require network access; local Markdown files remain available offline.

## Browser Edition

Open `index.html` directly in a modern browser. No build step is required. Third-party license notices are retained in the HTML.

The `.work/` directory contains local development sources and verification artifacts. It is not required to use or distribute the browser edition and is excluded from Git.

## Windows Development

Requirements: Node.js and npm.

```bash
npm install
npm start
```

Build the Windows x64 NSIS installer:

```bash
npm run dist
```

The desktop entry point is `windows/main.js`, and the restricted renderer bridge is `windows/preload.js`. Generated installers are written to `release/`, which is excluded from Git.

The installed application supports Markdown file associations, native open and save dialogs, and single-instance document opening. External links open in the system browser.

## Android Development

Requirements: JDK 17, Android SDK 35, and the included Gradle wrapper.

Set `sdk.dir` in `android/local.properties`, then run:

```bash
cd android
gradlew assembleRelease
```

On Linux or macOS, use `./gradlew assembleRelease`. The APK is generated under `android/app/build/outputs/apk/release/`.

Android 6.0 (API 23) or later is supported. The app requests no storage permission and uses the system document picker for opening and exporting files.

Without a custom signing configuration, the release build uses the local Android debug certificate and is suitable for sideloaded testing. For distribution or upgrades over an existing installation, provide the original signing configuration through `MARKVIEW_KEYSTORE`, `MARKVIEW_KEYSTORE_PASSWORD`, `MARKVIEW_KEY_ALIAS`, and `MARKVIEW_KEY_PASSWORD`. Never commit signing keys or passwords.

The Android web resource is stored at `android/app/src/main/assets/index.html` and must remain synchronized with the root `index.html`.

## License

MIT
