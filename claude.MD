Anda adalah seorang Mobile App Developer senior (spesialis Flutter / Android Native) dan Machine Learning Engineer (On-Device AI).

TUJUAN:
Buat kode dan arsitektur aplikasi mobile yang dapat:
1. Terhubung secara Bluetooth Serial (Classic SPP) ke perangkat ESP32 ("ESP32_Navigasi").
2. Menerima data stream JSON jarak & GPS secara realtime dari ESP32.
3. Menjalankan model Object Detection MobileNet (TensorFlow Lite / TFLite) secara offline menggunakan kamera HP secara realtime.
4. Memproses "Data Fusion" (menggabungkan label objek AI + data jarak ultrasonik dari Bluetooth) dan memutar suara berupa Text-to-Speech (TTS) ke pengguna.

SPESIFIKASI TEKNIS & INTEGRASI:

1. BLUETOOTH SERIAL LISTENER:
   - Aplikasi harus bisa mencari (scan) dan terhubung ke perangkat Bluetooth bernama "ESP32_Navigasi".
   - Menerima data berupa string JSON yang diakhiri newline ('\n').
   - Format JSON yang dikirim ESP32:
     {"depan_m": 5.0, "kanan_m": 1.2, "kiri_m": 2.0, "lat": -7.272959, "lng": 112.743773}
   - Parsing JSON ini secara konstan untuk mengupdate variabel lokal: `jarakDepan`, `jarakKanan`, `jarakKiri`.

2. MOBILENET OBJECT DETECTION (TFLITE):
   - Gunakan model TFLite Object Detection (misalnya SSD MobileNet v1/v2).
   - Ambil stream frame dari kamera HP (CameraX / Flutter Camera Plugin).
   - Deteksi objek di layar dan tentukan posisinya berdasarkan Bounding Box (Kiri, Tengah, Kanan).
   - Berikan threshold confidence (misal >= 60%) sebelum memproses objek.

3. LOGIKA KEPUTUSAN & TEXT-TO-SPEECH (TTS):
   - Jika MobileNet mendeteksi objek (misal: "chair" / kursi) di area TENGAH layar:
     - Ambil nilai `jarakDepan` dari data Bluetooth (misal: 5.0 meter).
     - Buat pesan teks dinamis dalam Bahasa Indonesia: "Ada [nama_objek] di depan, jarak [jarakDepan] meter".
   - Panggil engine Text-to-Speech (TTS) bawaan HP untuk mengucapkan kalimat tersebut.
   - Tambahkan mekanisme DEBOUNCE / COOL DOWN (misal: hanya mengucapkan suara setiap 3–5 detik sekali untuk objek yang sama) agar suara tidak tumpang tindih secara terus-menerus.

OUTPUT YANG DIHARAPKAN:
1. Struktur Proyek & Library/Dependency yang dibutuhkan (misal: flutter_bluetooth_serial, tflite_flutter, flutter_tts, camera).
2. Kode Lengkap & Terstruktur (Service Bluetooth, Controller Kamera/TFLite, dan UI Sederhana).
3. Penjelasan alur penanganan state saat Bluetooth putus/sambung kembali (Reconnection logic).