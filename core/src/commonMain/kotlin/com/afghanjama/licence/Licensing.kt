package com.afghanjama.licence

import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.LicencePrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * سرویسِ لایسنس — حقیقت را از تنظیمات، دفتر و سکو جمع می‌کند و به
 * [LicenceGate] می‌دهد.
 *
 * خودش هیچ حالتی نگه نمی‌دارد: هر بار از نو می‌خوانَد. پس ساختنِ چند
 * نمونه (یکی برای راه‌اندازی، یکی برای صفحهٔ لایسنس) بی‌خطر است.
 *
 * [repo] روی دسکتاپ می‌تواند `null` باشد (دفتر باز نشده)؛ آن‌وقت شمارِ
 * سفارش صفر و لنگرِ دفتر نبوده فرض می‌شود.
 *
 * [publicKeyDer] و [platformId] فقط برای آزمون عوض می‌شوند. کلیدِ
 * آزمایشی هرگز به کدِ ارسالی نمی‌آید.
 */
class Licensing(
    private val settings: Settings,
    private val repo: Repo?,
    private val publicKeyDer: ByteArray = LicenceKeys.production,
    private val platformId: () -> String? = { platformMachineId() },
    private val clock: () -> Long = { nowMillis() },
) {

    /** کدِ این دستگاه، و اینکه از شناسهٔ واقعیِ سکو آمده یا نه. */
    suspend fun machine(): MachineIdentity =
        withContext(Dispatchers.Default) { MachineIds.resolve(settings, platformId()) }

    /**
     * همه‌چیز را از نو می‌خوانَد، در [LicenceGate] می‌نشاند و وضعیت را
     * برمی‌گرداند. سکو آن را سرِ راه‌اندازی و هر چند دقیقه صدا می‌زند.
     */
    suspend fun refresh(): LicenceStatus {
        val now = clock()
        val machine = machine()
        val mode = LanPrefs.mode(settings)

        // ---- کلید ----
        val stored = LicencePrefs.key(settings)
        var licence: Licence? = null
        var rejected = false
        if (stored.isNotBlank()) {
            when (val r = Lnm1.verify(stored, publicKeyDer)) {
                is Lnm1.Result.Ok -> licence = r.licence
                is Lnm1.Result.Err -> rejected = true
            }
        }

        // ---- ساعت ----
        val lastSeen = maxOf(LicencePrefs.lastSeen(settings), now)
        LicencePrefs.setLastSeen(settings, lastSeen)
        val effectiveNow = LicencePolicy.effectiveNow(now, lastSeen).first

        // ---- شروعِ دورهٔ آزمایشی: زودترینِ تنظیمات و دفتر ----
        val fromPrefs = LicencePrefs.firstRun(settings).takeIf { it > 0 }
        val fromBook = runCatching { repo?.licenceTrialAnchor() }.getOrNull()
        val firstRun = listOfNotNull(fromPrefs, fromBook).minOrNull() ?: effectiveNow
        if (fromPrefs != firstRun) LicencePrefs.setFirstRun(settings, firstRun)
        if (fromBook == null && repo != null && mode != DeviceMode.WORKER) {
            runCatching { repo?.writeLicenceTrialAnchor(firstRun) }
        }

        // ---- دستگاهِ دیگر؟ ----
        var mismatchSince: Long? = null
        if (licence != null && !licence.anyMachine && licence.machine != machine.code) {
            mismatchSince = LicencePrefs.mismatchSince(settings, licence.id)
                ?: effectiveNow.also { LicencePrefs.setMismatchSince(settings, licence.id, it) }
        } else {
            LicencePrefs.clearMismatch(settings)
        }

        val orders = if (licence == null && mode != DeviceMode.WORKER) {
            runCatching { repo?.ordersCreatedSince(firstRun) }.getOrNull() ?: 0
        } else 0

        val facts = LicenceFacts(
            licence = licence,
            machineCode = machine.code,
            firstRunMillis = firstRun,
            lastSeenMillis = lastSeen,
            machineMismatchSinceMillis = mismatchSince,
            ordersSinceTrialStart = orders,
            mode = mode,
            mainReport = if (mode == DeviceMode.WORKER) storedMain() else null,
            storedKeyRejected = rejected,
        )
        LicenceGate.onMainReport = ::rememberMain
        LicenceGate.clock = clock
        LicenceGate.install(facts)
        return LicenceGate.current()!!
    }

    /** نتیجهٔ واردکردنِ کلید. */
    sealed interface Activation {
        /** نشست. [warning] اگر کلید برای دستگاهِ دیگری باشد. */
        data class Done(val status: LicenceStatus, val warning: String?) : Activation
        data class Rejected(val message: String) : Activation
    }

    /**
     * کلیدی که کاربر چسبانده یا از فایلِ `.lnmlic` خوانده.
     *
     * کلیدِ درست همان لحظه همهٔ محدودیت‌ها را برمی‌دارد — بی نصبِ دوباره،
     * بی واردکردنِ دوبارهٔ داده. کلیدِ نادرست هیچ چیزِ نصب‌شده را عوض
     * نمی‌کند.
     *
     * کلیدی که برای دستگاهِ دیگری صادر شده هم پذیرفته می‌شود (همان مهلتِ
     * ۳۰ روزهٔ «دستگاهِ تازه» برای وقتی که سیستم از نو نصب شده) ولی صریح
     * گفته می‌شود که کلیدِ درست را باید گرفت.
     */
    suspend fun activate(text: String): Activation {
        val key = Lnm1.compact(text)
        if (key.isEmpty()) return Activation.Rejected("کلید را بچسبانید یا فایلِ لایسنس را باز کنید.")
        val licence = when (val r = Lnm1.verify(key, publicKeyDer)) {
            is Lnm1.Result.Ok -> r.licence
            is Lnm1.Result.Err -> return Activation.Rejected(LicenceText.problem(r.problem))
        }
        LicencePrefs.setKey(settings, key)
        val status = refresh()
        val machine = machine()
        val warning = if (!licence.anyMachine && licence.machine != machine.code) {
            "این کلید برای دستگاهِ دیگری (${licence.machine}) صادر شده، نه این یکی (${machine.code}). " +
                "تا ${LicencePolicy.GRACE_DAYS} روز کار می‌کند؛ برای کلیدِ همین دستگاه با لینومیک تماس بگیرید."
        } else null
        return Activation.Done(status, warning)
    }

    private fun storedMain(): MainLicence? {
        val seen = LicencePrefs.mainSeen(settings)
        if (seen <= 0) return null
        val state = runCatching { LicenceState.valueOf(LicencePrefs.mainState(settings)) }.getOrNull()
            ?: return null
        return MainLicence(state, LicencePrefs.mainWritable(settings), seen)
    }

    private fun rememberMain(report: MainLicence) {
        LicencePrefs.setMain(settings, report.state.name, report.writable, report.seenAtMillis)
        LicenceGate.update { it.copy(mainReport = report) }
    }
}
