@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.proszczok.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.proszczok.player.data.AudioTrack
import pl.proszczok.player.data.asTime
import pl.proszczok.player.playback.EqualizerPreset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun MiniPlayer(viewModel: PlayerViewModel, track: AudioTrack, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.height(80.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onOpen,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                TrackArtwork(track, 224, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).clickable(onClickLabel = "Otwórz pełny odtwarzacz", onClick = onOpen)
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = "Teraz odtwarzane: ${track.title}, ${track.artist}. Otwórz pełny odtwarzacz"
                    },
            ) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, "Poprzedni utwór", Modifier.size(30.dp))
            }
            IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (viewModel.isPlaying) "Pauza" else "Wznów odtwarzanie",
                    Modifier.size(30.dp),
                )
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, "Następny utwór", Modifier.size(30.dp))
            }
        }
    }
}

@Composable
internal fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    track: AudioTrack,
    animationsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    var timerMenu by remember { mutableStateOf(false) }
    var equalizerOpen by remember { mutableStateOf(false) }
    var equalizerUnavailable by remember { mutableStateOf(false) }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    var dismissDragging by remember { mutableStateOf(false) }
    val animatedDismissDragY by animateFloatAsState(
        targetValue = if (animationsEnabled) dismissDragY.coerceAtLeast(0f) else 0f,
        animationSpec = if (dismissDragging) snap() else spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "Przeciągnięcie odtwarzacza",
    )
    BackHandler(onBack = onDismiss)
    Surface(
        Modifier.fillMaxSize().graphicsLayer {
            translationY = animatedDismissDragY
            val progress = (animatedDismissDragY / 900f).coerceIn(0f, 1f)
            alpha = 1f - progress * 0.24f
            scaleX = 1f - progress * 0.025f
            scaleY = scaleX
        },
        color = MaterialTheme.colorScheme.surface,
    ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            ) {
                val playerMaxWidth = maxWidth
                val playerMaxHeight = maxHeight
                val wide = playerMaxWidth >= 700.dp
                Column(Modifier.fillMaxSize()) {
                    PlayerHeader(onDismiss)
                    if (wide) {
                        val artSize = minOf(playerMaxWidth * 0.43f, playerMaxHeight - 112.dp)
                        Row(
                            Modifier.fillMaxSize().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerArtwork(
                                track,
                                viewModel,
                                onDismiss,
                                artSize,
                                animationsEnabled,
                            ) { offset, dragging ->
                                dismissDragY = offset
                                dismissDragging = dragging
                            }
                            PlayerDetails(
                                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                viewModel,
                                track,
                                timerMenu,
                                { timerMenu = it },
                                onOpenEqualizer = {
                                    if (viewModel.prepareEqualizer()) equalizerOpen = true else equalizerUnavailable = true
                                },
                            )
                        }
                    } else {
                        val artSize = minOf(playerMaxWidth - 8.dp, playerMaxHeight * 0.50f)
                        PlayerArtwork(
                            track,
                            viewModel,
                            onDismiss,
                            artSize,
                            animationsEnabled,
                        ) { offset, dragging ->
                            dismissDragY = offset
                            dismissDragging = dragging
                        }
                        Spacer(Modifier.height(12.dp))
                        PlayerDetails(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            viewModel,
                            track,
                            timerMenu,
                            { timerMenu = it },
                            onOpenEqualizer = {
                                if (viewModel.prepareEqualizer()) equalizerOpen = true else equalizerUnavailable = true
                            },
                        )
                    }
                }
            }
        }
    if (equalizerOpen) EqualizerDialog(viewModel) { equalizerOpen = false }
    if (equalizerUnavailable) {
        AlertDialog(
            onDismissRequest = { equalizerUnavailable = false },
            title = { Text("Korektor niedostępny") },
            text = { Text("Najpierw uruchom odtwarzanie utworu. Korektor działa na dźwięku odtwarzanym przez telefon.") },
            confirmButton = { TextButton(onClick = { equalizerUnavailable = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun PlayerHeader(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zminimalizuj odtwarzacz")
        }
        Text(
            "Teraz odtwarzane",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        CastRouteButton()
    }
}

@Composable
private fun PlayerArtwork(
    track: AudioTrack,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    artworkSize: Dp,
    animationsEnabled: Boolean,
    onDismissDrag: (offset: Float, dragging: Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val pageGap = 12.dp
    val pageDistance = with(LocalDensity.current) { (artworkSize + pageGap).toPx() }
    val settleOffset = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var gestureTrack by remember { mutableStateOf(track) }
    var gesturePreviousTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var gestureNextTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var displayedTrackOverride by remember { mutableStateOf<AudioTrack?>(null) }

    val idlePreviousTrack = viewModel.swipeTarget(next = false)
    val idleNextTrack = viewModel.swipeTarget(next = true)
    val gestureActive = dragging || settling
    val displayedTrack = if (gestureActive) gestureTrack else displayedTrackOverride ?: track
    val previousTrack = if (gestureActive) gesturePreviousTrack else idlePreviousTrack
    val nextTrack = if (gestureActive) gestureNextTrack else idleNextTrack
    val visibleOffset = when {
        !animationsEnabled -> 0f
        dragging -> dragX
        settling -> settleOffset.value
        else -> 0f
    }

    LaunchedEffect(track.id, displayedTrackOverride?.id) {
        val override = displayedTrackOverride ?: return@LaunchedEffect
        if (track.id == override.id) {
            displayedTrackOverride = null
        } else {
            // MediaController zwykle potwierdza zmianę w tej samej klatce. Ten
            // bezpiecznik nie pozwala pozostawić podglądu, gdy kolejka zmieni się
            // równocześnie z gestem albo odtwarzacz odrzuci nawigację.
            delay(750)
            displayedTrackOverride = null
        }
    }

    fun animateBackToCenter() {
        coroutineScope.launch {
            settleOffset.snapTo(dragX)
            settling = true
            dragging = false
            dragX = 0f
            settleOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
            )
            settling = false
        }
    }

    fun completeHorizontalSwipe(direction: Int, target: AudioTrack) {
        if (!animationsEnabled) {
            displayedTrackOverride = target
            dragging = false
            dragX = 0f
            dragY = 0f
            if (direction < 0) viewModel.swipeToNext() else viewModel.swipeToPrevious()
            return
        }
        coroutineScope.launch {
            settleOffset.snapTo(dragX)
            settling = true
            dragging = false
            settleOffset.animateTo(
                targetValue = direction * pageDistance,
                animationSpec = tween(durationMillis = 220),
            )

            // Gdy sąsiednia karta wypełnia kadr, staje się kartą bieżącą.
            // Wyzerowanie współrzędnych jest dzięki temu niewidoczne.
            gestureTrack = target
            displayedTrackOverride = target
            settleOffset.snapTo(0f)
            dragX = 0f
            dragY = 0f
            if (direction < 0) viewModel.swipeToNext() else viewModel.swipeToPrevious()
            settling = false
        }
    }

    val artworkShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier.size(artworkSize)
            .clip(artworkShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(track.id, animationsEnabled) {
                detectDragGestures(
                    onDragStart = {
                        if (settling) return@detectDragGestures
                        gestureTrack = displayedTrackOverride ?: track
                        gesturePreviousTrack = viewModel.swipeTarget(next = false)
                        gestureNextTrack = viewModel.swipeTarget(next = true)
                        dragging = true
                        dragX = 0f
                        dragY = 0f
                        onDismissDrag(0f, true)
                    },
                    onDrag = { change, amount ->
                        if (settling || !dragging) return@detectDragGestures
                        change.consume()
                        dragX = (dragX + amount.x).coerceIn(-pageDistance, pageDistance)
                        dragY += amount.y
                        onDismissDrag(dragY.coerceAtLeast(0f), true)
                    },
                    onDragEnd = {
                        if (settling || !dragging) return@detectDragGestures
                        val horizontalThreshold = maxOf(72.dp.toPx(), size.width * 0.18f)
                        val verticalThreshold = maxOf(90.dp.toPx(), size.height * 0.12f)
                        when {
                            dragY >= verticalThreshold && abs(dragY) > abs(dragX) * 1.15f -> {
                                val dismissOffset = dragY.coerceAtLeast(0f)
                                dragging = false
                                dragX = 0f
                                dragY = 0f
                                onDismissDrag(dismissOffset, false)
                                onDismiss()
                            }
                            dragX <= -horizontalThreshold && abs(dragX) > abs(dragY) &&
                                gestureNextTrack != null -> {
                                onDismissDrag(0f, false)
                                completeHorizontalSwipe(direction = -1, target = checkNotNull(gestureNextTrack))
                            }
                            dragX >= horizontalThreshold && abs(dragX) > abs(dragY) &&
                                gesturePreviousTrack != null -> {
                                onDismissDrag(0f, false)
                                completeHorizontalSwipe(direction = 1, target = checkNotNull(gesturePreviousTrack))
                            }
                            else -> {
                                dragY = 0f
                                onDismissDrag(0f, false)
                                if (animationsEnabled && dragX != 0f) animateBackToCenter()
                                else {
                                    dragging = false
                                    dragX = 0f
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (settling || !dragging) return@detectDragGestures
                        dragY = 0f
                        onDismissDrag(0f, false)
                        if (animationsEnabled && dragX != 0f) animateBackToCenter()
                        else {
                            dragging = false
                            dragX = 0f
                        }
                    },
                )
            }
            .semantics {
                contentDescription = "Okładka utworu ${track.title}. Przesuń w lewo lub w prawo, aby zmienić utwór; przesuń w dół, aby zminimalizować odtwarzacz."
                customActions = listOf(
                    CustomAccessibilityAction("Następny utwór") { viewModel.swipeToNext(); true },
                    CustomAccessibilityAction("Poprzedni utwór") { viewModel.swipeToPrevious(); true },
                    CustomAccessibilityAction("Zminimalizuj odtwarzacz") { onDismiss(); true },
                )
            },
    ) {
        if (animationsEnabled) {
            previousTrack?.let {
                ArtworkPage(it, visibleOffset - pageDistance, pageDistance, artworkShape)
            }
            nextTrack?.let {
                ArtworkPage(it, visibleOffset + pageDistance, pageDistance, artworkShape)
            }
        }
        ArtworkPage(displayedTrack, visibleOffset, pageDistance, artworkShape)
    }
}

@Composable
private fun ArtworkPage(
    track: AudioTrack,
    translation: Float,
    pageDistance: Float,
    shape: RoundedCornerShape,
) {
    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            translationX = translation
            val pageProgress = (translation / pageDistance).coerceIn(-1f, 1f)
            rotationZ = pageProgress * 3f
            val pageScale = 1f - abs(pageProgress) * 0.025f
            scaleX = pageScale
            scaleY = pageScale
        },
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        TrackArtwork(track, 1200, Modifier.fillMaxSize())
    }
}

@Composable
private fun PlayerDetails(
    modifier: Modifier,
    viewModel: PlayerViewModel,
    track: AudioTrack,
    timerMenu: Boolean,
    onTimerMenu: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Slider(
            value = viewModel.positionMs.coerceAtMost(viewModel.durationMs).toFloat(),
            onValueChange = { viewModel.seekTo(it.toLong()) },
            valueRange = 0f..viewModel.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.semantics {
                contentDescription = "Pozycja odtwarzania"
                stateDescription = "${viewModel.positionMs.asTime()} z ${viewModel.durationMs.asTime()}"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(viewModel.positionMs.asTime(), style = MaterialTheme.typography.labelMedium)
            Text(viewModel.durationMs.asTime(), style = MaterialTheme.typography.labelMedium)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    if (viewModel.shuffle) "Wyłącz odtwarzanie losowe" else "Włącz odtwarzanie losowe",
                    tint = if (viewModel.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Default.SkipPrevious, "Poprzedni utwór", Modifier.size(38.dp))
            }
            FloatingActionButton(onClick = viewModel::togglePlayPause, shape = CircleShape, modifier = Modifier.size(76.dp)) {
                Icon(
                    if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (viewModel.isPlaying) "Pauza" else "Odtwórz",
                    Modifier.size(42.dp),
                )
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Default.SkipNext, "Następny utwór", Modifier.size(38.dp))
            }
            IconButton(onClick = viewModel::cycleRepeat, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (viewModel.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    when (viewModel.repeatMode) {
                        Player.REPEAT_MODE_OFF -> "Powtarzanie wyłączone"
                        Player.REPEAT_MODE_ALL -> "Powtarzanie kolejki"
                        else -> "Powtarzanie jednego utworu"
                    },
                    tint = if (viewModel.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AssistChip(
                    onClick = { viewModel.toggleFavorite(track) },
                    label = { Text(if (track.id in viewModel.favoriteIds) "Ulubiony" else "Ulubione") },
                    leadingIcon = {
                        Icon(
                            if (track.id in viewModel.favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            Modifier.size(18.dp),
                        )
                    },
                )
            }
            item {
                AssistChip(
                    onClick = onOpenEqualizer,
                    label = { Text("Korektor") },
                    leadingIcon = { Icon(Icons.Default.Equalizer, null, Modifier.size(18.dp)) },
                )
            }
            item {
                Box {
                    AssistChip(
                        onClick = { onTimerMenu(true) },
                        label = { Text(viewModel.sleepTimerMinutes?.let { "$it min" } ?: "Timer") },
                        leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = timerMenu, onDismissRequest = { onTimerMenu(false) }) {
                        listOf<Int?>(null, 15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(minutes?.let { "$it minut" } ?: "Wyłącz timer") },
                                onClick = { viewModel.setSleepTimer(minutes); onTimerMenu(false) },
                            )
                        }
                    }
                }
            }
            item { CastRouteButton() }
        }
    }
}

@Composable
private fun EqualizerDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val revision = viewModel.equalizerRevision
    val enabled = viewModel.equalizerEnabled()
    val bands = viewModel.equalizerBands()
    val selectedPreset = viewModel.equalizerPreset()
    val polishLocale = Locale.forLanguageTag("pl-PL")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Korektor dźwięku") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = viewModel::setEqualizerEnabled,
                    ).semantics {
                        stateDescription = if (enabled) "Włączony" else "Wyłączony"
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Włącz korektor", Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = null,
                    )
                }
                Text("Preset", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(EqualizerPreset.entries.size) { index ->
                        val preset = EqualizerPreset.entries[index]
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { viewModel.applyEqualizerPreset(preset) },
                            label = { Text(preset.label) },
                            modifier = Modifier.semantics {
                                contentDescription = "Preset korektora ${preset.label}"
                                stateDescription = if (selectedPreset == preset) "Wybrany" else "Niewybrany"
                            },
                        )
                    }
                }
                bands.forEach { band ->
                    val frequency = if (band.centerHz < 1000) "${band.centerHz} Hz"
                    else String.format(polishLocale, "%.1f kHz", band.centerHz / 1000f)
                    val decibels = band.level / 100f
                    Text("$frequency, ${String.format(polishLocale, "%.1f", decibels)} dB")
                    Slider(
                        value = band.level.toFloat(),
                        onValueChange = { viewModel.setEqualizerBand(band.index, it.roundToInt().toShort()) },
                        valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                        enabled = enabled,
                        modifier = Modifier.semantics {
                            contentDescription = "Pasmo $frequency"
                            stateDescription = "${String.format(polishLocale, "%.1f", decibels)} decybela"
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
        dismissButton = { TextButton(onClick = viewModel::resetEqualizer) { Text("Wyzeruj") } },
    )
}
