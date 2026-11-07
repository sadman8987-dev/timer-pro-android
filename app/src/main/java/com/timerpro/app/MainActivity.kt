package com.timerpro.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(12,17,30)
    private val panel = Color.rgb(24,32,49)
    private val blue = Color.rgb(43,119,255)
    private val white = Color.WHITE
    private val muted = Color.rgb(160,174,196)
    private val handler = Handler(Looper.getMainLooper())
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_ALARM, 90) }
    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var lap: Button
    private lateinit var setup: LinearLayout
    private lateinit var laps: LinearLayout
    private lateinit var hInput: EditText
    private lateinit var mInput: EditText
    private lateinit var sInput: EditText
    private var countdown = false
    private var running = false
    private var startedAt = 0L
    private var elapsed = 0L
    private var remaining = 0L
    private var lapNumber = 0
    private var lastLap = 0L
    private var ticker: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(25), dp(22), dp(20))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.background = try {
            BitmapDrawable(resources, assets.open("timer_background.jpg").use { BitmapFactory.decodeStream(it) }).apply { alpha = 170 }
        } catch (_: Exception) {
            android.graphics.drawable.ColorDrawable(bg)
        }
        root.addView(label("TIMER PRO",25f,white,true).apply { gravity = Gravity.CENTER }, match())
        root.addView(label("مؤقتك اليومي — يعمل بدون إنترنت",14f,muted).apply {
            gravity = Gravity.CENTER
            setPadding(0,dp(8),0,dp(22))
        },match())

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4),dp(4),dp(4),dp(4))
            setBackgroundColor(panel)
        }
        val sw = button("ساعة الإيقاف")
        val cd = button("العد التنازلي")
        sw.setOnClickListener { changeMode(false) }
        cd.setOnClickListener { changeMode(true) }
        tabs.addView(sw,LinearLayout.LayoutParams(0,dp(48),1f))
        tabs.addView(cd,LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(tabs,match())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12),dp(30),dp(12),dp(25))
            setBackgroundColor(panel)
        }
        display = label("00:00:00",48f,white,true).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
        }
        status = label("المؤقت متوقف",14f,muted).apply { gravity = Gravity.CENTER; setPadding(0,dp(10),0,0) }
        card.addView(display,match())
        card.addView(status,match())
        root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })

        setup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(18),0,0) }
        setup.addView(label("حدد المدة",15f,white,true),match())
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hInput = input()
        mInput = input()
        sInput = input()
        listOf(Pair(hInput,"ساعة"),Pair(mInput,"دقيقة"),Pair(sInput,"ثانية")).forEach { pair ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(pair.first,LinearLayout.LayoutParams(dp(82),dp(52)))
            col.addView(label(pair.second,12f,muted).apply { gravity = Gravity.CENTER; setPadding(0,dp(5),0,0) },match())
            fields.addView(col,LinearLayout.LayoutParams(0,dp(88),1f))
        }
        setup.addView(fields,match())
        root.addView(setup,match())

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0,dp(18),0,0) }
        start = button("ابدأ")
        lap = button("لفة")
        val reset = button("إعادة ضبط")
        actions.addView(start,LinearLayout.LayoutParams(0,dp(54),1.2f).apply { marginEnd = dp(6) })
        actions.addView(lap,LinearLayout.LayoutParams(0,dp(54),0.8f).apply { marginEnd = dp(6) })
        actions.addView(reset,LinearLayout.LayoutParams(0,dp(54),1f))
        root.addView(actions,match())
        start.setOnClickListener { toggle() }
        lap.setOnClickListener { addLap() }
        reset.setOnClickListener { resetTimer() }
        root.addView(label("سجل اللفات",16f,white,true).apply { setPadding(0,dp(24),0,dp(8)) },match())
        laps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(laps,match())
        setContentView(root)
        setup.visibility = View.GONE
        lap.visibility = View.VISIBLE
        render()
    }

    private fun changeMode(toCountdown: Boolean) {
        if (countdown == toCountdown) return
        stopTicker()
        running = false
        countdown = toCountdown
        elapsed = 0L
        remaining = 0L
        lapNumber = 0
        lastLap = 0L
        laps.removeAllViews()
        setup.visibility = if (countdown) View.VISIBLE else View.GONE
        lap.visibility = if (countdown) View.GONE else View.VISIBLE
        start.text = "ابدأ"
        status.text = "المؤقت متوقف"
        render()
    }

    private fun toggle() {
        if (running) {
            updateTime()
            running = false
            stopTicker()
            start.text = "استكمال"
            status.text = "متوقف مؤقتًا"
            render()
            return
        }
        if (countdown && remaining <= 0L) {
            val h = hInput.text.toString().toLongOrNull() ?: 0L
            val m = mInput.text.toString().toLongOrNull() ?: 0L
            val s = sInput.text.toString().toLongOrNull() ?: 0L
            if (h == 0L && m == 0L && s == 0L) {
                Toast.makeText(this,"أدخل مدة أكبر من صفر",Toast.LENGTH_SHORT).show()
                return
            }
            remaining = ((h*3600L)+(m*60L)+s).coerceAtMost(99L*3600L)*1000L
        }
        running = true
        startedAt = SystemClock.elapsedRealtime()
        start.text = "إيقاف مؤقت"
        status.text = if (countdown) "العد التنازلي يعمل" else "ساعة الإيقاف تعمل"
        runTicker()
        render()
    }

    private fun updateTime() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now-startedAt).coerceAtLeast(0L)
        if (countdown) {
            remaining = (remaining-delta).coerceAtLeast(0L)
            if (remaining == 0L) {
                running = false
                start.text = "ابدأ من جديد"
                status.text = "انتهى الوقت!"
                try { tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,900) } catch (_: Exception) {}
                Toast.makeText(this,"انتهى الوقت!",Toast.LENGTH_LONG).show()
            }
        } else elapsed += delta
        startedAt = now
    }

    private fun runTicker() {
        stopTicker()
        val task = object : Runnable {
            override fun run() {
                if (!running) return
                updateTime()
                render()
                if (running) handler.postDelayed(this,50L)
            }
        }
        ticker = task
        handler.post(task)
    }

    private fun stopTicker() { ticker?.let { handler.removeCallbacks(it) }; ticker = null }

    private fun render() {
        val value = if (countdown) remaining else elapsed + if (running) SystemClock.elapsedRealtime()-startedAt else 0L
        val total = value.coerceAtLeast(0L)/1000L
        display.text = String.format(Locale.US,"%02d:%02d:%02d",total/3600,(total%3600)/60,total%60)
        lap.isEnabled = !countdown && running
        lap.alpha = if (lap.isEnabled) 1f else 0.45f
    }

    private fun addLap() {
        if (!running || countdown) return
        val current = elapsed + SystemClock.elapsedRealtime()-startedAt
        val split = current-lastLap
        lastLap = current
        lapNumber++
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14),dp(12),dp(14),dp(12))
            setBackgroundColor(panel)
        }
        row.addView(label("لفة " + lapNumber,14f,white),LinearLayout.LayoutParams(0,dp(30),1f))
        row.addView(label(format(current),14f,white,true),wrap())
        row.addView(label("  +" + format(split),12f,muted),wrap())
        laps.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(7) })
    }

    private fun resetTimer() {
        running = false
        stopTicker()
        elapsed = 0L
        remaining = 0L
        lapNumber = 0
        lastLap = 0L
        laps.removeAllViews()
        start.text = "ابدأ"
        status.text = "تمت إعادة الضبط"
        render()
    }

    override fun onPause() {
        super.onPause()
        if (running) { updateTime(); stopTicker() }
    }
    override fun onResume() {
        super.onResume()
        if (running) { startedAt = SystemClock.elapsedRealtime(); runTicker() }
    }
    override fun onDestroy() { stopTicker(); tone.release(); super.onDestroy() }

    private fun input() = EditText(this).apply {
        setText("00")
        setTextColor(white)
        textSize = 18f
        gravity = Gravity.CENTER
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setSelectAllOnFocus(true)
        setBackgroundColor(Color.rgb(39,50,72))
    }
    private fun label(value: String,size: Float,color: Int,bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value: String) = Button(this).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        setTextColor(white)
        backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
    }
    private fun format(ms: Long): String {
        val sec = ms/1000L
        return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60)
    }
    private fun dp(v: Int) = (v*resources.displayMetrics.density).toInt()
    private fun match() = LinearLayout.LayoutParams(-1,-2)
    private fun wrap() = LinearLayout.LayoutParams(-2,-2)
}