# Development

[Documentation index](README.md) | [BareShell introduction](../README.md)

## Requirements

- Android Studio stable with its compatible bundled JDK.
- Android SDK Platform **36**, Build-Tools **36.0.0**, and Platform-Tools.
- Android SDK Command-line Tools for SDK maintenance.
- An Android emulator or USB-debugging-enabled phone running Android 8.0/API 26
  or newer. An API 37 emulator can run this API 36-targeted app.

The project uses Android Gradle Plugin 9.0.1 with built-in Kotlin and the Gradle
9.1.0 Wrapper. There is no separate Kotlin plugin or global Gradle requirement.
The namespace and application ID are `net.jooti.bareshell`. This installs
separately from the former `com.example.minihome`; data and grants are not
migrated automatically.

## Build

Open this folder in Android Studio, allow Gradle sync, and select the `app` run
configuration. Use Android Studio's bundled JDK for Gradle.

Alternatively, from PowerShell in this folder (adjust paths if installed
elsewhere):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"

.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

These environment settings apply only to the current terminal. The wrapper
downloads its pinned Gradle version on first use. The debug APK is generated at
`app\build\outputs\apk\debug\app-debug.apk`.

### Local proxy, if needed

Gradle does not automatically use Windows' proxy or the `HTTPS_PROXY` environment
variable. The original development environment used a local proxy at
`127.0.0.1:10808` for Google's Maven repository while reaching Maven Central
directly. If your network needs a proxy, adjust the following example and run
it in the same PowerShell terminal before building:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808 -Dhttp.nonProxyHosts=localhost|127.*|repo.maven.apache.org|plugins.gradle.org'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Only use this setting while that proxy is running, and adjust its address for
other machines. It is a terminal-local setting, not a project requirement.

For ADB installation and switching launchers, see [Setup and recovery](setup-and-recovery.md).

## Automated releases

[Android release](../.github/workflows/release.yml) runs on every push to `main`,
including merged pull requests. It runs JVM tests and Android lint, builds a
signed release APK, checks its signature and packaged identity/version, and
publishes a GitHub Release with generated notes and these assets:

- `BareShell-0.1.<run_number>.apk`
- `BareShell-0.1.<run_number>.apk.sha256`

There is no pull-request trigger, Play Store upload, or App Bundle. The build
uses JDK 17, SDK 36, Build-Tools 36.0.0, and the checked-in Gradle Wrapper.
The first workflow run produces `v0.1.1`, versionName `0.1.1`, and versionCode
`2`. Subsequent runs use the workflow run number for the patch and
`1 + run_number` for versionCode. Failed runs leave gaps; reruns reuse the
original number. CI supplies `ciVersionName` and `ciVersionCode` Gradle
properties together; local defaults remain `0.1` and `1`.

Do not reset the workflow's run numbering, delete/recreate its identity, or
change the version baseline without checking existing tags and installed
versionCodes. Android updates require strictly increasing versionCodes.
The maximum supported versionCode is 2,100,000,000.

### Signing setup (repository owner)

Create a dedicated release key once, or reuse the existing release key if the
app is already distributed. For a new key, run JDK `keytool` interactively:

```powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE\bareshell-release.jks" -alias bareshell -keyalg RSA -keysize 4096 -validity 10000
```

Keep the keystore outside the repository. Back up the keystore, alias, and
passwords securely offline; every update must use the same key. Losing it can
prevent updates to installed copies. Never commit keys, put passwords in command
arguments, or paste secrets into issues or logs.

In **Settings > Secrets and variables > Actions**, add repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias, e.g. `bareshell` |
| `ANDROID_KEY_PASSWORD` | Private-key password (often the store password for PKCS12) |

To copy the base64 value without printing it in terminal output:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\bareshell-release.jks")) | Set-Clipboard
```

Paste it directly into the secret, then clear the clipboard and any clipboard
history containing it. Base64 is encoding, not encryption.

Enable GitHub Actions and allow the workflow's publication job to use
`contents: write` with the built-in `GITHUB_TOKEN`. Organization policies and
tag rulesets must allow that token to create `v0.1.*` tags and releases.
No personal access token is needed. Only the publication job has write access.

Missing/invalid signing inputs fail the build; there is no debug-key fallback.
CI decodes the keystore into runner temporary storage and deletes it when the
signing step exits. Gradle accepts `ANDROID_KEYSTORE_PATH` and the three
password/alias variables for explicitly signed local release builds too.
Supplying any of those four variables requires all four. Without them, ordinary
local debug builds and unsigned release builds retain their existing behavior.

### Retries and downloads

Publication creates a tag at the triggering commit and a draft release, uploads
the APK and checksum, then publishes. Rerun a failed workflow to resume its
draft; a mismatched tag or incomplete already-published release fails explicitly.
An already-published matching release is left unchanged, not rebuilt in place.
Overlapping pushes are not canceled or coalesced. GitHub's legacy latest-release
selection uses semantic versions rather than marking every finishing job latest.

After downloading both assets, compare the APK's hash with the checksum file:

```powershell
Get-FileHash .\BareShell-0.1.1.apk -Algorithm SHA256
Get-Content .\BareShell-0.1.1.apk.sha256
```

The checksum detects download corruption; it is not independent proof of origin.
Actual hosted publication can only be confirmed after configuring secrets and
pushing/merging to `main`. Inspect the Actions run and resulting release assets.
