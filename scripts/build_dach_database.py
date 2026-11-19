import json
import os
import re

channels = []

def add_ch(channelId, canonicalName, shortName, countryCode, countryName, region, language,
           category, genre, popularityRank, popularityScore, marketShare, marketShareYear, marketShareSource,
           aliases, keywords, tvgIds, tvgNamePatterns, matchPatterns, excludePatterns,
           isNational, isRegional, isPublicService, isPrivate, logo=""):
    channels.append({
        "channelId": channelId,
        "canonicalName": canonicalName,
        "shortName": shortName,
        "countryCode": countryCode,
        "countryName": countryName,
        "region": region,
        "language": language,
        "category": category,
        "genre": genre,
        "popularityRank": popularityRank,
        "popularityScore": popularityScore,
        "marketShare": marketShare,
        "marketShareYear": marketShareYear,
        "marketShareSource": marketShareSource,
        "aliases": aliases,
        "keywords": keywords,
        "tvgIds": tvgIds,
        "tvgNamePatterns": tvgNamePatterns,
        "matchPatterns": matchPatterns,
        "excludePatterns": excludePatterns,
        "isNational": isNational,
        "isRegional": isRegional,
        "isPublicService": isPublicService,
        "isPrivate": isPrivate,
        "logo": logo
    })

# ==========================================
# 1. DEUTSCHLAND (DE)
# ==========================================
# Öffentlich-rechtlich bundesweit
add_ch("DE_ARD_DAS_ERSTE", "Das Erste", "ARD", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Vollprogramm", 2, 98.0, 11.9, 2024, "AGF Videoforschung 2024",
       ["ARD", "Das Erste HD", "Das Erste FHD", "Das Erste UHD", "ARD HD", "DE: Das Erste", "DE| Das Erste HD", "DE: ARD HD"],
       ["ard", "das erste", "erste deutsche fernsehen"], ["DasErste.de", "ard.de"], ["das erste*", "ard hd*"],
       ["(?i)\\b(das\\s*erste|ard)\\b"], ["(?i)\\b(ard\\s*alpha|radio)\\b"], True, False, True, False)

add_ch("DE_ZDF", "ZDF", "ZDF", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Vollprogramm", 1, 100.0, 14.6, 2024, "AGF Videoforschung 2024",
       ["ZDF HD", "ZDF FHD", "ZDF UHD", "DE: ZDF", "DE: ZDF HD", "DE| ZDF HD"],
       ["zdf", "zweites deutsches fernsehen"], ["ZDF.de"], ["zdf*"],
       ["(?i)\\bzdf\\b"], ["(?i)\\b(zdfneo|zdfinfo|neo|info)\\b"], True, False, True, False)

add_ch("DE_3SAT", "3sat", "3sat", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Kultur", 20, 72.0, 1.4, 2024, "AGF Videoforschung 2024",
       ["3sat HD", "3sat FHD", "DE: 3sat", "DE| 3sat HD"],
       ["3sat", "dreisat", "kultur"], ["3sat.de"], ["3sat*"],
       ["(?i)\\b3sat\\b"], ["(?i)\\b3\\+\\b"], True, False, True, False)

add_ch("DE_ARTE", "arte", "arte", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Kultur", 23, 70.0, 1.2, 2024, "AGF Videoforschung 2024",
       ["arte HD", "arte FHD", "DE: arte", "DE: ARTE HD"],
       ["arte", "kulturkanal"], ["arte.de"], ["arte*"],
       ["(?i)\\barte\\b"], ["(?i)\\bfrance\\b"], True, False, True, False)

add_ch("DE_PHOENIX", "Phoenix", "Phoenix", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "News", 27, 65.0, 1.0, 2024, "AGF Videoforschung 2024",
       ["Phoenix HD", "Phoenix FHD", "DE: Phoenix", "DE: PHOENIX HD"],
       ["phoenix", "ereigniskanal", "politik"], ["phoenix.de"], ["phoenix*"],
       ["(?i)\\bphoenix\\b"], [], True, False, True, False)

add_ch("DE_ZDFNEO", "ZDFneo", "neo", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Entertainment", 8, 85.0, 2.7, 2024, "AGF Videoforschung 2024",
       ["ZDF neo", "ZDFneo HD", "ZDFneo FHD", "DE: ZDFneo", "DE: ZDF neo HD"],
       ["zdfneo", "zdf neo"], ["zdfneo.de"], ["zdfneo*", "zdf neo*"],
       ["(?i)\\bzdf\\s*neo\\b"], [], True, False, True, False)

add_ch("DE_ZDFINFO", "ZDFinfo", "info", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Documentary", 16, 76.0, 1.8, 2024, "AGF Videoforschung 2024",
       ["ZDF info", "ZDFinfo HD", "ZDFinfo FHD", "DE: ZDFinfo", "DE: ZDF info HD"],
       ["zdfinfo", "zdf info", "doku"], ["zdfinfo.de"], ["zdfinfo*", "zdf info*"],
       ["(?i)\\bzdf\\s*info\\b"], [], True, False, True, False)

add_ch("DE_ONE", "ONE", "ONE", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Entertainment", 28, 64.0, 1.0, 2024, "AGF Videoforschung 2024",
       ["ONE HD", "ARD ONE", "ARD ONE HD", "DE: ONE", "DE: ONE HD"],
       ["one", "ard one", "einsfestival"], ["one.ard.de"], ["one*"],
       ["(?i)\\b(ard\\s*one|one\\s*hd)\\b"], ["(?i)\\b(formula\\s*one|one1)\\b"], True, False, True, False)

add_ch("DE_KIKA", "KiKA", "KiKA", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Kids", 19, 73.0, 1.4, 2024, "AGF Videoforschung 2024",
       ["KiKA HD", "KiKA FHD", "DE: KiKA", "DE: KIKA HD"],
       ["kika", "kinderkanal"], ["kika.de"], ["kika*"],
       ["(?i)\\bkika\\b"], [], True, False, True, False)

add_ch("DE_TAGESSCHAU24", "tagesschau24", "tagesschau24", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "News", 35, 58.0, 0.5, 2024, "AGF Videoforschung 2024",
       ["tagesschau 24", "tagesschau24 HD", "DE: tagesschau24"],
       ["tagesschau24", "tagesschau 24", "eins extra"], ["tagesschau24.de"], ["tagesschau24*"],
       ["(?i)\\btagesschau\\s*24\\b"], [], True, False, True, False)

add_ch("DE_ARD_ALPHA", "ARD-alpha", "ARD-alpha", "DE", "Deutschland", "DE-NAT", "de",
       "Public", "Education", 36, 52.0, 0.3, 2024, "AGF Videoforschung 2024",
       ["ARD alpha", "ARD-alpha HD", "DE: ARD alpha"],
       ["ard-alpha", "ard alpha", "br-alpha"], ["alpha.ard.de"], ["ard alpha*", "ard-alpha*"],
       ["(?i)\\bard[- ]?alpha\\b"], [], True, False, True, False)

# Dritte Programme (Regional DE)
add_ch("DE_BR_FERNSEHEN", "BR Fernsehen", "BR", "DE", "Deutschland", "DE-BY", "de",
       "Regional", "Regional", 14, 80.0, 2.1, 2024, "AGF Videoforschung 2024",
       ["BR Fernsehen HD", "BR HD", "BR Nord HD", "BR Süd HD", "DE: BR Fernsehen", "Bayerisches Fernsehen"],
       ["br", "bayerischer rundfunk", "bayern"], ["br.de"], ["br fernsehen*"],
       ["(?i)\\b(br\\s*fernsehen|bayerisches\\s*fernsehen)\\b"], [], False, True, True, False)

add_ch("DE_HR_FERNSEHEN", "hr-fernsehen", "HR", "DE", "Deutschland", "DE-HE", "de",
       "Regional", "Regional", 29, 63.0, 0.9, 2024, "AGF Videoforschung 2024",
       ["hr Fernsehen HD", "hr HD", "hr-fernsehen HD", "DE: hr-fernsehen", "Hessischer Rundfunk"],
       ["hr", "hr3", "hessen"], ["hr.de"], ["hr fernsehen*"],
       ["(?i)\\bhr[- ]?fernsehen\\b"], [], False, True, True, False)

add_ch("DE_MDR_FERNSEHEN", "MDR Fernsehen", "MDR", "DE", "Deutschland", "DE-SN", "de",
       "Regional", "Regional", 10, 83.0, 2.5, 2024, "AGF Videoforschung 2024",
       ["MDR Fernsehen HD", "MDR HD", "MDR Sachsen HD", "MDR Thüringen HD", "MDR S-Anhalt HD", "DE: MDR"],
       ["mdr", "mitteldeutscher rundfunk", "sachsen", "thueringen"], ["mdr.de"], ["mdr fernsehen*"],
       ["(?i)\\bmdr(\\s*fernsehen)?\\b"], [], False, True, True, False)

add_ch("DE_NDR_FERNSEHEN", "NDR Fernsehen", "NDR", "DE", "Deutschland", "DE-HH", "de",
       "Regional", "Regional", 11, 82.0, 2.4, 2024, "AGF Videoforschung 2024",
       ["NDR Fernsehen HD", "NDR HD", "NDR Hamburg HD", "NDR NDS HD", "NDR SH HD", "NDR MV HD", "DE: NDR"],
       ["ndr", "norddeutscher rundfunk", "hamburg", "niedersachsen"], ["ndr.de"], ["ndr fernsehen*"],
       ["(?i)\\bndr(\\s*fernsehen)?\\b"], [], False, True, True, False)

add_ch("DE_RADIO_BREMEN_TV", "Radio Bremen TV", "RB TV", "DE", "Deutschland", "DE-HB", "de",
       "Regional", "Regional", 37, 50.0, 0.3, 2024, "AGF Videoforschung 2024",
       ["Radio Bremen HD", "RB TV HD", "DE: Radio Bremen TV"],
       ["radio bremen tv", "rb tv", "bremen"], ["radiobremen.de"], ["radio bremen*"],
       ["(?i)\\bradio\\s*bremen(\\s*tv)?\\b"], [], False, True, True, False)

add_ch("DE_RBB_FERNSEHEN", "rbb Fernsehen", "rbb", "DE", "Deutschland", "DE-BE", "de",
       "Regional", "Regional", 30, 62.0, 0.8, 2024, "AGF Videoforschung 2024",
       ["rbb Fernsehen HD", "rbb HD", "rbb Berlin HD", "rbb Brandenburg HD", "DE: rbb"],
       ["rbb", "berlin", "brandenburg"], ["rbb.de"], ["rbb fernsehen*"],
       ["(?i)\\brbb(\\s*fernsehen)?\\b"], [], False, True, True, False)

add_ch("DE_SR_FERNSEHEN", "SR Fernsehen", "SR", "DE", "Deutschland", "DE-SL", "de",
       "Regional", "Regional", 38, 51.0, 0.3, 2024, "AGF Videoforschung 2024",
       ["SR Fernsehen HD", "SR HD", "DE: SR Fernsehen"],
       ["sr", "saarlaendischer rundfunk", "saarland"], ["sr.de"], ["sr fernsehen*"],
       ["(?i)\\bsr\\s*fernsehen\\b"], [], False, True, True, False)

add_ch("DE_SWR_FERNSEHEN", "SWR Fernsehen", "SWR", "DE", "Deutschland", "DE-BW", "de",
       "Regional", "Regional", 12, 81.0, 2.3, 2024, "AGF Videoforschung 2024",
       ["SWR Fernsehen HD", "SWR HD", "SWR BW HD", "SWR RP HD", "DE: SWR Fernsehen"],
       ["swr", "suedwestrundfunk", "baden-wuerttemberg", "rheinland-pfalz"], ["swr.de"], ["swr fernsehen*"],
       ["(?i)\\bswr(\\s*fernsehen)?\\b"], [], False, True, True, False)

add_ch("DE_WDR_FERNSEHEN", "WDR Fernsehen", "WDR", "DE", "Deutschland", "DE-NW", "de",
       "Regional", "Regional", 7, 86.0, 2.8, 2024, "AGF Videoforschung 2024",
       ["WDR Fernsehen HD", "WDR HD", "WDR Köln HD", "WDR Dortmund HD", "WDR Düsseldorf HD", "DE: WDR"],
       ["wdr", "westdeutscher rundfunk", "koeln", "nrw"], ["wdr.de"], ["wdr fernsehen*"],
       ["(?i)\\bwdr(\\s*fernsehen)?\\b"], [], False, True, True, False)

# Private DE
add_ch("DE_RTL", "RTL", "RTL", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Vollprogramm", 3, 95.0, 8.0, 2024, "AGF Videoforschung 2024",
       ["RTL Television", "RTL HD", "RTL FHD", "RTL UHD", "DE: RTL HD", "DE| RTL HD"],
       ["rtl", "rtl television"], ["RTL.de"], ["rtl*"],
       ["(?i)\\brtl(\\s*television)?\\b"], ["(?i)\\b(austria|schweiz|ch|at|rtl\\s*2|rtl\\s*zwei|nitro|up)\\b"], True, False, False, True)

add_ch("DE_RTL_ZWEI", "RTL ZWEI", "RTL2", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 13, 80.5, 2.2, 2024, "AGF Videoforschung 2024",
       ["RTL 2", "RTL2", "RTL ZWEI HD", "RTL 2 HD", "DE: RTL ZWEI HD"],
       ["rtl2", "rtl zwei"], ["RTL2.de"], ["rtl 2*", "rtl zwei*"],
       ["(?i)\\brtl\\s*(zwei|2)\\b"], ["(?i)\\b(austria|schweiz|ch|at)\\b"], True, False, False, True)

add_ch("DE_RTLUP", "RTLup", "RTLup", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 18, 74.0, 1.6, 2024, "AGF Videoforschung 2024",
       ["RTL Plus", "RTL up", "RTLup HD", "DE: RTLup"],
       ["rtlup", "rtl plus"], ["RTLup.de"], ["rtlup*"],
       ["(?i)\\brtl\\s*(up|plus)\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_NITRO", "NITRO", "NITRO", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 15, 78.0, 2.0, 2024, "AGF Videoforschung 2024",
       ["RTL NITRO", "NITRO HD", "DE: NITRO HD", "DE| NITRO HD"],
       ["nitro", "rtl nitro"], ["nitro.de"], ["nitro*"],
       ["(?i)\\b(rtl\\s*)?nitro\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_SUPER_RTL", "SUPER RTL", "SUPER RTL", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Kids", 17, 75.0, 1.5, 2024, "AGF Videoforschung 2024",
       ["Super RTL HD", "Super RTL FHD", "TOGGO", "DE: Super RTL"],
       ["super rtl", "toggo"], ["superrtl.de"], ["super rtl*"],
       ["(?i)\\bsuper\\s*rtl\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_VOX", "VOX", "VOX", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Vollprogramm", 5, 90.0, 4.6, 2024, "AGF Videoforschung 2024",
       ["VOX HD", "VOX FHD", "VOX UHD", "DE: VOX HD", "DE| VOX HD"],
       ["vox"], ["vox.de"], ["vox*"],
       ["(?i)\\bvox\\b"], ["(?i)\\b(austria|schweiz|ch|at|voxup|vox\\s*up)\\b"], True, False, False, True)

add_ch("DE_VOXUP", "VOXup", "VOXup", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 34, 59.0, 0.6, 2024, "AGF Videoforschung 2024",
       ["VOX up", "VOXup HD", "DE: VOXup"],
       ["voxup", "vox up"], ["voxup.de"], ["voxup*"],
       ["(?i)\\bvox\\s*up\\b"], [], True, False, False, True)

add_ch("DE_SAT1", "SAT.1", "SAT.1", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Vollprogramm", 4, 91.0, 4.7, 2024, "AGF Videoforschung 2024",
       ["SAT 1", "SAT.1 HD", "SAT.1 FHD", "SAT 1 HD", "DE: SAT.1 HD", "DE| SAT.1 HD"],
       ["sat.1", "sat 1"], ["sat1.de"], ["sat.1*", "sat 1*"],
       ["(?i)\\bsat[\\. ]*1\\b"], ["(?i)\\b(austria|oesterreich|schweiz|gold|bayern|nrw)\\b"], True, False, False, True)

add_ch("DE_SAT1_GOLD", "SAT.1 Gold", "SAT.1 Gold", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 17, 75.0, 1.6, 2024, "AGF Videoforschung 2024",
       ["SAT 1 Gold", "SAT.1 Gold HD", "DE: SAT.1 Gold"],
       ["sat.1 gold", "sat 1 gold"], ["sat1gold.de"], ["sat.1 gold*"],
       ["(?i)\\bsat[\\. ]*1\\s*gold\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_PROSIEBEN", "ProSieben", "Pro7", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Vollprogramm", 6, 88.0, 3.2, 2024, "AGF Videoforschung 2024",
       ["Pro 7", "Pro7", "ProSieben HD", "ProSieben FHD", "Pro 7 HD", "DE: ProSieben HD"],
       ["prosieben", "pro 7", "pro7"], ["prosieben.de"], ["prosieben*", "pro 7*"],
       ["(?i)\\bpro\\s*(sieben|7)\\b"], ["(?i)\\b(austria|schweiz|maxx)\\b"], True, False, False, True)

add_ch("DE_PROSIEBEN_MAXX", "ProSieben MAXX", "Pro7 MAXX", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 26, 66.0, 1.0, 2024, "AGF Videoforschung 2024",
       ["Pro 7 MAXX", "ProSieben MAXX HD", "DE: ProSieben MAXX"],
       ["prosieben maxx", "pro7 maxx"], ["prosiebenmaxx.de"], ["prosieben maxx*"],
       ["(?i)\\bpro\\s*(sieben|7)\\s*maxx\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_KABEL_EINS", "Kabel Eins", "Kabel 1", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 9, 84.0, 2.6, 2024, "AGF Videoforschung 2024",
       ["Kabel 1", "Kabel1", "Kabel Eins HD", "Kabel 1 HD", "DE: Kabel Eins HD"],
       ["kabel eins", "kabel 1"], ["kabeleins.de"], ["kabel eins*", "kabel 1*"],
       ["(?i)\\bkabel\\s*(eins|1)\\b"], ["(?i)\\b(austria|schweiz|doku)\\b"], True, False, False, True)

add_ch("DE_KABEL_EINS_DOKU", "Kabel Eins Doku", "K1 Doku", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Documentary", 25, 67.0, 1.1, 2024, "AGF Videoforschung 2024",
       ["Kabel 1 Doku", "Kabel Eins Doku HD", "DE: Kabel Eins Doku"],
       ["kabel eins doku", "kabel 1 doku"], ["kabeleinsdoku.de"], ["kabel eins doku*"],
       ["(?i)\\bkabel\\s*(eins|1)\\s*doku\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_SIXX", "sixx", "sixx", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 24, 68.0, 1.1, 2024, "AGF Videoforschung 2024",
       ["sixx HD", "sixx FHD", "DE: sixx HD", "DE| sixx HD"],
       ["sixx"], ["sixx.de"], ["sixx*"],
       ["(?i)\\bsixx\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_TELE5", "Tele 5", "Tele 5", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Entertainment", 33, 60.0, 0.6, 2024, "AGF Videoforschung 2024",
       ["Tele 5 HD", "DE: Tele 5"],
       ["tele 5", "tele5"], ["tele5.de"], ["tele 5*"],
       ["(?i)\\btele\\s*5\\b"], [], True, False, False, True)

add_ch("DE_WELT", "WELT", "WELT", "DE", "Deutschland", "DE-NAT", "de",
       "News", "Nachrichten", 21, 71.0, 1.3, 2024, "AGF Videoforschung 2024",
       ["WELT HD", "N24", "WELT Nachrichtensender", "DE: WELT HD"],
       ["welt", "n24", "nachrichten"], ["welt.de"], ["welt*"],
       ["(?i)\\b(welt(\\s*hd)?|n24)\\b"], ["(?i)\\b(wetter|kino|reise)\\b"], True, False, False, True)

add_ch("DE_NTV", "ntv", "ntv", "DE", "Deutschland", "DE-NAT", "de",
       "News", "Nachrichten", 22, 70.5, 1.2, 2024, "AGF Videoforschung 2024",
       ["n-tv", "ntv HD", "n-tv HD", "DE: ntv HD"],
       ["ntv", "n-tv", "nachrichten"], ["ntv.de"], ["ntv*", "n-tv*"],
       ["(?i)\\bn[- ]?tv\\b"], [], True, False, False, True)

add_ch("DE_DMAX", "DMAX", "DMAX", "DE", "Deutschland", "DE-NAT", "de",
       "Documentary", "Dokumentation", 18, 74.5, 1.5, 2024, "AGF Videoforschung 2024",
       ["DMAX HD", "DMAX FHD", "DE: DMAX HD"],
       ["dmax"], ["dmax.de"], ["dmax*"],
       ["(?i)\\bdmax\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_TLC", "TLC", "TLC", "DE", "Deutschland", "DE-NAT", "de",
       "Documentary", "Lifestyle", 32, 60.5, 0.6, 2024, "AGF Videoforschung 2024",
       ["TLC HD", "DE: TLC HD"],
       ["tlc"], ["tlc.de"], ["tlc*"],
       ["(?i)\\btlc\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_SPORT1", "SPORT1", "SPORT1", "DE", "Deutschland", "DE-NAT", "de",
       "Sport", "Sport", 31, 61.5, 0.7, 2024, "AGF Videoforschung 2024",
       ["Sport 1", "SPORT1 HD", "Sport 1 HD", "DE: SPORT1"],
       ["sport1", "sport 1", "dsf"], ["sport1.de"], ["sport1*"],
       ["(?i)\\bsport\\s*1\\b"], ["(?i)\\b(plus|extra)\\b"], True, False, False, True)

add_ch("DE_EUROSPORT_1", "Eurosport 1", "Eurosport 1", "DE", "Deutschland", "DE-NAT", "de",
       "Sport", "Sport", 32, 60.8, 0.6, 2024, "AGF Videoforschung 2024",
       ["Eurosport", "Eurosport 1 HD", "Eurosport 1 Deutschland", "DE: Eurosport 1"],
       ["eurosport 1", "eurosport"], ["eurosport1.de"], ["eurosport 1*"],
       ["(?i)\\beurosport(\\s*1)?\\b"], ["(?i)\\b(2|player|uk|france)\\b"], True, False, False, True)

add_ch("DE_SKY_SPORT_NEWS", "Sky Sport News", "Sky Sport News", "DE", "Deutschland", "DE-NAT", "de",
       "Sport", "Sportnachrichten", 39, 50.0, 0.3, 2024, "AGF Videoforschung 2024",
       ["Sky Sport News HD", "DE: Sky Sport News HD"],
       ["sky sport news", "ssnhd"], ["skysportnews.de"], ["sky sport news*"],
       ["(?i)\\bsky\\s*sport\\s*news\\b"], [], True, False, False, True)

add_ch("DE_DISNEY_CHANNEL", "Disney Channel", "Disney", "DE", "Deutschland", "DE-NAT", "de",
       "Kids", "Kinder", 26, 66.5, 1.0, 2024, "AGF Videoforschung 2024",
       ["Disney Channel HD", "DE: Disney Channel HD"],
       ["disney channel"], ["disneychannel.de"], ["disney channel*"],
       ["(?i)\\bdisney\\s*channel\\b"], [], True, False, False, True)

add_ch("DE_COMEDY_CENTRAL", "Comedy Central", "Comedy Central", "DE", "Deutschland", "DE-NAT", "de",
       "Entertainment", "Comedy", 35, 57.0, 0.5, 2024, "AGF Videoforschung 2024",
       ["Comedy Central HD", "DE: Comedy Central"],
       ["comedy central"], ["comedycentral.de"], ["comedy central*"],
       ["(?i)\\bcomedy\\s*central\\b"], ["(?i)\\b(austria|schweiz)\\b"], True, False, False, True)

add_ch("DE_MTV", "MTV", "MTV", "DE", "Deutschland", "DE-NAT", "de",
       "Entertainment", "Musik", 40, 48.0, 0.2, 2024, "AGF Videoforschung 2024",
       ["MTV HD", "MTV Germany", "DE: MTV HD"],
       ["mtv"], ["mtv.de"], ["mtv*"],
       ["(?i)\\bmtv(\\s*germany)?\\b"], ["(?i)\\b(live|hits|80s|90s|00s)\\b"], True, False, False, True)

add_ch("DE_SERVUSTV_DE", "ServusTV Deutschland", "ServusTV DE", "DE", "Deutschland", "DE-NAT", "de",
       "Private", "Dokumentation", 41, 45.0, 0.2, 2024, "AGF Videoforschung 2024",
       ["Servus TV DE", "ServusTV DE HD", "DE: ServusTV"],
       ["servustv deutschland", "servustv de"], ["servustv.de"], ["servustv de*"],
       ["(?i)\\bservus\\s*tv\\s*(de|deutschland)\\b"], ["(?i)\\b(oesterreich|austria|at)\\b"], True, False, False, True)


# ==========================================
# 2. ÖSTERREICH (AT)
# ==========================================
# ORF öffentlich-rechtlich
add_ch("AT_ORF_1", "ORF 1", "ORF 1", "AT", "Österreich", "AT-NAT", "de",
       "ORF", "Vollprogramm", 2, 97.0, 10.8, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF1", "ORF 1 HD", "ORF1 HD", "ORF 1 FHD", "AT: ORF 1 HD", "AT| ORF 1 HD", "AT: ORF1"],
       ["orf1", "orf 1", "orf eins"], ["ORF1.at"], ["orf 1*", "orf1*"],
       ["(?i)\\borf\\s*(1|eins)\\b"], ["(?i)\\b(orf\\s*2|orf\\s*3|orf\\s*iii|sport)\\b"], True, False, True, False)

add_ch("AT_ORF_2", "ORF 2", "ORF 2", "AT", "Österreich", "AT-NAT", "de",
       "ORF", "Vollprogramm", 1, 100.0, 20.8, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF2", "ORF 2 HD", "ORF2 HD", "ORF 2 Europe", "AT: ORF 2 HD", "AT| ORF 2 HD"],
       ["orf2", "orf 2"], ["ORF2.at"], ["orf 2*", "orf2*"],
       ["(?i)\\borf\\s*2\\b"], ["(?i)\\b(burgenland|kaernten|niederoesterreich|oberoesterreich|salzburg|steiermark|tirol|vorarlberg|wien)\\b"], True, False, True, False)

add_ch("AT_ORF_2_BURGENLAND", "ORF 2 Burgenland", "ORF 2 B", "AT", "Österreich", "AT-BG", "de",
       "ORF", "Regional", 12, 60.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 B HD", "ORF 2 Burgenland HD", "AT: ORF 2 Burgenland"],
       ["orf 2 burgenland"], ["ORF2B.at"], ["orf 2 burgenland*"],
       ["(?i)\\borf\\s*2\\s*burgenland\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_KAERNTEN", "ORF 2 Kärnten", "ORF 2 K", "AT", "Österreich", "AT-KA", "de",
       "ORF", "Regional", 13, 60.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 K HD", "ORF 2 Kärnten HD", "ORF 2 Kaernten", "AT: ORF 2 Kärnten"],
       ["orf 2 kaernten", "orf 2 kärnten"], ["ORF2K.at"], ["orf 2 k*"],
       ["(?i)\\borf\\s*2\\s*k[aä]rnten\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_NIEDEROESTERREICH", "ORF 2 Niederösterreich", "ORF 2 NÖ", "AT", "Österreich", "AT-NO", "de",
       "ORF", "Regional", 14, 62.0, 0.5, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 NÖ HD", "ORF 2 NÖ", "ORF 2 Niederösterreich HD", "AT: ORF 2 NÖ"],
       ["orf 2 niederoesterreich", "orf 2 nö"], ["ORF2N.at"], ["orf 2 n*"],
       ["(?i)\\borf\\s*2\\s*(nieder[oö]sterreich|n[oö])\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_OBEROESTERREICH", "ORF 2 Oberösterreich", "ORF 2 OÖ", "AT", "Österreich", "AT-OO", "de",
       "ORF", "Regional", 15, 62.0, 0.5, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 OÖ HD", "ORF 2 OÖ", "ORF 2 Oberösterreich HD", "AT: ORF 2 OÖ"],
       ["orf 2 oberoesterreich", "orf 2 oö"], ["ORF2O.at"], ["orf 2 o*"],
       ["(?i)\\borf\\s*2\\s*(ober[oö]sterreich|o[oö])\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_SALZBURG", "ORF 2 Salzburg", "ORF 2 S", "AT", "Österreich", "AT-SB", "de",
       "ORF", "Regional", 16, 60.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 S HD", "ORF 2 Salzburg HD", "AT: ORF 2 Salzburg"],
       ["orf 2 salzburg"], ["ORF2S.at"], ["orf 2 salzburg*"],
       ["(?i)\\borf\\s*2\\s*salzburg\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_STEIERMARK", "ORF 2 Steiermark", "ORF 2 St", "AT", "Österreich", "AT-ST", "de",
       "ORF", "Regional", 17, 61.0, 0.5, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 St HD", "ORF 2 Steiermark HD", "AT: ORF 2 Steiermark"],
       ["orf 2 steiermark"], ["ORF2ST.at"], ["orf 2 steiermark*"],
       ["(?i)\\borf\\s*2\\s*steiermark\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_TIROL", "ORF 2 Tirol", "ORF 2 T", "AT", "Österreich", "AT-TI", "de",
       "ORF", "Regional", 18, 60.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 T HD", "ORF 2 Tirol HD", "AT: ORF 2 Tirol"],
       ["orf 2 tirol"], ["ORF2T.at"], ["orf 2 tirol*"],
       ["(?i)\\borf\\s*2\\s*tirol\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_VORARLBERG", "ORF 2 Vorarlberg", "ORF 2 V", "AT", "Österreich", "AT-VB", "de",
       "ORF", "Regional", 19, 59.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 V HD", "ORF 2 Vorarlberg HD", "AT: ORF 2 Vorarlberg"],
       ["orf 2 vorarlberg"], ["ORF2V.at"], ["orf 2 vorarlberg*"],
       ["(?i)\\borf\\s*2\\s*vorarlberg\\b"], [], False, True, True, False)

add_ch("AT_ORF_2_WIEN", "ORF 2 Wien", "ORF 2 W", "AT", "Österreich", "AT-WI", "de",
       "ORF", "Regional", 11, 65.0, 0.6, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 2 W HD", "ORF 2 Wien HD", "AT: ORF 2 Wien"],
       ["orf 2 wien"], ["ORF2W.at"], ["orf 2 wien*"],
       ["(?i)\\borf\\s*2\\s*wien\\b"], [], False, True, True, False)

add_ch("AT_ORF_III", "ORF III", "ORF 3", "AT", "Österreich", "AT-NAT", "de",
       "ORF", "Kultur", 6, 82.0, 2.5, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF 3", "ORF3", "ORF III HD", "ORF 3 HD", "AT: ORF III HD", "AT: ORF 3"],
       ["orf iii", "orf 3", "orf drei", "kultur und information"], ["ORF3.at"], ["orf 3*", "orf iii*"],
       ["(?i)\\borf\\s*(3|iii)\\b"], [], True, False, True, False)

add_ch("AT_ORF_SPORT_PLUS", "ORF SPORT+", "ORF Sport+", "AT", "Österreich", "AT-NAT", "de",
       "ORF", "Sport", 10, 70.0, 0.6, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ORF Sport Plus", "ORF Sport + HD", "ORF Sport Plus HD", "AT: ORF SPORT+ HD"],
       ["orf sport+", "orf sport plus"], ["ORFSport.at"], ["orf sport*"],
       ["(?i)\\borf\\s*sport\\s*(\\+|plus)?\\b"], [], True, False, True, False)

# Private Österreich
add_ch("AT_SERVUSTV", "ServusTV", "ServusTV", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 3, 90.0, 4.7, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ServusTV HD", "ServusTV Österreich", "Servus TV Österreich HD", "AT: ServusTV HD", "AT| ServusTV HD"],
       ["servustv", "servus tv"], ["ServusTV.at"], ["servustv*"],
       ["(?i)\\bservus\\s*tv(\\s*[oö]sterreich)?\\b"], ["(?i)\\b(de|deutschland)\\b"], True, False, False, True)

add_ch("AT_PULS_4", "PULS 4", "PULS 4", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 4, 86.0, 2.7, 2025, "Teletest / ORF Jahresbericht 2025",
       ["PULS4", "PULS 4 HD", "PULS4 HD", "AT: PULS 4 HD", "AT| PULS 4 HD"],
       ["puls 4", "puls4"], ["Puls4.at"], ["puls 4*", "puls4*"],
       ["(?i)\\bpuls\\s*4\\b"], ["(?i)\\b(24|8)\\b"], True, False, False, True)

add_ch("AT_ATV", "ATV", "ATV", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 5, 85.0, 2.6, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ATV HD", "ATV Österreich", "AT: ATV HD", "AT| ATV HD"],
       ["atv"], ["ATV.at"], ["atv*"],
       ["(?i)\\batv\\b"], ["(?i)\\b(2|ii|smart)\\b"], True, False, False, True)

add_ch("AT_ATV2", "ATV II", "ATV 2", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Entertainment", 9, 74.0, 1.0, 2025, "Teletest / ORF Jahresbericht 2025",
       ["ATV2", "ATV 2", "ATV II HD", "ATV 2 HD", "AT: ATV2 HD"],
       ["atv 2", "atv2", "atv ii"], ["ATV2.at"], ["atv 2*", "atv2*", "atv ii*"],
       ["(?i)\\batv\\s*(2|ii)\\b"], [], True, False, False, True)

add_ch("AT_PULS_24", "PULS 24", "PULS 24", "AT", "Österreich", "AT-NAT", "de",
       "Private", "News", 8, 76.0, 1.1, 2025, "Teletest / ORF Jahresbericht 2025",
       ["PULS24", "PULS 24 HD", "PULS24 HD", "AT: PULS 24 HD"],
       ["puls 24", "puls24", "nachrichten"], ["Puls24.at"], ["puls 24*", "puls24*"],
       ["(?i)\\bpuls\\s*24\\b"], ["(?i)\\b(4|8)\\b"], True, False, False, True)

add_ch("AT_OE24_TV", "oe24.TV", "oe24", "AT", "Österreich", "AT-NAT", "de",
       "Private", "News", 7, 78.0, 1.5, 2025, "Teletest / ORF Jahresbericht 2025",
       ["oe24", "oe24 TV", "oe24.TV HD", "AT: oe24.TV HD"],
       ["oe24", "oe24.tv"], ["oe24.at"], ["oe24*"],
       ["(?i)\\boe24([\\. ]*tv)?\\b"], [], True, False, False, True)

add_ch("AT_KURIER_TV", "KURIER TV", "KURIER TV", "AT", "Österreich", "AT-NAT", "de",
       "Private", "News", 20, 58.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["Kurier TV HD", "AT: KURIER TV"],
       ["kurier tv", "schau tv kurier"], ["kuriertv.at"], ["kurier tv*"],
       ["(?i)\\bkurier\\s*tv\\b"], [], True, False, False, True)

add_ch("AT_KRONE_TV", "Krone TV", "krone.tv", "AT", "Österreich", "AT-NAT", "de",
       "Private", "News", 21, 57.0, 0.4, 2025, "Teletest / ORF Jahresbericht 2025",
       ["krone.tv", "Krone TV HD", "AT: Krone TV"],
       ["krone tv", "krone.tv"], ["kronetv.at"], ["krone tv*", "krone.tv*"],
       ["(?i)\\bkrone([\\. ]*tv)?\\b"], [], True, False, False, True)

add_ch("AT_W24", "W24", "W24", "AT", "Österreich", "AT-WI", "de",
       "Regional", "Regional", 22, 55.0, 0.3, 2025, "Teletest / ORF Jahresbericht 2025",
       ["W24 HD", "W24 Wien", "AT: W24 HD"],
       ["w24", "wien fernsehen"], ["w24.at"], ["w24*"],
       ["(?i)\\bw24\\b"], [], False, True, False, True)

add_ch("AT_LAOLA1", "LAOLA1", "LAOLA1", "AT", "Österreich", "AT-NAT", "de",
       "Sport", "Sport", 23, 55.0, 0.3, 2025, "Teletest / ORF Jahresbericht 2025",
       ["LAOLA1.TV", "LAOLA1 HD", "AT: LAOLA1 HD"],
       ["laola1", "laola1.tv"], ["laola1.at"], ["laola1*"],
       ["(?i)\\blaola\\s*1([\\. ]*tv)?\\b"], [], True, False, False, True)

add_ch("AT_RED_BULL_TV", "Red Bull TV", "Red Bull TV", "AT", "Österreich", "AT-NAT", "de",
       "Sport", "Extreme Sport", 24, 54.0, 0.3, 2025, "Teletest / ORF Jahresbericht 2025",
       ["Red Bull TV HD", "AT: Red Bull TV"],
       ["red bull tv"], ["redbulltv.at"], ["red bull tv*"],
       ["(?i)\\bred\\s*bull\\s*tv\\b"], [], True, False, False, True)

add_ch("AT_STEIERMARK_TV", "Steiermark TV", "Steiermark TV", "AT", "Österreich", "AT-ST", "de",
       "Regional", "Regional", 25, 50.0, 0.2, 2025, "Teletest / ORF Jahresbericht 2025",
       ["Kanal 3 Steiermark", "Steiermark TV HD", "AT: Steiermark TV"],
       ["steiermark tv", "kanal 3"], ["steiermarktv.at"], ["steiermark tv*"],
       ["(?i)\\bsteiermark\\s*tv\\b"], [], False, True, False, True)

add_ch("AT_TIROL_TV", "Tirol TV", "Tirol TV", "AT", "Österreich", "AT-TI", "de",
       "Regional", "Regional", 26, 50.0, 0.2, 2025, "Teletest / ORF Jahresbericht 2025",
       ["Tirol TV HD", "AT: Tirol TV"],
       ["tirol tv"], ["tiroltv.at"], ["tirol tv*"],
       ["(?i)\\btirol\\s*tv\\b"], [], False, True, False, True)

add_ch("AT_LT1", "LT1", "LT1", "AT", "Österreich", "AT-OO", "de",
       "Regional", "Regional", 27, 49.0, 0.2, 2025, "Teletest / ORF Jahresbericht 2025",
       ["LT1 OÖ", "LT1 HD", "AT: LT1 HD"],
       ["lt1", "lt1 oberoesterreich"], ["lt1.at"], ["lt1*"],
       ["(?i)\\blt1\\b"], [], False, True, False, True)

# Österreichische Werbefenster (Sehr verbreitet in IPTV)
add_ch("AT_RTL_AUSTRIA", "RTL Austria", "RTL AT", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 30, 84.0, 4.5, 2025, "Teletest 2025",
       ["RTL Österreich", "RTL AT HD", "AT: RTL HD"],
       ["rtl austria", "rtl oesterreich"], ["rtl.at"], ["rtl austria*"],
       ["(?i)\\brtl\\s*(austria|[oö]sterreich|at)\\b"], [], True, False, False, True)

add_ch("AT_SAT1_OESTERREICH", "SAT.1 Österreich", "SAT.1 AT", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 31, 80.0, 3.2, 2025, "Teletest 2025",
       ["SAT 1 Österreich", "SAT.1 Austria", "SAT.1 AT HD", "AT: SAT.1 HD"],
       ["sat.1 oesterreich", "sat 1 austria"], ["sat1.at"], ["sat.1 austria*"],
       ["(?i)\\bsat[\\. ]*1\\s*(austria|[oö]sterreich|at)\\b"], [], True, False, False, True)

add_ch("AT_PROSIEBEN_AUSTRIA", "ProSieben Austria", "Pro7 AT", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 32, 78.0, 2.5, 2025, "Teletest 2025",
       ["Pro 7 Austria", "Pro7 AT", "ProSieben AT HD", "AT: ProSieben HD"],
       ["prosieben austria", "pro7 at"], ["prosieben.at"], ["prosieben austria*"],
       ["(?i)\\bpro\\s*(sieben|7)\\s*(austria|[oö]sterreich|at)\\b"], [], True, False, False, True)

add_ch("AT_VOX_AUSTRIA", "VOX Austria", "VOX AT", "AT", "Österreich", "AT-NAT", "de",
       "Private", "Vollprogramm", 33, 77.0, 2.4, 2025, "Teletest 2025",
       ["VOX Österreich", "VOX AT HD", "AT: VOX HD"],
       ["vox austria", "vox oesterreich"], ["vox.at"], ["vox austria*"],
       ["(?i)\\bvox\\s*(austria|[oö]sterreich|at)\\b"], [], True, False, False, True)


# ==========================================
# 3. SCHWEIZ (CH)
# ==========================================
# Deutschschweiz (DE-CH) - SRG SSR
add_ch("CH_SRF_1", "SRF 1", "SRF 1", "CH", "Schweiz", "DE-CH", "de",
       "SRF", "Vollprogramm", 1, 100.0, 19.2, 2024, "Mediapulse TV Data 2024",
       ["SRF1", "SRF 1 HD", "SRF1 HD", "CH: SRF 1 HD", "CH| SRF 1 HD"],
       ["srf 1", "srf1", "sf 1", "schweizer fernsehen"], ["SRF1.ch"], ["srf 1*", "srf1*"],
       ["(?i)\\b(srf|sf)\\s*1\\b"], ["(?i)\\b(zwei|2|info)\\b"], True, False, True, False)

add_ch("CH_SRF_ZWEI", "SRF zwei", "SRF 2", "CH", "Schweiz", "DE-CH", "de",
       "SRF", "Vollprogramm", 2, 94.0, 9.1, 2024, "Mediapulse TV Data 2024",
       ["SRF 2", "SRF2", "SRF zwei HD", "SRF 2 HD", "CH: SRF zwei HD", "CH| SRF 2 HD"],
       ["srf zwei", "srf 2", "srf2", "sf zwei", "sf2"], ["SRFzwei.ch"], ["srf 2*", "srf zwei*"],
       ["(?i)\\b(srf|sf)\\s*(2|zwei)\\b"], [], True, False, True, False)

add_ch("CH_SRF_INFO", "SRF info", "SRF info", "CH", "Schweiz", "DE-CH", "de",
       "SRF", "News", 5, 75.0, 1.2, 2024, "Mediapulse TV Data 2024",
       ["SRFinfo", "SRF info HD", "CH: SRF info HD"],
       ["srf info", "srfinfo", "sf info"], ["SRFinfo.ch"], ["srf info*"],
       ["(?i)\\b(srf|sf)\\s*info\\b"], [], True, False, True, False)

# Deutschschweiz Private
add_ch("CH_3_PLUS", "3+", "3+", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 3, 85.0, 2.5, 2024, "Mediapulse TV Data 2024",
       ["3plus", "3 Plus", "3+ HD", "CH: 3+ HD", "CH| 3+ HD"],
       ["3+", "3plus", "3 plus"], ["3plus.ch"], ["3+*"],
       ["(?i)\\b(3\\+|3\\s*plus)\\b"], ["(?i)\\b3sat\\b"], True, False, False, True)

add_ch("CH_4_PLUS", "4+", "4+", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 6, 73.0, 1.0, 2024, "Mediapulse TV Data 2024",
       ["4plus", "4 Plus", "4+ HD", "CH: 4+ HD"],
       ["4+", "4plus", "4 plus"], ["4plus.ch"], ["4+*"],
       ["(?i)\\b(4\\+|4\\s*plus)\\b"], [], True, False, False, True)

add_ch("CH_5_PLUS", "5+", "5+", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 8, 68.0, 0.6, 2024, "Mediapulse TV Data 2024",
       ["5plus", "5 Plus", "5+ HD", "CH: 5+ HD"],
       ["5+", "5plus", "5 plus"], ["5plus.ch"], ["5+*"],
       ["(?i)\\b(5\\+|5\\s*plus)\\b"], [], True, False, False, True)

add_ch("CH_6_PLUS", "6+", "6+", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 14, 60.0, 0.3, 2024, "Mediapulse TV Data 2024",
       ["6plus", "6 Plus", "6+ HD", "CH: 6+ HD"],
       ["6+", "6plus", "6 plus"], ["6plus.ch"], ["6+*"],
       ["(?i)\\b(6\\+|6\\s*plus)\\b"], [], True, False, False, True)

add_ch("CH_7_PLUS", "7+", "7+", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 18, 55.0, 0.2, 2024, "Mediapulse TV Data 2024",
       ["7plus", "7 Plus", "7+ HD", "CH: 7+ HD"],
       ["7+", "7plus", "7 plus"], ["7plus.ch"], ["7+*"],
       ["(?i)\\b(7\\+|7\\s*plus)\\b"], [], True, False, False, True)

add_ch("CH_PULS_8", "Puls 8", "Puls 8", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 7, 72.0, 0.9, 2024, "Mediapulse TV Data 2024",
       ["Puls8", "Puls 8 HD", "CH: Puls 8 HD"],
       ["puls 8", "puls8"], ["puls8.ch"], ["puls 8*"],
       ["(?i)\\bpuls\\s*8\\b"], ["(?i)\\b(4|24)\\b"], True, False, False, True)

add_ch("CH_TV24", "TV24", "TV24", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 4, 78.0, 1.4, 2024, "Mediapulse TV Data 2024",
       ["TV 24", "TV24 HD", "CH: TV24 HD"],
       ["tv24", "tv 24"], ["tv24.ch"], ["tv24*"],
       ["(?i)\\btv\\s*24\\b"], [], True, False, False, True)

add_ch("CH_TV25", "TV25", "TV25", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 9, 67.0, 0.6, 2024, "Mediapulse TV Data 2024",
       ["TV 25", "TV25 HD", "CH: TV25 HD"],
       ["tv25", "tv 25"], ["tv25.ch"], ["tv25*"],
       ["(?i)\\btv\\s*25\\b"], [], True, False, False, True)

add_ch("CH_STAR_TV", "Star TV", "Star TV", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 15, 58.0, 0.2, 2024, "Mediapulse TV Data 2024",
       ["Star TV HD", "CH: Star TV"],
       ["star tv", "kino und film"], ["startv.ch"], ["star tv*"],
       ["(?i)\\bstar\\s*tv\\b"], [], True, False, False, True)

add_ch("CH_S1", "S1", "S1", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Entertainment", 16, 57.0, 0.2, 2024, "Mediapulse TV Data 2024",
       ["S1 TV", "Schweiz 1", "CH: S1 HD"],
       ["s1", "schweiz 1"], ["s1.ch"], ["s1*"],
       ["(?i)\\b(s1(\\s*tv)?|schweiz\\s*1)\\b"], ["(?i)\\bsat\\b"], True, False, False, True)

# Deutschschweiz Regional
add_ch("CH_TELEZUERI", "TeleZüri", "TeleZüri", "CH", "Schweiz", "DE-CH", "de",
       "Regional", "Regional", 10, 66.0, 0.6, 2024, "Mediapulse TV Data 2024",
       ["Tele Zueri", "Tele Züri HD", "CH: TeleZüri HD"],
       ["telezueri", "telezüri", "zuerich"], ["telezueri.ch"], ["telezueri*", "telezüri*"],
       ["(?i)\\btele\\s*z[uü]ri\\b"], [], False, True, False, True)

add_ch("CH_TELEBAERN", "TeleBärn", "TeleBärn", "CH", "Schweiz", "DE-CH", "de",
       "Regional", "Regional", 11, 63.0, 0.4, 2024, "Mediapulse TV Data 2024",
       ["Tele Baern", "TeleBärn HD", "CH: TeleBärn HD"],
       ["telebaern", "telebärn", "bern"], ["telebaern.ch"], ["telebaern*"],
       ["(?i)\\btele\\s*b[aä]rn\\b"], [], False, True, False, True)

add_ch("CH_TELE_M1", "Tele M1", "Tele M1", "CH", "Schweiz", "DE-CH", "de",
       "Regional", "Regional", 12, 63.0, 0.4, 2024, "Mediapulse TV Data 2024",
       ["Tele M1 HD", "CH: Tele M1"],
       ["tele m1", "mittelland", "aarau"], ["telem1.ch"], ["tele m1*"],
       ["(?i)\\btele\\s*m1\\b"], [], False, True, False, True)

add_ch("CH_TELEBASEL", "Telebasel", "Telebasel", "CH", "Schweiz", "DE-CH", "de",
       "Regional", "Regional", 13, 62.0, 0.3, 2024, "Mediapulse TV Data 2024",
       ["Tele Basel", "Telebasel HD", "CH: Telebasel"],
       ["telebasel", "tele basel"], ["telebasel.ch"], ["telebasel*"],
       ["(?i)\\btele\\s*basel\\b"], [], False, True, False, True)

add_ch("CH_TELE_TOP", "Tele Top", "Tele Top", "CH", "Schweiz", "DE-CH", "de",
       "Regional", "Regional", 17, 56.0, 0.2, 2024, "Mediapulse TV Data 2024",
       ["Tele Top HD", "CH: Tele Top"],
       ["tele top", "winterthur", "thurgau"], ["teletop.ch"], ["tele top*"],
       ["(?i)\\btele\\s*top\\b"], [], False, True, False, True)

# Romandie Französische Schweiz (FR-CH) - RTS
add_ch("CH_RTS_1", "RTS 1", "RTS 1", "CH", "Schweiz", "FR-CH", "fr",
       "RTS", "Vollprogramm", 20, 95.0, 21.5, 2024, "Mediapulse Romandie 2024",
       ["RTS Un", "RTS 1 HD", "RTS Un HD", "CH: RTS 1 HD", "CH| RTS 1 HD"],
       ["rts 1", "rts un", "romandie"], ["RTS1.ch"], ["rts 1*", "rts un*"],
       ["(?i)\\brts\\s*(1|un)\\b"], ["(?i)\\b(deux|2|info)\\b"], True, False, True, False)

add_ch("CH_RTS_2", "RTS 2", "RTS 2", "CH", "Schweiz", "FR-CH", "fr",
       "RTS", "Vollprogramm", 21, 88.0, 7.8, 2024, "Mediapulse Romandie 2024",
       ["RTS Deux", "RTS 2 HD", "RTS Deux HD", "CH: RTS 2 HD"],
       ["rts 2", "rts deux"], ["RTS2.ch"], ["rts 2*", "rts deux*"],
       ["(?i)\\brts\\s*(2|deux)\\b"], [], True, False, True, False)

add_ch("CH_RTS_INFO", "RTS Info", "RTS Info", "CH", "Schweiz", "FR-CH", "fr",
       "RTS", "News", 24, 70.0, 1.0, 2024, "Mediapulse Romandie 2024",
       ["RTS Info HD", "CH: RTS Info"],
       ["rts info", "information romandie"], ["RTSInfo.ch"], ["rts info*"],
       ["(?i)\\brts\\s*info\\b"], [], True, False, True, False)

add_ch("CH_LEMAN_BLEU", "Léman Bleu", "Léman Bleu", "CH", "Schweiz", "FR-CH", "fr",
       "Regional", "Regional", 25, 65.0, 0.7, 2024, "Mediapulse Romandie 2024",
       ["Leman Bleu", "Léman Bleu HD", "CH: Léman Bleu"],
       ["leman bleu", "léman bleu", "geneve"], ["lemanbleu.ch"], ["leman bleu*"],
       ["(?i)\\bl[eé]man\\s*bleu\\b"], [], False, True, False, True)

# Svizzera italiana Italienische Schweiz (IT-CH) - RSI
add_ch("CH_RSI_LA_1", "RSI LA 1", "LA 1", "CH", "Schweiz", "IT-CH", "it",
       "RSI", "Vollprogramm", 30, 95.0, 18.5, 2024, "Mediapulse Svizzera italiana 2024",
       ["RSI LA1", "RSI LA 1 HD", "CH: RSI LA 1 HD", "CH| RSI LA 1 HD"],
       ["rsi la 1", "rsi la1", "tessin", "svizzera italiana"], ["RSILA1.ch"], ["rsi la 1*", "rsi la1*"],
       ["(?i)\\brsi\\s*la\\s*1\\b"], ["(?i)\\bla\\s*2\\b"], True, False, True, False)

add_ch("CH_RSI_LA_2", "RSI LA 2", "LA 2", "CH", "Schweiz", "IT-CH", "it",
       "RSI", "Vollprogramm", 31, 86.0, 6.5, 2024, "Mediapulse Svizzera italiana 2024",
       ["RSI LA2", "RSI LA 2 HD", "CH: RSI LA 2 HD"],
       ["rsi la 2", "rsi la2"], ["RSILA2.ch"], ["rsi la 2*", "rsi la2*"],
       ["(?i)\\brsi\\s*la\\s*2\\b"], [], True, False, True, False)

# Rätoromanische Schweiz (RM-CH) - RTR
add_ch("CH_RTR", "RTR", "RTR", "CH", "Schweiz", "RM-CH", "rm",
       "SRF", "Regional", 40, 50.0, 0.2, 2024, "Mediapulse 2024",
       ["RTR Telesguard", "RTR TV", "CH: RTR"],
       ["rtr", "telesguard", "rumantsch", "grischun"], ["rtr.ch"], ["rtr*"],
       ["(?i)\\brtr(\\s*telesguard)?\\b"], [], False, True, True, False)

# Schweizer Programmfenster
add_ch("CH_RTL_SCHWEIZ", "RTL Schweiz", "RTL CH", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Vollprogramm", 45, 75.0, 1.8, 2024, "Mediapulse 2024",
       ["RTL CH HD", "CH: RTL HD"],
       ["rtl schweiz", "rtl ch"], ["rtl.ch"], ["rtl schweiz*"],
       ["(?i)\\brtl\\s*(schweiz|ch)\\b"], [], True, False, False, True)

add_ch("CH_SAT1_SCHWEIZ", "SAT.1 Schweiz", "SAT.1 CH", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Vollprogramm", 46, 74.0, 1.5, 2024, "Mediapulse 2024",
       ["SAT 1 Schweiz", "SAT.1 CH HD", "CH: SAT.1 HD"],
       ["sat.1 schweiz", "sat 1 schweiz"], ["sat1.ch"], ["sat.1 schweiz*"],
       ["(?i)\\bsat[\\. ]*1\\s*(schweiz|ch)\\b"], [], True, False, False, True)

add_ch("CH_PROSIEBEN_SCHWEIZ", "ProSieben Schweiz", "Pro7 CH", "CH", "Schweiz", "DE-CH", "de",
       "Private", "Vollprogramm", 47, 72.0, 1.3, 2024, "Mediapulse 2024",
       ["Pro 7 Schweiz", "ProSieben CH HD", "CH: ProSieben HD"],
       ["prosieben schweiz", "pro7 ch"], ["prosieben.ch"], ["prosieben schweiz*"],
       ["(?i)\\bpro\\s*(sieben|7)\\s*(schweiz|ch)\\b"], [], True, False, False, True)

print(f"Total defined channels: {len(channels)}")

# Write dach_channels.json
channels_output = {
    "version": "1.0.0",
    "updatedAt": "2026-09-23",
    "totalCount": len(channels),
    "countries": ["DE", "AT", "CH"],
    "channels": channels
}

# Aliases file
aliases_list = []
for c in channels:
    for a in c["aliases"]:
        aliases_list.append({
            "alias": a,
            "channelId": c["channelId"],
            "canonicalName": c["canonicalName"],
            "countryCode": c["countryCode"],
            "region": c["region"]
        })

aliases_output = {
    "version": "1.0.0",
    "totalAliases": len(aliases_list),
    "aliases": aliases_list,
    "qualityTagsStripping": [
        "HD", "FHD", "UHD", "4K", "SD", "HEVC", "H264", "H265",
        "1080P", "720P", "50FPS", "60FPS", "RAW", "HDR"
    ]
}

# Popularity file
de_pop = [c for c in channels if c["countryCode"] == "DE"]
de_pop.sort(key=lambda x: x["popularityRank"])
at_pop = [c for c in channels if c["countryCode"] == "AT"]
at_pop.sort(key=lambda x: x["popularityRank"])
ch_pop = [c for c in channels if c["countryCode"] == "CH"]
ch_pop.sort(key=lambda x: x["popularityRank"])

popularity_output = {
    "version": "1.0.0",
    "year": 2025,
    "DE": {
        "country": "Deutschland",
        "source": "AGF Videoforschung / Marktanteile 2024-2025",
        "ranking": [{
            "rank": ch["popularityRank"],
            "channelId": ch["channelId"],
            "canonicalName": ch["canonicalName"],
            "marketShare": ch["marketShare"],
            "popularityScore": ch["popularityScore"]
        } for ch in de_pop]
    },
    "AT": {
        "country": "Österreich",
        "source": "Teletest / ORF Jahresbericht 2025",
        "ranking": [{
            "rank": ch["popularityRank"],
            "channelId": ch["channelId"],
            "canonicalName": ch["canonicalName"],
            "marketShare": ch["marketShare"],
            "popularityScore": ch["popularityScore"]
        } for ch in at_pop]
    },
    "CH": {
        "country": "Schweiz",
        "source": "Mediapulse TV Data 2024-2025",
        "ranking": [{
            "rank": ch["popularityRank"],
            "channelId": ch["channelId"],
            "canonicalName": ch["canonicalName"],
            "marketShare": ch["marketShare"],
            "popularityScore": ch["popularityScore"],
            "region": ch["region"]
        } for ch in ch_pop]
    }
}

# Validation and Collisions
channel_ids = [c["channelId"] for c in channels]
duplicate_ids = [cid for cid in channel_ids if channel_ids.count(cid) > 1]

alias_map = {}
duplicate_aliases = []
for a in aliases_list:
    clean = re.sub(r'[^a-zA-Z0-9]', '', a["alias"].upper())
    if clean in alias_map and alias_map[clean]["channelId"] != a["channelId"]:
        duplicate_aliases.append({
            "alias": a["alias"],
            "channel1": alias_map[clean],
            "channel2": {"channelId": a["channelId"], "canonicalName": a["canonicalName"], "country": a["countryCode"]}
        })
    else:
        alias_map[clean] = {"channelId": a["channelId"], "canonicalName": a["canonicalName"], "country": a["countryCode"]}

missing_popularity = [c["channelId"] for c in channels if not c.get("popularityRank")]
missing_country = [c["channelId"] for c in channels if not c.get("countryCode")]
missing_category = [c["channelId"] for c in channels if not c.get("category")]
missing_region = [c["channelId"] for c in channels if not c.get("region")]

# Check Collision Candidates
de_names = {c["channelId"]: c["canonicalName"].upper() for c in de_pop}
at_names = {c["channelId"]: c["canonicalName"].upper() for c in at_pop}
ch_names = {c["channelId"]: c["canonicalName"].upper() for c in ch_pop}

de_at_collisions = [
    {"pattern": "RTL vs RTL Austria", "de": "DE_RTL", "at": "AT_RTL_AUSTRIA", "rule": "Austrian window must match AT, default RTL is DE."},
    {"pattern": "SAT.1 vs SAT.1 Österreich", "de": "DE_SAT1", "at": "AT_SAT1_OESTERREICH", "rule": "Österreich keyword directs to AT."},
    {"pattern": "ProSieben vs ProSieben Austria", "de": "DE_PROSIEBEN", "at": "AT_PROSIEBEN_AUSTRIA", "rule": "Austria keyword directs to AT."},
    {"pattern": "VOX vs VOX Austria", "de": "DE_VOX", "at": "AT_VOX_AUSTRIA", "rule": "Austria keyword directs to AT."},
    {"pattern": "ServusTV DE vs ServusTV AT", "de": "DE_SERVUSTV_DE", "at": "AT_SERVUSTV", "rule": "ServusTV without qualifier defaults to AT (origin), ServusTV Deutschland is DE."}
]

de_ch_collisions = [
    {"pattern": "3sat (DE) vs 3+ (CH)", "de": "DE_3SAT", "ch": "CH_3_PLUS", "rule": "3sat is DE cultural channel, 3+ is CH private entertainment. Regex negative lookaheads prevent false positive."},
    {"pattern": "RTL vs RTL Schweiz", "de": "DE_RTL", "ch": "CH_RTL_SCHWEIZ", "rule": "Schweiz/CH tag directs to CH."},
    {"pattern": "SAT.1 vs SAT.1 Schweiz", "de": "DE_SAT1", "ch": "CH_SAT1_SCHWEIZ", "rule": "Schweiz/CH tag directs to CH."},
    {"pattern": "ProSieben vs ProSieben Schweiz", "de": "DE_PROSIEBEN", "ch": "CH_PROSIEBEN_SCHWEIZ", "rule": "Schweiz/CH tag directs to CH."}
]

at_ch_collisions = [
    {"pattern": "PULS 4 (AT) vs PULS 24 (AT) vs Puls 8 (CH)", "at": ["AT_PULS_4", "AT_PULS_24"], "ch": "CH_PULS_8", "rule": "Strict digit boundaries: Puls 4 != Puls 8 != Puls 24."},
    {"pattern": "ATV (AT) vs ATV II (AT)", "at": ["AT_ATV", "AT_ATV2"], "ch": None, "rule": "ATV matches only ATV without II or 2."}
]

validation_output = {
    "summary": {
        "totalDeChannels": len(de_pop),
        "totalAtChannels": len(at_pop),
        "totalChChannels": len(ch_pop),
        "totalChannels": len(channels),
        "duplicateChannelIdsCount": len(duplicate_ids),
        "duplicateAliasesCount": len(duplicate_aliases),
        "missingPopularityCount": len(missing_popularity),
        "missingCountryCount": len(missing_country),
        "missingCategoryCount": len(missing_category),
        "missingRegionCount": len(missing_region)
    },
    "duplicateChannelIds": duplicate_ids,
    "duplicateAliases": duplicate_aliases,
    "missingPopularity": missing_popularity,
    "missingCountry": missing_country,
    "missingCategory": missing_category,
    "missingRegion": missing_region,
    "collisionCandidates": {
        "deAtCollisions": de_at_collisions,
        "deChCollisions": de_ch_collisions,
        "atChCollisions": at_ch_collisions
    },
    "validationStatus": "PASSED" if len(duplicate_ids) == 0 and len(missing_country) == 0 and len(missing_category) == 0 and len(missing_region) == 0 else "FAILED"
}

# Ensure destination dirs exist
os.makedirs("app/src/main/assets", exist_ok=True)

targets = ["app/src/main/assets", "."]

for target_dir in targets:
    with open(os.path.join(target_dir, "dach_channels.json"), "w", encoding="utf-8") as f:
        json.dump(channels_output, f, indent=2, ensure_ascii=False)

    with open(os.path.join(target_dir, "dach_channel_aliases.json"), "w", encoding="utf-8") as f:
        json.dump(aliases_output, f, indent=2, ensure_ascii=False)

    with open(os.path.join(target_dir, "dach_popularity.json"), "w", encoding="utf-8") as f:
        json.dump(popularity_output, f, indent=2, ensure_ascii=False)

    with open(os.path.join(target_dir, "dach_channel_validation.json"), "w", encoding="utf-8") as f:
        json.dump(validation_output, f, indent=2, ensure_ascii=False)

print("SUCCESS: Generated all 4 DACH files successfully.")
