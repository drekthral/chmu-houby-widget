# Houby ČHMÚ – Android widget

Widget na plochu Androidu, který jednou denně ukáže **pravděpodobnost růstu hub** pro vybrané místo
podle mapy ČHMÚ [Pravděpodobnost růstu hub](https://www.chmi.cz/namerena-data/pravdepodobnost-rustu-hub).

| Stupeň | Význam | Barva |
|---|---|---|
| 1 | velmi nízká | ![#d93629](https://placehold.co/12x12/d93629/d93629.png) `#d93629` |
| 2 | nízká | ![#f88d51](https://placehold.co/12x12/f88d51/f88d51.png) `#f88d51` |
| 3 | střední | ![#ffffbf](https://placehold.co/12x12/ffffbf/ffffbf.png) `#ffffbf` |
| 4 | vysoká | ![#86cb66](https://placehold.co/12x12/86cb66/86cb66.png) `#86cb66` |
| 5 | velmi vysoká | ![#219b51](https://placehold.co/12x12/219b51/219b51.png) `#219b51` |

Widget zobrazuje stupeň a jeho barvu, datum, ke kterému mapa platí, a výřez mapy (~40 km) se značkou místa.

## Instalace

1. Stáhni `app-debug.apk` z [Releases](../../releases) nebo z artefaktů posledního běhu
   [Actions](../../actions).
2. Nainstaluj (povol instalaci z neznámých zdrojů).
3. Dlouze podrž plochu → Widgety → **Houby ČHMÚ**, vyhledej obec a vyber ji.
4. Klepnutím na widget data kdykoli obnovíš.

## Jak to funguje

- **Data:** `GET https://data-provider.chmi.cz/api/data/rizika/houby` vrací JSON
  `{"timeLabel": "so 10. 10.", "img": "data:image/png;base64,..."}`. PNG (2755 × 1600 px) pokrývá
  obdélník lon 12.0425–19.1177, lat 48.4037–51.0597 v projekci Web Mercator
  (viz `boundingBoxes` v [map init](https://data-provider.chmi.cz/api/map/init/bio-pocasi.bio-pocasi-rust-hub?client=mobile)).
  Widget přepočítá souřadnice na pixel, přečte jeho barvu a přiřadí ji ke stupni legendy.
- **Kdy:** mapa se generuje dopoledne (PNG z 10. 10. 2026 má čas vytvoření 10:45 SELČ).
  WorkManager budí aplikaci každých 30 min, ale síť použije jen pokud widget nemá dnešní mapu a je
  po 11:00 (`RefreshWorker.FETCH_AFTER`). Prakticky tedy jedno stažení denně mezi 11:00 a 11:30.
- **Výběr místa:** vyhledávání obcí přes [Nominatim](https://nominatim.openstreetmap.org/) (OpenStreetMap).

## Build

```
./gradlew assembleDebug
```

Vyžaduje JDK 17 a Android SDK 35 (nebo stačí otevřít v Android Studiu). GitHub Actions sestaví APK
při každém pushi; tag `v*` vytvoří release.

## Zdroje a licence dat

- Mapa: © ČHMÚ, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Mapa je orientační model
  vláhových a teplotních podmínek pro mykorhizní houby, ne záznam skutečných nálezů.
- Geokódování: © přispěvatelé OpenStreetMap.
