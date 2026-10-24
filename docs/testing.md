# Testing and validation

[Documentation index](README.md) | [BareShell introduction](../README.md)

## Validation

Use the existing JVM tests, debug assembly, and Android lint from the project
root (JDK/SDK environment setup is in [Development](development.md)):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

For a focused task-model iteration:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests net.jooti.bareshell.LauncherTaskTest
```

### Release automation

Run publication/versioning tests without credentials or GitHub writes:

```powershell
node --test .github\scripts\release.test.cjs
```

These tests cover run-based versions and Android limits, checksum validation,
exact-commit tags, draft retries, upload failures, published-release reruns,
conflicting tags, and API failures. They use temporary fixtures, not real APKs.
The main-branch workflow runs them alongside the existing Android checks.

For Gradle version overrides, pass both properties, for example:

```powershell
.\gradlew.bat :app:assembleDebug -PciVersionName=0.1.123 -PciVersionCode=124
```

Also check that malformed versions, out-of-range codes, and supplying only one
property fail. Test signed release builds only with a disposable test keystore,
not the production key. Set the four signing environment variables described in
[Development](development.md), run `:app:assembleRelease`, and use SDK
`apksigner verify --verbose` and `aapt dump badging` to check the resulting APK's
signature, application ID, versionName, and versionCode. Check that partial
signing inputs fail and that debug assembly still works with those variables
unset. Remove the temporary key and clear the test environment afterward.

Workflow syntax can be checked with `actionlint` when available. Local checks
cannot verify repository permissions, tag rulesets, or Actions secrets: after
setup, confirm a real `main` run publishes both assets, verifies their checksum,
and leaves an already-published release unchanged on rerun.

On an isolated target emulator/phone:

1. Confirm BareShell appears in default Home app settings.
2. Complete setup using real swipes/shake. Check prompt order, refusal to reuse
   gestures, Back, and setup progress through a theme change or rotation.
3. Relaunch and confirm setup persists. Test each forward/reverse pair, ignored
   destination-to-destination gestures, normal list scrolling, and Android Home.
4. Check Top Apps usage access/fallback, frequent short visits versus screen time,
   all six independent list-count options, and uncapped All Apps/Search.
   Tap an app to launch it and return Home.
5. Test contacts grant, denial, permanent denial, and revocation on both contact
   pages. Star/unstar system contacts and confirm Close Contacts refreshes and
   All Contacts orders stars first. Confirm inline contact actions and app search
   remains usable without contacts access.
6. Scroll All Contacts and All Apps to reveal the end-side alphabet rail. Test letter taps,
   dragging from B to X, the Fav shortcut, and auto-hide after releasing. Verify
   rail interaction does not trigger launcher gestures. Expand a contact and
   confirm the rail remains hidden, surrounding names dim but remain tappable,
   and nested numbers, default dialer/SMS handoffs, switching, and Back work.
7. Test combined search, empty queries, automatic input focus/keyboard on entry
   (including Search as Home), Back hiding the keyboard without reopening it,
   keyboard dismissal when leaving Search, query preservation on recreation,
   and selection/replacement of the previous query on re-entry.
   Search for X with substring matches such as Box and Firefox installed: X must
   appear first, also for `x` and `  x  `. Check longer exact names and shared
   exact names across packages; retain order within exact and partial groups,
   leave contacts in their previous order, and keep blank queries empty.
   With a test app whose application and launcher activity labels differ, verify
   Top Apps (when included), All Apps, and Search use the launcher label, search
   finds that name, and tapping still opens the correct activity. Check an
   installed WebAPK separately from a website shortcut pinned in another
   launcher: the latter is not imported. Recheck after returning Home.
8. Check light/dark, large fonts, landscape, repeated Home, and restart/unlock.
9. Verify recovery through system Home settings. After authorized testing on a
   borrowed device, restore its original launcher selection and permissions.
10. Long-press blank Home space for Settings; verify moving/paused swipes and
    text/control interactions do not open it. Test confirmed/canceled reset and
    history clearing, persistence failures, and retention of unrelated settings.
11. Open contacts and use View contact from each contact surface, including
    no-phone contacts. Verify exact point increments, no points for closing,
    submenus, typing, refresh, return, or recreation; check missed-day decay,
    score-zero expiry, identity changes/deletion, and permission revocation.
12. In setup, select Tasks as Home and as a destination. Test Home-only setup,
    four-swipe capacity on single-touch/no-shake hardware, five destinations
    with multitouch or shake, Back through selection and assignment, and
    recreation during setup. Confirm All Apps/All Contacts default to Not
    included, the other non-Home pages default to Included, and Add/Remove
    choices survive Back. Upgrade a legacy setup without changing its
    Home/shortcuts; rerun setup to enable Tasks.
13. Add today, past, and future tasks, including RTL/LTR titles and multiline
    notes. Verify section ordering/counts, overdue carryover, automatic due-date
    grouping, date-picker cancellation, immutable original dates after repeated
    postponement, note-only saving, finish/cancel/reopen, and restart persistence.
14. Check single-task expansion, muted surrounding titles, keyboard/Back,
    rotation during editing and saving, draft protection on task switches,
    navigation and Android Home, explicit failure/retry feedback, dark/light
    themes, large fonts, and RTL layouts. Confirm wizard reset and clearing
    contact history preserve tasks.
    Quick actions must precede note/date details and work without opening an
    editor. Edit/Save/Discard are separate; Save returns to details. Check
    transitions while a write is pending and recreation without duplicate saves.
15. Check all five theme previews and both font samples, actual launcher/input/
    dialog colors, System night-mode changes versus forced themes, restart
    persistence, scroll position after selection, and explicit save failure/retry.
16. Assign each two-finger direction using real multitouch input; check forward
    and same-finger-count reverse navigation, staggered lifts, starts over text/
    inputs, pinches, opposing directions, a late second finger, and a third
    finger. Verify ordinary scrolling and one-finger shortcuts are unchanged.
17. Compare Top Apps and Close Contacts at short/long counts, including 20.
    The first item should remain near the one-third boundary, with the top
    area fixed during scrolling and contact expansion. Check reduced spacing
    and a usable lower viewport in landscape with large text.
18. Verify Set as default/Not now only after a successful wizard save, no repeat
    after declining or chooser cancellation, already-default suppression, Home
    launch behavior, Settings status/retry, and recreation without duplicate
    prompts. Keep the original launcher installed and untouched on shared devices.
19. Confirm separate Theme/Font headings, explanations, and independent selection.
    Inspect the logo in launcher masks and themed-icon light/dark modes; verify
    packaged identity `net.jooti.bareshell`, label BareShell, and private Settings.

Run these checks on Android 16/API 36 and Android 8.0/API 26 for the target and
minimum supported versions. Newer emulators provide additional compatibility
coverage, not proof of API 26 compatibility.
