package com.decvade.tg

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private val VALID = Regex("^[a-zA-Z][a-zA-Z0-9_]{3,30}[a-zA-Z0-9]$")
        private val PREFIX_OK = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
    }

    private val ui = Handler(Looper.getMainLooper())
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
    private lateinit var checkButton: Button
    @Volatile private var checkingUsername = false

    private lateinit var appConfig: AppConfig
    private val domainChecker = DomainChecker()
    private lateinit var scorer: UsernameScorer

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
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
        v.setHintTextColor(Color.parseColor("#8994A6"))
        v.setTextColor(Color.parseColor("#F2F5F9"))
        v.setSingleLine(true)
        v.textSize = 15f
        v.background = rounded(Color.parseColor("#171A1F"), 12)
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        v.layoutParams = lp(8)
        return v
    }

    private fun button(t: String, primary: Boolean = true): Button {
        val v = Button(this)
        v.text = t
        v.isAllCaps = false
        v.textSize = 15f
        v.setTextColor(if (primary) Color.parseColor("#101820") else Color.WHITE)
        v.background = rounded(
            if (primary) Color.parseColor("#76A9FF") else Color.parseColor("#303744"),
            12
        )
        v.layoutParams = lp(8)
        return v
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.parseColor("#202630"), 18)
        setPadding(dp(16), dp(14), dp(16), dp(16))
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appConfig = UsernameConfigLoader.load(this)
        scorer = UsernameScorer(appConfig.scoring)

        window.statusBarColor = Color.parseColor("#171A1F")
        window.navigationBarColor = Color.parseColor("#171A1F")

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#171A1F"))
        root.setPadding(dp(18), dp(18), dp(18), dp(12))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(label("Decvade", 28f, true))
        header.addView(
            label(
                "Поиск и проверка Telegram-юзернеймов",
                14f,
                color = Color.parseColor("#A5B0C2")
            ).apply { setPadding(0, dp(3), 0, 0) }
        )
        root.addView(header)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val checkCard = card()
        checkCard.addView(label("Проверить ник", 18f, true))
        checkCard.addView(
            label(
                "Публичная проверка в Telegram и Fragment",
                13f,
                color = Color.parseColor("#A5B0C2")
            ).apply { setPadding(0, dp(4), 0, 0) }
        )
        nameInput = input("@username")
        checkCard.addView(nameInput)
        checkButton = button("Проверить в Telegram и Fragment")
        checkCard.addView(checkButton)
        singleView = label("", 14f)
        singleView.setPadding(0, dp(10), 0, 0)
        checkCard.addView(singleView)
        controls.addView(checkCard, lp(8))

        val generatorCard = card()
        generatorCard.addView(label("Сгенерировать варианты", 18f, true))
        generatorCard.addView(
            label(
                "Подбор работает локально. Проверка сайтов — только по вашему нажатию.",
                13f,
                color = Color.parseColor("#A5B0C2")
            ).apply { setPadding(0, dp(4), 0, 0) }
        )
        prefixInput = input("Начало ника (необязательно)")
        generatorCard.addView(prefixInput)
        lenInput = input("Длина (необязательно, 5-32)")
        lenInput.inputType = InputType.TYPE_CLASS_NUMBER
        generatorCard.addView(lenInput)
        lettersOnly = CheckBox(this)
        lettersOnly.text = "Только буквы"
        lettersOnly.setTextColor(Color.parseColor("#F2F5F9"))
        lettersOnly.isChecked = true
        generatorCard.addView(lettersOnly)
        dictBox = CheckBox(this)
        dictBox.text = "По словам из словаря (words.txt)"
        dictBox.setTextColor(Color.parseColor("#F2F5F9"))
        dictBox.isChecked = true
        generatorCard.addView(dictBox)
        startBtn = button("Найти варианты")
        generatorCard.addView(startBtn)
        controls.addView(generatorCard, lp(12))

        val controlsScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
            addView(controls)
        }
        root.addView(
            controlsScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f)
        )

        statusView = label("", 14f)
        statusView.setTextColor(Color.parseColor("#A5B0C2"))
        statusView.setPadding(0, dp(6), 0, dp(6))
        root.addView(statusView)

        val resultsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        resultsHeader.addView(label("Варианты", 17f, true))
        resultsHeader.addView(
            label("  ·  нажмите, чтобы проверить", 13f, color = Color.parseColor("#A5B0C2"))
        )
        root.addView(resultsHeader)

        val list = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, found) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.parseColor("#F2F5F9"))
                    textSize = 16f
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
        list.adapter = adapter
        list.background = rounded(Color.parseColor("#202630"), 16)
        list.divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#303744"))
        list.dividerHeight = dp(1)
        list.setPadding(dp(8), dp(4), dp(8), dp(4))
        list.isVerticalScrollBarEnabled = true
        list.setOnItemClickListener { _, _, pos, _ ->
            checkUsername(found[pos])
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        checkButton.setOnClickListener { checkUsername(nameInput.text.toString().trim().removePrefix("@")) }

        startBtn.setOnClickListener { generateSuggestions() }
    }

    private fun isValid(n: String) = VALID.matches(n) && !n.contains("__")

    private fun checkUsername(name: String) {
        val normalized = name.trim().removePrefix("@")
        nameInput.setText(normalized)
        if (!isValid(normalized)) {
            singleView.text = "Ник должен содержать 5–32 латинских символа, цифры или _."
            return
        }
        if (checkingUsername) {
            toast("Дождитесь завершения текущей проверки")
            return
        }

        val domains = appConfig.domains.filter { it.enabled }
        if (domains.isEmpty()) {
            singleView.text = "Нет включённых сервисов для проверки."
            return
        }

        checkingUsername = true
        checkButton.isEnabled = false
        checkButton.text = "Проверяю…"
        singleView.text = "Проверка @$normalized…"

        Thread {
            val results = domains.map { domainChecker.check(normalized, it) }
            val message = buildString {
                results.forEach { result ->
                    val serviceName = when (result.domain) {
                        "telegram" -> "Telegram"
                        "fragment" -> "Fragment"
                        else -> result.domain
                    }
                    append("$serviceName: ")
                    when {
                        result.statusCode == 429 -> {
                            append("лимит запросов")
                            if (result.waitSeconds > 0) append(", повторите через ${result.waitSeconds} с")
                        }
                        result.error != null -> append("не удалось проверить (${result.error})")
                        result.available == true -> append("страница профиля не найдена, возможно свободен")
                        result.available == false -> append("страница найдена, возможно занят")
                        else -> append("сервис не дал однозначного результата")
                    }
                    append('\n')
                }
                append("Предварительный результат: сайты не гарантируют возможность занять ник.")
            }
            ui.post {
                checkingUsername = false
                checkButton.isEnabled = true
                checkButton.text = "Проверить в Telegram и Fragment"
                singleView.text = message
            }
        }.start()
    }

    private fun generateSuggestions() {
        val prefix = prefixInput.text.toString().trim().removePrefix("@")
        if (prefix.length >= 32) {
            toast("Начало слишком длинное")
            return
        }
        if (prefix.isNotEmpty() && !PREFIX_OK.matches(prefix)) {
            toast("В начале только латиница, цифры и _, первая буква")
            return
        }

        val lengthText = lenInput.text.toString().trim()
        val requestedLength = if (lengthText.isEmpty()) {
            null
        } else {
            lengthText.toIntOrNull()?.takeIf { it in appConfig.search.minLength..appConfig.search.maxLength }
        }
        if (lengthText.isNotEmpty() && requestedLength == null) {
            toast("Длина должна быть от ${appConfig.search.minLength} до ${appConfig.search.maxLength}")
            return
        }
        val letters = lettersOnly.isChecked

        try {
            val dictionary = if (dictBox.isChecked) {
                UsernameConfigLoader.loadDictionary(this, appConfig.generator)
            } else {
                emptyList()
            }
            val candidates = UsernameGenerator(appConfig.generator, dictionary)
                .generateCandidates(
                    prefix = prefix,
                    maxCount = 200,
                    minLength = requestedLength ?: appConfig.search.minLength,
                    maxLength = requestedLength ?: appConfig.search.maxLength,
                    lettersOnly = letters,
                    allowDigits = appConfig.search.allowDigits,
                    allowUnderscore = appConfig.search.allowUnderscore
                )
                .filter(::isValid)
            val ranked = candidates
                .distinct()
                .sortedByDescending(scorer::score)

            found.clear()
            found.addAll(ranked)
            adapter.notifyDataSetChanged()
            statusView.text = if (found.isEmpty()) {
                "Подходящих вариантов не найдено"
            } else {
                "Сгенерировано вариантов: ${found.size}. Выберите ник и запустите ручную проверку."
            }
        } catch (e: Exception) {
            statusView.text = "Не удалось создать варианты"
            toast(e.message ?: "Ошибка чтения конфигурации или словаря")
        }
    }
}
