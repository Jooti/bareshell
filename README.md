# BareShell

![BareShell logo](docs/assets/bareshell-logo.svg)

A minimal, text-first Android home launcher. No icons in your lists, widgets,
network access, or background service: just the apps, people, and tasks you need.
Supports Android 8.0 (API 26) and newer.

## Make Home your own

Choose a Home page, include the other pages you want, and give each a gesture:

| Page | What it does |
| --- | --- |
| **Top Apps** | Your most-used apps, balancing opening frequency and foreground time. |
| **Close Contacts** | Starred people first, then contacts you interact with in BareShell. |
| **All Apps** | An alphabetical app list with an on-scroll alphabet rail. |
| **All Contacts** | Your contacts, with starred people first and inline actions. |
| **Search** | Find apps and contacts; the keyboard opens ready to replace the last query. |
| **Tasks** | Today and overdue work in Doing, followed by Postponed, Finished, and Canceled. |

All Apps and All Contacts start unselected in the wizard; add them if you want
shortcuts. Use one- or two-finger swipes in blank space, or shake on supported
devices. The opposite gesture returns Home. Long-press blank Home space for
Settings.

## Less friction, less decoration

- **Reachable favorites:** Top Apps and Close Contacts start about one-third
  down the screen, even with long lists.
- **Fast returns:** cached-first pages refresh behind their last successful
  content rather than showing a loading screen on every visit.
- **Simple tasks:** tap for Finish, Postpone, Cancel task, or Edit. Notes and
  original dates stay with each task; closed tasks can be reopened.
- **Your appearance:** System, Dark, Light, Terminal Green, and Neutral Gray
  color themes with real previews. Font selection is a separate group:
  Monospace by default, or System Sans.
- **Text that fits:** RTL/LTR names and notes, system font scaling, scrollable
  layouts, and accessible text actions.
- **Local by design:** tasks, preferences, and contact scores stay on the device
  and are excluded from backup. Contacts and usage access are optional.

## Get started

Build and install the APK using the [development guide](docs/development.md),
then complete the wizard. BareShell offers **Set as default Home** or **Not now**
after setup. You can also set it later in BareShell Settings or Android's
**Apps → Default apps → Home app**.

Keep your stock launcher installed. Android still controls system navigation,
Recents, the notification shade, and the keyboard.


## Learn more or contribute

- [Documentation index](docs/README.md)
- [Setup, detailed features, and recovery](docs/setup-and-recovery.md)
- [Privacy, permissions, and ranking](docs/privacy-and-data.md)
- [Contributing](docs/contributing.md)
- [Architecture](docs/architecture.md)
- [Testing and validation](docs/testing.md)
