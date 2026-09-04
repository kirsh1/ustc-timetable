package com.ustc.timetable.appearance

import android.content.ContentResolver
import android.content.Intent
import androidx.core.net.toUri

interface WallpaperUriGrants {
    fun persist(uri: String)
    fun release(uri: String)
}

class AndroidWallpaperUriGrants(private val resolver: ContentResolver) : WallpaperUriGrants {
    override fun persist(uri: String) {
        runCatching {
            resolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun release(uri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

object WallpaperSelection {
    fun resolve(current: String?, selected: String?, grants: WallpaperUriGrants): String? {
        if (selected == null || selected == current) return current
        grants.persist(selected)
        current?.let(grants::release)
        return selected
    }

    fun clear(current: String?, grants: WallpaperUriGrants) {
        current?.let(grants::release)
    }
}
