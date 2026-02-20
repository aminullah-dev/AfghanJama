// app/src/main/java/com/afghanjama/ui/sound/MoneySound.kt
package com.afghanjama.ui.sound

import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * هر بار که [playKey] تغییر کند (و != 0 باشد) صدا را یک‌بار پخش می‌کند.
 * برای جلوگیری از لیک، MediaPlayer بعد از اتمام آزاد می‌شود.
 */
@Composable
fun PlayRawSoundOnce(
    playKey: Int,
    @RawRes resId: Int,
    onConsumed: () -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(playKey) {
        if (playKey == 0) return@LaunchedEffect

        val mp = MediaPlayer.create(context, resId)
        if (mp == null) {
            onConsumed()
            return@LaunchedEffect
        }

        mp.setOnCompletionListener {
            it.reset()
            it.release()
        }
        mp.setOnErrorListener { player, _, _ ->
            player.reset()
            player.release()
            true
        }

        try {
            mp.start()
        } catch (_: Throwable) {
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }

        onConsumed()
    }
}
