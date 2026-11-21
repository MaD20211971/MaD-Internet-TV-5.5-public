# PROTOKOLL – MaD IPTV public

## Sektion 1 – GESCHÜTZTE MODULE (NICHT ÄNDERN ohne ausdrückliche Anweisung)
- **Datenbank & Persistenz:** `AppDatabase.kt`, `ChannelDao.kt`, `PlaylistDao.kt`, `EpgDao.kt`, `RecordingDao.kt`, `WatchHistoryDao.kt`, `ChannelFts.kt`, `WatchHistoryEntities.kt`
- **Core-Parser & Sync-Engine:** `M3UParser.kt`, `XtreamCodesClient.kt`, `MacPortalClient.kt`, `XmltvParser.kt`, `EpgMatcher.kt`, `EpgSyncManager.kt`, `PrioritizedSyncEngine.kt`, `ContentClassifier.kt`, `SeriesAggregationEngine.kt`
- **DACH- & Länder-Engine:** `DachMasterDatabase.kt`, `CountryResolver.kt`, `CountryNormalizer.kt`, `ConflictResolver.kt`, `StreamQualityRanker.kt`, `IptvCategoryPrioritizer.kt`, `ProviderCategoryEngine.kt`
- **Player & Recording-Kern:** `PlayerManager.kt` (Wiedergabe-/Fallback-/Watchdog-Logik), `LiveRecordingDataSource.kt`, `MediaCacheHolder.kt`, `StreamRecorderHelper.kt`, `VodWatchHistoryManager.kt`
- **ViewModel & Filter-Engine:** `MainViewModel.kt`, `ChannelFilterEngine.kt`
- **Signing & Build-Grundstruktur:** `signingConfigs` (`release`, `debugConfig`), `namespace = "com.example"` in `app/build.gradle.kts` sowie `majorCapabilities` in `metadata.json`
- **Radio-Browser & Audio-Recording:** `RadioBrowserDatabase.kt`, `RadioStationDao.kt`, `RadioBrowserApiClient.kt`, `RadioBrowserRepository.kt`, `RadioStreamRecorder.kt`
- **Live TV Light (1:1-Integration `Android-TV-Light`):** Paket `com.example.androidtvlight.*` (`AndroidTvLightHost.kt`, `TvExoPlayerView.kt`, `TvMediaPlaybackService.kt`, `TvLightRepository.kt`, `Tv3CatalogProvider.kt`, `TvEpgProvider.kt`, `TvStreamRecorder.kt`, `TvLightViewModel.kt`, `TvLightScreens.kt`, `TvEpgScreen.kt`, `TvComponents.kt`, `ui/theme/*`) & `LiveTvLightView.kt`

## Sektion 2 – OFFENE PUNKTE / NÄCHSTE SCHRITTE
- Auf Wunsch: Weitere Anpassungen an der In-App-Favoritenansicht (z. B. manuelle Sortierung der Lieblingssender oder Schnellstart-Karussell im Live-TV-Kopf)
- Regelmäßige GitHub-Sicherung nach jedem abgeschlossenen Feature-Schritt

## Sektion 3 – CHRONOLOGISCHES LOG

### 2026-11-21 – Universal Cast Engine: Google Cast, DLNA, Roku, AirPlay, Kodi, Web-Cast & Miracast
- **Erledigte Aufgaben:**
  - Neues isoliertes Modul `com.example.cast.*` aufgebaut (ohne Änderungen an geschützten Datenbank-/Parser-/PlayerManager-Kernmodulen):
    - `CastModels.kt` & `ManualCastDeviceStore.kt`: Modelle für alle 7 Cast-Protokolle sowie lokale Persistenz für manuell hinzugefügte Geräte (Kodi mit Port/Benutzer/Passwort, Roku, DLNA, AirPlay).
    - `GoogleCastOptionsProvider.kt` & `GoogleCastController.kt`: Offizielles Google Cast SDK (`play-services-cast-framework`) + `androidx.mediarouter` mit Default Media Receiver (`CC1AD845`), crash-sicher auch auf Geräten ohne Google Play Services.
    - `DlnaCastController.kt`: Native SSDP-Erkennung (`239.255.255.250:1900`), Device-XML-Parser sowie SOAP-Steuerung (`SetAVTransportURI` mit DIDL-Lite, `Play`, `Pause`, `Stop`, `SetVolume`, `SetMute`).
    - `RokuCastController.kt`: SSDP-Erkennung (`roku:ecp`) + Roku ECP HTTP-API (`:8060`, PlayOnRoku-Start & Medien-/Lautstärke-Tasten).
    - `KodiCastController.kt`: mDNS/NSD-Erkennung (`_xbmc-jsonrpc-h._tcp.`) + Kodi JSON-RPC 2.0 HTTP-API inkl. Basic-Auth.
    - `AirPlayCastController.kt`: mDNS/NSD-Erkennung (`_airplay._tcp.`) + AirPlay-1-HTTP-Steuerung (`/play`, `/rate`, `/stop`) inkl. klarem Web-Cast-Hinweis bei verschlüsseltem AirPlay 2.
    - `LocalCastProxyAndWebServer.kt`: Lokaler HTTP-Server im WLAN (`:8099`) mit IPTV-Header-/CORS-Relay-Proxy, HLS-M3U8-Manifest-Rewriting, Range-Support für lokale Dateien sowie Fullscreen-HTML5/HLS.js-Web-Cast-Empfänger inkl. automatischem Senderwechsel.
    - `MiracastLauncher.kt`: Direkter System-Aufruf (`ACTION_CAST_SETTINGS` + Samsung Smart View / Wireless-Display-Fallback).
    - `UniversalCastManager.kt`: Zentraler `StateFlow`-Orchestrator inkl. `WifiManager.MulticastLock`, WLAN-Prüfung und automatischem Senderwechsel bei aktiver Sitzung.
    - `QrCodeGenerator.kt` (`zxing:core`) & `CastControlSheet.kt`: Einheitliches Material-3-Steuer-Sheet (Geräteliste, Web-Cast QR-Code, Miracast, manuelle IP/Kodi-Eingabe, Proxy-Schalter und aktive TV-Fernbedienung).
  - Ausschließlich das **reine Cast-Symbol** (`Icons.Default.Cast` / `Icons.Default.CastConnected`) in `NexusPlayerView.kt` (`player_cast_button`) und nach Nutzer-Freigabe minimal-invasiv in `TvExoPlayerView.kt` (`exo_cast_button`) integriert; automatisches Pausieren der lokalen Wiedergabe beim Cast-Start.
  - `CHANGE_WIFI_MULTICAST_STATE` und `GoogleCastOptionsProvider`-Metadaten im `AndroidManifest.xml` sowie `play-services-cast-framework`, `androidx-mediarouter` und `zxing-core` in `gradle/libs.versions.toml` / `app/build.gradle.kts` eingetragen.
  - Unit-Tests in `UniversalCastEngineTest.kt` ergänzt.
- **Geänderte / neue Dateien:**
  - Neu: `app/src/main/java/com/example/cast/model/CastModels.kt`, `app/src/main/java/com/example/cast/ManualCastDeviceStore.kt`, `app/src/main/java/com/example/cast/UniversalCastManager.kt`, `app/src/main/java/com/example/cast/protocol/{GoogleCastOptionsProvider.kt, GoogleCastController.kt, DlnaCastController.kt, RokuCastController.kt, KodiCastController.kt, AirPlayCastController.kt, LocalCastProxyAndWebServer.kt, MiracastLauncher.kt}`, `app/src/main/java/com/example/cast/ui/{QrCodeGenerator.kt, CastControlSheet.kt}`, `app/src/test/java/com/example/UniversalCastEngineTest.kt`
  - Geändert: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/ui/components/NexusPlayerView.kt`, `app/src/main/java/com/example/androidtvlight/ui/components/TvExoPlayerView.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-19 – Web TV: PIN-Entfernung, 17 offizielle Mediatheken/Portale & Standardbrowser-Button
- **Erledigte Aufgaben:**
  - Beide Web-TV-PIN-Abfragen vollständig entfernt: Sperrbildschirm (`WebTvPinLockScreen`) + „Sperren“-Button in `WebStreamBrowserView.kt` sowie die Web-TV-PIN-Sperre in `SettingsView.kt`. Der allgemeine App-Installations-PIN (`1971`) und sonstige PINs bleiben unverändert aktiv.
  - Alle bisherigen Web-TV-Altseiten (`2ix2.com`, `kool.to`, `kool.ws`, `movie2k.cx`, `megakino18.com`, `nydus.org`, `tele24seven.com`) aus `WebTvSitesManager.kt` und `WebStreamBrowserView.kt` restlos entfernt; `SharedPreferences`-Key auf `web_tv_sites_json_v2` migriert, sodass bestehende Installationen automatisch auf den neuen Bestand umstellen.
  - Exakt **17 deduplizierte offizielle Webseiten** mit sauberen deutschen Titeln, Kategorien, Badges und Emojis hinterlegt (ARD Mediathek, ZDF, 3sat Programm, ARTE, WELT Mediathek, DF1 Live, TELE 5 Mediathek, ServusTV, Joyn Mediatheken, SendungVerpasst.de, DMAX, WDR Aktuelle Stunde, hr-fernsehen, KiKA Kinderfilme, netzwelt Mediathek, ARD YouTube, ZDF YouTube).
  - Ersten Unter-Tab von *„2ix2 & Web TV“* auf **„Mediatheken & TV“** umbenannt und dynamisch aus `WebTvSitesManager.sitesFlow` gespeist; Standard-Startseite des In-App-Browsers auf `https://www.ardmediathek.de/` umgestellt.
  - Neuen Button **„Standardbrowser“ / „Im Standardbrowser öffnen“** (`ACTION_VIEW` + `CATEGORY_BROWSABLE`) in den Webseiten-Karten, der Kachel-Übersicht, der freien URL-Eingabe sowie in der Toolbar und der Vollbild-Steuerleiste des In-App-Browsers ergänzt.
- **Geänderte Dateien:**
  - `app/src/main/java/com/example/data/storage/WebTvSitesManager.kt`, `app/src/main/java/com/example/ui/components/SettingsView.kt`, `app/src/main/java/com/example/ui/components/WebStreamBrowserView.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-19 – Live TV Light EPG: Responsive & variable Kartenhöhe auf Smartphones (`TvEpgScreen.kt`)
- **Erledigte Aufgaben:**
  - Ursache behoben: In `EpgChannelProgramCard` lagen Logo, Sender-Badges und alle 4 Aktions-Buttons in einer einzigen `Row`, wodurch schmale Handy-Displays die Badges vertikal umbrechen ließen, die Karte stark aufblähten und das Senderlogo vertikal in die Mitte rutschte.
  - `EpgChannelProgramCard` per `BoxWithConstraints` (`< 540 dp`) auf ein kompaktes Smartphone-Layout mit variabler, inhaltsabhängiger Höhe (`wrapContentHeight`) umgestellt: Logo oben links bündig, Sendername/Badges/Uhrzeit einzeilig ohne Umbruch, Sendungstitel, optionale Beschreibung (`0 dp` wenn leer), Live-Fortschritt, *„Gleich danach“* und eigene kompakte Aktionsleiste unten; das breite Android-TV-Layout bleibt ab `540 dp` erhalten.
  - `epg_auto_update_bar`, `EpgChannelTimelineRowCard` und `EpgProgramDetailDialog` ebenfalls responsiv mit variabler Höhe (max. `84 %` Bildschirmhöhe + scrollbarer Inhalt + Sticky Header/Footer) abgesichert.
- **Geänderte Dateien:**
  - `app/src/main/java/com/example/androidtvlight/ui/screens/TvEpgScreen.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-19 – Umbenennung zu „MaD IPTV public“, Paketpfad `iptv.publicapp`, Download-Ordner & neues App-Icon / Android-TV-Banner
- **Erledigte Aufgaben:**
  - App-Name in `app/src/main/res/values/strings.xml`, `settings.gradle.kts`, `metadata.json` sowie im In-App-Branding (`SettingsView.kt`, `TopBarSection.kt`, `TvNavigationRail.kt`, `StreamPlaybackService.kt`, `PlaylistBackupManager.kt`) auf **MaD IPTV public** umgestellt.
  - Installationspfad (`applicationId`) in `app/build.gradle.kts` auf **`iptv.publicapp`** geändert (`namespace = "com.example"` und `signingConfigs` unverändert; `FileProvider` nutzt automatisch `iptv.publicapp.fileprovider`).
  - Lokalen Aufnahme-/Download-Ordner in `DownloadStorageHelper.kt` (`SUBFOLDER = "MaDIPTVPublic"`) sowie Dialoghinweise in `NexusPlayerView.kt` auf `Downloads/MaDIPTVPublic` (inkl. `Downloads/MaDIPTVPublic/Radio`) umgestellt; `LocalM3uFolderScanner.kt` scannt `MaDIPTVPublic` sowie abwärtskompatibel `MaDIPTVPrivat` und `MaDIPTV`.
  - Neues Benutzerbild („MaD TV / IPTV 5.5“, `#090D1A`) verarbeitet zu:
    - Hochauflösendem 16:9 Android-TV-Banner `tv_banner.png` (`640×360 px`, Lanczos) für die Android-TV-Favoritenleiste (`android:banner`).
    - Adaptive Icon (`ic_launcher_foreground_img.png` `512×512 px`, `ic_launcher_foreground.xml` mit `66dp` Safe-Zone, `ic_launcher_background.xml` in `#090D1A`) für Smartphones & runde Google-TV-Favoritenleiste (komplettes Motiv unbeschnitten zentriert).
    - Allen 5 Mipmap-Dichten (`mdpi`–`xxxhdpi`) als echte RGBA-PNGs (`ic_launcher.png` quadratisch + `ic_launcher_round.png` kreisförmig maskiert).
    - Einheitlichem In-App-Logo `app_logo.png` (`512×512 px`).
  - Geschützte Kernmodule (`AppDatabase`, `PlayerManager.kt`, `RadioStreamRecorder.kt`, `com.example.androidtvlight.*` etc.) blieben unangetastet.
- **Geänderte Dateien:**
  - `app/build.gradle.kts`, `settings.gradle.kts`, `metadata.json`, `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable/{tv_banner.png, app_logo.png, ic_launcher_foreground_img.png, ic_launcher_foreground.xml, ic_launcher_background.xml}`
  - `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher.png, ic_launcher_round.png}`
  - `app/src/main/java/com/example/data/storage/{DownloadStorageHelper.kt, LocalM3uFolderScanner.kt}`
  - `app/src/main/java/com/example/service/StreamPlaybackService.kt`, `app/src/main/java/com/example/data/parser/PlaylistBackupManager.kt`
  - `app/src/main/java/com/example/ui/components/{SettingsView.kt, TopBarSection.kt, TvNavigationRail.kt, NexusPlayerView.kt}`
  - `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Live TV Light: 1:1-Übernahme aus GitHub-Projekt `Android-TV-Light`
- **Erledigte Aufgaben:**
  - Alle 16 Kotlin-Module aus `https://github.com/MaD20211971/Android-TV-Light` unverändert 1:1 in das isolierte Paket `com.example.androidtvlight.*` übernommen (inkl. Original-ExoPlayer `TvExoPlayerView`, `TvMediaPlaybackService`/`TvPlayerManager`, `TvLightRepository`, `Tv3CatalogProvider`, `TvEpgProvider`, `TvEpgScreen`, `TvStreamRecorder`, `TvLightScreens`, `TvComponents`, `TvLightViewModel`, `AndroidTvLightTheme`) sowie die Fonts `outfit.ttf` & `plus_jakarta_sans.ttf`.
  - `AndroidTvLightHost.kt` für die nahtlose Einbettung angepasst: Installations-PIN-Abfrage (`1971`) übersprungen (Antwort 1), App-Beenden-EXIT-Button aus der Übersicht entfernt während Vollbild-Player-Exit aktiv bleibt (Antwort 2), sauberer `TvPlayerManager`-Stopp beim Verlassen der Rubrik (Vorschlag A) und `onFullscreenChanged`-Callback für echten Vollbild-Modus (Vorschlag B).
  - `LiveTvLightView.kt` vollständig durch `AndroidTvLightHost` ersetzt.
  - `AppNavigation.kt` um Doppelton-Schutz (pausiert Haupt-Player beim Wechsel in `LIVE_TV_LIGHT`) und `isLiveTvLightFullscreen` (blendet TopBar, Bottom-Navigation & TV-Rail im Vollbild aus) erweitert.
  - `TvMediaPlaybackService` in `AndroidManifest.xml` registriert sowie `datastore-preferences`, `media3-exoplayer-dash` & `media3-common` (`1.5.1`) aktiviert.
  - Kompilierung per `compile_applet` erfolgreich abgeschlossen (`Build succeeded`).
- **Geänderte / neue Dateien:**
  - Neu: `app/src/main/java/com/example/androidtvlight/**` (16 Dateien), `app/src/main/res/font/{outfit.ttf, plus_jakarta_sans.ttf}`
  - Geändert: `app/src/main/java/com/example/ui/components/LiveTvLightView.kt`, `app/src/main/java/com/example/ui/navigation/AppNavigation.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Live TV light: Direkter 1:1-Import von 2ix2.com (Streams + Original-Logos + Persistenter JSON-Cache)
- **Erledigte Aufgaben:**
  - `LiveTvLight2ix2Scraper.kt` neu erstellt: Multi-Page-WordPress-Scraper für alle Seiten von `2ix2.com/category/deutsche-tv-sender/` und `2ix2.com/category/oesterreichische-tv/`, extrahiert exakte Sender-`pageUrl`, bereinigten Namen und originales `2ix2.com`-Logo (`/wp-content/uploads/...`, inkl. `data-lazy-src` / `data-src` / `srcset` und Skipping von Base64-Platzhaltern) sowie automatische Kategorie-, Badge- & Markenfarben-Zuordnung.
  - `LiveTvLightCatalogStore.kt` neu erstellt (Vorschlag A): Persistenter JSON-Cache (`SharedPreferences`) für 0 ms Sofortstart mit allen importierten `2ix2.com`-Sendern, echten `pageUrls` und Originallogos.
  - `LiveTvLightCatalog.kt` um `pageUrl`, `fallbackLogoUrl`, reaktiven `channelsFlow`, `initializeFromCache(...)` und `applyScrapedCatalog(...)` erweitert (alle bekannten 58 Seed-Sender erhalten echte `2ix2.com`-`pageUrl` & Originallogos; zusätzliche Sender auf `2ix2.com` werden automatisch ergänzt).
  - `LiveTvLightStreamResolver.kt` auf direkte `2ix2.com`-`pageUrl`-Auflösung umgestellt (HTTP + bis zu 2-stufige Player-Iframes + schlanker asset-/ad-blockender Headless-Interceptor mit 5,5 s Fenster, rein nativer Nexus ExoPlayer ohne Embedded-Fallback), inkl. `Cookie`-/ `Referer`-/`Origin`-Weitergabe an `PlayerManager` und Range-freiem Retry bei `#EXTM3U`-Prüfung (400/405/416).
  - `LiveTvLightView.kt` auf `channelsFlow.collectAsStateWithLifecycle()` und ein 3-Stufen-Logo-System umgestellt (1. originales `2ix2.com`-Logo mit `Referer`- & `User-Agent`-Header in Coil → 2. HD-`fallbackLogoUrl` → 3. Marken-Badge).
  - `LiveTvLightPerformanceTest.kt` auf 10 Unit-Tests erweitert (inkl. Multi-Page-Scraper-, JSON-Cache-, Cookie-Header- & Base64-/Escaped-M3U8-Tests).
- **Geänderte / neue Dateien:**
  - Neu: `app/src/main/java/com/example/data/livetvlight/LiveTvLight2ix2Scraper.kt`, `app/src/main/java/com/example/data/livetvlight/LiveTvLightCatalogStore.kt`
  - Geändert: `app/src/main/java/com/example/data/livetvlight/LiveTvLightCatalog.kt`, `app/src/main/java/com/example/data/livetvlight/LiveTvLightStreamResolver.kt`, `app/src/main/java/com/example/ui/components/LiveTvLightView.kt`, `app/src/test/java/com/example/LiveTvLightPerformanceTest.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Live TV light Ausland-/Geo-Block-Fix: 100 % geo-freie 2ix2-Priorisierung & echte `#EXTM3U`-Verifikation
- **Erledigte Aufgaben:**
  - `LiveTvLightCatalog.kt` für alle **58 Sender (47 DE + 11 AT)** auf **geo-freie 2ix2-/Relay-Priorität** umgestellt (kein blinder Instant-Start mehr auf geo-gesperrte ARD/ZDF/MDR/WDR/SWR/ORF-CDNs) + `alternateResolverKeys` (Mehrfach-Slug-Kandidaten) für jeden Sender ergänzt.
  - `LiveTvLightStreamResolver.kt` auf echte **`#EXTM3U`-2-KB-Inhaltsprüfung** (`verifyValidM3u8Content` / `isValidM3u8Payload`) umgestellt (Vorschlag 1): HTML-Fehlerseiten, 403-Geo-Blocks und tote Redirects werden zuverlässig vor dem Player aussortiert.
  - Dynamische Header-Weitergabe (`registerDynamicHeaders` / `getHeadersForUrl`) inkl. Host-basierter Weitergabe für `.ts`-Segmente sowie Header-Capture im Asset-blockenden Headless-WebView-Extractor ergänzt.
  - Einmaligen stillen Hintergrund-Slug-Abgleich (`ensureBackgroundCatalogSyncOnce`) beim Öffnen von `LiveTvLightView.kt` aktiviert.
  - In `PlayerManager.kt` dynamische Live-TV-Light-Header in `resolveHeaders(url)` eingebunden, `isResolvedRelayUrl` für das Live-Fenster genutzt und ein schnelles **6-Sekunden-Buffering-Failover** für `live_tv_light` ergänzt.
  - `LiveTvLightPerformanceTest.kt` auf 7 Unit-Tests erweitert.
- **Geänderte Dateien:**
  - `LiveTvLightCatalog.kt`, `LiveTvLightStreamResolver.kt`, `PlayerManager.kt`, `LiveTvLightView.kt`, `LiveTvLightPerformanceTest.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Live TV light Sofortreparatur: ARD/ZDF-Startfix & automatischer Player-Fallback
- **Erledigte Aufgaben:**
  - Veraltete ARD-/ONE-Primär-URLs in `LiveTvLightCatalog.kt` auf aktuelle offizielle Endpunkte (`daserste-live.ard-mcdn.de/.../de/master.m3u8`, `mcdn-one.ard.de/ardone/hls/master.m3u8`) aktualisiert und Geo-/CDN-Fallbacks (`/int/`, `/de/high/`, `/none/veryhigh/`) für ARD, ZDF, ZDFneo, ZDFinfo, 3sat und PHOENIX ergänzt.
  - In `PlayerManager.kt` das kurze `3,5 s`-Live-Edge-Fenster (`isShortWindowHls`) streng auf `hopslan.com`-Relays begrenzt, damit offizielle ARD-/ZDF-HLS-Streams wieder ihr reguläres stabiles Live-Fenster nutzen.
  - Automatische Fehler-Fallback-Kette (`LiveTvLightStreamResolver.resolveFallbackAfterError` + `resolveFallbackAfterErrorAsync`) in `PlayerManager.kt` integriert: Schlägt ein Stream fehl, wird die fehlerhafte URL gemerkt und nahtlos per Parallel-Racing auf den nächsten funktionierenden Fallback-/Resolver-Stream umgeschaltet.
  - Unit-Test in `LiveTvLightPerformanceTest.kt` um ARD-/ZDF-URL-Check & Auto-Fallback-Test erweitert.
- **Geänderte Dateien:**
  - `LiveTvLightCatalog.kt`, `LiveTvLightStreamResolver.kt`, `PlayerManager.kt`, `LiveTvLightPerformanceTest.kt`, `PROTOKOLL.md`

### 2026-11-18 – Live TV light Überarbeitung: 47 DE + 11 AT Sender & Maximale Performance
- **Erledigte Aufgaben:**
  - `LiveTvLightCatalog.kt` (`@Immutable`) modular ausgelagert und auf **exakt 47 deutsche Sender** (inkl. bisher fehlender Sender wie RTLup, VOXup, MTV, Welt der Wunder, Sky Sport News HD, Bloomberg TV, MDF.1 sowie Bugfix für fehlerhaften SPORT1-Link) und **exakt 11 österreichische Sender** (ORF 1, ORF 2, ORF III, ServusTV, Ländle TV, FS1, M4TV, W24 TV, Tirol TV, OKTO TV, DORF TV) erweitert (= **58 Sender**, 0 ms Offline-Start).
  - `LiveTvLightStreamResolver.kt` (Turbo-Resolver) neu gebaut: Zero-Delay-Fast-Path (0 ms) für direkte/gecachte Streams, optimierter OkHttp-Connection-Pool & Dispatcher, In-Flight-Request-Deduplizierung, paralleles **Fastest-Wins-Probing** (`HEAD`/Range-Probe + HTTP-Extractor zeitgleich), Asset-/Ad-blockender Headless-WebView-Fallback (blockiert Bilder/CSS/Fonts/Tracker) sowie paralleler Hintergrund-Aktualisieren-Sync beider Kategorieseiten.
  - `LiveTvLightView.kt` verschlankt & auf maximale Grid-Performance umgestellt: `SubcomposeAsyncImage` durch leichtgewichtiges `AsyncImage` mit Coil-Zielgröße (`128x128`) + sofortigem Marken-Badge ersetzt, `derivedStateOf`, `contentType`, Refresh-Button im Header ohne jegliche Quellen-/URL-Anzeige, kompakte `DE`/`AT`-Badges ohne zusätzliche Länder-Tab-Zeile.
  - Gezielte Anpassung im `LIVE_TV_LIGHT_PLAYLIST_ID`-Zweig von `MainViewModel.kt` (Doppel-Resolve entfernt, alle 58 DE+AT Sender inkl. korrektem `countryCode` unterstützt) und `AppNavigation.kt` (Zapping über alle 58 Sender).
  - Unit-Tests (`LiveTvLightPerformanceTest.kt`) ergänzt.
- **Geänderte / neue Dateien:**
  - Neu: `app/src/main/java/com/example/data/livetvlight/LiveTvLightCatalog.kt`, `app/src/main/java/com/example/data/livetvlight/LiveTvLightStreamResolver.kt`, `app/src/test/java/com/example/LiveTvLightPerformanceTest.kt`
  - Geändert: `app/src/main/java/com/example/ui/components/LiveTvLightView.kt`, `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt`, `app/src/main/java/com/example/ui/navigation/AppNavigation.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Integration von radio-browser.info, lokaler Radio-DB, Deutschrock-Priorisierung & 1:1-MP3-Aufnahme
- **Erledigte Aufgaben:**
  - Rubrik **Radio** dauerhaft als festen Tab in der unteren Smartphone-Navigationsleiste (`BottomNavigationSection.kt`) und in der Android-TV-Seitenleiste (`TvNavigationRail.kt`) verankert (immer sichtbar inkl. Zähler-Badge).
  - Isolierte lokale Room-Datenbank (`RadioBrowserDatabase.kt`, `RadioStationEntity.kt`, `RadioStationDao.kt`) ohne Eingriff in die geschützte `AppDatabase.kt` implementiert.
  - Öffentliche `radio-browser.info`-API (`RadioBrowserApiClient.kt`) mit automatischem Mirror-Fallback (`de1`, `de2`, `at1`, `fi1`, `all`), Sync-Batch (Deutschrock + Top DACH) und weltweiter Online-Suche angebunden (`RadioBrowserRepository.kt`).
  - **Deutschrock-Sender** automatisch erkannt und ganz oben in der Radioliste priorisiert (+ eigener `🎸 Deutschrock`-Chip, `Nur MP3`-Filter, Codec-/Bitrate-Badges und 1-Klick-Lokal-Speicherung in `RadioExplorer.kt`).
  - Dedizierten 1:1-Audio-Recorder (`RadioStreamRecorder.kt`) mit ICY-Metadaten-Demuxer (knackfreie `.mp3` in exakt der gelieferten Original-Stream-Bitrate ohne Upsampling + Live-Songtitel-Anzeige) in `MainViewModel.kt`, `NexusPlayerView.kt` und `AppNavigation.kt` integriert; Speicherung unter `Downloads/MaDIPTVPrivat/Radio/` mit Eintrag im Aufnahme-Manager.
  - Unit-Tests (`RadioBrowserAndRecorderTest.kt`) für Deutschrock-Erkennung, Original-Bitrate-Formatwahl, JSON-Parsing und ICY-Demuxing ergänzt.
- **Geänderte / neue Dateien:**
  - Neu: `app/src/main/java/com/example/data/radio/{RadioStationEntity.kt, RadioStationDao.kt, RadioBrowserDatabase.kt, RadioBrowserApiClient.kt, RadioBrowserRepository.kt, RadioStreamRecorder.kt}`, `app/src/test/java/com/example/RadioBrowserAndRecorderTest.kt`
  - Geändert: `AppContainer.kt`, `MainViewModel.kt`, `BottomNavigationSection.kt`, `TvNavigationRail.kt`, `RadioExplorer.kt`, `NexusPlayerView.kt`, `AppNavigation.kt`, `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`

### 2026-11-18 – Umbenennung, Paketpfad `privat.iptv`, neues Icon/TV-Banner & Favoriten-Badge
- **Erledigte Aufgaben:**
  - App-Name in `strings.xml`, `settings.gradle.kts`, `metadata.json` sowie In-App-Branding (`SettingsView.kt`, `TvNavigationRail.kt`, `TopBarSection.kt`, `StreamPlaybackService.kt`, `PlayerManager.kt`, `PlaylistBackupManager.kt`) auf **MaD IPTV Privat** umgestellt.
  - `applicationId` in `app/build.gradle.kts` auf **`privat.iptv`** geändert (Parallel-Installation ohne Konflikt; `FileProvider` nutzt automatisch `privat.iptv.fileprovider`).
  - Lokalen Aufnahme-/Download-Unterordner in `DownloadStorageHelper.kt`, `LocalM3uFolderScanner.kt` (abwärtskompatibel inkl. `MaDIPTV`) und `NexusPlayerView.kt` auf `Downloads/MaDIPTVPrivat` aktualisiert.
  - Neues Piloten-/MiG-16-Motiv aus Benutzerbild zu `app_logo.png` (512×512), Adaptive Icon (`ic_launcher_foreground_img.png`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml` mit gesampeltem `#ADC0DE`), allen 5 Mipmap-Dichten (`ic_launcher.png` + rund maskierte `ic_launcher_round.png`) sowie 16:9 Android-TV-Banner (`tv_banner.png`, 640×360 px mit Schriftzug „MaD IPTV Privat“) für Android-TV- & Google-TV-Favoritenleiste generiert.
  - Favoriten-Zähler-Badge (`favoriteCount`) in `BottomNavigationSection.kt`, `TvNavigationRail.kt` und `AppNavigation.kt` integriert.
  - `FUNKTIONSUEBERSICHT.md` und `PROTOKOLL.md` initial im Projekt-Root angelegt.
- **Geänderte / neue Dateien:**
  - `app/build.gradle.kts`, `settings.gradle.kts`, `metadata.json`, `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable/app_logo.png`, `ic_launcher_foreground_img.png`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `tv_banner.png`
  - `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` & `ic_launcher_round.png`
  - `app/src/main/java/com/example/data/storage/DownloadStorageHelper.kt`, `LocalM3uFolderScanner.kt`
  - `app/src/main/java/com/example/service/StreamPlaybackService.kt`, `app/src/main/java/com/example/data/player/PlayerManager.kt`, `app/src/main/java/com/example/data/parser/PlaylistBackupManager.kt`
  - `app/src/main/java/com/example/ui/components/SettingsView.kt`, `TopBarSection.kt`, `TvNavigationRail.kt`, `BottomNavigationSection.kt`, `NexusPlayerView.kt`
  - `app/src/main/java/com/example/ui/navigation/AppNavigation.kt`
  - `FUNKTIONSUEBERSICHT.md`, `PROTOKOLL.md`
