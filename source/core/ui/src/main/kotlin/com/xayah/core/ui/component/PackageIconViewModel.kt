package com.xayah.core.ui.component

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.ViewModel
import com.xayah.core.rootservice.service.RemoteRootService
import com.xayah.core.util.PathUtil
import com.xayah.core.util.localBackupSaveDir
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class PackageIconViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootService: RemoteRootService,
) : ViewModel() {
    private val cache = LruCache<String, Drawable>(128)
    private val mutex = Mutex()

    suspend fun load(packageName: String, refresh: Boolean): Drawable? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()?.let { return@withContext it }
        mutex.withLock {
            if (refresh) cache.remove(packageName) else cache.get(packageName)?.let { return@withLock it }
            val appsDir = "${context.localBackupSaveDir()}/${PathUtil.getAppsRelativeDir()}"
            val bytes = rootService.readBytes(PathUtil.getAppIconPath(appsDir, packageName))
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.toDrawable(context.resources)?.also {
                cache.put(packageName, it)
            }
        }
    }
}
