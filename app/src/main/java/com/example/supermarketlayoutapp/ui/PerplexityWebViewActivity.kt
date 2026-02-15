package com.example.supermarketlayoutapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivityPerplexityWebviewBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

class PerplexityWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerplexityWebviewBinding
    private lateinit var janCode: String
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = Handler(Looper.getMainLooper())
    private var promptInjected = false
    private var injectionAttempts = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerplexityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        janCode = intent.getStringExtra("JAN_CODE") ?: ""

        setupWebView()
        setupButtons()
        loadPerplexity()
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Page finished: $url")
                    if (url?.contains("perplexity.ai") == true && !promptInjected) {
                        schedulePromptInjection()
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        Log.d("WebView-Console", "${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnInjectPrompt.setOnClickListener {
            injectPrompt()
        }

        binding.btnExtractData.setOnClickListener {
            extractData()
        }
    }

    private fun loadPerplexity() {
        binding.webView.loadUrl("https://www.perplexity.ai/")
    }

    private fun schedulePromptInjection() {
        // タイミングを延長し、リトライロジックを追加
        handler.postDelayed({ attemptInjectionWithRetry() }, 4000)
    }

    private fun attemptInjectionWithRetry() {
        if (promptInjected || injectionAttempts >= 5) {
            if (!promptInjected) {
                Toast.makeText(this, "自動注入失敗。手動ボタンをお試しください", Toast.LENGTH_LONG).show()
            }
            return
        }
        injectionAttempts++
        Log.d("WebView", "Injection attempt #$injectionAttempts")
        injectPrompt()
        
        // 失敗時は2秒後に再試行
        handler.postDelayed({ 
            if (!promptInjected) attemptInjectionWithRetry() 
        }, 2000)
    }

    private fun injectPrompt() {
        val prompt = buildPrompt(janCode)
        val safePrompt = jsEscape(prompt)
        
        // 改善版の注入スクリプト（複数の手法を組み合わせ）
        val script = """
            (function() {
                console.log('[Injection] Starting prompt injection...');
                var prompt = "$safePrompt";
                
                // 複数のセレクタパターンを試行
                var selectors = [
                    'textarea[placeholder*="Ask"]',
                    'textarea[placeholder*="質問"]',
                    'div[contenteditable="true"][role="textbox"]',
                    'div[contenteditable="true"]',
                    'textarea',
                    '[data-testid="search-input"]',
                    '[data-testid="ask-input"]',
                    'input[type="text"]'
                ];
                
                var input = null;
                for (var i = 0; i < selectors.length; i++) {
                    input = document.querySelector(selectors[i]);
                    if (input) {
                        console.log('[Injection] Found input with selector: ' + selectors[i]);
                        break;
                    }
                }
                
                if (!input) {
                    console.error('[Injection] INPUT_NOT_FOUND');
                    return 'INPUT_NOT_FOUND';
                }
                
                // フォーカスを当てる
                input.focus();
                console.log('[Injection] Input focused');
                
                // 方法1: contentEditable要素への直接代入
                if (input.contentEditable === 'true') {
                    input.innerText = prompt;
                    input.textContent = prompt;
                    console.log('[Injection] Set via innerText/textContent');
                } 
                // 方法2: textarea/inputへのvalue設定
                else if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                    input.value = prompt;
                    console.log('[Injection] Set via value');
                }
                
                // イベントディスパッチ（複数種類）
                try {
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                    input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                    console.log('[Injection] Events dispatched');
                } catch(e) {
                    console.error('[Injection] Event dispatch error:', e);
                }
                
                // 送信ボタンの自動クリック
                setTimeout(function() {
                    var btnSelectors = [
                        'button[aria-label*="送信"]',
                        'button[aria-label*="Send"]',
                        'button[aria-label*="Submit"]',
                        'button[type="submit"]',
                        'button svg[data-icon="arrow-up"]',
                        'button:has(svg)'
                    ];
                    
                    var sendBtn = null;
                    for (var i = 0; i < btnSelectors.length; i++) {
                        sendBtn = document.querySelector(btnSelectors[i]);
                        if (sendBtn && !sendBtn.disabled) {
                            console.log('[Injection] Found send button with: ' + btnSelectors[i]);
                            sendBtn.click();
                            console.log('[Injection] SENT');
                            return 'SENT';
                        }
                    }
                    console.log('[Injection] Send button not found or disabled');
                    return 'INPUT_SET';
                }, 1500);
                
                return 'PROCESSING';
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(script) { result ->
            Log.d("WebView", "Injection result: $result")
            
            when {
                result?.contains("INPUT_NOT_FOUND") == true -> {
                    Log.w("WebView", "Input element not found. Will retry...")
                }
                result?.contains("INPUT_SET") == true || result?.contains("SENT") == true -> {
                    promptInjected = true
                    Toast.makeText(this, "プロンプトを注入しました", Toast.LENGTH_SHORT).show()
                    startAutoMonitoring()
                }
                result?.contains("PROCESSING") == true -> {
                    Log.d("WebView", "Injection processing...")
                    // PROCESSINGの場合も成功とみなす（送信ボタンは後で押される）
                    handler.postDelayed({
                        if (!promptInjected) {
                            promptInjected = true
                            Toast.makeText(this, "プロンプトを設定しました", Toast.LENGTH_SHORT).show()
                            startAutoMonitoring()
                        }
                    }, 2000)
                }
            }
        }
    }

    private fun startAutoMonitoring() {
        handler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                if (attempts > 30) {
                    Log.w("WebView", "Auto-monitoring timeout after 30 attempts")
                    return
                }
                
                binding.webView.evaluateJavascript("(function() { return document.body.innerText; })();") { result ->
                    if (result != null && result.contains("<DATA_START>") && result.contains("<DATA_END>")) {
                        Log.d("WebView", "Data markers found! Extracting...")
                        extractData()
                    } else {
                        attempts++
                        handler.postDelayed(this, 2000)
                    }
                }
            }
        }, 5000)
    }

    private fun extractData() {
        binding.webView.evaluateJavascript(
            "(function() { return document.body.innerText; })();"
        ) { result ->
            result?.let {
                val decoded = decodeJsString(it)
                parseAndReturnData(decoded)
            }
        }
    }

    private fun buildPrompt(jan: String): String {
        return """
あなたはスーパーマーケット向け棚割システムのための商品マスターJSONを作成します。
必ず以下の制約を守ってください。
# 出力形式
- 回答は必ず次の形式だけを出力してください。
- 「<DATA_START>」の行から「<DATA_END>」の行までの間に、1つのJSONだけを出力してください。
- 説明・補足・会話文など、JSON以外のテキストは絶対に出力しないでください。
# JSONスキーマ
<DATA_START>
{
  "jan": "4901234567890",
  "maker": "〇〇株式会社",
  "name": "サンプル商品",
  "category": "清涼飲料",
  "min_price": 98,
  "max_price": 128,
  "width_cm": 5.5,
  "height_cm": 18.0,
  "depth_cm": 5.5
}
<DATA_END>
# ルール
- 数値項目は数値型で出力し、単位はすべてcmとする。
- サイズが不明な場合は null を入れる。
- 価格が不明な場合は min_price, max_price に null を入れる。
- 上のスキーマとキー名は絶対に変更しないこと。
# 入力情報
JAN: $jan
この入力情報を使い、上記と同じ形式でJSONを1つだけ出力してください。
        """.trimIndent()
    }

    private fun parseAndReturnData(htmlContent: String) {
        try {
            val jsonString = extractJsonFromAiText(htmlContent)
            if (jsonString != null) {
                Log.d("WebView", "Extracted JSON: $jsonString")
                val productData = json.decodeFromString<ProductData>(jsonString)
                returnResult(productData)
            } else {
                Log.e("WebView", "Failed to extract JSON from content")
            }
        } catch (e: Exception) {
            Log.e("PerplexityWebView", "JSON parse error", e)
        }
    }

    private fun extractJsonFromAiText(text: String): String? {
        val startMarker = "<DATA_START>"
        val endMarker = "<DATA_END>"
        val lastStart = text.lastIndexOf(startMarker)
        if (lastStart == -1) return null
        val lastEnd = text.indexOf(endMarker, lastStart)
        if (lastEnd == -1) return null
        return text.substring(lastStart + startMarker.length, lastEnd).trim()
    }

    private fun returnResult(data: ProductData) {
        val resultIntent = Intent().apply {
            putExtra("jan", data.jan)
            putExtra("maker", data.maker)
            putExtra("name", data.name)
            putExtra("category", data.category)
            putExtra("min_price", data.min_price ?: 0)
            putExtra("max_price", data.max_price ?: 0)
            putExtra("width_mm", data.width_cm?.times(10)?.toInt() ?: 0)
            putExtra("height_mm", data.height_cm?.times(10)?.toInt() ?: 0)
            putExtra("depth_mm", data.depth_cm?.times(10)?.toInt() ?: 0)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun jsEscape(s: String): String {
        val builder = StringBuilder()
        for (c in s) {
            when {
                c == '\\' -> builder.append("\\\\")
                c == '"' -> builder.append("\\\"")
                c == '\n' -> builder.append("\\n")
                c == '\r' -> builder.append("\\r")
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun decodeJsString(raw: String): String {
        var text = raw.removeSurrounding("\"")
        text = text.replace("\\n", "\n")
        text = text.replace("\\\"", "\"")
        return text
    }

    @Serializable
    data class ProductData(
        val jan: String,
        val maker: String? = null,
        val name: String,
        val category: String? = null,
        val min_price: Int? = null,
        val max_price: Int? = null,
        val width_cm: Double? = null,
        val height_cm: Double? = null,
        val depth_cm: Double? = null
    )
}
