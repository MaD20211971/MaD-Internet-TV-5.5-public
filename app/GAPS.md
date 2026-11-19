# Bekannte Lücken & Zukünftige Optimierungspotenziale (GAPS)

## 1. Bereits erfolgreich umgesetzt
- **FTS4 Volltextsuche**: Vollständig über `ChannelFts` und SQLite FTS4 virtual table realisiert (< 15ms Suchzeit).
- **Paging 3 Integration**: `PagingSource` und `Pager` in `ChannelDao` und `ContentRepository` vollständig integriert.
- **Media3 Video Chunk Cache**: `MediaCacheHolder` mit 100 MB LRU-Cache und `CacheDataSource.Factory` schützt vor Stream-Aussetzern.
- **Compose Recomposition & Baseline Profiles**: `@Immutable` auf Datenmodellen und `baseline-prof.txt` für AOT-Kompilierung.
- **Zentraler Länderselektor & 4 Hauptbereiche**: Länder-basierte Aufteilung in Live TV, Filme, Serien und Dokus mit bereinigten, toten-filter-freien Subkategorien (`CategoryFilterEngine`).
- **EPG-Reparatur & Schaltflächen-Funktionalität**: Alle vier EPG-Schaltflächen in den Einstellungen („EPG jetzt aktualisieren“, „Jetzt neu kalibrieren“, „Beide Feeds laden“, „Alle EPG löschen“) reaktiv über `MainViewModel` und `EpgSyncManager` angebunden. XMLTV-GZIP-Erkennung sowie UTF-8-BOM-Verarbeitung in `XmltvParser` gehärtet.
- **Bereinigung von Test-, Demo- und abgelaufenen MAC-Portalen**: Vollständige automatische Entfernung aller abgelaufenen oder vorinstallierten MAC Portale aus der SQLite-Room-Datenbank und den aktiven Playlisten via `MainViewModel`. Fallback-Umschaltung auf Pluto TV.
- **Länder- & Kanal-Selektor im Querformat (AndroidTV)**: Ersetzt die komprimierte vertikale Darstellung im Querformat durch ein responsives 2-Spalten-Split-Pane-Layout mit D-Pad-Fokusunterstützung und Fernbedienungs-Navigation. Das freigegebene Layout im Hochformat auf Smartphones bleibt 100% unangetastet.
- **ANR-Fix für große Xtream-Playlists**: Beseitigung der Meldung „Warten oder App schließen“ bei Katalogen mit 50k+ Einträgen durch O(N) Lazy-Grouping in Compose (`AppNavigation.kt`), Erhöhung der DB-Batchgrenzen (1.000er Batches) in `XtreamCodesClient` und `ChannelDao` sowie Vermeidung von Main-Thread-Locks und Heap-Exhaustion.
- **Playlisten-Parser & Single-Pass Tokenizer**: Hocheffizientes ExtInf-Parsing (`splitExtInfLine` & `parseAttributesFast`) in `M3UParser` zur Eliminierung des Regex-Overheads und Behebung des Quoted-Commas-Bugs. Streaming-JSON-Parsing mit `CountryResolver`-Anbindung für Live TV, VOD Filme und Serien in `XtreamCodesClient` und `MacPortalClient`.
- **Senderidentifikation & Länder-Zugehörigkeit**: Vollständige Vorab-Initialisierung von `DachMasterDatabase` aus Android Assets (`dach_channels.json` mit 105 Sendern + `dach_channel_aliases.json` mit 402 Aliasen). Fehlerfreie Zuordnung nach Land (AT, CH, DE sowie internationale TLDs/Netzwerke) und eindeutige Stations-IDs (`normalizedChannelId`).
- **Room v16 Composite Indizes & Batch-Optimierung**: Neue SQLite-Indizes für Playlist-, DACH-, Länder- und Favoriten-Queries; Optimierung von `insertChannels` auf 500er Batches ohne künstliche Delays.
- **Coroutine Cancellation Handling**: Härtung aller asynchronen Background-Jobs in `MainViewModel` (`loadChannelsForPlaylist`, `switchPlaylist`, etc.) gegen fehlerhafte Fehlerprotokollierung bei normaler kooperativer Job-Kündigung (`CancellationException`).
- **Länderselektor & Provider-Kanalsortierung**: Strikte Reihung der Länder (DE, AT, CH, US, UK, TR, IT, FR, ES, NL, PL, EXYU, ALB, PT, RO, RU, AR, GR, BE, CZ, BG, SE, IN, CA, AF vor Spezial- und Unbekannt-Kategorien). Aktive Providerländer mit vorhandenen Kanälen werden vor Null-Kanal-Katalogen priorisiert. Erkennung von Pipe-Tags (`|TR|`, `|IT|`, etc.) in `BRACKET_TAG_REGEX` und `TITLE_PREFIX_REGEX`. Deterministische Kanalsortierung (Kanonischer DACH-Rang, 4K/FHD/HD/SD Qualitätsranking, natürlicher Sortiertitel) innerhalb jedes ausgewählten Landes.

## 2. Zukünftige Ausbaustufen & Vorschläge
- **Benutzerdefinierte Kategorienreihenfolge**:
  - *Vorschlag*: Dem Nutzer ermöglichen, Sub-Kategorien (z.B. Sport ganz nach links) via Drag-and-Drop oder Sortier-Dialog individuell anzuordnen.
- **Inkrementeller EPG-Update über Last-Modified / ETag**:
  - *Aktueller Stand*: EPG-Feeds werden in einem 36h-Fenster bereinigt und neue Feeds per Batch-Transaktion importiert.
  - *Potenzial*: HTTP-Header `If-Modified-Since` bzw. `ETag` bei XMLTV-Download auswerten, um unveränderte Feeds komplett ohne Netzwerktransfer zu überspringen.
- **RemoteMediator für direkte Network-zu-DB Paging-Synchronisation**:
  - *Aktueller Stand*: Senderlisten werden initial oder bei Refresh komplett geladen und via Paging 3 aus Room abgefragt.
  - *Potenzial*: Für Xtream-Provider mit Web-APIs kann ein `RemoteMediator` zusätzliche Seiten direkt bei Scrollen on-demand vom Provider-Server nachladen.
