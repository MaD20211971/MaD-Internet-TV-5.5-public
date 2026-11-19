package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainTab

/**
 * Modernes 2-Stufen Glassmorphic Dock für Android 13+ Smartphones & Portrait-Tablets:
 * - Untere Hauptleiste: 5 großzügige, ergonomische Kern-Tabs (Live TV, EPG, Filme, Serien, Favoriten) + Hub-Schalter
 * - Obere Quick-Hub-Leiste: Schnellzugriff auf Live TV Light, Fortsetzen, Suche, Local M3U, Web TV & Einstellungen
 *   (jederzeit ein-/ausklappbar für maximale Übersichtlichkeit).
 */
@Composable
fun BottomNavigationSection(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    liveCount: Int,
    radioCount: Int = 0,
    movieCount: Int,
    seriesCount: Int,
    favoriteCount: Int = 0,
    isEnglish: Boolean = false
) {
    val secondaryTabs = remember {
        setOf(
            MainTab.LIVE_TV_LIGHT,
            MainTab.CONTINUE_WATCHING,
            MainTab.SEARCH,
            MainTab.LOCAL_M3U,
            MainTab.WEB_STREAMS,
            MainTab.SETTINGS
        )
    }
    var showQuickHubRibbon by remember { mutableStateOf(false) }
    val isSecondaryActive = selectedTab in secondaryTabs

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ObsidianBlack.copy(alpha = 0.96f),
        tonalElevation = 12.dp,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkNavyCard.copy(alpha = 0.95f),
                            ObsidianBlack
                        )
                    )
                )
        ) {
            // 1. Obere Quick-Hub Pill-Leiste (sekundäre Werkzeuge & Spezial-Modi)
            AnimatedVisibility(
                visible = showQuickHubRibbon || isSecondaryActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickHubPillItem(
                            icon = Icons.Default.PlayCircleOutline,
                            label = "Live TV Light",
                            isSelected = selectedTab == MainTab.LIVE_TV_LIGHT,
                            accentColor = AccentEmerald,
                            testTag = "nav_tab_livetv_light",
                            onClick = { onTabSelected(MainTab.LIVE_TV_LIGHT) }
                        )

                        QuickHubPillItem(
                            icon = Icons.Default.PlayArrow,
                            label = if (isEnglish) "Resume" else "Fortsetzen",
                            isSelected = selectedTab == MainTab.CONTINUE_WATCHING,
                            accentColor = AccentEmerald,
                            testTag = "nav_tab_continue",
                            onClick = { onTabSelected(MainTab.CONTINUE_WATCHING) }
                        )

                        QuickHubPillItem(
                            icon = Icons.Default.Search,
                            label = if (isEnglish) "Search" else "Suche",
                            isSelected = selectedTab == MainTab.SEARCH,
                            accentColor = GoldLight,
                            testTag = "nav_tab_search",
                            onClick = { onTabSelected(MainTab.SEARCH) }
                        )

                        QuickHubPillItem(
                            icon = Icons.Default.FolderOpen,
                            label = "Local M3U",
                            isSelected = selectedTab == MainTab.LOCAL_M3U,
                            accentColor = AccentAmber,
                            testTag = "nav_tab_local_m3u",
                            onClick = { onTabSelected(MainTab.LOCAL_M3U) }
                        )

                        QuickHubPillItem(
                            icon = Icons.Default.Language,
                            label = if (isEnglish) "Web Streams" else "Web TV",
                            isSelected = selectedTab == MainTab.WEB_STREAMS,
                            accentColor = AccentCyan,
                            testTag = "nav_tab_webstreams",
                            onClick = { onTabSelected(MainTab.WEB_STREAMS) }
                        )

                        QuickHubPillItem(
                            icon = Icons.Default.Settings,
                            label = if (isEnglish) "Settings" else "Einstellungen",
                            isSelected = selectedTab == MainTab.SETTINGS,
                            accentColor = GoldPrimary,
                            testTag = "nav_tab_settings",
                            onClick = { onTabSelected(MainTab.SETTINGS) }
                        )
                    }
                    HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
                }
            }

            // 2. Untere Primäre Komfort-Navigationsleiste (5 feste Hauptziele + Hub-Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryDockItem(
                    icon = Icons.Default.Tv,
                    label = "Live TV",
                    count = liveCount,
                    isSelected = selectedTab == MainTab.LIVE_TV,
                    badgeColor = AccentEmerald,
                    testTag = "nav_tab_livetv",
                    onClick = { onTabSelected(MainTab.LIVE_TV) },
                    modifier = Modifier.weight(1f)
                )

                PrimaryDockItem(
                    icon = Icons.Default.CalendarMonth,
                    label = "EPG Guide",
                    count = null,
                    isSelected = selectedTab == MainTab.EPG_GUIDE,
                    badgeColor = GoldPrimary,
                    testTag = "nav_tab_epg",
                    onClick = { onTabSelected(MainTab.EPG_GUIDE) },
                    modifier = Modifier.weight(1f)
                )

                PrimaryDockItem(
                    icon = Icons.Default.Movie,
                    label = if (isEnglish) "Movies" else "Filme",
                    count = movieCount,
                    isSelected = selectedTab == MainTab.MOVIES,
                    badgeColor = AccentCyan,
                    testTag = "nav_tab_movies",
                    onClick = { onTabSelected(MainTab.MOVIES) },
                    modifier = Modifier.weight(1f)
                )

                PrimaryDockItem(
                    icon = Icons.Default.VideoLibrary,
                    label = if (isEnglish) "Series" else "Serien",
                    count = seriesCount,
                    isSelected = selectedTab == MainTab.SERIES,
                    badgeColor = AccentPurple,
                    testTag = "nav_tab_series",
                    onClick = { onTabSelected(MainTab.SERIES) },
                    modifier = Modifier.weight(1f)
                )

                PrimaryDockItem(
                    icon = Icons.Default.Radio,
                    label = "Radio",
                    count = radioCount,
                    isSelected = selectedTab == MainTab.RADIO,
                    badgeColor = AccentCyan,
                    testTag = "nav_tab_radio",
                    onClick = { onTabSelected(MainTab.RADIO) },
                    modifier = Modifier.weight(1f)
                )

                PrimaryDockItem(
                    icon = Icons.Default.Star,
                    label = if (isEnglish) "Favorites" else "Favoriten",
                    count = favoriteCount,
                    isSelected = selectedTab == MainTab.FAVORITES,
                    badgeColor = AccentRose,
                    testTag = "nav_tab_favorites",
                    onClick = { onTabSelected(MainTab.FAVORITES) },
                    modifier = Modifier.weight(1f)
                )

                // Toggle Button für die obere Quick-Hub-Leiste (exakt identische Geometrie wie PrimaryDockItem)
                PrimaryDockItem(
                    icon = if (showQuickHubRibbon) Icons.Default.KeyboardArrowDown else Icons.Default.Apps,
                    label = if (isEnglish) "More" else "Mehr",
                    count = null,
                    isSelected = showQuickHubRibbon || isSecondaryActive,
                    badgeColor = GoldPrimary,
                    testTag = "nav_tab_more_hub",
                    onClick = { showQuickHubRibbon = !showQuickHubRibbon },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatCompactBadgeCount(count: Int): String {
    return when {
        count >= 100_000 -> "${count / 1000}k"
        count >= 10_000 -> "${count / 1000}k"
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1000f).replace(".0k", "k")
        else -> count.toString()
    }
}

@Composable
private fun PrimaryDockItem(
    icon: ImageVector,
    label: String,
    count: Int?,
    isSelected: Boolean,
    badgeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Fester Container (48.dp x 28.dp), damit das Icon immer 100% identisch zentriert bleibt,
        // egal ob count == 0 oder count > 0 ist. Das Badge liegt als reines Overlay darüber.
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Material 3 Active Pill Indicator mit festen Maßen (44.dp x 26.dp)
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (isSelected) GoldPrimary.copy(alpha = 0.22f) else Color.Transparent
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) GoldPrimary else Color.Transparent,
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) GoldPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (count != null && count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .border(0.75.dp, ObsidianBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 3.5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatCompactBadgeCount(count),
                        color = ObsidianBlack,
                        fontSize = 7.5.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                )
            )
        )
    }
}

@Composable
private fun QuickHubPillItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.22f) else DarkSurface
            )
            .border(
                width = if (isSelected) 1.2.dp else 1.dp,
                color = if (isSelected) accentColor else BorderSubtle,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) accentColor else TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TextPrimary else TextSecondary,
            maxLines = 1
        )
    }
}
