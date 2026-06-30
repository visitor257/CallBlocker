package com.callblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callblocker.app.BuildConfig
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.R
import com.callblocker.app.data.entity.SimConfig
import com.callblocker.app.ui.theme.*
import com.callblocker.app.util.PermissionUtils
import com.callblocker.app.util.SimUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = CallBlockerApp.instance
    val scope = rememberCoroutineScope()
    val simConfigs by app.repository.allSimConfigs.collectAsState(initial = emptyList())
    val sims = remember(simConfigs) {
        val detected = SimUtils.getActiveSims(context)
        val configMap = simConfigs.associateBy { it.simSlot }
        // System carrier name takes priority; config name only used when system returns nothing
        detected.map { info ->
            val cfg = configMap[info.simSlot]
            if (cfg != null && info.displayName.isEmpty()) {
                info.copy(displayName = cfg.displayName)
            } else info
        }.ifEmpty { SimUtils.getDefaultSims(context) }
    }
    var selectedSim by remember { mutableIntStateOf(0) }
    val currentConfig = simConfigs.find { it.simSlot == selectedSim } ?: SimConfig(selectedSim)
    val simLabel = sims.find { it.simSlot == selectedSim }?.displayName ?: stringResource(R.string.sim_card_format, selectedSim + 1)
    val otherSims = sims.filter { it.simSlot != selectedSim }
    var copyFromSlot by remember(selectedSim) { mutableStateOf(otherSims.firstOrNull()?.simSlot ?: -1) }
    var copyDropdownExpanded by remember { mutableStateOf(false) }

    var intervalMinutes by remember(currentConfig) { mutableFloatStateOf(currentConfig.intervalMinutes.toFloat()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCopyConfirm by remember { mutableStateOf(false) }
    var configCopied by remember { mutableStateOf(false) }
    var copyMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SimSelector(sims = sims, selectedSim = selectedSim, onSelect = { selectedSim = it })
        Spacer(modifier = Modifier.height(4.dp))

        Text(text = stringResource(R.string.settings_title, simLabel), style = MaterialTheme.typography.titleLarge)

        if (sims.size > 1) {
            val copyFromSim = otherSims.find { it.simSlot == copyFromSlot }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.settings_copy_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_copy_desc, simLabel),
                        style = MaterialTheme.typography.bodySmall, color = Gray600
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = copyDropdownExpanded,
                        onExpandedChange = { copyDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = copyFromSim?.displayName ?: stringResource(R.string.settings_copy_placeholder),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_copy_source_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = copyDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = copyDropdownExpanded,
                            onDismissRequest = { copyDropdownExpanded = false }
                        ) {
                            otherSims.forEach { info ->
                                DropdownMenuItem(
                                    text = { Text(info.displayName) },
                                    onClick = {
                                        copyFromSlot = info.simSlot
                                        copyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCopyConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = copyFromSlot >= 0
                    ) {
                        Text(stringResource(R.string.settings_copy_button, copyFromSim?.displayName ?: ""))
                    }
                    if (configCopied) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = copyMessage, style = MaterialTheme.typography.labelSmall, color = Green500)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.settings_interval_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_interval_desc, intervalMinutes.toInt()),
                    style = MaterialTheme.typography.bodySmall, color = Gray600
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = intervalMinutes, onValueChange = { intervalMinutes = it },
                    onValueChangeFinished = {
                        scope.launch {
                            app.repository.saveSimConfig(currentConfig.copy(intervalMinutes = intervalMinutes.toInt()))
                        }
                    },
                    valueRange = 1f..120f, steps = 118,
                    colors = SliderDefaults.colors(thumbColor = Blue700, activeTrackColor = Blue500)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_interval_min), style = MaterialTheme.typography.labelSmall, color = Gray400)
                    Text(stringResource(R.string.settings_interval_value, intervalMinutes.toInt()), style = MaterialTheme.typography.labelSmall, color = Blue700, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_interval_max), style = MaterialTheme.typography.labelSmall, color = Gray400)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.settings_system_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { PermissionUtils.openCallScreeningSettings(context) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue700)) {
                    Text(stringResource(R.string.settings_set_default))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.settings_set_default_desc), style = MaterialTheme.typography.bodySmall, color = Gray600)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.settings_permissions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { PermissionUtils.openAppSettings(context) }) { Text(stringResource(R.string.settings_open_permissions)) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.settings_data_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)) {
                    Text(stringResource(R.string.settings_clear_records, simLabel))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = Gray600)
                Text(text = stringResource(R.string.settings_app_subtitle), style = MaterialTheme.typography.labelSmall, color = Gray400)
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.dialog_clear_records)) },
            text = { Text(stringResource(R.string.confirm_clear_records, simLabel)) },
            confirmButton = { TextButton(onClick = {
                scope.launch { app.repository.clearBlockedLogsBySim(selectedSim) }
                showClearConfirm = false
            }) { Text(stringResource(R.string.action_clear), color = Red500) }},
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showCopyConfirm) {
        val copyFromSim = otherSims.find { it.simSlot == copyFromSlot }
        AlertDialog(
            onDismissRequest = { showCopyConfirm = false },
            title = { Text(stringResource(R.string.dialog_copy_config)) },
            text = {
                Text(stringResource(R.string.confirm_copy_config_text, copyFromSim?.displayName ?: "", simLabel))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.repository.copySimConfig(copyFromSlot, selectedSim)
                        val count = app.repository.copyWhitelist(copyFromSlot, selectedSim)
                        val blackCount = app.repository.copyBlacklist(copyFromSlot, selectedSim)
                        intervalMinutes = app.repository.getSimConfig(selectedSim).intervalMinutes.toFloat()
                        copyMessage = context.getString(R.string.confirm_copy_success, count, blackCount)
                        configCopied = true
                    }
                    showCopyConfirm = false
                }) { Text(stringResource(R.string.action_confirm_copy), color = Blue700) }
            },
            dismissButton = { TextButton(onClick = { showCopyConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
