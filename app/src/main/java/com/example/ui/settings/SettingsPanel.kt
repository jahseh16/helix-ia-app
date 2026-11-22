package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.agent.permissions.Permission
import com.example.agent.permissions.PermissionPolicy
import com.example.ui.ModelCatalog
import com.example.ui.chat.BrandLogo
import com.example.ui.chat.ToolPresentation
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppDanger
import com.example.ui.theme.AppGreen
import com.example.ui.theme.AppWarning
import androidx.compose.foundation.layout.heightIn
import com.example.ui.theme.AppAccentSoft
import com.example.ui.theme.AppStroke
import com.example.ui.theme.AppSurface2
import com.example.ui.theme.AppSurface3
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.BodyStyle
import com.example.ui.theme.MetaStyle
import com.example.ui.theme.ShapeMedium
import com.example.ui.theme.TitleStyle

private val PROVIDERS = ModelCatalog.providers

/** Contenido de la hoja de ajustes (la envuelve un ModalBottomSheet en [com.example.ui.HelixApp]). */
@Composable
fun SettingsPanel(
    optKey: String,
    groqKey: String,
    selectedProvider: String,
    selectedModelId: String,
    systemPrompt: String,
    permissions: Map<String, Permission>,
    onPermissionChange: (String, Permission) -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var oKey by remember { mutableStateOf(optKey) }
    var gKey by remember { mutableStateOf(groqKey) }
    var provider by remember { mutableStateOf(selectedProvider) }
    var model by remember { mutableStateOf(selectedModelId) }
    var sysPrompt by remember { mutableStateOf(systemPrompt) }

    var oKeyVisible by remember { mutableStateOf(false) }
    var gKeyVisible by remember { mutableStateOf(false) }
    var showModelsDropdown by remember { mutableStateOf(false) }

    val models = ModelCatalog.modelsFor(provider)
    var customModel by remember { mutableStateOf(model.isNotBlank() && models.none { it.id == model }) }

    LaunchedEffect(provider) {
        if (!customModel && models.none { it.id == model } && models.isNotEmpty()) {
            model = models.first().id
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(text = "Ajustes", style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Proveedor, API key y modelo", style = MetaStyle)
        }

        // Selector segmentado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppSurface2)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PROVIDERS.forEach { item ->
                val selected = provider == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppAccent else Color.Transparent)
                        .clickable { provider = item }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = BodyStyle.copy(
                            fontSize = 14.sp,
                            color = if (selected) Color.White else AppTextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        if (provider == "OpenRouter") {
            ApiKeyField(
                label = "OpenRouter API Key",
                value = oKey,
                onValueChange = { oKey = it },
                visible = oKeyVisible,
                onToggleVisible = { oKeyVisible = !oKeyVisible },
                testTag = "api_key_openrouter_input"
            )
        } else {
            ApiKeyField(
                label = "Groq API Key",
                value = gKey,
                onValueChange = { gKey = it },
                visible = gKeyVisible,
                onToggleVisible = { gKeyVisible = !gKeyVisible },
                testTag = "api_key_groq_input"
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (customModel) "Personalizado" else ModelCatalog.resolve(provider, model).name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Modelo", style = MetaStyle) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppAccentSoft)
                },
                textStyle = BodyStyle,
                colors = helixFieldColors(),
                shape = ShapeMedium,
                modifier = Modifier.fillMaxWidth()
            )
            // Capa transparente: un TextField de solo lectura no recibe bien el click.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ShapeMedium)
                    .clickable { showModelsDropdown = true }
            )

            DropdownMenu(
                expanded = showModelsDropdown,
                onDismissRequest = { showModelsDropdown = false },
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(AppSurface3, ShapeMedium)
            ) {
                models.forEach { m ->
                    DropdownMenuItem(
                        leadingIcon = { BrandLogo(m.brand, size = 24.dp) },
                        text = {
                            Text(
                                text = m.name,
                                style = BodyStyle.copy(
                                    fontSize = 14.sp,
                                    color = if (!customModel && m.id == model) AppAccentSoft else AppText
                                )
                            )
                        },
                        onClick = {
                            model = m.id
                            customModel = false
                            showModelsDropdown = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Personalizado…",
                            style = BodyStyle.copy(fontSize = 14.sp, color = AppAccentSoft, fontWeight = FontWeight.SemiBold)
                        )
                    },
                    onClick = {
                        customModel = true
                        showModelsDropdown = false
                    }
                )
            }
        }

        if (customModel) {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("ID del modelo", style = MetaStyle) },
                singleLine = true,
                textStyle = BodyStyle,
                colors = helixFieldColors(),
                shape = ShapeMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = sysPrompt,
            onValueChange = { sysPrompt = it },
            label = { Text("Instrucciones del sistema", style = MetaStyle) },
            minLines = 2,
            maxLines = 5,
            textStyle = BodyStyle,
            colors = helixFieldColors(),
            shape = ShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )

        PermissionsSection(permissions = permissions, onChange = onPermissionChange)

        Button(
            onClick = { onSave(oKey, gKey, provider, model.trim(), sysPrompt) },
            enabled = model.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppAccent,
                contentColor = Color.White,
                disabledContainerColor = AppSurface3,
                disabledContentColor = AppTextMuted
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_settings_button")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Guardar", style = BodyStyle.copy(color = Color.Unspecified, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MetaStyle) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "Ocultar key" else "Mostrar key",
                    tint = AppAccentSoft
                )
            }
        },
        textStyle = BodyStyle,
        colors = helixFieldColors(),
        shape = ShapeMedium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
internal fun helixFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppText,
    unfocusedTextColor = AppText,
    focusedBorderColor = AppAccent,
    unfocusedBorderColor = AppStroke,
    focusedContainerColor = AppSurface2,
    unfocusedContainerColor = AppSurface2,
    focusedLabelColor = AppAccentSoft,
    unfocusedLabelColor = AppTextMuted,
    cursorColor = AppAccent
)

@Composable
private fun PermissionsSection(permissions: Map<String, Permission>, onChange: (String, Permission) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("Permisos de herramientas", style = TitleStyle.copy(fontSize = 17.sp))
            Text("Toca para alternar: Auto · Preguntar · Bloquear", style = MetaStyle)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AppSurface2)
        ) {
            PermissionPolicy.TOOL_NAMES.forEachIndexed { index, tool ->
                val level = permissions[tool] ?: Permission.ASK
                // El shell nunca puede ir en automático
                val cycle = if (tool in PermissionPolicy.NEVER_AUTO) listOf(Permission.ASK, Permission.DENY)
                else listOf(Permission.AUTO, Permission.ASK, Permission.DENY)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ToolPresentation.title(tool),
                        style = BodyStyle.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                    val (label, color) = when (level) {
                        Permission.AUTO -> "Auto" to AppGreen
                        Permission.ASK -> "Preguntar" to AppWarning
                        Permission.DENY -> "Bloquear" to AppDanger
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.14f))
                            .clickable { onChange(tool, cycle[(cycle.indexOf(level) + 1) % cycle.size]) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("perm_$tool")
                    ) {
                        Text(label, style = MetaStyle.copy(color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                if (index < PermissionPolicy.TOOL_NAMES.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AppStroke))
                }
            }
        }
    }
}
