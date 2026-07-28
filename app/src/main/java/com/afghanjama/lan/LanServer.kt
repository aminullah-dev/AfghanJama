package com.afghanjama.lan

import android.content.Context
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.data.entities.SyncRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * سرورِ گوشیِ اصلی روی وای‌فای کارگاه.
 *
 * **قانونِ یکتای این فایل:** هرچه از شبکه می‌آید فقط می‌تواند یک
 * «درخواست» در جدولِ `sync_requests` بنشاند. هیچ مسیری اینجا سفارش،
 * فروش، پرداخت یا موجودی را دست نمی‌زند. تصمیمِ پول فقط روی گوشیِ
 * کارفرما و با دستِ خودش گرفته می‌شود — این همان چیزی است که کلِ
 * مسئلهٔ تضادِ دو نویسنده را حذف می‌کند.
 */
class LanServer(private val context: Context) {

    private var socket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    @Volatile var running: Boolean = false
        private set

    /** رمزِ اتصال؛ روی گوشیِ اصلی نمایش داده می‌شود و کارگر واردش می‌کند. */
    @Volatile private var code: String = ""

    fun start(pairCode: String): Boolean {
        if (running) return true
        code = pairCode.trim()
        if (code.isBlank()) return false
        return runCatching {
            val s = ServerSocket(Lan.PORT)
            s.reuseAddress = true
            socket = s
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = sc
            running = true
            sc.launch { acceptLoop(s, sc) }
            true
        }.getOrElse {
            running = false
            false
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        scope?.cancel()
        scope = null
    }

    private suspend fun acceptLoop(server: ServerSocket, sc: CoroutineScope) {
        while (sc.isActive && running) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            sc.launch { runCatching { handle(client) }; runCatching { client.close() } }
        }
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]

        // هدرها
        var contentLength = 0
        var givenCode = ""
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val i = line.indexOf(':')
            if (i <= 0) continue
            val key = line.substring(0, i).trim()
            val value = line.substring(i + 1).trim()
            when {
                key.equals("Content-Length", true) -> contentLength = value.toIntOrNull() ?: 0
                key.equals(Lan.HEADER_CODE, true) -> givenCode = value
            }
        }

        val body = if (contentLength > 0) {
            val buf = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buf, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            String(buf, 0, read)
        } else ""

        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")

        // /ping بدونِ رمز جواب می‌دهد تا کارگر بفهمد نشانی درست است؛
        // ولی هیچ داده‌ای در آن نیست.
        if (path == Lan.PATH_PING) {
            respond(client, 200, JSONObject().apply {
                put("ok", true)
                put("app", "AfghanJama")
                put("protocol", Lan.PROTOCOL)
            })
            return
        }

        if (givenCode != code) {
            respond(client, 401, JSONObject().apply {
                put("ok", false); put("error", "رمزِ اتصال درست نیست.")
            })
            return
        }

        when {
            method == "GET" && path == Lan.PATH_MY_WORK -> serveMyWork(client, param(query, "worker"))
            method == "POST" && path == Lan.PATH_REQUEST -> acceptRequest(client, body)
            else -> respond(client, 404, JSONObject().apply {
                put("ok", false); put("error", "مسیر پیدا نشد.")
            })
        }
    }

    private fun param(query: String, key: String): String =
        query.split("&").firstNotNullOfOrNull {
            val i = it.indexOf('=')
            if (i > 0 && it.substring(0, i) == key)
                runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrNull()
            else null
        }.orEmpty()

    /** فقط خواندن: کارِ زیرِ دستِ همین خیاط و کارمزدِ تسویه‌نشده‌اش. */
    private suspend fun serveMyWork(client: Socket, worker: String) {
        val name = worker.trim()
        if (name.isBlank()) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "نامِ کارگر خالی است.")
            })
            return
        }
        val db = buildAppDatabase(context)
        try {
            val mine = db.sewingAssignmentDao().observeAll().let { flow ->
                kotlinx.coroutines.flow.first(flow)
            }.filter { it.tailorLabel.trim() == name }

            val items = JSONArray()
            mine.filter { it.status != "DONE" }.forEach { a ->
                items.put(JSONObject().apply {
                    put("id", a.id)
                    put("orderCode", a.orderCode)
                    put("qty", a.qty)
                    put("unitWage", a.unitWage)
                    put("createdAt", a.createdAt)
                })
            }
            val doneCount = mine.count { it.status == "DONE" }
            respond(client, 200, JSONObject().apply {
                put("ok", true)
                put("worker", name)
                put("inHand", items)
                put("doneTotal", doneCount)
            })
        } finally {
            db.close()
        }
    }

    /** تنها راهِ نوشتن از شبکه — و فقط در صندوقِ درخواست‌ها. */
    private suspend fun acceptRequest(client: Socket, body: String) {
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json == null) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "بدنهٔ درخواست خوانده نشد.")
            })
            return
        }
        val type = json.optString("type").trim()
        if (type !in ALLOWED_TYPES) {
            respond(client, 400, JSONObject().apply {
                put("ok", false); put("error", "نوعِ درخواست پذیرفته نیست.")
            })
            return
        }
        val db = buildAppDatabase(context)
        try {
            val id = db.syncRequestDao().insert(
                SyncRequest(
                    deviceName = json.optString("device").take(60).ifBlank { "گوشی کارگر" },
                    worker = json.optString("worker").trim().take(60),
                    type = type,
                    summary = json.optString("summary").take(200),
                    refId = json.optLong("refId", 0L),
                    amount = json.optInt("amount", 0),
                    note = json.optString("note").take(300)
                )
            )
            respond(client, 200, JSONObject().apply {
                put("ok", true); put("id", id)
                put("message", "درخواست ثبت شد و منتظرِ تأییدِ کارفرماست.")
            })
        } finally {
            db.close()
        }
    }

    private fun respond(client: Socket, status: Int, json: JSONObject) {
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else "ERROR"}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            client.getOutputStream().apply {
                write(head.toByteArray(Charsets.UTF_8))
                write(payload)
                flush()
            }
        }
    }

    companion object {
        /**
         * فهرستِ بسته و صریحِ کارهایی که از شبکه پذیرفته می‌شوند. هر
         * چیزی بیرونِ این فهرست رد می‌شود — تا اضافه‌شدنِ یک قابلیتِ
         * جدید ناخواسته دری به دفتر باز نکند.
         */
        val ALLOWED_TYPES = setOf(
            "SEWING_DONE", "ATTENDANCE_IN", "ATTENDANCE_OUT", "NOTE"
        )
    }
}
