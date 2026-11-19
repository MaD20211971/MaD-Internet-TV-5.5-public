# Restore Point — MaD Internet TV V.5.0 final

- **Version:** `MaD Internet TV V.5.0 final` (`versionCode = 5`, `versionName = "V.5.0 final"`)
- **Timestamp:** `2026-10-29T01:21:25-07:00`
- **Archive Path:** `scripts/restore_point_v5_0_final.tar.gz`
- **SHA-256 Checksum:** `d3b428a9818864f8c3ac6409777534cf1dca2bb8eee945b12ac72daedaf4d80a`
- **Restore Command:** `tar -xzf scripts/restore_point_v5_0_final.tar.gz`

## Enthaltener Funktionsstand
1. **TopBar & Layout:** `"MaD TV"` Kurzlabel oben links neben dem App-Logo mit direkt sichtbarem Playlisten-Selektor; saubere Systemleisten-Abstände im Smartphone-Hochformat.
2. **Passwort-/PIN-Schutz (PIN `1971`, 12-stellige Vorlage):**
   - Maskierte Playlisten-Übersicht in den Einstellungen (Freischaltung von URLs, MAC-Adressen und Zugangsdaten erst nach PIN-Eingabe).
   - Gesperrte Rubrik `"Web TV"` ohne Einsicht vor Eingabe des PIN `1971` (12-Stellen-PIN-Maske).
3. **Rubrik `"Live TV light"`:**
   - Platziert rechts neben `"Serien"` in der Navigationsleiste.
   - 39 deutsche Live-TV-Sender mit Senderlogos, direkter Wiedergabe im internen Nexus Player sowie externen Playern (VLC, MX Player, Kodi, XPlayer, Just Player, MPV) ohne Anzeige der Stream-Herkunft.
   - Optimiertes HLS-Live-Edge-Timing (`targetOffsetMs = 3500 ms`, direkte `tracks-v1a1/mono.ts.m3u8` Anbindung, automatische `Referer`/`Origin`-Header und Live-Edge-Resync ohne falsche VPN-/Status-456-Meldung).
4. **VOD Download Manager & PVR Aufnahme-Timer:**
   - Offline-Downloads für Filme & Serien nach `Downloads/MaDIPTV` im MP4-Format.
   - Geplante Live-TV-Aufnahmen über Digital-Zeiteingabe (Startzeit / Endzeit) direkt im Player.
5. **EPG-Engine (100% eindeutige Zuordnung + Streaming-Sync):**
   - Speicherschonender XMLTV-Streaming-Parser ohne OOM/Lag und striktes 100%-Matching inkl. EPG-Grid.
