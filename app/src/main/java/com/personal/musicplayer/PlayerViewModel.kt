package com.personal.musicplayer

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val store = PlaylistStore(app)

    // ---- library ----
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var albums by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistItem>>(emptyList()); private set
    var playlists by mutableStateOf(store.load()); private set
    var loaded by mutableStateOf(false); private set
    var scanning by mutableStateOf(false); private set
    private var songMap = emptyMap<Long, Song>()
    var favorites by mutableStateOf(store.loadFavorites()); private set
    private var customSongArt = CustomArt.songIds(app)

    // ---- cover picking (the gallery picker lives in MainActivity) ----
    sealed interface CoverTarget {
        data class ForSong(val song: Song) : CoverTarget
        data class ForPlaylist(val id: String) : CoverTarget
    }
    private var coverTarget: CoverTarget? = null
    var pickRequest by mutableIntStateOf(0); private set

    fun requestCover(target: CoverTarget) { coverTarget = target; pickRequest++ }

    fun applyCover(uri: android.net.Uri?) {
        val t = coverTarget ?: return
        coverTarget = null
        if (uri == null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val key = when (t) { is CoverTarget.ForSong -> "s_${t.song.id}"; is CoverTarget.ForPlaylist -> "p_${t.id}" }
            if (CustomArt.save(getApplication(), key, uri)) afterCoverChange()
        }
    }

    fun removeCover(target: CoverTarget) {
        val key = when (target) { is CoverTarget.ForSong -> "s_${target.song.id}"; is CoverTarget.ForPlaylist -> "p_${target.id}" }
        CustomArt.delete(getApplication(), key)
        afterCoverChange()
    }

    fun hasCustomCover(s: Song) = s.id in customSongArt

    private fun afterCoverChange() {
        customSongArt = CustomArt.songIds(getApplication())
        ArtLoader.invalidate()
    }

    // ---- favorites ----
    fun isFavorite(s: Song?) = s != null && s.id in favorites
    fun toggleFavorite(s: Song) {
        favorites = if (s.id in favorites) favorites - s.id else favorites + s.id
        store.saveFavorites(favorites)
    }
    val favoriteSongs: List<Song> get() = songs.filter { it.id in favorites }

    // ---- player state ----
    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var upNext by mutableStateOf<List<QueueEntry>>(emptyList()); private set
    var sleepRemainingSec by mutableIntStateOf(0); private set

    private var controller: MediaController? = null
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            sync()
        }, MoreExecutors.directExecutor())

        // progress bar ticker (only touches state while music is playing)
        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let { if (it.isPlaying) positionMs = it.currentPosition }
            }
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }

    // ---- loading ----
    fun loadLibrary() = viewModelScope.launch {
        applyLibrary(repo.load())
    }

    fun rescan() = viewModelScope.launch {
        scanning = true
        try {
            repo.rescan()
            applyLibrary(repo.load())
        } finally {
            scanning = false
        }
    }

    private fun applyLibrary(list: List<Song>) {
        songs = list
        songMap = list.associateBy { it.id }
        albums = list.groupBy { it.albumId }.map { (id, s) ->
            val sorted = s.sortedBy { it.track }
            AlbumItem(id, sorted.first().album, sorted.first().artist, sorted)
        }.sortedBy { it.name.lowercase() }
        artists = list.groupBy { it.artist }.map { (n, s) -> ArtistItem(n, s) }.sortedBy { it.name.lowercase() }
        loaded = true
        sync()
    }

    // ---- playback ----
    private fun toItem(s: Song) = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .setArtworkUri(
                    if (s.id in customSongArt) android.net.Uri.fromFile(CustomArt.file(getApplication(), "s_${s.id}")) else s.uri
                )
                .build()
        )
        .build()

    fun play(list: List<Song>, index: Int, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        if (shuffleOn != null) c.shuffleModeEnabled = shuffleOn
        c.setMediaItems(list.map(::toItem), index, 0L)
        c.prepare()
        c.play()
    }

    fun shufflePlay(list: List<Song>) {
        if (list.isNotEmpty()) play(list, Random.nextInt(list.size), true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---- queue ----
    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(s))
    }
    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(toItem(s))
    }
    fun jumpTo(windowIndex: Int) { controller?.let { it.seekToDefaultPosition(windowIndex); it.play() } }
    fun removeFromQueue(windowIndex: Int) { controller?.removeMediaItem(windowIndex) }

    // ---- sleep timer ----
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { sleepRemainingSec = 0; return }
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            while (true) {
                val left = ((endAt - SystemClock.elapsedRealtime()) / 1000).toInt()
                if (left <= 0) break
                sleepRemainingSec = left
                delay(1000)
            }
            sleepRemainingSec = 0
            controller?.pause()
        }
    }

    // ---- playlists ----
    private fun savePlaylists(list: List<Playlist>) { playlists = list; store.save(list) }
    fun playlistSongs(p: Playlist) = p.songIds.mapNotNull { songMap[it] }
    fun createPlaylist(name: String, first: Song? = null) {
        if (name.isBlank()) return
        savePlaylists(playlists + Playlist(UUID.randomUUID().toString(), name.trim(), listOfNotNull(first?.id)))
    }
    fun addToPlaylist(pid: String, s: Song) = savePlaylists(playlists.map {
        if (it.id == pid && s.id !in it.songIds) it.copy(songIds = it.songIds + s.id) else it
    })
    fun removeFromPlaylist(pid: String, s: Song) = savePlaylists(playlists.map {
        if (it.id == pid) it.copy(songIds = it.songIds - s.id) else it
    })
    fun deletePlaylist(pid: String) = savePlaylists(playlists.filter { it.id != pid })

    // ---- sync from the player ----
    private fun sync() {
        val c = controller ?: return
        currentSong = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { songMap[it] }
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
        positionMs = c.currentPosition
        upNext = computeUpNext(c)
    }

    private fun computeUpNext(c: MediaController): List<QueueEntry> {
        val tl = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>()
        var i = tl.getNextWindowIndex(c.currentMediaItemIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < 200) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { id -> songMap[id]?.let { out.add(QueueEntry(i, it)) } }
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }
}
