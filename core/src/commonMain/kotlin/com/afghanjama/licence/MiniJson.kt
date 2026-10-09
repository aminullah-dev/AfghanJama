package com.afghanjama.licence

/**
 * خوانندهٔ کوچکِ JSON — فقط برای بارِ کلیدِ لایسنس.
 *
 * **چرا کتابخانه نه.** `:core` هیچ کتابخانهٔ JSONِ چندسکویی ندارد
 * (`org.json` فقط روی جاوا و اندروید است و `lan/` از همان استفاده
 * می‌کند). بارِ کلید هم کوچک و ثابت است: یک شیء با رشته، عدد، `null`
 * و یک فهرستِ رشته. افزودنِ یک وابستگیِ تازه به هر سه سکو برای همین
 * چند خط، بهایی است که نمی‌ارزد.
 *
 * سخت‌گیر است: هر چیزِ نادرست (ویرگولِ اضافه، متنِ ناتمام، دنبالهٔ
 * نامعتبر) استثنا می‌دهد و صداکننده آن را «کلیدِ خراب» می‌خوانَد —
 * نه اینکه نیمه‌اش را بپذیرد.
 *
 * خروجی: `Map<String, Any?>`، `List<Any?>`، `String`، `Long`، `Double`،
 * `Boolean` یا `null`.
 */
internal object MiniJson {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw IllegalArgumentException("متنِ اضافه پس از JSON")
        return v
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i] in " \t\r\n") i++
        }

        private fun peek(): Char =
            if (i < s.length) s[i] else throw IllegalArgumentException("JSON ناتمام")

        private fun expect(c: Char) {
            if (peek() != c) throw IllegalArgumentException("انتظارِ «$c» در $i")
            i++
        }

        fun value(): Any? = when (peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> num()
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw IllegalArgumentException("واژهٔ نامعتبر در $i")
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return out }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                expect(':')
                skipWs()
                if (out.containsKey(k)) throw IllegalArgumentException("کلیدِ تکراری «$k»")
                out[k] = value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw IllegalArgumentException("انتظارِ «,» یا «}» در $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { i++; return out }
            while (true) {
                skipWs()
                out += value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw IllegalArgumentException("انتظارِ «,» یا «]» در $i")
                }
            }
        }

        private fun str(): String {
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
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("\\u ناتمام")
                                val hex = s.substring(i, i + 4)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("دنبالهٔ نامعتبر \\$e")
                        }
                    }
                    c < ' ' -> throw IllegalArgumentException("نویسهٔ کنترلی در رشته")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t == "-") throw IllegalArgumentException("عددِ نامعتبر در $start")
            return t.toLongOrNull() ?: t.toDoubleOrNull()
                ?: throw IllegalArgumentException("عددِ نامعتبر «$t»")
        }
    }
}
