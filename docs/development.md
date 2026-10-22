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
