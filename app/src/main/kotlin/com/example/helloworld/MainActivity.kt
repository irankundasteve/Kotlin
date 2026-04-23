package com.example.helloworld

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
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
    private var isPlaying = false
    private var isPaused = false
    private var startTime: Long = 0

    private lateinit var etInput: EditText
    private lateinit var tvCounter: TextView
    private lateinit var btnClear: ImageButton
    private lateinit var btnPaste: ImageButton
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var fabStop: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var autoCompleteTxt: AutoCompleteTextView

    private val languages = arrayOf("English - US", "Swahili - TZ", "Kirundi - BI")
    private val locales = arrayOf(Locale.US, Locale("sw", "TZ"), Locale("rn", "BI"))
    private var currentText = ""

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
        fabStop = findViewById(R.id.fab_stop)
        seekBar = findViewById(R.id.seek_bar)
        autoCompleteTxt = findViewById(R.id.auto_complete_txt)

        // Setup Dropdown
        val adapterItems = ArrayAdapter(this, R.layout.list_item, languages)
        autoCompleteTxt.setAdapter(adapterItems)
        autoCompleteTxt.setText(languages[0], false)
        autoCompleteTxt.setOnItemClickListener { _, _, position, _ ->
            tts?.language = locales[position]
        }

        // Setup TextWatcher
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvCounter.text = "$length / 5000"
                
                if (length > 0) {
                    btnClear.visibility = View.VISIBLE
                    if (!isPlaying) {
                        fabPlay.isEnabled = true
                        fabPlay.alpha = 1.0f
                    }
                } else {
                    btnClear.visibility = View.GONE
                    if (!isPlaying) {
                        fabPlay.isEnabled = false
                        fabPlay.alpha = 0.5f
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Buttons
        btnClear.setOnClickListener {
            stopPlayback()
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
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }

        fabStop.setOnClickListener {
            stopPlayback()
        }

        // Setup SeekBar (Manual seeking is complex with TTS, so it acts as progress only here)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize TTS
        tts = TextToSpeech(this, this)
        etInput.requestFocus()
    }

    private fun startPlayback() {
        val text = etInput.text.toString()
        if (text.isEmpty()) return

        currentText = text
        isPlaying = true
        isPaused = false
        fabPlay.setImageResource(R.drawable.ic_pause)
        fabStop.visibility = View.VISIBLE
        seekBar.visibility = View.VISIBLE
        seekBar.max = text.length
        seekBar.progress = 0

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TTS_READER")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TTS_READER")
    }

    private fun pausePlayback() {
        // Android TTS doesn't have a native "pause". 
        // We simulate it by stopping and we would need to resume from index.
        // For simplicity in Part 3, we toggle stop/play.
        stopPlayback()
    }

    private fun stopPlayback() {
        tts?.stop()
        isPlaying = false
        isPaused = false
        fabPlay.setImageResource(R.drawable.ic_play)
        fabStop.visibility = View.GONE
        seekBar.visibility = View.INVISIBLE
        seekBar.progress = 0
        
        // Remove highlighting
        val content = etInput.text.toString()
        val spannable = SpannableString(content)
        etInput.setText(spannable)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "Started")
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread { stopPlayback() }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread { stopPlayback() }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    runOnUiThread {
                        highlightText(start, end)
                        seekBar.progress = start
                    }
                }
            })
            isTtsReady = true
        } else {
            isTtsReady = true
        }
    }

    private fun highlightText(start: Int, end: Int) {
        val fullText = etInput.text.toString()
        if (start < 0 || end > fullText.length) return
        
        val spannable = SpannableString(fullText)
        spannable.setSpan(
            BackgroundColorSpan(Color.parseColor("#44BB86FC")), // Translucent Primary
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        etInput.setText(spannable)
        etInput.setSelection(etInput.text.length) // Keep cursor at end or scroll?
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
