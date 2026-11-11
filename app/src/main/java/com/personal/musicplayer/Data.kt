package com.personal.musicplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val dateAdded: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

data class AlbumItem(val id: Long, val name: String, val artist: String, val songs: List<Song>) {
    val cover: Song get() = songs.first()
    val newest: Long get() = songs.maxOf { it.dateAdded }
}

data class ArtistItem(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
}

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

data class QueueEntry(val index: Int, val song: Song)

/** Reads music from Android's media library (MediaStore). */
class MusicRepository(private val context: Context) {

    suspend fun load(): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val m = MediaStore.Audio.Media
        val projection = arrayOf(m._ID, m.TITLE, m.ARTIST, m.ALBUM, m.ALBUM_ID, m.DURATION, m.TRACK, m.DATE_ADDED)
        val selection = "${m.IS_MUSIC} != 0 AND ${m.DURATION} >= 10000"
        context.contentResolver.query(m.EXTERNAL_CONTENT_URI, projection, selection, null, "${m.TITLE} COLLATE NOCASE ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    val artist = c.getString(2)?.takeIf { it != MediaStore.UNKNOWN_STRING } ?: "Unknown Artist"
                    val album = c.getString(3)?.takeIf { it != MediaStore.UNKNOWN_STRING } ?: "Unknown Album"
                    out.add(
                        Song(
                            id = c.getLong(0),
                            title = c.getString(1) ?: "Untitled",
                            artist = artist,
                            album = album,
                            albumId = c.getLong(4),
                            durationMs = c.getLong(5),
                            track = c.getInt(6) % 1000,
                            dateAdded = c.getLong(7),
                        )
                    )
                }
            }
        out
    }

    /** Asks Android to (re)index audio files in Music/ and Download/, e.g. after `adb push`. */
    suspend fun rescan() = withContext(Dispatchers.IO) {
        val exts = setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus")
        val paths = listOf(Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOWNLOADS)
            .map { Environment.getExternalStoragePublicDirectory(it) }
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in exts }.map { it.absolutePath }.toList() }
        if (paths.isEmpty()) return@withContext
        suspendCancellableCoroutine<Unit> { cont ->
            val left = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }
}

/** Playlists are saved as a tiny JSON string in SharedPreferences. */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> = try {
        val arr = JSONArray(prefs.getString("p", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("s")
            Playlist(o.getString("id"), o.getString("n"), (0 until ids.length()).map { ids.getLong(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun loadFavorites(): Set<Long> =
        prefs.getString("fav", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(ids: Set<Long>) {
        prefs.edit().putString("fav", ids.joinToString(",")).apply()
    }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("n", p.name).put("s", JSONArray(p.songIds)))
        }
        prefs.edit().putString("p", arr.toString()).apply()
    }
}

/** Custom cover images the user picked from the gallery (stored privately inside the app). Keys: "s_<songId>" or "p_<playlistId>". */
object CustomArt {
    private fun dir(context: Context) = File(context.filesDir, "art").apply { mkdirs() }
    fun file(context: Context, key: String) = File(dir(context), "$key.jpg")
    fun songIds(context: Context): Set<Long> =
        dir(context).list().orEmpty().filter { it.startsWith("s_") }.mapNotNull { it.removePrefix("s_").removeSuffix(".jpg").toLongOrNull() }.toSet()

    /** Crops the chosen picture to a square, shrinks it to 600px and saves it. */
    fun save(context: Context, key: String, source: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }!!
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, 600, 600, true)
        file(context, key).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }

    fun delete(context: Context, key: String) { file(context, key).delete() }
}

/** Album art comes from the tags inside the MP3 (Android extracts it for us), unless you set a custom cover. Small in-memory cache keeps scrolling smooth. */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    /** Changes whenever a custom cover is added/removed, so screens reload their art. */
    var version by mutableIntStateOf(0)
        private set

    private fun albumKey(song: Song, px: Int) = "${song.albumId}@$px"
    private fun songKey(song: Song, px: Int) = "s${song.id}@$px"

    fun cached(song: Song, px: Int): Bitmap? = cache.get(songKey(song, px)) ?: cache.get(albumKey(song, px))

    fun invalidate() {
        cache.evictAll()
        synchronized(missing) { missing.clear() }
        version++
    }

    private fun decodeCustom(context: Context, key: String, px: Int): Bitmap? {
        val f = CustomArt.file(context, key)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        return if (b.width > px) Bitmap.createScaledBitmap(b, px, px, true) else b
    }

    /** Art for a playlist's own custom cover, or null if it has none. */
    suspend fun loadPlaylist(context: Context, playlistId: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val k = "p_$playlistId@$px"
        cache.get(k)?.let { return@withContext it }
        decodeCustom(context, "p_$playlistId", px)?.also { cache.put(k, it) }
    }

    suspend fun load(context: Context, song: Song, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val sk = songKey(song, px)
        cache.get(sk)?.let { return@withContext it }
        decodeCustom(context, "s_${song.id}", px)?.let { cache.put(sk, it); return@withContext it }
        val k = albumKey(song, px)
        cache.get(k)?.let { return@withContext it }
        if (synchronized(missing) { k in missing }) return@withContext null
        val bmp = try {
            context.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(k, bmp) else synchronized(missing) { missing.add(k) }
        bmp
    }

    fun tint(b: Bitmap): Color = Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0))
}
