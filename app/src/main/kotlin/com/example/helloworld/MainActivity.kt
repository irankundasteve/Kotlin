package com.example.helloworld

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "Hello World"
        textView.textSize = 24f
        textView.gravity = Gravity.CENTER
        setContentView(textView)
    }
}
