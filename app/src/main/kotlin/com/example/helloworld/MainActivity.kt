package com.example.helloworld

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isPlaying = false
    private var startTime: Long = 0

    private lateinit var etInput: EditText
    private lateinit var tvCounter: TextView
    private lateinit var btnClear: ImageButton
    private lateinit var btnPaste: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var fabStop: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var autoCompleteTxt: AutoCompleteTextView
    
    private lateinit var sharedPreferences: SharedPreferences
    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    private val languages = arrayOf("English - US", "Swahili - TZ", "Kirundi - BI")
    private val locales = arrayOf(Locale.US, Locale("sw", "TZ"), Locale("rn", "BI"))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        startTime = System.currentTimeMillis()
        sharedPreferences = getSharedPreferences("TTS_PREFS", Context.MODE_PRIVATE)
        speechRate = sharedPreferences.getFloat("speech_rate", 1.0f)
        speechPitch = sharedPreferences.getFloat("speech_pitch", 1.0f)

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
        btnSettings = findViewById(R.id.btn_settings)
        fabPlay = findViewById(R.id.fab_play)
        fabStop = findViewById(R.id.fab_stop)
        seekBar = findViewById(R.id.seek_bar)
        autoCompleteTxt = findViewById(R.id.auto_complete_txt)

        updateSettingsIconState()

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

        btnSettings.setOnClickListener { showSettingsDialog() }

        fabPlay.setOnClickListener {
            if (isPlaying) stopPlayback() else startPlayback()
        }

        fabStop.setOnClickListener { stopPlayback() }

        // Initialize TTS
        tts = TextToSpeech(this, this)
        etInput.requestFocus()
    }

    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.settings_bottom_sheet, null)
        
        val seekSpeed = view.findViewById<SeekBar>(R.id.seek_speed)
        val seekPitch = view.findViewById<SeekBar>(R.id.seek_pitch)
        val tvSpeedLabel = view.findViewById<TextView>(R.id.tv_speed_label)
        val tvPitchLabel = view.findViewById<TextView>(R.id.tv_pitch_label)
        val btnReset = view.findViewById<Button>(R.id.btn_reset)
        val btnPreview = view.findViewById<Button>(R.id.btn_preview)

        // Set initial values: speed (0.5 to 2.0 -> 0 to 150), pitch (0.5 to 1.5 -> 0 to 100)
        seekSpeed.progress = ((speechRate - 0.5f) * 100).toInt()
        seekPitch.progress = ((speechPitch - 0.5f) * 100).toInt()
        tvSpeedLabel.text = "Speed: %.1fx".format(speechRate)
        tvPitchLabel.text = "Pitch: %.1f".format(speechPitch)

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                speechRate = (p / 100f) + 0.5f
                tvSpeedLabel.text = "Speed: %.1fx".format(speechRate)
                applySettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                speechPitch = (p / 100f) + 0.5f
                tvPitchLabel.text = "Pitch: %.1f".format(speechPitch)
                applySettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnReset.setOnClickListener {
            speechRate = 1.0f
            speechPitch = 1.0f
            seekSpeed.progress = 50
            seekPitch.progress = 50
            tvSpeedLabel.text = "Speed: 1.0x"
            tvPitchLabel.text = "Pitch: 1.0"
            applySettings()
        }

        btnPreview.setOnClickListener {
            tts?.speak("This is a voice preview.", TextToSpeech.QUEUE_FLUSH, null, "PREVIEW")
        }

        dialog.setContentView(view)
        dialog.show()
        dialog.setOnDismissListener {
            sharedPreferences.edit().putFloat("speech_rate", speechRate).putFloat("speech_pitch", speechPitch).apply()
            updateSettingsIconState()
        }
    }

    private fun applySettings() {
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
        // If playing, re-speak from current position for real-time feel
        if (isPlaying && !etInput.text.isNullOrEmpty()) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TTS_READER")
            tts?.speak(etInput.text.toString().substring(seekBar.progress), TextToSpeech.QUEUE_FLUSH, params, "TTS_READER")
        }
    }

    private fun updateSettingsIconState() {
        if (speechRate != 1.0f || speechPitch != 1.0f) {
            btnSettings.setColorFilter(Color.parseColor("#BB86FC"))
        } else {
            btnSettings.setColorFilter(Color.WHITE)
        }
    }

    private fun startPlayback() {
        val text = etInput.text.toString()
        if (text.isEmpty()) return

        isPlaying = true
        fabPlay.setImageResource(R.drawable.ic_pause)
        fabStop.visibility = View.VISIBLE
        seekBar.visibility = View.VISIBLE
        seekBar.max = text.length
        seekBar.progress = 0

        applySettings()
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TTS_READER")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TTS_READER")
    }

    private fun stopPlayback() {
        tts?.stop()
        isPlaying = false
        fabPlay.setImageResource(R.drawable.ic_play)
        fabStop.visibility = View.GONE
        seekBar.visibility = View.INVISIBLE
        
        val spannable = SpannableString(etInput.text.toString())
        etInput.setText(spannable)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == "TTS_READER") runOnUiThread { stopPlayback() } }
                override fun onError(id: String?) { runOnUiThread { stopPlayback() } }
                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    if (id == "TTS_READER") runOnUiThread {
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
        spannable.setSpan(BackgroundColorSpan(Color.parseColor("#44BB86FC")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        etInput.setText(spannable)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
