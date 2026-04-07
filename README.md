# Stimulo

**Stimulo** is an Android habit-scheduler app (Java, Material Design 3) that fires timed
notifications and sends commands to an **ESP32 peripheral** (buzz/vibrate) when a scheduled
event fires.

---

## Table of Contents
1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [How Scheduling Works](#how-scheduling-works)
4. [ESP32 Integration](#esp32-integration)
5. [Notification Channels](#notification-channels)
6. [Boot Persistence](#boot-persistence)
7. [Platform Limitations](#platform-limitations)
8. [Build & Run](#build--run)
9. [Extending the App](#extending-the-app)

---

## Architecture

The app follows **MVVM** (Model–View–ViewModel) with clear separation of concerns:

```
UI Layer          ViewModel Layer       Data Layer
─────────         ───────────────       ──────────
MainActivity  ──► ScheduleViewModel ──► ScheduleRepository
AddScheduleActivity                         │
DashboardFragment                      AppDatabase (Room)
ScheduleAdapter                        ScheduleDao
                                       TriggerLogDao
```

### Key Components

| Component | Class | Role |
|-----------|-------|------|
| Room DB | `AppDatabase` | SQLite persistence via Room |
| Repository | `ScheduleRepository` | Single source of truth; off-thread DB ops |
| ViewModel | `ScheduleViewModel` | UI-safe LiveData; schedule CRUD + WorkManager bridge |
| WorkManager | `ScheduleManager` / `ScheduleTriggerWorker` | Background scheduling & trigger execution |
| ForegroundService | `TriggerForegroundService` | Holds a foreground context during the trigger window |
| BroadcastReceiver | `BootReceiver` | Re-enqueues active schedules after device reboot |
| ESP32 stub | `StubEsp32Communicator` | Implements `Esp32Communicator`; logs only until hardware is connected |
| Notifications | `NotificationHelper` | Two channels: trigger alerts + foreground service |

---

## Project Structure

```
app/src/main/
├── java/com/stimulo/app/
│   ├── StimApp.java                        # Application class, creates notification channels
│   ├── MainActivity.java                   # Hosts DashboardFragment, requests POST_NOTIFICATIONS
│   ├── AddScheduleActivity.java            # Form to create a new schedule
│   ├── data/
│   │   ├── entity/
│   │   │   ├── ScheduleEntity.java         # Room entity: schedule row
│   │   │   └── TriggerLogEntity.java       # Room entity: per-trigger audit log
│   │   ├── dao/
│   │   │   ├── ScheduleDao.java            # CRUD + LiveData queries
│   │   │   └── TriggerLogDao.java          # Insert / status-update for logs
│   │   ├── db/
│   │   │   └── AppDatabase.java            # Room singleton
│   │   └── repository/
│   │       └── ScheduleRepository.java     # Async wrappers over DAO
│   ├── viewmodel/
│   │   ├── ScheduleViewModel.java
│   │   └── ScheduleViewModelFactory.java
│   ├── ui/dashboard/
│   │   ├── DashboardFragment.java          # RecyclerView of schedules + FAB
│   │   └── ScheduleAdapter.java            # ListAdapter with toggle + delete
│   ├── scheduling/
│   │   ├── ScheduleManager.java            # Enqueue / cancel WorkManager jobs
│   │   └── ScheduleTriggerWorker.java      # Worker: notify → service → ESP32 → reschedule
│   ├── service/
│   │   └── TriggerForegroundService.java   # Foreground service during trigger window
│   ├── receiver/
│   │   ├── BootReceiver.java               # BOOT_COMPLETED → reschedule all active
│   │   └── AlarmReceiver.java              # Placeholder for AlarmManager exact alarms
│   ├── esp/
│   │   ├── Esp32Communicator.java          # Interface (sendBuzzCommand, ping)
│   │   └── StubEsp32Communicator.java      # Logs to Logcat; simulates ACK
│   ├── notification/
│   │   └── NotificationHelper.java         # Channel creation + notification builders
│   └── model/
│       └── RepeatType.java                 # Enum: NONE | DAILY | COUNT_BASED
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── activity_add_schedule.xml
    │   ├── fragment_dashboard.xml
    │   └── item_schedule.xml
    ├── values/            colors, strings, themes, dimens
    ├── values-night/      dark-mode theme override
    ├── drawable/          ic_notification, ic_launcher_background, ic_launcher_foreground
    └── mipmap-*/          adaptive icon XMLs
```

---

## How Scheduling Works

```
User taps Save
      │
      ▼
AddScheduleActivity.saveSchedule()
      │  builds ScheduleEntity (triggerTimeMillis = next future occurrence)
      ▼
ScheduleViewModel.saveSchedule()
      │  inserts into Room, then in callback:
      ▼
ScheduleManager.schedule(entity)
      │  calculates delay = triggerTimeMillis - now
      │  enqueues OneTimeWorkRequest with that delay
      ▼
[ ... time passes ... ]
      ▼
ScheduleTriggerWorker.doWork()   ← WorkManager fires this on a background thread
      ├─ showTriggerNotification()
      ├─ startForegroundService(TriggerForegroundService)
      ├─ insert TriggerLogEntity (status=SENT)
      ├─ esp32Communicator.sendBuzzCommand()  ← stub for now
      └─ repeat logic:
            DAILY        → set triggerTimeMillis = same time tomorrow, re-enqueue
            COUNT_BASED  → decrement remainingCount; re-enqueue if > 0; else isActive=false
            NONE         → isActive = false
```

### ScheduleEntity fields relevant to scheduling

| Field | Purpose |
|-------|---------|
| `triggerTimeMillis` | UTC ms of the next trigger; updated after each fire |
| `hourOfDay` / `minuteOfHour` | Used to compute `nextDailyTrigger` (+1 day at same clock time) |
| `repeatType` | `NONE` / `DAILY` / `COUNT_BASED` |
| `remainingCount` | Decremented on each COUNT_BASED fire; schedule deactivates at 0 |
| `isActive` | Workers respect this; `ScheduleManager.schedule()` returns early if false |

---

## ESP32 Integration

### Interface

```java
public interface Esp32Communicator {
    void sendBuzzCommand(long scheduleId, String command, AckCallback callback);
    void ping(AckCallback callback);
}
```

### Command protocol (suggested)

```
BUZZ:<duration_ms>:<pattern>   e.g. BUZZ:500:1
PING
```

ESP32 responds with `ACK:<event_id>` on success or `ERR:<code>` on failure.

### Replacing the stub

1. Create `BleEsp32Communicator` (or `WifiEsp32Communicator`) implementing `Esp32Communicator`.
2. Establish a BLE GATT connection or TCP socket inside `sendBuzzCommand`.
3. On ACK, call `callback.onAck(scheduleId, response)`.
4. In `ScheduleTriggerWorker`, inject your real implementation instead of `StubEsp32Communicator`.
5. Add `BLUETOOTH_SCAN` permission (API 31+) if using BLE.

### Trigger log

Every fire writes a `TriggerLogEntity` row:

| Field | Values |
|-------|--------|
| `status` | `SENT` → `ACK` / `FAILED` / `RETRYING` |
| `espResponse` | Raw response string from device |

Update the status in `AckCallback.onAck()` / `onFailure()` using `TriggerLogDao.updateStatus()`.

---

## Notification Channels

| Channel ID | Name | Importance | Purpose |
|------------|------|------------|---------|
| `stimulo_trigger` | Schedule Triggers | HIGH | Shown when an event fires; vibrates |
| `stimulo_fg` | Background Service | LOW | Persistent notification while ForegroundService runs |

Both channels are created in `StimApp.onCreate()` via `NotificationHelper.createChannels()`.

---

## Boot Persistence

`BootReceiver` listens for:
- `android.intent.action.BOOT_COMPLETED`
- `android.intent.action.QUICKBOOT_POWERON` (HTC/Xiaomi devices)

On receipt it queries `scheduleDao.getAllActiveSchedulesSync()` on a background thread and
re-enqueues every schedule whose `triggerTimeMillis` is still in the future.

> **Note:** WorkManager already persists its own queue across reboots via a JobScheduler/AlarmManager
> integration. The `BootReceiver` acts as a safety net for edge cases (e.g., app updated while
> device was off, or WorkManager DB cleared by the OS).

---

## Platform Limitations

### WorkManager timing precision
WorkManager is not a hard real-time scheduler. On Android 6+ with Doze mode, workers may be
deferred up to **several minutes** if the device is idle. For strict timing:
- Request the user to exempt the app from battery optimization
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission is declared).
- Wire `AlarmReceiver` to `AlarmManager.setExactAndAllowWhileIdle()` (see TODO in
  `AlarmReceiver.java`).

### Exact alarms (Android 12+)
`SCHEDULE_EXACT_ALARM` requires the user to explicitly grant permission in
**Settings → Apps → Special app access → Alarms & Reminders** on API 31+.

### POST_NOTIFICATIONS (Android 13+)
`MainActivity` requests this permission at runtime on API 33+ (TIRAMISU).

### Foreground service type
`TriggerForegroundService` declares `foregroundServiceType="dataSync"` which satisfies
Android 14's requirement that foreground services declare a type.

### Background thread in BroadcastReceiver
`BootReceiver` spawns a `new Thread()` for the DB query. For production use, consider
using `goAsync()` with a coroutine/executor to get the full 10-second BroadcastReceiver
budget if the schedule list is large.

---

## Build & Run

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with Android Studio)
- Android SDK 34

### Steps
```bash
git clone <repo-url>
cd Stimulo-App
# Open in Android Studio and click Run, or:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Gradle versions
| Tool | Version |
|------|---------|
| Gradle | 8.2 |
| Android Gradle Plugin | 8.2.0 |
| Compile SDK | 34 |
| Min SDK | 24 |

---

## Extending the App

| Feature | Where to start |
|---------|---------------|
| Real ESP32 BLE | Implement `Esp32Communicator` in a new `BleEsp32Communicator` class |
| Edit existing schedule | Add `scheduleId` extra to `AddScheduleActivity`; load from Room on start |
| Schedule history screen | Query `TriggerLogDao.getLogsForSchedule()` in a new fragment |
| Multiple ESP32 devices | Add `deviceId` to `ScheduleEntity`; maintain a device registry in Room |
| Exact alarm fallback | Wire `AlarmReceiver` + `AlarmManager.setExactAndAllowWhileIdle()` |
| Retry on ESP32 failure | Implement exponential backoff in `AckCallback.onFailure()` using WorkManager `retry()` |