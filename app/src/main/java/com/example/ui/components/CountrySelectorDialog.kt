package com.example.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChannelItem
import com.example.data.parser.ContentClassifier
import com.example.data.parser.ContentClassifier.CountryInfo
import com.example.ui.theme.*

private enum class SelectorTab {
    COUNTRIES,
    CHANNELS,
    REMOVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectorDialog(
    allChannels: List<ChannelItem>,
    disabledCountries: Set<String>,
    hiddenChannelIds: Set<String>,
    selectedCategory: String,
    countrySummary: com.example.data.model.CountrySummary = com.example.data.model.CountrySummary.EMPTY,
    isTvMode: Boolean = false,
    onSelectCountry: (String) -> Unit,
    onToggleCountry: (String) -> Unit,
    onEnableAllCountries: () -> Unit,
    onDisableAllCountries: () -> Unit,
    onToggleChannelHidden: (String) -> Unit,
    onRemoveAllChannelsInCountry: (String) -> Unit,
    onRestoreAllChannelsInCountry: (String) -> Unit,
    onRestoreAllChannels: () -> Unit,
    onResetAll: () -> Unit,
    onAddCustomChannel: (name: String, streamUrl: String, countryCode: String, groupTitle: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = isTvMode || TvUiUtils.isTvDevice(context) || (isLandscape && configuration.screenWidthDp >= 600)

    var activeTab by remember { mutableStateOf(SelectorTab.COUNTRIES) }
    var focusedCountryCode by remember { mutableStateOf<String?>("DE") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Group all channels by normalized country code on Dispatchers.Default
    var channelsByCountry by remember { mutableStateOf<Map<String, List<ChannelItem>>>(emptyMap()) }
    var orderedCountries by remember { mutableStateOf<List<CountryInfo>>(ContentClassifier.ALL_KNOWN_COUNTRIES) }
    var removedChannels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }

    LaunchedEffect(allChannels, hiddenChannelIds) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val grouped = allChannels.groupBy { it.normalizedCountryCode.trim().uppercase() }
            val countryMap = LinkedHashMap<String, CountryInfo>()
            for (item in ContentClassifier.ALL_KNOWN_COUNTRIES) {
                val key = item.code.trim().uppercase()
                countryMap[key] = item
            }
            for (rawCode in grouped.keys) {
                val upper = rawCode.trim().uppercase()
                if (upper.isNotBlank()) {
                    val info = ContentClassifier.getCountryInfo(upper)
                    val targetCode = info.code.trim().uppercase()
                    if (!countryMap.containsKey(targetCode)) {
                        countryMap[targetCode] = info
                    }
                }
            }
            val ordered = countryMap.values
                .distinctBy { it.code.trim().uppercase() }
                .filter { info ->
                    val code = info.code.trim().uppercase()
                    if (code == "UNKNOWN" || code == "CONFLICT") {
                        (grouped[code]?.size ?: 0) > 0
                    } else {
                        true
                    }
                }
                .sortedWith(
                    compareBy<CountryInfo> {
                        val code = it.code.trim().uppercase()
                        when (code) {
                            "DE" -> 0
                            "AT" -> 1
                            "CH" -> 2
                            "ADULT" -> 3
                            else -> {
                                val count = grouped[code]?.size ?: 0
                                if (count > 0) 4 else 5
                            }
                        }
                    }
                    .thenBy { it.rank }
                    .thenBy { it.name }
                )
            val removed = if (hiddenChannelIds.isEmpty()) emptyList() else allChannels.filter { it.id in hiddenChannelIds }
            channelsByCountry = grouped
            orderedCountries = ordered
            removedChannels = removed
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        if (isLandscape || isTv) {
            BackHandler { onDismissRequest() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = if (isLandscape) 14.dp else 12.dp, vertical = if (isLandscape) 10.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLandscape) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.96f)
                        .widthIn(max = 1200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .testTag("country_selector_dialog"),
                    color = DarkNavyBackground,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // 1. Compact Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.2f))
                                        .border(1.dp, GoldPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🌍", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Länder- & Kanal-Selektor",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldLight
                                    )
                                    Text(
                                        text = "Priorität: DE:, AT:, CH:, ADULT • Andere Länder bei Auswahl synchronisieren",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                                    .testTag("close_country_selector_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Main Two-Column Split Pane
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // LEFT PANE: Controls, Navigation Tabs, Quick Actions, Status
                            Surface(
                                modifier = Modifier
                                    .width(310.dp)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                color = DarkSurface.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Tabs in sidebar
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkNavyBackground)
                                            .padding(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        TabButton(
                                            title = "🌍 Länder (${orderedCountries.size})",
                                            isSelected = activeTab == SelectorTab.COUNTRIES,
                                            onClick = { activeTab = SelectorTab.COUNTRIES },
                                            modifier = Modifier.fillMaxWidth(),
                                            testTag = "tab_countries"
                                        )
                                        TabButton(
                                            title = "📺 Kanäle verwalten",
                                            isSelected = activeTab == SelectorTab.CHANNELS,
                                            onClick = { activeTab = SelectorTab.CHANNELS },
                                            modifier = Modifier.fillMaxWidth(),
                                            testTag = "tab_channels"
                                        )
                                        TabButton(
                                            title = "🗑️ Entfernt (${removedChannels.size})",
                                            isSelected = activeTab == SelectorTab.REMOVED,
                                            onClick = { activeTab = SelectorTab.REMOVED },
                                            modifier = Modifier.fillMaxWidth(),
                                            badgeColor = if (removedChannels.isNotEmpty()) AccentRose else null,
                                            testTag = "tab_removed"
                                        )
                                    }

                                    when (activeTab) {
                                        SelectorTab.COUNTRIES -> {
                                            // Search Input
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("input_search_country"),
                                                placeholder = { Text("Land suchen...", fontSize = 11.sp, color = TextMuted) },
                                                leadingIcon = { Icon(Icons.Default.Search, null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                                                trailingIcon = {
                                                    if (searchQuery.isNotEmpty()) {
                                                        IconButton(onClick = { searchQuery = "" }) {
                                                            Icon(Icons.Default.Close, "Löschen", tint = TextMuted, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = DarkNavyBackground,
                                                    unfocusedContainerColor = DarkNavyBackground,
                                                    focusedBorderColor = GoldPrimary,
                                                    unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                )
                                            )

                                            // Quick Summary Filter Chips (Priority: DE:, AT:, CH:, ADULT)
                                            Text("Priorität (Sofort bereit):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldLight)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                FilterChip(
                                                    selected = selectedCategory.startsWith("DE"),
                                                    onClick = {
                                                        onSelectCountry("DE")
                                                        onDismissRequest()
                                                    },
                                                    label = { Text("🇩🇪 DE: (${countrySummary.deCount})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterChip(
                                                    selected = selectedCategory.startsWith("AT"),
                                                    onClick = {
                                                        onSelectCountry("AT")
                                                        onDismissRequest()
                                                    },
                                                    label = { Text("🇦🇹 AT: (${countrySummary.atCount})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                FilterChip(
                                                    selected = selectedCategory.startsWith("CH"),
                                                    onClick = {
                                                        onSelectCountry("CH")
                                                        onDismissRequest()
                                                    },
                                                    label = { Text("🇨🇭 CH: (${countrySummary.chCount})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                val adultCount = channelsByCountry["ADULT"]?.size ?: 0
                                                FilterChip(
                                                    selected = selectedCategory.startsWith("ADULT"),
                                                    onClick = {
                                                        onSelectCountry("ADULT")
                                                        onDismissRequest()
                                                    },
                                                    label = { Text("🔞 ADULT ($adultCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            // Batch Action Buttons (2x2 grid)
                                            Text("Aktionen:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldLight)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = onEnableAllCountries,
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("btn_enable_all_countries"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.7f))
                                                ) {
                                                    Text("✓ Alle an", fontSize = 10.sp, color = AccentEmerald, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = onDisableAllCountries,
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("btn_disable_all_countries"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.7f))
                                                ) {
                                                    Text("✕ Alle aus", fontSize = 10.sp, color = AccentRose, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = onResetAll,
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("btn_reset_countries"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.4f))
                                                ) {
                                                    Text("↺ Reset", fontSize = 10.sp, color = TextSecondary)
                                                }
                                                Button(
                                                    onClick = { showAddChannelDialog = true },
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("btn_add_custom_channel"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                                                ) {
                                                    Text("+ Kanal", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        SelectorTab.CHANNELS -> {
                                            Text("Land auswählen:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldLight)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 180.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                val uniqueCountries = orderedCountries.distinctBy { it.code.trim().uppercase() }
                                                for (c in uniqueCountries) {
                                                    val isSelected = c.code.trim().equals(focusedCountryCode?.trim(), ignoreCase = true)
                                                    val count = channelsByCountry[c.code.trim().uppercase()]?.size ?: 0
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { focusedCountryCode = c.code },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isSelected) GoldPrimary else DarkNavyBackground,
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "${c.flag} ${c.name}",
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) Color.Black else TextPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Text(
                                                                text = "$count",
                                                                fontSize = 10.sp,
                                                                color = if (isSelected) Color.Black else TextSecondary
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = { showAddChannelDialog = true },
                                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                                            ) {
                                                Text("+ Neuer Sender", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        SelectorTab.REMOVED -> {
                                            Text("Papierkorb:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentRose)
                                            Text(
                                                text = "${removedChannels.size} ausgeblendete Sender können hier reaktiviert werden.",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                            if (removedChannels.isNotEmpty()) {
                                                Button(
                                                    onClick = onRestoreAllChannels,
                                                    modifier = Modifier.fillMaxWidth().height(32.dp).testTag("btn_restore_all_removed_channels"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = Color.Black)
                                                ) {
                                                    Text("Alle wiederherstellen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Quick Status & Done
                                    val activeCountriesCount = orderedCountries.count { it.code !in disabledCountries }
                                    Text(
                                        text = "$activeCountriesCount/${orderedCountries.size} aktiv • ${allChannels.size - removedChannels.size} sichtbar",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )

                                    Button(
                                        onClick = onDismissRequest,
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("done_country_selector_button")
                                    ) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fertig", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // RIGHT PANE: List taking full height
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                when (activeTab) {
                                    SelectorTab.COUNTRIES -> {
                                        CountriesListContent(
                                            countries = orderedCountries,
                                            channelsByCountry = channelsByCountry,
                                            disabledCountries = disabledCountries,
                                            hiddenChannelIds = hiddenChannelIds,
                                            selectedCategory = selectedCategory,
                                            countrySummary = countrySummary,
                                            searchQuery = searchQuery,
                                            onSelectCountry = { code ->
                                                onSelectCountry(code)
                                                onDismissRequest()
                                            },
                                            onToggleCountry = onToggleCountry,
                                            onManageCountryChannels = { code ->
                                                focusedCountryCode = code
                                                activeTab = SelectorTab.CHANNELS
                                            },
                                            isLandscape = true
                                        )
                                    }
                                    SelectorTab.CHANNELS -> {
                                        ChannelsManagementTab(
                                            allChannels = allChannels,
                                            countries = orderedCountries,
                                            selectedCountryCode = focusedCountryCode,
                                            onSelectFocusedCountry = { focusedCountryCode = it },
                                            hiddenChannelIds = hiddenChannelIds,
                                            disabledCountries = disabledCountries,
                                            onToggleChannelHidden = onToggleChannelHidden,
                                            onRemoveAllInCountry = { code -> onRemoveAllChannelsInCountry(code) },
                                            onRestoreAllInCountry = { code -> onRestoreAllChannelsInCountry(code) },
                                            onOpenAddChannel = { showAddChannelDialog = true },
                                            isLandscape = true
                                        )
                                    }
                                    SelectorTab.REMOVED -> {
                                        RemovedChannelsTab(
                                            removedChannels = removedChannels,
                                            onRestoreChannel = onToggleChannelHidden,
                                            onRestoreAllChannels = onRestoreAllChannels,
                                            isLandscape = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .widthIn(max = 720.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .testTag("country_selector_dialog"),
                color = DarkNavyBackground,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 1. Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, GoldPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🌍", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Länder- & Kanal-Selektor",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Text(
                                    text = "Priorität: DE:, AT:, CH:, ADULT • Andere Länder bei Auswahl synchronisieren",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                                .testTag("close_country_selector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Navigation Tabs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TabButton(
                            title = "🌍 Länder (${orderedCountries.size})",
                            isSelected = activeTab == SelectorTab.COUNTRIES,
                            onClick = { activeTab = SelectorTab.COUNTRIES },
                            modifier = Modifier.weight(1f),
                            testTag = "tab_countries"
                        )
                        TabButton(
                            title = "📺 Kanäle verwalten",
                            isSelected = activeTab == SelectorTab.CHANNELS,
                            onClick = { activeTab = SelectorTab.CHANNELS },
                            modifier = Modifier.weight(1f),
                            testTag = "tab_channels"
                        )
                        TabButton(
                            title = "🗑️ Entfernt (${removedChannels.size})",
                            isSelected = activeTab == SelectorTab.REMOVED,
                            onClick = { activeTab = SelectorTab.REMOVED },
                            modifier = Modifier.weight(1f),
                            badgeColor = if (removedChannels.isNotEmpty()) AccentRose else null,
                            testTag = "tab_removed"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Tab Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            SelectorTab.COUNTRIES -> {
                                CountriesOverviewTab(
                                    countries = orderedCountries,
                                    channelsByCountry = channelsByCountry,
                                    disabledCountries = disabledCountries,
                                    hiddenChannelIds = hiddenChannelIds,
                                    selectedCategory = selectedCategory,
                                    countrySummary = countrySummary,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onSelectCountry = { code ->
                                        onSelectCountry(code)
                                        onDismissRequest()
                                    },
                                    onToggleCountry = onToggleCountry,
                                    onManageCountryChannels = { code ->
                                        focusedCountryCode = code
                                        activeTab = SelectorTab.CHANNELS
                                    },
                                    onEnableAllCountries = onEnableAllCountries,
                                    onDisableAllCountries = onDisableAllCountries,
                                    onResetAll = onResetAll,
                                    onOpenAddChannel = { showAddChannelDialog = true }
                                )
                            }
                            SelectorTab.CHANNELS -> {
                                ChannelsManagementTab(
                                    allChannels = allChannels,
                                    countries = orderedCountries,
                                    selectedCountryCode = focusedCountryCode,
                                    onSelectFocusedCountry = { focusedCountryCode = it },
                                    hiddenChannelIds = hiddenChannelIds,
                                    disabledCountries = disabledCountries,
                                    onToggleChannelHidden = onToggleChannelHidden,
                                    onRemoveAllInCountry = { code -> onRemoveAllChannelsInCountry(code) },
                                    onRestoreAllInCountry = { code -> onRestoreAllChannelsInCountry(code) },
                                    onOpenAddChannel = { showAddChannelDialog = true }
                                )
                            }
                            SelectorTab.REMOVED -> {
                                RemovedChannelsTab(
                                    removedChannels = removedChannels,
                                    onRestoreChannel = onToggleChannelHidden,
                                    onRestoreAllChannels = onRestoreAllChannels
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Footer Row: Quick Status & Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val activeCountriesCount = orderedCountries.count { it.code !in disabledCountries }
                        Text(
                            text = "$activeCountriesCount/${orderedCountries.size} Länder aktiv • ${allChannels.size - removedChannels.size} Sender sichtbar",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        Button(
                            onClick = onDismissRequest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("done_country_selector_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Fertig", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    }

    // Modal to add a new custom channel
    if (showAddChannelDialog) {
        AddCustomChannelDialog(
            defaultCountryCode = focusedCountryCode ?: "US",
            availableCountries = orderedCountries,
            onAddChannel = { name, url, code, group ->
                onAddCustomChannel(name, url, code, group)
                showAddChannelDialog = false
            },
            onDismiss = { showAddChannelDialog = false }
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeColor: Color? = null,
    testTag: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (isSelected) GoldPrimary else if (isFocused) GoldPrimary.copy(alpha = 0.25f) else Color.Transparent)
    val textColor = if (isSelected) Color.Black else if (isFocused) GoldLight else TextSecondary

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) GoldLight else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeColor != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
            }
        }
    }
}

@Composable
private fun CountriesListContent(
    countries: List<CountryInfo>,
    channelsByCountry: Map<String, List<ChannelItem>>,
    disabledCountries: Set<String>,
    hiddenChannelIds: Set<String>,
    selectedCategory: String,
    countrySummary: com.example.data.model.CountrySummary = com.example.data.model.CountrySummary.EMPTY,
    searchQuery: String,
    onSelectCountry: (String) -> Unit,
    onToggleCountry: (String) -> Unit,
    onManageCountryChannels: (String) -> Unit,
    isLandscape: Boolean = false
) {
    val filteredCountries = remember(countries, searchQuery) {
        val baseList = if (searchQuery.isBlank()) countries
        else {
            val q = searchQuery.trim().lowercase()
            countries.filter {
                it.code.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.flag.contains(q)
            }
        }
        baseList.distinctBy { it.code.trim().uppercase() }
    }

    if (isLandscape) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 270.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(
                items = filteredCountries,
                key = { index, country -> "${country.code.trim().uppercase()}_$index" }
            ) { _, country ->
                val codeUpper = country.code.trim().uppercase()
                val channels = channelsByCountry[codeUpper] ?: emptyList()
                val totalCount = countrySummary.getCountForCode(codeUpper).takeIf { it > 0 } ?: channels.size
                val removedCount = channels.count { it.id in hiddenChannelIds }
                val isEnabled = codeUpper !in disabledCountries
                val isCurrentlySelected = selectedCategory.trim().equals(codeUpper, ignoreCase = true) ||
                        (codeUpper == "DE" && selectedCategory in listOf("DE", "DE: GERMANY", "DE: DEUTSCHLAND", "DEUTSCHLAND", "GERMANY")) ||
                        (codeUpper == "AT" && selectedCategory in listOf("AT", "AT: AUSTRIA", "AT: ÖSTERREICH", "ÖSTERREICH", "AUSTRIA")) ||
                        (codeUpper == "CH" && selectedCategory in listOf("CH", "CH: SWITZERLAND", "CH: SCHWEIZ", "SCHWEIZ", "SWITZERLAND")) ||
                        (codeUpper == "US" && selectedCategory in listOf("US", "US: USA", "USA")) ||
                        (codeUpper == "UK" && selectedCategory in listOf("UK", "UK: GB", "GB"))

                CountryRowCard(
                    country = country,
                    channelCount = totalCount,
                    removedCount = removedCount,
                    isEnabled = isEnabled,
                    isSelected = isCurrentlySelected,
                    onSelect = {
                        if (!isEnabled) {
                            onToggleCountry(country.code)
                        }
                        onSelectCountry(country.code)
                    },
                    onToggleEnabled = { onToggleCountry(country.code) },
                    onManageChannels = { onManageCountryChannels(country.code) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = filteredCountries,
                key = { index, country -> "${country.code.trim().uppercase()}_$index" }
            ) { _, country ->
                val codeUpper = country.code.trim().uppercase()
                val channels = channelsByCountry[codeUpper] ?: emptyList()
                val totalCount = countrySummary.getCountForCode(codeUpper).takeIf { it > 0 } ?: channels.size
                val removedCount = channels.count { it.id in hiddenChannelIds }
                val isEnabled = codeUpper !in disabledCountries
                val isCurrentlySelected = selectedCategory.trim().equals(codeUpper, ignoreCase = true) ||
                        (codeUpper == "DE" && selectedCategory in listOf("DE", "DE: GERMANY", "DE: DEUTSCHLAND", "DEUTSCHLAND", "GERMANY")) ||
                        (codeUpper == "AT" && selectedCategory in listOf("AT", "AT: AUSTRIA", "AT: ÖSTERREICH", "ÖSTERREICH", "AUSTRIA")) ||
                        (codeUpper == "CH" && selectedCategory in listOf("CH", "CH: SWITZERLAND", "CH: SCHWEIZ", "SCHWEIZ", "SWITZERLAND")) ||
                        (codeUpper == "US" && selectedCategory in listOf("US", "US: USA", "USA")) ||
                        (codeUpper == "UK" && selectedCategory in listOf("UK", "UK: GB", "GB"))

                CountryRowCard(
                    country = country,
                    channelCount = totalCount,
                    removedCount = removedCount,
                    isEnabled = isEnabled,
                    isSelected = isCurrentlySelected,
                    onSelect = {
                        if (!isEnabled) {
                            onToggleCountry(country.code)
                        }
                        onSelectCountry(country.code)
                    },
                    onToggleEnabled = { onToggleCountry(country.code) },
                    onManageChannels = { onManageCountryChannels(country.code) }
                )
            }
        }
    }
}

@Composable
private fun CountriesOverviewTab(
    countries: List<CountryInfo>,
    channelsByCountry: Map<String, List<ChannelItem>>,
    disabledCountries: Set<String>,
    hiddenChannelIds: Set<String>,
    selectedCategory: String,
    countrySummary: com.example.data.model.CountrySummary = com.example.data.model.CountrySummary.EMPTY,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectCountry: (String) -> Unit,
    onToggleCountry: (String) -> Unit,
    onManageCountryChannels: (String) -> Unit,
    onEnableAllCountries: () -> Unit,
    onDisableAllCountries: () -> Unit,
    onResetAll: () -> Unit,
    onOpenAddChannel: () -> Unit
) {
    val filteredCountries = remember(countries, searchQuery) {
        val baseList = if (searchQuery.isBlank()) countries
        else {
            val q = searchQuery.trim().lowercase()
            countries.filter {
                it.code.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.flag.contains(q)
            }
        }
        baseList.distinctBy { it.code.trim().uppercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Priority Country Summary Bar: DE:, AT:, CH:, ADULT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedCategory.startsWith("DE"),
                onClick = { onSelectCountry("DE") },
                label = { Text("🇩🇪 DE: (${countrySummary.deCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedCategory.startsWith("AT"),
                onClick = { onSelectCountry("AT") },
                label = { Text("🇦🇹 AT: (${countrySummary.atCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedCategory.startsWith("CH"),
                onClick = { onSelectCountry("CH") },
                label = { Text("🇨🇭 CH: (${countrySummary.chCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            val adultCount = channelsByCountry["ADULT"]?.size ?: 0
            FilterChip(
                selected = selectedCategory.startsWith("ADULT"),
                onClick = { onSelectCountry("ADULT") },
                label = { Text("🔞 ADULT ($adultCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Action Buttons Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onEnableAllCountries,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_enable_all_countries"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f))
            ) {
                Text(text = "✓ Alle an", fontSize = 11.sp, color = AccentEmerald, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onDisableAllCountries,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_disable_all_countries"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f))
            ) {
                Text(text = "✕ Alle aus", fontSize = 11.sp, color = AccentRose, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_reset_countries"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.4f))
            ) {
                Text(text = "↺ Reset", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onOpenAddChannel,
                modifier = Modifier
                    .weight(1.2f)
                    .height(34.dp)
                    .testTag("btn_add_custom_channel"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
            ) {
                Text(text = "+ Kanal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar for Countries
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search_country"),
            placeholder = { Text("Land suchen (z.B. US, UK, DE, Türkei...)", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Löschen", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Country List
        CountriesListContent(
            countries = filteredCountries,
            channelsByCountry = channelsByCountry,
            disabledCountries = disabledCountries,
            hiddenChannelIds = hiddenChannelIds,
            selectedCategory = selectedCategory,
            countrySummary = countrySummary,
            searchQuery = "",
            onSelectCountry = onSelectCountry,
            onToggleCountry = onToggleCountry,
            onManageCountryChannels = onManageCountryChannels,
            isLandscape = false
        )
    }
}

@Composable
private fun CountryRowCard(
    country: CountryInfo,
    channelCount: Int,
    removedCount: Int,
    isEnabled: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onManageChannels: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }
    var isEditFocused by remember { mutableStateOf(false) }
    var isSelectFocused by remember { mutableStateOf(false) }

    val borderColor = if (isCardFocused) GoldLight else if (isSelected) GoldPrimary else if (isEnabled) BorderGlow.copy(alpha = 0.25f) else AccentRose.copy(alpha = 0.35f)
    val cardBg = if (isCardFocused) GoldPrimary.copy(alpha = 0.22f) else if (isSelected) GoldPrimary.copy(alpha = 0.12f) else if (isEnabled) DarkSurface else DarkSurface.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isCardFocused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .border(if (isCardFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .testTag("country_row_${country.code}"),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Flag + Name + Counts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Flag badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) DarkCardBackground else Color.Gray.copy(alpha = 0.2f))
                        .border(1.dp, if (isEnabled) GoldPrimary.copy(alpha = 0.4f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = country.flag, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = country.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) (if (isSelected || isCardFocused) GoldLight else TextPrimary) else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (country.rank <= 4) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldPrimary.copy(alpha = 0.25f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(text = "PRIORITÄT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = GoldLight)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusLabel = when {
                            channelCount > 0 -> "✓ $channelCount Einträge synchronisiert"
                            isEnabled -> "⏳ Wird bei Auswahl synchronisiert"
                            else -> "☁️ Bei Auswahl synchronisieren"
                        }
                        val statusColor = when {
                            channelCount > 0 -> AccentEmerald
                            isEnabled -> AccentCyan
                            else -> TextSecondary
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            color = statusColor
                        )
                        if (removedCount > 0) {
                            Text(
                                text = " • $removedCount entfernt",
                                fontSize = 11.sp,
                                color = AccentAmber
                            )
                        }
                    }
                }
            }

            // Right: Actions (Manage channels button, Select button, Enable/Disable switch)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Kanäle verwalten button
                IconButton(
                    onClick = onManageChannels,
                    modifier = Modifier
                        .size(32.dp)
                        .onFocusChanged { isEditFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(if (isEditFocused) AccentCyan.copy(alpha = 0.35f) else DarkCardBackground)
                        .border(if (isEditFocused) 1.5.dp else 0.dp, AccentCyan, CircleShape)
                        .testTag("manage_channels_${country.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Kanäle verwalten",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Auswählen Button
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .height(30.dp)
                        .onFocusChanged { isSelectFocused = it.isFocused }
                        .border(if (isSelectFocused) 1.5.dp else 0.dp, GoldLight, RoundedCornerShape(8.dp))
                        .testTag("select_country_${country.code}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) GoldPrimary else DarkCardBackground,
                        contentColor = if (isSelected) Color.Black else TextPrimary
                    )
                ) {
                    Text(
                        text = if (isSelected) "Aktiv" else "Wählen",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Toggle Country Switch (Hinzufügen / Entfernen)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("switch_country_${country.code}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldLight,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = DarkCardBackground
                    )
                )
            }
        }
    }
}

@Composable
private fun ChannelsManagementTab(
    allChannels: List<ChannelItem>,
    countries: List<CountryInfo>,
    selectedCountryCode: String?,
    onSelectFocusedCountry: (String?) -> Unit,
    hiddenChannelIds: Set<String>,
    disabledCountries: Set<String>,
    onToggleChannelHidden: (String) -> Unit,
    onRemoveAllInCountry: (String) -> Unit,
    onRestoreAllInCountry: (String) -> Unit,
    onOpenAddChannel: () -> Unit,
    isLandscape: Boolean = false
) {
    var channelSearchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, ACTIVE, HIDDEN

    val effectiveCountryCode = selectedCountryCode ?: "US"

    // Channels of the focused country, strictly sorted by canonical rank, quality, and clean title
    val countryChannels = remember(allChannels, effectiveCountryCode) {
        val upper = effectiveCountryCode.uppercase()
        val raw = allChannels.filter {
            it.normalizedCountryCode.uppercase() == upper ||
            ContentClassifier.detectCountryForGroup(it.groupTitle) == upper
        }
        com.example.ui.viewmodel.ChannelFilterEngine.filterAndSortLiveChannels(
            allChannels = raw,
            searchQuery = "",
            selectedCategory = upper,
            sortMode = com.example.data.model.ChannelSortMode.DACH_CANONICAL
        )
    }

    val filteredChannels = remember(countryChannels, channelSearchQuery, filterMode, hiddenChannelIds) {
        var list = countryChannels
        if (channelSearchQuery.isNotBlank()) {
            val q = channelSearchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.groupTitle.lowercase().contains(q)
            }
        }
        when (filterMode) {
            "ACTIVE" -> list.filter { it.id !in hiddenChannelIds }
            "HIDDEN" -> list.filter { it.id in hiddenChannelIds }
            else -> list
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isLandscape) {
            val uniqueCountries = remember(countries) {
                countries.distinctBy { it.code.trim().uppercase() }
            }

            // Country Selector Pills (horizontal scroll)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = uniqueCountries,
                    key = { index, c -> "${c.code.trim().uppercase()}_$index" }
                ) { _, c ->
                    val isSelected = c.code.trim().equals(effectiveCountryCode.trim(), ignoreCase = true)
                    val count = allChannels.count { it.normalizedCountryCode.trim().equals(c.code.trim(), ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) GoldPrimary else DarkSurface)
                            .border(1.dp, if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onSelectFocusedCountry(c.code) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("pill_country_${c.code}")
                    ) {
                        Text(
                            text = "${c.flag} ${c.code} ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Country Batch Actions Header
        val currentCountryInfo = remember(effectiveCountryCode) {
            ContentClassifier.getCountryInfo(effectiveCountryCode)
        }
        val hiddenInThisCountry = countryChannels.count { it.id in hiddenChannelIds }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${currentCountryInfo.flag} ${currentCountryInfo.name}: ${countryChannels.size} Sender",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Batch Remove
                OutlinedButton(
                    onClick = { onRemoveAllInCountry(effectiveCountryCode) },
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f))
                ) {
                    Text("Alle entfernen", fontSize = 10.sp, color = AccentRose)
                }

                // Batch Restore
                OutlinedButton(
                    onClick = { onRestoreAllInCountry(effectiveCountryCode) },
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f))
                ) {
                    Text("Alle hinzufügen", fontSize = 10.sp, color = AccentEmerald)
                }

                // Add Custom Channel
                Button(
                    onClick = onOpenAddChannel,
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("+ Neu", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search + Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = channelSearchQuery,
                onValueChange = { channelSearchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("input_search_channels_in_country"),
                placeholder = { Text("Sender filtern...", fontSize = 11.sp, color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Filter Chips (Alle, Aktiv, Entfernt)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("Alle (${countryChannels.size})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = filterMode == "ACTIVE",
                    onClick = { filterMode = "ACTIVE" },
                    label = { Text("Aktiv", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = filterMode == "HIDDEN",
                    onClick = { filterMode = "HIDDEN" },
                    label = { Text("Entfernt ($hiddenInThisCountry)", fontSize = 10.sp, color = if (hiddenInThisCountry > 0) AccentRose else TextSecondary) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Channel Items List
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📡", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (countryChannels.isEmpty()) "Keine Sender für $effectiveCountryCode gefunden." else "Keine passenden Sender für diesen Filter.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenAddChannel,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "+ Sender zu $effectiveCountryCode hinzufügen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            if (isLandscape) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = filteredChannels,
                        key = { index, ch -> "${ch.id.ifBlank { "ch" }}_$index" }
                    ) { _, ch ->
                        val isRemoved = ch.id in hiddenChannelIds
                        ChannelManagementItem(
                            channel = ch,
                            isRemoved = isRemoved,
                            onToggleHidden = { onToggleChannelHidden(ch.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = filteredChannels,
                        key = { index, ch -> "${ch.id.ifBlank { "ch" }}_$index" }
                    ) { _, ch ->
                        val isRemoved = ch.id in hiddenChannelIds
                        ChannelManagementItem(
                            channel = ch,
                            isRemoved = isRemoved,
                            onToggleHidden = { onToggleChannelHidden(ch.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelManagementItem(
    channel: ChannelItem,
    isRemoved: Boolean,
    onToggleHidden: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isBtnFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) GoldLight else if (isRemoved) AccentRose.copy(alpha = 0.3f) else BorderGlow.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            )
            .testTag("channel_item_${channel.id}"),
        color = if (isFocused) GoldPrimary.copy(alpha = 0.15f) else if (isRemoved) DarkSurface.copy(alpha = 0.4f) else DarkSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRemoved) Color.Gray.copy(alpha = 0.15f) else GoldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isRemoved) "✕" else "📺", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = channel.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRemoved) TextMuted else (if (isFocused) GoldLight else TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.groupTitle.ifBlank { "Allgemein" },
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.resolutionBadge.isNotBlank()) {
                            Text(text = " • ${channel.resolutionBadge}", fontSize = 9.sp, color = GoldLight)
                        }
                    }
                }
            }

            // Action: + Hinzufügen (if removed) or − Entfernen (if active)
            if (isRemoved) {
                OutlinedButton(
                    onClick = onToggleHidden,
                    modifier = Modifier
                        .height(28.dp)
                        .onFocusChanged { isBtnFocused = it.isFocused }
                        .testTag("btn_restore_channel_${channel.id}"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(if (isBtnFocused) 1.5.dp else 1.dp, AccentEmerald)
                ) {
                    Text(text = "+ Hinzufügen", fontSize = 10.sp, color = AccentEmerald, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleHidden,
                    modifier = Modifier
                        .height(28.dp)
                        .onFocusChanged { isBtnFocused = it.isFocused }
                        .testTag("btn_remove_channel_${channel.id}"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(if (isBtnFocused) 1.5.dp else 1.dp, AccentRose.copy(alpha = 0.7f))
                ) {
                    Text(text = "− Entfernen", fontSize = 10.sp, color = AccentRose, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RemovedChannelsTab(
    removedChannels: List<ChannelItem>,
    onRestoreChannel: (String) -> Unit,
    onRestoreAllChannels: () -> Unit,
    isLandscape: Boolean = false
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${removedChannels.size} ausgeblendete Sender",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentRose
            )

            if (removedChannels.isNotEmpty()) {
                Button(
                    onClick = onRestoreAllChannels,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_restore_all_removed_channels"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = Color.Black)
                ) {
                    Text(text = "Alle wiederherstellen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (removedChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✨", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Keine Sender entfernt.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Alle Kanäle deiner Playlists sind aktiv und sichtbar!",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (isLandscape) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = removedChannels,
                        key = { index, ch -> "${ch.id.ifBlank { "removed" }}_$index" }
                    ) { _, ch ->
                        ChannelManagementItem(
                            channel = ch,
                            isRemoved = true,
                            onToggleHidden = { onRestoreChannel(ch.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = removedChannels,
                        key = { index, ch -> "${ch.id.ifBlank { "removed" }}_$index" }
                    ) { _, ch ->
                        ChannelManagementItem(
                            channel = ch,
                            isRemoved = true,
                            onToggleHidden = { onRestoreChannel(ch.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomChannelDialog(
    defaultCountryCode: String,
    availableCountries: List<CountryInfo>,
    onAddChannel: (name: String, streamUrl: String, countryCode: String, groupTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(defaultCountryCode.uppercase()) }
    var groupTitle by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, AccentCyan, RoundedCornerShape(18.dp))
                .testTag("add_custom_channel_modal"),
            color = DarkNavyBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Neuen Kanal hinzufügen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
                Text(
                    text = "Füge einen eigenen TV-Sender zu einem Land hinzu",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kanalname (z.B. CNN International HD)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_new_channel_name"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream-URL (m3u8, mp4, ts)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_new_channel_url"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Land zuweisen:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                val uniqueAvailable = remember(availableCountries) {
                    availableCountries.distinctBy { it.code.trim().uppercase() }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = uniqueAvailable,
                        key = { index, c -> "${c.code.trim().uppercase()}_$index" }
                    ) { _, c ->
                        val isSel = c.code.trim().equals(selectedCountry.trim(), ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) AccentCyan else DarkSurface)
                                .clickable { selectedCountry = c.code }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${c.flag} ${c.code}",
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.Black else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    label = { Text("Gruppe / Kategorie (z.B. Nachrichten, Sport)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_new_channel_group"),
                    shape = RoundedCornerShape(10.dp)
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Bitte Name und Stream-URL ausfüllen!", fontSize = 11.sp, color = AccentRose)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || streamUrl.isBlank()) {
                                showError = true
                            } else {
                                onAddChannel(name, streamUrl, selectedCountry, groupTitle)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_submit_add_channel")
                    ) {
                        Text("Hinzufügen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
