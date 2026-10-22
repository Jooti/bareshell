# Contributing

[Documentation index](README.md) | [Development](development.md) | [Testing](testing.md)

## Start here

1. Read the [product introduction](../README.md) and [architecture](architecture.md).
2. Install the existing SDK/JDK toolchain and run the current build/tests.
3. Make a focused change that preserves the native, minimal, text-first design.
4. Add or update directly relevant tests and documentation.
5. Describe the change, how it was verified, and any untested device/API cases
   when sharing it for review.

This project does not define a hosted issue/PR workflow, CI configuration,
release-signing process, or formatting tool here. Use the repository's actual
tools rather than assuming one exists.

## Coding and UI conventions

- Main/test Kotlin packages are `net.jooti.bareshell`. Keep directories aligned.
- Prefer existing helpers (`textLabel`, `onTextClick`, `appearanceColor`) and
  small native Views over new frameworks or dependencies.
- Put user-visible strings in resources. Respect content-based RTL/LTR direction,
  multiline notes, scaled fonts, touch targets, accessibility headings, and
  disabled-animation preferences.
- Resolve colors from the active appearance. Keep real theme previews in sync
  with native palette resources; font choice is independent of color theme.
- Keep launcher gestures in blank space. Text, editors, action controls, alphabet
  rails, system bars, and the keyboard must retain their intended interactions.
- Keep work that can block off the UI thread. Cancel/ignore obsolete results
  and avoid holding Activity references in retained/background work.
- Handle expected storage/provider/handoff failures explicitly with actionable
  UI and repository-standard logging. Do not turn failures into empty success.

## Data and behavior safety

- Preserve existing configuration validation, task revision checks, pending-write
  recovery, immutable original dates, and explicit draft saves/discards.
- Do not grant permissions or change Android's default launcher silently.
- Do not write contacts, place calls, send messages, add network access, or
  introduce data transfer/backup without an explicit feature decision.
- Never log private task text, names, numbers, queries, credentials, or usage data.
- Avoid unrelated fixes, generated-output edits, and new dependencies without a
  concrete requirement. Never include signing credentials or machine secrets.

## Verification

Run the smallest relevant JVM selectors while iterating, then build and lint
the touched surfaces. Package-wide refactors warrant the full existing unit
suite. Commands and the device checklist are in [Testing](testing.md).

For UI changes, use a disposable device/package and exercise themes, keyboard/
Back, RTL/LTR, large text, landscape, recreation, and failure/retry. Keep the
stock launcher installed. Do not modify another user's emulator, private data,
permissions, or default Home. Remove only fixtures/processes you created.

Document unavailable coverage honestly, especially API 26/36 checks and
vendor-specific contact/messenger integration. Historical emulator results in
[Verification history](verification-history.md) do not replace current testing.

## Documentation and visual identity

README is a short product introduction. Put technical/contributor details under
`docs/`, add index links, and use relative paths. Retain useful operational
knowledge without presenting a developer's local proxy/ADB configuration as a
requirement for everyone.

The reusable logo is [assets/bareshell-logo.svg](assets/bareshell-logo.svg).
Keep its geometry aligned with the Android vector/adaptive/monochrome resources
and inside the adaptive-icon safe zone. List pages remain text-only.
