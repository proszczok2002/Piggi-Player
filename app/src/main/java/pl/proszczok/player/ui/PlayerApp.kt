@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.proszczok.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import android.app.Activity
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.launch
import pl.proszczok.player.BuildConfig
import pl.proszczok.player.ui.theme.AppThemeMode
import pl.proszczok.player.update.InstallResult
import pl.proszczok.player.update.UpdateInfo
import pl.proszczok.player.update.UpdateCheckResult
import pl.proszczok.player.update.UpdateManager

internal enum class Section(val label: String, val icon: ImageVector) {
    LIBRARY("Biblioteka", Icons.Default.LibraryMusic),
    FOLDERS("Katalogi", Icons.Default.Folder),
    PLAYLISTS("Playlisty", Icons.AutoMirrored.Filled.QueueMusic),
    SETTINGS("Ustawienia", Icons.Default.Settings),
}

@Composable
fun PlayerApp(
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    themeMode: AppThemeMode,
    onThemeMode: (AppThemeMode) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsEnabled: (Boolean) -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    var section by remember { mutableStateOf(Section.LIBRARY) }
    var searchVisible by remember { mutableStateOf(false) }
    var fullPlayer by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaOperationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onMediaOperationAuthorizationResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel.mediaOperationRequest) {
        viewModel.mediaOperationRequest?.let { sender ->
            mediaOperationLauncher.launch(IntentSenderRequest.Builder(sender).build())
            viewModel.consumeMediaOperationRequest()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setUiVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setUiVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setUiVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setUiVisible(false)
        }
    }

    LaunchedEffect(hasAudioPermission) { if (hasAudioPermission) viewModel.refreshLibrary() }
    fun checkUpdates(showStatus: Boolean) {
        if (updateChecking) return
        scope.launch {
            updateChecking = true
            when (val result = UpdateManager.checkResult()) {
                is UpdateCheckResult.Available -> updateInfo = result.info
                UpdateCheckResult.UpToDate -> if (showStatus) {
                    updateStatusMessage = "Masz najnowszą wersję Piggi Player — wersja ${BuildConfig.VERSION_NAME}."
                }
                is UpdateCheckResult.Failed -> if (showStatus) {
                    updateStatusMessage = "Nie udało się sprawdzić aktualizacji. ${result.message}"
                }
            }
            updateChecking = false
        }
    }

    LaunchedEffect(Unit) {
        when (val result = UpdateManager.checkResult()) {
            is UpdateCheckResult.Available -> updateInfo = result.info
            else -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val expanded = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) AppNavigationRail(section) { section = it }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            if (searchVisible) OutlinedTextField(
                                value = viewModel.query,
                                onValueChange = { viewModel.query = it },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Szukaj w bibliotece" },
                                singleLine = true,
                                placeholder = { Text("Tytuł, wykonawca, album lub katalog") },
                            ) else Text("Piggi Player", modifier = Modifier.semantics { heading() })
                        },
                        actions = {
                            IconButton(onClick = {
                                searchVisible = !searchVisible
                                if (!searchVisible) viewModel.query = ""
                            }) {
                                Icon(
                                    if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                    if (searchVisible) "Zamknij wyszukiwanie" else "Szukaj",
                                )
                            }
                            CastRouteButton()
                        },
                    )
                },
                bottomBar = {
                    Column {
                        viewModel.currentTrack?.let { MiniPlayer(viewModel, it) { fullPlayer = true } }
                        if (!expanded) AppNavigationBar(section) { section = it }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (!hasAudioPermission) PermissionScreen(onRequestPermission)
                    else when (section) {
                        Section.LIBRARY -> LibraryScreen(viewModel)
                        Section.FOLDERS -> GroupedScreen(viewModel, { it.folder }, "Katalogi")
                        Section.PLAYLISTS -> PlaylistsScreen(viewModel)
                        Section.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            onThemeMode = onThemeMode,
                            animationsEnabled = animationsEnabled,
                            onAnimationsEnabled = onAnimationsEnabled,
                            updateChecking = updateChecking,
                            onCheckUpdates = { checkUpdates(true) },
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = fullPlayer && viewModel.currentTrack != null,
        enter = if (animationsEnabled) {
            slideInVertically(tween(280)) { it } + fadeIn(tween(220))
        } else {
            EnterTransition.None
        },
        exit = if (animationsEnabled) {
            slideOutVertically(tween(240)) { it } + fadeOut(tween(180))
        } else {
            ExitTransition.None
        },
    ) {
        viewModel.currentTrack?.let { track ->
            FullPlayerScreen(viewModel, track, animationsEnabled) { fullPlayer = false }
        }
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!updateBusy) updateInfo = null },
            title = { Text("Dostępna aktualizacja ${info.versionName}") },
            text = {
                Column {
                    Text(info.notes)
                    if (updateBusy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Pobieranie i weryfikacja…")
                    }
                    updateMessage?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !updateBusy, onClick = {
                    updateBusy = true
                    updateMessage = null
                    scope.launch {
                        when (val result = UpdateManager.downloadAndInstall(context, info)) {
                            InstallResult.Started -> updateInfo = null
                            InstallResult.PermissionRequired -> updateMessage = "Zezwól na instalowanie aplikacji, wróć i naciśnij „Aktualizuj” ponownie."
                            is InstallResult.Failed -> updateMessage = result.message
                        }
                        updateBusy = false
                    }
                }) { Text("Aktualizuj") }
            },
            dismissButton = { TextButton(enabled = !updateBusy, onClick = { updateInfo = null }) { Text("Później") } },
        )
    }

    updateStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateStatusMessage = null },
            title = { Text("Aktualizacje") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { updateStatusMessage = null }) { Text("OK") }
            },
        )
    }

    viewModel.mediaOperationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMediaOperationMessage,
            title = { Text("Zarządzanie plikami") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMediaOperationMessage) { Text("OK") }
            },
        )
    }
}

@Composable
private fun AppNavigationBar(selected: Section, onSelect: (Section) -> Unit) {
    NavigationBar {
        Section.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: Section, onSelect: (Section) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
        Spacer(Modifier.height(64.dp))
        Section.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Dostęp do muzyki", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Text(
                "Aplikacja potrzebuje dostępu wyłącznie do plików audio na tym urządzeniu. Nie wysyła biblioteki do chmury.",
                modifier = Modifier.padding(vertical = 16.dp),
            )
            TextButton(onClick = onRequest) { Text("Zezwól na dostęp") }
        }
    }
}

@Composable
internal fun CastRouteButton() {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                contentDescription = "Połącz z urządzeniem Chromecast"
                minimumWidth = 48.dp.value.toInt()
                minimumHeight = 48.dp.value.toInt()
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
            }
        },
        update = { it.contentDescription = "Połącz z urządzeniem Chromecast" },
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = "Połącz z urządzeniem Chromecast"
        },
    )
}
