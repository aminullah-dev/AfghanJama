package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.LanClient
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.LanHost
import com.afghanjama.lan.LanServer
import com.afghanjama.lan.RemoteWork
import com.afghanjama.prefs.Settings
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkshopLinkUi(
    val mode: DeviceMode = DeviceMode.STANDALONE,
    val serving: Boolean = false,
    val ip: String? = null,
    val code: String = "",
    val host: String = "",
    val deviceName: String = "",
    val myWork: List<RemoteWork> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val checking: Boolean = false
)

/**
 * اشتراکِ کارگاه روی وای‌فای — مدلِ «یک نویسنده».
 *
 * گوشیِ اصلی دفتر را دارد و سرور را بالا می‌آورد. گوشیِ کارگر فقط
 * می‌خواند و درخواست می‌فرستد؛ هیچ‌وقت چیزی نمی‌نویسد. برای همین
 * تضادِ دو نویسنده اصلاً پیش نمی‌آید و لازم نیست حلش کنیم.
 */
class WorkshopLinkViewModel(
    private val repo: Repo,
    private val host: LanHost
) : ViewModel() {

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var server: LanServer? = null

    private val _ui = MutableStateFlow(WorkshopLinkUi())
    val ui: StateFlow<WorkshopLinkUi> = _ui.asStateFlow()

    /** درخواست‌های منتظر — فقط روی گوشیِ اصلی پر می‌شود. */
    val pending: StateFlow<List<SyncRequest>> =
        repo.observePendingRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = Busy()

    fun load(s: Settings) {
        _ui.update {
            it.copy(
                mode = LanPrefs.mode(s),
                code = LanPrefs.code(s),
                host = LanPrefs.host(s),
                deviceName = LanPrefs.deviceName(s).ifBlank { host.deviceName },
                ip = host.localIp(),
                serving = server?.running == true
            )
        }
    }

    fun setDeviceName(s: Settings, name: String) {
        LanPrefs.setDeviceName(s, name)
        _ui.update { it.copy(deviceName = name) }
    }

    // ---------------- گوشیِ اصلی ----------------

    fun becomeMain(s: Settings) {
        val code = LanPrefs.code(s).ifBlank { LanPrefs.newCode().also { LanPrefs.setCode(s, it) } }
        LanPrefs.setMode(s, DeviceMode.MAIN)
        startServing(s, code)
    }

    fun newCode(s: Settings) {
        val c = LanPrefs.newCode()
        LanPrefs.setCode(s, c)
        _ui.update { it.copy(code = c) }
        if (server?.running == true) {
            stopServing()
            startServing(s, c)
        }
    }

    fun startServing(s: Settings, code: String = LanPrefs.code(s)) {
        val s = server ?: LanServer(host.ledger()).also { server = it }
        val ok = s.start(code)
        _ui.update {
            it.copy(
                serving = ok,
                mode = DeviceMode.MAIN,
                code = code,
                ip = host.localIp(),
                message = if (ok) "اشتراکِ کارگاه روشن شد."
                else "پورت باز نشد؛ شاید اپِ دیگری آن را گرفته باشد.",
                isError = !ok
            )
        }
    }

    fun stopServing() {
        server?.stop()
        _ui.update { it.copy(serving = false, message = "اشتراکِ کارگاه خاموش شد.", isError = false) }
    }

    fun approve(id: Long) = viewModelScope.launch {
        busy.once {
            val err = repo.approveRequest(id)
            _ui.update {
                if (err == null) it.copy(message = "✅ تأیید و ثبت شد.", isError = false)
                else it.copy(message = err, isError = true)
            }
        }
    }

    fun reject(id: Long) = viewModelScope.launch {
        busy.once {
            repo.rejectRequest(id)
            _ui.update { it.copy(message = "درخواست رد شد.", isError = false) }
        }
    }

    // ---------------- گوشیِ کارگر ----------------

    fun becomeWorker(s: Settings, host: String, code: String) = viewModelScope.launch {
        _ui.update { it.copy(checking = true, message = null) }
        val client = LanClient(host.trim(), code.trim())
        when (val r = client.ping()) {
            is LanResult.Err -> _ui.update {
                it.copy(checking = false, message = r.message, isError = true)
            }
            is LanResult.Ok -> {
                LanPrefs.setMode(s, DeviceMode.WORKER)
                LanPrefs.setHost(s, host)
                LanPrefs.setCode(s, code)
                _ui.update {
                    it.copy(
                        checking = false, mode = DeviceMode.WORKER,
                        host = host.trim(), code = code.trim(),
                        message = "وصل شد.", isError = false
                    )
                }
            }
        }
    }

    fun refreshMyWork(s: Settings, worker: String) = viewModelScope.launch {
        val host = LanPrefs.host(s)
        if (host.isBlank()) return@launch
        _ui.update { it.copy(checking = true) }
        when (val r = LanClient(host, LanPrefs.code(s)).myWork(worker)) {
            is LanResult.Err -> _ui.update {
                it.copy(checking = false, message = r.message, isError = true)
            }
            is LanResult.Ok -> _ui.update {
                it.copy(checking = false, myWork = r.value, message = null, isError = false)
            }
        }
    }

    fun sendRequest(
        s: Settings,
        worker: String,
        type: String,
        summary: String,
        refId: Long = 0,
        amount: Int = 0,
        note: String = ""
    ) = viewModelScope.launch {
        busy.once {
            val host = LanPrefs.host(s)
            val client = LanClient(host, LanPrefs.code(s))
            val device = LanPrefs.deviceName(s).ifBlank { host.deviceName }
            when (val r = client.sendRequest(device, worker, type, summary, refId, amount, note)) {
                is LanResult.Err -> _ui.update { it.copy(message = r.message, isError = true) }
                is LanResult.Ok -> _ui.update { it.copy(message = r.value, isError = false) }
            }
        }
    }

    fun disconnect(s: Settings) {
        LanPrefs.setMode(s, DeviceMode.STANDALONE)
        stopServing()
        _ui.update { it.copy(mode = DeviceMode.STANDALONE, myWork = emptyList()) }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    override fun onCleared() {
        server?.stop()
        super.onCleared()
    }
}
