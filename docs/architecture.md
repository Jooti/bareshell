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
| `WelcomeOverlay` | Isolated, presentation-only sample pages and first-use swipe practice with accessible controls. |
| `WelcomeStep` | Guided Apps → Contacts → shortcut-map progression, expected gestures, and Back behavior. |
| `DefaultHome` | Actual Home role/default detection and Android chooser/settings handoff. |
| `LauncherConfiguration`, `ConfigurationStore` | Typed pages/gestures, wizard progression, validated private configuration. |
| `GestureInput`, `GestureRecognition` | Blank-space hit testing, pointer tracking, swipes, and optional foreground shake. |
| `LauncherPages`, `AlphabetIndex` | Cached text lists, Search input/results, mutually exclusive details/hold options, and alphabet rails. |
| `AppRepository`, `AppRankingStore`, ranking/frequency models | Eligible apps, additive public usage points, and private package adjustments. |
| `ContactRepository`, catalog/actions/history models | Permission-aware contacts, validated handoffs, local interaction scoring. |
| `TasksPage`, `LauncherTask`, `TaskStore` | Compact task details/actions, separate drafts, pure transition rules, revision-checked SQLite. |
| `CalendarPage`, `CalendarRepository`, `CalendarModel`, `CalendarDates` | Native agenda/editor, permission-aware provider instances and writes, pure local-day grouping, ICU date display/input. |
| `PageCache`, `PageCacheStore` | Cache keys, invalidation, and private app/contact snapshots. |
| `SettingsActivity`, `SettingsStore`, `LauncherSettings` | Retained settings work, durable preferences, separate theme/font controls. |
| `LauncherAppearance`, `AppearancePalette`, `TextViews` | Native themes, previews, palette resolution, and shared text/touch styling. |

## Work and lifecycle

Catalog/filter work is off the UI thread. Activity-owned generation checks
prevent obsolete results from updating the current page. Contact details are
loaded lazily and revalidated after external returns.

App/contact catalog work and ranking writes share a serial worker across
Activity recreation. Accepted adjustments are not replayed, and pending writes
disable ranking options until persistence completes. Signed `RankingAdjustment`
balances decay toward zero by local calendar day, independently of contact tap
points. Contact-history schema version 2 adds manual fields without replacing
old interaction data. Lookup reconciliation merges both balances and preserves
negative adjustments until exhausted.

Close Contacts defers ranked-page refreshes while any expansion is open, while
still saving tap points and revalidating the selected contact. The held list
survives recreation as transient Activity state and is never restored without
contacts permission. Closing the expansion or leaving the page applies the
latest ranking. Hold actions dismiss options before writes or external handoffs;
View/Edit revalidation remains generation-checked after dismissal.

Tasks use a shared serial worker so reads after recreation follow pending
writes. New tasks carry a stable UUID, and updates check the previous revision
inside a transaction. Pending task/action state survives recreation; a read
verifies the result instead of blindly repeating an insert or state transition.
Selection is independent of edit mode. Quick actions use saved title/note data;
edits require Save or Discard and retain navigation protection.

Settings retains application-context work across recreation. A successful
durable save updates the UI and broadcasts personalization changes within the
package. Failed writes keep the previous appearance and report failure.

Calendar provider work shares a serial application-context worker. Page
generations discard obsolete results. Provider observation is foreground-only;
resume, provider changes, local midnight, and clock/zone changes refresh the
agenda. Instances expand recurrence; date-only all-day boundaries use UTC,
while timed events group in the device zone. ICU calendar choice changes only
date presentation/input, not absolute event instants.

Calendar selection stores a list of provider/account identities and a separate
write default. Legacy single-calendar preferences migrate to that one-item list
and write default without changing the selected account. Instances queries merge
selected calendars before day grouping or the seven-section limit. Heading format
and weekday style are independent settings; editor dates retain full context.

Calendar drafts are separate from fresh event details and survive activity
state restoration. Ordinary writes revalidate the event/calendar and use
conditional updates against editable baseline fields. Pending write IDs retain
bounded in-memory outcomes across recreation, preventing automatic replay;
lost/unverified outcomes require explicit user recovery rather than duplicate
inserts. Calendar contents have no persisted launcher cache. Permission
revocation clears rows/drafts; recurring events use the external editor.

Fresh installations atomically store the canonical default configuration and a
pending-introduction flag on the catalog worker. Completed existing layouts
without this flag never receive the tour. The welcome overlay navigates with the
real contact shortcut for its two practice steps, retains its own step, and never writes
sample data or invokes live page actions. Completion replaces the introduction
flag with a pending default-Home offer; dismissing the offer clears that flag
durably. This survives process death without repeating a completed introduction.

Configuration parsing strictly validates these optional Boolean metadata keys.
An exact, durable `setup_requested` marker distinguishes deliberate wizard reset
from an empty first-use store. Configuration writes serialize across Activity
instances and restore the previous SharedPreferences memory state on failure.

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

Numeric ranking uses a new cache-key version. Ranked keys include the local day;
successful score writes advance the affected ranked page epoch. Cache writes
check the captured epoch under the same lock as invalidation so obsolete work
cannot repopulate cleared snapshots. Refreshes preserve visible rows and active
options/details where possible, but never preserve contacts after revocation.

## Compatibility boundaries

Home selection inside the wizard is not Android's default-Home choice.
`RoleManager.ROLE_HOME` is used on API 29+ where supported, with Home settings
for older/unsupported devices. Android owns the selection and cancellation.
The manifest exposes only the launcher Activity; Settings remains private.

The seven page identifiers, additive gesture identifiers, task schema, date-only
grouping, immutable original dates, and revision semantics are intentional data
contracts. Keep them stable unless a change explicitly includes migration.
BareShell's package rename is a separate installation, not such a migration.
Calendar is appended without renaming existing identifiers. Version-1 layouts
retain the original five-page universe; existing version-2 layouts never gain
new bindings automatically. Only fresh defaults assign Calendar to Up.
