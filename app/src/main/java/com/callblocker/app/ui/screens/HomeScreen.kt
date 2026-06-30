package com.callblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.callblocker.app.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.data.entity.BlockedCallRecord
import com.callblocker.app.data.entity.SimConfig
import com.callblocker.app.ui.theme.*
import com.callblocker.app.util.SimInfo
import com.callblocker.app.util.SimUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val app = CallBlockerApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val simConfigs by app.repository.allSimConfigs.collectAsState(initial = emptyList())
    val sims = remember(simConfigs) {
        val detected = SimUtils.getActiveSims(context)
        // Use system-detected carrier names; only apply custom config name if user edited it
        val configMap = simConfigs.associateBy { it.simSlot }
        val defaultLabel = { slot: Int -> context.getString(com.callblocker.app.R.string.sim_card_format, slot + 1) }
        detected.map { info ->
            val cfg = configMap[info.simSlot]
            if (cfg != null && cfg.displayName != defaultLabel(info.simSlot)) {
                info.copy(displayName = cfg.displayName)
            } else info
        }.ifEmpty { SimUtils.getDefaultSims(context) }
    }
    var selectedSim by remember { mutableIntStateOf(0) }

    // Per-SIM data
    val blockedCalls by app.repository.getBlockedCallsBySim(selectedSim).collectAsState(initial = emptyList())
    val whitelist by app.repository.getWhitelistBySim(selectedSim).collectAsState(initial = emptyList())
    val blacklist by app.repository.getBlacklistBySim(selectedSim).collectAsState(initial = emptyList())
    val config by app.repository.allSimConfigs.collectAsState(initial = emptyList())
    val currentConfig = config.find { it.simSlot == selectedSim } ?: SimConfig(selectedSim)
    var enabled by remember(selectedSim, currentConfig) { mutableStateOf(currentConfig.enabled) }

    LaunchedEffect(selectedSim) {
        val cfg = app.repository.getSimConfig(selectedSim)
        enabled = cfg.enabled
    }
    var showBlockedSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SimSelector(sims = sims, selectedSim = selectedSim, onSelect = { selectedSim = it })
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val simLabel = sims.find { it.simSlot == selectedSim }?.displayName ?: stringResource(R.string.sim_card_format, selectedSim + 1)
                    Text(
                        text = if (enabled) stringResource(R.string.home_service_running, simLabel) else stringResource(R.string.home_service_paused, simLabel),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (enabled) Green500 else Gray600
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_blocked_summary, blockedCalls.size, whitelist.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { v ->
                            enabled = v
                            scope.launch {
                                app.repository.saveSimConfig(currentConfig.copy(enabled = v))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Blue700)
                    )
                    Text(
                        text = if (enabled) stringResource(R.string.home_tap_pause) else stringResource(R.string.home_tap_enable),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Orange500.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.home_guide_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_guide_text),
                        style = MaterialTheme.typography.bodyMedium, color = Gray800
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showBlockedSheet = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_view_records, blockedCalls.size))
            }
        }
    }

    if (showBlockedSheet) {
        val whitelistNumbers = remember(whitelist) { whitelist.map { it.phoneNumber }.toSet() }
        val blacklistNumbers = remember(blacklist) { blacklist.map { it.phoneNumber }.toSet() }
        val simLabel = sims.find { it.simSlot == selectedSim }?.displayName ?: stringResource(R.string.sim_card_format, selectedSim + 1)
        ModalBottomSheet(
            onDismissRequest = { showBlockedSheet = false },
            sheetState = sheetState
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(text = stringResource(R.string.home_blocked_records, simLabel), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (blockedCalls.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gray50)) {
                            Text(
                                text = stringResource(R.string.home_no_records),
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge, color = Gray400
                            )
                        }
                    }
                } else {
                    items(blockedCalls.take(50)) { call ->
                        BlockedCallItem(
                            call = call,
                            whitelistNumbers = whitelistNumbers,
                            blacklistNumbers = blacklistNumbers,
                            onAddToWhitelist = { number, name ->
                                scope.launch {
                                    app.repository.addToWhitelist(number, name, "MANUAL", selectedSim)
                                }
                            },
                            onAddToBlacklist = { number, name ->
                                scope.launch {
                                    app.repository.addToBlacklist(number, name, "MANUAL", selectedSim)
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSelector(sims: List<SimInfo>, selectedSim: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedInfo = sims.find { it.simSlot == selectedSim }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SimSelectorChip(selectedInfo = selectedInfo, onClick = { showSheet = true }, modifier = modifier)

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.sim_selector_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                sims.forEach { info ->
                    Surface(
                        onClick = {
                            onSelect(info.simSlot)
                            showSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (info.simSlot == selectedSim) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(info.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (info.simSlot == selectedSim) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimSelectorChip(selectedInfo: SimInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stringResource(R.string.sim_selector_label), style = MaterialTheme.typography.labelSmall, color = Gray400)
                Text(
                    text = selectedInfo?.displayName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sim_selector_switch), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_expand), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BlockedCallItem(
    call: BlockedCallRecord,
    whitelistNumbers: Set<String>,
    blacklistNumbers: Set<String>,
    onAddToWhitelist: (String, String?) -> Unit,
    onAddToBlacklist: (String, String?) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val isWhitelisted = remember(call.phoneNumber, whitelistNumbers) {
        whitelistNumbers.contains(call.phoneNumber)
    }
    val isBlacklisted = remember(call.phoneNumber, blacklistNumbers) {
        blacklistNumbers.contains(call.phoneNumber)
    }

    val cardColor = remember(isWhitelisted, isBlacklisted) {
        when {
            isWhitelisted -> Green500.copy(alpha = 0.1f)
            isBlacklisted -> Red500.copy(alpha = 0.15f)
            else -> Red100.copy(alpha = 0.5f)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = call.displayName ?: call.phoneNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (call.displayName != null) {
                    Text(text = call.phoneNumber, style = MaterialTheme.typography.labelSmall, color = Gray600)
                }
            }
            Text(
                text = dateFormat.format(Date(call.timestamp)),
                style = MaterialTheme.typography.labelSmall, color = Gray600,
                modifier = Modifier.padding(end = 8.dp)
            )
            if (isWhitelisted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_in_whitelist),
                    tint = Green500,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isBlacklisted) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = stringResource(R.string.cd_in_blacklist),
                    tint = Red500,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                IconButton(onClick = { onAddToBlacklist(call.phoneNumber, call.displayName) }) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = stringResource(R.string.cd_add_blacklist),
                        tint = Red500,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { onAddToWhitelist(call.phoneNumber, call.displayName) }) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = stringResource(R.string.cd_add_whitelist),
                        tint = Blue700,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

