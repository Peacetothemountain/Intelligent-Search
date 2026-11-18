package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.content.edit
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream

/**
 * The one place Gallery changes something on the phone itself: setting a look's wallpaper, when the owner asks for
 * exactly that. Everything here blocks; call it off the main thread.
 *
 * Undo needs a copy of the wallpaper that was there. Android only lets an app read the current wallpaper with a file
 * access this app doesn't ask for, so on most phones there is no copy and no undo, and the sheet says so before
 * anything is set. Where the phone does allow the read, the copy is kept until it is put back.
 */
internal object PhoneWallpaper {
    enum class Where(val flags: Int, val label: String, val words: String) {
        Both(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, "Home and Lock Screens", "home and lock screens"),
        Home(WallpaperManager.FLAG_SYSTEM, "Home Screen", "home screen"),
        Lock(WallpaperManager.FLAG_LOCK, "Lock Screen", "lock screen")
    }

    private const val HOME_ID = "homeId"
    private const val LOCK_ID = "lockId"
    private const val CHANGED = "changed"
    private const val HAD_LOCK = "hadLock"

    private fun manager(context: Context) = WallpaperManager.getInstance(context)
    private fun prefs(context: Context) = context.getSharedPreferences("gallery_wallpaper", Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.filesDir, "gallery_wallpaper_before")
    private fun home(context: Context) = File(dir(context), "home")
    private fun lock(context: Context) = File(dir(context), "lock")

    /** Whether this phone lets an app set the wallpaper at all (a work profile or a kiosk may not). */
    fun allowed(context: Context): Boolean = runCatching {
        val wm = manager(context)
        wm.isWallpaperSupported && wm.isSetWallpaperAllowed
    }.getOrDefault(false)

    /** A copy of the owner's own wallpaper is waiting to be put back. */
    fun kept(context: Context): Boolean = home(context).isFile

    /** The wallpapers on the phone are still the ones Gallery set, so the kept copy is still the owner's own. */
    private fun stillOurs(context: Context): Boolean = runCatching {
        val wm = manager(context)
        val p = prefs(context)
        p.getInt(HOME_ID, -1) == wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) && p.getInt(LOCK_ID, -1) == wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
    }.getOrDefault(false)

    // Asked in order to find out: a refusal is the answer "no undo here".
    @SuppressLint("MissingPermission")
    private fun open(context: Context, which: Int): ParcelFileDescriptor? = runCatching { manager(context).getWallpaperFile(which) }.getOrNull()

    /** Whether setting a wallpaper now could be undone: the current one can be copied, or its copy is already kept. */
    fun canUndo(context: Context): Boolean {
        if (kept(context) && stillOurs(context)) return true
        // A live wallpaper is an app, not a picture: there is nothing to copy.
        if (runCatching { manager(context).wallpaperInfo }.getOrNull() != null) return false
        return open(context, WallpaperManager.FLAG_SYSTEM)?.use { true } ?: false
    }

    /** Copies the phone's wallpapers before they are replaced. True when the owner's own wallpaper is safely kept. */
    private fun keep(context: Context): Boolean {
        if (kept(context) && stillOurs(context)) return true
        forget(context)
        return runCatching {
            val source = open(context, WallpaperManager.FLAG_SYSTEM) ?: return false
            dir(context).mkdirs()
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input -> File(dir(context), "home.part").outputStream().use { input.copyTo(it) } }
            // The lock screen has its own picture only when the owner gave it one.
            val lockSource = open(context, WallpaperManager.FLAG_LOCK)
            if (lockSource != null) ParcelFileDescriptor.AutoCloseInputStream(lockSource).use { input -> lock(context).outputStream().use { input.copyTo(it) } }
            prefs(context).edit { putBoolean(HAD_LOCK, lockSource != null).putInt(CHANGED, 0) }
            File(dir(context), "home.part").renameTo(home(context))
        }.getOrDefault(false).also { if (!it) forget(context) }
    }

    private fun forget(context: Context) {
        dir(context).deleteRecursively()
        prefs(context).edit { clear() }
    }

    /**
     * Sets the PNG in [picture] as the phone's wallpaper on [where]. [screen] is the part of it that fills the screen;
     * the rest is for the home screen to slide to. Returns whether it can be undone. Throws if the phone refuses.
     */
    fun set(context: Context, picture: File, screen: Rect?, where: Where, wantUndo: Boolean): Boolean {
        val undo = wantUndo && keep(context)
        val wm = manager(context)
        FileInputStream(picture).use { wm.setStream(it, screen, true, where.flags) }
        if (undo) {
            val p = prefs(context)
            p.edit {
                putInt(HOME_ID, runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1))
                putInt(LOCK_ID, runCatching { wm.getWallpaperId(WallpaperManager.FLAG_LOCK) }.getOrDefault(-1))
                putInt(CHANGED, p.getInt(CHANGED, 0) or where.flags)
            }
        }
        return undo
    }

    /** Puts the owner's own wallpaper back where Gallery replaced it. True when it is back. */
    fun restore(context: Context): Boolean {
        if (!kept(context)) return false
        return runCatching {
            val wm = manager(context)
            val p = prefs(context)
            val changed = p.getInt(CHANGED, 0)
            if (changed and WallpaperManager.FLAG_SYSTEM != 0) FileInputStream(home(context)).use { wm.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
            if (changed and WallpaperManager.FLAG_LOCK != 0) {
                if (p.getBoolean(HAD_LOCK, false) && lock(context).isFile) FileInputStream(lock(context)).use { wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
                // It had none of its own: it shows the home screen's again.
                else wm.clear(WallpaperManager.FLAG_LOCK)
            }
            forget(context)
            true
        }.getOrDefault(false)
    }
}
