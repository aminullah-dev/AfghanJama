package com.afghanjama

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afghanjama.data.AppDb
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.AndroidLanHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * گوشیِ اصلیِ اندرویدی: درخواستی که سرورِ اشتراکِ کارگاه می‌نشاند، همان
 * لحظه در فهرستِ «درخواست‌های منتظر» دیده می‌شود.
 *
 * **اشکالی که این آزمون نگه می‌دارد:** سرور (`AndroidLanHost`) دفتر را با
 * یک نمونهٔ جدای Room باز می‌کرد و صفحه نمونهٔ `MainActivity` را می‌پایید.
 * Room تغییرِ یک نمونه را به `Flow`های نمونهٔ دیگر خبر نمی‌دهد، پس درخواست
 * تا ترکِ صفحه دیده نمی‌شد. روی JVM دیده نمی‌شود — Room اندرویدی لازم است.
 *
 * فقط روی شبیه‌ساز یا گوشی اجرا می‌شود (`connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class HubLiveRefreshTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHubWritesIntoTheSameLedgerTheScreensWatch() {
        assertSame(
            "the LAN host opened a second Room instance",
            AppDb.get(ctx), AndroidLanHost(ctx).ledger()
        )
    }

    @Test
    fun aWorkerRequestShowsUpInThePendingListWithoutLeavingTheScreen() = runBlocking {
        val screen = Repo(AppDb.get(ctx))
        val serverDb = AndroidLanHost(ctx).ledger()

        // صفحه **از پیش** می‌پاید؛ ردیفِ سرور باید در همین جریان برسد. جریانِ
        // تازه هر بار خودش از فایل می‌خوانَد و با دو نمونه هم سبز می‌شد.
        val emissions = Channel<List<SyncRequest>>(Channel.UNLIMITED)
        val watching = launch(Dispatchers.IO) { screen.observePendingRequests().collect { emissions.send(it) } }
        var id = 0L
        try {
            assertNotNull("the screen's list never loaded", withTimeoutOrNull(5_000) { emissions.receive() })
            id = serverDb.syncRequestDao().insert(
                SyncRequest(deviceName = "آزمون", worker = "[T1] آزمون", type = "NOTE", summary = "زنده")
            )
            val seen = withTimeoutOrNull(5_000) {
                var hit: List<SyncRequest>? = null
                while (hit == null) hit = emissions.receive().takeIf { list -> list.any { it.id == id } }
                hit
            }
            assertNotNull("the pending list the screen watches never saw the server's row", seen)
        } finally {
            watching.cancel()
            if (id > 0) screen.rejectRequest(id)
        }
    }
}
