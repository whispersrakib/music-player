package com.personal.musicplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Square album art from the MP3 tags (or a custom cover), with a music-note placeholder. */
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
fun Divider(start: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Round dark button (back, more…). */
@Composable
fun CircleButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(24.dp)) }
}

/** Top row of a page: round back button on the left, extra buttons on the right. */
@Composable
fun PageTop(onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) CircleButton(Ic.ChevronLeft, "Back", onBack)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun LargeTitle(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp))
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Play", Ic.Play, onPlay), Triple("Shuffle", Ic.Shuffle, onShuffle)).forEach { (label, icon, click) ->
            Button(
                onClick = click,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One song line: art, title, artist, and a ⋯ menu. */
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
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song, Modifier.size(56.dp), 150)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    song.title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    song.artist, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Ic.More, "More")
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
        Divider(start = 86.dp)
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

/** Full-screen picker: tap songs to add them to / remove them from a playlist. */
@Composable
fun AddMusicDialog(vm: PlayerViewModel, playlistId: String, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val playlist = vm.playlists.firstOrNull { it.id == playlistId } ?: return
    val ids = playlist.songIds.toSet()
    val list = remember(q, vm.songs) {
        val s = q.trim().lowercase()
        if (s.isEmpty()) vm.songs else vm.songs.filter { s in it.title.lowercase() || s in it.artist.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add Music", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done", fontSize = 17.sp) }
                }
                OutlinedTextField(
                    q, { q = it }, singleLine = true, placeholder = { Text("Search your songs") },
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { s ->
                        val inList = s.id in ids
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (inList) vm.removeFromPlaylist(playlistId, s) else vm.addToPlaylist(playlistId, s) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(s, Modifier.size(48.dp), 120)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                                Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (inList) Icon(Ic.Check, "Added", tint = MaterialTheme.colorScheme.primary)
                            else Icon(Ic.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** Floating rounded player above the tab bar. Tap to open Now Playing. */
@Composable
fun MiniPlayer(vm: PlayerViewModel, onClick: () -> Unit) {
    val song = vm.currentSong ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(48.dp), 120)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = vm::togglePlay) { Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, "Play/Pause", Modifier.size(30.dp)) }
        IconButton(onClick = vm::next) { Icon(Ic.Forward, "Next", Modifier.size(30.dp)) }
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
