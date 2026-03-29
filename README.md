# TreadmillSync 🏃‍♂️⌚

**TreadmillSync** is an Android utility that turns your smartphone into a virtual **BLE (Bluetooth Low Energy) Foot Pod**. It broadcasts speed and cadence data directly to your Garmin, COROS, Suunto, or Apple Watch, allowing you to sync indoor treadmill runs without extra hardware.

Built with a focus on modern Android architecture and a "Cyberpunk" aesthetic.

---

## ✨ Features

* **Virtual Foot Pod:** Implements the standard Bluetooth RSC (Running Speed and Cadence) profile.
* **Garmin Optimized:** Includes specific GATT configurations (Appearance 1088, specific descriptors) to ensure stable pairing with picky sports watches.
* **Foreground Service:** Stays active and broadcasts even when the app is minimized or the screen is locked.
* **Quick Access Presets:** Customizable speed buttons for intervals and tempo changes.
* **Metric & Imperial Support:** Toggle between KM/H and MPH with automatic pace calculation ($min/km$).
* **Neon UI:** A high-contrast, dark-mode interface designed for high visibility while running.

---

## 🛠 Tech Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Bluetooth** | Android BLE Peripheral API |
| **Architecture** | MVVM + Foreground Services |
| **Target SDK** | Android 14 (API 34) |

---

## 🚀 How to Use

1.  **Install the APK** on your Android device.
2.  **Grant Permissions:** The app requires Bluetooth Advertise/Connect and Post Notifications (for background stability).
3.  **Start Broadcasting:** Tap the start button in the app.
4.  **Pair your Watch:**
    * On your watch, go to **Sensors & Accessories > Add New**.
    * Select **Foot Pod** .
    * Find **Foot Pod**  and pair. (Disconnect watch from phone if connected)
5.  **Run:** Adjust the speed on the phone app; the watch will update your pace in real-time.

---

## 📱 Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/yourusername/TreadmillSync.git](https://github.com/yourusername/TreadmillSync.git)
    ```
2.  **Open the project** in Android Studio Hedgehog (or newer).
3.  **Build and run** on a physical Android device.
    > **Note:** Emulators do not support BLE Advertising.

---

## 🔧 Project Structure

* `BleManager.kt`: The core Bluetooth engine handling GATT services and advertising packets.
* `BleService.kt`: Manages the Foreground Service and Notification to prevent the app from being killed by Android's battery optimization.
* `MainActivity.kt`: The main entry point using Jetpack Compose for the UI logic.
* `MainViewModel.kt`: Handles state, speed calculations, and unit conversions.
