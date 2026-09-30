# Dokumentasi API Airis Locator

Endpoint API ini digunakan untuk komunikasi data koordinat lokasi antara perangkat ESP32 dan Mobile App. 

**Base URL** (saat pengembangan lokal):  
`http://localhost:3000/api/location`

---

## 1. GET - Mengambil Data Lokasi (Untuk Mobile App)

Endpoint ini digunakan untuk membaca data lokasi (koordinat) terakhir yang dikirimkan oleh perangkat ESP32. Mobile App dapat melakukan _polling_ ke endpoint ini.

- **Method**: `GET`
- **Endpoint**: `/api/location`
- **Headers**: Tidak perlu headers khusus

### Response Sukses (200 OK)
```json
{
  "coordinate_airis": "-6.200000, 106.816666" 
}
```
*(Catatan: Jika belum ada data yang pernah dikirim oleh ESP32 atau Mobile App sejak server berjalan, nilainya akan berisi pesan `"Belum ada koordinat yang diterima"`)*

---

## 2. POST - Mengirim Data Lokasi (Mobile App / HP)

Fitur pada Mobile App (Android) sudah ditambahkan untuk mengambil data lokasi real-time dari GPS HP dan melakukan HTTP POST ke endpoint secara berkala (setiap 5 detik).
- **Method**: `POST`
- **Endpoint**: `/api/location`
- **Body**: Menggunakan key `coordinate_airis`.
- **Implementasi**: Menggunakan `FusedLocationProviderClient` dari Google Play Services dan `OkHttp` untuk request ke server.

---

## 2. POST - Mengirim Data Lokasi (Untuk ESP32)

Endpoint ini digunakan oleh perangkat ESP32 untuk mengirimkan atau memperbarui data lokasi terbarunya. Sesuai dengan spesifikasi, disarankan untuk melakukan pengiriman data (POST) secara berkala (misal: setiap 5 detik).

- **Method**: `POST`
- **Endpoint**: `/api/location`
- **Headers**: 
  - `Content-Type: application/json`

### Body Request (JSON)
Pastikan key yang digunakan adalah `coordinate_airis`.

```json
{
  "coordinate_airis": "-6.200000, 106.816666"
}
```

### Response Sukses (200 OK)
```json
{
  "success": true,
  "message": "Koordinat berhasil diupdate",
  "coordinate_airis": "-6.200000, 106.816666"
}
```

### Response Error 
**400 Bad Request** (Field `coordinate_airis` tidak ada atau salah nama):
```json
{
  "success": false,
  "message": "Field 'coordinate_airis' tidak ditemukan dalam JSON"
}
```

**400 Bad Request** (Format body bukan JSON yang valid):
```json
{
  "success": false,
  "message": "Format request harus berupa JSON yang valid"
}
```

---

## Contoh Kode Implementasi pada ESP32 (C/C++ Arduino IDE)

Berikut adalah contoh fungsi sederhana untuk mengirimkan koordinat dari ESP32 menggunakan HTTP Client.

```cpp
#include <HTTPClient.h>
#include <WiFi.h>

void sendLocation(String latitude, String longitude) {
  // Pastikan ESP32 sudah terkoneksi ke WiFi
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    
    // Ganti dengan IP Address server/komputer Anda atau domain public (jika sudah di-deploy)
    http.begin("http://192.168.1.X:3000/api/location"); 
    http.addHeader("Content-Type", "application/json");

    // Membuat format data koordinat dan JSON string
    String coordinate = latitude + ", " + longitude;
    String httpRequestData = "{\"coordinate_airis\":\"" + coordinate + "\"}";

    // Melakukan POST request
    int httpResponseCode = http.POST(httpRequestData);
    
    if (httpResponseCode > 0) {
      Serial.print("HTTP Response code: ");
      Serial.println(httpResponseCode);
      String payload = http.getString();
      Serial.println(payload);
    } else {
      Serial.print("Error code: ");
      Serial.println(httpResponseCode);
    }
    
    // Bebaskan resource HTTP Client
    http.end();
  } else {
    Serial.println("WiFi Disconnected");
  }
}
```
