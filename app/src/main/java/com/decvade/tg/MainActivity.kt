package com.decvade.tg

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

class MainActivity : Activity() {

    companion object {
        const val FREE = 0
        const val TAKEN = 1
        const val LIMIT = 2
        const val ERROR = 3
        private val VALID = Regex("^[a-zA-Z][a-zA-Z0-9_]{3,30}[a-zA-Z0-9]$")
        private val PREFIX_OK = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
    }

    private class Res(val kind: Int, val waitSec: Int = 0)

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private val rnd = Random()
    private val seen = HashSet<String>()
    private val found = ArrayList<String>()

    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var statusView: TextView
    private lateinit var singleView: TextView
    private lateinit var startBtn: Button
    private lateinit var nameInput: EditText
    private lateinit var prefixInput: EditText
    private lateinit var lenInput: EditText
    private lateinit var lettersOnly: CheckBox
    private lateinit var dictBox: CheckBox
    private val doneWords = HashSet<String>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(Color.WHITE)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        v.setPadding(0, dp(8), 0, dp(4))
        return v
    }

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun lp(top: Int = 8): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun input(h: String): EditText {
        val v = EditText(this)
        v.hint = h
        v.setHintTextColor(Color.GRAY)
        v.setTextColor(Color.WHITE)
        v.setSingleLine(true)
        v.background = rounded(0xFF1A1A22.toInt(), 14)
        v.setPadding(dp(16), dp(14), dp(16), dp(14))
        v.layoutParams = lp()
        return v
    }

    private fun button(t: String): Button {
        val v = Button(this)
        v.text = t
        v.isAllCaps = false
        v.textSize = 16f
        v.setTextColor(Color.BLACK)
        v.background = rounded(Color.WHITE, 14)
        v.layoutParams = lp(10)
        return v
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(dp(16), dp(24), dp(16), dp(16))

        root.addView(label("Decvade - TG", 28f, true))

        // Проверка одного ника
        root.addView(label("Проверить ник", 16f, true))
        nameInput = input("@username")
        root.addView(nameInput)
        val singleBtn = button("Проверить")
        root.addView(singleBtn)
        singleView = label("", 14f)
        root.addView(singleView)

        // Автопоиск
        root.addView(label("Автопоиск свободных", 16f, true))
        prefixInput = input("Начало ника (необязательно)")
        root.addView(prefixInput)
        lenInput = input("Длина (необязательно, 5-32)")
        lenInput.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(lenInput)
        lettersOnly = CheckBox(this)
        lettersOnly.text = "Только буквы"
        lettersOnly.setTextColor(Color.WHITE)
        lettersOnly.isChecked = true
        root.addView(lettersOnly)
        dictBox = CheckBox(this)
        dictBox.text = "По словам из словаря (words.txt)"
        dictBox.setTextColor(Color.WHITE)
        dictBox.isChecked = true
        root.addView(dictBox)
        startBtn = button("Старт")
        root.addView(startBtn)
        statusView = label("", 14f)
        root.addView(statusView)

        root.addView(label("Свободные (нажми, чтобы скопировать)", 14f))
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, found)
        list.adapter = adapter
        list.background = rounded(0xFF14141A.toInt(), 16)
        list.divider = null
        list.setPadding(dp(8), dp(8), dp(8), dp(8))
        list.setOnItemClickListener { _, _, pos, _ ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("username", "@" + found[pos]))
            toast("Скопировано: @" + found[pos])
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        singleBtn.setOnClickListener {
            val n = nameInput.text.toString().trim().removePrefix("@")
            if (!isValid(n)) {
                singleView.text = "Неверный ник: 5-32 символа, латиница, цифры и _, начинается с буквы"
                return@setOnClickListener
            }
            singleView.text = "Проверяю..."
            Thread {
                val r = check(n)
                val msg = when (r.kind) {
                    FREE -> "@$n похоже свободен. Но Telegram может не дать его занять (зарезервирован или на Fragment)."
                    TAKEN -> "@$n занят"
                    LIMIT -> "Telegram просит подождать, попробуй позже"
                    else -> "Не удалось проверить, смотри интернет"
                }
                ui.post { singleView.text = msg }
            }.start()
        }

        startBtn.setOnClickListener {
            if (running) {
                running = false
            } else {
                startSearch()
            }
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun isValid(n: String) = VALID.matches(n) && !n.contains("__")

    private fun startSearch() {
        val prefix = prefixInput.text.toString().trim().removePrefix("@")
        if (prefix.length >= 32) {
            toast("Начало слишком длинное")
            return
        }
        if (prefix.isNotEmpty() && !PREFIX_OK.matches(prefix)) {
            toast("В начале только латиница, цифры и _, первая буква")
            return
        }
        val typed = lenInput.text.toString().trim().toIntOrNull()
        val len = if (typed == null) 0 else typed.coerceIn(5, 32)
        val letters = lettersOnly.isChecked
        var dict: List<String>? = null
        if (dictBox.isChecked) {
            val all = try {
                assets.open("words.txt").bufferedReader().readLines()
            } catch (e: Exception) {
                emptyList<String>()
            }
            val p = prefix.lowercase()
            val list = all.map { it.trim().lowercase() }
                .filter { isValid(it) && it.startsWith(p) && !doneWords.contains(it) }
                .filter { len == 0 || it.length == len }
                .filter { !letters || it.all { c -> c.isLetter() } }
                .distinct().shuffled()
            if (list.isEmpty()) {
                toast("Подходящих слов не найдено")
                return
            }
            dict = list
        }
        val d = dict
        running = true
        startBtn.text = "Стоп"
        Thread { runSearch(prefix, len, letters, d) }.start()
    }

    private fun setStatus(t: String) {
        ui.post { statusView.text = t }
    }

    private fun sleepMs(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
        }
    }

    private fun runSearch(prefix: String, len: Int, letters: Boolean, dict: List<String>?) {
        val queue = ArrayDeque<String>(dict ?: emptyList())
        var checked = 0
        var freeCount = 0
        var fails = 0
        var pause = 800L

        while (running) {
            val name: String? = if (dict != null) {
                queue.removeFirstOrNull()
            } else {
                val l = maxOf(if (len == 0) 5 + rnd.nextInt(4) else len, prefix.length + 1, 5)
                candidate(prefix, minOf(l, 32), letters)
            }
            if (name == null) {
                setStatus("Слова или варианты закончились. Проверено: $checked, свободных: $freeCount")
                break
            }
            setStatus("Проверено: $checked, свободных: $freeCount\nСейчас: @$name")
            val r = check(name)

            if (r.kind == LIMIT || r.kind == ERROR) {
                // этот ник не проверили, вернём его в очередь
                if (dict != null) queue.addFirst(name) else seen.remove(name)
                fails++
                if (r.kind == LIMIT) pause = minOf(pause + 1000L, 10000L)
                val wait = if (r.waitSec > 0) r.waitSec else minOf(300, 10 * (1 shl minOf(fails, 5)))
                var left = wait
                while (running && left > 0) {
                    setStatus("Пауза (лимит или нет сети): $left с\nПроверено: $checked, свободных: $freeCount")
                    sleepMs(1000)
                    left--
                }
                continue
            }

            fails = 0
            checked++
            if (dict != null) doneWords.add(name)
            if (r.kind == FREE) {
                freeCount++
                ui.post {
                    found.add(0, name)
                    adapter.notifyDataSetChanged()
                }
            }
            pause = maxOf(800L, pause - 100L)
            sleepMs(pause)
        }

        running = false
        ui.post {
            startBtn.text = "Старт"
            if (statusView.text.startsWith("Проверено") || statusView.text.startsWith("Пауза")) {
                statusView.text = "Остановлено. Проверено: $checked, свободных: $freeCount"
            }
        }
    }

    private fun candidate(prefix: String, len: Int, letters: Boolean): String? {
        val abc = "abcdefghijklmnopqrstuvwxyz"
        val full = if (letters) abc else abc + "0123456789_"
        repeat(300) {
            val sb = StringBuilder(prefix)
            while (sb.length < len) {
                val isLast = sb.length == len - 1
                var ch = 'a'
                do {
                    val pool = if (sb.isEmpty()) abc else full
                    ch = pool[rnd.nextInt(pool.length)]
                } while (ch == '_' && (isLast || sb.last() == '_'))
                sb.append(ch)
            }
            val s = sb.toString()
            if (isValid(s) && seen.add(s)) return s
        }
        return null
    }

    private fun check(name: String): Res {
        var c: HttpURLConnection? = null
        try {
            c = URL("https://t.me/$name").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            val code = c.responseCode
            if (code == 429) {
                val w = c.getHeaderField("Retry-After")?.trim()?.toIntOrNull() ?: 0
                return Res(LIMIT, w)
            }
            if (code != 200) return Res(ERROR)
            val html = c.inputStream.bufferedReader().use { it.readText() }
            return if (html.contains("tgme_page_title")) Res(TAKEN) else Res(FREE)
        } catch (e: Exception) {
            return Res(ERROR)
        } finally {
            c?.disconnect()
        }
    }
}
