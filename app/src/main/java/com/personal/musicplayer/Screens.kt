package com.personal.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Screen {
    data object Playlists : Screen
    data object Artists : Screen
    data object Albums : Screen
    data object Songs : Screen
    data class Album(val id: Long) : Screen
    data class Artist(val name: String) : Screen
    data class PlaylistScreen(val id: String) : Screen
}

private fun footer(songs: List<Song>): String {
    val minutes = (songs.sumOf { it.durationMs } / 60000).toInt().coerceAtLeast(if (songs.isEmpty()) 0 else 1)
    return "${songs.size} song${if (songs.size == 1) "" else "s"}, $minutes minutes"
}

// ---------------- Library (home) ----------------
@Composable
fun LibraryTab(vm: PlayerViewModel, open: (Screen) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val recent = remember(vm.albums) { vm.albums.sortedByDescending { it.newest }.take(10).chunked(2) }
    val rows: List<Triple<String, ImageVector, Screen>> = listOf(
        Triple("Playlists", Ic.Queue, Screen.Playlists),
        Triple("Artists", Ic.Person, Screen.Artists),
        Triple("Albums", Ic.Album, Screen.Albums),
        Triple("Songs", Ic.Note, Screen.Songs),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    CircleButton(Ic.More, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (vm.scanning) "Scanning…" else "Rescan music") },
                            onClick = { menu = false; vm.rescan() },
                        )
                    }
                }
            }
        }
        items(rows, key = { it.first }) { (label, icon, screen) ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { open(screen) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 18.dp))
                    Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider(start = 64.dp)
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Recently Added") }
            items(recent, key = { it.first().id }) { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { a -> AlbumTile(a, Modifier.weight(1f)) { open(Screen.Album(a.id)) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(a: AlbumItem, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Artwork(a.cover, Modifier.fillMaxWidth().aspectRatio(1f), 300, 10.dp)
        Text(a.name, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
        Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Playlists ----------------
@Composable
fun PlaylistsPage(vm: PlayerViewModel, onBack: () -> Unit, open: (Screen) -> Unit) {
    var newDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop(onBack) { CircleButton(Ic.Add, "New playlist") { newDialog = true } } }
        item { LargeTitle("Playlists") }
        if (vm.playlists.isEmpty()) {
            item { Text("No playlists yet. Tap + to create one.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(vm.playlists, key = { it.id }) { p ->
            val songs = vm.playlistSongs(p)
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { open(Screen.PlaylistScreen(p.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(songs.firstOrNull(), Modifier.size(76.dp), 200, 8.dp, playlistId = p.id)
                    Text(p.name, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                    Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider(start = 108.dp)
            }
        }
    }
    if (newDialog) NewPlaylistDialog({ vm.createPlaylist(it) }) { newDialog = false }
}

// ---------------- Songs ----------------
@Composable
fun SongsPage(vm: PlayerViewModel, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop(onBack) }
        item { LargeTitle("Songs") }
        item { PlayShuffleRow({ vm.play(vm.songs, 0) }, { vm.shufflePlay(vm.songs) }) }
        itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(vm.songs, i) }) }
    }
}

// ---------------- Albums ----------------
@Composable
fun AlbumsPage(vm: PlayerViewModel, onBack: () -> Unit, open: (Screen) -> Unit) {
    val rows = remember(vm.albums) { vm.albums.chunked(2) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop(onBack) }
        item { LargeTitle("Albums") }
        items(rows, key = { it.first().id }) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { a -> AlbumTile(a, Modifier.weight(1f)) { open(Screen.Album(a.id)) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------- Artists ----------------
@Composable
fun ArtistsPage(vm: PlayerViewModel, onBack: () -> Unit, open: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop(onBack) }
        item { LargeTitle("Artists") }
        items(vm.artists, key = { it.name }) { a -> ArtistRow(a) { open(Screen.Artist(a.name)) } }
    }
}

@Composable
private fun ArtistRow(a: ArtistItem, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(a.songs.first(), Modifier.size(64.dp), 150, 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(a.name, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.albumCount} albums · ${a.songs.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Divider(start = 96.dp)
    }
}

// ---------------- Favorites ----------------
@Composable
fun FavoritesTab(vm: PlayerViewModel) {
    val songs = vm.favoriteSongs
    LazyColumn(Modifier.fillMaxSize()) {
        item { LargeTitle("Favorites") }
        if (songs.isEmpty()) {
            item { Text("Tap the star on the Now Playing screen to add favorites.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { PlayShuffleRow({ vm.play(songs, 0) }, { vm.shufflePlay(songs) }) }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(songs, i) }) }
        }
    }
}

// ---------------- Search ----------------
@Composable
fun SearchTab(vm: PlayerViewModel, open: (Screen) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim().lowercase()
    val songs = remember(query, vm.songs) {
        if (query.isEmpty()) emptyList() else vm.songs.filter { query in it.title.lowercase() || query in it.artist.lowercase() || query in it.album.lowercase() }
    }
    val albums = remember(query, vm.albums) { if (query.isEmpty()) emptyList() else vm.albums.filter { query in it.name.lowercase() }.take(10) }
    val artists = remember(query, vm.artists) { if (query.isEmpty()) emptyList() else vm.artists.filter { query in it.name.lowercase() }.take(10) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { LargeTitle("Search") }
        item {
            OutlinedTextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Ic.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Ic.Close, "Clear") } },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            items(artists, key = { "a" + it.name }) { a -> ArtistRow(a) { open(Screen.Artist(a.name)) } }
        }
        if (albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            items(albums, key = { "b" + it.id }) { a ->
                Column {
                    Row(Modifier.fillMaxWidth().clickable { open(Screen.Album(a.id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(a.cover, Modifier.size(56.dp), 150)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(a.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.artist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider(start = 86.dp)
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionTitle("Songs") }
            itemsIndexed(songs, key = { _, s -> "s" + s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(songs, i) }) }
        }
    }
}

// ---------------- Detail pages (big centered art, Play / Shuffle, songs) ----------------
@Composable
private fun DetailPage(
    onBack: () -> Unit,
    vm: PlayerViewModel,
    songs: List<Song>,
    title: String,
    subtitle: String?,
    art: @Composable (Modifier) -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    onAddMusic: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            PageTop(onBack) {
                if (menu != null) {
                    Box {
                        CircleButton(Ic.More, "More") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                art(Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
                Text(title, Modifier.padding(top = 18.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        item { PlayShuffleRow({ vm.play(songs, 0, false) }, { vm.shufflePlay(songs) }) }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            if (onRemove == null) {
                SongRow(s, vm, onClick = { vm.play(songs, i) })
            } else {
                SongRow(s, vm, onClick = { vm.play(songs, i) }, extraMenu = { dismiss ->
                    DropdownMenuItem(text = { Text("Remove from Playlist") }, onClick = { dismiss(); onRemove(s) })
                })
            }
        }
        if (onAddMusic != null) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onAddMusic).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Ic.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Add Music", fontSize = 18.sp, modifier = Modifier.padding(start = 14.dp))
                    }
                    Divider(start = 86.dp)
                }
            }
        }
        item { Text(footer(songs), Modifier.padding(16.dp), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun AlbumDetail(vm: PlayerViewModel, id: Long, onBack: () -> Unit) {
    val a = vm.albums.firstOrNull { it.id == id } ?: return onBack()
    DetailPage(onBack, vm, a.songs, a.name, a.artist, art = { m -> Artwork(a.cover, m, 600, 14.dp) })
}

@Composable
fun ArtistDetail(vm: PlayerViewModel, name: String, onBack: () -> Unit) {
    val a = vm.artists.firstOrNull { it.name == name } ?: return onBack()
    DetailPage(onBack, vm, a.songs, a.name, "${a.songs.size} songs", art = { m -> Artwork(a.songs.first(), m, 600, 1000.dp) })
}

@Composable
fun PlaylistDetail(vm: PlayerViewModel, id: String, onBack: () -> Unit) {
    val p = vm.playlists.firstOrNull { it.id == id } ?: return onBack()
    val songs = vm.playlistSongs(p)
    val ctx = LocalContext.current
    var addMusic by remember { mutableStateOf(false) }
    DetailPage(
        onBack, vm, songs, p.name, null,
        art = { m -> Artwork(songs.firstOrNull(), m, 600, 14.dp, playlistId = p.id) },
        menu = { dismiss ->
            DropdownMenuItem(text = { Text("Change Cover Image…") }, onClick = { dismiss(); vm.requestCover(PlayerViewModel.CoverTarget.ForPlaylist(p.id)) })
            if (ArtLoader.version >= 0 && CustomArt.file(ctx, "p_${p.id}").exists()) {
                DropdownMenuItem(text = { Text("Remove Cover") }, onClick = { dismiss(); vm.removeCover(PlayerViewModel.CoverTarget.ForPlaylist(p.id)) })
            }
            DropdownMenuItem(text = { Text("Delete Playlist") }, onClick = { dismiss(); vm.deletePlaylist(p.id); onBack() })
        },
        onRemove = { s -> vm.removeFromPlaylist(p.id, s) },
        onAddMusic = { addMusic = true },
    )
    if (addMusic) AddMusicDialog(vm, p.id) { addMusic = false }
}
