# Shreenathji Textile — Native Android Sales App

A lightweight, 100% Native Android application built with **Kotlin** and **Jetpack Compose** for textile field sales reps operating in high-density market complexes (~100,000 m²).

---

## 🚀 Core Features

### 1. Three Essential Screens
* **Parties List (`PartyListScreen.kt`)**: Searchable client directory with shop numbers, market sections (e.g. RKTM, Millennium), distance indicators, and one-tap order booking.
* **Order Form (`OrderFormScreen.kt`)**: Fast fabric/quality selection (Cotton 60x60, Rayon Foil, Modal Silk, etc.), quantity & rate calculations, notes, and instant submit with automatic GPS tagging.
* **Order History (`OrderHistoryScreen.kt`)**: Chronological list of orders booked, sync status badges (`SYNCED` vs `LOCAL (OFFLINE)`), and expandable item breakdowns.

### 2. Automated Background GPS Tracking
* **No Shift Toggle Required**: Tracking starts automatically when the app opens and auto-resumes after device reboots (`BootReceiver.kt`).
* **Android Foreground Service (`LocationTrackingService.kt`)**: Keeps GPS polling alive in background with a low-priority sticky notification.
* **Smart Sampling**: Configured with 2-minute interval and 20-meter displacement filter to preserve battery while maintaining high accuracy in dense market complexes.
* **Transaction GPS Stamping**: Exact instantaneous GPS coordinates are stamped directly on every submitted order.

### 3. Offline-First Architecture
* Powered by Google **Room Database (SQLite)**.
* Reps can search clients and book orders inside concrete basements or zero-reception alleys without interruptions.

---

## 🛠️ Tech Stack
* **Language**: Kotlin 1.9.23
* **UI**: Jetpack Compose + Material 3
* **Local Database**: Room 2.6.1 (SQLite) with KSP
* **Location Engine**: Google Play Services `FusedLocationProviderClient`
* **Architecture**: MVVM + Repository Pattern + StateFlow + Coroutines

---

## 📂 Project Structure
```
android-app/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/shreenathji/sales/
│   │   │   ├── SalesApplication.kt
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── dao/ (PartyDao, ProductDao, OrderDao, LocationDao)
│   │   │   │   │   └── entities/ (PartyEntity, ProductEntity, OrderEntity, OrderItemEntity, LocationLogEntity)
│   │   │   │   └── repository/ (SalesRepository.kt)
│   │   │   ├── receiver/ (BootReceiver.kt)
│   │   │   ├── service/ (LocationTrackingService.kt)
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       ├── navigation/ (NavGraph.kt, Screen.kt)
│   │   │       ├── screens/ (PartyListScreen, OrderFormScreen, OrderHistoryScreen)
│   │   │       ├── theme/ (Color.kt, Theme.kt, Type.kt)
│   │   │       └── viewmodels/ (PartyListViewModel, OrderFormViewModel, OrderHistoryViewModel)
│   │   └── res/ (values, xml)
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```
