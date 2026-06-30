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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = CallBlockerApp.instance
    val scope = rememberCoroutineScope()

    val configList by app.repository.globalConfig.collectAsState(initial = emptyList())
    val currentConfig = configList.firstOrNull() ?: SimConfig(0)

    var intervalMinutes by remember(currentConfig) { mutableFloatStateOf(currentConfig.intervalMinutes.toFloat()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title_global),
            style = MaterialTheme.typography.titleLarge
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_interval_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_interval_desc_global, intervalMinutes.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray600
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = intervalMinutes,
                    onValueChange = { intervalMinutes = it },
                    onValueChangeFinished = {
                        scope.launch {
                            app.repository.saveConfig(currentConfig.copy(intervalMinutes = intervalMinutes.toInt()))
                        }
                    },
                    valueRange = 1f..120f,
                    steps = 118,
                    colors = SliderDefaults.colors(thumbColor = Blue700, activeTrackColor = Blue500)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.settings_interval_min),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400
                    )
                    Text(
                        stringResource(R.string.settings_interval_value, intervalMinutes.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Blue700,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.settings_interval_max),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_system_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { PermissionUtils.openCallScreeningSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue700)
                ) {
                    Text(stringResource(R.string.settings_set_default))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_set_default_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray600
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_permissions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { PermissionUtils.openAppSettings(context) }) {
                    Text(stringResource(R.string.settings_open_permissions))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_data_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)
                ) {
                    Text(stringResource(R.string.settings_clear_records_global))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600
                )
                Text(
                    text = stringResource(R.string.settings_app_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray400
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.dialog_clear_records)) },
            text = { Text(stringResource(R.string.confirm_clear_records_global)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.repository.clearBlockedLogs() }
                    showClearConfirm = false
                }) { Text(stringResource(R.string.action_clear), color = Red500) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
