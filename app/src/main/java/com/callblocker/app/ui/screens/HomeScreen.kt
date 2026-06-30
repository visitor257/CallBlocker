package com.callblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.R
import com.callblocker.app.data.entity.BlockedCallRecord
import com.callblocker.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val app = CallBlockerApp.instance
    val scope = rememberCoroutineScope()

    val blockedCalls by app.repository.allBlockedCalls.collectAsState(initial = emptyList())
    val whitelist by app.repository.allWhitelist.collectAsState(initial = emptyList())
    val blacklist by app.repository.allBlacklist.collectAsState(initial = emptyList())
    val configList by app.repository.globalConfig.collectAsState(initial = emptyList())
    val config = configList.firstOrNull() ?: remember { com.callblocker.app.data.entity.SimConfig(0) }
    var enabled by remember(config) { mutableStateOf(config.enabled) }

    LaunchedEffect(config) { enabled = config.enabled }

    var showBlockedSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (enabled) stringResource(R.string.home_service_running_global) else stringResource(R.string.home_service_paused_global),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (enabled) Green500 else Gray600
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_blocked_summary_global, blockedCalls.size, whitelist.size, blacklist.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { v ->
                            enabled = v
                            scope.launch {
                                app.repository.saveConfig(config.copy(enabled = v))
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
                    Text(
                        text = stringResource(R.string.home_guide_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_guide_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray800
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
                Text(stringResource(R.string.home_view_records_global, blockedCalls.size))
            }
        }
    }

    if (showBlockedSheet) {
        val whitelistNumbers = remember(whitelist) { whitelist.map { it.phoneNumber }.toSet() }
        val blacklistNumbers = remember(blacklist) { blacklist.map { it.phoneNumber }.toSet() }

        ModalBottomSheet(
            onDismissRequest = { showBlockedSheet = false },
            sheetState = sheetState
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.home_blocked_records_global),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (blockedCalls.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Gray50)
                        ) {
                            Text(
                                text = stringResource(R.string.home_no_records),
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Gray400
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
                                scope.launch { app.repository.addToWhitelist(number, name) }
                            },
                            onAddToBlacklist = { number, name ->
                                scope.launch { app.repository.addToBlacklist(number, name) }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.displayName ?: call.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (call.displayName != null) {
                    Text(
                        text = call.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray600
                    )
                }
            }
            Text(
                text = dateFormat.format(Date(call.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = Gray600,
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
