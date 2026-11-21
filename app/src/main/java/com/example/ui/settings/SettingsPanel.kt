package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.chat.*
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    optKey: String,
    groqKey: String,
    selectedProvider: String,
    selectedModelId: String,
    termuxUrl: String,
    systemPrompt: String,
    openRouterModels: List<Pair<String, String>>,
    groqModels: List<Pair<String, String>>,
    onClose: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var oKey by remember { mutableStateOf(optKey) }
    var gKey by remember { mutableStateOf(groqKey) }
    var provider by remember { mutableStateOf(selectedProvider) }
    var model by remember { mutableStateOf(selectedModelId) }
    var termuxAddr by remember { mutableStateOf(termuxUrl) }
    var sysPrompt by remember { mutableStateOf(systemPrompt) }

    var oKeyVisible by remember { mutableStateOf(false) }
    var gKeyVisible by remember { mutableStateOf(false) }
    var showModelsDropdown by remember { mutableStateOf(false) }

    val modelsForActiveProvider = if (provider == "OpenRouter") openRouterModels else groqModels

    LaunchedEffect(provider) {
        val currentHasModel = modelsForActiveProvider.any { it.first == model }
        if (!currentHasModel && modelsForActiveProvider.isNotEmpty()) {
            model = modelsForActiveProvider.first().first
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        color = AppSurface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, AppStroke.copy(alpha = 0.80f)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configuración",
                        style = TitleStyle.copy(fontSize = 19.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ajustes de API, modelo y conexión",
                        style = MetaStyle
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar ajustes",
                        tint = AppTextMuted
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppSurface2)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("OpenRouter", "Groq").forEach { item ->
                    val isSelected = provider == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) AppAccent.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isSelected) AppAccent.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { provider = item }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item,
                            style = MetaStyle.copy(
                                color = if (isSelected) AppText else AppTextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            if (provider == "OpenRouter") {
                OutlinedTextField(
                    value = oKey,
                    onValueChange = { oKey = it },
                    label = { Text("OpenRouter API Key", style = MetaStyle) },
                    visualTransformation = if (oKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { oKeyVisible = !oKeyVisible }) {
                            Icon(
                                imageVector = if (oKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Key visibility",
                                tint = AppAccentSoft
                            )
                        }
                    },
                    textStyle = BodyStyle,
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_openrouter_input")
                )
            } else {
                OutlinedTextField(
                    value = gKey,
                    onValueChange = { gKey = it },
                    label = { Text("Groq API Key", style = MetaStyle) },
                    visualTransformation = if (gKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { gKeyVisible = !gKeyVisible }) {
                            Icon(
                                imageVector = if (gKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Key visibility",
                                tint = AppAccentSoft
                            )
                        }
                    },
                    textStyle = BodyStyle,
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_groq_input")
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = modelsForActiveProvider.find { it.first == model }?.second ?: model,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modelo", style = MetaStyle) },
                    trailingIcon = {
                        IconButton(onClick = { showModelsDropdown = !showModelsDropdown }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Show models list",
                                tint = AppCyan
                            )
                        }
                    },
                    textStyle = BodyStyle,
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModelsDropdown = !showModelsDropdown }
                )

                DropdownMenu(
                    expanded = showModelsDropdown,
                    onDismissRequest = { showModelsDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(AppSurface2)
                ) {
                    modelsForActiveProvider.forEach { modelPair ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = modelPair.second,
                                    style = MetaStyle.copy(
                                        color = if (modelPair.first == model) AppCyan else AppText
                                    )
                                )
                            },
                            onClick = {
                                model = modelPair.first
                                showModelsDropdown = false
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Custom ID Input",
                                style = MetaStyle.copy(
                                    color = AppAccentSoft,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        onClick = {
                            model = ""
                            showModelsDropdown = false
                        }
                    )
                }
            }

            if (!modelsForActiveProvider.any { it.first == model } && model.isEmpty()) {
                var customIdText by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = customIdText,
                    onValueChange = {
                        customIdText = it
                        model = it
                    },
                    label = { Text("Custom Model ID", style = MetaStyle) },
                    textStyle = BodyStyle,
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = termuxAddr,
                onValueChange = { termuxAddr = it },
                label = { Text("Termux WebSocket Address", style = MetaStyle) },
                textStyle = BodyStyle,
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("termux_url_input")
            )

            OutlinedTextField(
                value = sysPrompt,
                onValueChange = { sysPrompt = it },
                label = { Text("System Prompt", style = MetaStyle) },
                maxLines = 3,
                textStyle = BodyStyle,
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSave(oKey, gKey, provider, model, termuxAddr, sysPrompt) },
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save settings",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Guardar configuración",
                    style = BodyStyle.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
internal fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppText,
    unfocusedTextColor = AppText,
    focusedBorderColor = AppAccent.copy(alpha = 0.60f),
    unfocusedBorderColor = AppStroke,
    focusedContainerColor = AppSurface2,
    unfocusedContainerColor = AppSurface2,
    focusedLabelColor = AppAccentSoft,
    unfocusedLabelColor = AppTextMuted,
    cursorColor = AppAccentSoft
)
