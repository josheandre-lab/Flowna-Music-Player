# Backup Workflow

Bu proje icin bundan sonra geri donus noktalarini git uzerinden tutuyoruz.

## Kural

Her buyuk degisiklikten once:

1. Mevcut calisan durumu commit et
2. Restore tag olustur
3. GitHub'a push et

## Tek komut

```powershell
.\scripts\create-restore-point.ps1 -Message "Player duzenlemesi oncesi" -Push
```

## Sonuc

Bu komut:

1. Butun degisiklikleri stage eder
2. Gerekiyorsa commit atar
3. `restore-YYYYMMDD-HHMMSS-etiket` formatinda tag olusturur
4. Branch'i ve tag'i GitHub'a yollar

## Geri donus

Bir restore point'e donmek icin:

```powershell
git checkout <tag-adi>
```

veya o etiketten yeni branch acmak icin:

```powershell
git checkout -b geri-donus/<ad> <tag-adi>
```
