# EnviMesh – Aplikasi Android (Kotlin + Jetpack Compose)

Panduan teknis aplikasi Android EnviMesh. Untuk gambaran proyek secara keseluruhan, lihat [README utama](../README.md).

Aplikasi ini awalnya prototipe MIT App Inventor (`EnviMesh.aia`), lalu ditulis ulang ke Kotlin agar koneksi Bluetooth LE ke perangkat MeshCore lebih terkontrol.

## Cara membuka

1. Clone repo, lalu di Android Studio pilih **File › Open** dan arahkan ke folder root repo (yang berisi `gradlew`).
2. Tunggu Gradle sync (pertama kali perlu internet untuk mengunduh dependensi).
3. Kalau Android Studio menawarkan upgrade AGP/Kotlin, boleh diterima. Versi yang dipakai:
   AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7, Compose BOM 2024.06.00, minSdk 26, JDK 17.
4. Run ke HP Android atau emulator.

> Proyek ini ditulis tanpa bisa di-build di lingkungan pembuatnya. Kalau ada error saat sync atau build,
> biasanya cukup menyesuaikan versi di `build.gradle.kts`.

## Melihat tampilan

- **Preview (tanpa menjalankan app):** buka `ui/Previews.kt`, klik **Split** atau **Design** di kanan atas editor,
  lalu **Build & Refresh** kalau diminta. Kedelapan layar muncul berdampingan. Peta tampil sebagai kotak
  placeholder di Preview; peta asli hanya muncul saat app dijalankan.
- **Emulator:** Device Manager › Create Virtual Device (mis. Pixel 7, API 34) › Run ▶.
- **HP asli:** aktifkan Developer options + USB debugging (atau Wireless debugging), sambungkan, Run ▶.
  Bluetooth asli hanya bisa diuji di HP, emulator tidak punya Bluetooth.

## Mode online (Supabase)

App bisa membaca node dan alert langsung dari Supabase (data yang dikirim simulator atau gateway),
diperbarui tiap 10 detik, dan memunculkan notifikasi di HP saat ada alert baru.

1. Buka `local.properties` di root proyek (file ini tidak ikut ke GitHub) dan tambahkan:
   ```
   supabase.url=https://xxxx.supabase.co
   supabase.publishableKey=sb_publishable_xxxx
   ```
   Pakai **publishable key**, bukan secret key. Database hanya mengizinkan key ini membaca data.
2. **File › Sync Project with Gradle Files**, lalu Run ▶.
3. Mode online aktif otomatis. Bisa dimatikan di **Settings › Online data (Supabase)** untuk kembali ke simulasi lokal.

Catatan: notifikasi muncul selama app masih berjalan (termasuk di latar belakang). Kalau app ditutup paksa,
notifikasi berhenti. Untuk itu nanti perlu WorkManager atau push notification.

## Pemetaan layar App Inventor → Kotlin

| App Inventor | Kotlin (Compose) | File |
| --- | --- | --- |
| Intro | `IntroScreen` (otomatis lanjut setelah 1,5 detik) | `ui/screens/IntroAndScanScreens.kt` |
| Screen1 (scan) | `ScanScreen` | `ui/screens/IntroAndScanScreens.kt` |
| Home | `HomeScreen` | `ui/screens/NodeScreens.kt` |
| Monitoring | `MonitoringScreen` (menampilkan node yang dipilih) | `ui/screens/NodeScreens.kt` |
| Setup | `SetupScreen` (validasi input, Save benar-benar menyimpan di app) | `ui/screens/NodeScreens.kt` |
| Map | `MapScreen` (OpenStreetMap + marker per status) | `ui/screens/FieldScreens.kt` |
| Alert | `AlertScreen` (Navigate ke peta, Confirm) | `ui/screens/FieldScreens.kt` |
| Settings | `SettingsScreen` (Disconnect kembali ke scan) | `ui/screens/FieldScreens.kt` |
| BottomNavigation | `NavigationBar` di `EnviMeshApp` | `ui/navigation/EnviMeshApp.kt` |

## Struktur

```
app/src/main/java/com/envimesh/app/
├─ MainActivity.kt
├─ data/
│  ├─ Models.kt            // SensorNode, NodeConfig, IncidentAlert, status()
│  └─ MeshRepository.kt    // interface + FakeMeshRepository (data dummy)
├─ ble/
│  └─ MeshCoreProtocol.kt  // DRAFT frame MeshCore + format pesan ALERT EnviMesh
└─ ui/
   ├─ EnviMeshViewModel.kt // satu sumber state untuk semua layar
   ├─ navigation/EnviMeshApp.kt
   ├─ components/Common.kt
   ├─ screens/...
   └─ theme/Theme.kt       // warna dari versi App Inventor
```

UI hanya bicara ke `MeshRepository`. Untuk data asli, buat `BleMeshRepository` yang mengimplementasikan
interface yang sama, lalu ganti `FakeMeshRepository()` di `EnviMeshViewModel`.

## Perbaikan dibanding versi App Inventor

- Nilai sinyal Bluetooth negatif (mis. `-58 dBm`) dengan label Strong/Fair/Weak.
- Threshold default 40 cm (usulan: waspada di −0,4 m, sesuai PP 57/2016).
- Status node dihitung dari threshold yang diset di Setup, bukan ditulis manual.
- Monitoring menampilkan node yang diklik, bukan selalu Node-02.
- Peta memakai lokasi GPS HP; node simulasi diacak dalam radius ±1,5 km dari lokasi itu
  (tombol **Randomize** untuk mengacak ulang). Node-01 selalu di atas threshold supaya Alert bisa didemokan.
- Typo diperbaiki (Coordinates, through, moisture, Longitude, Language, threshold).

## Langkah berikutnya

1. **BLE ke team mesh device**: scan, connect, subscribe TX, kirim `APP_START`, lalu `SYNC_NEXT_MESSAGE`.
   Gunakan `ble/MeshCoreProtocol.kt` dan cocokkan dulu byte-nya dengan https://docs.meshcore.io/companion_protocol/
   serta uji pakai nRF Connect.
2. **Izin runtime**: minta `BLUETOOTH_SCAN` dan `BLUETOOTH_CONNECT` (Android 12+) sebelum scan.
3. **Peta offline**: siapkan tile area lokasi (osmdroid mendukung arsip tile offline).
4. **Jarak di Alert**: ambil posisi HP (FusedLocation/LocationManager) lalu hitung jarak ke koordinat insiden.
5. **Simpan Setup permanen**: DataStore, lalu kirim konfigurasi ke node.
