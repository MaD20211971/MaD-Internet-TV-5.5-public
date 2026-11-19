package com.example.data.parser

import com.example.data.model.ChannelItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType

object SampleData {

    const val PLUTO_TV_PLAYLIST_ID = "m3u_pluto_tv"
    const val RAKUTEN_TV_PLAYLIST_ID = "m3u_rakuten_tv"
    const val LG_TV_PLAYLIST_ID = "m3u_lg_tv"
    const val DE_INTERNATIONAL_PLAYLIST_ID = "m3u_de_international"
    const val GERMAN_TV_JOSXHA_PLAYLIST_ID = "m3u_german_tv_josxha"
    const val FREE_LIVE_SPORTS_PLAYLIST_ID = "m3u_free_live_sports"
    const val IPTV_ORG_DE_PLAYLIST_ID = "m3u_iptv_org_de"
    const val KOOL_TO_PLAYLIST_ID = "m3u_kool_to_live"

    const val XPANEL_TEST_MAC_PLAYLIST_ID = "mac_portal_xpanel_146cfc"

    const val DRAGON_IPTV_PLAYLIST_ID = "mac_portal_dragoniptv"
    const val ONAIR8K_PLAYLIST_ID = "mac_portal_onair8k"
    const val USER_M3U_PLAYLIST_ID = "playlist_154_6_41_15"
    const val FXMAG_PLAYLIST_ID = "mac_portal_fxmag"
    const val EURO_DELUXE_PLAYLIST_ID = "mac_portal_euro_deluxe"
    const val WEASELTV_PLAYLIST_ID = "mac_portal_weaseltv"
    const val ZEROONEX2_PLAYLIST_ID = "mac_portal_zeroonex2"

    fun getInitialPlaylists(): List<PlaylistEntity> {
        return listOf(
            PlaylistEntity(
                id = PLUTO_TV_PLAYLIST_ID,
                name = "Pluto TV",
                url = "https://raw.githubusercontent.com/BuddyChewChew/pluto/main/pluto_de.m3u",
                type = PlaylistType.M3U_URL,
                isActive = true
            ),
            PlaylistEntity(
                id = KOOL_TO_PLAYLIST_ID,
                name = "Kool.to Live TV (DE/AT/CH)",
                url = "http://kool.to/channels",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = RAKUTEN_TV_PLAYLIST_ID,
                name = "Rakuten TV",
                url = "https://www.apsattv.com/rakuten_de.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = LG_TV_PLAYLIST_ID,
                name = "LG TV",
                url = "https://www.apsattv.com/atlg.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = DE_INTERNATIONAL_PLAYLIST_ID,
                name = "DE & Internationales",
                url = "https://iptv-org.github.io/iptv/index.language.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = GERMAN_TV_JOSXHA_PLAYLIST_ID,
                name = "German TV (DE)",
                url = "https://raw.githubusercontent.com/josxha/german-tv-m3u/refs/heads/main/german-tv.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = FREE_LIVE_SPORTS_PLAYLIST_ID,
                name = "Free Live Sports",
                url = "https://www.apsattv.com/freelivesports.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            ),
            PlaylistEntity(
                id = IPTV_ORG_DE_PLAYLIST_ID,
                name = "IPTV-Org Deutschland",
                url = "https://iptv-org.github.io/iptv/countries/de.m3u",
                type = PlaylistType.M3U_URL,
                isActive = false
            )
        )
    }

    fun getDefaultChannels(playlistId: String): List<ChannelItem> {
        return emptyList()
    }
}
