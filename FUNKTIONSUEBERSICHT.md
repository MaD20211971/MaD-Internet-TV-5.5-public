# FUNKTIONSÜBERSICHT – MaD IPTV public

## 1. Zweck & Identität der App
- **App-Name:** MaD IPTV public
- **Installationspfad (`applicationId`):** `iptv.publicapp` (parallel installierbar zu `privat.iptv` und anderen IPTV-Apps; interner Code-Namespace `com.example`)
- **Plattformen:** Android Smartphones, Tablets, Android TV / Google TV & Fire TV (`LEANBACK_LAUNCHER` + Adaptive Icons + 16:9 TV-Banner)
- **Version:** V5.5 (`versionCode = 55`)
- **Lokaler Speicherordner für Aufnahmen & Downloads:** `Downloads/MaDIPTVPublic`

## 2. Architektur
- **Pattern:** MVVM mit zentralem `AppContainer` (Constructor Injection), `MainViewModel` (`StateFlow` / `collectAsStateWithLifecycle`) und modularen Data-/Domain-/UI-Schichten
- **UI:** 100 % Jetpack Compose (Material 3, Dark Aurora Studio Theme, Edge-to-Edge, Adaptive Layout mit `BottomNavigationSection` für Mobile und `TvNavigationRail` für Android TV / Wide-Screens)
- **Persistenz:** Room Database (`AppDatabase` mit FTS-Suche + isolierte `RadioBrowserDatabase` für lokal gespeicherte `radio-browser.info`-Sender) + lokale JSON-DACH-Stammdaten
- **Media-Engine:** AndroidX Media3 / ExoPlayer (`PlayerManager`, `LiveRecordingDataSource`, `StreamPlaybackService` Foreground-Service mit Standby-Schutz, `RadioStreamRecorder` für 1:1-MP3-Aufnahme inkl. ICY-Demuxer)

## 3. Fertiggestellte Funktionen & Screens
- **Live TV:** Länder-/Bouquet-Filter, DACH-Master-Datenbank, Konfliktauflösung, Qualitäts-Ranking, Sortiermodi (`ChannelSortDialog`), Länder-Manager (`CountrySelectorDialog`), Favoriten-Chip & Senderkarten (`ChannelCard`)
- **Live TV Light (1:1-Integration von `Android-TV-Light` im Paket `com.example.androidtvlight.*`):**
  - Vollständige, unveränderte 1:1-Übernahme aus dem GitHub-Projekt **`Android-TV-Light`** (`AndroidTvLightHost`, `TvExoPlayerView`, `TvMediaPlaybackService` / `TvPlayerManager`, `TvLightRepository`, `Tv3CatalogProvider`, `TvEpgProvider`, `TvEpgScreen`, `TvStreamRecorder`, `TvLightScreens`, `TvComponents`, `TvLightViewModel`, Fonts `outfit.ttf` & `plus_jakarta_sans.ttf`).
  - Nahtlos in die Rubrik **Live TV Light** eingebettet – **ohne** Installations-PIN-Abfrage (`1971`) und **ohne** App-Beenden-EXIT-Button in der Übersicht (Player-Vollbild-Exit bleibt voll aktiv).
  - **Doppelton-Schutz & echter Vollbild-Modus:** Beim Wechsel in *Live TV Light* pausiert der Haupt-Player von *MaD IPTV public*; beim Verlassen stoppt `TvPlayerManager` sauber. Im Vollbild-Player werden äußere TopBar, Bottom-Navigation & TV-Seitenleiste automatisch ausgeblendet.
  - **Responsives EPG (`TvEpgScreen`):** Variable, inhaltsabhängige Höhe der EPG-Karten und des Detail-Dialogs auf Smartphones & Android TV (`BoxWithConstraints`, oben bündiges Senderlogo, kein vertikales Umbruch-Quetschen, leere Beschreibungen beanspruchen 0 dp).
- **EPG-Programmzeitschrift (`EpgGuideView`, `EpgScheduleScreen`):** XMLTV-Import, automatisches & manuelles EPG-Mapping (`ManualEpgMappingDialog`), Zeitzonen-/Offset-Konfiguration
- **VOD – Filme, Serien & Fortsetzen (`MovieExplorer`, `SeriesExplorer`, `VodContinueWatchingScreen`):** Serien-Aggregation nach Staffeln/Episoden, Watch-History & Resume-Funktion, Offline-MP4-Downloads
- **Radio (`RadioExplorer` – fest in der unteren Hauptleiste & TV-Seitenleiste):**
  - Direkte Integration von **https://www.radio-browser.info/** mit automatischem Mirror-Fallback (`de1`, `de2`, `at1`, `fi1`, `all`).
  - **Lokale Radio-Datenbank (`RadioBrowserDatabase`):** Speichert Deutschrock- & Top-DACH-Sender lokal inkl. manueller **Sync-Funktion** (mit Zeitstempel & Favoritenerhalt) sowie **weltweiter Online-Suche** mit 1-Klick-Lokal-Speicherung.
  - **Deutschrock-Priorisierung:** Deutschrock-Sender stehen automatisch ganz oben in der Liste (+ eigener `🎸 Deutschrock`- und `Nur MP3`-Filter, Codec-/Bitrate-Badges auf jeder Karte).
  - **1:1-MP3-Aufnahme im Player (`RadioStreamRecorder`):** Verlustfreie Aufnahme in exakt der gelieferten Original-Stream-Bitrate (z. B. 32 kbps → 32 kbps, 128 kbps → 128 kbps) inkl. ICY-Metadaten-Demuxer (knackfreie MP3 + Live-Songtitel), gespeichert unter `Downloads/MaDIPTVPublic/Radio/` und direkt im Aufnahme-Manager abspielbar.
- **Favoriten:** Eigener Favoriten-Tab inkl. Live-Zähler-Badge in Smartphone-Navigationsleiste (`BottomNavigationSection`) & Android-TV-Seitenleiste (`TvNavigationRail`) sowie Favoriten-Filter in der TopBar
- **Playlisten & Quellen:** M3U/M3U8 (URL & lokal via `LocalM3uManagerView`), Xtream Codes, MAC/Stalker-Portale (`MacPortalConnectionDialog`), Import-Assistent (`PlaylistImportAssistantDialog`), verschlüsseltes Backup/Restore (`PlaylistBackupManager`, `BackupDialog`)
- **Web TV (`WebStreamBrowserView`, `WebTvSitesManager`):** Direkt zugänglich **ohne Web-TV-PIN** (nur der allgemeine Installations-PIN bleibt bestehen); enthält **17 offizielle deutsche Mediatheken, Live-TV- & YouTube-Portale** (ARD Mediathek, ZDF, 3sat, ARTE, WELT, DF1 Live, TELE 5, ServusTV, Joyn, SendungVerpasst.de, DMAX, WDR, hr-fernsehen, KiKA, netzwelt, ARD & ZDF YouTube) inkl. Kategorie-Kachelansicht (*Mediatheken & TV*), manueller Verwaltung (Hinzufügen/Bearbeiten/Löschen), In-App-Vollbild-Browser, Chrome Custom Tab und dediziertem **„Im Standardbrowser öffnen“**-Button.
- **Universal-Cast in beiden Playern (`com.example.cast.*`, `NexusPlayerView`, `TvExoPlayerView`):**
  - Zeigt im Player ausschließlich das kompakte **Cast-Symbol** (`Icons.Default.Cast` / `Icons.Default.CastConnected` in Cyan bei aktiver Verbindung).
  - Unterstützt alle 7 Cast-Technologien: **Google Cast / Chromecast** (offizielles Cast SDK + MediaRouter, Default Media Receiver `CC1AD845`), **DLNA / UPnP** (SSDP-Erkennung + SOAP `AVTransport` / `RenderingControl`), **Roku** (SSDP + ECP-API), **Kodi** (mDNS/NSD + JSON-RPC 2.0 inkl. Basic-Auth), **AirPlay** (mDNS/NSD + AirPlay 1 HTTP + Hinweis bei AirPlay 2), **Web-Cast** (eingebetteter lokaler HTML5/HLS.js-Empfänger auf Port `8099` inkl. QR-Code & automatischem Senderwechsel) sowie **Miracast / Smart View** (direkter System-Aufruf).
  - **IPTV-Kompatibilitäts-Proxy:** Lokaler HTTP-Relay-Proxy mit Header-Weiterleitung, CORS-Freigabe, HLS-M3U8-Manifest-Rewriting und HTTP-Range-Support (auch für lokale Aufnahmen/Downloads).
  - **Remote-Steuerung & Manuelle Geräte:** Pausiert lokale Wiedergabe automatisch beim Cast-Start, bietet Play/Pause, Lautstärke, Stumm & Trennen direkt über das Cast-Symbol und erlaubt das manuelle Speichern von Geräten per IP/Port/Login.
- **Aufnahme & Download-Manager (`RecordingTimerAndDownloadManagerDialogs`, `StreamRecorderHelper`, `RadioStreamRecorder`):** Live-TV-Aufnahmen & VOD-Downloads nach `Downloads/MaDIPTVPublic` sowie Radio-MP3-Mitschnitte nach `Downloads/MaDIPTVPublic/Radio`
- **Branding & TV-Homescreen:** Neues „MaD TV / IPTV 5.5“-Neon-Motiv in allen 5 Mipmap-Dichten (quadratisch + echt rund maskiert mit Alpha-Transparenz), Adaptive Icon (`66dp` Safe-Zone auf `#090D1A`) für Smartphones & Google-TV-Favoritenleiste, hochauflösendes 16:9 Android-TV-Banner (`tv_banner.png`, `640×360 px`) sowie einheitliches In-App-Logo (`app_logo.png`, `512×512 px`)

## 4. Berechtigungen (`AndroidManifest.xml`)
- Netzwerk & Wiedergabe: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`
- Lokaler Speicher / Downloads / Lokale M3U: `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`
- TV & Hardware-Features: `android.software.leanback` (`required="false"`), `android.hardware.touchscreen` (`required="false"`)
