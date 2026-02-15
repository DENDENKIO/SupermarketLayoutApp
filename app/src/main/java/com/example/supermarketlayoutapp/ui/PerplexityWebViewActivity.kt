package com.example.supermarketlayoutapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Page finished: $url")
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        Log.d("WebView", "${it.message()}")
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
    
    private fun injectPrompt() {
        val prompt = buildPrompt(janCode)
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        
        val js = """
            (function() {
                try {
                    var textarea = document.querySelector('textarea');
                    if (!textarea) {
                        textarea = document.querySelector('[contenteditable="true"]');
                    }
                    
                    if (textarea) {
                        if (textarea.contentEditable === 'true') {
                            textarea.innerText = `$escapedPrompt`;
                        } else {
                            textarea.value = `$escapedPrompt`;
                        }
                        textarea.dispatchEvent(new Event('input', { bubbles: true }));
                        textarea.focus();
                        return 'success';
                    } else {
                        return 'textarea not found';
                    }
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })();
        """.trimIndent()
        
        binding.webView.evaluateJavascript(js) { result ->
            Toast.makeText(this, "プロンプトを貼り付けました。手動で送信してください。", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun extractData() {
        binding.webView.evaluateJavascript(
            "(function() { return document.body.innerText; })();"
        ) { result ->
            result?.let {
                val unescaped = it
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
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
                Log.d("WebView", "Extracted JSON: $jsonString")
                val productData = json.decodeFromString<ProductData>(jsonString)
                returnResult(productData)
            } else {
                Toast.makeText(this, "JSONが見つかりませんでした。AIの応答を確認してください。", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PerplexityWebView", "JSON parse error", e)
            Toast.makeText(this, "データ解析エラー: ${e.message}", Toast.LENGTH_LONG).show()
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
            putExtra("width_mm", data.width_cm?.times(10)?.toInt() ?: 0)
            putExtra("height_mm", data.height_cm?.times(10)?.toInt() ?: 0)
            putExtra("depth_mm", data.depth_cm?.times(10)?.toInt() ?: 0)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
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
