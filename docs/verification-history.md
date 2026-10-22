# Verification history

[Documentation index](README.md) | [BareShell introduction](../README.md)

These records preserve observed coverage and limitations, not guarantees about
every device or later build. Use [Testing and validation](testing.md) for current
commands and acceptance checks. Package names and device-state statements in
the Mini Home section below are historical.

## BareShell identity, task actions, and appearance grouping

- **122 JVM tests** passed, along with normal debug assembly and Android lint:
  zero errors and the existing API-target warning. Packaged metadata and native
  resource loading confirmed label **BareShell**, application identity
  **net.jooti.bareshell**, the new launcher component, adaptive icon,
  monochrome layer, and legacy logo.
- A disposable API 37 emulator verified Home-only wizard completion, the
  post-save default-Home offer through rotation, Not now with no restart nag,
  Settings chooser cancellation/retry, explicit Home-role selection, actual
  Home-key launch, and suppression when already default.
- Actual task UI checks covered compact actions before note/date details,
  Save returning to details without an editor, Postpone/Cancel/Finish from
  Postponed, Reopen, immutable original dates, saved-note retention, separate
  editing, dirty-draft protection, rotation, and canceled date picking.
  An injected SQLite update failure left the saved state unchanged and showed
  explicit error text; retry after removing the fixture was durable.
- Dependency-free temporary Android instrumentation held a SQLite write lock
  during creation and quick Finish, recreating the Activity while each write
  was pending. Both recovered correctly with exactly one created record and
  one transition revision. These harnesses were verification fixtures, not
  new project dependencies.
- RTL/multiline notes, overdue grouping, Green/Gray/Dark appearances, and
  200%-font portrait/landscape layouts were checked. Quick controls stacked
  in narrow layouts and stayed reachable by scrolling. Separate Theme and
  Font headings were verified, and changing either preserved the other's
  saved value. Search retained focus, its 24dp keyboard gap, and select-all
  replacement; favorite reach and task/Home swipe navigation still worked.
- Native rendering checked the new logo with adaptive masks, themed light/dark
  colors, and legacy rendering. A pixel-distance check verified the foreground
  stays away from the circular mask edge.
- All 42 local documentation link targets resolved across nine Markdown files.
  The original Mini Home installation/shared device was not modified.
  The disposable emulator, temporary instrumentation packages, and all helper/
  database/screenshot fixtures were removed after verification.
  API 26/36 devices and vendor-specific chooser behavior remain unverified.

## Mini Home checks before the BareShell rename

### Pre-rename local verification

- Appearance, gesture, configuration, and settings changes passed **45 focused
  tests**, normal debug assembly, and lint (zero errors; the existing SDK-target
  warning). Tests include text contrast, gray luminance, native-theme/preview
  palette consistency, favorite viewport geometry, pointer-stream rejection,
  same-finger-count reverse navigation, and full-list default exclusions for
  every Home choice. An isolated API 37 emulator verified all five selectable
  themes, real swatches, both fonts, System light/dark switching, native gray
  dialogs and green date picking, saved selection after restart, and explicit
  appearance-write failure followed by a successful persistent retry. Actual
  two-pointer streams assigned a wizard shortcut, navigated in all four
  directions, and respected blank-space starts. All Apps/All Contacts defaulted
  to Not included, could be added, and retained explicit choices on Back; an
  existing completed setup was unchanged by upgrading. Top Apps limits of
  5/20/50 and 20 seeded Close Contacts retained the same one-third boundary,
  including scrolling and contact expansion. A 200%-font landscape layout
  retained a usable list viewport. Search still focused/opened the keyboard
  with its 24dp gap and replaced a selected previous query on re-entry; a task
  draft survived a system-theme recreation. The disposable emulator and all
  fixtures were removed without touching the original launcher or its device.
  API 26/36 device checks remain outstanding.
- Tasks, optional wizard destinations, and cached-first pages passed **41
  focused configuration/task/cache tests**, debug assembly, and lint (only the
  existing SDK-target warning). An isolated API 37 test package verified task
  creation, overdue grouping, notes, finish/cancel/reopen, future-only
  postponement, original-date retention, restart persistence, draft protection
  through rotation and Android Home, and recovery from injected read/write
  failures. A deliberately delayed insert survived recreation without creating
  a duplicate on the next Save. Native RTL/multiline editing, dark appearance,
  larger text, legacy setup preservation, Tasks destination selection,
  duplicate-gesture feedback, reverse navigation, ranked Home snapshots after
  process restart, and cache invalidation on contact-history clearing were also
  checked. Wizard reset and history clearing retained tasks. The original
  launcher data was untouched; the isolated test app and fixtures were removed.
  API 26/36 device checks remain part of the acceptance checklist above.
- Settings/personalization passed **85 unit tests**, debug assembly, and lint
  (only the intentional SDK-target warning). API 37 emulator checks verified
  Home-only blank-space Settings access, all six app-count choices, independent
  saved counts, a five-row Top Apps limit with uncapped All Apps, slow/paused
  swipes, canceled/confirmed wizard reset, explicit count/reset write failures, and
  renamed wizard choices/descriptions using retained counts.
- Real contact-view handoffs opened the correct system contact from Close
  Contacts, All Contacts, and Search, including a contact without a number.
  SQLite inspection verified exact menu/handoff points, no extra points for
  close/submenu/search typing/return/theme recreation, score ordering after
  stars, persisted restart behavior, two missed days, same-day idempotence,
  and zero-score expiry. Canceled/confirmed history clearing preserved system
  stars. Settings also survived dark, 200%-font landscape recreation.
- Provider-side merges combined two lookup-score records correctly; deleting
  the merged person pruned its history. Revoking contacts access and making
  history read-only did not erase scores. Read-only storage showed an explicit
  stars-only fallback. Wizard reset preserved both existing contact history
  and independent count settings.
- The redesigned five-step wizard passed **28 targeted configuration/gesture
  tests**, debug assembly, and lint (only the intentional SDK-target warning).
  API 37 emulator checks covered all five described Home choices, disabled-motion
  input, real slow swipes, duplicate-owner feedback, assignment confirmation,
  visible/system Back equivalence, theme/rotation state, large-font dark
  landscape, and actual shake assignment. Illustration-region pixel comparisons
  verified animation after pause/resume. A temporary write restriction verified
  explicit save failure, successful Retry, automatic Home entry, and persistence.
  The user's completed configuration was restored exactly; permissions were not
  changed. No extra review screen or tap-to-assign gesture path was introduced.
- Inline contact actions passed **64 unit tests**, debug assembly, and Android
  lint. On the API 37 emulator, synthetic contacts verified preferred-number
  selection, formatting deduplication, nested labeled Call/SMS actions, contact
  switching/toggle, anchored push-down layout, and explicit no-action status.
  Screenshot pixel checks confirmed normal black versus muted `#666666` in
  light mode, including Search app results, and two indentation levels.
  Keyboard-first Back, query-change collapse, default dialer recipient handoff,
  SMS app handoff, and expansion preservation on external return were checked
  without placing calls or sending messages.
- Both alphabet rails passed drag/jump and inactivity checks. Contact expansion
  kept the rail hidden during scrolling. Rotation/theme recreation preserved
  the contact and number submenu; dark screenshots confirmed white selected
  text versus muted `#999999`. Actions remained scrollable at 200% font in dark
  landscape. Deleting a selected contact or revoking permission removed stale
  private action rows on refresh.
- Standard Telegram and WhatsApp are absent from the development emulator.
  Unsupported/uninstalled messaging actions were verified hidden, even with a
  synthetic provider row; actual account-specific messaging/calling handoffs
  still require testing with those installed apps and real synced accounts.
- The contacts/index follow-up passed **42 targeted contact, search, gesture,
  and configuration tests**, debug assembly, and Android lint using SDK 36.
  Ranking and name-search suites also passed during the preceding wizard
  milestone. Coverage includes configuration validation/serialization, wizard
  ordering/Back, all Home choices, contextual reverse navigation, swipe
  distance/direction thresholds, shake settling/cooldown, name search,
  starred-first ordering, stable contact identities, and alphabet-section jumps.
- On the API 37 emulator, provider fixtures verified seven favorites (not capped
  at five), starred-first grouping, live star changes, the no-favorites state,
  and permission denial/grant/revocation. The index revealed on scroll, jumped
  from non-starred B to X by dragging, supported letter taps and the Fav shortcut,
  and faded after inactivity. Contact Search kept global alphabetical order.
  Index navigation also passed at 200% font in dark landscape.
- Verified slow swipes in all four directions below wizard content, blank
  space beside text and app names, a pause before releasing, and fast swipes.
  Blank taps, short movements, and ambiguous diagonals did not navigate.
  List text still scrolled, app-name taps launched apps, and Search accepted
  input normally.
- On the Android 17/API 37 emulator, verified five clickable text-only rows,
  denied/granted/revoked usage access, opening actual Usage access settings,
  real usage ranking, tapping an app to launch it, and returning Home.
- Verified the first-run wizard, prompt order/fades, duplicate-gesture rejection,
  Back, theme-change progress, completed-setting persistence after force-stop,
  and different Home selections. Real virtual-accelerometer shakes assigned a
  page, opened it, and returned Home. Other gestures could not jump between
  destination pages, and list scrolling did not trigger navigation.
- Verified full All Apps, contact permission
  denial/permanent denial/settings/grant/revocation, text-only
  contacts, combined grouped search, empty queries, keyboard-only-on-tap,
  keyboard-first Back, query preservation on rotation, and app search after
  revoking contacts access.
- Verified both system light/dark appearances, including exact background colors
  and visible contrasting system bars. Also checked 200% font size in landscape,
  scrolling to the access action, cold launch, and repeated Home with one Activity.
- Temporary contacts and test data were removed; user configuration,
  permissions, and appearance were restored. Existing Usage access was
  preserved. Mini Home remains the default Home app with the user's completed
  Home/gesture configuration, not an unfinished or test setup.
- Android 16 phone, API 26 emulator, and reboot/unlock checks have not yet been
  performed; those devices/images were not available for this verification.
- Lint has no errors and one warning: targeting API 36 rather than the newest
  Android version, intentionally retaining the selected SDK. Legacy and modern
  backup rules exclude saved preferences and the contact-score database.
