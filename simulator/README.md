# EnviMesh simulator

Node sensor virtual yang mengirim data ke Supabase, supaya database, alert, dan dashboard bisa diuji
tanpa hardware. Hanya memakai library bawaan Python (Python 3.9+), tidak perlu `pip install`.

## Skenario

| Node | Perilaku | Yang terlihat di database |
| --- | --- | --- |
| Node-01 | Gambut perlahan mengering | alert `warning` (≥ 40 cm) lalu `danger` (≥ 50 cm) |
| Node-02 | Stabil, basah (~18 cm) | status `good` |
| Node-03 | Naik-turun di sekitar 40 cm | alert `warning` |
| Node-04 | Normal, sesekali sensor gagal | alert `sensor_error` |
| Node-05 | Kirim 3 data lalu diam | status `offline` setelah 2× interval |

Suhu dan kelembapan udara mengikuti siklus harian (WIB). Flag `possible_fire` muncul kalau
muka air ≥ 55 cm, suhu ≥ 32,5 °C, dan kelembapan ≤ 62% (biasanya siang hari).

## Cara pakai

1. Salin `.env.example` menjadi `.env`, lalu isi `SUPABASE_URL` dan `SUPABASE_SECRET_KEY`
   (Supabase › Project Settings › API Keys). **Jangan commit `.env`.**
2. Dari folder ini:

```bash
python simulator.py --once    # kirim 1 putaran untuk tes
python simulator.py           # jalan terus (Ctrl+C untuk berhenti)
python simulator.py --reset   # hapus semua node simulasi beserta data dan alert-nya
```

3. Lihat hasilnya di Supabase › Table Editor (`readings`, `alerts`) atau jalankan
   `select * from node_latest;` di SQL Editor.

## Kecepatan

- Default demo: satu putaran tiap 30 detik, node dianggap offline setelah 2 menit tanpa data.
  Node-01 mencapai warning sekitar 6 menit dan danger sekitar 12 menit setelah mulai.
- Realistis: `SIM_TICK_SECONDS=1800` dan `SIM_WAKE_INTERVAL_MIN=30` (sesuai node asli yang bangun tiap 30 menit).
- `SIM_CENTER_LAT` / `SIM_CENTER_LON` mengatur lokasi node (default: area gambut Kubu Raya, Kalimantan Barat).

Sebelum demo, jalankan `--reset` dulu supaya alert lama hilang dan skenario mulai dari awal.
