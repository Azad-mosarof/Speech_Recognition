package com.example.speech_recognition

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class web_activity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        webView = findViewById(R.id.webView)
        var text = intent.getStringExtra("quarry")?.replace("search","")
        val search = "https://www.google.com/search?q=$text"
        webView.apply {
            loadUrl(search)
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
        }
    }
}