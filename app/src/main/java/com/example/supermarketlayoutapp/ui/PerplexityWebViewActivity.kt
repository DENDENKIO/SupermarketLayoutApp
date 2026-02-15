package com.example.supermarketlayoutapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.R
import com.example.supermarketlayoutapp.databinding.ActivityPerplexityWebviewBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PerplexityWebViewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPerplexityWebviewBinding
    private lateinit var janCode: String
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 10
    
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
    
    private fun setupButtons() {
        binding.btnInjectPrompt.setOnClickListener {
            injectPrompt()
            Toast.makeText(this, "プロンプトを入力しました", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnExtractData.setOnClickListener {
            extractDataFromPage()
        }
        
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished loading: $url")
                    if (url?.contains("perplexity.ai") == true) {
                        // ページ読み込み完了後、自動でプロンプト入力を試行
                        retryCount = 0
                        schedulePromptInjection()
                    }
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        Log.d(TAG, "Console: ${it.message()} [${it.sourceId()}:${it.lineNumber()}]")
                    }
                    return true
                }
            }
        }
    }
    
    private fun loadPerplexity() {
        Log.d(TAG, "Loading Perplexity.ai")
        binding.webView.loadUrl("https://www.perplexity.ai/")
    }
    
    private fun schedulePromptInjection() {
        handler.postDelayed({
            injectPrompt()
        }, 3000) // 3秒待ってから実行
    }
    
    private fun injectPrompt() {
        val prompt = buildPrompt(janCode)
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        
        // 複数のセレクターを試すJavaScript
        val js = """
            (function() {
                var selectors = [
                    'textarea[placeholder*="Ask"]',
                    'textarea[placeholder*="anything"]',
                    'textarea',
                    'div[contenteditable="true"]',
                    'input[type="text"]'
                ];
                
                var element = null;
                for (var i = 0; i < selectors.length; i++) {
                    element = document.querySelector(selectors[i]);
                    if (element) {
                        console.log('Found element with selector: ' + selectors[i]);
                        break;
                    }
                }
                
                if (element) {
                    if (element.tagName === 'TEXTAREA' || element.tagName === 'INPUT') {
                        element.value = "$escapedPrompt";
                        element.focus();
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                    } else if (element.contentEditable === 'true') {
                        element.textContent = "$escapedPrompt";
                        element.focus();
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                    
                    // 送信ボタンを探してクリック
                    setTimeout(function() {
                        var submitSelectors = [
                            'button[type="submit"]',
                            'button[aria-label*="Submit"]',
                            'button[aria-label*="Send"]',
                            'button svg',
                            'button'
                        ];
                        
                        for (var j = 0; j < submitSelectors.length; j++) {
                            var buttons = document.querySelectorAll(submitSelectors[j]);
                            for (var k = 0; k < buttons.length; k++) {
                                var btn = buttons[k];
                                if (btn && !btn.disabled) {
                                    console.log('Clicking submit button');
                                    btn.click();
                                    return 'SUCCESS';
                                }
                            }
                        }
                        return 'NO_BUTTON';
                    }, 500);
                    
                    return 'INJECTED';
                } else {
                    console.log('No input element found');
                    return 'NOT_FOUND';
                }
            })();
        """.trimIndent()
        
        binding.webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "Injection result: $result")
            when {
                result?.contains("INJECTED") == true -> {
                    Log.d(TAG, "Prompt injected successfully")
                    binding.statusText.text = "プロンプトを入力しました。AIの応答を待っています..."
                    // 10秒後にデータ抽出を開始
                    handler.postDelayed({ startPollingForData() }, 10000)
                }
                result?.contains("NOT_FOUND") == true && retryCount < maxRetries -> {
                    retryCount++
                    Log.d(TAG, "Retry $retryCount/$maxRetries")
                    handler.postDelayed({ injectPrompt() }, 2000)
                }
                else -> {
                    Log.e(TAG, "Failed to inject prompt after $maxRetries retries")
                    binding.statusText.text = "自動入力に失敗しました。手動でプロンプトを入力してください。"
                    binding.manualButtons.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun startPollingForData() {
        handler.post(object : Runnable {
            var pollCount = 0
            val maxPolls = 30 // 30秒間ポーリング
            
            override fun run() {
                if (pollCount >= maxPolls) {
                    binding.statusText.text = "タイムアウト。手動でデータを抽出してください。"
                    binding.manualButtons.visibility = View.VISIBLE
                    return
                }
                
                extractDataFromPage()
                pollCount++
                handler.postDelayed(this, 1000) // 1秒ごとにチョック
            }
        })
    }
    
    private fun extractDataFromPage() {
        binding.webView.evaluateJavascript(
            "(function() { return document.body.innerText; })();"
        ) { result ->
            result?.let { 
                val unescaped = result.replace("\\\"", "\"").removeSurrounding("\"")
                Log.d(TAG, "Page content length: ${unescaped.length}")
                parseAndReturnData(unescaped)
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
- サイズが不明な場合は null を入れる（例: "width_cm": null）。
- 価格が不明な場合は min_price, max_price に null を入れる。
- 上のスキーマとキー名は絶対に変更しないこと。

# 入力情報
JAN: $jan

この入力情報を使い、上記と同じ形式でJSONを1つだけ出力してください。
必ず1つのJSONだけを<DATA_START>と<DATA_END>の間に出力し、それ以外は何も出力しないこと。
        """.trimIndent()
    }
    
    private fun parseAndReturnData(htmlContent: String) {
        try {
            val jsonString = extractJsonFromAiText(htmlContent)
            if (jsonString != null) {
                Log.d(TAG, "Found JSON: $jsonString")
                val productData = json.decodeFromString<ProductData>(jsonString)
                returnResult(productData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
        }
    }
    
    private fun extractJsonFromAiText(text: String): String? {
        val lastStart = text.lastIndexOf("<DATA_START>")
        if (lastStart == -1) return null
        
        val lastEnd = text.indexOf("<DATA_END>", lastStart)
        if (lastEnd == -1) return null
        
        return text.substring(lastStart + "<DATA_START>".length, lastEnd).trim()
    }
    
    private fun returnResult(data: ProductData) {
        val resultIntent = Intent().apply {
            putExtra("jan", data.jan)
            putExtra("maker", data.maker)
            putExtra("name", data.name)
            putExtra("category", data.category)
            putExtra("min_price", data.min_price ?: 0)
            putExtra("max_price", data.max_price ?: 0)
            
            // cmをmmに変換
            putExtra("width_mm", data.width_cm?.times(10)?.toInt() ?: 0)
            putExtra("height_mm", data.height_cm?.times(10)?.toInt() ?: 0)
            putExtra("depth_mm", data.depth_cm?.times(10)?.toInt() ?: 0)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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
    
    companion object {
        private const val TAG = "PerplexityWebView"
    }
}
