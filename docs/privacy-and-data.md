# Privacy, data, and ranking

[Documentation index](README.md) | [BareShell introduction](../README.md)

## Local storage and installation identity

BareShell's application ID is `net.jooti.bareshell`. It does not import, erase,
or change data/permissions/default selection belonging to the former Mini Home
package (`com.example.minihome`). Android's default Home chooser requires an
explicit user selection, not a permission automatically granted during setup.

Tasks store a title, note, scheduled day, immutable original day, status, and
revision/history metadata in private SQLite. Preferences store wizard and
appearance/list-count choices. Private app/contact snapshots support cached-first
rendering; contact actions remain in memory and are loaded fresh. Locale,
permission, count, and history invalidation prevent reuse of inappropriate
snapshots. Tasks, preferences, scores, and snapshots are excluded from backup/
device transfer. There is no network sync, reminder service, or data export.

## Contacts access and privacy

Close Contacts ranks the sum of **50 points for a system star**, local interaction
points, and a signed manual adjustment. Only positive totals are included,
including for stars. People with more than 50 interaction/adjustment points can
outrank a star. Ties use most recent interaction, then locale-aware name and
stable identity, before applying the selected count cap. Star/unstar people in
the Contacts app, then return to refresh. Favorites kept privately by another
app, instead of Android's Contacts Provider, are not accessible.

Opening a contact's normal inline details earns **one point**. A successful
external View/Edit/Call/SMS/Telegram/WhatsApp handoff earns **another point**.
This applies in Close Contacts,
All Contacts, and Search. Closing or switching away, opening Other numbers,
typing a query, failed actions, returning, refreshing, and recreation do not
earn points. Explicitly opening another contact does.

Each elapsed **local calendar day** subtracts one interaction point, clamped to zero.
Missed days are applied together; repeated reads on the same day do not decay
again, and a backward clock does not create negative elapsed days. Zero-score
contacts leave Close Contacts unless their star bonus or manual balance keeps
the total positive. Decay runs on reads,
resume, foreground midnight, and date/time/time-zone changes, without a service.
Private SQLite rows store only lookup keys, interaction points/day, manual
points/day, and the last interaction time. Existing interaction history is
preserved when upgrading. Lookup reconciliation handles changed identities/merges and
prunes deleted contacts. Provider failure or denied permission does not erase
history; a history failure shows an unavailable/retry action rather than
discarding manual penalties and claiming a successful stars-only ranking.

Hold any contact name for inline **View, Edit, Up, Down** options instead of
normal call/message details. View and Edit open the system contact handler;
there is no internal editor or WRITE_CONTACTS permission. Up adds 10 and Down
subtracts 10 from one signed balance. Holds, closing options, and Up/Down earn
no interaction points and do not change interaction recency. Scores stay hidden.
Selecting any option closes the hold menu. Interaction points are saved at once,
but Close Contacts defers reordered rows until its expansion closes or the user
leaves the page; selecting another person keeps the current order stable.
Positive and negative manual balances move toward zero by one point per elapsed
local calendar day; repeated adjustments accumulate or cancel, rather than
decaying independently. Missed decay is applied before adjustments; a backward
clock cannot restart decay. Negative balances remain stored until exhausted.
Clearing contact history removes tap points and contact manual balances,
not system stars, app adjustments, or tasks.

All Contacts puts that starred group first and sorts names alphabetically within
each group. This is **favorites-first, not frequency ranking**: Android 10+
removed public contact-interaction counters and frequent-contact queries.
[Android's contacts-affinity restrictions](https://source.android.com/docs/core/permissions/contacts-affinities)
explain why the launcher does not use those deprecated fields. Contact search
keeps its global alphabetical ordering, independently of stars.

While scrolling All Contacts, an alphabet rail appears at the screen's end edge
(right in left-to-right layouts). Tap or drag to jump between sections:

- **Fav** jumps to the starred group, when one exists.
- Letters jump to the first matching **non-starred** contact. Only sections
  present in the list are shown, using Android's locale-aware alphabet buckets.
- **#** covers names outside those alphabet buckets, such as numeric names.

The rail stays visible during scrolling or dragging and fades after a 1.5-second
pause. All Apps uses the same rail, without the Fav shortcut. It is a separate
touch control, not a launcher-swipe surface. Other blank areas still accept
launcher gestures.

Close Contacts, All Contacts, and Search show **Allow contacts** when access is missing. Only
tapping that action requests Android's `READ_CONTACTS` permission. Setup and the
other pages do not request it. After permanent denial, the text action opens the
app's system permission settings. Permission changes are checked on return.

Denying or revoking access leaves an explanation and does not disable app search.
Provider errors offer a retry rather than pretending the address book is empty.
The list reads only aggregated contact identities, display names, and the
system-starred flag. Phone numbers, labels/default flags, and supported messenger
data rows are read lazily for the selected contact. Same-named contacts retain
their distinct identities. Numbers and action details stay in memory, not saved
preferences or logs. Emails and photos are not used; edits happen in the
external Contacts app.

Address-book contents, phone numbers, usage history, and search queries are not
copied into the score database or uploaded. Scores describe interest inside
BareShell, not completed calls/messages or activity in other apps. Local
preferences and the score database are excluded from backup and transfer.
The app requests no `WRITE_CONTACTS`, calling, SMS, networking, or
`QUERY_ALL_PACKAGES` permission. Shake uses the optional accelerometer only while
the launcher is foregrounded and setup or a shake binding needs it.

## Top Apps ordering and usage access

Top Apps shows up to the selected count of enabled, launchable applications for the current
Android user. It includes launchable system apps, excludes itself, and shows one
entry per package even when an app has multiple launcher activities. Work profiles
and other users are not included.

App names use Android's resolved launcher activity label, falling back to the
application label when no activity label is supplied. Top Apps, All Apps, and
Search share these names and eligibility rules.

Installed PWAs packaged as WebAPKs are eligible when they expose an enabled,
exported MAIN/LAUNCHER activity for the current user. A website shortcut saved on
another launcher's home screen is not necessarily an installed app. BareShell
does not discover or import those launcher-only shortcuts. Likewise, additional
named launcher activities in the same package do not get separate app rows.
An item appearing in another launcher therefore does not guarantee it appears
in BareShell's app lists or Search.

Ordering adds **one point per foreground entry** over seven days, **one point
per complete ten minutes of accumulated foreground time**, and the app's signed
manual balance. Usage outside BareShell is included. Sum all daily milliseconds
before dividing by 600,000, so partial minutes carry across daily buckets.
Scores are numeric internally but never shown. Apps are deduplicated/filtered
first, and the count cap is applied after sorting.

Frequency uses public foreground/resume events, deduplicating consecutive events
from the same package. Screen-off, lock, and shutdown events reset that session.
This is an approximation, not an exact icon-launch count: event retention,
multiple activities, same-app returns, and multi-window can affect it. No hidden
launch-count API is used.

Time uses Android's daily usage records. Android returns whole
overlapping daily buckets, so the oldest day can include time outside a strict
rolling seven-day boundary. This is a combined usage ranking, not precise
stopwatch-style accounting.

Hold an app name in Top Apps, All Apps, or Search for inline **App Info, Up,
Down**. App Info opens Android's settings for that package. Up/Down adjusts its
balance by +/-10, with the same one-point-per-local-day decay toward zero as
contacts. Private SQLite stores package names, balances, and decay days, not
usage records. Removed apps are pruned only after successful discovery.

Equal scores sort alphabetically using the current locale. Apps without history
or adjustments fill remaining slots alphabetically; negative totals remain
eligible behind higher totals. If fewer than the selected count of
eligible apps exist, all of them are shown.

To enable usage ranking:

1. Tap **Enable usage access** below the app list.
2. Select **BareShell** in Android's Usage access settings and allow access.
3. Return Home; the list refreshes automatically.

This is the special `PACKAGE_USAGE_STATS` access granted in Settings, not a
standard runtime permission prompt. Declaring it does not grant it automatically.
While denied or revoked, the launcher remains usable with manual-adjustment
ranking (alphabetical if no adjustments) and the access action.
Granted access with no history or adjustments also uses alphabetical
ordering, but does not show the access action. Unexpected failures display a
retry message rather than silently treating failed queries as empty history.

Application names launch their apps when tapped. Page data refreshes on resume
and Home re-entry, including after returning from an app or Settings. All Apps
and app search reuse the same app eligibility rules without the Top Apps limit
or requiring Usage access. The manifest declares usage-stats and contacts-read
access plus narrow visibility for MAIN/LAUNCHER apps.
