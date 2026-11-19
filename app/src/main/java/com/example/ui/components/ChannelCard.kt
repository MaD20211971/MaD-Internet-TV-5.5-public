package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChannelItem
import com.example.data.model.EpgProgram
import com.example.data.parser.ContentClassifier
import com.example.data.parser.FreeEpgService
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
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

/**
 * Modernisierte Aurora-Studio Senderkarte für Android TV (10-Foot UI) & Android 13+.
 * Unterstützt sowohl den Standard-Komfort-Modus (inkl. Live-EPG-Zeitleiste & Folgesendung)
 * als auch den platzsparenden Kompakt-Modus (`isCompact = true`).
 */
@Composable
fun ChannelCard(
    channel: ChannelItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    epgTimezone: String = "Europe/Berlin",
    epgTimeOffsetHours: Int = 0,
    currentProgram: EpgProgram? = null,
    nextProgram: EpgProgram? = null,
    roomPrograms: List<EpgProgram>? = null,
    onInspectCountry: (() -> Unit)? = null,
    isCompact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val currentMinute = (System.currentTimeMillis() / 30000)
    val (currentShow, nextShow) = remember(
        channel.tvgId,
        channel.name,
        epgTimezone,
        epgTimeOffsetHours,
        currentMinute,
        currentProgram,
        nextProgram,
        roomPrograms
    ) {
        if (currentProgram != null || nextProgram != null) {
            Pair(currentProgram, nextProgram)
        } else {
            FreeEpgService.getCurrentAndNextProgram(
                tvgId = channel.tvgId,
                channelName = channel.name,
                timezoneId = epgTimezone,
                offsetHours = epgTimeOffsetHours,
                targetTimeMs = System.currentTimeMillis(),
                roomPrograms = roomPrograms
            )
        }
    }

    val cardBg by animateColorAsState(
        targetValue = if (isFocused) DarkSurfaceElevated else DarkNavyCard,
        animationSpec = tween(140),
        label = "channel_card_bg"
    )

    val borderColor = when {
        isFocused -> GoldPrimary
        channel.isFavorite -> AccentRose.copy(alpha = 0.55f)
        channel.isDach -> BorderGlow
        else -> BorderSubtle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("channel_item_${channel.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isFocused) {
                            listOf(
                                GoldPrimary.copy(alpha = 0.14f),
                                DarkSurfaceElevated,
                                DarkNavyCard
                            )
                        } else {
                            listOf(
                                DarkSurface,
                                DarkNavyCard
                            )
                        }
                    )
                )
                .padding(horizontal = 12.dp, vertical = if (isCompact) 8.dp else 11.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Studio Logo Box
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 44.dp else 54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    DarkSurfaceElevated,
                                    ObsidianBlack
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isFocused) GoldPrimary else BorderSubtle,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(if (isCompact) 36.dp else 44.dp)
                                .padding(2.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        val initial = channel.name.trim().take(2).uppercase()
                        Text(
                            text = initial,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            fontSize = if (isCompact) 14.sp else 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Channel Title, Metadata Chips & Inline Compact EPG
                Column(modifier = Modifier.weight(1f)) {
                    // Metadata Tag Strip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val isAdult = channel.countryCode.uppercase() == "ADULT" ||
                            ContentClassifier.isAdultContent(channel.name, channel.groupTitle)
                        val countryText = when {
                            isAdult -> "🔞 18+"
                            channel.countryCode.uppercase() == "DE" -> "🇩🇪 DE"
                            channel.countryCode.uppercase() == "AT" -> "🇦🇹 AT"
                            channel.countryCode.uppercase() == "CH" -> "🇨🇭 CH"
                            channel.countryCode.uppercase() == "EXYU" -> "🇷🇸 EX-YU"
                            channel.countryCode.uppercase() == "TR" -> "🇹🇷 TR"
                            channel.countryCode.uppercase() in listOf("UK", "GB") -> "🇬🇧 UK"
                            channel.countryCode.uppercase() == "US" -> "🇺🇸 US"
                            channel.countryCode.uppercase() == "IT" -> "🇮🇹 IT"
                            channel.countryCode.uppercase() == "FR" -> "🇫🇷 FR"
                            channel.countryCode.uppercase() == "ES" -> "🇪🇸 ES"
                            channel.countryCode.uppercase() == "PL" -> "🇵🇱 PL"
                            channel.countryCode.uppercase() == "NL" -> "🇳🇱 NL"
                            channel.countryCode.uppercase() == "AL" -> "🇦🇱 AL"
                            channel.countryCode.uppercase() == "AR" -> "🇸🇦 AR"
                            channel.countryCode.uppercase() == "RU" -> "🇷🇺 RU"
                            else -> if (channel.isDach) "🇩🇪 DE" else "🌐 INT"
                        }

                        // Country Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    when {
                                        isAdult -> AccentRose.copy(alpha = 0.22f)
                                        channel.countryCode.uppercase() in listOf("AT", "CH") -> AccentRose.copy(alpha = 0.18f)
                                        channel.countryCode.uppercase() == "DE" || channel.isDach -> GoldPrimary.copy(alpha = 0.18f)
                                        else -> ObsidianBlack.copy(alpha = 0.65f)
                                    }
                                )
                                .then(
                                    if (onInspectCountry != null) {
                                        Modifier
                                            .clickable { onInspectCountry() }
                                            .testTag("inspect_country_${channel.id}")
                                    } else Modifier
                                )
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = countryText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    isAdult -> AccentRose
                                    channel.countryCode.uppercase() in listOf("AT", "CH") -> AccentRose
                                    channel.countryCode.uppercase() == "DE" || channel.isDach -> GoldLight
                                    else -> TextSecondary
                                }
                            )
                        }

                        // Resolution Badge (4K / FHD / HD)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(AccentCyan.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = channel.resolutionBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentCyan
                            )
                        }

                        // VIP Badge
                        if (channel.isVip || channel.name.contains("★") || channel.name.contains("⭐") || channel.name.contains("VIP", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentAmber.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "★ VIP",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentAmber
                                )
                            }
                        }

                        // 24/7 Badge
                        if (channel.is24_7 || channel.name.contains("24/7", ignoreCase = true) || channel.groupTitle.contains("24/7", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "24/7",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentEmerald
                                )
                            }
                        }

                        // Group / Bouquet Title
                        Text(
                            text = channel.groupTitle,
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Channel Name + Favorite Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = channel.name,
                            fontSize = if (isCompact) 14.sp else 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (channel.isFavorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentRose.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "♥ FAV",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AccentRose
                                )
                            }
                        }
                    }

                    // Inline EPG preview when in Compact mode
                    if (isCompact && currentShow != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmerald)
                            )
                            Text(
                                text = currentShow.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Ergonomic Favorite & Play Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (channel.isFavorite) AccentRose.copy(alpha = 0.18f)
                                else ObsidianBlack.copy(alpha = 0.45f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (channel.isFavorite) AccentRose.copy(alpha = 0.5f) else BorderSubtle,
                                shape = CircleShape
                            )
                            .testTag("channel_favorite_${channel.id}")
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (channel.isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                            tint = if (channel.isFavorite) AccentRose else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GoldLight, GoldPrimary)
                                )
                            )
                            .clickable(onClick = onClick)
                            .testTag("channel_play_${channel.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Abspielen",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // 4. Full Live EPG Capsule (Comfort Mode)
            if (!isCompact && currentShow != null) {
                Spacer(modifier = Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack.copy(alpha = 0.65f))
                        .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AccentEmerald
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentShow.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = FreeEpgService.formatProgramTime(currentShow.startTime, epgTimezone),
                                fontSize = 10.5.sp,
                                color = GoldLight,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${(currentShow.progress * 100).toInt()}%",
                                fontSize = 10.5.sp,
                                color = AccentEmerald,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        LinearProgressIndicator(
                            progress = { currentShow.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = GoldPrimary,
                            trackColor = DarkSurfaceElevated
                        )

                        if (nextShow != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Danach: ${nextShow.title}",
                                fontSize = 10.5.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
