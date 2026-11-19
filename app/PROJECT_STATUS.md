# Projektstatus: IPTV & EPG App

## Aktueller Stand (Stand: September 2026)

### 1. Implementierte & Verifizierte Kernbereiche

| Bereich | Status | Details |
|---|---|---|
| **Datenbank & Performance (Room v16)** | ✅ Abgeschlossen | Version 16, WAL-Modus, 256MB MMAP, 64MB Cache, Busy Timeout 5s, neue Composite Indizes auf `channels` (`playlistId, isDach, name`, `countryCode, isFavorite, name`, `playlistId, countryCode, isFavorite`, `normalizedChannelId`). Batch-Inserts in 500er Chunks ohne `delay(5)`. |
| **Playlisten-Parser (M3U, Xtream, MAC)** | ✅ Abgeschlossen | Hocheffizienter Single-Pass Tokenizer (`splitExtInfLine` & `parseAttributesFast`) in `M3UParser` zur Behebung des Quoted-Comma-Bugs. Streaming-JSON-Parsing mit `CountryResolver`-Anbindung für Live TV, VOD Filme und Serien in `XtreamCodesClient` und `MacPortalClient`. |
| **Senderidentifikation & Länder-Zuordnung** | ✅ Fehlerfrei | Vollständige Asset-Initialisierung von `DachMasterDatabase` (105 DACH-Sender + 402 Aliase). Präzise Unterscheidung von AT/CH/DE, Erkennung internationaler Sender (US, UK, TR, IT, FR, ES, NL, PL, etc.), eindeutige `normalizedChannelId` und Normalisierung. |
| **Volltextsuche (FTS4)** | ✅ Abgeschlossen | `ChannelFts` mit SQLite FTS4 für sub-15ms Suche bei 100k+ Katalogen, automatische Index-Synchronisation bei Kanal-Imports. |
| **Paging 3 Integration** | ✅ Abgeschlossen | `PagingSource` in `ChannelDao`, Pager-Flows in `ContentRepository` für speicherschonendes Laden ohne UI-Thread-Blockaden. |
| **Video Chunk Caching (Media3)** | ✅ Abgeschlossen | `MediaCacheHolder` mit 100 MB LRU-Cache und `CacheDataSource.Factory` zur Unterbrechungsüberbrückung bei Netzwerkfluktuationen. |
| **Baseline Profiles & UI Stabilität** | ✅ Abgeschlossen | `baseline-prof.txt` für AOT-Kompilierung; `@Immutable` auf allen Entitäten und UI-State-Klassen für 0 überflüssige Recompositions. |
| **EPG Ingestion, Parsing & Schaltflächen** | ✅ Vollständig repariert | Alle 4 EPG-Schaltflächen voll funktionsfähig („EPG jetzt aktualisieren“, „Jetzt neu kalibrieren“, „Beide Feeds laden“, „Alle EPG löschen“). Robuste GZIP-Magic-Byte-Erkennung und UTF-8-BOM-Bereinigung in `XmltvParser`, nahtlose Room-Datenbank-Anbindung und Live-EPG-Map-Aktualisierung. |
| **Kategorien & Länderselektor** | ✅ Abgeschlossen | Zentraler Länderselektor (DE, AT, CH, Alle & International) als Anker; Verteilung in 4 Hauptbereiche: Live TV, Filme, Serien, Dokus; Bereinigung von Provider-Müll und toten Filtern mit `CategoryFilterEngine`. |
| **Sender-zu-EPG Zuordnung & Kalibrierung** | ✅ Vollständig repariert | 4-Stufen-Hierarchie in `EpgMatcher` (Exakte ID, normalisierte ID, Aliase/Masterdatenbank, normalisierte Namen). „Jetzt neu kalibrieren“ führt vollständige Neuzuordnung gegen Room-DB durch und persistiert Ergebnisse in `ChannelDao`. |
| **Diagnose & Manuelle Zuordnung** | ✅ Abgeschlossen | `ManualEpgMappingDialog` zur visuellen Prüfung von Zuordnungsmethode, Status und Kandidaten sowie persistentem manuellem Zuweisen/Lösen. |
| **„Jetzt läuft“ & „Als Nächstes“** | ✅ Abgeschlossen | Exakte Kriterien (`startTime <= now < endTime` und nächstes `startTime > now`), N+1-Abfragen eliminiert durch `getProgramsInTimeWindow()`. Automatische Aktualisierung bei Sendungswechsel. |
| **Ländertrennung & Sortierung** | ✅ Abgeschlossen | Strikte Trennung von EPG- und Länderlogik; DACH Master-Sortierung, Favoriten und benutzerdefinierte Reihungen bleiben geschützt. |
| **Playlist-Bereinigung & Portale** | ✅ Abgeschlossen | Vollständige Bereinigung abgelaufener MAC Portale aus den installierten Playlisten und der SQLite-Room-DB via `initializeInitialData()` und `performBackgroundStartupMaintenance()` in `MainViewModel`. Bei Löschung des aktiven Portals automatische, nahtlose Umschaltung auf Pluto TV. |
| **Länderselektor im Querformat (AndroidTV)** | ✅ Vollständig optimiert | 2-Spalten-Split-Pane für AndroidTV und Querformat: 310dp Steuerungssidebar links (Tabs, Suche, Schnellwahl, Batch-Aktionen) und vollständige vertikale Höhe für Länder-/Kanalliste rechts. Volle D-Pad-Fokussierbarkeit mit `GoldPrimary` Glow-Highlights und `BackHandler`. Hochformat auf Smartphones bleibt 100% unverändert. |
| **Xtream Playlists Performance & ANR-Fix** | ✅ Vollständig repariert | Behebung der Meldung „Warten oder App schließen“ bei großen Xtream-Playlists (50k+ Einträge). Umstellung der Hauptliste (`AppNavigation.kt`) von O(N²) `displayChannels.count` und Eager-`item()`-Allocation auf precomputed O(N) Lazy-Grouping mit `items()`. Erhöhung der DB-Batchgrenzen (1.000er Chunks) in `XtreamCodesClient` & `ChannelDao`, Vermeidung von Heap-Thrashing und sofortige Bereitstellung von `_filteredLiveChannels`. |
| **Coroutine Cancellation Handling** | ✅ Vollständig repariert | `CancellationException` wird in `loadChannelsForPlaylist`, `switchPlaylist`, `selectCountry`, `loadNextPage`, `initializeInitialData` und `performBackgroundStartupMaintenance` kooperativ re-thrown statt als falscher Fehler geloggt. |
| **Länderselektor & Provider-Kanalsortierung** | ✅ 100% Optimiert | Zuverlässige Erkennung von Provider-Ländergruppen (`|TR|`, `[IT]`, `EXYU`, `NL`, etc.) via `CountryResolver` und `BRACKET_TAG_REGEX`. Strikte kanonische Reihung der Länder (DE #1, AT #2, CH #3, US #4, UK #5, TR #6, IT #7, FR #8, ES #9, NL #10, PL #11, EXYU #12, ALB #13, PT #14, RO #15, RU #16, AR #17, GR #18, BE #19, CZ #20, BG #21, SE #22, IN #23, CA #24, AF #25, VIP #30, SPORT #80, ADULT #90, CONFLICT #95, OTHER #98, UNKNOWN #99). Länder mit tatsächlichen Provider-Kanälen werden priorisiert vor Null-Kanal-Platzhaltern gerankt. Kanäle innerhalb jedes Landes werden kanonisch nach Stations-Wichtigkeit, Stream-Qualität (4K -> FHD -> HD -> SD) und Bereinigungstitel sortiert. |

---

### 2. Testergebnisse

Alle 90 Unit- und Robolectric-Tests laufen auf der JVM zu 100% erfolgreich durch:
- **`M3UParserTest`**:
  - `testStandardM3uParsing`: Bestanden
  - `testUnquotedAttributesAndExtGrp`: Bestanden
  - `testSeriesAndMovieDetection`: Bestanden
  - `testInputStreamStreaming`: Bestanden
- **`CountryResolverTest`**:
  - `testGermanBroadcastersResolveToDE`: Bestanden
  - `testAustrianBroadcastersResolveToAT`: Bestanden
  - `testSwissBroadcastersResolveToCH`: Bestanden
  - `testRegionalBroadcasterVariants`: Bestanden
  - `testGermanLanguageAloneNeverDefaultsToDE`: Bestanden
  - `testConflictingMetadataResultsInConflictStatus`: Bestanden
  - `testNormalizeIndexCountryCodeStrictValues`: Bestanden
- **`DachMasterDatabaseTest`**:
  - Alle Tests zu kanonischer Sendererkennung, Alias-Disambiguierung (3sat vs 3+, Puls-Sender, ATV-Sender) und Popularitätsranking bestanden.
- **`ChannelSortingTest` & `ContentClassifierTest`**:
  - 100% bestanden (Kanal-Clustering, Qualitäts-Sortierung, Category-Priorisierung, Provider-Ländersortierung & -Erkennung).
- **`DatabasePerformanceTest` & `EpgOptimizationTest`**:
  - 100% bestanden.
