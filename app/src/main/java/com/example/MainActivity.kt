package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ChatMessageEntity
import com.example.ui.ChatViewModel
import com.example.ui.ConnectionState
import com.example.ui.AgentPhase
import com.example.ui.AgentProgress
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

private val DarkBackground = Color(0xFF0E0F16)
private val TitaniumGray = Color(0xFF1E2A29)
private val GlacierCyan = Color(0xFF4CCFE1)

private val AppBg = DarkBackground
private val AppSurface = Color(0xFF161F1E) // Slate/Titanium dark gray
private val AppSurface2 = Color(0xFF1E2726) // Slightly lighter slate
private val AppSurface3 = Color(0xFF24302F) // More visible slate/gray
private val AppStroke = Color(0xFF2B3A39) // Titanium/gray-cyan border
private val AppText = Color(0xFFE4F3F5) // Soft ice/white text
private val AppTextMuted = Color(0xFF8BA2A5) // Soft grayish cyan muted text
private val AppAccent = GlacierCyan // Active/primary color (ice cyan!)
private val AppAccentSoft = GlacierCyan.copy(alpha = 0.6f)
private val AppCyan = GlacierCyan
private val AppGreen = Color(0xFF4DDF9B) // Clean modern green
private val AppPink = Color(0xFFE26180) // Soft modern pink
private val AppDanger = Color(0xFFFF627C) // Soft modern red

private val UiSans = FontFamily.SansSerif
private val UiMono = FontFamily.Monospace

private val TitleStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.01).em,
    color = AppText
)

private val BodyStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = AppText
)

private val MetaStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = AppTextMuted
)

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HelixApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelixApp(viewModel: ChatViewModel) {
    val messages by viewModel.allMessages.collectAsStateWithLifecycle()
    val wsState by viewModel.webSocketState.collectAsStateWithLifecycle()
    val wsError by viewModel.webSocketErrorMsg.collectAsStateWithLifecycle()

    val optKey by viewModel.apiKeyOpenRouter.collectAsStateWithLifecycle()
    val groqKey by viewModel.apiKeyGroq.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val termuxUrl by viewModel.termuxUrl.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    val openRouterModels = listOf(
        "cohere/north-mini-code:free" to "cohere/north-mini-code:free",
        "nvidia/nemotron-3.5-content-safety:free" to "nvidia/nemotron-3.5-content-safety:free",
        "nvidia/nemotron-3-ultra-550b-a55b:free" to "nvidia/nemotron-3-ultra-550b-a55b:free",
        "poolside/laguna-m.1:free" to "poolside/laguna-m.1:free",
        "openai/gpt-oss-20b:free" to "openai/gpt-oss-20b:free",
        "poolside/laguna-xs-2.1:free" to "poolside/laguna-xs-2.1:free"
    )

    val groqModels = listOf(
        "llama-4-scout-mock" to "Llama 4 Scout",
        "llama-3.3-70b-versatile" to "Llama 3.3 70B",
        "qwen-2.5-32b" to "Qwen 3 32B / Qwen 3.6 27B",
        "gpt-oss-20b" to "GPT OSS 120B / GPT OSS 20B",
        "whisper-large-v3" to "Whisper Large v3 / Turbo"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HelixTopBar(
                wsState = wsState,
                showSettings = showSettings,
                onSettingsToggle = { showSettings = !showSettings },
                onReconnect = { viewModel.connectWebSocket() },
                onClearChat = { viewModel.clearHistory() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackground,
                            TitaniumGray,
                            GlacierCyan.copy(alpha = 0.15f)
                        ),
                        center = Offset.Unspecified,
                        radius = 1200f
                    )
                )
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (showSettings) 14.dp else 0.dp)
            ) {
                if (wsState == ConnectionState.ERROR && wsError.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = AppDanger.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, AppDanger.copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = "Termux desconectado: $wsError",
                            style = MetaStyle.copy(
                                color = AppDanger,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ChatHistoryList(messages = messages)
                }

                AgentProgressCard(viewModel = viewModel)

                BottomInputBar(
                    viewModel = viewModel
                )
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showSettings = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Prevent click propagation inside the modal card from closing it
                            }
                    ) {
                        SettingsPanel(
                            optKey = optKey,
                            groqKey = groqKey,
                            selectedProvider = selectedProvider,
                            selectedModelId = selectedModelId,
                            termuxUrl = termuxUrl,
                            systemPrompt = systemPrompt,
                            openRouterModels = openRouterModels,
                            groqModels = groqModels,
                            onClose = { showSettings = false },
                            onSave = { oKey, gKey, prov, model, url, prompt ->
                                viewModel.updateSettings(oKey, gKey, prov, model, url, prompt)
                                showSettings = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelixTopBar(
    wsState: ConnectionState,
    showSettings: Boolean,
    onSettingsToggle: () -> Unit,
    onReconnect: () -> Unit,
    onClearChat: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = when (wsState) {
            ConnectionState.CONNECTED -> AppGreen
            ConnectionState.CONNECTING -> AppCyan
            ConnectionState.DISCONNECTED -> AppTextMuted
            ConnectionState.ERROR -> AppDanger
        },
        label = "status_color"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppSurface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppStroke.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Helix",
                    style = TitleStyle.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Chat local con IA",
                    style = MetaStyle
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface3.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        indicatorColor.copy(alpha = 0.30f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onReconnect() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (wsState) {
                        ConnectionState.CONNECTED -> "Conectado"
                        ConnectionState.CONNECTING -> "Conectando"
                        ConnectionState.DISCONNECTED -> "Desconectado"
                        ConnectionState.ERROR -> "Error"
                    },
                    style = MetaStyle.copy(
                        color = indicatorColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onClearChat,
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Chat History",
                    tint = AppTextMuted
                )
            }

            IconButton(
                onClick = onSettingsToggle,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = if (showSettings) Icons.Default.Close else Icons.Default.Settings,
                    contentDescription = "Settings Configuration",
                    tint = if (showSettings) AppDanger else AppAccentSoft
                )
            }
        }
    }
}

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
fun ChatHistoryList(messages: List<ChatMessageEntity>) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AppSurface2)
                        .border(1.dp, AppStroke, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = AppAccentSoft,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Tu chat está listo",
                    style = TitleStyle.copy(fontSize = 22.sp)
                )

                Text(
                    text = "Escribe en lenguaje natural o usa /cmd para ejecución directa. La UI ahora está pensada como producto moderno, no como terminal.",
                    style = BodyStyle.copy(
                        color = AppTextMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    ),
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatMessageBubble(message = message)
            }
        }
    }
}

@Composable
fun HighlightedCodeText(code: String, language: String) {
    val annotatedString = remember(code, language) {
        val lang = language.lowercase()
        buildAnnotatedString {
            append(code)

            val commentRegexes = when (lang) {
                "bash", "shell", "sh" -> listOf("""#.*""".toRegex())
                "html", "xml" -> listOf("""<!--.*?-->""".toRegex(RegexOption.DOT_MATCHES_ALL))
                "css" -> listOf("""/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL))
                else -> listOf(
                    """//.*""".toRegex(),
                    """/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL)
                )
            }

            val stringRegexes = listOf(
                """"[^"\\]*(?:\\.[^"\\]*)*"""".toRegex(),
                """'[^'\\]*(?:\\.[^'\\]*)*'""".toRegex()
            )

            val keywords = when (lang) {
                "js", "javascript", "ts", "typescript" -> listOf(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while",
                    "import", "export", "class", "from", "await", "async", "try", "catch", "new"
                )
                "json" -> listOf("true", "false", "null")
                "bash", "shell", "sh" -> listOf(
                    "sudo", "apt", "npm", "pnpm", "yarn", "pm2", "git", "ssh", "cd", "ls", "node",
                    "mkdir", "rm", "cp", "mv", "chmod", "curl", "wget", "echo", "status", "install"
                )
                "html", "xml" -> listOf(
                    "div", "span", "p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "script", "style", "link",
                    "class", "id", "href", "src", "type", "rel", "meta"
                )
                "css" -> listOf(
                    "color", "background", "border", "padding", "margin", "display", "position",
                    "width", "height", "font", "flex", "align", "justify"
                )
                else -> listOf("return", "if", "else", "for", "while", "class", "import")
            }

            for (regex in stringRegexes) {
                regex.findAll(code).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = AppGreen),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }

            val keywordPattern = if (lang == "html" || lang == "xml") {
                """\b(${keywords.joinToString("|")})\b|<\/?([a-zA-Z0-9]+)""".toRegex()
            } else {
                """\b(${keywords.joinToString("|")})\b""".toRegex()
            }

            keywordPattern.findAll(code).forEach { match ->
                addStyle(
                    style = SpanStyle(color = AppCyan, fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            """\b(\d+|true|false|null)\b""".toRegex().findAll(code).forEach { match ->
                addStyle(
                    style = SpanStyle(color = AppPink),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            for (regex in commentRegexes) {
                regex.findAll(code).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = AppTextMuted),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }
        }
    }

    Text(
        text = annotatedString,
        color = Color(0xFFE7EBF3),
        fontSize = 12.sp,
        lineHeight = 19.sp,
        fontFamily = UiMono,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun FilesBlockContainer(code: String) {
    val lines = remember(code) { code.split("\n").filter { it.trim().isNotEmpty() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (lines.isEmpty()) {
            Surface(
                color = AppSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppStroke)
            ) {
                Text(
                    text = "Directorio vacío o inaccesible.",
                    style = BodyStyle.copy(color = AppTextMuted),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AppStroke),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header inside card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = AppCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Archivos encontrados",
                            style = TitleStyle.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppText
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${lines.size} ítems",
                            style = MetaStyle.copy(
                                fontSize = 11.sp,
                                color = AppTextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // List of files
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lines.forEachIndexed { index, raw ->
                            val ansiStripped = raw.replace("\u001B\\[[;0-9]*[a-zA-Z]".toRegex(), "")
                            val cleaned = ansiStripped
                                .trim()
                                .replace("""^\d+\.\s*""".toRegex(), "")
                                .replace("📁", "")
                                .replace("📄", "")
                                .replace("⚙️", "")
                                .trim()

                            val isDir = raw.contains("📁") || cleaned.endsWith("/") || raw.contains("<DIR>")
                            val isZip = cleaned.endsWith(".zip", ignoreCase = true) || 
                                        cleaned.endsWith(".tar", ignoreCase = true) || 
                                        cleaned.endsWith(".gz", ignoreCase = true) || 
                                        cleaned.endsWith(".rar", ignoreCase = true) || 
                                        cleaned.endsWith(".7z", ignoreCase = true)
                            val isImage = cleaned.endsWith(".png", ignoreCase = true) || 
                                          cleaned.endsWith(".jpg", ignoreCase = true) || 
                                          cleaned.endsWith(".jpeg", ignoreCase = true) || 
                                          cleaned.endsWith(".webp", ignoreCase = true) || 
                                          cleaned.endsWith(".gif", ignoreCase = true) || 
                                          cleaned.endsWith(".svg", ignoreCase = true)
                            val isVideo = cleaned.endsWith(".mp4", ignoreCase = true) || 
                                          cleaned.endsWith(".mkv", ignoreCase = true) || 
                                          cleaned.endsWith(".avi", ignoreCase = true) || 
                                          cleaned.endsWith(".mov", ignoreCase = true) || 
                                          cleaned.endsWith(".webm", ignoreCase = true)
                            val isEnv = cleaned.contains(".env", ignoreCase = true) || cleaned.endsWith(".properties")

                            val rowBg = if (index % 2 == 0) AppSurface2.copy(alpha = 0.4f) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Item number (fixed width)
                                Text(
                                    text = String.format("%02d", index + 1),
                                    style = MetaStyle.copy(
                                        color = AppTextMuted.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(28.dp)
                                )

                                // 2. Icon according to file type
                                val iconColor = when {
                                    isDir -> AppCyan
                                    isZip -> AppPink
                                    isImage -> AppGreen
                                    isVideo -> Color(0xFFFFB74D) // Soft warm orange for videos
                                    isEnv -> AppAccentSoft
                                    else -> AppTextMuted
                                }
                                val iconVector = when {
                                    isDir -> Icons.Default.Folder
                                    isZip -> Icons.Default.FolderZip
                                    isImage -> Icons.Default.Image
                                    isVideo -> Icons.Default.Movie
                                    isEnv -> Icons.Default.Key
                                    else -> Icons.Default.Description
                                }

                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // 3. Filename
                                Text(
                                    text = cleaned,
                                    style = BodyStyle.copy(
                                        fontSize = 13.5.sp,
                                        color = AppText,
                                        fontFamily = UiSans
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageContentWithCodeBlocks(
    text: String,
    isUser: Boolean,
    isTermuxOut: Boolean,
    isTermuxErr: Boolean,
    isSystem: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    val parts = remember(text) { parseMessageText(text) }

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is MessagePart.TextBlock -> {
                    val isLogOrSystem = isTermuxOut || isTermuxErr || isSystem
                    if (isLogOrSystem) {
                        Text(
                            text = part.text.trim(),
                            style = BodyStyle.copy(
                                fontFamily = UiMono,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = when {
                                    isTermuxOut -> AppGreen
                                    isTermuxErr -> AppDanger
                                    else -> AppTextMuted
                                }
                            )
                        )
                    } else {
                        val defaultColor = if (isUser) AppText else AppText.copy(alpha = 0.97f)
                        val annotatedText = remember(part.text) {
                            buildMarkdownAnnotatedString(part.text.trim(), defaultColor, isUser)
                        }
                        Text(
                            text = annotatedText,
                            style = BodyStyle.copy(
                                color = defaultColor,
                                lineHeight = 21.sp
                            )
                        )
                    }
                }

                is MessagePart.CodeBlock -> {
                    if (part.language.lowercase() == "files") {
                        FilesBlockContainer(code = part.code)
                    } else {
                        CodeBlockContainer(
                            code = part.code,
                            language = part.language,
                            onCopy = {
                                val clip = android.content.ClipData.newPlainText("Copied Code", part.code)
                                clipboardManager.setPrimaryClip(clip)
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class MessagePart {
    data class TextBlock(val text: String) : MessagePart()
    data class CodeBlock(val code: String, val language: String) : MessagePart()
}

fun parseMessageText(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val regex = """```([a-zA-Z0-9+#-]*)\n?(.*?)\n?```""".toRegex(RegexOption.DOT_MATCHES_ALL)
    var lastIndex = 0

    regex.findAll(text).forEach { matchResult ->
        val textBefore = text.substring(lastIndex, matchResult.range.first)
        if (textBefore.isNotEmpty()) {
            parts.add(MessagePart.TextBlock(textBefore))
        }
        val language = matchResult.groupValues[1]
        val code = matchResult.groupValues[2]
        parts.add(MessagePart.CodeBlock(code, language))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < text.length) {
        val textAfter = text.substring(lastIndex)
        if (textAfter.isNotEmpty()) {
            parts.add(MessagePart.TextBlock(textAfter))
        }
    }

    if (parts.isEmpty() && text.isNotEmpty()) {
        parts.add(MessagePart.TextBlock(text))
    }

    return parts
}

@Composable
fun CodeBlockContainer(
    code: String,
    language: String,
    onCopy: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0C111A),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AppCyan.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurface2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase().ifEmpty { "CODE" },
                    style = MetaStyle.copy(
                        color = AppCyan,
                        fontFamily = UiMono,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (copied) AppGreen.copy(alpha = 0.14f)
                            else AppAccent.copy(alpha = 0.14f)
                        )
                        .border(
                            1.dp,
                            if (copied) AppGreen.copy(alpha = 0.40f) else AppAccent.copy(alpha = 0.40f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            onCopy()
                            copied = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) AppGreen else AppAccentSoft,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copied) "Copiado" else "Copiar",
                        style = MetaStyle.copy(
                            color = if (copied) AppGreen else AppText,
                            fontFamily = UiSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                HighlightedCodeText(code = code, language = language)
            }
        }
    }
}

@Composable
fun CyberLoadingIndicator(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val dotCount by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val progressWidth by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    val dots = ".".repeat(dotCount) + " ".repeat(4 - dotCount)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = AppSurface2.copy(alpha = 0.92f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = AppCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$text$dots",
                    style = MetaStyle.copy(
                        color = AppText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AppAccent, AppCyan)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.sender == "USER"
    val isSystem = message.sender == "TERMUX_SYS"
    val isTermuxOut = message.sender == "TERMUX_OUT"
    val isTermuxErr = message.sender == "TERMUX_ERR"

    val isThinking =
        message.sender == "AI" && (message.text.contains("Analyzing matrix") || message.text.startsWith("▌"))
    val isRunning =
        message.text.startsWith("[RUNNING]") ||
            message.text.startsWith("[PROCESS]") ||
            message.text.startsWith("[SSH_CONNECT]")

    if (isThinking) {
        CyberLoadingIndicator(text = "Procesando")
        return
    }

    if (isRunning) {
        val cleanStatus = message.text
            .replace("[RUNNING]", "Ejecutando")
            .replace("[PROCESS]", "Procesando")
            .replace("[SSH_CONNECT]", "Conectando por SSH")
            .trim()
        CyberLoadingIndicator(text = cleanStatus)
        return
    }

    if (message.sender == "AI") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AppAccent, AppCyan))
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.modelUsed ?: "Assistant",
                    style = MetaStyle.copy(
                        color = AppAccentSoft,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 28.dp)
            ) {
                MessageContentWithCodeBlocks(
                    text = message.text,
                    isUser = false,
                    isTermuxOut = false,
                    isTermuxErr = false,
                    isSystem = false
                )
            }
        }
    } else {
        val label = when (message.sender) {
            "USER" -> "Tú"
            "TERMUX_SYS" -> "Sistema"
            "TERMUX_OUT" -> "Termux salida"
            "TERMUX_ERR" -> "Termux error"
            else -> "Dispositivo"
        }

        val labelColor = when (message.sender) {
            "USER" -> AppAccentSoft
            "TERMUX_SYS" -> AppTextMuted
            "TERMUX_OUT" -> AppGreen
            "TERMUX_ERR" -> AppDanger
            else -> AppTextMuted
        }

        val bubbleShape = when {
            isUser -> RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
            isSystem -> RoundedCornerShape(18.dp)
            else -> RoundedCornerShape(20.dp)
        }

        val bubbleBg = when {
            isUser -> AppAccent.copy(alpha = 0.14f)
            isSystem -> AppSurface2.copy(alpha = 0.88f)
            isTermuxOut || isTermuxErr -> AppSurface2.copy(alpha = 0.92f)
            else -> AppSurface
        }

        val bubbleBorder = when {
            isUser -> BorderStroke(1.dp, AppAccent.copy(alpha = 0.35f))
            isSystem -> BorderStroke(1.dp, AppStroke)
            isTermuxOut -> BorderStroke(1.dp, AppGreen.copy(alpha = 0.35f))
            isTermuxErr -> BorderStroke(1.dp, AppDanger.copy(alpha = 0.35f))
            else -> BorderStroke(1.dp, AppStroke)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = label,
                style = MetaStyle.copy(
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )

            Surface(
                shape = bubbleShape,
                color = bubbleBg,
                border = bubbleBorder,
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                MessageContentWithCodeBlocks(
                    text = message.text,
                    isUser = isUser,
                    isTermuxOut = isTermuxOut,
                    isTermuxErr = isTermuxErr,
                    isSystem = isSystem
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomInputBar(
    viewModel: ChatViewModel
) {
    var textState by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val agentProgress by viewModel.agentProgress.collectAsStateWithLifecycle()

    val isExecuting = agentProgress.phase != AgentPhase.IDLE &&
                      agentProgress.phase != AgentPhase.COMPLETED &&
                      agentProgress.phase != AgentPhase.FAILED &&
                      agentProgress.phase != AgentPhase.CANCELLED

    val action = {
        if (isExecuting) {
            viewModel.cancelAgentExecution()
        } else if (textState.trim().isNotEmpty()) {
            viewModel.sendMessage(textState)
            textState = ""
            keyboardController?.hide()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(AppSurface.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    brush = if (isExecuting) {
                        Brush.horizontalGradient(listOf(AppDanger, AppDanger.copy(alpha = 0.4f)))
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                AppAccent.copy(alpha = 0.55f),
                                AppCyan.copy(alpha = 0.25f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = {
                    Text(
                        text = if (isExecuting) "Helix está trabajando..." else "Pregúntame cualquier cosa…",
                        style = BodyStyle.copy(color = AppTextMuted)
                    )
                },
                enabled = !isExecuting,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { action() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    disabledTextColor = AppTextMuted,
                    cursorColor = AppAccentSoft,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_textfield"),
                textStyle = BodyStyle
            )

            Spacer(modifier = Modifier.width(8.dp))

            val buttonBrush = if (isExecuting) {
                Brush.linearGradient(listOf(AppDanger, Color(0xFFE57373)))
            } else {
                Brush.linearGradient(listOf(AppAccent, AppCyan))
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(buttonBrush)
                    .clickable { action() }
                    .testTag(if (isExecuting) "stop_button" else "send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExecuting) Icons.Default.Close else Icons.Default.ArrowUpward,
                    contentDescription = if (isExecuting) "Stop execution" else "Send transmission",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AgentProgressCard(viewModel: ChatViewModel) {
    val progress by viewModel.agentProgress.collectAsStateWithLifecycle()

    val isExecuting = progress.phase != AgentPhase.IDLE &&
                      progress.phase != AgentPhase.COMPLETED &&
                      progress.phase != AgentPhase.FAILED &&
                      progress.phase != AgentPhase.CANCELLED

    AnimatedVisibility(
        visible = isExecuting,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = AppSurface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (progress.phase == AgentPhase.RETRYING) AppDanger.copy(alpha = 0.35f) else AppAccent.copy(alpha = 0.35f)),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (progress.phase == AgentPhase.RETRYING) AppDanger else AppCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = progress.message,
                        style = BodyStyle.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppText
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (progress.maxAttempts > 1) {
                        Text(
                            text = "Intento ${progress.currentAttempt} de ${progress.maxAttempts}",
                            style = MetaStyle.copy(
                                fontSize = 11.sp,
                                color = AppAccentSoft
                            )
                        )
                    }
                }

                if (progress.currentCommand.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AppStroke)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$",
                                style = BodyStyle.copy(
                                    color = AppCyan,
                                    fontFamily = UiMono,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = progress.currentCommand,
                                style = BodyStyle.copy(
                                    color = AppText,
                                    fontFamily = UiMono,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "progress")
                    val progressWidth by infiniteTransition.animateFloat(
                        initialValue = 0.10f,
                        targetValue = 0.90f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (progress.phase == AgentPhase.RETRYING) {
                                        listOf(AppDanger, Color(0xFFE57373))
                                    } else {
                                        listOf(AppAccent, AppCyan)
                                    }
                                )
                            )
                    )
                }
            }
        }
    }
}

fun buildMarkdownAnnotatedString(text: String, defaultColor: Color, isUser: Boolean = false): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val nextBold = text.indexOf("**", index)
            val nextCode = text.indexOf("`", index)

            if (nextBold != -1 && (nextCode == -1 || nextBold < nextCode)) {
                append(text.substring(index, nextBold))

                val endBold = text.indexOf("**", nextBold + 2)
                if (endBold != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (isUser) AppText else AppCyan))
                    append(text.substring(nextBold + 2, endBold))
                    pop()
                    index = endBold + 2
                } else {
                    append("**")
                    index = nextBold + 2
                }
            } else if (nextCode != -1 && (nextBold == -1 || nextCode < nextBold)) {
                append(text.substring(index, nextCode))

                val endCode = text.indexOf("`", nextCode + 1)
                if (endCode != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = UiMono,
                            color = AppAccentSoft,
                            background = Color.White.copy(alpha = 0.08f)
                        )
                    )
                    append(text.substring(nextCode + 1, endCode))
                    pop()
                    index = endCode + 1
                } else {
                    append("`")
                    index = nextCode + 1
                }
            } else {
                append(text.substring(index))
                break
            }
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
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