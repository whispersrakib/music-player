package com.personal.musicplayer

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** Thick rounded bar you can tap or drag (used for progress and volume). */
@Composable
private fun SeekBar(fraction: Float, onChange: (Float) -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier, thickness: Dp = 8.dp) {
    var width by remember { mutableIntStateOf(1) }
    val c = MaterialTheme.colorScheme.onSurface
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / width).coerceIn(0f, 1f)); onFinish() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onChange((it.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { onFinish() },
                    onDragCancel = { onFinish() },
                ) { change, _ -> change.consume(); onChange((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(RoundedCornerShape(thickness / 2)).background(c.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thickness).clip(RoundedCornerShape(thickness / 2)).background(c.copy(alpha = 0.75f)))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, tint: Color? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.15f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(22.dp), tint = tint ?: cs.onSurface) }
}

/** Full-screen Now Playing. Uses a cheap colour gradient from the cover (no blur) to stay smooth on a low-end phone. */
@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val song = vm.currentSong
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showQueue by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }

    val tint by produceState(Color.Transparent, song?.albumId, song?.id, ArtLoader.version) {
        val b = song?.let { ArtLoader.load(ctx, it, 300) }
        value = if (b != null) ArtLoader.tint(b) else Color.Transparent
    }

    if (sleepDialog) SleepTimerDialog(vm) { sleepDialog = false }
    if (addDialog && song != null) AddToPlaylistDialog(vm, song) { addDialog = false }

    Box(
        Modifier.fillMaxSize().background(cs.background)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.25f))))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // grabber: tap or swipe down to close
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 80f) onClose() }) { _, d -> total += d }
                    }
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(cs.onSurface.copy(alpha = 0.35f)))
            }

            if (showQueue) {
                QueueView(vm, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val scale by animateFloatAsState(if (vm.isPlaying) 1f else 0.86f, tween(300), label = "art")
                    Artwork(
                        song,
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .shadow(20.dp, RoundedCornerShape(12.dp)),
                        px = 600, corner = 12.dp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artist ?: "", fontSize = 21.sp, color = cs.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (song != null) {
                    val fav = vm.isFavorite(song)
                    RoundIcon(if (fav) Ic.Star else Ic.StarOutline, "Favorite", if (fav) cs.primary else null) { vm.toggleFavorite(song) }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        RoundIcon(Ic.More, "More") { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Add to Playlist…") }, onClick = { menu = false; addDialog = true })
                            DropdownMenuItem(text = { Text("Change Cover Image…") }, onClick = { menu = false; vm.requestCover(PlayerViewModel.CoverTarget.ForSong(song)) })
                            if (vm.hasCustomCover(song)) {
                                DropdownMenuItem(text = { Text("Remove Custom Cover") }, onClick = { menu = false; vm.removeCover(PlayerViewModel.CoverTarget.ForSong(song)) })
                            }
                            DropdownMenuItem(text = { Text("Sleep Timer…") }, onClick = { menu = false; sleepDialog = true })
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = vm.durationMs.coerceAtLeast(1L).toFloat()
            val frac = (dragging ?: (vm.positionMs / dur)).coerceIn(0f, 1f)
            SeekBar(
                frac,
                onChange = { dragging = it },
                onFinish = { dragging?.let { vm.seekTo((it * dur).toLong()) }; dragging = null },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((frac * dur).toLong()), fontSize = 14.sp, color = cs.onSurface.copy(alpha = 0.55f))
                Text("-" + fmt(vm.durationMs - (frac * dur).toLong()), fontSize = 14.sp, color = cs.onSurface.copy(alpha = 0.55f))
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, Modifier.size(72.dp)) { Icon(Ic.Rewind, "Previous", Modifier.size(52.dp)) }
                IconButton(onClick = vm::togglePlay, Modifier.size(88.dp)) {
                    Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, "Play/Pause", Modifier.size(72.dp))
                }
                IconButton(onClick = vm::next, Modifier.size(72.dp)) { Icon(Ic.Forward, "Next", Modifier.size(52.dp)) }
            }

            // volume
            val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
            var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.VolumeLow, null, Modifier.size(22.dp), tint = cs.onSurface.copy(alpha = 0.6f))
                SeekBar(
                    vol,
                    onChange = { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt(), 0) },
                    onFinish = {},
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    thickness = 8.dp,
                )
                Icon(Ic.VolumeHigh, null, Modifier.size(22.dp), tint = cs.onSurface.copy(alpha = 0.6f))
            }

            val muted = cs.onSurface.copy(alpha = 0.55f)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::toggleShuffle) { Icon(Ic.Shuffle, "Shuffle", tint = if (vm.shuffle) cs.primary else muted) }
                IconButton(onClick = { sleepDialog = true }) { Icon(Ic.Clock, "Sleep timer", tint = if (vm.sleepRemainingSec > 0) cs.primary else muted) }
                IconButton(onClick = { showQueue = !showQueue }) { Icon(Ic.Queue, "Queue", tint = if (showQueue) cs.primary else muted) }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, "Repeat",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) muted else cs.primary,
                    )
                }
            }
        }
    }
}

/** Pick any time from 1 to 60 minutes. */
@Composable
private fun SleepTimerDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf(if (vm.sleepRemainingSec > 0) (vm.sleepRemainingSec + 59) / 60 else 15) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    if (minutes == 1) "1 minute" else "$minutes minutes",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceIn(1, 60) },
                    valueRange = 1f..60f,
                    steps = 58,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 min", fontSize = 12.sp); Text("60 min", fontSize = 12.sp)
                }
                if (vm.sleepRemainingSec > 0) {
                    Text("Music stops in ${vm.sleepRemainingSec / 60 + 1} min", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setSleepTimer(minutes); onDismiss() }) { Text("Start") } },
        dismissButton = {
            Row {
                if (vm.sleepRemainingSec > 0) TextButton(onClick = { vm.setSleepTimer(0); onDismiss() }) { Text("Turn off") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun QueueView(vm: PlayerViewModel, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Playing Next", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (vm.upNext.isEmpty()) {
            Text("Nothing queued after this song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.upNext, key = { it.index }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.jumpTo(e.index) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(e.song, Modifier.size(48.dp), 120)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(e.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                        Text(e.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.removeFromQueue(e.index) }) {
                        Icon(Ic.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
