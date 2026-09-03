package com.family.gatelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// --- Кольорова палітра ---
private val DeepForest = Color(0xFF0B2B1D)
private val ForestGreen = Color(0xFF14432E)
private val AccentGreen = Color(0xFF3ED598)
private val AccentGreenDark = Color(0xFF1F9D6C)
private val WarnAmber = Color(0xFFFFB74D)
private val CardSurface = Color(0xFF16332A)
private val TextPrimary = Color(0xFFF3FBF6)
private val TextSecondary = Color(0xFF9FC4B2)

class MainActivity : ComponentActivity() {

    private val haClient = HaClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentGreen,
                    onPrimary = DeepForest,
                    background = DeepForest,
                    surface = CardSurface,
                    onSurface = TextPrimary,
                    secondary = AccentGreenDark
                )
            ) {
                GateLockApp(store = store, haClient = haClient)
            }
        }
    }
}

@Composable
fun GateLockApp(store: SettingsStore, haClient: HaClient) {
    var showSettings by remember { mutableStateOf(false) }
    val settings by store.settingsFlow.collectAsState(initial = HaSettings())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepForest, ForestGreen))
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Хвіртка",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Налаштування",
                                tint = TextSecondary
                            )
                        }
                    }
                )
            }
        ) { padding ->
            if (showSettings) {
                SettingsScreen(
                    store = store,
                    haClient = haClient,
                    initial = settings,
                    onDone = { showSettings = false },
                    modifier = Modifier.padding(padding)
                )
            } else {
                HomeScreen(
                    settings = settings,
                    haClient = haClient,
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    settings: HaSettings,
    haClient: HaClient,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var justOpened by remember { mutableStateOf(false) }
    val configured = settings.baseUrl.isNotBlank() && settings.token.isNotBlank() && settings.entityId.isNotBlank()

    val scale = remember { Animatable(1f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!configured) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Спочатку налаштуй підключення до Home Assistant",
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = DeepForest)
                    ) { Text("Відкрити налаштування") }
                }
            }
            return@Column
        }

        // Радіальне сяйво навколо кнопки
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(320.dp)) {
                val glowColor = if (justOpened) AccentGreen else AccentGreenDark
                drawCircle(
                    brush = ShaderBrush(
                        RadialGradientShader(
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.minDimension / 2,
                            colors = listOf(glowColor.copy(alpha = 0.35f), Color.Transparent)
                        ) as Shader
                    )
                )
            }

            fun handleTap() {
                isLoading = true
                status = null
                isError = false
                scope.launch {
                    when (val result = haClient.triggerOpen(settings)) {
                        is HaResult.Success -> {
                            status = "Хвіртку відкрито"
                            isError = false
                            justOpened = true
                        }
                        is HaResult.Error -> {
                            status = result.message
                            isError = true
                        }
                    }
                    isLoading = false
                }
            }

            Surface(
                shape = CircleShape,
                color = CardSurface,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale.value)
                    .pointerInput(isLoading) {
                        detectTapGestures(
                            onPress = {
                                if (!isLoading) {
                                    scope.launch { scale.animateTo(0.92f, tween(80)) }
                                    tryAwaitRelease()
                                    scope.launch { scale.animateTo(1f, tween(120)) }
                                }
                            },
                            onTap = { if (!isLoading) handleTap() }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                    } else {
                        AnimatedContent(
                            targetState = justOpened,
                            transitionSpec = {
                                (fadeIn(tween(250)) togetherWith fadeOut(tween(250)))
                            },
                            label = "lock-icon"
                        ) { opened ->
                            Icon(
                                if (opened) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = "Відкрити хвіртку",
                                modifier = Modifier.size(84.dp),
                                tint = AccentGreen
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Натисни, щоб відкрити хвіртку",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            settings.entityId,
            fontSize = 13.sp,
            color = TextSecondary
        )

        status?.let {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) Color(0xFF3D1F1F) else Color(0xFF1B3B2C)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) WarnAmber else AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = TextPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    store: SettingsStore,
    haClient: HaClient,
    initial: HaSettings,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember(initial) { mutableStateOf(initial.baseUrl) }
    var token by remember(initial) { mutableStateOf(initial.token) }
    var entityId by remember(initial) { mutableStateOf(initial.entityId) }
    var domain by remember(initial) { mutableStateOf(initial.domain) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(true) }
    var testing by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Підключення до Home Assistant",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Адреса сервера") },
                    placeholder = { Text("http://192.168.1.109:8123") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Long-Lived Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = entityId,
                    onValueChange = { entityId = it },
                    label = { Text("Entity ID") },
                    placeholder = { Text("switch.gate_relay") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
                Spacer(Modifier.height(18.dp))

                Text("Тип entity", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntityDomain.entries.forEach { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            RadioButton(
                                selected = domain == d,
                                onClick = { domain = d },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentGreen, unselectedColor = TextSecondary)
                            )
                            Text(d.name.lowercase(), color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    testing = true
                    testStatus = null
                    scope.launch {
                        val current = HaSettings(baseUrl, token, entityId, domain)
                        when (val r = haClient.testConnection(current)) {
                            is HaResult.Success -> { testStatus = "З'єднання успішне"; testOk = true }
                            is HaResult.Error -> { testStatus = r.message; testOk = false }
                        }
                        testing = false
                    }
                },
                enabled = !testing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (testing) "Перевірка..." else "Перевірити")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        store.save(HaSettings(baseUrl, token, entityId, domain))
                        onDone()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = DeepForest),
                modifier = Modifier.weight(1f)
            ) {
                Text("Зберегти")
            }
        }

        testStatus?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (testOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (testOk) AccentGreen else WarnAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(it, color = TextPrimary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Порада: у Home Assistant → Профіль → Long-Lived Access Tokens створи токен спеціально для цього застосунку.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
