package com.decvade.tg

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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

    private fun input(h: String): EditText {
        val v = EditText(this)
        v.hint = h
        v.setHintTextColor(Color.GRAY)
        v.setTextColor(Color.WHITE)
        v.setSingleLine(true)
        return v
    }

    private fun button(t: String): Button {
        val v = Button(this)
        v.text = t
        v.setTextColor(Color.BLACK)
        v.setBackgroundColor(Color.WHITE)
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

        root.addView(label("Decvade - TG", 24f, true))

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
        lenInput = input("Длина ника (5-32)")
        lenInput.inputType = InputType.TYPE_CLASS_NUMBER
        lenInput.setText("5")
        root.addView(lenInput)
        lettersOnly = CheckBox(this)
        lettersOnly.text = "Только буквы"
        lettersOnly.setTextColor(Color.WHITE)
        lettersOnly.isChecked = true
        root.addView(lettersOnly)
        startBtn = button("Старт")
        root.addView(startBtn)
        statusView = label("", 14f)
        root.addView(statusView)

        root.addView(label("Свободные (нажми, чтобы скопировать)", 14f))
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, found)
        list.adapter = adapter
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
        val len = lenInput.text.toString().trim().toIntOrNull() ?: 5
        if (len < 5 || len > 32) {
            toast("Длина должна быть от 5 до 32")
            return
        }
        if (prefix.length >= len) {
            toast("Начало должно быть короче длины")
            return
        }
        if (prefix.isNotEmpty() && !PREFIX_OK.matches(prefix)) {
            toast("В начале только латиница, цифры и _, первая буква")
            return
        }
        val letters = lettersOnly.isChecked
        running = true
        startBtn.text = "Стоп"
        Thread { runSearch(prefix, len, letters) }.start()
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

    private fun runSearch(prefix: String, len: Int, letters: Boolean) {
        var checked = 0
        var freeCount = 0
        var fails = 0
        var pause = 2500L

        while (running) {
            val name = candidate(prefix, len, letters)
            if (name == null) {
                setStatus("Варианты закончились. Проверено: $checked, свободных: $freeCount")
                break
            }
            setStatus("Проверено: $checked, свободных: $freeCount\nСейчас: @$name")
            val r = check(name)

            if (r.kind == LIMIT || r.kind == ERROR) {
                // этот ник не проверили, вернём его в очередь
                seen.remove(name)
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
            if (r.kind == FREE) {
                freeCount++
                ui.post {
                    found.add(0, name)
                    adapter.notifyDataSetChanged()
                }
            }
            pause = maxOf(2500L, pause - 200L)
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
