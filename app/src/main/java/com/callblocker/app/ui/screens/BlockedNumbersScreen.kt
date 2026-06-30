package com.callblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callblocker.app.CallBlockerApp
import androidx.compose.ui.res.stringResource
import com.callblocker.app.R
import com.callblocker.app.data.entity.BlockedNumber
import com.callblocker.app.data.entity.SimConfig
import com.callblocker.app.ui.theme.*
import com.callblocker.app.util.SimUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(modifier: Modifier = Modifier) {
    val app = CallBlockerApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simConfigs by app.repository.allSimConfigs.collectAsState(initial = emptyList())
    val sims = remember(simConfigs) {
        val detected = SimUtils.getActiveSims(context)
        val configMap = simConfigs.associateBy { it.simSlot }
        detected.map { info ->
            configMap[info.simSlot]?.let { cfg ->
                info.copy(displayName = cfg.displayName)
            } ?: info
        }.ifEmpty { SimUtils.getDefaultSims(context) }
    }
    var selectedSim by remember { mutableIntStateOf(0) }

    val blacklist by app.repository.getBlacklistBySim(selectedSim).collectAsState(initial = emptyList())
    val whitelist by app.repository.getWhitelistBySim(selectedSim).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<BlockedNumber?>(null) }
    var showMoveToWhitelistConfirm by remember { mutableStateOf<BlockedNumber?>(null) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Red500) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add), tint = OnPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SimSelector(sims = sims, selectedSim = selectedSim, onSelect = { selectedSim = it })
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                val simLabel = sims.find { it.simSlot == selectedSim }?.displayName ?: stringResource(R.string.sim_card_format, selectedSim + 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.blacklist_title, simLabel, blacklist.size), style = MaterialTheme.typography.titleLarge)
                }
            }

            if (blacklist.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gray50)) {
                        Text(
                            text = stringResource(R.string.blacklist_empty, sims.find { it.simSlot == selectedSim }?.displayName ?: ""),
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = Gray400
                        )
                    }
                }
            } else {
                items(blacklist, key = { it.id }) { entry ->
                    val isInWhitelist = whitelist.any { it.phoneNumber == entry.phoneNumber && it.simSlot == entry.simSlot }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.displayName ?: entry.phoneNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (entry.displayName != null) {
                                    Text(text = entry.phoneNumber, style = MaterialTheme.typography.labelSmall, color = Gray600)
                                }
                                Text(
                                    text = if (entry.source == "CONTACT") stringResource(R.string.blacklist_source_contact) else stringResource(R.string.blacklist_source_manual),
                                    style = MaterialTheme.typography.labelSmall, color = Gray400
                                )
                                if (isInWhitelist) {
                                    Text(text = stringResource(R.string.blacklist_also_in_whitelist), style = MaterialTheme.typography.labelSmall, color = Orange500)
                                }
                            }
                            if (!isInWhitelist) {
                                IconButton(onClick = { showMoveToWhitelistConfirm = entry }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.cd_move_whitelist), tint = Green500)
                                }
                            }
                            IconButton(onClick = { showDeleteConfirm = entry }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove_blacklist), tint = Red500)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var number by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.dialog_add_blacklist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text(stringResource(R.string.label_phone_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_nickname_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { app.repository.addToBlacklist(number, name, "MANUAL", selectedSim) }
                showAddDialog = false
            }, enabled = number.isNotBlank()) { Text(stringResource(R.string.action_add)) }},
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    showDeleteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_remove_blacklist)) },
            text = { Text(stringResource(R.string.confirm_remove_blacklist, entry.displayName ?: entry.phoneNumber)) },
            confirmButton = { TextButton(onClick = {
                scope.launch { app.repository.removeFromBlacklist(entry.id) }
                showDeleteConfirm = null
            }) { Text(stringResource(R.string.action_remove), color = Red500) }},
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    showMoveToWhitelistConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showMoveToWhitelistConfirm = null },
            title = { Text(stringResource(R.string.dialog_move_whitelist_from_blacklist)) },
            text = { Text(stringResource(R.string.confirm_move_to_whitelist, entry.displayName ?: entry.phoneNumber)) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    app.repository.addToWhitelist(entry.phoneNumber, entry.displayName, entry.source, entry.simSlot)
                    app.repository.removeFromBlacklist(entry.id)
                }
                showMoveToWhitelistConfirm = null
            }) { Text(stringResource(R.string.action_move_whitelist_from_blacklist), color = Green500) }},
            dismissButton = { TextButton(onClick = { showMoveToWhitelistConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
