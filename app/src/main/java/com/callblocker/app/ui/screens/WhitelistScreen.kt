package com.callblocker.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.data.entity.BlockedNumber
import com.callblocker.app.data.entity.SimConfig
import androidx.compose.ui.res.stringResource
import com.callblocker.app.R
import com.callblocker.app.data.entity.WhitelistEntry
import com.callblocker.app.ui.theme.*
import com.callblocker.app.util.SimUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(modifier: Modifier = Modifier) {
    val app = CallBlockerApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simConfigs by app.repository.allSimConfigs.collectAsState(initial = emptyList())
    val sims = remember(simConfigs) {
        val detected = SimUtils.getActiveSims(context)
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

    val whitelist by app.repository.getWhitelistBySim(selectedSim).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<WhitelistEntry?>(null) }
    var showMoveToBlacklistConfirm by remember { mutableStateOf<WhitelistEntry?>(null) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
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
                    Text(text = stringResource(R.string.whitelist_title, simLabel, whitelist.size), style = MaterialTheme.typography.titleLarge)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val count = app.repository.importFromContacts(context, selectedSim)
                                snackbarMessage = if (count > 0) context.getString(R.string.whitelist_import_success, count, simLabel) else context.getString(R.string.whitelist_import_none)
                                snackbarHostState.showSnackbar(snackbarMessage)
                            }
                        }) {
                            Icon(Icons.Default.ImportContacts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.whitelist_import_contacts))
                        }
                    }
                }
            }

            if (whitelist.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gray50)) {
                        Text(
                            text = stringResource(R.string.whitelist_empty, sims.find { it.simSlot == selectedSim }?.displayName ?: ""),
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = Gray400
                        )
                    }
                }
            } else {
                items(whitelist, key = { it.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.displayName ?: entry.phoneNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (entry.displayName != null) {
                                    Text(text = entry.phoneNumber, style = MaterialTheme.typography.labelSmall, color = Gray600)
                                }
                                Text(
                                    text = if (entry.source == "CONTACT") stringResource(R.string.whitelist_source_contact) else stringResource(R.string.whitelist_source_manual),
                                    style = MaterialTheme.typography.labelSmall, color = if (entry.source == "CONTACT") Blue500 else Gray400
                                )
                            }
                            IconButton(onClick = { showMoveToBlacklistConfirm = entry }) {
                                Icon(Icons.Default.Block, contentDescription = stringResource(R.string.cd_move_blacklist), tint = Orange500)
                            }
                            IconButton(onClick = { showDeleteConfirm = entry }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = Red500)
                            }
                        }
                    }
                }
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        )

        // Floating action button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Blue700
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add), tint = OnPrimary)
        }
    }

    if (showAddDialog) {
        var number by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.dialog_add_whitelist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text(stringResource(R.string.label_phone_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_nickname_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { app.repository.addToWhitelist(number, name, "MANUAL", selectedSim) }
                showAddDialog = false
            }, enabled = number.isNotBlank()) { Text(stringResource(R.string.action_add)) }},
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    showDeleteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_remove_whitelist)) },
            text = { Text(stringResource(R.string.confirm_remove_whitelist, entry.displayName ?: entry.phoneNumber)) },
            confirmButton = { TextButton(onClick = {
                scope.launch { app.repository.removeFromWhitelist(entry.id) }
                showDeleteConfirm = null
            }) { Text(stringResource(R.string.action_remove), color = Red500) }},
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    showMoveToBlacklistConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showMoveToBlacklistConfirm = null },
            title = { Text(stringResource(R.string.dialog_move_blacklist)) },
            text = { Text(stringResource(R.string.confirm_move_to_blacklist, entry.displayName ?: entry.phoneNumber)) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    app.repository.addToBlacklist(entry.phoneNumber, entry.displayName, entry.source, entry.simSlot)
                    app.repository.removeFromWhitelist(entry.id)
                }
                showMoveToBlacklistConfirm = null
            }) { Text(stringResource(R.string.action_move_blacklist), color = Red500) }},
            dismissButton = { TextButton(onClick = { showMoveToBlacklistConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
