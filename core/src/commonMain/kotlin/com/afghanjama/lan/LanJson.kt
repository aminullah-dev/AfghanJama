package com.afghanjama.lan

/**
 * JSONِ قراردادِ ۲ — یک خواننده و یک نویسنده برای هر سه سکو.
 *
 * **چرا لازم شد.** قراردادِ ۱ با `org.json` نوشته شده، که فقط روی جاوا و
 * اندروید هست. قراردادِ ۲ را آیفون هم حرف می‌زند، و همان پیام باید روی
 * کامپیوتر (سرور) ساخته و روی آیفون (مشتری) خوانده شود؛ اگر هر سو نویسندهٔ
 * خودش را داشت، روزی یکی کلیدی را جا می‌انداخت که آن یکی می‌خواست.
 *
 * مقدارها همان شکلِ ساده‌اند: `Map<String, Any?>`، `List<Any?>`، `String`،
 * `Long`، `Double`، `Boolean` یا `null`.
 *
 * **سخت‌گیر است.** هر چیزِ ناجور (ویرگولِ اضافه، کلیدِ تکراری، متنِ ناتمام،
 * دنبالهٔ نامعتبر، عمقِ بیش از [MAX_DEPTH]) [LanJsonError] می‌دهد، نه
 * نیمه‌ای از پیام. خواننده‌ای که «تا جایی که فهمید» بپذیرد، پیامی را که
 * کسی دست‌کاری کرده نیمه‌کاره اجرا می‌کند.
 *
 * این رمزنگاری نیست و جای آن را نمی‌گیرد: هر پیامِ قراردادِ ۲ که داده دارد
 * پیش از رسیدن به اینجا با AES-GCM سنجیده شده ([LanCrypto]).
 */
object LanJson {

    /** ژرف‌ترین تودرتویی که پذیرفته می‌شود — پیام‌های ما سه چهار لایه‌اند. */
    const val MAX_DEPTH = 32

    class LanJsonError(message: String) : IllegalArgumentException(message)

    /** متن را می‌خوانَد؛ هر ایراد [LanJsonError]. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (!p.atEnd()) throw LanJsonError("متنِ اضافه پس از JSON")
        return v
    }

    /** مثلِ [parse]، ولی فقط شیء؛ هر چیزِ دیگر یا ایراد `null`. */
    fun parseObject(text: String): Map<String, Any?>? =
        runCatching { parse(text) }.getOrNull()?.let { asObject(it) }

    @Suppress("UNCHECKED_CAST")
    fun asObject(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    /** مقدار را می‌نویسد. نوعِ ناشناخته ایراد است، نه `toString()`ِ بی‌صدا. */
    fun write(v: Any?): String = StringBuilder().also { out(it, v, 0) }.toString()

    private fun out(sb: StringBuilder, v: Any?, depth: Int) {
        if (depth > MAX_DEPTH) throw LanJsonError("عمقِ بیش از اندازه")
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> {
                if (v.isNaN() || v.isInfinite()) throw LanJsonError("عددِ نامتناهی در JSON نمی‌نشیند")
                sb.append(v.toString())
            }
            is Float -> out(sb, v.toDouble(), depth)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (k !is String) throw LanJsonError("کلیدِ غیرِرشته")
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k)
                    sb.append(':')
                    out(sb, value, depth + 1)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, x ->
                    if (i > 0) sb.append(',')
                    out(sb, x, depth + 1)
                }
                sb.append(']')
            }
            else -> throw LanJsonError("نوعِ ناشناخته در JSON: ${v::class.simpleName}")
        }
    }

    private const val HEX = "0123456789abcdef"

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> {
                    val n = c.code
                    sb.append("\\u")
                    sb.append(HEX[(n shr 12) and 0xF]).append(HEX[(n shr 8) and 0xF])
                    sb.append(HEX[(n shr 4) and 0xF]).append(HEX[n and 0xF])
                }
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else throw LanJsonError("JSON ناتمام")

        private fun expect(c: Char) {
            if (peek() != c) throw LanJsonError("انتظارِ «$c» در $i")
            i++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw LanJsonError("عمقِ بیش از اندازه")
            return when (peek()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> num()
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw LanJsonError("واژهٔ نامعتبر در $i")
            i += w.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (peek() == '}') { i++; return out }
            while (true) {
                ws()
                val k = str()
                ws()
                expect(':')
                ws()
                if (out.containsKey(k)) throw LanJsonError("کلیدِ تکراری «$k»")
                out[k] = value(depth + 1)
                ws()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw LanJsonError("انتظارِ «,» یا «}» در $i")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            ws()
            if (peek() == ']') { i++; return out }
            while (true) {
                ws()
                out += value(depth + 1)
                ws()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw LanJsonError("انتظارِ «,» یا «]» در $i")
                }
            }
        }

        private fun hex4(): Int {
            if (i + 4 > s.length) throw LanJsonError("\\u ناتمام")
            var n = 0
            repeat(4) {
                val d = HEX.indexOf(s[i].lowercaseChar())
                if (d < 0) throw LanJsonError("\\u نامعتبر")
                n = (n shl 4) or d
                i++
            }
            return n
        }

        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                i++
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> sb.append(hex4().toChar())
                            else -> throw LanJsonError("دنبالهٔ نامعتبر \\$e")
                        }
                    }
                    c < ' ' -> throw LanJsonError("نویسهٔ کنترلی در رشته")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (peek() == '-') i++
            val digits = i
            while (i < s.length && s[i] in '0'..'9') i++
            if (i == digits) throw LanJsonError("عددِ نامعتبر در $start")
            // صفرِ پیشین (`012`) در JSON نیست.
            if (s[digits] == '0' && i - digits > 1) throw LanJsonError("عددِ نامعتبر در $start")
            var fraction = false
            if (i < s.length && s[i] == '.') {
                fraction = true
                i++
                val f = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == f) throw LanJsonError("عددِ نامعتبر در $start")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                fraction = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val e = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == e) throw LanJsonError("عددِ نامعتبر در $start")
            }
            val t = s.substring(start, i)
            if (!fraction) t.toLongOrNull()?.let { return it }
            return t.toDoubleOrNull()?.takeIf { !it.isInfinite() && !it.isNaN() }
                ?: throw LanJsonError("عددِ نامعتبر «$t»")
        }
    }
}

/** یک شیءِ JSON، همان‌طور که [LanJson.parse] می‌دهد. */
typealias JsonObj = Map<String, Any?>

/*
 * خواندنِ امنِ یک شیء. هر کلیدِ نبوده یا ناجور مقدارِ پیش‌فرض می‌گیرد؛
 * آنچه واقعاً لازم است صداکننده با `?:` می‌خواهد و نبودنش را رد می‌کند.
 */

fun JsonObj.jStr(key: String): String? = this[key] as? String

fun JsonObj.jLong(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 9.0e15) v.toLong() else null
    else -> null
}

fun JsonObj.jInt(key: String): Int? = jLong(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

fun JsonObj.jBool(key: String): Boolean? = this[key] as? Boolean

fun JsonObj.jObj(key: String): Map<String, Any?>? = LanJson.asObject(this[key])

fun JsonObj.jList(key: String): List<Any?>? = this[key] as? List<Any?>

/** فهرستِ شیء‌ها؛ هر عضوی که شیء نیست کنار می‌رود. */
fun JsonObj.jObjects(key: String): List<Map<String, Any?>> =
    jList(key).orEmpty().mapNotNull { LanJson.asObject(it) }

/** فهرستِ رشته‌ها؛ هر عضوی که رشته نیست کنار می‌رود. */
fun JsonObj.jStrings(key: String): List<String> =
    jList(key).orEmpty().filterIsInstance<String>()
