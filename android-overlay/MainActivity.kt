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
    Platform("STORE", "LOJA NEXOS", "⌑", "LOJA", listOf(Color(0xFF713E92), Color(0xFF171020)), "store")
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
            if (screen == "home") {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(42.dp)) {
                        Text("NEX", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("OS", color = Neon, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("CYBER ARCADE  //  GAME LIBRARY", color = Muted, fontSize = 8.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showSettings = true }) {
                            androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Neon)
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        val current = platforms.firstOrNull { it.id == selectedId } ?: platforms[1]
                        Text(current.caption, color = current.colors.first().copy(alpha = .10f),
                            fontSize = 100.sp, fontWeight = FontWeight.Black, letterSpacing = (-7).sp, maxLines = 1)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            platforms.forEachIndexed { index, platform ->
                                val selected = selectedId == platform.id
                                val distance = index - platforms.indexOfFirst { it.id == selectedId }
                                Column(
                                    Modifier.width(190.dp).height(285.dp)
                                        .graphicsLayer {
                                            rotationY = when {
                                                selected -> 0f
                                                distance < 0 -> 24f
                                                else -> -24f
                                            }
                                            cameraDistance = 18f * density
                                            val scale = if (selected) 1f else .82f
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = if (selected) 1f else .78f
                                        }
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(if (selected) 2.dp else 1.dp,
                                            if (selected) Neon else Color.White.copy(alpha = .22f),
                                            RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(listOf(
                                            platform.colors.first(), Color(0xFF191321), platform.colors.last()
                                        )))
                                        .clickable {
                                            if (selectedId == platform.id && awaitingSecondTap) {
                                                uiSound(true)
                                                openPlatform(platform)
                                            } else {
                                                selectedId = platform.id
                                                awaitingSecondTap = true
                                                uiSound()
                                            }
                                        }
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("NEXOS", color = Neon, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                        Text("0${index + 1}", color = Muted, fontSize = 9.sp)
                                    }
                                    Box(
                                        Modifier.fillMaxWidth().weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Brush.radialGradient(listOf(
                                                platform.colors.first().copy(alpha = .9f), Color(0xFF100D17)
                                            ))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(platform.symbol, color = White, fontSize = 76.sp, fontWeight = FontWeight.Black)
                                        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center)
                                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Neon, Color(0xFF35E7FF), Color.Transparent))))
                                        Text(platform.caption, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                            color = Neon, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(platform.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(5.dp))
                                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                                            .background(if (selected) Neon else Color.White.copy(alpha = .18f)))
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("DESLIZE PARA OS LADOS  •  UM TOQUE SELECIONA  •  DOIS TOQUES ENTRAM",
                            color = Muted, fontSize = 8.sp, letterSpacing = .5.sp)
                        Text("NEXOS OS  //  v0.4", color = Neon.copy(alpha = .8f), fontSize = 8.sp, letterSpacing = 1.sp)
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
