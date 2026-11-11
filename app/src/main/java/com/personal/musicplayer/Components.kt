package com.personal.musicplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Square album art from the MP3 tags, with a music-note placeholder. */
@Composable
fun Artwork(song: Song?, modifier: Modifier = Modifier, px: Int = 160, corner: Dp = 6.dp, playlistId: String? = null) {
    val ctx = LocalContext.current
    val ver = ArtLoader.version
    val bmp by produceState(if (playlistId == null) song?.let { ArtLoader.cached(it, px) } else null, song?.id, playlistId, px, ver) {
        val custom = if (playlistId != null) ArtLoader.loadPlaylist(ctx, playlistId, px) else null
        value = custom ?: if (song == null) null else ArtLoader.cached(song, px) ?: ArtLoader.load(ctx, song, px)
    }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Ic.Note, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Play", Ic.Play, onPlay), Triple("Shuffle", Ic.Shuffle, onShuffle)).forEach { (label, icon, click) ->
            Button(
                onClick = click,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One song line: art, title, artist, and a ⋯ menu (play next / queue / playlist). */
@Composable
fun SongRow(
    song: Song,
    vm: PlayerViewModel,
    onClick: () -> Unit,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    val current = vm.currentSong?.id == song.id
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(48.dp), 120)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                song.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                song.artist, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Ic.More, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play Next") }, onClick = { menu = false; vm.playNext(song) })
                DropdownMenuItem(text = { Text("Add to Queue") }, onClick = { menu = false; vm.addToQueue(song) })
                DropdownMenuItem(text = { Text("Add to Playlist…") }, onClick = { menu = false; addDialog = true })
                DropdownMenuItem(
                    text = { Text(if (vm.isFavorite(song)) "Remove from Favorites" else "Add to Favorites") },
                    onClick = { menu = false; vm.toggleFavorite(song) },
                )
                DropdownMenuItem(
                    text = { Text("Change Cover Image…") },
                    onClick = { menu = false; vm.requestCover(PlayerViewModel.CoverTarget.ForSong(song)) },
                )
                if (vm.hasCustomCover(song)) {
                    DropdownMenuItem(
                        text = { Text("Remove Custom Cover") },
                        onClick = { menu = false; vm.removeCover(PlayerViewModel.CoverTarget.ForSong(song)) },
                    )
                }
                extraMenu?.invoke { menu = false }
            }
        }
    }
    if (addDialog) AddToPlaylistDialog(vm, song) { addDialog = false }
}

@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true)
                } else {
                    Text(
                        "＋ New Playlist", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                    )
                    vm.playlists.forEach { p ->
                        Text(
                            p.name,
                            modifier = Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id, song); onDismiss() }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { vm.createPlaylist(name, song); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onCreate(name); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small floating player above the tab bar. Tap to open Now Playing. */
@Composable
fun MiniPlayer(vm: PlayerViewModel, onClick: () -> Unit) {
    val song = vm.currentSong ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(44.dp), 120)
        Text(
            song.title, Modifier.weight(1f).padding(horizontal = 12.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = vm::togglePlay) { Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, "Play/Pause") }
        IconButton(onClick = vm::next) { Icon(Ic.Next, "Next") }
    }
}

@Composable
fun EmptyState(scanning: Boolean, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Ic.Note, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("No music found", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy MP3 files into the phone's Music folder, then tap Scan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRescan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan for music") }
    }
}
