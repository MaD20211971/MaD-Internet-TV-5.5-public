package com.example.androidtvlight.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.androidtvlight.domain.model.AmbientLightScene
import com.example.androidtvlight.ui.theme.TvBrightSky
import com.example.androidtvlight.ui.theme.TvElectricCyan
import com.example.androidtvlight.ui.theme.TvGoldenFocus
import com.example.androidtvlight.ui.viewmodel.RemoteCommand
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the original station logo from 2ix2.com with custom Referer/User-Agent headers,
 * high-contrast container styling, and a stylized station crest fallback.
 */
@Composable
fun ChannelLogoBadge(
    logoUrl: String,
    channelName: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    cornerRadius: Dp = 14.dp
) {
    val context = LocalContext.current
    val imageRequest = remember(logoUrl) {
        ImageRequest.Builder(context)
            .data(logoUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://www.2ix2.com/")
            .crossfade(true)
            .build()
    }

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.White,
        border = BorderStroke(2.dp, accentColor.copy(alpha = 0.9f)),
        shadowElevation = 4.dp
    ) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = "$channelName Original Sender Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius - 4.dp))
                        .background(Color(0xFF0A192F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channelName.take(4).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius - 4.dp))
                        .background(Color(0xFF0A192F)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = channelName.take(5).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        )
    }
}

/**
 * Interactive card specifically engineered for both Android TV D-Pad remote focus
 * and direct Touchscreen taps, featuring high-contrast border rings and scale elevation.
 */
@Composable
fun TvFocusableCard(
    isSelectedOrFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = "tv_focusable_card",
    accentColor: Color = TvElectricCyan,
    cornerRadius: Dp = 20.dp,
    content: @Composable (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHardwareFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val activeFocus = isHardwareFocused || isSelectedOrFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            activeFocus -> 1.03f
            else -> 1.0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tv_card_scale"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (activeFocus) 3.dp else 1.dp,
        label = "tv_card_border_width"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isHardwareFocused -> TvGoldenFocus
            activeFocus -> accentColor
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        label = "tv_card_border_color"
    )

    val containerColor by animateColorAsState(
        targetValue = if (activeFocus) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "tv_card_bg"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .testTag(testTagName)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (activeFocus) 12.dp else 3.dp
        )
    ) {
        content(activeFocus)
    }
}

@Composable
fun AmbientBiasLightCanvas(
    scene: AmbientLightScene,
    brightness: Float,
    dynamicPulse: Boolean,
    warmFilter: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_light_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (9000 / scene.pulseSpeed.coerceAtLeast(0.5f)).toInt()
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val effectiveAlpha = (brightness * if (dynamicPulse) pulseAlpha else 0.92f).coerceIn(0.15f, 1.0f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = scene.primaryColor)

        val offset1 = Offset(
            x = w * (0.28f + 0.14f * cos(phase)),
            y = h * (0.32f + 0.12f * sin(phase))
        )
        val offset2 = Offset(
            x = w * (0.74f - 0.15f * sin(phase * 0.8f)),
            y = h * (0.68f + 0.12f * cos(phase * 0.8f))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scene.tertiaryColor.copy(alpha = 0.52f * effectiveAlpha),
                    scene.secondaryColor.copy(alpha = 0.25f * effectiveAlpha),
                    Color.Transparent
                ),
                center = offset1,
                radius = (w.coerceAtLeast(h) * 0.62f).coerceAtLeast(10f)
            ),
            center = offset1,
            radius = (w.coerceAtLeast(h) * 0.62f).coerceAtLeast(10f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scene.secondaryColor.copy(alpha = 0.58f * effectiveAlpha),
                    scene.primaryColor.copy(alpha = 0.2f * effectiveAlpha),
                    Color.Transparent
                ),
                center = offset2,
                radius = (w.coerceAtLeast(h) * 0.55f).coerceAtLeast(10f)
            ),
            center = offset2,
            radius = (w.coerceAtLeast(h) * 0.55f).coerceAtLeast(10f)
        )

        if (warmFilter > 0.01f) {
            drawRect(
                color = Color(0xFFF59E0B).copy(alpha = (warmFilter * 0.28f).coerceIn(0f, 0.25f))
            )
        }
    }
}

@Composable
fun VirtualRemotePadDock(
    onCommand: (RemoteCommand) -> Unit,
    onCloseDock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("virtual_remote_dock"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF091931).copy(alpha = 0.96f),
        border = BorderStroke(2.dp, TvElectricCyan.copy(alpha = 0.85f)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SettingsRemote,
                        contentDescription = null,
                        tint = TvGoldenFocus,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TV-Steuerkreuz",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Ausblenden",
                    style = MaterialTheme.typography.labelMedium,
                    color = TvBrightSky,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { onCloseDock() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("hide_remote_dock_button")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            RemoteDirectionButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDesc = "Nach oben",
                tag = "dpad_up_button",
                onClick = { onCommand(RemoteCommand.UP) }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteDirectionButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDesc = "Nach links",
                    tag = "dpad_left_button",
                    onClick = { onCommand(RemoteCommand.LEFT) }
                )

                Surface(
                    onClick = { onCommand(RemoteCommand.OK) },
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("dpad_ok_button"),
                    shape = CircleShape,
                    color = TvElectricCyan,
                    border = BorderStroke(2.dp, TvGoldenFocus)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF041326)
                        )
                    }
                }

                RemoteDirectionButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDesc = "Nach rechts",
                    tag = "dpad_right_button",
                    onClick = { onCommand(RemoteCommand.RIGHT) }
                )
            }

            RemoteDirectionButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDesc = "Nach unten",
                tag = "dpad_down_button",
                onClick = { onCommand(RemoteCommand.DOWN) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallRemotePillButton(
                    label = "Zurück",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    tag = "dpad_back_button",
                    onClick = { onCommand(RemoteCommand.BACK) }
                )
                SmallRemotePillButton(
                    label = "Start",
                    icon = Icons.Default.Home,
                    tag = "dpad_home_button",
                    onClick = { onCommand(RemoteCommand.HOME) }
                )
                SmallRemotePillButton(
                    label = "CH+",
                    icon = Icons.Default.CheckCircle,
                    tag = "dpad_ch_up_button",
                    onClick = { onCommand(RemoteCommand.CHANNEL_UP) }
                )
            }
        }
    }
}

@Composable
private fun RemoteDirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(tag),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFF16325B),
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun SmallRemotePillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF16325B),
        border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TvElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
    }
}
