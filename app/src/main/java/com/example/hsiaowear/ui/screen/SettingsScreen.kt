package com.example.hsiaowear.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hsiaowear.R
import com.example.hsiaowear.ui.components.SettingsGroup
import com.example.hsiaowear.ui.components.SettingsRow
import com.example.hsiaowear.ui.theme.LocalAppShape
import com.example.hsiaowear.viewmodel.SettingsViewModel

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, paddingValues: androidx.compose.foundation.layout.PaddingValues) {
    val shapes = LocalAppShape.current
    val apiSettings by viewModel.apiSettings.collectAsState()
    val themeSettings by viewModel.themeSettings.collectAsState()
    val models by viewModel.models.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    var showAboutDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_detail_title)) },
            text = { Text(stringResource(R.string.about_detail_body)) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.error_dismiss))
                }
            },
            shape = shapes.large
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        SettingsGroup(title = "通用文字对话设置") {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiSettings.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text(stringResource(R.string.settings_api_key), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = apiSettings.baseUrl,
                onValueChange = { viewModel.updateApiEndpoint(it) },
                label = { Text(stringResource(R.string.settings_api_endpoint), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = apiSettings.modelName,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.settings_model), style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            shape = shapes.input,
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        viewModel.updateModelName(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { viewModel.refreshModels() },
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新模型",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Spacer(modifier = Modifier.height(12.dp))
            // OutlinedTextField(
            //     value = apiSettings.systemPrompt,
            //     onValueChange = { viewModel.updateSystemPrompt(it) },
            //     label = { Text(stringResource(R.string.settings_system_prompt), style = MaterialTheme.typography.bodyMedium) },
            //     minLines = 2,
            //     modifier = Modifier.fillMaxWidth(),
            //     shape = shapes.input,
            //     colors = OutlinedTextFieldDefaults.colors(
            //         focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            //         unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            //         focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            //         unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            //     ),
            //     textStyle = MaterialTheme.typography.bodyLarge
            // )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.testApiConnection(apiSettings.baseUrl, apiSettings.apiKey) },
                    enabled = !isTesting && apiSettings.baseUrl.isNotBlank() && apiSettings.apiKey.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.settings_api_test), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (testResult != null) {
                    Text(
                        text = if (testResult == true) "✓ ${stringResource(R.string.settings_api_configured)}" else "✗ ${stringResource(R.string.settings_api_not_configured)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testResult == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = if (apiSettings.isConfigured) stringResource(R.string.settings_api_configured) else stringResource(R.string.settings_api_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsGroup(title = "阿里云换衣设置") {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = viewModel.tryOnSettings.collectAsState().value.apiKey,
                onValueChange = { viewModel.updateTryOnApiKey(it) },
                label = { Text("试衣 API Key", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.tryOnSettings.collectAsState().value.host,
                onValueChange = { viewModel.updateTryOnApiHost(it) },
                label = { Text("试衣 API Host", style = MaterialTheme.typography.bodyMedium) },
                placeholder = { Text("https://your-host.aliyuncs.com", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        SettingsGroup(title = "火山方舟抠图设置") {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiSettings.volcEngineAccessKey,
                onValueChange = { viewModel.updateVolcengineAccessKey(it) },
                label = { Text("Access Key ID", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = apiSettings.volcEngineSecretKey,
                onValueChange = { viewModel.updateVolcengineSecretKey(it) },
                label = { Text("Secret Access Key", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_parameters)) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${stringResource(R.string.settings_temperature)}: ${String.format("%.1f", apiSettings.temperature)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = apiSettings.temperature.toFloat(),
                onValueChange = { viewModel.updateTemperature(it.toDouble()) },
                valueRange = 0f..2f, steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${stringResource(R.string.settings_max_tokens)}: ${apiSettings.maxTokens}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = apiSettings.maxTokens.toFloat(),
                onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                valueRange = 256f..8192f, steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SettingsGroup(title = stringResource(R.string.settings_appearance)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeSettings.themeMode == "system",
                        onClick = { viewModel.updateThemeMode("system") },
                        label = { Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Filled.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = shapes.pill
                    )
                    FilterChip(
                        selected = themeSettings.themeMode != "system",
                        onClick = {
                            if (systemDarkTheme) {
                                viewModel.updateThemeMode("light")
                            } else {
                                viewModel.updateThemeMode("dark")
                            }
                        },
                        label = {
                            Text(
                                if (systemDarkTheme) stringResource(R.string.settings_theme_light) else stringResource(R.string.settings_theme_dark),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (systemDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = shapes.pill
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = themeSettings.fontScale.toFloat(),
                    onValueChange = { viewModel.updateFontScale(it.toInt()) },
                    valueRange = 0f..4f, steps = 3,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("XS", "S", "M", "L", "XL").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        SettingsGroup(title = stringResource(R.string.settings_about)) {
            SettingsRow(
                label = stringResource(R.string.about_version),
                onClick = { showAboutDialog = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.about_detail_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
