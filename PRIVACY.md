# Privacy

nekokata is a **local-only** Android app. There is no account, no analytics SDK, and no nekokata server.

## What stays on the device

Schedules, habit logs, todos, measurements, nutrition, water, profile, and settings are stored in the WebView’s `localStorage` on that phone. Export/import is a file you choose. Reset erases local data after a confirmation step.

## What we do not collect

The app does not send your planner data to a nekokata backend. There are no sign-in, sync, or telemetry services in this project.

## Optional Android permissions

| Permission | Why | Required? |
|---|---|---|
| `READ_CALENDAR` | Show device calendar events on Home (today + 7 days), read-only. nekokata never writes to the calendar. | No. Off until you enable Calendar sync in Settings. |
| `POST_NOTIFICATIONS` | Reminders at timed tasks. | No. Off until you enable Notifications in Settings. |
| `VIBRATE` | Haptic feedback on checks. | Used locally. |
| `RECEIVE_BOOT_COMPLETED` | Restore widget ticks and reminders after reboot. | Used locally. |
| `INTERNET` | Present for the Capacitor/WebView stack. The app does not call a nekokata API. | Not used for accounts or analytics. |

## Calendar

Calendar sync reads event title, time, calendar name, and location from calendars you select, and shows them as read-only Home rows. Disconnect removes imported events from the app. Device calendar data is never uploaded by nekokata.

## Backups

`android:allowBackup` is **false**. Android Auto Backup / ADB backup will not copy app data off the device. Use **Settings → Export** if you want a file you control. A reset keeps a temporary in-session restore only until you leave the app.

## Source tree

Profile and measurement defaults in this tree are empty. First launch writes `blank-seed-v1-applied` and starts with no schedule or body numbers.

## Fonts

Interface fonts (Inter, Roboto Mono) are bundled in `www/fonts/` under their upstream licenses. See that folder.
