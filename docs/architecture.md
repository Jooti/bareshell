# Architecture

[Documentation index](README.md) | [Contribution guide](contributing.md)

BareShell uses native Android Activities and Views, Kotlin, SQLite, and private
preferences. There is no UI framework, network client, or background service.
The application ID and namespace are `net.jooti.bareshell`.

## Source map

Main Kotlin sources live in
[`app/src/main/java/net/jooti/bareshell`](../app/src/main/java/net/jooti/bareshell);
JVM tests mirror that package in
[`app/src/test/java/net/jooti/bareshell`](../app/src/test/java/net/jooti/bareshell).

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Wizard/page lifecycle, navigation, async catalogs, Search focus, permission return, and default-Home offer. |
| `DefaultHome` | Actual Home role/default detection and Android chooser/settings handoff. |
| `LauncherConfiguration`, `ConfigurationStore` | Typed pages/gestures, wizard progression, validated private configuration. |
| `GestureInput`, `GestureRecognition` | Blank-space hit testing, pointer tracking, swipes, and optional foreground shake. |
| `LauncherPages`, `AlphabetIndex` | Cached text lists, Search input/results, contact expansion, and alphabet rails. |
| `AppRepository`, ranking/frequency models | Eligible apps and public usage-based ranking. |
| `ContactRepository`, catalog/actions/history models | Permission-aware contacts, validated handoffs, local interaction scoring. |
| `TasksPage`, `LauncherTask`, `TaskStore` | Compact task details/actions, separate drafts, pure transition rules, revision-checked SQLite. |
| `PageCache`, `PageCacheStore` | Cache keys, invalidation, and private app/contact snapshots. |
| `SettingsActivity`, `SettingsStore`, `LauncherSettings` | Retained settings work, durable preferences, separate theme/font controls. |
| `LauncherAppearance`, `AppearancePalette`, `TextViews` | Native themes, previews, palette resolution, and shared text/touch styling. |

## Work and lifecycle

Catalog/filter work is off the UI thread. Activity-owned generation checks
prevent obsolete results from updating the current page. Contact details are
loaded lazily and revalidated after external returns.

Tasks use a shared serial worker so reads after recreation follow pending
writes. New tasks carry a stable UUID, and updates check the previous revision
inside a transaction. Pending task/action state survives recreation; a read
verifies the result instead of blindly repeating an insert or state transition.
Selection is independent of edit mode. Quick actions use saved title/note data;
edits require Save or Discard and retain navigation protection.

Settings retains application-context work across recreation. A successful
durable save updates the UI and broadcasts personalization changes within the
package. Failed writes keep the previous appearance and report failure.

## Cached-first rendering

Successful app/contact snapshots are private on-device files; they make cold
starts and page returns immediate while data refreshes. Keys account for locale,
relevant count, permissions, and invalidation epoch. Revoked contacts access must
not expose cached contacts; clearing history invalidates contact snapshots.
Contact actions are always loaded fresh. Search reuses its catalog and recent
query results; tasks reuse an in-memory snapshot and refresh from SQLite.
First use without a snapshot still loads normally. Refresh failures remain
visible rather than masquerading as a successful empty list. Snapshots are
excluded from backup.

## Compatibility boundaries

Home selection inside the wizard is not Android's default-Home choice.
`RoleManager.ROLE_HOME` is used on API 29+ where supported, with Home settings
for older/unsupported devices. Android owns the selection and cancellation.
The manifest exposes only the launcher Activity; Settings remains private.

The six page identifiers, additive gesture identifiers, task schema, date-only
grouping, immutable original dates, and revision semantics are intentional data
contracts. Keep them stable unless a change explicitly includes migration.
BareShell's package rename is a separate installation, not such a migration.
