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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Ic.Library),
    Albums("Albums", Ic.Album),
    Artists("Artists", Ic.Person),
    Songs("Songs", Ic.Note),
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
    val notifLauncher =rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                    top is Screen.Album -> AlbumDetail(vm, top.id, back)
                    top is Screen.Artist -> ArtistDetail(vm, top.name, back)
                    top is Screen.PlaylistScreen -> PlaylistDetail(vm, top.id, back)
                    top is Screen.Favorites -> FavoritesDetail(vm, back)
                    else -> when (tab) {
                        Tab.Library -> LibraryTab(vm, open)
                        Tab.Albums -> AlbumsTab(vm, open)
                        Tab.Artists -> ArtistsTab(vm, open)
                        Tab.Songs -> SongsTab(vm)
                        Tab.Search -> SearchTab(vm, open)
                    }
                }
            }
            MiniPlayer(vm) { expanded = true }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; stack.clear() },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && vm.currentSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) { NowPlaying(vm) { expanded = false } }
    }
}
