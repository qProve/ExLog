<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="ExLog Icon" width="128" height="128" />
  <h1>ExLog</h1>
  <p><b>A modern, local-first Android Workout Tracker built for speed and efficiency.</b></p>
  
  [![Version](https://img.shields.io/badge/Version-1.3.x-blue.svg)](https://github.com/qProve-P/ExLog)
  [![Platform](https://img.shields.io/badge/Platform-Android-4ECDC4.svg?logo=android)](https://github.com/qProve-P/ExLog)
  [![License](https://img.shields.io/badge/License-GPL_3.0-green.svg)](https://www.gnu.org/licenses/gpl-3.0)

</div>

<br/>

> ⚠️ **Notice**: This application is currently in active development. Features, database schemas, and functionality are subject to change at any time.

## 📋 Overview

ExLog is an offline-first fitness logging application designed to remove friction from your workouts. Build your template routines, tap start, and log your progress without any cloud reliance or subscriptions. Your data is yours.

## ✨ Features

- 🏋️‍♂️ **Exercise Library:** Build a personal library of templates with optional notes and reference links.
- 📋 **Workout Builder:** Create complex workout templates by composing sets, target reps, and reference weights for any number of exercises.
- ⏱️ **Live Session Logging:** Dynamic session screen with an active workout timer and real-time set tracking. Add or remove sets on the fly.
- 📈 **Performance History:** Review all your past sessions sorted by date.
- 📊 **Progress Graphs:** Visualize your weight and reps over time (1 Week, 1 Month, 1 Year, or All-Time) with dual-axis interactive charts.
- 💾 **Local-First & Portable:** All data is saved instantly to a local SQLite database (Room).
- 🔄 **Export / Import:** Take full control of your data with 1-click JSON exports and imports. Back up your logs anywhere you want.

## 🛠 Tech Stack

- **Platform:** Android (Java)
- **Database:** Room Persistence Library (Jetpack)
- **UI:** Material Design 3 Components, Bottom Navigation, RecyclerViews
- **Charts:** MPAndroidChart
- **Data Serialization:** GSON (for Export/Import)

## 🚀 Getting Started

To build and run ExLog from source:

1. Clone this repository:
   ```bash
   git clone https://github.com/qProve-P/ExLog.git
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle and run on an Emulator or physical Android device. (Requires Android SDK 24+)

## 🔒 Privacy Focus

ExLog respects your privacy. It requests **zero** network permissions. All data, templates, and history remain securely on your local device unless explicitly exported by you.

## ⚖️ License

This project is licensed under the **GNU General Public License v3.0**. 

Because this is a strong copyleft license, it guarantees end users the freedom to run, study, share, and modify the software. If you plan to distribute a modified version of ExLog, you must also distribute your source code under the exact same terms.

Please familiarize yourself with the full terms in the `LICENCE` file before making any derivative works.