package com.personal.musicplayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

/** Full-screen "Now Playing": big art, scrubber, controls, queue. Uses a cheap gradient (no blur) to stay smooth. */
@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val song = vm.currentSong
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showQueue by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    val tint by produceState(Color.Transparent, song?.albumId) {
        val b = song?.let { ArtLoader.load(ctx, it, 300) }
        value = if (b != null) ArtLoader.tint(b) else Color.Transparent
    }

    if (sleepMenu) SleepTimerDialog(vm) { sleepMenu = false }

    Box(
        Modifier.fillMaxSize().background(cs.background)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.5f), Color.Transparent)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onClose) { Icon(Ic.ChevronDown, "Close", Modifier.size(32.dp)) }

            if (showQueue) {
                QueueView(vm, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val scale by animateFloatAsState(if (vm.isPlaying) 1f else 0.86f, tween(300), label = "art")
                    Artwork(
                        song,
                        Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale },
                        px = 600, corner = 14.dp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artist ?: "", fontSize = 18.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (song != null) {
                    IconButton(onClick = { vm.requestCover(PlayerViewModel.CoverTarget.ForSong(song)) }) {
                        Icon(Ic.Image, "Change cover", tint = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.toggleFavorite(song) }) {
                        val fav = vm.isFavorite(song)
                        Icon(if (fav) Ic.Heart else Ic.HeartOutline, "Favorite", tint = if (fav) cs.primary else cs.onSurfaceVariant)
                    }
                }
            }

            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = vm.durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = dragging ?: vm.positionMs.toFloat().coerceIn(0f, dur),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.seekTo(it.toLong()) }; dragging = null },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(
                    thumbColor = cs.onSurface,
                    activeTrackColor = cs.onSurface.copy(alpha = 0.8f),
                    inactiveTrackColor = cs.onSurface.copy(alpha = 0.2f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((dragging ?: vm.positionMs.toFloat()).toLong()), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text("-" + fmt(vm.durationMs - (dragging ?: vm.positionMs.toFloat()).toLong()), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, Modifier.size(64.dp)) { Icon(Ic.Prev, "Previous", Modifier.size(40.dp)) }
                IconButton(onClick = vm::togglePlay, Modifier.size(80.dp)) {
                    Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, "Play/Pause", Modifier.size(64.dp))
                }
                IconButton(onClick = vm::next, Modifier.size(64.dp)) { Icon(Ic.Next, "Next", Modifier.size(40.dp)) }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Ic.Shuffle, "Shuffle", tint = if (vm.shuffle) cs.primary else cs.onSurfaceVariant)
                }
                IconButton(onClick = { sleepMenu = true }) {
                    Icon(Ic.Clock, "Sleep timer", tint = if (vm.sleepRemainingSec > 0) cs.primary else cs.onSurfaceVariant)
                }
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(Ic.Queue, "Queue", tint = if (showQueue) cs.primary else cs.onSurfaceVariant)
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, "Repeat",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) cs.onSurfaceVariant else cs.primary,
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
        Text("Playing Next", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (vm.upNext.isEmpty()) {
            Text("Nothing queued after this song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.upNext, key = { it.index }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.jumpTo(e.index) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(e.song, Modifier.size(44.dp), 120)
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
