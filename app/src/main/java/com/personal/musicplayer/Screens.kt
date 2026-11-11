package com.personal.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Screen {
    data class Album(val id: Long) : Screen
    data class Artist(val name: String) : Screen
    data class PlaylistScreen(val id: String) : Screen
    data object Favorites : Screen
}

// ---------------- Library (home) ----------------
@Composable
fun LibraryTab(vm: PlayerViewModel, open: (Screen) -> Unit) {
    var newDialog by remember { mutableStateOf(false) }
    val recent = remember(vm.albums) { vm.albums.sortedByDescending { it.newest }.take(12) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("Library") }
                TextButton(onClick = vm::rescan, enabled = !vm.scanning) { Text(if (vm.scanning) "Scanning…" else "Rescan") }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { newDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text("New Playlist…", Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { open(Screen.Favorites) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Ic.Heart, null, Modifier.size(28.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Favorites", fontSize = 17.sp)
                    Text("${vm.favoriteSongs.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(vm.playlists, key = { it.id }) { p ->
            val songs = vm.playlistSongs(p)
            Row(
                Modifier.fillMaxWidth().clickable { open(Screen.PlaylistScreen(p.id)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(songs.firstOrNull(), Modifier.size(56.dp), 150, playlistId = p.id)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Recently Added") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent, key = { it.id }) { a ->
                        Column(Modifier.width(150.dp).clickable { open(Screen.Album(a.id)) }) {
                            Artwork(a.cover, Modifier.size(150.dp), 300, 8.dp)
                            Text(a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (newDialog) NewPlaylistDialog({ vm.createPlaylist(it) }) { newDialog = false }
}

// ---------------- Songs ----------------
@Composable
fun SongsTab(vm: PlayerViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Songs") }
        item { PlayShuffleRow({ vm.play(vm.songs, 0) }, { vm.shufflePlay(vm.songs) }) }
        itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(vm.songs, i) }) }
    }
}

// ---------------- Albums ----------------
@Composable
fun AlbumsTab(vm: PlayerViewModel, open: (Screen) -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitleFlat("Albums") }
        items(vm.albums, key = { it.id }) { a ->
            Column(Modifier.clickable { open(Screen.Album(a.id)) }) {
                Artwork(a.cover, Modifier.fillMaxWidth().aspectRatio(1f), 300, 8.dp)
                Text(a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScreenTitleFlat(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

// ---------------- Artists ----------------
@Composable
fun ArtistsTab(vm: PlayerViewModel, open: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Artists") }
        items(vm.artists, key = { it.name }) { a -> ArtistRow(a) { open(Screen.Artist(a.name)) } }
    }
}

@Composable
private fun ArtistRow(a: ArtistItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(a.songs.first(), Modifier.size(52.dp), 120, 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.albumCount} albums · ${a.songs.size} songs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { ScreenTitle("Search") }
        item {
            OutlinedTextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Ic.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Ic.Close, "Clear") } },
                shape = RoundedCornerShape(12.dp),
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
                Row(Modifier.fillMaxWidth().clickable { open(Screen.Album(a.id)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(a.cover, Modifier.size(52.dp), 120)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(a.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionTitle("Songs") }
            itemsIndexed(songs, key = { _, s -> "s" + s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(songs, i) }) }
        }
    }
}

// ---------------- Detail pages ----------------
@Composable
private fun DetailScaffold(
    onBack: () -> Unit,
    header: @Composable () -> Unit,
    songs: List<Song>,
    vm: PlayerViewModel,
    onRemove: ((Song) -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { IconButton(onClick = onBack, Modifier.padding(start = 4.dp)) { Icon(Ic.Back, "Back", tint = MaterialTheme.colorScheme.primary) } }
        item { header() }
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
    }
}

@Composable
private fun BigHeader(art: Song?, title: String, subtitle: String, round: Boolean = false, playlistId: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(art, Modifier.size(220.dp), 500, if (round) 110.dp else 12.dp, playlistId)
        Text(title, Modifier.padding(top = 14.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AlbumDetail(vm: PlayerViewModel, id: Long, onBack: () -> Unit) {
    val a = vm.albums.firstOrNull { it.id == id } ?: return onBack()
    DetailScaffold(onBack, { BigHeader(a.cover, a.name, a.artist) }, a.songs, vm)
}

@Composable
fun ArtistDetail(vm: PlayerViewModel, name: String, onBack: () -> Unit) {
    val a = vm.artists.firstOrNull { it.name == name } ?: return onBack()
    DetailScaffold(onBack, { BigHeader(a.songs.first(), a.name, "${a.songs.size} songs", round = true) }, a.songs, vm)
}

@Composable
fun FavoritesDetail(vm: PlayerViewModel, onBack: () -> Unit) {
    val songs = vm.favoriteSongs
    DetailScaffold(
        onBack,
        {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Ic.Heart, null, Modifier.size(96.dp), tint = Color.White)
                }
                Text("Favorites", Modifier.padding(top = 14.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${songs.size} songs", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        songs, vm,
    )
}

@Composable
fun PlaylistDetail(vm: PlayerViewModel, id: String, onBack: () -> Unit) {
    val p = vm.playlists.firstOrNull { it.id == id } ?: return onBack()
    val songs = vm.playlistSongs(p)
    DetailScaffold(
        onBack,
        {
            Column {
                BigHeader(songs.firstOrNull(), p.name, "${songs.size} songs", playlistId = p.id)
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    TextButton(onClick = { vm.requestCover(PlayerViewModel.CoverTarget.ForPlaylist(p.id)) }) { Text("Change cover") }
                    if (ArtLoader.version >= 0 && CustomArt.file(LocalContext.current, "p_${p.id}").exists()) {
                        TextButton(onClick = { vm.removeCover(PlayerViewModel.CoverTarget.ForPlaylist(p.id)) }) { Text("Remove cover") }
                    }
                    TextButton(onClick = { vm.deletePlaylist(p.id); onBack() }) { Text("Delete") }
                }
            }
        },
        songs, vm,
        onRemove = { s -> vm.removeFromPlaylist(p.id, s) },
    )
}
