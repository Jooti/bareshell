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

Close Contacts reads the system's `STARRED` flag. Stars come first,
alphabetically, within the selected total count; star or unstar people in the
Contacts app, then return to BareShell to refresh. If stars fill the count,
scored non-starred people are not shown. Favorites kept privately by another
app, instead of in Android's Contacts Provider, are not accessible.
There is no in-launcher editing or favorite selection.

Remaining slots contain positive-score non-stars, ordered by score, most recent
interaction, then locale-aware name and stable identity. Opening a contact's
inline menu earns **one point**. A successful external View/Call/SMS/Telegram/
WhatsApp handoff earns **another point**. This applies in Close Contacts,
All Contacts, and Search. Closing or switching away, opening Other numbers,
typing a query, failed actions, returning, refreshing, and recreation do not
earn points. Explicitly opening another contact does.

Each elapsed **local calendar day** subtracts one point, clamped to zero.
Missed days are applied together; repeated reads on the same day do not decay
again, and a backward clock does not create negative elapsed days. Zero-score
non-stars leave Close Contacts; stars remain eligible. Decay runs on reads,
resume, foreground midnight, and date/time/time-zone changes, without a service.
Private SQLite rows store only lookup keys, points, the decay day, and the last
interaction time. Lookup reconciliation handles changed identities/merges and
prunes deleted contacts. Provider failure or denied permission does not erase
history; a history failure explicitly falls back to stars with a retry action.

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
preferences or logs. Emails, photos, and contact edits are not used.

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

Ordering gives **equal 50/50 weight** to foreground-entry frequency and total
foreground time over seven days, including use outside BareShell. For each
metric, a positive app's percentile is the fraction of positive eligible app
values less than or equal to it; zero maps to zero. The two percentiles are
averaged. Normalization happens before applying the count cap, so changing the
count does not change relative scores. Apps are deduplicated/filtered first.

Frequency uses public foreground/resume events, deduplicating consecutive events
from the same package. Screen-off, lock, and shutdown events reset that session.
This is an approximation, not an exact icon-launch count: event retention,
multiple activities, same-app returns, and multi-window can affect it. No hidden
launch-count API is used.

Time uses Android's daily usage records. Android returns whole
overlapping daily buckets, so the oldest day can include time outside a strict
rolling seven-day boundary. This is a combined usage ranking, not precise
stopwatch-style accounting.

Equal scores sort alphabetically using the current locale. Apps without history
fill remaining slots alphabetically. If fewer than the selected count of
eligible apps exist, all of them are shown.

To enable usage ranking:

1. Tap **Enable usage access** below the app list.
2. Select **BareShell** in Android's Usage access settings and allow access.
3. Return Home; the list refreshes automatically.

This is the special `PACKAGE_USAGE_STATS` access granted in Settings, not a
standard runtime permission prompt. Declaring it does not grant it automatically.
While denied or revoked, the launcher remains usable with the selected count of alphabetical apps
and the access action. Granted access with no history also uses alphabetical
ordering, but does not show the access action. Unexpected failures display a
retry message rather than silently treating failed queries as empty history.

Application names launch their apps when tapped. Page data refreshes on resume
and Home re-entry, including after returning from an app or Settings. All Apps
and app search reuse the same app eligibility rules without the Top Apps limit
or requiring Usage access. The manifest declares usage-stats and contacts-read
access plus narrow visibility for MAIN/LAUNCHER apps.
