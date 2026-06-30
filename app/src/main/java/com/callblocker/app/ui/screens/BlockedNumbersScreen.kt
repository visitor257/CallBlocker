package com.callblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import com.callblocker.app.R
import com.callblocker.app.data.entity.BlockedNumber
import com.callblocker.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(modifier: Modifier = Modifier) {
    val app = CallBlockerApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val blacklist by app.repository.allBlacklist.collectAsState(initial = emptyList())
    val whitelist by app.repository.allWhitelist.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<BlockedNumber?>(null) }
    var showMoveToWhitelistConfirm by remember { mutableStateOf<BlockedNumber?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.blacklist_title_global, blacklist.size),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (blacklist.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Gray50)
                    ) {
                        Text(
                            text = stringResource(R.string.blacklist_empty_global),
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Gray400
                        )
                    }
                }
            } else {
                items(blacklist, key = { it.id }) { entry ->
                    val isInWhitelist = whitelist.any { it.phoneNumber == entry.phoneNumber }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayName ?: entry.phoneNumber,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (entry.displayName != null) {
                                    Text(
                                        text = entry.phoneNumber,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Gray600
                                    )
                                }
                                Text(
                                    text = if (entry.source == "CONTACT") {
                                        stringResource(R.string.blacklist_source_contact)
                                    } else {
                                        stringResource(R.string.blacklist_source_manual)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gray400
                                )
                                if (isInWhitelist) {
                                    Text(
                                        text = stringResource(R.string.blacklist_also_in_whitelist),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Orange500
                                    )
                                }
                            }
                            if (!isInWhitelist) {
                                IconButton(onClick = { showMoveToWhitelistConfirm = entry }) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.cd_move_whitelist),
                                        tint = Green500
                                    )
                                }
                            }
                            IconButton(onClick = { showDeleteConfirm = entry }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_remove_blacklist),
                                    tint = Red500
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        )

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Red500
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.cd_add),
                tint = OnPrimary
            )
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
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.label_phone_number)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.label_nickname_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { app.repository.addToBlacklist(number, name) }
                        showAddDialog = false
                    },
                    enabled = number.isNotBlank()
                ) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    showDeleteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_remove_blacklist)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_remove_blacklist_global,
                        entry.displayName ?: entry.phoneNumber
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.repository.removeFromBlacklist(entry.id) }
                    showDeleteConfirm = null
                }) { Text(stringResource(R.string.action_remove), color = Red500) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    showMoveToWhitelistConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showMoveToWhitelistConfirm = null },
            title = { Text(stringResource(R.string.dialog_move_whitelist_from_blacklist)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_move_to_whitelist_global,
                        entry.displayName ?: entry.phoneNumber
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.repository.addToWhitelist(entry.phoneNumber, entry.displayName, entry.source)
                        app.repository.removeFromBlacklist(entry.id)
                    }
                    showMoveToWhitelistConfirm = null
                }) {
                    Text(
                        stringResource(R.string.action_move_whitelist_from_blacklist),
                        color = Green500
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveToWhitelistConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
