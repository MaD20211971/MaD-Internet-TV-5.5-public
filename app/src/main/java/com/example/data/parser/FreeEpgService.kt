package com.example.data.parser

import android.util.LruCache
import com.example.data.country.DachMasterDatabase
import com.example.data.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object FreeEpgService {

    // Thread-safe fast cache for generated current & next programs (keyed by channel + offset + 2-min time bucket)
    private val programCache = LruCache<String, Pair<EpgProgram?, EpgProgram?>>(1000)
    private val matchKeyCache = LruCache<String, String>(2000)
    private val normalizedNameCache = LruCache<String, String>(2000)

    private val REGEX_PREFIXES = Regex("""(?i)\b(de:|ger:|at:|ch:|\|de\||\[de\]|\(de\)|\|at\||\[at\]|\(at\)|\|ch\||\[ch\]|\(ch\)|\bhd\b|\bfhd\b|\buhd\b|\b4k\b|\bhevc\b|\braw\b|1080p|720p|\bbackup\b)\b""")
    private val REGEX_NON_ALPHANUMERIC = Regex("""[^a-z0-9]""")
    private val REGEX_WHITESPACE = Regex("""\s+""")
    private val REGEX_DOMAIN = Regex("""\.(de|at|ch|uk|us|com|net|org|tv)$""")
    private val REGEX_SCHEDULE_TAGS = Regex("""(?i)\b(HD|FHD|UHD|4K|DE|GER|AT|CH|\[.*?\]|\(.*?\))\b""")

    val SOURCE_TIMEZONE: TimeZone = TimeZone.getTimeZone("Europe/Berlin")

    val DEFAULT_EPG_SERVERS = listOf(
        EpgServerOption(
            name = "Free-EPG Deutschland (free-epg.de)",
            url = "https://www.free-epg.de/api/epg/de.xml.gz",
            country = "🇩🇪 Deutschland (free-epg.de)",
            isGzip = true
        ),
        EpgServerOption(
            name = "EPG.lat Deutschland (epg.lat)",
            url = "https://epg.lat/files/de.xml.gz",
            country = "🇩🇪 Deutschland (epg.lat)",
            isGzip = true
        )
    )

    data class EpgServerOption(
        val name: String,
        val url: String,
        val country: String,
        val isGzip: Boolean
    )

    val SUPPORTED_TIMEZONES = listOf(
        TimezoneOption("Europe/Berlin", "🇩🇪 Deutschland / Berlin (CET UTC+1 / CEST UTC+2)", "Standard"),
        TimezoneOption("Europe/Vienna", "🇦🇹 Österreich / Wien (CET/CEST)", "UTC+1/2"),
        TimezoneOption("Europe/Zurich", "🇨🇭 Schweiz / Zürich (CET/CEST)", "UTC+1/2"),
        TimezoneOption("Asia/Bangkok", "🇹🇭 Thailand / Bangkok (UTC+7)", "+5h Sommer / +6h Winter"),
        TimezoneOption("Europe/London", "🇬🇧 UK / London (GMT/BST)", "UTC+0/1"),
        TimezoneOption("Asia/Dubai", "🇦🇪 VAE / Dubai (GST)", "UTC+4"),
        TimezoneOption("Asia/Singapore", "🇸🇬 Singapur / Bali (SGT)", "UTC+8"),
        TimezoneOption("America/New_York", "🇺🇸 USA / New York (EST/EDT)", "UTC-5/4"),
        TimezoneOption("America/Los_Angeles", "🇺🇸 USA / Los Angeles (PST/PDT)", "UTC-8/7"),
        TimezoneOption("UTC", "🌐 Universal Time Coordinated", "UTC+0")
    )

    data class TimezoneOption(val id: String, val label: String, val utcOffset: String)

    data class ScheduleSlot(
        val startHour: Int,
        val startMin: Int,
        val endHour: Int,
        val endMin: Int,
        val title: String,
        val desc: String,
        val category: String
    )

    // Comprehensive 24/7 broadcast schedules for all major TV channels
    val DACH_EPG_SCHEDULES = mapOf(
        // 1. DAS ERSTE / ARD
        "daserste.de" to listOf(
            ScheduleSlot(0, 0, 5, 30, "Nachtmagazin & Dokumentationen", "Ausführliche Zusammenfassung der Ereignisse der Nacht und Reportagen.", "Nachrichten"),
            ScheduleSlot(5, 30, 9, 0, "ARD-Morgenmagazin", "Live aus Köln: Aktuelle Politik, Hintergründe, Wirtschaft, Kultur und Sport.", "Nachrichten"),
            ScheduleSlot(9, 0, 9, 55, "Live nach Neun", "Gute Laune, praktische Alltagstipps und berührende Geschichten.", "Magazin"),
            ScheduleSlot(9, 55, 10, 45, "In aller Freundschaft - Die jungen Ärzte", "Erfurter Nachwuchsärzte meistern medizinische und private Herausforderungen.", "Serie"),
            ScheduleSlot(10, 45, 11, 35, "Meister des Alltags", "Das heitere Wissensquiz mit Florian Weber und prominenten Rateteams.", "Quiz"),
            ScheduleSlot(11, 35, 12, 0, "Gefragt - Gejagt", "Vier Kandidaten fordern die Quiz-Jäger im spannenden Duell heraus.", "Quiz"),
            ScheduleSlot(12, 0, 14, 0, "ARD-Mittagsmagazin", "Informationen, Live-Schalten aus Berlin und Brüssel.", "Nachrichten"),
            ScheduleSlot(14, 0, 15, 0, "Tagesschau & Rote Rosen", "Telenovela über Freundschaft, Romantik und spannende Intrigen in Lüneburg.", "Serie"),
            ScheduleSlot(15, 0, 16, 0, "Sturm der Liebe", "Gefühlvolle Liebesgeschichte im Fünf-Sterne-Hotel Fürstenhof.", "Serie"),
            ScheduleSlot(16, 0, 17, 0, "Verrückt nach Meer", "Faszinierende Kreuzfahrtdokumentation auf den Weltmeeren.", "Dokumentation"),
            ScheduleSlot(17, 0, 18, 0, "Brisant - Das Boulevardmagazin", "Aktuelle Promi-News, Lifestyle, Trends und Alltagsgeschichten.", "Magazin"),
            ScheduleSlot(18, 0, 19, 45, "Wer weiß denn sowas?", "Bernhard Hoëcker und Elton im unterhaltsamen Rateduell mit Kai Pflaume.", "Show"),
            ScheduleSlot(19, 45, 20, 0, "Wissen vor acht & Börse vor acht", "Kompakte Wissenschafts- und Börsen-Updates vor der Primetime.", "Wissen"),
            ScheduleSlot(20, 0, 20, 15, "Tagesschau 20:00", "Die wichtigste Hauptnachrichtensendung im deutschen Fernsehen.", "Nachrichten"),
            ScheduleSlot(20, 15, 21, 45, "Tatort: Schatten der Nacht", "Spannender Kriminalfall mit Kommissaren im Einsatz.", "Krimi / Film"),
            ScheduleSlot(21, 45, 22, 15, "Tagesthemen", "Hintergründe, Kommentare, Kultur und internationale Berichte.", "Nachrichten"),
            ScheduleSlot(22, 15, 23, 30, "Maischberger", "Der politische Talk mit scharfsinnigen Debatten und Gästen.", "Talk"),
            ScheduleSlot(23, 30, 24, 0, "Kulturjournal & Doku Spezial", "Bücher, Kunst, Musik und Filmpremieren der Woche.", "Kultur")
        ),

        // 2. ZDF
        "zdf.de" to listOf(
            ScheduleSlot(0, 0, 5, 30, "ZDF-Nachtstudio & Terra X Dokus", "Faszinierende Reportagen und Einblicke in Wissenschaft und Natur.", "Kultur"),
            ScheduleSlot(5, 30, 9, 0, "ZDF Morgenmagazin", "Frische Nachrichten, Interviews und die aktuelle Wetterlage am Morgen.", "Nachrichten"),
            ScheduleSlot(9, 0, 10, 30, "Volle Kanne - Service täglich", "Praktische Lebenshilfen, Kochempfehlungen und prominente Gäste live im Studio.", "Magazin"),
            ScheduleSlot(10, 30, 11, 15, "Notruf Hafenkante", "Polizeialltag und dramatische Einsätze im Hamburger Hafenrevier.", "Krimi"),
            ScheduleSlot(11, 15, 12, 0, "SOKO Wismar", "Norddeutsche Ermittler lösen rätselhafte Verbrechen an der Ostseeküste.", "Serie"),
            ScheduleSlot(12, 0, 13, 0, "heute - Nachrichten & Drehscheibe", "Aktuelle Kurznachrichten und Berichte aus den Bundesländern.", "Nachrichten"),
            ScheduleSlot(13, 0, 14, 0, "ZDF Mittagsmagazin", "Journalistisches Nachrichtenmagazin mit aktuellen Brennpunkten.", "Nachrichten"),
            ScheduleSlot(14, 0, 15, 0, "Die Küchenschlacht", "Hobbyköche im kulinarischen Wettstreit unter der Leitung einer Profi-Jury.", "Show"),
            ScheduleSlot(15, 0, 16, 0, "Bares für Rares", "Horst Lichter präsentiert seltene Antiquitäten und Sammlerstücke im Händlerraum.", "Show"),
            ScheduleSlot(16, 0, 17, 0, "Die Rosenheim-Cops", "Bayerischer Charme und spitzfindige Ermittlungen im Chiemgau.", "Krimi"),
            ScheduleSlot(17, 0, 19, 0, "heute & hallo deutschland", "Nachrichten, Boulevard und emotionale Berichte.", "Magazin"),
            ScheduleSlot(19, 0, 19, 30, "heute 19:00", "Die Hauptnachrichten des ZDF mit ausführlicher Wetterkarte.", "Nachrichten"),
            ScheduleSlot(19, 30, 20, 15, "WISO", "Verbrauchermagazin für Wirtschaft, Finanzen, Recht und Alltag.", "Magazin"),
            ScheduleSlot(20, 15, 21, 45, "Der Bergdoktor - Neue Wege", "Dr. Martin Gruber behandelt knifflige medizinische Fälle am Wilden Kaiser.", "Serie"),
            ScheduleSlot(21, 45, 22, 15, "heute journal", "Ausführliche Hintergrundberichte, Analysen und Live-Interviews.", "Nachrichten"),
            ScheduleSlot(22, 15, 23, 0, "Maybrit Illner", "Politischer Talk mit hochkarätigen Entscheidungsträgern.", "Talk"),
            ScheduleSlot(23, 0, 24, 0, "Markus Lanz", "Die spätabendliche Gesprächsrunde zu Politik, Wirtschaft und Gesellschaft.", "Talk")
        ),

        // 3. ZDF NEO
        "zdfneo.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ZDFneo Late Night & True Crime", "Spannende Ermittlungen und internationale Krimis.", "Krimi"),
            ScheduleSlot(6, 0, 10, 0, "Bares für Rares - Classics", "Schätze, Raritäten und Antiquitäten im Händlerraum.", "Show"),
            ScheduleSlot(10, 0, 13, 0, "Death in Paradise", "Britisch-karibische Morde und knifflige Rätsel auf Saint Marie.", "Krimi"),
            ScheduleSlot(13, 0, 16, 0, "Psych & Monk", "Geniale Detektive lösen die ungewöhnlichsten Verbrechen.", "Serie"),
            ScheduleSlot(16, 0, 18, 0, "Duell der Gartenprofis", "Landschaftsgärtner gestalten Traumgärten.", "Doku-Soap"),
            ScheduleSlot(18, 0, 20, 15, "Bares für Rares XXL", "Lieblingsstücke und Händlerduelle mit Horst Lichter.", "Show"),
            ScheduleSlot(20, 15, 22, 0, "Nord Nord Mord / Inspector Barnaby", "Spannender Inselkrimi auf Sylt oder Midsomer Murders.", "Krimi"),
            ScheduleSlot(22, 0, 23, 15, "Neo Ragazzi & Comedy", "Humorvoller Talk und innovative Unterhaltungsformate.", "Talk"),
            ScheduleSlot(23, 15, 24, 0, "ZDF Magazin Royale", "Satire, Recherche und Unterhaltung mit Jan Böhmermann.", "Satire")
        ),

        // 4. ZDF INFO
        "zdfinfo.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ZDFinfo Doku-Nacht: Geheimbünde & Mysterien", "Historische Rätsel und unerklärliche Phänomene.", "Dokumentation"),
            ScheduleSlot(6, 0, 10, 0, "ZDFinfo Geschichte: Das Römische Reich", "Faszinierende Ausgrabungen und archäologische Funde.", "Dokumentation"),
            ScheduleSlot(10, 0, 14, 0, "ZDFinfo Zeitgeschichte: Meilensteine & Epochen", "Historische Wendepunkte und gesellschaftliche Entwicklungen.", "Dokumentation"),
            ScheduleSlot(14, 0, 18, 0, "ZDFinfo Ermittler!: Echte Verbrechen im Fokus", "Kriminalistik, Spurensicherung und forensische Durchbrüche.", "Dokumentation"),
            ScheduleSlot(18, 0, 20, 15, "ZDFinfo Planet: Megabauten der Menschheit", "Ingenieurskunst und architektonische Meisterleistungen.", "Dokumentation"),
            ScheduleSlot(20, 15, 22, 30, "ZDFinfo Prime: Brennpunkt Weltpolitik", "Hintergründe zu globalen Krisen, Mächten und Ressourcen.", "Dokumentation"),
            ScheduleSlot(22, 30, 24, 0, "ZDFinfo Mafia & Drogenkartelle", "Investigative Recherchen über die globale Schattenwirtschaft.", "Dokumentation")
        ),

        // 5. RTL
        "rtl.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "RTL Nachtjournal & CSI", "Late Night Nachrichten und US-Krimi.", "Nachrichten"),
            ScheduleSlot(6, 0, 9, 0, "Punkt 6, 7 & 8", "Der perfekte Start in den Tag mit News, Service, VIPs und Wetter.", "Magazin"),
            ScheduleSlot(9, 0, 12, 0, "Gala TV & Barbara Salesch", "Spannende Gerichtsfälle und Promi-News.", "Unterhaltung"),
            ScheduleSlot(12, 0, 14, 0, "Punkt 12 - Das RTL Mittagsjournal", "Verbrauchertipps, Promi-News und Live-Schalten.", "Magazin"),
            ScheduleSlot(14, 0, 17, 30, "Die Retter & Blaulichtreport", "Spannende Reportagen aus dem Rettungsdienstalltag.", "Reportage"),
            ScheduleSlot(17, 30, 18, 45, "Unter uns & Explosiv", "Die tägliche Daily Soap gefolgt vom Boulevardmagazin.", "Serie"),
            ScheduleSlot(18, 45, 19, 40, "RTL Aktuell", "Nachrichten aus aller Welt, Wetter und Sport-Highlights mit Peter Kloeppel.", "Nachrichten"),
            ScheduleSlot(19, 40, 20, 15, "Gute Zeiten, schlechte Zeiten (GZSZ)", "Deutschlands beliebteste tägliche Vorabendserie.", "Serie"),
            ScheduleSlot(20, 15, 22, 15, "Wer wird Millionär? / Show-Highlight", "Günther Jauch moderiert das legendäre Quizduell.", "Show"),
            ScheduleSlot(22, 15, 23, 30, "RTL Direkt & Stern TV Spezial", "Vertiefende Recherchen, Reportagen und Emotionen.", "Magazin"),
            ScheduleSlot(23, 30, 24, 0, "RTL Nachtjournal", "Der informative Tagesabschluss mit allen Top-Themen.", "Nachrichten")
        ),

        // 6. RTL ZWEI
        "rtl2.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "The Walking Dead & Autopsie", "Mystery, Endzeit und echte Kriminalfälle.", "Serie"),
            ScheduleSlot(6, 0, 11, 0, "Frauentausch & Die Schnäppchenhäuser", "Kult-Klassiker und Renovierungsideen am Vormittag.", "Doku-Soap"),
            ScheduleSlot(11, 0, 16, 0, "Hartz und herzlich / Armes Deutschland", "Echte Lebensrealitäten und authentische Reportagen.", "Reportage"),
            ScheduleSlot(16, 0, 17, 0, "Krass Schule - Die jungen Lehrer", "Spannender Schulalltag und emotionale Dramen.", "Serie"),
            ScheduleSlot(17, 0, 18, 0, "Köln 50667", "Die aufregendste WG am Rhein mit Liebe und Intrigen.", "Serie"),
            ScheduleSlot(18, 0, 19, 0, "Berlin - Tag & Nacht (BT&N)", "Das pulsierende Hauptstadtleben unserer Kult-Charaktere.", "Serie"),
            ScheduleSlot(19, 0, 20, 15, "RTLZWEI News & Wetter", "Nachrichten für die junge Zielgruppe auf den Punkt.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 15, "Die Geissens / Love Island / Kampf der Realitystars", "Glamour, Luxusurlaub oder heiße Flirts.", "Show"),
            ScheduleSlot(22, 15, 24, 0, "Naked Attraction & Doku-Highlights", "Ungewöhnliche Dating-Formate und echte Geschichten.", "Unterhaltung")
        ),

        // 7. SUPER RTL / RTL SUPER
        "superrtl.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "RTL Super Night: Kaminfeuer & Infomercial", "Entspannung durch die Nacht.", "Unterhaltung"),
            ScheduleSlot(6, 0, 12, 0, "TOGGO: PAW Patrol, Peppa Pig & Dragons", "Die besten Serien für die Kleinen am Morgen.", "Kinder"),
            ScheduleSlot(12, 0, 15, 0, "TOGGO: SpongeBob & Tom und Jerry", "Zeichentrick-Klassiker und rasante Comedy.", "Kinder"),
            ScheduleSlot(15, 0, 18, 0, "TOGGO: Ninjago, Pokémon & ALVINNN!!!", "Spannende Abenteuer für Schulkinder.", "Kinder"),
            ScheduleSlot(18, 0, 20, 15, "TOGGO: Die Schlümpfe & Angelo!", "Spaß und Action am Vorabend.", "Kinder"),
            ScheduleSlot(20, 15, 22, 15, "RTL Super Primetime: Monk / CSI: Miami", "Kultige US-Krimiserien zur besten Sendezeit.", "Serie"),
            ScheduleSlot(22, 15, 24, 0, "RTL Super Movie: Romantische Komödie", "Gefühlvolle Filme und Hollywood-Unterhaltung.", "Film")
        ),

        // 8. NITRO
        "nitro.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "NITRO Night: Top Gear & Auto-Dokus", "Rasante PS-Action und Kult-Klassiker.", "Sport"),
            ScheduleSlot(6, 0, 11, 0, "Ein Käfig voller Helden & M*A*S*H", "Legendäre Kult-Sitcoms.", "Comedy"),
            ScheduleSlot(11, 0, 15, 0, "Hör mal, wer da hämmert! & Alle unter einem Dach", "Familien-Sitcoms nonstop.", "Comedy"),
            ScheduleSlot(15, 0, 18, 0, "King of Queens & Eine schrecklich nette Familie", "Al Bundy und Doug Heffernan im Comedy-Marathon.", "Comedy"),
            ScheduleSlot(18, 0, 20, 15, "Medical Detectives & CSI: NY", "Echte Kriminalfälle und Forensik.", "Krimi"),
            ScheduleSlot(20, 15, 22, 30, "NITRO Action: James Bond / Bud Spencer & Terence Hill", "Kultige Action-Blockbuster.", "Film"),
            ScheduleSlot(22, 30, 24, 0, "100% Bundesliga / Football Highlights", "Spannende Sport-Zusammenfassungen und Analysen.", "Sport")
        ),

        // 9. SAT.1
        "sat1.de" to listOf(
            ScheduleSlot(0, 0, 5, 30, "Sat.1 Crime & Night", "Spannende Ermittlungen durch die Nacht.", "Krimi"),
            ScheduleSlot(5, 30, 10, 0, "Sat.1-Frühstücksfernsehen", "Gute Laune, VIPs, News, Service und Kaffeetalk zum Morgen.", "Magazin"),
            ScheduleSlot(10, 0, 14, 0, "Auf Streife - Die Spezialisten", "Polizeibeamte im packenden Einsatz für Sicherheit.", "Doku-Soap"),
            ScheduleSlot(14, 0, 18, 0, "Klinik am Südring & Notfalleinsatz", "Medizinische Notfälle und emotionale Geschichten.", "Serie"),
            ScheduleSlot(18, 0, 19, 0, "Das Küstenrevier", "Spannende Krimiserie an der Ostseeküste.", "Krimi"),
            ScheduleSlot(19, 0, 19, 45, "Die Landarztpraxis", "Dramatische Liebes- und Familienintrigen in den Bergen.", "Serie"),
            ScheduleSlot(19, 45, 20, 15, ":newstime", "Aktuelle Nachrichten, Wetter und Sport kompakt.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 30, "The Voice of Germany / Film-Blockbuster", "Große Samstagabendshow und beste Unterhaltung.", "Show"),
            ScheduleSlot(22, 30, 24, 0, "Sat.1 Spezial & Spiegel TV", "Investigative Reportagen zu Brennpunktthemen.", "Reportage")
        ),

        // 10. PROSIEBEN
        "prosieben.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ProSieben Night & Sitcoms", "US-Sitcoms und Late Night Comedy.", "Comedy"),
            ScheduleSlot(6, 0, 10, 0, "The Big Bang Theory & Brooklyn Nine-Nine", "Lachmuskeltraining am Vormittag.", "Comedy"),
            ScheduleSlot(10, 0, 13, 0, "Two and a Half Men & How I Met Your Mother", "Kult-Sitcoms nonstop.", "Comedy"),
            ScheduleSlot(13, 0, 17, 0, "The Middle & Young Sheldon", "Unterhaltsame Familienkomödien.", "Comedy"),
            ScheduleSlot(17, 0, 18, 0, "taff.", "Das Lifestyle-Magazin mit den heißesten Web-Trends und VIPs.", "Magazin"),
            ScheduleSlot(18, 0, 19, 0, "Die Simpsons", "Kult-Cartoonserie aus Springfield.", "Animation"),
            ScheduleSlot(19, 0, 19, 45, "Galileo", "Wissen für jedermann: faszinierende Technik und Experimente.", "Wissen"),
            ScheduleSlot(19, 45, 20, 15, ":newstime", "Kompaktes Nachrichten-Update.", "Nachrichten"),
            ScheduleSlot(20, 15, 23, 0, "Joko & Klaas / Blockbuster Primetime", "Entertainment, Adrenalin und Hollywood-Hits.", "Show / Film"),
            ScheduleSlot(23, 0, 24, 0, "Late Night Berlin", "Klaas Heufer-Umlauf mit Gästen und Studioaktionen.", "Comedy")
        ),

        // 11. VOX
        "vox.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "VOX Night & Medical Detectives", "Spannende Kriminalforensik und True Crime.", "Doku"),
            ScheduleSlot(6, 0, 12, 0, "CSI: Den Tätern auf der Spur", "Hightech-Forensiker lösen knifflige Mordfälle.", "Krimi"),
            ScheduleSlot(12, 0, 14, 0, "Shopping Queen", "Guido Maria Kretschmer sucht das perfekte Outfit.", "Lifestyle"),
            ScheduleSlot(14, 0, 17, 0, "Zwischen Tüll und Tränen & Mein Kind, dein Kind", "Familiendokus und Brautkleidträume.", "Doku-Soap"),
            ScheduleSlot(17, 0, 18, 0, "First Dates - Ein Tisch für zwei", "Roland Trettl verkuppelt Singles beim Gourmet-Dinner.", "Dating"),
            ScheduleSlot(18, 0, 19, 0, "Das perfekte Dinner", "Fünf Hobbyköche laden reihum zum Mehrgänge-Menü ein.", "Show"),
            ScheduleSlot(19, 0, 20, 15, "First Dates Hotel", "Romantik und Urlaubsflirt unter der Sonne.", "Dating"),
            ScheduleSlot(20, 15, 23, 0, "Die Höhle der Löwen / Kitchen Impossible", "Gründer werben um Investoren oder Tim Mälzer am Herd.", "Show"),
            ScheduleSlot(23, 0, 24, 0, "Goodbye Deutschland! Die Auswanderer", "Deutsche auf der Suche nach ihrem Glück im Ausland.", "Doku-Soap")
        ),

        // 12. KABEL EINS
        "kabel1.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Castle & Cold Case", "Krimiserien der Extraklasse durch die Nacht.", "Krimi"),
            ScheduleSlot(6, 0, 11, 0, "Blue Bloods & Navy CIS", "Polizeiermittlungen in New York und Washington.", "Krimi"),
            ScheduleSlot(11, 0, 15, 0, "Without a Trace & Numb3rs", "Faszinierende Ermittlerteams im Einsatz.", "Krimi"),
            ScheduleSlot(15, 0, 17, 55, "Mein Lokal, Dein Lokal", "Der Profi kommt: Restaurant-Wettstreit mit Mike Süsser.", "Doku-Soap"),
            ScheduleSlot(17, 55, 18, 55, "Achtung Kontrolle! Wir kümmern uns drum", "Ordnungshüter und Zöllner im täglichen Einsatz.", "Reportage"),
            ScheduleSlot(18, 55, 20, 15, ":newstime & Kabel Eins News", "Nachrichten und aktuelle Berichte.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 45, "Die besten Filme aller Zeiten / Abenteuer Leben", "Kino-Klassiker oder das Wissensmagazin am Abend.", "Film / Magazin"),
            ScheduleSlot(22, 45, 24, 0, "Late Night Movie / Reportage", "Spannung und packende Geschichten zur späten Stunde.", "Film")
        ),

        // 13. N-TV
        "ntv.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "n-tv Telebörse & Dokumentationen", "Wirtschaftsnachrichten und Weltgeschehen.", "Nachrichten"),
            ScheduleSlot(6, 0, 12, 0, "n-tv Nachrichten am Morgen & Telebörse", "Laufende Live-News, Dax-Entwicklung und Interviews.", "Nachrichten"),
            ScheduleSlot(12, 0, 18, 0, "n-tv News Spezial & Märkte am Nachmittag", "Börsenberichte, Analysen und globale Krisen.", "Nachrichten"),
            ScheduleSlot(18, 0, 20, 15, "n-tv Der Tag & Telebörse am Abend", "Die wichtigsten Ereignisse kompakt zusammengefasst.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 0, "n-tv Doku: Giganten der Lüfte & Megaprojekte", "Faszinierende Technik- und Militärdokumentationen.", "Dokumentation"),
            ScheduleSlot(22, 0, 24, 0, "n-tv Doku: Geheimnisse der Geschichte & True Crime", "Investigative Reportagen und historische Einblicke.", "Dokumentation")
        ),

        // 14. WELT
        "welt.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "WELT Nachrichten & Dokus der Nacht", "Laufende Breaking News und Weltpolitik.", "Nachrichten"),
            ScheduleSlot(6, 0, 12, 0, "WELT Live am Morgen & Börsen-Update", "Live-Schalten zu Korrespondenten weltweit.", "Nachrichten"),
            ScheduleSlot(12, 0, 18, 0, "WELT Der Tag & Studio-Talks", "Politische Debatten und Live-Pressekonferenzen.", "Nachrichten"),
            ScheduleSlot(18, 0, 20, 15, "WELT Hauptnachrichten & Wirtschaft", "Zusammenfassung aller Brennpunkte des Tages.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 15, "WELT Doku: Space - Geheimnisse des Universums", "Astronomie, Astrophysik und Raketentechnik.", "Dokumentation"),
            ScheduleSlot(22, 15, 24, 0, "WELT Doku: Spezialeinheiten & Gefechtstaktik", "Militär- und Sicherheitsanalysen.", "Dokumentation")
        ),

        // 15. DMAX
        "dmax.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "DMAX Night: Asphalt-Cowboys & Trucker", "Schwere Maschinen auf den Highways.", "Doku"),
            ScheduleSlot(6, 0, 11, 0, "Die Gebrauchtwagen-Profis (Wheeler Dealers)", "Mike Brewer und Edd China restaurieren Traumautos.", "Auto / Doku"),
            ScheduleSlot(11, 0, 15, 0, "Fang des Lebens - Der gefährlichste Job Alaskas", "Krabbenfischer trotzen der rauen Beringsee.", "Abenteuer"),
            ScheduleSlot(15, 0, 18, 0, "Steel Buddies - Stahlharte Geschäfte", "Michael Manousakis und sein Team im Westerwald.", "Doku-Soap"),
            ScheduleSlot(18, 0, 20, 15, "Goldrausch in Alaska", "Parker Schnabel und Rick Ness schürfen nach Gold.", "Doku-Soap"),
            ScheduleSlot(20, 15, 22, 30, "Der Schrottplatz-König / Texas Survival", "Männer, Motoren und Abenteuer.", "Doku-Soap"),
            ScheduleSlot(22, 30, 24, 0, "Moonshiners - Die Schwarzbrenner von Virginia", "Illegale Schnapsbrenner im Visier des Gesetzes.", "Doku-Soap")
        ),

        // 16. TELE 5
        "tele5.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "SchleFaZ & Sci-Fi Night", "Die schlechtesten Filme aller Zeiten und B-Movie Perlen.", "Sci-Fi"),
            ScheduleSlot(6, 0, 12, 0, "Star Trek: The Next Generation & Voyager", "Captain Picard und Janeway im Weltraum.", "Sci-Fi"),
            ScheduleSlot(12, 0, 16, 0, "Star Trek & Sci-Fi Highlights", "Kultige Science-Fiction und Abenteuer im Weltraum.", "Sci-Fi"),
            ScheduleSlot(16, 0, 20, 15, "Star Trek: Deep Space Nine", "Kultserie aus den Weiten des Alpha-Quadranten.", "Sci-Fi"),
            ScheduleSlot(20, 15, 22, 45, "Tele 5 Movie-Highlight: Fantasy & Action", "Großes Spielfilm-Kino für Cineasten.", "Film"),
            ScheduleSlot(22, 45, 24, 0, "Kalkofes Mattscheibe & Kult-Kino", "Satirische Fernsehkritik mit Oliver Kalkofe.", "Satire")
        ),

        // 17. ARTE
        "arte.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ARTE Lounge & Konzerte", "Internationale Musikevents, Opern und Jazz-Nächte.", "Musik"),
            ScheduleSlot(6, 0, 12, 0, "ARTE Journal & Geheime Gärten", "Europäische Kultur, Gartenkunst und Reisen.", "Kultur"),
            ScheduleSlot(12, 0, 16, 0, "Zu Tisch in... & Wildes Europa", "Kulinarische Traditionen und atemberaubende Natur.", "Dokumentation"),
            ScheduleSlot(16, 0, 19, 0, "Stadt Land Kunst & ARTE Journal", "Reisereportagen und europäische Tagespolitik.", "Kultur"),
            ScheduleSlot(19, 0, 20, 15, "ARTE Journal & 28 Minuten", "Kulturmagazin mit scharfsinnigen Debatten.", "Magazin"),
            ScheduleSlot(20, 15, 22, 15, "ARTE Kino-Klassiker / Meisterwerk", "Preisgekrönte europäische Filmkunst in Originalfassung.", "Film"),
            ScheduleSlot(22, 15, 24, 0, "ARTE Doku: Philosophie & Kunstgeschichten", "Intellektuelle Horizonte erweitern.", "Dokumentation")
        ),

        // 18. 3SAT
        "3sat.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "3sat Jazz Night & Theaterstücke", "Bühnenkunst aus Deutschland, Österreich und der Schweiz.", "Kultur"),
            ScheduleSlot(6, 0, 12, 0, "ZIB & nano - Die Welt von morgen", "Wissenschaftsmagazin und Zukunftstechnologien.", "Wissen"),
            ScheduleSlot(12, 0, 17, 0, "Traumorte der Alpen & Kulturzeit", "Reisen durch DACH-Regionen und Kulturschauen.", "Kultur"),
            ScheduleSlot(17, 0, 19, 20, "scobel & nano", "Tiefgründige Gesprächsrunden zu Wissenschaft und Ethik.", "Wissen"),
            ScheduleSlot(19, 20, 20, 15, "Kulturzeit & heute 19:00", "Das tägliche Kulturmagazin im deutschsprachigen Raum.", "Kultur"),
            ScheduleSlot(20, 15, 22, 0, "3sat Spielfilm & Festivalperlen", "Autorenfilme und anspruchsvolle Dramen.", "Film"),
            ScheduleSlot(22, 0, 24, 0, "ZIB 2 & Kabarett aus Franken", "Schlagfertige Satire und scharfe politische Analysen.", "Kabarett")
        ),

        // 19. KIKA
        "kika.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "KiKa Sendepause & Bernd das Brot", "Bernd das Brot in der endlosen Zeitschleife.", "Kinder"),
            ScheduleSlot(6, 0, 9, 0, "KiKANiNCHEN & Sesamstraße", "MitMach-Programm für die allerkleinsten Zuschauer.", "Kinder"),
            ScheduleSlot(9, 0, 12, 0, "Die Biene Maja & Heidi", "Liebevolle Zeichentrickklassiker für Vorschulkinder.", "Kinder"),
            ScheduleSlot(12, 0, 15, 0, "Die Sendung mit der Maus & Löwenzahn", "Lach- und Sachgeschichten mit der Maus, Elefant und Fritz Fuchs.", "Wissen"),
            ScheduleSlot(15, 0, 18, 0, "Pfefferkörner & Schloss Einstein", "Spannende Detektiv- und Internatsgeschichten.", "Jugend"),
            ScheduleSlot(18, 0, 19, 0, "Wissen macht Ah! & Checker Tobi", "Spannende Checker-Fragen verständlich erklärt.", "Wissen"),
            ScheduleSlot(19, 0, 19, 50, "logo! - Die Kindernachrichten & Sandmännchen", "Wichtige Weltnachrichten verständlich für Kinder erklärt.", "Kinder"),
            ScheduleSlot(19, 50, 21, 0, "KiKa Live & Filme", "Shows, Games und spannende Jugendfilme.", "Jugend"),
            ScheduleSlot(21, 0, 24, 0, "Bernd das Brot - Die Nachtschleife", "Bernd das Brot verharrt im Bildschirm.", "Kinder")
        ),

        // 20. PHOENIX
        "phoenix.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "phoenix vor ort - Debatten im Bundestag", "Parlamentsmitschnitte in voller Länge.", "Politik"),
            ScheduleSlot(6, 0, 12, 0, "phoenix vor ort & Presseclub", "Live-Übertragungen aus Berlin, Brüssel und der Bundespressekonferenz.", "Nachrichten"),
            ScheduleSlot(12, 0, 18, 0, "phoenix der Tag & Hintergrund", "Politische Analysen mit renommierten Experten.", "Nachrichten"),
            ScheduleSlot(18, 0, 20, 15, "phoenix runde & Tagesschau", "Der hochkarätige politische Diskurs des Tages.", "Talk"),
            ScheduleSlot(20, 15, 22, 15, "phoenix History: Jahrhundertwenden", "Tiefgehende historische Dokumentationen.", "Dokumentation"),
            ScheduleSlot(22, 15, 24, 0, "phoenix persönlich & Unter den Linden", "Ausführliche Einzelinterviews mit Spitzenpolitikern.", "Talk")
        ),

        // 21. TAGESSCHAU24
        "tagesschau24.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "tagesschau24 - Die Nacht im Überblick", "Kontinuierliche Breaking News und Börsenberichte.", "Nachrichten"),
            ScheduleSlot(6, 0, 12, 0, "tagesschau24 - Live am Vormittag", "Aktuelle Ereignisse aus Berlin, Europa und aller Welt.", "Nachrichten"),
            ScheduleSlot(12, 0, 18, 0, "tagesschau24 - Das Nachmittags-Update", "Live-Schalten zu Reportern und Expertenanalysen.", "Nachrichten"),
            ScheduleSlot(18, 0, 21, 0, "tagesschau24 - Der Abend im Fokus", "Kommentare, Brennpunkte und Hintergrundberichte.", "Nachrichten"),
            ScheduleSlot(21, 0, 24, 0, "tagesschau24 - Weltspiegel & Dokus", "Auslandsreportagen und investigative Recherchen.", "Dokumentation")
        ),

        // 22. WDR
        "wdr.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "WDR Nacht & Rockpalast", "Legendäre Rock- und Popkonzerte aus NRW.", "Musik"),
            ScheduleSlot(6, 0, 12, 0, "WDR aktuell & Daheim & unterwegs", "Regionale Berichterstattung aus Nordrhein-Westfalen.", "Magazin"),
            ScheduleSlot(12, 0, 18, 0, "Hier und heute & WDR aktuell", "Service, Menschen und Kultur zwischen Rhein und Weser.", "Magazin"),
            ScheduleSlot(18, 0, 19, 30, "Lokalzeit - Aus Ihrer Region", "Die elf Lokalzeiten berichten live aus Aachen, Köln, Ruhrgebiet und OWL.", "Regional"),
            ScheduleSlot(19, 30, 20, 0, "Aktuelle Stunde", "Das Leitmedium für ganz NRW mit allen wichtigen Themen.", "Nachrichten"),
            ScheduleSlot(20, 0, 20, 15, "Tagesschau", "Hauptnachrichten der ARD.", "Nachrichten"),
            ScheduleSlot(20, 15, 21, 45, "Wunderschön! - Reiseziele in NRW & Europa", "Urlaubsträume mit Tamina Kallert und Daniel Aßmann.", "Reise"),
            ScheduleSlot(21, 45, 22, 45, "Markt - Verbrauchermagazin", "Lebensmittelchecks, Preisfallen und Konsumentenschutz.", "Magazin"),
            ScheduleSlot(22, 45, 24, 0, "Kölner Treff", "Bettina Böttinger und Gäste im offenen Promi-Talk.", "Talk")
        ),

        // 23. NDR
        "ndr.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "NDR Nacht & Die Nordstory", "Reportagen von den Küsten und Inseln des Nordens.", "Doku"),
            ScheduleSlot(6, 0, 12, 0, "NDR Info & Mein Nachmittag", "Nachrichten und Unterhaltung für Norddeutschland.", "Magazin"),
            ScheduleSlot(12, 0, 18, 0, "NDR Info & DAS!", "Das rote Sofa empfängt prominente Persönlichkeiten.", "Magazin"),
            ScheduleSlot(18, 0, 19, 30, "Nordmagazin / Hamburg Journal / Hallo Niedersachsen", "Regionales aus allen vier Küstenländern.", "Regional"),
            ScheduleSlot(19, 30, 20, 0, "DAS! - Gäste auf dem Roten Sofa", "Interviews und Live-Gäste in lockerer Atmosphäre.", "Talk"),
            ScheduleSlot(20, 0, 20, 15, "Tagesschau", "Hauptnachrichten der ARD.", "Nachrichten"),
            ScheduleSlot(20, 15, 21, 45, "Die Nordstory / mareTV", "Geschichten und Traditionen von der Nord- und Ostsee.", "Doku"),
            ScheduleSlot(21, 45, 22, 45, "Extra 3", "Der Irrsinn der Woche mit Christian Ehring.", "Satire"),
            ScheduleSlot(22, 45, 24, 0, "NDR Talk Show", "Barbara Schöneberger und Hubertus Meyer-Burckhardt im Talk.", "Talk")
        ),

        // 24. BR
        "br.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Space Night & Bayern erleben", "Faszinierende Erdaufnahmen aus dem Orbit zu Chillout-Klängen.", "Entspannung"),
            ScheduleSlot(6, 0, 12, 0, "BR24 & Wir in Bayern", "Heimatgefühl, Brauchtum und Kulinarik aus dem Freistaat.", "Magazin"),
            ScheduleSlot(12, 0, 17, 30, "Wir in Bayern & Abendschau", "Gespräche mit Handwerkern, Köchen und Bergsteigern.", "Magazin"),
            ScheduleSlot(17, 30, 18, 30, "Abendschau - Der Süden", "Aktuelle Berichte aus den bayerischen Regionen.", "Regional"),
            ScheduleSlot(18, 30, 19, 0, "BR24 Rundschau", "Die bayerischen Hauptnachrichten.", "Nachrichten"),
            ScheduleSlot(19, 0, 19, 30, "Dahoam is Dahoam", "Die bayerische Kultserie aus Lansing.", "Serie"),
            ScheduleSlot(19, 30, 20, 0, "quer - Durch die Woche mit Christoph Süß", "Kritisches und Hintergründiges aus Politik und Gesellschaft.", "Magazin"),
            ScheduleSlot(20, 0, 20, 15, "Tagesschau", "Hauptnachrichten der ARD.", "Nachrichten"),
            ScheduleSlot(20, 15, 21, 45, "Bergauf-Bergab / Bayern feiern", "Das Alpinmagazin für Bergsteiger und Wanderer.", "Sport"),
            ScheduleSlot(21, 45, 22, 45, "BR24 Thema & Grünwald Freitagscomedy", "Hintergrundanalysen und bayerischer Humor.", "Comedy"),
            ScheduleSlot(22, 45, 24, 0, "Ringlstetter", "Hannes Ringlstetter mit bayerischem Late-Night-Talk.", "Talk")
        ),

        // 25. SWR
        "swr.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "SWR Nacht & Eisenbahn-Romantik", "Züge auf spektakulären Strecken weltweit.", "Doku"),
            ScheduleSlot(6, 0, 12, 0, "SWR Aktuell & Kaffee oder Tee", "Ratgeber, Garten- und Kochtipps für den Südwesten.", "Magazin"),
            ScheduleSlot(12, 0, 18, 0, "SWR Aktuell & Landesschau", "Menschliches und Regionales aus BW und RLP.", "Magazin"),
            ScheduleSlot(18, 0, 19, 30, "Landesschau Baden-Württemberg & Rheinland-Pfalz", "Live vor Ort in Städten und Dörfern.", "Regional"),
            ScheduleSlot(19, 30, 20, 0, "SWR Aktuell Baden-Württemberg / RLP", "Die Abendnachrichten des Südwestrundfunks.", "Nachrichten"),
            ScheduleSlot(20, 0, 20, 15, "Tagesschau", "Hauptnachrichten der ARD.", "Nachrichten"),
            ScheduleSlot(20, 15, 21, 45, "Handwerkskunst! & Traumorte im Südwesten", "Traditionelle Handwerkskunst und Schwarzwaldidylle.", "Doku"),
            ScheduleSlot(21, 45, 22, 45, "SWR Nachtcafé", "Michael Steinbrecher spricht mit Menschen über Schicksale.", "Talk"),
            ScheduleSlot(22, 45, 24, 0, "Spätschicht - Die Comedy Bühne", "Kabarett und Comedy aus dem Südwesten.", "Comedy")
        ),

        // 26. ORF 1
        "orf1.at" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ORF 1 Night & US-Serien", "Nachtprogramm und Comedy.", "Comedy"),
            ScheduleSlot(6, 0, 12, 0, "ORF 1 OKIDOKI & Sitcom-Frühstück", "Kinderprogramm und amerikanische Kultserien.", "Unterhaltung"),
            ScheduleSlot(12, 0, 17, 0, "Malcolm mittendrin & Young Sheldon", "Comedy und Serienmarathon.", "Serie"),
            ScheduleSlot(17, 0, 19, 30, "ZIB Flash & Sport am 1", "Österreichische Sportberichterstattung und News.", "Sport"),
            ScheduleSlot(19, 30, 20, 15, "ZIB 20 & Magazin 1", "Österreichs Nachrichten zur Primetime.", "Nachrichten"),
            ScheduleSlot(20, 15, 22, 0, "ORF 1 Spielfilm-Highlight", "Große Blockbuster und Kino-Premieren.", "Film"),
            ScheduleSlot(22, 0, 24, 0, "ZIB 2 & Willkommen Österreich", "Grissemann und Stermann mit satirischem Late-Night-Talk.", "Comedy")
        ),

        // 27. ORF 2
        "orf2.at" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ORF 2 Nacht & Österreich-Bild", "Traditionen und Impressionen aus allen Bundesländern.", "Kultur"),
            ScheduleSlot(6, 0, 12, 0, "Guten Morgen Österreich & ZIB", "Live-Frühstücksfernsehen aus den Bundesländern.", "Magazin"),
            ScheduleSlot(12, 0, 18, 0, "Aktuell nach eins & Studio 2", "Österreichische Gesellschaft, Kultur und Service.", "Magazin"),
            ScheduleSlot(18, 0, 19, 0, "Konkret - Das Konsumentenmagazin", "Konsumentenschutz und Recht in Österreich.", "Magazin"),
            ScheduleSlot(19, 0, 19, 30, "Bundesland heute (Wien, NÖ, OÖ, Stmk, Ktn, Sbg, Tir, Vbg, Bgld)", "Regionale Berichte aus Ihrem Bundesland.", "Regional"),
            ScheduleSlot(19, 30, 20, 0, "Zeit im Bild (ZIB 1)", "Die wichtigste Hauptnachrichtensendung Österreichs.", "Nachrichten"),
            ScheduleSlot(20, 0, 20, 15, "Seitenblicke & Wetter", "Boulevard und Gesellschaftsmagazin.", "Promis"),
            ScheduleSlot(20, 15, 22, 0, "Universum Doku: Wilde Alpen / Österreich", "Spektakuläre Naturdokumentationen.", "Dokumentation"),
            ScheduleSlot(22, 0, 23, 0, "ZIB 2 mit Armin Wolf", "Das kritische politische Interviewmagazin.", "Nachrichten"),
            ScheduleSlot(23, 0, 24, 0, "Universum History & Talk", "Spannende historische Reportagen.", "Dokumentation")
        ),

        // 28. SRF 1
        "srf1.ch" to listOf(
            ScheduleSlot(0, 0, 6, 0, "SRF 1 Nachtprogramm & Dokus", "Schweizer Impressionen und Wiederholungen.", "Kultur"),
            ScheduleSlot(6, 0, 12, 0, "SRF 1 Info & Morgenprogramm", "Schweizer Nachrichten und Naturdokus.", "Information"),
            ScheduleSlot(12, 0, 18, 0, "Tagesschau & Schweiz aktuell", "Regionale Berichte aus allen Schweizer Kantonen.", "Magazin"),
            ScheduleSlot(18, 0, 19, 30, "Guetnachtgschichtli & 1 gegen 100", "Schweizer Quizshow mit Susanne Kunz.", "Quiz"),
            ScheduleSlot(19, 30, 20, 0, "Tagesschau Hauptausgabe & Meteo", "Schweizer Hauptnachrichten mit Wetterkarte.", "Nachrichten"),
            ScheduleSlot(20, 0, 21, 45, "SRF 1 DOK: Bergwelten & Natur", "Faszinierende Dokumentation über die Schweizer Alpen.", "Dokumentation"),
            ScheduleSlot(21, 45, 22, 30, "10vor10", "Schweizer Nachrichtenmagazin mit vertiefenden Berichten.", "Nachrichten"),
            ScheduleSlot(22, 30, 24, 0, "Club - Die Diskussionsrunde", "Spannende Debatten zu aktuellen Schweizer Themen.", "Talk")
        ),

        // 29. SRF ZWEI
        "srf2.ch" to listOf(
            ScheduleSlot(0, 0, 6, 0, "SRF zwei Night & Sport Replays", "Sport-Highlights und US-Serien.", "Sport"),
            ScheduleSlot(6, 0, 12, 0, "Zambo Kinderprogramm & Serien", "Unterhaltung für junge Zuschauer.", "Jugend"),
            ScheduleSlot(12, 0, 18, 0, "Sport Aktuell & Magazin", "Skifahren, Tennis, Radsport und Schweizer Liga.", "Sport"),
            ScheduleSlot(18, 0, 20, 15, "Super League / Champions League Live", "Live-Übertragungen des Schweizer Spitzenfußballs.", "Sport Live"),
            ScheduleSlot(20, 15, 22, 30, "Sportpanorama & Blockbuster", "Die besten Sportler im Interview und Hollywood-Kino.", "Sport / Film"),
            ScheduleSlot(22, 30, 24, 0, "Late Night Movie / Serie", "Spannung und packende Geschichten.", "Serie")
        ),

        // 30. SERVUS TV
        "servustv.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "ServusTV Nacht: Bergwelten Classics", "Alpenpanorama und Dokumentationen.", "Doku"),
            ScheduleSlot(6, 0, 12, 0, "Servus Nachrichten & Heimatleuchten", "Kultur, Brauchtum und Handwerk.", "Kultur"),
            ScheduleSlot(12, 0, 18, 0, "Servus Journal & Kulinarik der Alpen", "Geschichten und regionale Rezepte.", "Magazin"),
            ScheduleSlot(18, 0, 19, 20, "Servus Nachrichten & Wetter", "Aktuelle Nachrichten aus Österreich und der Welt.", "Nachrichten"),
            ScheduleSlot(19, 20, 20, 15, "Servus Reportage", "Investigative Geschichten vor Ort.", "Reportage"),
            ScheduleSlot(20, 15, 22, 0, "Bergwelten / Formel 1 Live / Tennis", "Packende Bergtouren oder Motorsport-Highlights.", "Sport / Doku"),
            ScheduleSlot(22, 0, 24, 0, "Talk im Hangar-7", "Kontroverse Debatten im spektakulären Flugzeughangar in Salzburg.", "Talk")
        ),

        // 31. SKY SPORT BUNDESLIGA
        "skybundesliga.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Bundesliga Replay: Die Klassiker", "Legendäre Spiele und historische Derbys der Bundesliga.", "Sport"),
            ScheduleSlot(6, 0, 11, 0, "Bundesliga Kompakt & Torjäger", "Alle Treffer, Analysen und Stimmen des Spieltags.", "Sport"),
            ScheduleSlot(11, 0, 13, 0, "Sky Bundesliga Vorberichte", "Taktik-Checks, Aufstellungen und Interviews live aus den Stadien.", "Sport Talk"),
            ScheduleSlot(13, 0, 15, 30, "Sky Bundesliga Countdown", "Die Expertenrunde vor dem Anpfiff.", "Sport Live"),
            ScheduleSlot(15, 30, 17, 30, "Bundesliga Konferenz / Topspiel Live", "Alle Tore in Echtzeit live auf allen Plätzen.", "Sport Live"),
            ScheduleSlot(17, 30, 20, 0, "Alle Spiele, alle Tore Live", "Die ausführliche Spieltagszusammenfassung und strittige Szenen.", "Sport"),
            ScheduleSlot(20, 0, 22, 30, "Das Topspiel der Woche Live", "Das Samstagabend-Livespiel in voller Länge.", "Sport Live"),
            ScheduleSlot(22, 30, 24, 0, "Sky90 - Die Fußballdebatte", "Patrick Wasserziehr und hochkarätige Gäste diskutieren.", "Sport Talk")
        ),

        // 32. SKY SPORT TOP EVENT / PREMIER LEAGUE / F1
        "skysport.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Sky Sport Highlights & Replays", "Premier League, F1 und Tennis im Rückblick.", "Sport"),
            ScheduleSlot(6, 0, 12, 0, "Sky Sport News Live", "24/7 alle Transfer-News, Pressekonferenzen und Interviews.", "Sport"),
            ScheduleSlot(12, 0, 15, 0, "Premier League Vorberichte / F1 Warm-Up", "Hintergründe aus England und dem Fahrerlager.", "Sport Talk"),
            ScheduleSlot(15, 0, 18, 0, "Premier League Live / Formel 1 Qualifying & Rennen", "Spitzensport live in HD & UHD.", "Sport Live"),
            ScheduleSlot(18, 0, 20, 30, "ATP & WTA Tennis Live / Sky Sport Mix", "Grand Slam und Masters-Duelle.", "Sport Live"),
            ScheduleSlot(20, 30, 23, 0, "Sky Top Event Live", "Das größte Sportereignis des Abends.", "Sport Live"),
            ScheduleSlot(23, 0, 24, 0, "Sky Sport News Late Update", "Der kompakte Sport-Tagesabschluss.", "Sport")
        ),

        // 33. DAZN 1 & DAZN 2
        "dazn.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "DAZN Replay & UEFA Champions League Tore", "Die spektakulärsten Szenen des europäischen Spitzenfußballs.", "Sport"),
            ScheduleSlot(6, 0, 12, 0, "DAZN World & Serie A / La Liga Magazine", "Internationale Fußball-Ligen und Star-Porträts.", "Sport"),
            ScheduleSlot(12, 0, 15, 0, "DAZN Matchday Vorberichte", "Taktik-Checks und Live-Schalten in die Stadien Europas.", "Sport Talk"),
            ScheduleSlot(15, 0, 18, 0, "UEFA Champions League / Serie A Live", "Europäischer Spitzenfußball in voller Länge.", "Sport Live"),
            ScheduleSlot(18, 0, 20, 30, "La Liga Topspiel Live: Real Madrid / FC Barcelona", "Spaniens El Clásico und Titelkampf.", "Sport Live"),
            ScheduleSlot(20, 30, 23, 0, "Bundesliga am Freitag/Sonntag Live", "Das Bundesliga-Livespiel exklusiv bei DAZN.", "Sport Live"),
            ScheduleSlot(23, 0, 24, 0, "DAZN Highlights & Analysen", "Alle Tore und strittige Szenen des Spieltags.", "Sport")
        ),

        // 34. EUROSPORT 1 & 2
        "eurosport.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Eurosport Replay: Radsport & Wintersport", "Tour de France, Ski Alpin und Biathlon Highlights.", "Sport"),
            ScheduleSlot(6, 0, 11, 0, "Eurosport News & Tennis Masters", "Live-Matches und Zusammenfassungen.", "Sport"),
            ScheduleSlot(11, 0, 15, 0, "Snooker Live / Ski Alpin Weltcup Live", "Präzision am Tisch oder rasante Abfahrten.", "Sport Live"),
            ScheduleSlot(15, 0, 18, 30, "Radsport: Tour de France / Giro d'Italia Live", "Die großen Etappen der Grand Tours.", "Sport Live"),
            ScheduleSlot(18, 30, 20, 15, "Tennis: Australian Open / French Open / US Open Live", "Die besten Ballwechsel der Grand Slam Turniere.", "Sport Live"),
            ScheduleSlot(20, 15, 23, 0, "Eurosport Primetime Live Event: Snooker / Motorsport", "Spannung pur bis zur letzten Entscheidung.", "Sport Live"),
            ScheduleSlot(23, 0, 24, 0, "Eurosport News Flash", "Kompakte Zusammenfassung der Weltsport-Ereignisse.", "Sport")
        ),

        // 35. SPORT1
        "sport1.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Sport1 Night: Teleshopping & Classics", "Unterhaltung und historische Sportmomente.", "Unterhaltung"),
            ScheduleSlot(6, 0, 11, 0, "Storage Wars - Die Geschäftemacher", "Bieterwettstreit um verlassene Lagerabteile.", "Doku-Soap"),
            ScheduleSlot(11, 0, 13, 30, "Der Doppelpass Live", "Florian König und Experten debattieren am Sonntagmittag.", "Sport Talk"),
            ScheduleSlot(13, 30, 17, 0, "Darts: PDC World Darts Championship Live", "Ally Pally Atmosphäre und High-Finishes.", "Sport Live"),
            ScheduleSlot(17, 0, 19, 30, "Bundesliga Pur & 2. Bundesliga Highlights", "Alle Tore und strittige Szenen.", "Sport"),
            ScheduleSlot(19, 30, 20, 15, "Sport1 News", "Aktuelles aus der Welt des Sports.", "Nachrichten"),
            ScheduleSlot(20, 15, 23, 0, "PDC Darts Live / Topspiel 2. Bundesliga Live", "Der Samstagabend-Kracher im Free-TV.", "Sport Live"),
            ScheduleSlot(23, 0, 24, 0, "Sport1 Darts Highlights & Analysen", "Elmar Paulke und Experten fassen den Abend zusammen.", "Sport")
        ),

        // 36. SKY CINEMA PREMIEREN & BEST OF
        "skycinema.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Sky Cinema Night: Thriller & Sci-Fi", "Hochspannung und Mystery-Blockbuster.", "Film"),
            ScheduleSlot(6, 0, 9, 30, "Sky Cinema Morning: Familien-Kino & Animation", "Beste Unterhaltung für die ganze Familie.", "Film"),
            ScheduleSlot(9, 30, 12, 0, "Sky Cinema Action: Blockbuster", "Hollywood-Stars in packenden Actionkrachern.", "Film"),
            ScheduleSlot(12, 0, 14, 30, "Sky Cinema Comedy: Beste Lacher", "Gute Laune mit den besten Filmkomödien.", "Film"),
            ScheduleSlot(14, 30, 17, 0, "Sky Cinema Drama & Romantik", "Große Emotionen und Oscar-prämierte Darsteller.", "Film"),
            ScheduleSlot(17, 0, 20, 15, "Sky Cinema Countdown & Film-Highlight", "Vorfreude auf die große TV-Premiere.", "Film"),
            ScheduleSlot(20, 15, 22, 30, "Sky Cinema Premieren-Hit (Exklusiv)", "Neueste Kino-Blockbuster erstmals im Pay-TV.", "Film"),
            ScheduleSlot(22, 30, 24, 0, "Sky Cinema Late Night Horror / Action", "Adrenalingeladener Abschluss des Filmabends.", "Film")
        ),

        // 37. DISCOVERY CHANNEL & NATIONAL GEOGRAPHIC
        "discovery.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Discovery Night: Tiefsee-Mysterien & All", "Geheimnisse der Ozeane und Galaxien.", "Dokumentation"),
            ScheduleSlot(6, 0, 10, 0, "How It's Made - So wird's gemacht", "Faszinierende Einblicke in industrielle Fertigung.", "Wissen"),
            ScheduleSlot(10, 0, 14, 0, "MythBusters - Die Wissensjäger", "Urbane Mythen wissenschaftlich überprüft.", "Wissen"),
            ScheduleSlot(14, 0, 18, 0, "Expedition Unknown mit Josh Gates", "Archäologische Rätsel und verlorene Zivilisationen.", "Abenteuer"),
            ScheduleSlot(18, 0, 20, 15, "Naked and Afraid - Das Survival-Experiment", "Überleben in der rauen Wildnis.", "Doku-Soap"),
            ScheduleSlot(20, 15, 22, 30, "Discovery Prime: Das Universum / Haie hautnah", "Spektakuläre Naturdokumentationen in 4K.", "Dokumentation"),
            ScheduleSlot(22, 30, 24, 0, "Geheimakte NASA & Alien-Akten", "Wissenschaftler untersuchen ungelöste Phänomene.", "Dokumentation")
        ),

        // 38. WARNER TV & 13TH STREET & SYFY
        "warnertv.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Pay-TV Serien-Marathon: The Wire / Sopranos", "Preisgekrönte HBO-Meisterwerke durch die Nacht.", "Serie"),
            ScheduleSlot(6, 0, 11, 0, "Two and a Half Men & The Big Bang Theory", "Sitcom-Marathon am Vormittag.", "Comedy"),
            ScheduleSlot(11, 0, 15, 0, "Chicago P.D. & Law & Order: SVU", "Packende Ermittlerteams im Einsatz.", "Krimi"),
            ScheduleSlot(15, 0, 18, 0, "The Flash & Supernatural", "Superhelden und Mystery-Abenteuer.", "Sci-Fi"),
            ScheduleSlot(18, 0, 20, 15, "4 Blocks / Babylon Berlin", "Deutsche Premium-Serien mit Tiefgang.", "Serie"),
            ScheduleSlot(20, 15, 22, 30, "Warner TV Prime: House of the Dragon / The Last of Us", "Epische Fantasy- und Drama-Highlights.", "Serie"),
            ScheduleSlot(22, 30, 24, 0, "Dexter / Breaking Bad", "Kultserien zur späten Stunde.", "Serie")
        ),

        // 39. DISNEY CHANNEL & NICKELODEON
        "disneychannel.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Disney Night: Phineas und Ferb & Kim Possible", "Kult-Zeichentrickserien nonstop.", "Kinder"),
            ScheduleSlot(6, 0, 12, 0, "Micky Maus Spielhaus & Bluey", "Spannende Abenteuer für die Kleinsten.", "Kinder"),
            ScheduleSlot(12, 0, 15, 0, "Willkommen im Haus der Eulen & DuckTales", "Action, Humor und Zauberei.", "Kinder"),
            ScheduleSlot(15, 0, 18, 0, "Miraculous - Geschichten von Ladybug und Cat Noir", "Die Pariser Superhelden im Einsatz.", "Kinder"),
            ScheduleSlot(18, 0, 20, 15, "Phineas und Ferb & Gravity Falls", "Lachen und Rätsel lösen.", "Kinder"),
            ScheduleSlot(20, 15, 22, 15, "Disney Filmabend: Der König der Löwen / Aladdin", "Große Disney-Meisterwerke für die ganze Familie.", "Film"),
            ScheduleSlot(22, 15, 24, 0, "Modern Family & Hör mal, wer da hämmert!", "Die besten Comedy-Serien am späten Abend.", "Comedy")
        ),

        // 40. ANIMAL PLANET (DACH & International)
        "animalplanet.de" to listOf(
            ScheduleSlot(0, 0, 6, 0, "Animal Planet Night: Geheimnisse der Tierwelt", "Nachtaktive Raubtiere und Überlebensstrategien in der Natur.", "Dokumentation"),
            ScheduleSlot(6, 0, 9, 0, "Unglaubliche Tierfreundschaften & Tierretter", "Herzerwärmende Geschichten geretteter Tiere und ihrer Pfleger.", "Tiere"),
            ScheduleSlot(9, 0, 11, 30, "Dr. Jeff: Der Rocky Mountain Tierarzt", "Spektakuläre Notoperationen und Rettungseinsätze in Colorado.", "Doku-Soap"),
            ScheduleSlot(11, 30, 14, 0, "Der Baumhaus-Profi / Pit Bulls auf Bewährung", "Außergewöhnliche Rettungsprojekte für Mensch und Tier.", "Doku-Soap"),
            ScheduleSlot(14, 0, 15, 30, "Fluss-Monster mit Jeremy Wade", "Auf der Suche nach den gefährlichsten Süßwassermonstern weltweit.", "Abenteuer"),
            ScheduleSlot(15, 30, 17, 0, "Die Aquarium-Profis (Tanked)", "Wayde King und Brett Raymer bauen gigantische, spektakuläre Luxus-Aquarien.", "Doku-Soap"),
            ScheduleSlot(17, 0, 18, 30, "Tierarzt Dr. Dreesen - Praxisgeschichten", "Der sympathische Landtierarzt im Dauereinsatz.", "Doku-Soap"),
            ScheduleSlot(18, 30, 20, 15, "Amerikas spektakulärste Nationalparks", "Atemberaubende Naturwunder und seltene Wildtierarten.", "Naturdoku"),
            ScheduleSlot(20, 15, 22, 15, "Animal Planet Primetime: Giganten der Ozeane & Weiße Haie", "Meeresforscher lüften die letzten Geheimnisse der Tiefsee.", "Naturdoku"),
            ScheduleSlot(22, 15, 24, 0, "Monster-Karpfen & Jagd auf Raubfische", "Aufregende Angelabenteuer und Expeditionen an abgelegenen Gewässern.", "Abenteuer")
        )
    )

    private fun resolveScheduleKeyAndSlots(tvgId: String?, channelName: String): Pair<String, List<ScheduleSlot>> {
        val primaryKey = matchKey(tvgId, channelName)
        DACH_EPG_SCHEDULES[primaryKey]?.let { return Pair(primaryKey, it) }

        val aliasKey = when {
            primaryKey.startsWith("servustv") -> "servustv.de"
            primaryKey.startsWith("eurosport") -> "eurosport.de"
            primaryKey.startsWith("skysportbundesliga") || primaryKey.startsWith("skybundesliga") -> "skybundesliga.de"
            primaryKey.startsWith("skysport") -> "skysport.de"
            primaryKey.startsWith("skycinema") -> "skycinema.de"
            primaryKey.startsWith("dazn") -> "dazn.de"
            primaryKey.startsWith("orf3") || primaryKey.startsWith("orfiii") -> "orf2.at"
            primaryKey.startsWith("srfinfo") -> "srf1.ch"
            primaryKey.startsWith("mdr") || primaryKey.startsWith("hr") || primaryKey.startsWith("rbb") -> "wdr.de"
            primaryKey.startsWith("natgeo") || primaryKey.startsWith("nationalgeographic") -> "discovery.de"
            primaryKey.startsWith("13thstreet") || primaryKey.startsWith("syfy") -> "warnertv.de"
            primaryKey.startsWith("nick") -> "disneychannel.de"
            else -> null
        }
        if (aliasKey != null) {
            DACH_EPG_SCHEDULES[aliasKey]?.let { return Pair(primaryKey.ifBlank { aliasKey }, it) }
        }
        return Pair(primaryKey.ifBlank { normalizeChannelName(channelName).ifBlank { "live.tv" } }, generateIntelligentSchedule(channelName))
    }

    /**
     * Generates a continuous 24/7 schedule list of EpgProgram items for the requested time window
     * using the curated DACH station schedules or station-genre schedules when XMLTV feeds do not
     * cover a channel yet.
     */
    fun getScheduleForWindow(
        tvgId: String?,
        channelName: String,
        windowStartMs: Long,
        windowEndMs: Long,
        timezoneId: String = "Europe/Berlin"
    ): List<EpgProgram> {
        val (resolvedKey, slots) = resolveScheduleKeyAndSlots(tvgId, channelName)
        if (slots.isEmpty()) return emptyList()

        val tz = try {
            TimeZone.getTimeZone(timezoneId.ifBlank { "Europe/Berlin" })
        } catch (_: Exception) {
            SOURCE_TIMEZONE
        }

        val result = ArrayList<EpgProgram>(slots.size * 3)
        val baseCal = Calendar.getInstance(tz).apply {
            timeInMillis = windowStartMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }

        for (dayIdx in 0..3) {
            val dayStartMs = baseCal.timeInMillis
            for ((slotIdx, slot) in slots.withIndex()) {
                val startMs = dayStartMs + ((slot.startHour * 60L + slot.startMin) * 60_000L)
                val endMs = dayStartMs + ((slot.endHour * 60L + slot.endMin) * 60_000L)
                if (endMs > windowStartMs && startMs < windowEndMs && endMs > startMs) {
                    result.add(
                        EpgProgram(
                            id = "${resolvedKey}_${dayIdx}_${slotIdx}_$startMs",
                            channelTvgId = resolvedKey,
                            channelName = channelName,
                            title = slot.title,
                            description = slot.desc,
                            startTime = startMs,
                            endTime = endMs,
                            category = slot.category
                        )
                    )
                }
            }
            baseCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return result.sortedBy { it.startTime }
    }

    fun getEpgForChannel(
        tvgId: String?,
        channelName: String,
        timezoneId: String = "Europe/Berlin",
        offsetHours: Int = 0
    ): List<EpgProgram> {
        val now = System.currentTimeMillis() + (offsetHours * 3600_000L)
        return getScheduleForWindow(
            tvgId = tvgId,
            channelName = channelName,
            windowStartMs = now - (6 * 3600_000L),
            windowEndMs = now + (24 * 3600_000L),
            timezoneId = timezoneId
        )
    }

    /**
     * Determines current and next program according to strict criteria:
     * - Current program: startTime <= now < endTime
     * - Next program: earliest valid program where startTime > now
     * - Safely filters invalid/corrupted time ranges, deduplicates, and resolves overlaps.
     */
    fun findCurrentAndNextProgram(
        programs: List<EpgProgram>,
        currentTimeUtcMs: Long = System.currentTimeMillis()
    ): Pair<EpgProgram?, EpgProgram?> {
        if (programs.isEmpty()) return Pair(null, null)

        val validPrograms = programs
            .filter { it.startTime > 0 && it.endTime > it.startTime }
            .distinctBy { "${it.channelTvgId}_${it.startTime}_${it.endTime}" }
            .sortedBy { it.startTime }

        // 1. Current program: startTime <= now < endTime
        val current = validPrograms
            .filter { it.startTime <= currentTimeUtcMs && currentTimeUtcMs < it.endTime }
            .maxByOrNull { it.startTime }

        // 2. Next program: earliest valid entry whose startTime > now
        val next = if (current != null) {
            validPrograms
                .filter { it.startTime >= current.endTime || it.startTime > currentTimeUtcMs }
                .filter { it.startTime > currentTimeUtcMs }
                .minByOrNull { it.startTime }
        } else {
            validPrograms
                .filter { it.startTime > currentTimeUtcMs }
                .minByOrNull { it.startTime }
        }

        return Pair(current, next)
    }

    fun getCurrentAndNextProgram(
        tvgId: String?,
        channelName: String,
        timezoneId: String = "Europe/Berlin",
        offsetHours: Int = 0,
        targetTimeMs: Long = System.currentTimeMillis(),
        roomPrograms: List<EpgProgram>? = null
    ): Pair<EpgProgram?, EpgProgram?> {
        val effectiveTime = targetTimeMs + (offsetHours * 3600_000L)

        if (!roomPrograms.isNullOrEmpty()) {
            val (current, next) = findCurrentAndNextProgram(roomPrograms, effectiveTime)
            if (current != null || next != null) {
                return Pair(current, next)
            }
        }

        val timeBucket = effectiveTime / 120_000L
        val cacheKey = "${tvgId.orEmpty()}|$channelName|$timezoneId|$offsetHours|$timeBucket"
        programCache.get(cacheKey)?.let { return it }

        val fallbackList = getScheduleForWindow(
            tvgId = tvgId,
            channelName = channelName,
            windowStartMs = effectiveTime - (4 * 3600_000L),
            windowEndMs = effectiveTime + (12 * 3600_000L),
            timezoneId = timezoneId
        )
        val resolved = findCurrentAndNextProgram(fallbackList, effectiveTime)
        programCache.put(cacheKey, resolved)
        return resolved
    }

    fun formatProgramTime(timeInMillis: Long, timezoneId: String = "Europe/Berlin"): String {
        return try {
            val tz = TimeZone.getTimeZone(timezoneId)
            val sdf = SimpleDateFormat("HH:mm", Locale.GERMAN)
            sdf.timeZone = tz
            sdf.format(timeInMillis)
        } catch (e: Exception) {
            val sdf = SimpleDateFormat("HH:mm", Locale.GERMAN)
            sdf.format(timeInMillis)
        }
    }

    fun formatProgramRange(startTime: Long, endTime: Long, timezoneId: String = "Europe/Berlin"): String {
        return "${formatProgramTime(startTime, timezoneId)} - ${formatProgramTime(endTime, timezoneId)}"
    }

    /**
     * Extracts all search candidate keys for a given channel to guarantee 100% match rate
     * against XMLTV EPG databases from any provider (e.g. epgshare, iptv-org, custom xmltv feeds).
     */
    fun getCandidateKeys(tvgId: String?, channelName: String, tvgName: String? = null): List<String> {
        val keys = mutableSetOf<String>()

        fun addVariations(raw: String?) {
            if (raw.isNullOrBlank()) return
            val trimmed = raw.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            keys.add(lower)

            // Remove country domains like .de, .at, .ch, .uk, .us, .com
            val withoutDomain = lower.replace(REGEX_DOMAIN, "").trim()
            if (withoutDomain.isNotBlank()) keys.add(withoutDomain)

            // Normalized without quality/country prefixes
            val cleaned = normalizeChannelName(lower)
            if (cleaned.isNotBlank()) {
                keys.add(cleaned)
                // Pure alphanumeric slug (no spaces/symbols)
                val slug = cleaned.replace(REGEX_NON_ALPHANUMERIC, "")
                if (slug.isNotBlank()) keys.add(slug)
            }
        }

        addVariations(tvgId)
        addVariations(tvgName)
        addVariations(channelName)

        // Add exact canonical DACH TVG IDs only if matched to this EXACT station
        val dachMatch = DachMasterDatabase.findMatch(channelName, tvgName, tvgId)
        if (dachMatch.isMatch && dachMatch.channel != null) {
            val master = dachMatch.channel
            val chDigits = channelName.filter { it.isDigit() }
            val masterDigits = master.canonicalName.filter { it.isDigit() }
            if (chDigits.isEmpty() || masterDigits.isEmpty() || chDigits == masterDigits) {
                master.tvgIds.forEach { addVariations(it) }
                addVariations(master.canonicalName)
            }
        }

        return keys.filter { it.isNotBlank() && it.length >= 2 }.distinct()
    }

    fun hasPreloadedSchedule(tvgId: String?, channelName: String): Boolean {
        return false
    }

    /**
     * Exact canonical channel key resolution via DachMasterDatabase or explicit tvgId.
     * Prevents false cross-channel collisions (e.g. ATV/Puls4 to ORF, Sixx to Pro7, etc.).
     */
    fun matchKey(tvgId: String?, channelName: String): String {
        val cacheLookupKey = "${tvgId ?: ""}##$channelName"
        val cached = matchKeyCache.get(cacheLookupKey)
        if (cached != null) return cached

        val dachMatch = DachMasterDatabase.findMatch(channelName, null, tvgId)
        val result = if (dachMatch.isMatch && dachMatch.channel != null) {
            val master = dachMatch.channel
            master.tvgIds.firstOrNull()?.lowercase(Locale.ROOT) ?: master.channelId.lowercase(Locale.ROOT)
        } else if (!tvgId.isNullOrBlank()) {
            tvgId.trim().lowercase(Locale.ROOT)
        } else {
            normalizeChannelName(channelName)
        }

        matchKeyCache.put(cacheLookupKey, result)
        return result
    }

    fun normalizeChannelName(raw: String): String {
        val cached = normalizedNameCache.get(raw)
        if (cached != null) return cached

        val normalized = raw.lowercase(Locale.ROOT)
            .replace(REGEX_PREFIXES, " ")
            .replace(REGEX_NON_ALPHANUMERIC, " ")
            .replace(REGEX_WHITESPACE, " ")
            .trim()

        normalizedNameCache.put(raw, normalized)
        return normalized
    }

    fun generateIntelligentSchedule(name: String): List<ScheduleSlot> {
        val cleanName = name.replace(REGEX_SCHEDULE_TAGS, "").trim()
        val displayName = if (cleanName.isNotBlank()) cleanName else name
        val lower = displayName.lowercase(Locale.ROOT)

        val isSport = lower.contains("sport") || lower.contains("fußball") || lower.contains("soccer") || lower.contains("liga")
        val isMovie = lower.contains("cinema") || lower.contains("movie") || lower.contains("film") || lower.contains("kino")
        val isNews = lower.contains("news") || lower.contains("nachricht") || lower.contains("info") || lower.contains("24")
        val isKids = lower.contains("kid") || lower.contains("junior") || lower.contains("cartoon") || lower.contains("toon")
        val isDoku = lower.contains("doku") || lower.contains("docu") || lower.contains("nature") || lower.contains("wild")

        return when {
            isSport -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Replay & Highlights", "Die spektakulärsten Spielszenen und Tore.", "Sport"),
                ScheduleSlot(6, 0, 11, 0, "$displayName • Matchday Countdown", "Vorberichte, Tabellenstände und Interviews.", "Sport"),
                ScheduleSlot(11, 0, 14, 0, "$displayName • Live-Magazin", "Aktuelle Trainingsberichte und Taktikanalysen.", "Sport"),
                ScheduleSlot(14, 0, 17, 30, "$displayName • Spitzensport Live", "Spannende Wettkämpfe und Livespiele.", "Sport Live"),
                ScheduleSlot(17, 30, 20, 15, "$displayName • Spieltag & Highlights", "Zusammenfassung der Top-Spiele.", "Sport"),
                ScheduleSlot(20, 15, 22, 45, "$displayName • Primetime Match Live", "Das Top-Duell des Abends live in HD.", "Sport Live"),
                ScheduleSlot(22, 45, 24, 0, "$displayName • Expertenrunde & Analyse", "Diskussion der strittigen Szenen und Entscheidungen.", "Sport Talk")
            )

            isMovie -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Late Night Thriller", "Spannung und packende Filme zur späten Stunde.", "Film"),
                ScheduleSlot(6, 0, 9, 30, "$displayName • Morgen-Kino", "Klassiker und lockere Filmunterhaltung.", "Film"),
                ScheduleSlot(9, 30, 12, 30, "$displayName • Action & Abenteuer", "Hollywood-Blockbuster am Vormittag.", "Film"),
                ScheduleSlot(12, 30, 15, 0, "$displayName • Comedy & Romance", "Gefühlvolle und heitere Filmperlen.", "Film"),
                ScheduleSlot(15, 0, 17, 45, "$displayName • Meisterwerk des Kinos", "Preisgekrönte Darsteller und Regisseure.", "Film"),
                ScheduleSlot(17, 45, 20, 15, "$displayName • Vorabend-Highlight", "Spannender Spielfilm vor der Primetime.", "Film"),
                ScheduleSlot(20, 15, 22, 30, "$displayName • Primetime Blockbuster", "Der Top-Film des Abends in brillantem HD.", "Film"),
                ScheduleSlot(22, 30, 24, 0, "$displayName • Spätfilm-Highlight", "Kultstreifen und Hollywood-Spannung.", "Film")
            )

            isNews -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Weltnachrichten der Nacht", "Laufende Breaking News und Berichte.", "Nachrichten"),
                ScheduleSlot(6, 0, 12, 0, "$displayName • Live am Morgen", "Wirtschaft, Politik und Live-Schalten.", "Nachrichten"),
                ScheduleSlot(12, 0, 18, 0, "$displayName • Nachmittags-Update", "Live-Berichte und Korrespondentenberichte.", "Nachrichten"),
                ScheduleSlot(18, 0, 20, 15, "$displayName • Das Abendjournal", "Zusammenfassung der wichtigsten Tagesereignisse.", "Nachrichten"),
                ScheduleSlot(20, 15, 22, 0, "$displayName • Primetime Brennpunkt", "Hintergrundberichte und Experteninterviews.", "Nachrichten"),
                ScheduleSlot(22, 0, 24, 0, "$displayName • Late News & Fazit", "Ausblick auf die Themen des kommenden Tages.", "Nachrichten")
            )

            isKids -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Gute-Nacht-Schleife", "Sanfte Musik und Schlafgeschichten.", "Kinder"),
                ScheduleSlot(6, 0, 10, 0, "$displayName • Morgenspaß & Zeichentrick", "Bunte Serien für einen fröhlichen Start.", "Kinder"),
                ScheduleSlot(10, 0, 14, 0, "$displayName • Cartoons & Comedy", "Lustige Abenteuer und Animation.", "Kinder"),
                ScheduleSlot(14, 0, 18, 0, "$displayName • Serien-Marathon", "Spannende Geschichten für Schulkinder.", "Kinder"),
                ScheduleSlot(18, 0, 20, 15, "$displayName • Vorabend-Special", "Die beliebtesten Zeichentrickhelden.", "Kinder"),
                ScheduleSlot(20, 15, 22, 0, "$displayName • Familienfilm", "Großer Animationsfilm für Jung und Alt.", "Kinder / Film"),
                ScheduleSlot(22, 0, 24, 0, "$displayName • Night Cartoons", "Klassiker für ältere Zeichentrickfans.", "Animation")
            )

            isDoku -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Geheimnisse der Natur", "Faszinierende Einblicke in unbekannte Welten.", "Dokumentation"),
                ScheduleSlot(6, 0, 11, 0, "$displayName • Faszination Erde", "Atemberaubende Landschaften und Tiere.", "Dokumentation"),
                ScheduleSlot(11, 0, 15, 0, "$displayName • Technik & Pioniere", "Geniale Erfindungen und Megabauten.", "Wissen"),
                ScheduleSlot(15, 0, 18, 30, "$displayName • Wilde Kontinente", "Expeditionen in unberührte Wildnis.", "Dokumentation"),
                ScheduleSlot(18, 30, 20, 15, "$displayName • Geschichte & Rätsel", "Verlorene Schätze und antike Kulturen.", "Dokumentation"),
                ScheduleSlot(20, 15, 22, 30, "$displayName • Primetime Doku-Event", "Hochkarätige 4K-Dokumentation.", "Dokumentation"),
                ScheduleSlot(22, 30, 24, 0, "$displayName • Universum & Kosmos", "Sterne, Galaxien und moderne Raumfahrt.", "Wissen")
            )

            else -> listOf(
                ScheduleSlot(0, 0, 6, 0, "$displayName • Nachtprogramm", "Wiederholungen und Dokumentationen durch die Nacht.", "Nachtjournal"),
                ScheduleSlot(6, 0, 9, 0, "$displayName • Morgenmagazin", "Frische Nachrichten, Hintergrundberichte und Wetter.", "Nachrichten"),
                ScheduleSlot(9, 0, 12, 0, "$displayName • Vormittagsjournal & Serie", "Unterhaltung, Ratgeber und spannende Serien.", "Magazin"),
                ScheduleSlot(12, 0, 14, 0, "$displayName • Mittagsmagazin", "Journalistisches Nachrichtenmagazin mit aktuellen Brennpunkten.", "Nachrichten"),
                ScheduleSlot(14, 0, 17, 30, "$displayName • Nachmittagsshow & Doku", "Faszinierende Einblicke in Natur, Alltag und Gesellschaft.", "Dokumentation"),
                ScheduleSlot(17, 30, 19, 0, "$displayName • Vorabend & Boulevard", "Aktuelle Promi-News, Gesellschaftsthemen und Quiz.", "Show"),
                ScheduleSlot(19, 0, 20, 0, "$displayName • Hauptnachrichten & Magazin", "Kompakte Zusammenfassung der wichtigsten Tagesereignisse.", "Nachrichten"),
                ScheduleSlot(20, 0, 20, 15, "$displayName • Brennpunkt & Update", "Kurzberichte vor dem Hauptabendprogramm.", "Information"),
                ScheduleSlot(20, 15, 22, 15, "$displayName • Prime Time Highlight", "Der beste Film oder die Top-Show des Abends.", "Prime Time"),
                ScheduleSlot(22, 15, 23, 30, "$displayName • Late Night Talk & Journal", "Spätabendliche Berichterstattung, Analysen und Interviews.", "Talk"),
                ScheduleSlot(23, 30, 24, 0, "$displayName • Nachtjournal", "Tagesausklang mit den wichtigsten News der Welt.", "Nachrichten")
            )
        }
    }
}
