# Flowna Capacitor UI

Bu klasor, `C:\Users\AKNYLMZ\Downloads\flowna.html` referans arayuzunu Capacitor ile calistirmaya hazir bir web kabugu olarak tutar.

Notlar:
- Mevcut Android uygulama Jetpack Compose + Media3 + yt-dlp ile native calisiyor.
- Bu HTML arayuzunu tamamen uygulamanin ana runtime'i yapmak icin native player, indirme, kutuphane ve guncelleme islemlerinin Capacitor plugin/bridge ile baglanmasi gerekir.
- Bu turda paket kurulumu calistirilmadi; `npm install` ve `npx cap ...` komutlari dis paket kurulumu/uretilmis Android proje olusturma adimlaridir.

Hazirlik sonrasi beklenen komutlar:

```powershell
cd capacitor-ui
npm install
npm run cap:sync
```
