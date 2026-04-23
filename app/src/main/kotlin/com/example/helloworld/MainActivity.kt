package com.example.helloworld

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.*
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
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
    private lateinit var btnImport: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var fabStop: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var autoCompleteTxt: AutoCompleteTextView
    private lateinit var loadingOverlay: View
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var database: AppDatabase
    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    private val languages = arrayOf("English - US", "Swahili - TZ", "Kirundi - BI")
    private val locales = arrayOf(Locale.US, Locale("sw", "TZ"), Locale("rn", "BI"))

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        startTime = System.currentTimeMillis()
        PDFBoxResourceLoader.init(applicationContext)
        database = AppDatabase.getDatabase(this)
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
        btnImport = findViewById(R.id.btn_import)
        btnHistory = findViewById(R.id.btn_history)
        btnFavorite = findViewById(R.id.btn_favorite)
        fabPlay = findViewById(R.id.fab_play)
        fabStop = findViewById(R.id.fab_stop)
        seekBar = findViewById(R.id.seek_bar)
        autoCompleteTxt = findViewById(R.id.auto_complete_txt)
        loadingOverlay = findViewById(R.id.loading_overlay)

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
                updateButtonsState(length > 0)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Buttons
        btnClear.setOnClickListener { stopPlayback(); etInput.text.clear() }
        btnPaste.setOnClickListener { pasteFromClipboard() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnImport.setOnClickListener { openFilePicker() }
        btnHistory.setOnClickListener { showHistoryPanel() }
        btnFavorite.setOnClickListener { saveToFavorites() }

        fabPlay.setOnClickListener { if (isPlaying) stopPlayback() else startPlayback() }
        fabStop.setOnClickListener { stopPlayback() }

        // Initialize TTS
        tts = TextToSpeech(this, this)
        etInput.requestFocus()
    }

    private fun updateButtonsState(hasText: Boolean) {
        if (hasText) {
            btnClear.visibility = View.VISIBLE
            btnFavorite.alpha = 1.0f
            if (!isPlaying) {
                fabPlay.isEnabled = true
                fabPlay.alpha = 1.0f
            }
        } else {
            btnClear.visibility = View.GONE
            btnFavorite.alpha = 0.5f
            if (!isPlaying) {
                fabPlay.isEnabled = false
                fabPlay.alpha = 0.5f
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0)
        item?.text?.let { etInput.append(it) }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/pdf"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun saveToFavorites() {
        val text = etInput.text.toString()
        if (text.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val item = HistoryItem(
                text = text,
                date = System.currentTimeMillis(),
                language = autoCompleteTxt.text.toString(),
                rate = speechRate,
                pitch = speechPitch,
                isFavorite = true
            )
            database.historyDao().insert(item)
            withContext(Dispatchers.Main) {
                btnFavorite.setColorFilter(Color.RED)
                Toast.makeText(this@MainActivity, "Saved to Favorites", Toast.LENGTH_SHORT).show()
            }
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

        // Save to History
        lifecycleScope.launch(Dispatchers.IO) {
            database.historyDao().insert(HistoryItem(
                text = text,
                date = System.currentTimeMillis(),
                language = autoCompleteTxt.text.toString(),
                rate = speechRate,
                pitch = speechPitch
            ))
        }

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
        btnFavorite.setColorFilter(Color.WHITE)
        etInput.setText(SpannableString(etInput.text.toString()))
    }

    private fun importFile(uri: Uri) {
        loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            var extractedText = ""
            var error: String? = null
            try {
                val mimeType = contentResolver.getType(uri)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (mimeType == "application/pdf") {
                        val document = PDDocument.load(inputStream)
                        extractedText = PDFTextStripper().getText(document)
                        document.close()
                    } else {
                        extractedText = BufferedReader(InputStreamReader(inputStream)).readText()
                    }
                }
            } catch (e: Exception) {
                error = "Error: Could not read file."
            }

            withContext(Dispatchers.Main) {
                loadingOverlay.visibility = View.GONE
                if (error != null) Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                else if (extractedText.trim().isEmpty()) Toast.makeText(this@MainActivity, "No readable text found.", Toast.LENGTH_SHORT).show()
                else {
                    if (extractedText.length > 5000) {
                        extractedText = extractedText.substring(0, 5000)
                        Toast.makeText(this@MainActivity, "Clipped to 5,000 chars.", Toast.LENGTH_LONG).show()
                    }
                    stopPlayback()
                    etInput.setText(extractedText)
                }
            }
        }
    }

    private fun showHistoryPanel() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.history_bottom_sheet, null)
        val rvHistory = view.findViewById<RecyclerView>(R.id.rv_history)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        val btnClearAll = view.findViewById<ImageButton>(R.id.btn_clear_all)

        rvHistory.layoutManager = LinearLayoutManager(this)
        val adapter = HistoryAdapter { item ->
            loadHistoryItem(item)
            dialog.dismiss()
        }
        rvHistory.adapter = adapter

        lifecycleScope.launch {
            database.historyDao().getAllHistory().collect { list ->
                val filteredList = if (tabLayout.selectedTabPosition == 0) list else list.filter { it.isFavorite }
                adapter.submitList(filteredList)
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                lifecycleScope.launch {
                    val list = database.historyDao().getAllHistory().first()
                    adapter.submitList(if (tab?.position == 0) list else list.filter { it.isFavorite })
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnClearAll.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { database.historyDao().clearHistory() }
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList[viewHolder.adapterPosition]
                lifecycleScope.launch(Dispatchers.IO) { database.historyDao().delete(item) }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvHistory)

        dialog.setContentView(view)
        dialog.show()
    }

    private fun loadHistoryItem(item: HistoryItem) {
        stopPlayback()
        etInput.setText(item.text)
        speechRate = item.rate
        speechPitch = item.pitch
        autoCompleteTxt.setText(item.language, false)
        val index = languages.indexOf(item.language)
        if (index != -1) tts?.language = locales[index]
        applySettings()
        updateSettingsIconState()
    }

    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.settings_bottom_sheet, null)
        val seekSpeed = view.findViewById<SeekBar>(R.id.seek_speed)
        val seekPitch = view.findViewById<SeekBar>(R.id.seek_pitch)
        val tvSpeedLabel = view.findViewById<TextView>(R.id.tv_speed_label)
        val tvPitchLabel = view.findViewById<TextView>(R.id.tv_pitch_label)

        seekSpeed.progress = ((speechRate - 0.5f) * 100).toInt()
        seekPitch.progress = ((speechPitch - 0.5f) * 100).toInt()
        tvSpeedLabel.text = "Speed: %.1fx".format(speechRate)
        tvPitchLabel.text = "Pitch: %.1f".format(speechPitch)

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                speechRate = (p / 100f) + 0.5f
                tvSpeedLabel.text = "Speed: %.1fx".format(speechRate)
                applySettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                speechPitch = (p / 100f) + 0.5f
                tvPitchLabel.text = "Pitch: %.1f".format(speechPitch)
                applySettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        view.findViewById<Button>(R.id.btn_reset).setOnClickListener {
            speechRate = 1.0f; speechPitch = 1.0f
            seekSpeed.progress = 50; seekPitch.progress = 50
            tvSpeedLabel.text = "Speed: 1.0x"; tvPitchLabel.text = "Pitch: 1.0"
            applySettings()
        }

        view.findViewById<Button>(R.id.btn_preview).setOnClickListener {
            tts?.speak("Voice preview.", TextToSpeech.QUEUE_FLUSH, null, "PREVIEW")
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
        if (isPlaying && etInput.text.isNotEmpty()) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TTS_READER")
            tts?.speak(etInput.text.toString().substring(seekBar.progress), TextToSpeech.QUEUE_FLUSH, params, "TTS_READER")
        }
    }

    private fun updateSettingsIconState() {
        btnSettings.setColorFilter(if (speechRate != 1.0f || speechPitch != 1.0f) Color.parseColor("#BB86FC") else Color.WHITE)
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
                    if (id == "TTS_READER") runOnUiThread { highlightText(start, end); seekBar.progress = start }
                }
            })
            isTtsReady = true
        } else isTtsReady = true
    }

    private fun highlightText(start: Int, end: Int) {
        val fullText = etInput.text.toString()
        if (start < 0 || end > fullText.length) return
        val spannable = SpannableString(fullText)
        spannable.setSpan(BackgroundColorSpan(Color.parseColor("#44BB86FC")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        etInput.setText(spannable)
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }

    inner class HistoryAdapter(private val onClick: (HistoryItem) -> Unit) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        var currentList = emptyList<HistoryItem>()
        fun submitList(newList: List<HistoryItem>) { currentList = newList; notifyDataSetChanged() }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_history, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) = h.bind(currentList[p])
        override fun getItemCount() = currentList.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            fun bind(item: HistoryItem) {
                itemView.findViewById<TextView>(R.id.tv_snippet).text = item.text
                val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.date))
                itemView.findViewById<TextView>(R.id.tv_meta).text = "$dateStr • ${item.language}"
                val btnFav = itemView.findViewById<ImageButton>(R.id.btn_favorite_item)
                btnFav.setImageResource(if (item.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                btnFav.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) { database.historyDao().update(item.copy(isFavorite = !item.isFavorite)) }
                }
                itemView.findViewById<View>(R.id.ll_content).setOnClickListener { onClick(item) }
            }
        }
    }
}
