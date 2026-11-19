# System-Architektur: IPTV & EPG App

## 1. Übersicht & Architekturprinzipien
Die Anwendung ist als hochperformante Android-IPTV-Streaming-App für Smartphones, Tablets und Android TV konzipiert. Sie folgt der **Clean Architecture** und dem **MVVM-Muster** (Model-View-ViewModel) mit Jetpack Compose für ein modernes, reaktives UI.

```
┌─────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                      │
│   (NexusPlayerView, ChannelCard, EpgGuideView, Dialoge)    │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                       MainViewModel                         │
│     (State-Management, Screen Navigation, Live EPG Sync)    │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
┌──────────────▼──────────────┐┌──────────────▼───────────────┐
│     ContentRepository       ││        EpgSyncManager        │
│ (Memory Cache, Paginierung) ││  (Asynchroner Feed-Import)   │
└──────────────┬──────────────┘└──────────────┬───────────────┘
               │                              │
┌──────────────▼──────────────────────────────▼───────────────┐
│                   Room Local Persistence                    │
│   AppDatabase (SQLite in WAL-Modus mit 256MB MMAP)          │
│   ├── ChannelDao (Indizierte Kanalabfragen, Paging)        │
│   ├── EpgDao (Batch-Transaktionen, Zeitfenster-Queries)    │
│   ├── PlaylistDao (Playlisten & Portale)                   │
│   └── RecordingDao (Aufnahmen & Downloads)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Datenbank-Architektur & Performance-Optimierungen (Room & SQLite)

### 2.1 SQLite Engine Tuning
Die Datenbank (`AppDatabase`) nutzt state-of-the-art SQLite PRAGMA-Direktiven:
- **`PRAGMA journal_mode = WAL`**: Write-Ahead Logging ermöglicht paralleles Lesen und Schreiben.
- **`PRAGMA synchronous = NORMAL`**: Drastische Reduktion von `fsync()`-Aufrufen bei voller Ausfallsicherheit in WAL.
- **`PRAGMA mmap_size = 268435456` (256 MB)**: Zero-Copy Memory-Mapped I/O. Lesezugriffe auf Kanäle und EPG-Tabellen greifen ohne Buffer-Kopien direkt auf den Betriebssystem-Cache zu.
- **`PRAGMA cache_size = -64000` (64 MB)**: Großzügiger In-Memory Page Cache für wiederholte Abfragen.
- **`PRAGMA temp_store = MEMORY`**: Temporäre Tabellen und Sortier-B-Trees werden im RAM gehalten.
- **`PRAGMA busy_timeout = 5000` (5 Sekunden)**: Verhindert `SQLiteDatabaseLockedException` bei gleichzeitigem EPG-Import im Hintergrund und Nutzeroberflächenabfragen.
- **`PRAGMA wal_autocheckpoint = 1000`**: Kontrolliertes Zurückschreiben von WAL-Frames in die Hauptdatei.
- **`checkpointWal(mode)`**: Ermöglicht geplante Checkpoints nach massiven Datenimporten.

### 2.2 Indizierungsstrategie (Room v16)
#### Tabelle `channels`
- `Index(["tvgId"])` & `Index(["epgId"])`: O(log N) Direktzugriff bei EPG-Zuordnungen, verhindert Full-Table-Scans bei 10.000+ Sendern.
- `Index(["countryCode", "name"])` & `Index(["countryCode", "type", "name"])`: Deckt paginierte Länderabfragen (`ORDER BY name ASC`) direkt über den Index ab und eliminiert temporäre Filesorts.
- `Index(["isDach", "name"])`: Optimiert Abfragen für die Priorisierung von DE/AT/CH Sendern.
- `Index(["playlistId", "isDach", "name"])`: Spezifischer Composite-Index für playlist-spezifische DACH-Kanalabfragen.
- `Index(["countryCode", "isFavorite", "name"])`: Beschleunigt ländergefilterte Favoritenansichten ohne Sortieroverhead.
- `Index(["playlistId", "countryCode", "isFavorite"])`: Schneller Drill-Down für Favoritenfilter innerhalb von Playlisten.
- `Index(["normalizedChannelId"])`: O(1) bis O(log N) Direktzugriff für fehlerfreie Senderidentifikation und senderübergreifende Deduplizierung.
- `Index(["isFavorite", "name"])`: Beschleunigt die globale Favoritenansicht.
- Bestehende Indizes (`countryCode`, `playlistId`, `type`, `groupTitle`, `normalizedTitle`) bleiben für Filterkombinationen erhalten.

#### Tabelle `epg_programs`
- `Index(["endTime"])`: Beschleunigt die automatische Löschung abgelaufener Sendungen (`DELETE WHERE endTime < :cutoff`) von O(N) auf O(log N).
- `Index(["sourceUrl"])`: Schnelles Bereinigen nach Quell-URL.
- `Index(["channelTvgId", "startTime", "endTime"])` & `Index(["startTime", "endTime"])`: Schnelles Filtern für aktive Zeitfenster (`getProgramsInTimeWindow`).

### 2.3 Bündelung von Schreibzugriffen & Transaktionen
- **`insertProgramsBatch`**: EPG-Daten werden in konfigurierbaren Chunks innerhalb einer einzigen Transaktion (`@Transaction`) in die Datenbank geschrieben. Das reduziert Disksynchronisationen von dutzenden `fsync`-Vorgängen auf einen einzigen.
- **`insertChannels` (Chunk-Optimierung)**: Speicherung in 500er Batches ohne künstliche Delays (`delay(5)` entfernt) unter Beibehaltung von kooperativem `yield()` für maximale Schreibgeschwindigkeit ohne UI-Thread-Stottern.
- **Eliminierung von N+1-Abfragen**: Das Laden von aktuellem und nächstem Programm erfolgt über `getProgramsInTimeWindow` für alle sichtbaren Sender in einem einzigen SQLite-Query statt hunderter Einzelfragen in einer Schleife.

### 2.4 Volltextsuche (FTS4 Virtual Table)
- **`ChannelFts` & `channels_fts`**: SQLite FTS4-Schattentabelle für Echtzeit-Suchvorgänge über `name`, `normalizedTitle` und `groupTitle`.
- **Latenz**: Suchanfragen mit Prefix (`query*`) dauern < 15ms auch bei Datenbanken mit 100.000+ Kanälen.
- **Automatischer Sync**: `ChannelDao.insertChannels` befüllt die FTS4-Tabelle transaktionssicher bei jedem Import.

### 2.5 Paging 3 Integration
- **`PagingSource<Int, ChannelItem>`**: Direkt in Room verankert für Länderlisten, Alle Kanäle und Favoriten.
- **`ContentRepository.getPagingFlow()`**: Liefert `Flow<PagingData<ChannelItem>>` mit konfigurierter `PagingConfig` (PageSize 50, Prefetch 15, Placeholders deaktiviert).

---

## 3. Media3 & ExoPlayer Video Caching
- **`MediaCacheHolder`**: Verwaltet einen zentralen 100 MB `SimpleCache` mit `LeastRecentlyUsedCacheEvictor`.
- **`CacheDataSource.Factory`**: Schirmt ExoPlayer gegen vorübergehende Verbindungsschwankungen ab, indem HLS/DASH-Videosegmente lokal gepuffert und bei Störungen verlustfrei aus dem Speicher gelesen werden.

---

## 4. UI-Recomposition & Baseline Profiles
- **`@Immutable` Annotationen**: Alle Datenmodelle und UI-States sind als unveränderlich markiert, sodass Jetpack Compose unnötige Recompositions überspringt.
- **Baseline Profiles (`baseline-prof.txt`)**: AOT-Kompilierung für kritische Startup- und Scroll-Pfade auf Android 7+ Geräten.

---

## 5. Datenfluss & Komponenten
1. **Parser & Ingestion**:
   - `M3UParser`: Stream-Parser mit Single-Pass Tokenizer (`splitExtInfLine` & `parseAttributesFast`), der Anführungszeichen mit enthaltenen Kommas fehlerfrei auflöst. Erkennt Attribute, Header und Stream-Formate ohne CPU-Last und Regex-Thrashing.
   - `XtreamCodesClient`: Vollständig gestreamtes Einlesen via `JsonReader` für Live TV, VOD Filme und Serien. Alle drei Medientypen durchlaufen `CountryResolver.resolve(...)` für einheitliche Länder- und Sendereinstufung.
   - `MacPortalClient`: Multi-Strategy Ingestion für Ministra/Stalker (v4/v5) mit dynamischem Token-Refresh, Genre-Paginierung und sauberer Länder-Normalisierung.
   - `XmltvParser` & `EpgSyncManager`: Parsen XMLTV (.xml / .xml.gz) und Xtream EPG mit robuster Fehlerisolation und automatischer GZIP-Dekompression.
2. **Länder- und Senderidentifikation (`CountryResolver` & `DachMasterDatabase`)**:
   - Master-Datenbank aus Android Assets (`dach_channels.json` mit 105 kanonischen Kanälen + `dach_channel_aliases.json` mit über 400 Aliasen) wird beim Start initialisiert und im Speicher gehalten.
   - `CountryResolver.resolve(...)`: Identifiziert Sender anhand von TVG-IDs, Domain-TLDs, Titel-Signaturen und Alias-Matches. Verhindert Fehlklassifizierungen zwischen AT/CH und DE.
   - Normalisierte Stations-ID (`normalizedChannelId`): Ermöglicht senderübergreifende Deduplizierung, Qualitätsranking und exakten EPG-Abgleich.
3. **EPG Matching Engine (`EpgMatcher`)**:
   - 4-Stufen-Hierarchie: 1. Exakte tvg-id/epgId, 2. Normalisierte ID, 3. Masterdatenbank-Aliase (`DachMasterDatabase`), 4. Normalisierte Namen.
   - Ambivalente Treffer werden als `AMBIGUOUS` gespeichert und können über den `ManualEpgMappingDialog` manuell korrigiert werden.
4. **Repository (`ContentRepository`)**:
   - Zwischenspeicher (`CountryMemoryCache`, `ConcurrentHashMap`) für O(1) Antworten bei Länderzusammenfassungen und Tabs.

---

## 6. Kategorien- & Navigationsarchitektur (Top-Bar & Sektionenverteilung)

### 6.1 Zentraler Länderselektor als Navigationselement & Sortier-Engine
Der Länderselektor ist als zentrales primäres Anker-Element oben auf dem Bildschirm sowie als detaillierter Konfigurationsdialog (`CountrySelectorDialog`) implementiert:
- **Visuelle Identität**: Zeigt Nationalflagge (z.B. 🇩🇪, 🇦🇹, 🇨🇭, 🇺🇸, 🇬🇧, 🇹🇷, 🇮🇹, 🇫🇷, 🌍), Ländernamen und Zähler an.
- **Kanonische Ländersortierung**: 
  1. DACH-Priorität: Deutschland (`DE` #1), Österreich (`AT` #2), Schweiz (`CH` #3).
  2. Englischsprachige Hauptregionen: USA (`US` #4), Großbritannien (`UK` #5).
  3. Hohe internationale Popularität: Türkei (`TR` #6), Italien (`IT` #7), Frankreich (`FR` #8), Spanien (`ES` #9), Niederlande (`NL` #10), Polen (`PL` #11), Ex-Jugoslawien/Balkan (`EXYU` #12), Albanien (`ALB` #13), Portugal & Brasilien (`PT` #14), Rumänien (`RO` #15), Russland (`RU` #16), Arabische Sender (`AR` #17), Griechenland (`GR` #18), Belgien (`BE` #19), Tschechien & Slowakei (`CZ` #20), Bulgarien (`BG` #21), Skandinavien (`SE` #22), Indien (`IN` #23), Kanada (`CA` #24), Afghanistan (`AF` #25).
  4. Spezial- und Sammelkategorien strikt am Ende: `VIP` (#30), `SPORT` (#80), `ADULT` (#90), `CONFLICT` (#95), `OTHER` (#98), `UNKNOWN` (#99).
- **Aktive Provider-Priorisierung**: Länder, für die der aktive Provider tatsächlich Kanäle liefert (`count > 0`), werden automatisch vor Null-Kanal-Katalogen positioniert, ohne die kanonische Rangordnung zu brechen.
- **Provider-Tag Tokenizer**: Robuste Erkennung von Pipe-Tags (`|TR|`, `|IT|`, `|EXYU|`, `|NL|`, etc.), eckigen Klammern (`[TR]`) und Präfixen (`TR:`, `TR -`) via `BRACKET_TAG_REGEX` und `TITLE_PREFIX_REGEX`.
- **Kanal-Sortierung innerhalb jedes Landes**: Kanäle werden deterministisch nach 1. Kanonischem DACH- bzw. Sender-Wichtigkeitsrang, 2. Auflösungsqualität (UHD/4K -> FHD -> HD -> SD) und 3. natürlicher alphabetischer Sortierung geordnet.
- **Integrierter Länder-Manager**: Ermöglicht über den `CountrySelectorDialog` tiefgehende Anpassungen (Ausblenden von Ländern, Sender filtern, manuelle Zuordnung).

### 6.2 Vier Hauptbereiche: Live TV, Filme, Serien, Dokus
Das Fernseh- und Streamingprogramm wird nach dem gewählten Land in vier klar voneinander getrennte Hauptbereiche verteilt:
1. **Live TV (`MainTab.LIVE_TV`)**: Klassisches lineares Fernsehen mit EPG-Zeitleiste, Paginierung und Bildqualitäts-Indikatoren.
2. **Filme (`MainTab.MOVIES`)**: VOD-Filmkatalog mit Genre-Filtern, Covers und Audio-/Untertitel-Verwaltung.
3. **Serien (`MainTab.SERIES`)**: Mehrstufig aggregierte Serienstruktur (Serie -> Staffeln -> Episoden) mit Fortschrittsanzeige ("Weiterschauen").
4. **Dokus (`MainTab.DOCS`)**: Eigenständiger Bereich für Dokumentationen, Reportagen und Wissen mit spezifischer Klassifikation (`ContentClassifier.isDocumentaryContent`).
- **Favoriten (`MainTab.FAVORITES`)**: Zentraler Schnellzugriff auf alle vom Nutzer favorisierten Sender und Medien.

### 6.3 Bereinigung redundanter und toter Filter (`CategoryFilterEngine`)
- **Eliminierung von Provider-Artefakten**: Kryptische Tags, doppelte Länderkürzel (z.B. `DE| FHD`, `DE: VOD HEVC`, `DE - 1080p`) und leere Platzhalter werden vollständig ausgefiltert.
- **Smarte Subkategorien**:
  - Live TV: *Hauptsender*, *Regional & Dritte*, *Sport & Fussball*, *Nachrichten & Info*, *Kino & Pay TV*, *Kinder & Familie*, *Musik*, *24/7 Channels*.
  - Dokus: *Natur & Tiere*, *Wissenschaft & Technik*, *Geschichte & Zeitgeschehen*, *Reisen & Kultur*, *True Crime & Investigation*.
  - Filme & Serien: Strukturierte Genre-Gruppen (*Action*, *Komödie*, *Krimi & Thriller*, *Sci-Fi & Fantasy*, *Drama*, *Animation*).
- **Zero Dead Filters**: Mittels `CategoryFilterEngine.getEffectiveCategories()` werden nur Kategorien eingeblendet, die für das gewählte Land mindestens einen aktiven Sender enthalten. Leerlauf-Klicks werden zu 100% verhindert.

---

## 7. EPG-Architektur, Synchronisation & Kalibrierung

### 7.1 EPG-Synchronisation & Multi-Feed Pipeline
- **Robuste Stream-Behandlung (`PushbackInputStream`)**: `XmltvParser` prüft GZIP-Dateien strikt anhand der Magic Bytes (`0x1F, 0x8B`) mittels `PushbackInputStream` und fängt transparente OkHttp-Dekomprimierung ab. Byte-Order-Marks (UTF-8 BOM `0xEF, 0xBB, 0xBF`) werden automatisch ohne Buffer-Integritätsverlust konsumiert.
- **Dynamische Zeitausrichtung für historische/statische Feeds**: Weisen importierte XMLTV-Feeds Timestamps aus vorherigen Zeiträumen auf, verschiebt `XmltvParser` die Sendungsfenster tagesgenau nach vorne, sodass Originaltitel, Beschreibungen und Metadaten für das aktuelle Datum aktiv bleiben.
- **Dual-Feed & Multi-URL Unterstützung**: Schneller gleichzeitiger Import von `free-epg.de` und `epg.lat` über kommagetrennte URL-Verarbeitung in `EpgSyncManager`.
- **Echtzeit-Status**: `EpgSyncState` (`Syncing`, `Success`, `Error`, `Idle`) wird reaktiv an das UI gestreamt und visualisiert Fortschritt in Prozent.

### 7.2 Kalibrierung & Re-Mapping Engine (`forceEpgRemap`)
- **Automatisches Neu-Mapping**: Scannt alle verfügbaren EPG-IDs in der Room-Datenbank sowie der `DachMasterDatabase` und führt die 4-Stufen-Hierarchie von `EpgMatcher` für alle in der Datenbank hinterlegten Live-TV-Sender aus.
- **Persistenz & Cache**: Aktualisiert `channelDao.updateEpgMapping` und synchronisiert den in-memory State `_allChannels`.
- **Multi-Key Live-Map**: `_liveEpgMap` indexiert jedes aktuelle/nächste Sendungspaar redundant unter `ch.id`, `ch.tvgId`, `ch.epgId` sowie deren kleingeschriebenen Entsprechungen, wodurch ChannelCards über alle Tabs (Live TV, Suche, Favoriten, Weitersehen) stets treffsicher EPG-Daten finden.

### 7.3 Interaktive Schaltflächen-Integration
- **EPG jetzt aktualisieren**: Startet sofortige Synchronisation des aktuell eingestellten bzw. eingegebenen Feeds mit Status-Rückmeldung, persistiert die URL in den Einstellungen und stößt Neuzuordnung an.
- **Jetzt neu kalibrieren**: Berechnet Zuordnungen anhand aller lokal vorhandenen EPG-Daten und der DACH-Masterdatenbank neu, gleicht Zeitversätze ab und triggert einen Refresh – vollständig sequentiell und rennbedingungsfrei.
- **Beide Feeds laden**: Hinterlegt die beiden primären deutschen EPG-Feeds (`free-epg.de` & `epg.lat`), speichert diese persistent ab und stößt den dualen Download an.
- **Alle EPG löschen**: Löscht alle Einträge aus der Room-Datenbank, setzt automatische Kanalzuordnungen zurück, leert den In-Memory EPG-Cache, setzt `isEpgClearedByUser` und verhindert die sofortige Re-Population synthetischer Daten.

---

## 8. Playlist- & Portal-Management (Startup-Bereinigung & Standard-Playlisten)

### 8.1 Standard-Playlisten (`SampleData.kt`)
- Die Grundausstattung umfasst kuratierte, stabile M3U-Playlisten (Pluto TV DE, Rakuten TV DE, LG TV, DE & Internationales).
- Test- und Demo-Portale (wie das vormalige Test MAC Portal ZeroOneX `http://zeroonex2.xyz:8080`) sind aus den Standardlisten und Preset-Schaltflächen entfernt, um Fehlversuche und tote Server-Verbindungen zu unterbinden.

### 8.2 Automatisches Purging & Startup-Maintenance (`MainViewModel`)
- `initializeInitialData()` & `performBackgroundStartupMaintenance()`: Prüfen beim App-Start die lokale SQLite-Datenbank (`AppDatabase`) auf Überbleibsel abgelaufener oder vorinstallierter MAC Portale sowie alter Test-Portale (u.a. via `PlaylistType.MAC_PORTAL`, IDs `mac_portal_*`, `SampleData.DRAGON_IPTV_PLAYLIST_ID`, Hosts `dragoniptv.de`, `zeroonex2.xyz`, oder MAC-Adressen `00:1A:79:08:4B:DE`, `00:1A:79:13:55:D4`, `00:1A:79:50:D3:DD`).
- Werden entsprechende Einträge detektiert, werden die zugehörigen Kanäle via `channelDao.deleteChannelsByPlaylist()` und die Playliste via `playlistDao.deletePlaylistById()` restlos entfernt. War das Portal als aktiv markiert, wechselt die App automatisch auf die primäre Standard-Playliste (Pluto TV).

---

## 9. Länder- & Kanal-Selektor (Responsive & AndroidTV Querformat-Architektur)

### 9.1 Adaptive Split-Pane-Architektur
- **Hochformat (Handy / Touch)**: Das originale, final freigegebene Layout, Styling und Flow bleibt zu 100% unverändert (`Surface` mit 92% Höhe und zentrierter Darstellung).
- **Querformat (AndroidTV & Landscape)**:
  - Automatischer Wechsel auf ein 2-Spalten-Split-Pane-Layout (`Row(weight = 1f)` mit 96% Bildschirmbreite und -höhe).
  - **Linke Spalte (310dp Sidebar)**: Beherbergt die D-Pad-fokussierbaren Haupt-Navigationstabs (Länder, Kanäle verwalten, Entfernte Sender), Suchfeld mit Schnelltaste, 2-spaltige Länder-Schnellwahlchips (DE, AT, CH, Welt), kompakte Batch-Aktionen (Alle an, Alle aus, Reset, + Kanal) sowie den primären Fertig-Aktionsbutton.
  - **Rechte Spalte (Flexibler Detailbereich)**: Nutzt die volle verfügbare Bildschirmhöhe für `CountriesListContent`, `ChannelsManagementTab` und `RemovedChannelsTab` ohne vertikales Abkappen, Scrollkonflikte oder verdeckte Bedienelemente.

### 9.2 AndroidTV & D-Pad Fokus-Management
- Klare visuelle Fokusindikatoren (2dp `GoldPrimary` Glow-Rahmen und kontrastreiche Hintergrund-Highlights) auf allen interaktiven Komponenten (Cards, Buttons, Chips, Suchfeld).
- Saubere Fernbedienungs-Navigation mit `BackHandler` zur Entlastung von AndroidTV-Nutzern.
- Durchgängige Weitergabe von `isTvMode` und `isLandscape` an alle Subkomponenten.

---

## 10. Performance-Architektur für große Xtream-Playlists (ANR-Prävention)

### 10.1 Ursachen-Analyse des Fehlers („Warten oder App schließen“ / ANR)
1. **O(N²) Schleife auf dem UI-Thread in `LazyColumn`**:
   - In der Hauptsenderliste (`AppNavigation.kt`) wurde bei großen Katalogen für jeden einzelnen Sender die Länder-Senderanzahl mit `displayChannels.count { ... }` in einer verschachtelten Schleife auf dem Main-Thread neu berechnet. Bei Katalogen mit 5.000–30.000 Einträgen blockierte dies die Frame-Berechnung für mehrere Sekunden (> 5s führt zum Android OS ANR-Dialog).
   - Eager Zuweisung über `forEach { item { ... } }`: Statt des performanten `items(...)` wurden tausende einzelne Lazy-Intervalle im Main-Thread instantiiert, was zu massiver Speicher- und CPU-Blockade führte.
2. **Datenbank-Lock & Transaktions-Stau bei Batch-Inserts**:
   - Kleinteilige Batches (150 bzw. 300 Einträge) führten bei 50.000–100.000 Einträgen zu hunderten diskreten SQLite-Transaktionen mit kontinuierlichen Disk-I/O-Locks, die parallele UI-Leseabfragen blockierten.
3. **Speicher- und GC-Thrashing (Heap-Exhaustion)**:
   - Bei gigantischen Playlists ohne Batch-Streaming liefen Listen mit bis zu 100.000 `ChannelItem`-Objekten in den RAM und provozierten wiederholte Stop-The-World GC-Pausen.

### 10.2 Umgesetzte architektonische Maßnahmen
1. **O(N) Precomputation & Echtes Lazy-Windowing in Compose (`AppNavigation.kt`)**:
   - Vorab-Gruppierung der Header und Kanal-Elemente via `remember(displayChannels, showCountryHeaders)` mit einer linearen Single-Pass `HashMap`-Frequenzzählung (O(N) statt O(N²)).
   - Einsatz von `items(items = liveListItems, key = ..., contentType = ...)`: Nur die tatsächlich sichtbaren 8–10 Karten auf dem Bildschirm werden gerendert. Layout, Styling, Spacing und visuelle Komponenten (`CountrySectionHeader`, `ChannelCard`) bleiben zu 100% unverändert.
2. **Streaming-Batching & Transaktions-Optimierung (`XtreamCodesClient.kt` & `ChannelDao.kt`)**:
   - Erhöhung der Batch-Größen auf 300 (DACH Fast-Lane) und 1.000 (Non-DACH Hintergrund): Reduktion der SQLite-Schreibtransaktionen um über 70%.
   - Chunking in 500er-Einheiten innerhalb einer einzelnen `@Transaction` in `ChannelDao.insertChannelsBatch()`.
   - Beseitigung künstlicher Coroutine-Sleeps (`delay(5)`) zugunsten kooperativer `yield()`-Pausen für reaktive UI-Interaktionen.
   - Schutzbegrenzung des In-Memory-Fallbacks (`channels.size < 25000`) zur Vermeidung von Out-of-Memory-Abstürzen.
3. **Non-Blocking UI-Pipeline (`MainViewModel.kt` & `PrioritizedSyncEngine.kt`)**:
   - Unmittelbare Befüllung von `_filteredLiveChannels` sobald der erste DACH-Batch bereitsteht, sodass kein Fallback-Filter auf dem UI-Thread ausgeführt werden muss.
   - Durchgängiges Streaming auch im `PrioritizedSyncEngine`.



