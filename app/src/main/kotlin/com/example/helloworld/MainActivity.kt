package com.example.helloworld

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var startTime: Long = 0

    private lateinit var etInput: EditText
    private lateinit var tvCounter: TextView
    private lateinit var btnClear: ImageButton
    private lateinit var btnPaste: ImageButton
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var autoCompleteTxt: AutoCompleteTextView

    private val languages = arrayOf("English - US", "Swahili - TZ", "Kirundi - BI")
    private val locales = arrayOf(Locale.US, Locale("sw", "TZ"), Locale("rn", "BI"))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        startTime = System.currentTimeMillis()

        splashScreen.setKeepOnScreenCondition {
            val elapsedTime = System.currentTimeMillis() - startTime
            !isTtsReady || elapsedTime < 1000
        }

        setContentView(R.layout.activity_main)

        // Initialize Views
        etInput = findViewById(R.id.et_input)
        tvCounter = findViewById(R.id.tv_counter)
        btnClear = findViewById(R.id.btn_clear)
        btnPaste = findViewById(R.id.btn_paste)
        fabPlay = findViewById(R.id.fab_play)
        autoCompleteTxt = findViewById(R.id.auto_complete_txt)

        // Setup Dropdown
        val adapterItems = ArrayAdapter(this, R.layout.list_item, languages)
        autoCompleteTxt.setAdapter(adapterItems)
        autoCompleteTxt.setText(languages[0], false)
        autoCompleteTxt.setOnItemClickListener { _, _, position, _ ->
            tts?.language = locales[position]
        }

        // Setup TextWatcher for Counter and FAB state
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvCounter.text = "$length / 5000"
                
                if (length > 0) {
                    btnClear.visibility = View.VISIBLE
                    fabPlay.isEnabled = true
                    fabPlay.alpha = 1.0f
                } else {
                    btnClear.visibility = View.GONE
                    fabPlay.isEnabled = false
                    fabPlay.alpha = 0.5f
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Buttons
        btnClear.setOnClickListener {
            etInput.text.clear()
        }

        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteData = item?.text
            if (pasteData != null) {
                etInput.append(pasteData)
            }
        }

        fabPlay.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_READER")
            }
        }

        // Initialize TTS
        tts = TextToSpeech(this, this)
        etInput.requestFocus()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        } else {
            Log.e("TTS", "Initialization failed")
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
