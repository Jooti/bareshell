# Setup, navigation, and recovery

[Documentation index](README.md) | [BareShell introduction](../README.md)

## First-run setup

Without a complete, supported saved configuration, the wizard starts with
Home selection:

> Choose your Home screen

Choose **Top Apps**, **Close Contacts**, **All Apps**, **All Contacts**, **Search**,
or **Tasks**.
Each option includes a short description. Home is what appears when you press
Android Home; choosing it does not grant permissions or change Android's default
launcher selection. Usage and contacts access can be enabled later.

Next, select which other pages to enable. Unless chosen as Home, **All Apps** and
**All Contacts** start under **Not included**, while the other non-Home pages start under **Included
pages**, within the device's gesture capacity. Each group shows its count.
Use **Add** or **Remove** beside a page to move it between groups; unselected
pages stay disabled, and Home alone is also supported. Returning from gesture
assignment preserves your choices rather than restoring the defaults.
Continue to assign each selected page a unique
gesture in the order above. The step count follows your selection:

> How will you open {page}?

An animated phone illustration demonstrates unused gestures, with a labeled
area where you can perform a real swipe. The examples are not buttons and never
assign a gesture for you. Animation stops in the background; disabling system
animations shows static illustrations and keeps setup immediately usable.

Perform a swipe in the illustrated area or **any blank area of the screen**,
or gently shake the actual device. Swipe across the space, then lift your finger;
slow swipes work too. Swipe over text to scroll the instructions instead.
Each destination needs a different gesture. Available
gestures are left, right, up, and down with one finger, the same four directions
with two fingers on multitouch devices, and shake when an accelerometer is
available. Devices with only four single-finger swipes can enable up to four
destinations; multitouch or shake support allows all five. Inline feedback names the existing destination if
you reuse a gesture. The next step confirms your assignment, and a setup summary
lists your chosen Home and shortcuts. Tips explain opposite-swipe/shake-again
return navigation and that shortcuts open destinations from Home.

Both the visible **Back** text action and Android Back revisit the previous step.
Rotation/theme changes preserve progress. Long instructions scroll and follow
the chosen appearance and system font size.
The final gesture saves the complete configuration and opens the chosen Home
(for Home-only setups, Continue saves immediately).
There is no extra required setup review or confirmation screen.
After successful setup, **Set as default Home** / **Not now** offers Android's
Home chooser unless BareShell is already the default. Declining or canceling
does not repeat the offer on later launches. Settings always provides the action;
completing a reset wizard may offer it again.
Incomplete setup is not stored as a completed configuration and may restart after
a fresh launch. Save failures offer a retry instead of reporting success.

The completed Home and gesture settings are stored privately on this device.
Cloud backup is disabled, and modern cloud/device-transfer rules exclude the
saved preferences, local contact-score database, and task database from transfer.
Invalid, incomplete, or unsupported gesture settings require setup again.
Existing completed setups keep their Home and bindings when page labels change
or Tasks is added. Use **Reset wizard** in Settings to enable Tasks; the update
does not silently add or remap a shortcut.
BareShell uses `net.jooti.bareshell` and installs separately from Mini Home.
Its old `com.example.minihome` data, grants, and launcher selection are untouched.

This wizard chooses **BareShell's Home page**, not Android's default launcher.
Android's separate default Home app selection is described below.

## Pages and navigation

| Page | Contents |
| --- | --- |
| Top Apps | Up to the selected count (default 10), balancing opening frequency and foreground time; tap to launch. |
| Close Contacts | Up to the selected count (default 10): system stars first, then locally scored contacts; tap for inline actions. |
| All Apps | Full alphabetical list of launchable app names; tap to launch. Scrolling reveals an alphabet rail. |
| All Contacts | System-starred contacts first, alphabetical within each group; tap for inline actions. |
| Search | App/contact display-name matches as you type; apps first, contacts second. |
| Tasks | Doing, Postponed, Finished, and Canceled tasks, with inline editing and actions. |

Navigation swipes can start in **any blank area**, including below content,
beside text, and in the empty space above or below a short list.
Move at least 40dp in a clear direction and release; slow drags and a pause before
release work too, without needing a fast fling. Taps, short movements, ambiguous
diagonals, canceled touches, and gestures with three or more fingers do not navigate.
Two-finger shortcuts require both fingers to start in blank space and move at
least 40dp in the same clear direction. Pinches, opposing directions, and adding
a second finger after a one-finger swipe is established are ignored. Lift both
fingers to finish. Accessibility services can reserve two-finger gestures;
use single-finger swipes or shake if those gestures are unavailable.
Swiping over the text itself scrolls lists or wizard content instead. Text names
and actions retain a minimum 48dp touch target; the search field remains an input,
not a gesture surface. Android's system bars, keyboard, and system edge gestures
remain controlled by Android.

From Home, an assigned gesture opens its page. Inside that page, only its reverse
returns Home: left/right or up/down with the **same number of fingers**, or
shake/shake. Other assigned gestures cannot
jump directly between destination pages. For example, Home-left can open All Apps
and Home-right can open All Contacts; right *inside All Apps* returns Home.

Top Apps and Close Contacts keep approximately the upper third of the usable
screen blank, placing their first item within thumb reach. Short and long lists
start at the same boundary. The blank area stays fixed while scrolling and
expanding contacts; smaller screens and larger text reduce it when necessary
to retain a usable list viewport. All Apps, All Contacts, Search, and Tasks keep
their own layouts.

Android Home always returns to your configured Home page. Back returns from a
destination to Home, with Back closing the keyboard, Other numbers submenu,
and expanded contact (in that order) before navigating. Back at Home does
not close the launcher. Ordinary returns from Settings or permission dialogs keep
the relevant page instead of resetting it to Home.

Search matches names using case-insensitive substring matching and ignores outer
query whitespace. Empty queries show no results. Opening Search automatically
focuses its text box, selects the previous query for replacement, and opens the
keyboard. The previous query and results remain until you type. With the keyboard
open, a 24dp gap below the system top inset keeps the field away from the top edge.
Back dismisses the keyboard without
immediately reopening it; tapping the input opens it again. App results launch, and contact
results offer the same inline actions as the contact pages. Search queries are temporary UI state, not saved
launcher settings.

### Minimal settings

**Long-press any blank space on your configured Home** to open Settings.
Long-pressing a name, action, input, alphabet rail, destination page, or setup
screen does not open it. Moving beyond Android's touch slop cancels the hold,
so slow swipes and pausing after moving still work.

- **Top Apps** and **Close Contacts** have independent counts: **5, 10, 15, 20,
  30, or 50**, defaulting to 10. Counts are durable only after a successful save.
  Full lists and Search remain uncapped.
- **Appearance** offers **System** (default, follows the device), **Dark**,
  **Light**, **Terminal Green** (green on near-black), and **Neutral Gray**
  (`#767676`, approximately 18% linear luminance, with near-black text). Each
  choice shows its real background and sample text before selection.
- **Monospace** is the default font; **System Sans** restores the previous
  proportional style. Font choices include samples. Theme and font changes
  apply immediately after a successful durable save and persist after restart.
- **Theme · Colors** and **Font · Typography** have separate headings,
  descriptions, and spacing. Choosing one never changes the other.
- **Set as default Home** opens Android's Home chooser. Once selected, Settings
  shows **BareShell is your default Home** instead.
- **Reset wizard** asks for confirmation and clears only Home, enabled-page,
  and gesture choices. It keeps tasks, list counts, appearance, contact scores, permissions,
  and Android's default Home.
- **Clear contact history** separately confirms removal of local scores.
  It never changes system stars.

Settings uses the selected appearance and supports scrolling and rotation.
Storage failures show an error instead of claiming the change was saved.

### Tasks

Tasks stay privately on this device, without permissions, reminders, time-of-day
scheduling, recurrence, sync, or cloud/device-transfer backup. Resetting the
wizard and clearing contact history never remove tasks. App/contact Search
does not search tasks.

**Add task** opens a small form with a required title, optional multiline note,
and a date defaulting to today. Title and note independently support RTL, LTR,
and mixed-language text. Save creates an open task:

- **Doing** is always open and includes tasks dated today or earlier.
  Unfinished tasks remain here until acted on.
- **Postponed** contains future-dated open tasks. They move to Doing when due,
  including after restarting the launcher. Date/time-zone changes and local
  midnight refresh grouping while the launcher is visible.
- **Finished** and **Canceled** retain closed tasks until reopened. They do
  not automatically become Doing.

The sections always appear in that order; the last three start collapsed and
show counts. Doing and Postponed sort by scheduled date, earliest first.
History sorts by the most recent status change, not note edits.

Tap a task to expand its indented details and actions, like contacts. One task
is expanded at a time and other titles are muted but remain tappable.
Quick actions appear directly below the title, before longer notes and dates.
**Finish** and **Cancel task** apply immediately; **Postpone** opens a date
picker and requires a future date. These actions use the saved title and note.
Expanded details show the note and both current and original dates; the original
date never changes. Finished/canceled tasks offer **Reopen** and **Edit**.
Reopening keeps the scheduled date and returns the task to Doing or Postponed.
Successful transitions collapse the details and update section counts without
automatically opening history sections.

**Edit** opens the title/note form separately. **Save** persists edits without
changing date or status and returns to compact details. **Discard edits** returns
without saving. State-changing controls are not mixed into this form.

Edits are not automatically saved. **Discard edits** is distinct from
**Cancel task**. Closing or leaving an unsaved editor asks before discarding.
Drafts, expansion, and section state survive rotation and refresh. Back hides the keyboard
first, then closes the editor (with a discard prompt when needed), then collapses details, then returns
from a destination to Home. Storage failures retain edits and show an error;
retry the action rather than assuming it was saved.

### Inline contact actions

Tap a contact to reveal indented text actions beneath its name, smoothly pushing
later rows down. Tap it again to close, or another contact to switch the expansion.
The selected name and its actions retain normal contrast; other contact names
and Search app results become muted but remain tappable. The alphabet rail stays
hidden for the entire expansion, including while scrolling.

- **Call number** opens the default dialer with the preferred number; it does not
  place a cellular call. Preference is the system super-primary number, then
  primary, then a deterministic first number. The launcher never writes defaults.
- **Other numbers** expands labeled Call and SMS actions for alternative numbers.
  Formatting-only duplicates are removed; distinct extensions and dial sequences
  are retained.
- **SMS number** opens a messaging app addressed to that number without sending.
- **View contact** opens that person's system contact page using its lookup
  identity, including contacts without a phone number.
- **Telegram Message**, **WhatsApp Message**, and **WhatsApp Call** appear only
  for matching contact-specific Contacts Provider data and an enabled, exported,
  compatible standard Telegram or WhatsApp handler. Installing a messenger alone
  does not prove a contact uses it. Multiple targets are separately labeled.
  **WhatsApp Call may start a call immediately when explicitly tapped.**

Vendor integrations depend on those apps' private provider contracts and may
change. Alternate clients, WhatsApp Business, video calls, guessed membership,
and network lookups are not supported. Missing actions and provider failures
have explicit status/retry text.

Expansion and the number submenu survive rotation/theme recreation and ordinary
external-app returns; actions are revalidated on return. Changing pages, pressing
Home, or editing the Search query closes the expansion.

## Install and use as Home

Start an emulator through Android Studio Device Manager, or connect a phone and
authorize its USB debugging prompt. Install the manufacturer's ADB USB driver on
Windows only if required.

From the project root in PowerShell (adjust the SDK path if needed):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$adbPort = 5037
& $adb -P $adbPort devices -l
$serial = 'emulator-5554' # Replace with the desired device serial from the list.
& $adb -P $adbPort -s $serial install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -P $adbPort -s $serial shell am start -n net.jooti.bareshell/.MainActivity
```

If another application occupies ADB's default port 5037, set `$adbPort = 5038`
to use a separate server rather than stopping that application. The original
development environment needed this workaround; it is not a project requirement.

If the running `emulator-5554` is listed as offline on the alternate server:

```powershell
& $adb -P $adbPort reconnect offline
& $adb -P $adbPort connect 127.0.0.1:5555
& $adb -P $adbPort devices -l
```

The TCP connection above is for the emulator whose console port is 5554; other
emulators use different port pairs. It may list the same emulator under both
its emulator serial and TCP address. Select one explicit serial.

Opening the app does not silently make it the default Home app. After setup,
tap **Set as default Home** and choose BareShell in Android's screen, or use the
same action in BareShell Settings later. Android 10+ uses the Home role chooser
when supported; older/unsupported devices open Home settings. Canceling leaves
the current default unchanged. If the system screen is unavailable, explicit
feedback explains how to open **Settings > Apps > Default apps > Home app**
manually. Names vary by manufacturer.
Press Home or perform the Home gesture to verify the configured BareShell page.

Test in an emulator first and keep your stock launcher installed. Android owns the
notification shade, system navigation, Recents, and any startup splash screen;
this app does not replace them.

## Switch back / recovery

Swipe down to open the notification shade and use its Settings shortcut. Navigate
to **Apps > Default apps > Home app** and select the original launcher.

With an authorized ADB connection, you can also open Home settings:

```powershell
& $adb -P $adbPort -s $serial shell am start -a android.settings.HOME_SETTINGS
```

Or uninstall this development app:

```powershell
& $adb -P $adbPort -s $serial uninstall net.jooti.bareshell
& $adb -P $adbPort -s $serial shell input keyevent KEYCODE_HOME
```

Android can then use or prompt for another installed Home app.
