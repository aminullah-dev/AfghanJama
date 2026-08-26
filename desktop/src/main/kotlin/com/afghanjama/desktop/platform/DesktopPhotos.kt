package com.afghanjama.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.afghanjama.desktop.data.dataDir
import com.afghanjama.platform.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * [Photos] روی ویندوز.
 *
 * **جای فایل‌ها کنارِ دفتر است، نه کنارِ برنامه** — همان قاعده‌ای که
 * دیتابیس دارد. `Program Files` فقط-خواندنی است و به‌روزرسانی هم
 * می‌تواند رویش بنویسد.
 *
 * **دوربین نیست و پنهانش نمی‌کنیم:** [rememberCapture] برابرِ `null`
 * است، پس دکمهٔ دوربین روی پی‌سی ساخته نمی‌شود. انتخاب از فایل هست و
 * همان کار را می‌کند.
 */
class DesktopPhotos : Photos {

    private val dir: File by lazy {
        File(dataDir(), "order_photos").apply { mkdirs() }
    }

    private fun file(name: String) = File(dir, name)

    override fun delete(name: String) {
        if (name.isBlank()) return
        runCatching { file(name).delete() }
    }

    override fun exists(name: String): Boolean =
        name.isNotBlank() && file(name).isFile

    @Composable
    override fun rememberThumb(name: String, maxSide: Int): ImageBitmap? {
        var image by remember(name, maxSide) { mutableStateOf<ImageBitmap?>(null) }

        // خواندن و کوچک‌کردن روی نخِ رابطِ کاربری انجام نمی‌شود؛ یک عکسِ
        // ۱۶۰۰ پیکسلی محسوس است و نوارِ عکس‌ها چندتا با هم دارد.
        LaunchedEffect(name, maxSide) {
            image = withContext(Dispatchers.IO) { load(file(name), maxSide) }
        }
        return image
    }

    /**
     * روی ویندوز دوربینی نیست.
     *
     * `null` یعنی «این دکمه را نساز» — نه اینکه دکمه‌ای بسازی که هیچ
     * نمی‌کند.
     */
    @Composable
    override fun rememberCapture(onCaptured: (String) -> Unit): (() -> Unit)? = null

    @Composable
    override fun rememberPicker(onPicked: (String) -> Unit): () -> Unit = remember(onPicked) {
        {
            val picked = chooseFile()
            if (picked != null) {
                val name = newName(picked.extension)
                runCatching {
                    picked.copyTo(file(name), overwrite = true)
                    shrink(file(name), Photos.MAX_SIDE)
                }.onSuccess { onPicked(name) }
                    .onFailure { delete(name) }
            }
        }
    }

    private fun chooseFile(): File? {
        // `FileDialog`ِ AWT پنجرهٔ خودِ ویندوز را می‌آورد، نه یکی
        // Swing‌ی که بینِ برنامه‌های دیگرِ کارگاه غریبه به نظر برسد.
        val dlg = FileDialog(null as Frame?, "انتخابِ عکس", FileDialog.LOAD)
        dlg.setFilenameFilter { _, n ->
            n.lowercase().let {
                it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                    it.endsWith(".png") || it.endsWith(".webp")
            }
        }
        dlg.isVisible = true
        val d = dlg.directory ?: return null
        val f = dlg.file ?: return null
        return File(d, f).takeIf { it.isFile }
    }

    private fun newName(ext: String): String {
        val safe = ext.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        return "p_${System.currentTimeMillis()}_${(1000..9999).random()}.$safe"
    }

    private fun load(f: File, maxSide: Int): ImageBitmap? = runCatching {
        if (!f.isFile) return@runCatching null
        val src = ImageIO.read(f) ?: return@runCatching null
        val side = max(src.width, src.height)
        val img = if (side <= maxSide) {
            src
        } else {
            val scale = maxSide.toDouble() / side
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            g.dispose()
            out
        }
        img.toComposeImageBitmap()
    }.getOrNull()

    /**
     * عکسِ واردشده یک بار کوچک می‌شود.
     *
     * بی این، عکسِ ۱۲ مگاپیکسلیِ یک گوشیِ امروزی همان‌طور در پوشهٔ دفتر
     * می‌نشیند و پشتیبان‌گیری را چند برابر می‌کند — همان کاری که
     * `PhotoStore.shrinkInPlace` روی اندروید می‌کند.
     */
    private fun shrink(f: File, maxSide: Int) {
        runCatching {
            val src = ImageIO.read(f) ?: return
            val side = max(src.width, src.height)
            if (side <= maxSide) return
            val scale = maxSide.toDouble() / side
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            g.dispose()
            ImageIO.write(out, "jpg", f)
        }
    }
}
