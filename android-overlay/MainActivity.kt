package com.nexos.os

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.media.AudioManager
import android.media.ToneGenerator
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Night = Color(0xFF08070D)
private val Panel = Color(0xFF17121F)
private val Purple = Color(0xFF9D55E8)
private val Neon = Color(0xFFD5B4FF)
private val Muted = Color(0xFFB9ADC9)
private val White = Color(0xFFF9F5FF)

private data class Platform(
    val id: String,
    val title: String,
    val symbol: String,
    val caption: String,
    val colors: List<Color>,
    val kind: String = "system"
)

private data class ImportedGame(val name: String, val uri: Uri, val system: String, val size: String)

private val platforms = listOf(
    Platform("PS1", "PLAYSTATION", "◈", "PS1", listOf(Color(0xFF453B87), Color(0xFF171326))),
    Platform("PS2", "PLAYSTATION 2", "Ⅱ", "PS2", listOf(Color(0xFF673B91), Color(0xFF171126))),
    Platform("PS3", "PLAYSTATION 3", "Ⅲ", "PS3", listOf(Color(0xFF46566F), Color(0xFF10131D))),
    Platform("XBOX", "XBOX", "⊗", "XBOX", listOf(Color(0xFF376B4A), Color(0xFF101A15))),
    Platform("XBOX360", "XBOX 360", "✳", "360", listOf(Color(0xFF34735B), Color(0xFF101A15))),
    Platform("SNES", "SUPER NINTENDO", "✦", "SNES", listOf(Color(0xFF79558E), Color(0xFF19121F))),
    Platform("MEGA", "MEGA DRIVE", "◆", "MEGA", listOf(Color(0xFF426A83), Color(0xFF111722))),
    Platform("PSP", "PLAYSTATION PORTABLE", "▰", "PSP", listOf(Color(0xFF3D5A9B), Color(0xFF101321))),
    Platform("PC", "PC GAMING", "⌘", "PC", listOf(Color(0xFF5C4C76), Color(0xFF17121F))),
    Platform("IMPORT", "ADICIONAR ROMS", "＋", "IMPORTAR", listOf(Color(0xFF30283E), Color(0xFF111018)), "import"),
    Platform("STORE", "LOJA NEXOS", "⌑", "LOJA", listOf(Color(0xFF713E92), Color(0xFF171020)), "store"),
    Platform("LIBRARY", "MINHA BIBLIOTECA", "▣", "BIBLIOTECA", listOf(Color(0xFF347FA4), Color(0xFF111A2A)), "library")
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { NexosApp() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }
}

@Composable
private fun NexosApp() {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf("PS2") }
    var awaitingSecondTap by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("home") }
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(3200); splashDone = true }
    var selectedSystem by remember { mutableStateOf("PS2") }
    var imported by remember { mutableStateOf(listOf<ImportedGame>()) }
    var search by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showPpssppHelp by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }
    fun uiSound(confirm: Boolean = false) {
        if (soundEnabled) toneGenerator.startTone(if (confirm) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 75)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val additions = uris.map { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Arquivo de jogo"
            var size = "Tamanho desconhecido"
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val ni = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = it.getColumnIndex(OpenableColumns.SIZE)
                if (it.moveToFirst()) {
                    if (ni >= 0) name = it.getString(ni) ?: name
                    if (si >= 0 && !it.isNull(si)) {
                        val bytes = it.getLong(si)
                        size = if (bytes >= 1_000_000_000) "%.2f GB".format(bytes / 1_000_000_000.0)
                        else if (bytes >= 1_000_000) "%.0f MB".format(bytes / 1_000_000.0)
                        else "%.0f KB".format(bytes / 1_000.0)
                    }
                }
            }
            ImportedGame(name, uri, selectedSystem, size)
        }
        imported = (imported + additions).distinctBy { it.uri.toString() }
        screen = "library"
    }

    fun launchPsp(game: ImportedGame) {
        val mime = context.contentResolver.getType(game.uri) ?: "application/octet-stream"
        val packages = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
        var opened = false
        for (packageName in packages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(packageName)
                    setDataAndType(game.uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                opened = true
                break
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        if (!opened) showPpssppHelp = true
    }

    fun openPlatform(platform: Platform) {
        awaitingSecondTap = false
        when (platform.kind) {
            "store" -> screen = "store"
            "library" -> { selectedSystem = "PS2"; search = ""; screen = "library" }
            "import" -> {
                selectedSystem = selectedId.takeIf { it !in listOf("IMPORT", "STORE") } ?: "PSP"
                screen = "import"
            }
            else -> {
                selectedSystem = platform.id
                search = ""
                screen = "library"
            }
        }
    }

    BackHandler(enabled = screen != "home") { screen = "home"; awaitingSecondTap = false }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Purple,
        onPrimary = White, onBackground = White, onSurface = White
    )) {
        val backdropColor = platforms.firstOrNull { it.id == selectedId }?.colors?.firstOrNull() ?: Purple
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(backdropColor.copy(alpha = .42f), Color(0xFF151020), Night))
            )
        ) {
            if (!splashDone) {
                NexosSplash(toneGenerator = toneGenerator, soundEnabled = soundEnabled)
            } else if (screen == "home") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cardWidth = (maxWidth * 0.205f).coerceIn(112.dp, 205.dp)
                    val cardHeight = cardWidth * 1.42f
                    val horizontalPadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(12.dp)
                    val railState = rememberLazyListState()
                    val selectedIndex = platforms.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                    LaunchedEffect(selectedId) {
                        railState.animateScrollToItem(selectedIndex)
                    }
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEXOS", color = White, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showSettings = true }) {
                                androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Neon)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.fillMaxWidth(0.82f).height(cardHeight * 0.78f)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                (platforms[selectedIndex].colors.firstOrNull() ?: Purple).copy(alpha = .28f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            LazyRow(
                                state = railState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = horizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(platforms, key = { it.id }) { platform ->
                                    val chosen = platform.id == selectedId
                                    Column(
                                        Modifier.width(cardWidth)
                                            .graphicsLayer {
                                                val offset = (platforms.indexOf(platform) - selectedIndex).toFloat()
                                                rotationY = when {
                                                    offset < 0 -> 13f
                                                    offset > 0 -> -13f
                                                    else -> 0f
                                                }
                                                cameraDistance = 24f * density
                                                scaleX = if (chosen) 1f else .91f
                                                scaleY = if (chosen) 1f else .91f
                                                alpha = if (chosen) 1f else .78f
                                            }
                                            .clickable {
                                                if (chosen && awaitingSecondTap) {
                                                    uiSound(true)
                                                    openPlatform(platform)
                                                } else {
                                                    selectedId = platform.id
                                                    awaitingSecondTap = true
                                                    uiSound()
                                                }
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        SystemCover(platform, cardHeight, chosen)
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            Modifier.fillMaxWidth().height(cardHeight * .30f)
                                                .graphicsLayer { scaleY = -1f; alpha = .24f }
                                                .clip(RoundedCornerShape(5.dp))
                                        ) {
                                            SystemCover(platform, cardHeight, chosen, reflection = true)
                                            Box(
                                                Modifier.fillMaxSize().background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Night.copy(alpha = .92f))
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            platforms[selectedIndex].title,
                            color = White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "DESLIZE PARA NAVEGAR  •  TOQUE DUAS VEZES PARA ENTRAR",
                            color = Muted,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { screen = "home" }) {
                            androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Neon)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (screen) {
                                    "store" -> "LOJA NEXOS"
                                    "import" -> "ADICIONAR ROMS"
                                    else -> platforms.firstOrNull { it.id == selectedSystem }?.title ?: "MINHA BIBLIOTECA"
                                },
                                color = White, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                            )
                            Text("CAPAS • COLEÇÃO • JOGOS", color = Neon, fontSize = 8.sp, letterSpacing = 2.sp)
                        }
                        if (screen == "library") {
                            Button(onClick = { screen = "import" }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                                Text("＋ ROMs", fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when (screen) {
                        "store" -> {
                            Text("Seu próximo jogo começa aqui.", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("A loja visual será conectada a um catálogo autorizado em uma próxima etapa.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StoreCard("DESTAQUES", "✦")
                                StoreCard("CLÁSSICOS", "◈")
                                StoreCard("MINHA COLEÇÃO", "▣")
                            }
                        }
                        "import" -> {
                            Text("Importe arquivos de jogos que você possui.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                                Text("Escolher arquivos")
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Para emular PSP, selecione um arquivo ISO, CSO ou PBP. O PPSSPP precisa estar instalado para executar o jogo.", color = Muted, fontSize = 11.sp)
                        }
                        else -> {
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                placeholder = { Text("Buscar jogo na biblioteca...", fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Purple, unfocusedBorderColor = Color(0xFF40364F),
                                    focusedTextColor = White, unfocusedTextColor = White
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            val games = imported.filter {
                                it.system == selectedSystem && it.name.contains(search, true)
                            }
                            if (games.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("＋", color = Neon, fontSize = 42.sp)
                                        Text("Sua biblioteca começa aqui", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(5.dp))
                                        Text("Adicione seus arquivos para preencher as capas.", color = Muted, fontSize = 11.sp)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { screen = "import" }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                                            Text("Adicionar ROMs")
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 132.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(games) { game ->
                                        Column(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(Panel).clickable {
                                                    if (selectedSystem == "PSP") launchPsp(game)
                                                    else showPpssppHelp = true
                                                }
                                        ) {
                                            Box(
                                                Modifier.fillMaxWidth().height(128.dp)
                                                    .background(Brush.linearGradient(listOf(Color(0xFF65418C), Color(0xFF161121)))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(game.name.take(1).uppercase(), color = White, fontSize = 50.sp, fontWeight = FontWeight.Black)
                                            }
                                            Column(Modifier.padding(10.dp)) {
                                                Text(game.name, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.height(3.dp))
                                                Text(game.size, color = Muted, fontSize = 9.sp)
                                                Text(if (selectedSystem == "PSP") "ABRIR NO PPSSPP ↗" else "EMULADOR NÃO CONFIGURADO", color = Neon, fontSize = 8.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configurações NEXOS") },
            text = {
                Column {
                    Text("Interface em tela cheia", color = White)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Som de navegação", modifier = Modifier.weight(1f), color = White)
                        Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                    }
                    Text("O visual e a navegação estão em fase inicial.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Fechar") } },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = Muted
        )
    }

    if (showPpssppHelp) {
        AlertDialog(
            onDismissRequest = { showPpssppHelp = false },
            title = { Text("Emulador necessário") },
            text = {
                Text(
                    if (selectedSystem == "PSP") "O NEXOS encaminha jogos PSP para o PPSSPP, mas ele ainda não está instalado ou não conseguiu abrir este arquivo. Instale o PPSSPP oficial e tente novamente."
                    else "A biblioteca já pode organizar os arquivos, mas o emulador desta plataforma ainda não foi integrado. Nesta versão, a execução está preparada para PSP via PPSSPP.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPpssppHelp = false
                    if (selectedSystem == "PSP") {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ppsspp.org/download/")))
                        } catch (_: Exception) { }
                    }
                }) { Text(if (selectedSystem == "PSP") "Obter PPSSPP" else "Entendi") }
            },
            dismissButton = { TextButton(onClick = { showPpssppHelp = false }) { Text("Fechar") } },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = Muted
        )
    }
}


@Composable
private fun NexosSplash(toneGenerator: ToneGenerator, soundEnabled: Boolean) {
    val scale = remember { Animatable(0.72f) }
    val alpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0.35f) }
    LaunchedEffect(Unit) {
        if (soundEnabled) {
            // Original NEXOS electronic startup signature: rising notes and a bright finish.
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 110)
            delay(90)
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_4, 120)
            delay(100)
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_7, 150)
            delay(120)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 220)
        }
        alpha.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
        while (true) {
            glow.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            glow.animateTo(0.42f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF08051A), Color(0xFF02030A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF176DFF).copy(alpha = glow.value), Color(0xFF9A39FF).copy(alpha = glow.value), Color.Transparent))))
        Box(Modifier.fillMaxWidth(.9f).height(280.dp).align(Alignment.Center)
            .background(Brush.radialGradient(listOf(Color(0xFF3422A0).copy(alpha = .28f * glow.value), Color.Transparent))))
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("NE", color = Color(0xFFE8F3FF), fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("X", color = Color(0xFF59BFFF), fontSize = 58.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.graphicsLayer { shadowElevation = 18f * glow.value })
                Text("OS", color = Color(0xFFE8E1FF), fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.width(42.dp).height(2.dp).background(Color(0xFF218BFF)))
                Spacer(Modifier.width(12.dp))
                Text("O S", color = Color.White, fontSize = 18.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(42.dp).height(2.dp).background(Color(0xFF9A39FF)))
            }
            Spacer(Modifier.height(22.dp))
            Text("TODOS OS SEUS JOGOS. EM UM SÓ LUGAR.", color = Color(0xFFC8C7E5),
                fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(62.dp))
            Box(Modifier.size(24.dp).border(2.dp, Color(0xFF438BFF).copy(alpha = glow.value), RoundedCornerShape(50)))
            Spacer(Modifier.height(12.dp))
            Text("INICIANDO NEXOS OS...", color = Color.White.copy(alpha = .88f), fontSize = 9.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun SystemCover(platform: Platform, height: androidx.compose.ui.unit.Dp, selected: Boolean, reflection: Boolean = false) {
    val c1 = platform.colors.firstOrNull() ?: Purple
    val c2 = platform.colors.lastOrNull() ?: Night
    val heroes = when (platform.id) {
        "PS1" -> listOf("🦊", "⚔️", "🏎️")
        "PS2" -> listOf("⚔️", "🥷", "🐉")
        "PS3" -> listOf("🧔🏻", "👧🏻", "🎮")
        "XBOX" -> listOf("🪖", "🗡️", "🚙")
        "XBOX360" -> listOf("🪖", "👩🏻", "⚔️")
        "SNES" -> listOf("🍄", "🦖", "⭐")
        "MEGA" -> listOf("🦔", "🦊", "💎")
        "PSP" -> listOf("🥷", "🏁", "⚔️")
        "PC" -> listOf("⌨️", "🖱️", "⚡")
        "IMPORT" -> listOf("＋", "📁", "🎮")
        "STORE" -> listOf("✦", "🛒", "🎮")
        else -> listOf("▣", "🎮", "✦")
    }
    val device = when (platform.id) {
        "PS1" -> "◉"
        "PS2" -> "▰"
        "PS3" -> "▰"
        "XBOX" -> "▣"
        "XBOX360" -> "▰"
        "SNES" -> "▰"
        "MEGA" -> "▰"
        "PSP" -> "▱"
        else -> "▣"
    }
    Box(
        Modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(listOf(c1, Color(0xFF080A14), c2)))
            .border(
                if (selected && !reflection) 2.dp else 1.dp,
                if (selected && !reflection) Color(0xFF56C7FF) else c1.copy(alpha = .9f),
                RoundedCornerShape(7.dp)
            )
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).align(Alignment.TopCenter)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, c1, Color(0xFF70D7FF), c1, Color.Transparent))))
        Text(
            when (platform.id) {
                "PS1" -> "PLAYSTATION"
                "PS2" -> "PLAYSTATION 2"
                "PS3" -> "PLAYSTATION 3"
                "XBOX" -> "XBOX"
                "XBOX360" -> "XBOX 360"
                "SNES" -> "SUPER NINTENDO"
                "MEGA" -> "SEGA MEGA DRIVE"
                "PSP" -> "PLAYSTATION PORTABLE"
                "PC" -> "PC GAMING"
                "IMPORT" -> "IMPORTAR ROMS"
                "STORE" -> "LOJA NEXOS"
                "LIBRARY" -> "MINHA BIBLIOTECA"
                else -> platform.title
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 7.dp, vertical = 13.dp),
            color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
            letterSpacing = .8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Box(Modifier.fillMaxWidth().fillMaxHeight(.48f).align(Alignment.Center)
            .background(Brush.radialGradient(listOf(c1.copy(alpha = .62f), Color.Transparent))),
            contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                heroes.forEach { hero ->
                    Text(hero, fontSize = 29.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, c1, Color.White.copy(alpha = .9f), c1, Color.Transparent))))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(device, color = Color.White.copy(alpha = .9f), fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(platform.caption, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("NEXOS OS", color = Color.White.copy(alpha = .82f), fontSize = 7.sp, letterSpacing = 2.sp)
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .56f)))
        ))
    }
}

@Composable
private fun StoreCard(title: String, symbol: String) {
    Column(
        Modifier.width(150.dp).height(165.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF59327B), Color(0xFF171020))))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(2.dp))
        Text(symbol, color = Neon, fontSize = 43.sp, fontWeight = FontWeight.Black)
        Text(title, color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
