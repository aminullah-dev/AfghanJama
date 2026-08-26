package com.afghanjama.platform

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.afghanjama.platform.Photos
import com.afghanjama.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Photos] روی اندروید — پوستهٔ نازکی روی `PhotoStore`ِ موجود.
 *
 * **هیچ رفتاری روی گوشی عوض نمی‌شود.** همان پوشه، همان نام‌گذاری، همان
 * دوربین و همان گالری. تنها کاری که اینجا انجام می‌شود این است که
 * صفحه‌ها به‌جای صدا زدنِ مستقیمِ `PhotoStore` (که `Context` می‌خواهد و
 * برای همین آن‌ها را در `:app` نگه داشته بود) این واسط را ببینند.
 */
class AndroidPhotos(private val context: Context) : Photos {

    override fun delete(name: String) {
        if (name.isBlank()) return
        PhotoStore.delete(context, name)
    }

    override fun exists(name: String): Boolean =
        name.isNotBlank() && PhotoStore.exists(context, name)

    @Composable
    override fun rememberThumb(name: String, maxSide: Int): ImageBitmap? {
        var image by remember(name, maxSide) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(name, maxSide) {
            image = withContext(Dispatchers.IO) {
                runCatching {
                    PhotoStore.decode(PhotoStore.file(context, name), maxSide)?.asImageBitmap()
                }.getOrNull()
            }
        }
        return image
    }

    /**
     * دوربین — روی گوشی هست، پس `null` نیست.
     *
     * فایل **پیش از** باز کردنِ دوربین ساخته می‌شود چون اپِ دوربین به
     * یک URIِ آماده می‌نویسد؛ اگر کاربر منصرف شد، همان فایلِ خالی پاک
     * می‌شود.
     */
    @Composable
    override fun rememberCapture(onCaptured: (String) -> Unit): (() -> Unit)? {
        val ctx = LocalContext.current
        val cb by rememberUpdatedState(onCaptured)
        var pending by remember { mutableStateOf<String?>(null) }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { ok ->
            val name = pending
            pending = null
            if (name == null) return@rememberLauncherForActivityResult
            if (ok) {
                PhotoStore.shrinkInPlace(ctx, name)
                cb(name)
            } else {
                PhotoStore.delete(ctx, name)
            }
        }

        return remember(launcher) {
            {
                val name = PhotoStore.newFileName()
                pending = name
                launcher.launch(PhotoStore.uriFor(ctx, name))
            }
        }
    }

    @Composable
    override fun rememberPicker(onPicked: (String) -> Unit): () -> Unit {
        val ctx = LocalContext.current
        val cb by rememberUpdatedState(onPicked)

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val name = PhotoStore.newFileName()
            if (PhotoStore.copyFrom(ctx, uri, name)) {
                PhotoStore.shrinkInPlace(ctx, name)
                cb(name)
            } else {
                PhotoStore.delete(ctx, name)
            }
        }

        return remember(launcher) {
            {
                launcher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        }
    }
}
