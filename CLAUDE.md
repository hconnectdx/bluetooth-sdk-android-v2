# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This repo (Gradle root project name `bluetooth-sdk-android-v2`) is a monorepo of three independently-published Android BLE SDKs plus one example app per SDK:

| Module | Publishes as | Role |
|---|---|---|
| `bluetooth-sdk-android-v2` | `kr.co.hconnect:bluetooth-sdk-android-v2` | **Central** (client) role — scan, connect, GATT read/write/notify, MTU negotiation. Entry point: `HCBle` (singleton object). |
| `bluetooth-sdk-android-peripheral` | `kr.co.hconnect:bluetooth-sdk-android-peripheral` | **Peripheral** (GATT server) role — advertise, accept connections, notify data to a connected Central. Entry point: `HCBlePeripheral` (singleton object). |
| `polihealth-sdk-android-v2` | `kr.co.hconnect:polihealth-sdk-android-v2` | Domain-level SDK for the "Poli" health band: wraps `HCBle` with protocol parsing (sleep/daily) and a ktor-based REST client (`PoliClient`) that uploads parsed data to the backend. |
| `bluetooth-sdk-android-v2-example`, `bluetooth-sdk-android-peripheral-example`, `polihealth-sdk-android-v2-example` | (apps) | Sample apps, one per SDK, used for manual/interactive testing. |

## Build commands

```bash
# Build everything
./gradlew build

# Build/compile a single module
./gradlew :bluetooth-sdk-android-v2:assembleDebug
./gradlew :bluetooth-sdk-android-v2:compileDebugKotlin   # fast compile-only check

# Lint
./gradlew lint

# Install an example app to a connected device
./gradlew :bluetooth-sdk-android-v2-example:installDebug
./gradlew :bluetooth-sdk-android-peripheral-example:installDebug
```

There are no real unit/instrumented tests in any module — only the default Android-Studio-generated `ExampleUnitTest`/`ExampleInstrumentedTest` boilerplate. Verify changes by running one of the `-example` apps.

### `local.properties` is required for *any* Gradle invocation

Every module's `build.gradle.kts` reads GitHub Packages publish credentials **eagerly at configuration time** (not just when `publish` is invoked):

```kotlin
val rootProjectProps = Properties()
rootProjectProps.load(FileInputStream(project.file("../local.properties")))
val githubUrl: String = rootProjectProps.getProperty("githubUrl")          // NPE if missing
val githubUsername: String = rootProjectProps.getProperty("githubUsername")
val githubAccessToken: String = rootProjectProps.getProperty("githubAccessToken")
```

Because Gradle configures all subprojects for most task invocations, **any** `./gradlew` command fails with `... must not be null` if `local.properties` only has `sdk.dir` and is missing `githubUrl`/`githubUsername`/`githubAccessToken`. Add all three keys (dummy values are fine for local compile-only work; real ones are only needed to actually `publish`) before running any Gradle task. `--configure-on-demand` narrows configuration to the requested module's build script but does not remove the requirement for that module.

### Publishing a module

Publishing is **not** wired to `assembleRelease` automatically — the `MavenPublication` references a static prebuilt AAR path (`build/outputs/aar/<module>-release.aar`), so you must build the release AAR yourself first:

```bash
./gradlew :bluetooth-sdk-android-v2:assembleRelease
./gradlew :bluetooth-sdk-android-v2:publish
```

The published version comes from that module's `project.properties` file (`version=...`), not from `build.gradle.kts` — bump it there before publishing.

## Architecture

### Central role: `HCBle` + `GATTController`

`HCBle` (in `bluetooth-sdk-android-v2`) is a singleton facade over scanning, connecting, and GATT I/O. It supports **multiple simultaneous device connections**, tracked in `mapBLEGatt: MutableMap<String, GATTController>` keyed by device address — one `GATTController` instance per connected device holds that connection's service/characteristic state and in-flight-operation bookkeeping. All of `HCBle`'s per-device GATT methods (`readCharacteristic`, `writeCharacteristic`, `setCharacteristicNotification`, etc.) just look up the device's `GATTController` and delegate.

Android's `BluetoothGatt` only allows **one outstanding GATT operation per connection at a time**; the OS synchronously rejects a new request with a status code (e.g. `BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY` = 201) if the previous operation's callback hasn't returned yet — it does not queue it. `GATTController.writeCharacteristic()` implements an internal write queue (`writeQueue` + `isWriteInFlight`) for exactly this reason: callers can fire writes at any time without worrying about GATT busy errors, and if the OS still rejects a write outright (no callback will ever come for a rejected request), the controller retries after a short delay (`WRITE_RETRY_DELAY_MS`, capped at `MAX_WRITE_RETRIES`). The queue is advanced from `HCBle`'s `BluetoothGattCallback.onCharacteristicWrite` → `GATTController.handleCharacteristicWriteResult()`. When touching GATT operations, remember this single-operation-per-connection constraint applies to every op type (read, write, descriptor write, MTU request) — only `writeCharacteristic` has queuing today; `readCharacteristic`/`setCharacteristicNotification` do not, and can still cross paths with a queued write.

`GATTController.requestMtu()` already handles its async result correctly via a stored `mtuRequestCallback` invoked from `handleMtuChanged()` — use it as the reference pattern for any other operation that needs the same treatment.

`GATTService.kt` is `@Deprecated("Use GATTController instead")` — dead code kept for source compat, do not build on it. `service/BleService.kt` and `service/BleWorker.kt` (a WorkManager-based background scan prototype) are fully commented out and unused.

### Peripheral role: `HCBlePeripheral`

`HCBlePeripheral` owns a single GATT server + advertiser and a single `connectedDevice` (one Central at a time, unlike the Central-role SDK). `sendData()` is `@Synchronized` and already serializes outbound notifications correctly: it frames the payload with a 4-byte length header, splits it into MTU-sized chunks, and for each chunk calls `notifyCharacteristicChanged` then blocks on `notifySemaphore.tryAcquire(2, SECONDS)` waiting for the `onNotificationSent` callback before sending the next chunk — so it does not suffer from the same "fire another op before the last one's callback returns" class of bug that `GATTController.writeCharacteristic` needed a queue for.

### `polihealth-sdk-android-v2`

Built on top of the Central role, but **depends on the published artifact** (`libs.bluetooth.sdk.android.v2`, version pinned in `gradle/libs.versions.toml` as `bluetoothSdkAndroidV2`), not the in-repo `bluetooth-sdk-android-v2` module — the `implementation(project(":bluetooth-sdk-android-v2"))` alternative is present but commented out. **This means a source change to `bluetooth-sdk-android-v2` does not affect `polihealth-sdk-android-v2` (or its example app) until `bluetooth-sdk-android-v2` is published and the version in `libs.versions.toml` is bumped.** `PoliBLE.kt` is the connection/GATT-facing entry point (mirrors `HCBle`'s API shape under different names); `PoliClient.kt` + `api/` hold the ktor REST client and request/response DTOs for the sleep/daily protocol endpoints, which are separate from the BLE layer.

### Known source-layout inconsistency

`gatt/BLEState.kt` and `gatt/UUIDs.kt` (inside `bluetooth-sdk-android-v2`) declare `package kr.co.hconnect.bluetooth_sdk_android.gatt` (missing the `_v2` suffix) even though they live in the `bluetooth_sdk_android_v2` module directory tree — a leftover from an earlier `bluetooth-sdk-android` (v1) library. `GATTState.kt` in the same directory uses the correct `bluetooth_sdk_android_v2.gatt` package. Both compile fine, but don't assume all files under a module's `gatt/` folder share one package when grepping/importing.
