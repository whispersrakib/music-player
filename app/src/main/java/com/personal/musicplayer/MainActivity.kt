package com.personal.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Ic.Library),
    Favorites("Favorites", Ic.Star),
    Search("Search", Ic.Search),
}

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlayerTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.applyCover(it) }
    LaunchedEffect(vm.pickRequest) { if (vm.pickRequest > 0) coverPicker.launch("image/*") }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(granted) {
        if (granted) {
            vm.loadLibrary()
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Music", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Allow access to your music to get started.", Modifier.padding(vertical = 16.dp))
                Button(onClick = { launcher.launch(perm) }) { Text("Allow access") }
            }
            return@Box
        }

        var tab by rememberSaveable { mutableStateOf(Tab.Library) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        val stack = remember { mutableStateListOf<Screen>() }

        BackHandler(enabled = expanded) { expanded = false }
        BackHandler(enabled = !expanded && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                val top = stack.lastOrNull()
                val back = { stack.removeAt(stack.lastIndex); Unit }
                val open: (Screen) -> Unit = { stack.add(it) }
                when {
                    vm.loaded && vm.songs.isEmpty() -> EmptyState(vm.scanning, vm::rescan)
                    top is Screen.Playlists -> PlaylistsPage(vm, back, open)
                    top is Screen.Artists -> ArtistsPage(vm, back, open)
                    top is Screen.Albums -> AlbumsPage(vm, back, open)
                    top is Screen.Songs -> SongsPage(vm, back)
                    top is Screen.Album -> AlbumDetail(vm, top.id, back)
                    top is Screen.Artist -> ArtistDetail(vm, top.name, back)
                    top is Screen.PlaylistScreen -> PlaylistDetail(vm, top.id, back)
                    else -> when (tab) {
                        Tab.Library -> LibraryTab(vm, open)
                        Tab.Favorites -> FavoritesTab(vm)
                        Tab.Search -> SearchTab(vm, open)
                    }
                }
            }
            MiniPlayer(vm) { expanded = true }
            FloatingTabs(tab) { tab = it; stack.clear() }
        }

        AnimatedVisibility(
            visible = expanded && vm.currentSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) { NowPlaying(vm) { expanded = false } }
    }
}

/** Rounded floating tab bar with a separate round Search button. */
@Composable
private fun FloatingTabs(tab: Tab, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(32.dp)).background(cs.surfaceVariant).padding(5.dp)) {
            listOf(Tab.Library, Tab.Favorites).forEach { t ->
                val sel = tab == t
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(27.dp))
                        .background(if (sel) cs.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(t) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, Modifier.size(24.dp), tint = if (sel) cs.primary else cs.onSurface)
                    Text(t.label, fontSize = 12.sp, color = if (sel) cs.primary else cs.onSurface)
                }
            }
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(cs.surfaceVariant).clickable { onSelect(Tab.Search) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Tab.Search.icon, "Search", Modifier.size(28.dp), tint = if (tab == Tab.Search) cs.primary else cs.onSurface)
        }
    }
}
