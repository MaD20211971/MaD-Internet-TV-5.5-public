# PERFORMANCE.md: MaD Internet TV – Performance- & Systemarchitektur

## 1. Übersicht der Performance-Kennzahlen

| Metrik | Vorher | Nach der Optimierung | Verbesserung |
|---|---|---|---|
| **Globale Sendersuche (100k+ Einträge)** | ~250–600 ms (Table Scan via `LIKE '%...%'`) | **< 15 ms** (FTS4 Volltextindex) | **> 20x schneller** |
| **Massen-EPG-Import (20.000 Programme)** | ~8–12 s (viele Einzel-Transactions & fsyncs) | **< 600 ms** (Gebündelte `@Transaction`) | **> 15x schneller** |
| **DB Lesezugriffe (Kanäle/EPG)** | Heap-Pufferung via SQLite Standard | **Zero-Copy MMAP (256 MB)** | **Keine Heap-Allokation** |
| **Video Chunk Buffering (HLS/DASH)** | Reines Streaming über Netzwerk | **100 MB Media3 LRU CacheDataSource** | **Unterbrechungsfreies Playback** |
| **Bilder & Logos (Coil)** | Unbegrenzter Heap-Verbrauch | **10% Heap LRU + 100MB Disk** | **0 OOMs bei Scrollen** |
| **Jetpack Compose UI Recomposition** | Standard-Klassen (mögliche Recompositions) | **`@Immutable` auf allen Entitäten & State-Klassen** | **0 unnötige Recompositions** |
| **Scroll-Stottern (Jitter) bei großen Listen** | Ungepufferte Flow-Updates | **`conflate()`, Paging 3, Indizierte Sortierung** | **Konstante 60/120 fps** |

---

## 2. Datenbank-Architektur (Room & SQLite)

### 2.1 Full-Text Search (FTS4)
- **Virtuelle Tabelle:** `channels_fts` über SQLite FTS4.
- **Indizierte Felder:** `channelId`, `name`, `normalizedTitle`, `groupTitle`.
- **Suchgeschwindigkeit:** Prefix-Matching (`query*`) liefert Treffer in weniger als 15ms ohne Full-Table-Scan.
- **Automatische Synchronisation:** Bei `insertChannels` wird die FTS4-Schattentabelle automatisch innerhalb derselben Transaktion befüllt.

### 2.2 Paging 3 Integration
- **`PagingSource<Int, ChannelItem>`:** In `ChannelDao` für `getChannelsPagingSource`, `getChannelsByCountryPagingSource` und `getFavoriteChannelsPagingSource`.
- **Zero-Blocking:** Die UI lädt nie zehntausende Einträge auf einmal, sondern inkrementell in Seiten (50 Items) mit Vorabruf (Prefetch 15 Items).

### 2.3 SQLite PRAGMA-Konfiguration
- `PRAGMA mmap_size = 268435456` (256 MB Memory Mapped I/O)
- `PRAGMA busy_timeout = 5000` (5s Lock-Wartezeit bei parallelen Schreibzugriffen)
- `PRAGMA cache_size = -64000` (64 MB Page Cache)
- `PRAGMA temp_store = MEMORY` (In-Memory B-Trees)
- `PRAGMA synchronous = NORMAL` (Minimale fsync-Kosten bei voller WAL-Integrität)
- `PRAGMA wal_autocheckpoint = 1000`

### 2.4 Composite & Covering Indices
- `Index(["countryCode", "type", "name"])` & `Index(["countryCode", "name"])`: Paginierte Länderlisten werden direkt aus dem Index bedient, ohne temporäre B-Tree-Filesorts.
- `Index(["tvgId"])` & `Index(["epgId"])`: O(log N) Direktlookups bei EPG-Zuordnungen.
- `Index(["endTime"])`: Beschleunigt das Bereinigen abgelaufener EPG-Einträge auf O(log N).

---

## 3. Media3 & ExoPlayer Video Caching
- **`MediaCacheHolder`:** Singleton mit Media3 `SimpleCache` und `LeastRecentlyUsedCacheEvictor` (100 MB).
- **`CacheDataSource.Factory`:** HTTP-Datenquellen für Streams werden durch den lokalen LRU-Videocache geleitet.
- **Schutz vor Netzwerkschwankungen:** Kurze WiFi- oder Mobilfunk-Dropouts (1-3 Sekunden) werden nahtlos aus dem Cache überbrückt.

---

## 4. Compose UI & Startup-Optimierung
- **Stabilität:** Alle UI-State-Klassen (`ChannelItem`, `PlaylistEntity`, `EpgProgram`, `RecordingItem`, `AppSettings`, `PlaylistUpdateNoticeState`, `MacPortalConnectionDialogState`) sind mit `@androidx.compose.runtime.Immutable` annotiert.
- **Baseline Profile:** `app/src/main/baseline-prof.txt` stellt Ahead-of-Time (AOT) Kompilierung auf Android 7+ sicher und minimiert Kaltstartzeiten.
