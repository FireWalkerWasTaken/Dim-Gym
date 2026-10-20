<p align="center">
  <img src="app/src/main/res/drawable/dimgym_logo.png" alt="DimGym logo" width="200">
</p>

<h1 align="center">DimGym</h1>

<p align="center">
  <b>Open the app. See today's workout. Train.</b><br>
  A fully offline Android workout app that lays out your plan each day and remembers your working weights.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Offline" src="https://img.shields.io/badge/data-100%25%20offline-FFB300">
</p>

---

## Why DimGym

Most gym apps are either a notes app (you write everything yourself) or a social platform full of accounts, feeds and graphs. DimGym is neither. You build your plan once, and from then on the app simply shows you the next workout in your cycle, with the sets, reps and weights you used last time.

It is built for gym goers who already know what they are doing and just don't want to forget **what weight they lifted last week**.

The name says the rest: a dark, dim-lit UI (near-black with an amber accent) to match the mood of a gym.

## Features

**Today**
- Opens straight onto today's workout as a simple card list
- Tick off exercises as you go, with a progress bar and a **Finish workout** button (with undo)
- Tap an exercise to see its weight and your own notes (form, cues)
- Quick **+/- steppers** for weight, easy to use one-handed or with gloves
- Manual **switch workout** menu to skip or jump to a different day

**Plan**
- Build your cycle day by day: add a day, name it, add exercises with sets × reps
- Mark any day as a **rest day** (it gets its own rest-day screen)
- **Drag to reorder** exercises (with Move up / Move down for screen readers)
- Remove with **undo**; delete a whole day without losing your weights and notes
- Typing an exercise name that already exists **reuses it**, so its weight is shared everywhere it appears (e.g. Leg Curl in both Legs A and Legs B)

**Weights and notes**
- Weight is optional (leave it empty for bodyweight exercises)
- Per-exercise weight and notes are stored once and shared across days; sets × reps are set per day
- Updating a weight simply overwrites the old one. No history, no clutter

**Reminders**
- One daily reminder at a time you choose
- Skipped on rest days, and skipped if you already tapped Done today
- Expanded notification lists the day's exercises with sets × reps and weights
- Rescheduled automatically after a phone reboot

**Settings and backup**
- Weight unit: kg / lb
- Theme: dark / light
- **Export / Import backup**: one readable JSON file with your plan, weights, notes and settings. Save it wherever you want; there is no cloud

**Rolling cycle, not fixed weekdays.** The app always shows the *next* workout in your order. Miss a day and nothing breaks. Tapping **Done** advances the cycle.

## Example plan

A 6-day Push / Pull / Legs split works naturally as a cycle:

`Push A → Pull A → Legs A → Push B → Pull B → Legs B → Rest`

Setting up a full plan like this should take about ten minutes.

## Privacy

DimGym is completely offline. There are no accounts, no analytics, no network calls and no cloud sync. Your data lives in a local Room database on your phone and leaves it only when you export a backup yourself.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Storage | Room (SQLite) |
| Backup | JSON export/import via the system file picker |
| Reminders | `AlarmManager` + `BroadcastReceiver` + notifications |
| IDE | Android Studio |

Package: `com.dimgym`

## Project structure

```
com.dimgym
├── MainActivity.kt        App shell, bottom navigation, tab keep-alive, splash
├── Splash.kt              Animated opening screen
├── TodayScreen.kt         Today's workout, check-off, finish, exercise detail
├── PlanScreen.kt          Day list and day editor (drag-to-reorder)
├── SettingsScreen.kt      Unit, theme, reminder, backup
├── Components.kt          Shared UI: AppCard, PrimaryButton, EmptyState, transitions
├── Reminder.kt            Scheduling and notification
├── Backup.kt              JSON export / restore
├── data/                  Room entities, DAO and database
└── ui/theme/              Dark and light themes
```

## Getting started

1. Clone the repo
   ```bash
   git clone https://github.com/<your-username>/Dim-Gym.git
   ```
2. Open it in **Android Studio**
3. Let Gradle sync finish
4. Run on a device or emulator. A real phone over USB is much smoother than the emulator

On Android 13 and above the app asks for notification permission when you turn the reminder on.

## Backup format

Backups are plain JSON, so they are easy to read and keep:

```json
{
  "app": "DimGym",
  "version": 1,
  "settings": { "unit": "kg", "theme": "dark", "reminderMinutes": 1080, "cyclePosition": 0 },
  "exercises": [ { "id": 1, "name": "Bench Press", "weight": 60.0, "notes": "" } ],
  "days": [ { "id": 1, "name": "Push A", "position": 0, "isRest": false } ],
  "dayExercises": [ { "id": 1, "dayId": 1, "exerciseId": 1, "position": 0, "sets": 3, "reps": 10 } ]
}
```

Importing replaces all current data. An invalid file is rejected and nothing is changed.

## Not in v1

On purpose, to keep the app simple and focused:

- Multiple plans
- Body measurements, bodyweight or nutrition tracking
- Accounts, sharing or social features
- Progress graphs or weight history

## Roadmap

- [ ] Built-in form guide for each exercise
- [ ] Real-world testing with a full PPL plan
- [ ] Sharing with other gym goers
- [ ] Possible Play Store release

## Author

Built by **Lakshay**, a Computer Science student at Chandigarh University, as a first Android app and a tool for his own training.

## License

Add a license of your choice (for example MIT) before publishing.
