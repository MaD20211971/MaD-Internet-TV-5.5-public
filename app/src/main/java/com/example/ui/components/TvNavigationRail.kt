package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkNavyBackground
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
 * 10-Foot Leanback Side-Navigation Studio Rail für Android TV, Fire TV & Wide-Screen Tablets.
 * Bietet einen übersichtlichen Komfort-Modus (Icon + Klartext + Live-Zähler) sowie einen
 * einklappbaren Kompakt-Modus, optimiert für D-Pad-Fernbedienungen und große Bildschirme.
 */
@Composable
fun TvNavigationRail(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    liveCount: Int,
    radioCount: Int = 0,
    movieCount: Int,
    seriesCount: Int,
    favoriteCount: Int = 0,
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 196.dp else 78.dp,
        animationSpec = tween(durationMillis = 220),
        label = "tv_rail_width"
    )

    Surface(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight(),
        color = ObsidianBlack,
        tonalElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = BorderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBlack,
                            DarkNavyCard,
                            ObsidianBlack
                        )
                    )
                )
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Studio Brand Header + Collapse/Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isExpanded) Arrangement.SpaceBetween else Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(DarkSurfaceElevated, ObsidianBlack)
                                )
                            )
                            .border(1.5.dp, GoldPrimary.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "MaD IPTV public",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    if (isExpanded) {
                        Column {
                            Text(
                                text = "MaD IPTV public",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                )
                                Text(
                                    text = "Privat V5.5",
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (isExpanded) {
                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = if (isEnglish) "Collapse sidebar" else "Seitenleiste einklappen",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isExpanded) {
                IconButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isEnglish) "Expand sidebar" else "Seitenleiste ausklappen",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Scrollable Categorized Navigation Items
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isExpanded) {
                    RailSectionLabel(text = if (isEnglish) "LIVE & BROADCAST" else "FERNSEHEN & EPG")
                }

                // Live TV
                TvNavRailItem(
                    icon = Icons.Default.Tv,
                    label = "Live TV",
                    count = liveCount,
                    isSelected = selectedTab == MainTab.LIVE_TV,
                    badgeColor = AccentEmerald,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_livetv",
                    onClick = { onTabSelected(MainTab.LIVE_TV) }
                )

                // EPG Programmzeitschrift
                TvNavRailItem(
                    icon = Icons.Default.CalendarMonth,
                    label = if (isEnglish) "TV Guide (EPG)" else "EPG Programm",
                    count = null,
                    isSelected = selectedTab == MainTab.EPG_GUIDE,
                    badgeColor = GoldPrimary,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_epg",
                    onClick = { onTabSelected(MainTab.EPG_GUIDE) }
                )

                // Live TV light
                TvNavRailItem(
                    icon = Icons.Default.PlayCircleOutline,
                    label = "Live TV Light",
                    count = null,
                    isSelected = selectedTab == MainTab.LIVE_TV_LIGHT,
                    badgeColor = AccentEmerald,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_livetv_light",
                    onClick = { onTabSelected(MainTab.LIVE_TV_LIGHT) }
                )

                // Radio (radio-browser.info + IPTV)
                TvNavRailItem(
                    icon = Icons.Default.Radio,
                    label = "Radio",
                    count = radioCount,
                    isSelected = selectedTab == MainTab.RADIO,
                    badgeColor = AccentCyan,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_radio",
                    onClick = { onTabSelected(MainTab.RADIO) }
                )

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(4.dp))

                if (isExpanded) {
                    RailSectionLabel(text = if (isEnglish) "CINEMA & ON DEMAND" else "KINO & MEDIATHEK")
                }

                // Filme
                TvNavRailItem(
                    icon = Icons.Default.Movie,
                    label = if (isEnglish) "Movies" else "Filme & Kino",
                    count = movieCount,
                    isSelected = selectedTab == MainTab.MOVIES,
                    badgeColor = AccentCyan,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_movies",
                    onClick = { onTabSelected(MainTab.MOVIES) }
                )

                // Serien
                TvNavRailItem(
                    icon = Icons.Default.VideoLibrary,
                    label = if (isEnglish) "Series" else "Serien",
                    count = seriesCount,
                    isSelected = selectedTab == MainTab.SERIES,
                    badgeColor = AccentPurple,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_series",
                    onClick = { onTabSelected(MainTab.SERIES) }
                )

                // Weiter ansehen
                TvNavRailItem(
                    icon = Icons.Default.PlayArrow,
                    label = if (isEnglish) "Continue" else "Fortsetzen",
                    count = null,
                    isSelected = selectedTab == MainTab.CONTINUE_WATCHING,
                    badgeColor = AccentEmerald,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_continue",
                    onClick = { onTabSelected(MainTab.CONTINUE_WATCHING) }
                )

                // Favoriten
                TvNavRailItem(
                    icon = Icons.Default.Star,
                    label = if (isEnglish) "Favorites" else "Favoriten",
                    count = favoriteCount,
                    isSelected = selectedTab == MainTab.FAVORITES,
                    badgeColor = AccentRose,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_favorites",
                    onClick = { onTabSelected(MainTab.FAVORITES) }
                )

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(4.dp))

                if (isExpanded) {
                    RailSectionLabel(text = if (isEnglish) "TOOLS & SOURCES" else "SUCHE & QUELLEN")
                }

                // Suche
                TvNavRailItem(
                    icon = Icons.Default.Search,
                    label = if (isEnglish) "Global Search" else "Globale Suche",
                    count = null,
                    isSelected = selectedTab == MainTab.SEARCH,
                    badgeColor = GoldLight,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_search",
                    onClick = { onTabSelected(MainTab.SEARCH) }
                )

                // Local M3U
                TvNavRailItem(
                    icon = Icons.Default.FolderOpen,
                    label = "Local M3U",
                    count = null,
                    isSelected = selectedTab == MainTab.LOCAL_M3U,
                    badgeColor = AccentAmber,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_local_m3u",
                    onClick = { onTabSelected(MainTab.LOCAL_M3U) }
                )

                // Web Streams
                TvNavRailItem(
                    icon = Icons.Default.Language,
                    label = "Web TV",
                    count = null,
                    isSelected = selectedTab == MainTab.WEB_STREAMS,
                    badgeColor = AccentCyan,
                    isExpanded = isExpanded,
                    testTag = "tv_rail_webstreams",
                    onClick = { onTabSelected(MainTab.WEB_STREAMS) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // 3. Pinned Settings Footer
            TvNavRailItem(
                icon = Icons.Default.Settings,
                label = if (isEnglish) "Settings" else "Einstellungen",
                count = null,
                isSelected = selectedTab == MainTab.SETTINGS,
                badgeColor = GoldPrimary,
                isExpanded = isExpanded,
                testTag = "tv_rail_settings",
                onClick = { onTabSelected(MainTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun RailSectionLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun TvNavRailItem(
    icon: ImageVector,
    label: String,
    count: Int?,
    isSelected: Boolean,
    badgeColor: Color,
    isExpanded: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerBg by animateColorAsState(
        targetValue = when {
            isFocused -> GoldPrimary.copy(alpha = 0.24f)
            isSelected -> DarkSurfaceElevated
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "rail_item_bg"
    )

    val borderColor = when {
        isFocused -> GoldPrimary
        isSelected -> GoldPrimary.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    val iconTint = when {
        isFocused -> Color.White
        isSelected -> GoldPrimary
        else -> TextSecondary
    }

    val textColor = when {
        isFocused -> Color.White
        isSelected -> TextPrimary
        else -> TextSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(
                horizontal = if (isExpanded) 10.dp else 4.dp,
                vertical = if (isExpanded) 9.dp else 7.dp
            )
            .testTag(testTag)
    ) {
        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Active / Focus Pill Bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    isFocused -> GoldLight
                                    isSelected -> GoldPrimary
                                    else -> Color.Transparent
                                }
                            )
                    )

                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (count != null && count > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected || isFocused) badgeColor
                                else badgeColor.copy(alpha = 0.18f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (count > 9999) "${count / 1000}k" else "$count",
                            color = if (isSelected || isFocused) ObsidianBlack else badgeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )

                    if (count != null && count > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor)
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = if (count > 999) "${count / 1000}k" else "$count",
                                color = ObsidianBlack,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
