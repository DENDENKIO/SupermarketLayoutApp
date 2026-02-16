package com.example.supermarketlayoutapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivitySettingsBinding

/**
 * 設定画面
 * 
 * AIサイトへのログイン状態管理を行います。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    
    companion object {
        private const val PREFS_NAME = "SupermarketLayoutApp"
        private const val KEY_PERPLEXITY_LOGGED_IN = "perplexity_logged_in"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        updateLoginStatus()
        setupButtons()
        setupBackPressedHandler()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    /**
     * バックボタンハンドラーの設定
     */
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // WebView表示中の場合は設定画面に戻る
                if (binding.webView.visibility == android.view.View.VISIBLE) {
                    binding.webView.visibility = android.view.View.GONE
                    binding.settingsContent.visibility = android.view.View.VISIBLE
                    binding.btnBackToSettings.visibility = android.view.View.GONE
                    updateLoginStatus()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * ログイン状態を更新
     */
    private fun updateLoginStatus() {
        val isLoggedIn = isPerplexityLoggedIn()
        
        if (isLoggedIn) {
            binding.tvLoginStatus.text = "✅ Perplexity.aiにログイン済み"
            binding.btnLogin.text = "再ログイン"
        } else {
            binding.tvLoginStatus.text = "❌ Perplexity.aiに未ログイン"
            binding.btnLogin.text = "Perplexity.aiにログイン"
        }
    }

    /**
     * ボタンの設定
     */
    private fun setupButtons() {
        // ログインボタン
        binding.btnLogin.setOnClickListener {
            openPerplexityLogin()
        }
        
        // ログアウトボタン
        binding.btnLogout.setOnClickListener {
            clearPerplexityCookies()
            Toast.makeText(this, "ログアウトしました", Toast.LENGTH_SHORT).show()
            updateLoginStatus()
        }
    }

    /**
     * Perplexity.aiログイン画面を開く
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun openPerplexityLogin() {
        // WebViewを表示
        binding.webView.visibility = android.view.View.VISIBLE
        binding.settingsContent.visibility = android.view.View.GONE
        
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // ログイン完了を検知（URLがホームに戻ったら）
                    if (url?.contains("perplexity.ai") == true && 
                        !url.contains("/login") && 
                        !url.contains("/signin")) {
                        
                        // Cookieが設定されているか確認
                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)
                        
                        if (cookies != null && cookies.isNotEmpty()) {
                            // ログイン状態を保存
                            savePerplexityLoginStatus(true)
                            
                            Toast.makeText(
                                this@SettingsActivity,
                                "ログインが完了しました",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            loadUrl("https://www.perplexity.ai/")
        }
        
        // 戻るボタンを有効化
        binding.btnBackToSettings.visibility = android.view.View.VISIBLE
        binding.btnBackToSettings.setOnClickListener {
            binding.webView.visibility = android.view.View.GONE
            binding.settingsContent.visibility = android.view.View.VISIBLE
            binding.btnBackToSettings.visibility = android.view.View.GONE
            updateLoginStatus()
        }
    }

    /**
     * Perplexityのログイン状態を確認
     */
    private fun isPerplexityLoggedIn(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedStatus = prefs.getBoolean(KEY_PERPLEXITY_LOGGED_IN, false)
        
        // Cookieが存在するかも確認
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://www.perplexity.ai")
        
        return savedStatus && cookies != null && cookies.isNotEmpty()
    }

    /**
     * Perplexityのログイン状態を保存
     */
    private fun savePerplexityLoginStatus(isLoggedIn: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERPLEXITY_LOGGED_IN, isLoggedIn).apply()
    }

    /**
     * PerplexityのCookieをクリア
     */
    private fun clearPerplexityCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        
        savePerplexityLoginStatus(false)
    }
}
