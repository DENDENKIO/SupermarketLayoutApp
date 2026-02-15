package com.example.supermarketlayoutapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivityPerplexityWebviewBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

/**
 * Perplexity.ai連携WebView Activity
 * 
 * AIへのプロンプト自動注入、応答監視、JSONデータ抽出を実装。
 * **複数JANコードを一括処理**し、速度を大幅に向上。
 * メーカー公式サイトまたはAmazon.co.jpから商品情報を取得。
 * 
 * @see docs/AI_AUTO_INJECTION_SPEC.md 詳細仕様書
 */
class PerplexityWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerplexityWebviewBinding
    private lateinit var janCodeList: List<String>
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = Handler(Looper.getMainLooper())
    
    // 注入状態管理
    private enum class InjectionState {
        NOT_STARTED,
        INJECTING,
        COMPLETED,
        FAILED
    }
    
    private var injectionState = InjectionState.NOT_STARTED
    private var injectionAttempts = 0
    private val MAX_INJECTION_ATTEMPTS = 5
    
    // 監視状態管理
    private var isMonitoring = false
    private var responseStarted = false
    private var lastTextLength = 0
    private var stableCount = 0
    private var initialPageTextLength = 0
    private var monitoringRunnable: Runnable? = null
    private var monitoringStartTime = 0L

    companion object {
        private const val TAG = "PerplexityWebView"
        private const val INITIAL_INJECTION_DELAY = 3000L
        private const val RETRY_INJECTION_DELAY = 2000L
        private const val SEND_BUTTON_DELAY = 500L
        private const val MONITORING_INTERVAL = 1500L
        private const val REQUIRED_STABLE_COUNT = 5  // 7.5秒間安定
        private const val MONITORING_TIMEOUT = 60000L  // 60秒でタイムアウト
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerplexityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 複数JANコードを受け取る
        janCodeList = intent.getStringArrayExtra("JAN_CODE_LIST")?.toList() 
            ?: listOf(intent.getStringExtra("JAN_CODE") ?: "")
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "Activity起動: JANリスト=${janCodeList.size}件")
        Log.d(TAG, "JAN: $janCodeList")
        Log.d(TAG, "========================================")

        setupWebView()
        setupButtons()
        loadPerplexity()
    }

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        isMonitoring = false
        Log.d(TAG, "onDestroy()呼び出されました")
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "ページ読み込み完了: $url, injectionState=$injectionState")
                    
                    if (url?.contains("perplexity.ai") == true && 
                        injectionState == InjectionState.NOT_STARTED) {
                        Log.d(TAG, "${INITIAL_INJECTION_DELAY}ms後に注入スケジュール")
                        
                        view?.evaluateJavascript("document.body.innerText.length") { lengthStr ->
                            initialPageTextLength = lengthStr?.toIntOrNull() ?: 0
                            Log.d(TAG, "初期ページテキスト長: $initialPageTextLength")
                        }
                        
                        schedulePromptInjection()
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        val level = when (it.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                            ConsoleMessage.MessageLevel.WARNING -> "WARN"
                            else -> "INFO"
                        }
                        Log.d("$TAG-Console", "[$level] ${it.message()}")
                    }
                    return true
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnInjectPrompt.setOnClickListener {
            Log.d(TAG, "手動注入ボタンが押されました")
            injectionState = InjectionState.NOT_STARTED
            injectPrompt()
        }

        binding.btnExtractData.setOnClickListener {
            Log.d(TAG, "手動抽出ボタンが押されました")
            extractData()
        }
    }

    private fun loadPerplexity() {
        Log.d(TAG, "Perplexity.aiを読み込み中...")
        binding.webView.loadUrl("https://www.perplexity.ai/")
    }

    private fun schedulePromptInjection() {
        handler.postDelayed({ 
            attemptInjectionWithRetry() 
        }, INITIAL_INJECTION_DELAY)
    }

    private fun attemptInjectionWithRetry() {
        if (injectionState == InjectionState.COMPLETED || 
            injectionAttempts >= MAX_INJECTION_ATTEMPTS) {
            if (injectionState != InjectionState.COMPLETED) {
                Log.w(TAG, "${MAX_INJECTION_ATTEMPTS}回の注入試行がすべて失敗しました")
                injectionState = InjectionState.FAILED
                Toast.makeText(
                    this, 
                    "自動注入に失敗しました。手動ボタンをお試しください。", 
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        
        injectionAttempts++
        Log.d(TAG, "★★★ 注入試行 #$injectionAttempts/$MAX_INJECTION_ATTEMPTS ★★★")
        injectPrompt()
        
        handler.postDelayed({ 
            if (injectionState != InjectionState.COMPLETED) {
                Log.d(TAG, "前回の注入が失敗。${RETRY_INJECTION_DELAY}ms後に再試行...")
                attemptInjectionWithRetry()
            }
        }, RETRY_INJECTION_DELAY)
    }

    private fun injectPrompt() {
        if (injectionState == InjectionState.INJECTING) {
            Log.d(TAG, "注入処理が既に実行中です。スキップします。")
            return
        }
        
        injectionState = InjectionState.INJECTING
        
        val prompt = buildPrompt(janCodeList)
        val safePrompt = jsEscape(prompt)
        
        Log.d(TAG, "★★★ プロンプト注入開始 ★★★")
        Log.d(TAG, "実際に送信するプロンプト(最初500文字):")
        Log.d(TAG, prompt.take(500))
        
        val script = buildPerplexityInjectionScript(safePrompt)

        binding.webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "注入結果: $result")
            
            when {
                result?.contains("INPUT_NOT_FOUND") == true -> {
                    Log.w(TAG, "入力欄が見つかりません。")
                    injectionState = InjectionState.FAILED
                }
                result?.contains("INPUT_SET") == true || result?.contains("SENT") == true -> {
                    injectionState = InjectionState.COMPLETED
                    Log.d(TAG, "★ プロンプト注入成功! ★")
                    Toast.makeText(this, "プロンプトを注入しました", Toast.LENGTH_SHORT).show()
                    startAutoMonitoring()
                }
                result?.contains("PROCESSING") == true -> {
                    Log.d(TAG, "注入処理中... 送信ボタンは後で押されます")
                    handler.postDelayed({
                        if (injectionState == InjectionState.INJECTING) {
                            injectionState = InjectionState.COMPLETED
                            Log.d(TAG, "★ プロンプト設定完了 ★")
                            Toast.makeText(this, "プロンプトを設定しました", Toast.LENGTH_SHORT).show()
                            startAutoMonitoring()
                        }
                    }, 2000)
                }
            }
        }
    }

    private fun buildPerplexityInjectionScript(safePrompt: String): String {
        return """
(function() {
  console.log('[Injection] ★★★ Perplexity.aiプロンプト注入開始 ★★★');
  var prompt = "$safePrompt";
  
  var input = 
    document.querySelector('#ask-input[contenteditable="true"][data-lexical-editor="true"]') ||
    document.querySelector('#ask-input[contenteditable="true"]') ||
    document.querySelector('div[contenteditable="true"][role="textbox"]') ||
    document.querySelector('textarea');
  
  if (!input) {
    console.error('[Injection] INPUT_NOT_FOUND');
    return 'INPUT_NOT_FOUND';
  }
  
  input.focus();
  
  try {
    var sel = window.getSelection();
    var range = document.createRange();
    range.selectNodeContents(input);
    sel.removeAllRanges();
    sel.addRange(range);
    document.execCommand('delete');
  } catch(e) {}
  
  var ok = false;
  try {
    ok = document.execCommand('insertText', false, prompt);
  } catch(e) {
    ok = false;
  }
  
  if (!ok) {
    try {
      input.textContent = prompt;
      input.dispatchEvent(new InputEvent('input', { 
        bubbles: true, 
        data: prompt, 
        inputType: 'insertText' 
      }));
    } catch(e) {}
  }
  
  setTimeout(function() {
    var sendBtn = 
      document.querySelector('button[aria-label="送信"]') ||
      document.querySelector('button[aria-label*="Send"]') ||
      document.querySelector('button[type="submit"]');
    
    if (sendBtn) {
      sendBtn.click();
      return 'SENT';
    } else {
      input.dispatchEvent(new KeyboardEvent('keydown', { 
        bubbles: true, 
        key: 'Enter', 
        code: 'Enter', 
        keyCode: 13 
      }));
      return 'INPUT_SET';
    }
  }, ${SEND_BUTTON_DELAY});
  
  return 'PROCESSING';
})();
        """.trimIndent()
    }

    private fun startAutoMonitoring() {
        Log.d(TAG, "========== 監視ループ開始 ==========")
        
        isMonitoring = true
        responseStarted = false
        lastTextLength = 0
        stableCount = 0
        monitoringStartTime = System.currentTimeMillis()
        
        handler.postDelayed({
            monitoringLoop()
        }, 2000)
    }

    private fun monitoringLoop() {
        if (!isMonitoring) return
        
        // タイムアウトチェック
        val elapsedTime = System.currentTimeMillis() - monitoringStartTime
        if (elapsedTime > MONITORING_TIMEOUT) {
            Log.w(TAG, "★★★ タイムアウト: ${elapsedTime}ms経過 ★★★")
            isMonitoring = false
            runOnUiThread {
                Toast.makeText(
                    this,
                    "AI応答がタイムアウトしました。手動で抽出してください。",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        
        binding.webView.evaluateJavascript("document.body.innerText") { rawText ->
            val currentText = decodeJsString(rawText)
            val currentLength = currentText.length
            
            // <DATA_END>があれば即座に完了
            val hasDataEnd = currentText.contains("<DATA_END>")
            
            if (!responseStarted) {
                if (currentLength > initialPageTextLength + 100) {
                    responseStarted = true
                    Log.d(TAG, "AI応答検出。生成完了を待機中...")
                } else {
                    Log.d(TAG, "AI応答待機中... (長さ=$currentLength)")
                    handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
                    return@evaluateJavascript
                }
            }
            
            Log.d(TAG, "監視中: <DATA_END>=${if (hasDataEnd) "YES" else "NO"}, " +
                       "長さ=$currentLength, 安定=$stableCount/$REQUIRED_STABLE_COUNT")
            
            // <DATA_END>があれば即座に完了
            if (hasDataEnd) {
                isMonitoring = false
                Log.d(TAG, "★★★ <DATA_END>検出！即座に取得開始 ★★★")
                
                handler.postDelayed({
                    binding.webView.evaluateJavascript("document.body.innerText") { finalRaw ->
                        val fullText = decodeJsString(finalRaw)
                        Log.d(TAG, "抽出完了。パース処理を開始...")
                        parseAndReturnData(fullText)
                    }
                }, 1000)
                return@evaluateJavascript
            }
            
            // テキスト長安定化チェック
            if (currentLength != lastTextLength) {
                stableCount = 0
                lastTextLength = currentLength
            } else {
                stableCount++
            }
            
            if (stableCount >= REQUIRED_STABLE_COUNT) {
                isMonitoring = false
                Log.d(TAG, "★★★ テキスト安定化検出！結果を取得中... ★★★")
                
                handler.postDelayed({
                    binding.webView.evaluateJavascript("document.body.innerText") { finalRaw ->
                        val fullText = decodeJsString(finalRaw)
                        Log.d(TAG, "抽出完了。パース処理を開始...")
                        parseAndReturnData(fullText)
                    }
                }, 1500)
            } else {
                handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
            }
        }
    }

    private fun extractData() {
        Log.d(TAG, "========== データ抽出開始 (手動) ==========")
        
        binding.webView.evaluateJavascript(
            "document.body.innerText"
        ) { result ->
            result?.let {
                Log.d(TAG, "evaluateJavascript後の生データ長: ${it.length}")
                val decoded = decodeJsString(it)
                Log.d(TAG, "デコード後のデータ長: ${decoded.length}")
                
                parseAndReturnData(decoded)
            } ?: run {
                Log.e(TAG, "evaluateJavascriptがnullを返しました")
            }
        }
    }

    /**
     * 複数JANコード用のプロンプトを構築
     * メーカー公式サイトまたはAmazon.co.jpから情報取得するよう指示
     */
    private fun buildPrompt(janList: List<String>): String {
        val janListStr = janList.joinToString(", ")
        
        return """
あなたはスーパーマーケット向け棚割システムのための商品マスターJSONを作成します。
必ず以下の制約を守ってください。

# 情報源の指定
- **必ずメーカー公式サイトまたはAmazon.co.jpから商品情報を取得してください**
- メーカー公式サイトが見つかる場合は、メーカーサイトを優先してください
- Amazon.co.jpで商品ページが見つかる場合は、そこから正確なサイズ・価格情報を取得してください
- 推測や不確かな情報は避け、信頼できるソースから取得した情報のみを使用してください

# 出力形式
- 回答は必ず次の形式だけを出力してください。
- 「<DATA_START>」の行から「<DATA_END>」の行までの間に、**JSON配列**を出力してください。
- 説明・補足・会話文など、JSON以外のテキストは絶対に出力しないでください。

# JSONスキーマ (配列形式)
<DATA_START>
[
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
]
<DATA_END>

# ルール
- 必ず**JSON配列**で出力し、入力JANコードごとに1つのオブジェクトを作成すること。
- 数値項目は数値型で出力し、単位はすべてcmとする。
- サイズが不明な場合は null を入れる。
- 価格が不明な場合は min_price, max_price に null を入れる。
- 上のスキーマとキー名は絶対に変更しないこと。
- JANコードが見つからない場合でも、そのJANのオブジェクトを配列に含め、nameを"不明"としてください。
- **重要**: メーカー公式サイトまたはAmazon.co.jpのURLを参照し、そこから取得した情報であることを確認してください。

# 入力情報
JANコードリスト: $janListStr

これらの入力情報を使い、メーカー公式サイトまたはAmazon.co.jpから商品情報を検索し、上記と同じ形式でJSON配列を出力してください。
        """.trimIndent()
    }

    /**
     * 複数商品データをパースして結果を返却
     * エラーが発生しても必ずfinish()を呼ぶ
     */
    private fun parseAndReturnData(aiResponse: String) {
        Log.d(TAG, "========== parseAndReturnData 開始 ==========")
        Log.d(TAG, "AI応答テキスト長: ${aiResponse.length}")
        Log.d(TAG, "AI応答の最初500文字:")
        Log.d(TAG, aiResponse.take(500))
        Log.d(TAG, "AI応答の最後500文字:")
        Log.d(TAG, aiResponse.takeLast(500))
        
        try {
            val jsonString = extractJsonFromAiText(aiResponse)
            if (jsonString != null) {
                Log.d(TAG, "抽出されたJSON長: ${jsonString.length}")
                Log.d(TAG, "抽出されたJSONの最初300文字:")
                Log.d(TAG, jsonString.take(300))
                
                try {
                    // JSON配列としてパース
                    val productDataList = json.decodeFromString<List<ProductData>>(jsonString)
                    Log.d(TAG, "JSONパース成功: ${productDataList.size}件")
                    
                    // メインスレッドで結果を返して終了
                    runOnUiThread {
                        returnResults(productDataList)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "JSONパースエラー", e)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "JSONパースエラー: ${e.message}\n\nJSONの最初100文字: ${jsonString.take(100)}",
                            Toast.LENGTH_LONG
                        ).show()
                        // エラーでも終了
                        finishWithError()
                    }
                }
            } else {
                Log.e(TAG, "JSON抽出に失敗しました")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "JSONデータが見つかりませんでした。\n\nAIテキストの最初100文字: ${aiResponse.take(100)}",
                        Toast.LENGTH_LONG
                    ).show()
                    // エラーでも終了
                    finishWithError()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAndReturnDataで予期せぬエラー", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "エラー: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                // エラーでも終了
                finishWithError()
            }
        }
        
        Log.d(TAG, "========== parseAndReturnData 終了 ==========")
    }

    /**
     * AIテキストからJSONを抽出
     * HTMLエスケープされたマーカーにも対応
     */
    private fun extractJsonFromAiText(text: String): String? {
        Log.d(TAG, "========== extractJsonFromAiText 開始 ==========")
        
        // HTMLエスケープを正規化
        val normalizedText = text
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
        
        val startMarker = "<DATA_START>"
        val endMarker = "<DATA_END>"
        
        // マーカーの存在を検索
        val hasStart = normalizedText.contains(startMarker)
        val hasEnd = normalizedText.contains(endMarker)
        
        Log.d(TAG, "<DATA_START>検出: $hasStart")
        Log.d(TAG, "<DATA_END>検出: $hasEnd")
        
        if (!hasStart) {
            Log.w(TAG, "<DATA_START>マーカーが見つかりません")
            return null
        }
        
        val lastStart = normalizedText.lastIndexOf(startMarker)
        
        if (!hasEnd) {
            Log.w(TAG, "<DATA_END>マーカーが見つかりません")
            return null
        }
        
        val lastEnd = normalizedText.indexOf(endMarker, lastStart)
        if (lastEnd == -1) {
            Log.w(TAG, "<DATA_END>マーカーが<DATA_START>の後に見つかりません")
            return null
        }
        
        Log.d(TAG, "<DATA_START> 位置: $lastStart, <DATA_END> 位置: $lastEnd")
        
        val extracted = normalizedText.substring(lastStart + startMarker.length, lastEnd).trim()
        Log.d(TAG, "抽出されたテキスト長: ${extracted.length}")
        
        if (extracted.isEmpty()) {
            Log.w(TAG, "マーカー間が空です")
            return null
        }
        
        Log.d(TAG, "========== extractJsonFromAiText 終了 ==========")
        return extracted
    }

    /**
     * 複数の結果をIntentで返してActivityを終了
     */
    private fun returnResults(dataList: List<ProductData>) {
        Log.d(TAG, "========== ★★★ returnResults 開始 (${dataList.size}件) ★★★ ==========")
        
        try {
            val resultIntent = Intent()
            
            // 配列で返却
            resultIntent.putExtra("product_count", dataList.size)
            
            dataList.forEachIndexed { index, data ->
                val prefix = "product_$index"
                resultIntent.putExtra("${prefix}_jan", data.jan)
                resultIntent.putExtra("${prefix}_maker", data.maker)
                resultIntent.putExtra("${prefix}_name", data.name)
                resultIntent.putExtra("${prefix}_category", data.category)
                resultIntent.putExtra("${prefix}_min_price", data.min_price ?: 0)
                resultIntent.putExtra("${prefix}_max_price", data.max_price ?: 0)
                resultIntent.putExtra("${prefix}_width_mm", data.width_cm?.times(10)?.toInt() ?: 0)
                resultIntent.putExtra("${prefix}_height_mm", data.height_cm?.times(10)?.toInt() ?: 0)
                resultIntent.putExtra("${prefix}_depth_mm", data.depth_cm?.times(10)?.toInt() ?: 0)
                
                Log.d(TAG, "[$index] JAN=${data.jan}, 名称=${data.name}")
            }
            
            Log.d(TAG, "★ setResult(RESULT_OK) 呼び出し")
            setResult(RESULT_OK, resultIntent)
            
            Log.d(TAG, "★ finish() 呼び出し")
            finish()
            
            Log.d(TAG, "========== ★★★ returnResults 終了 ★★★ ==========")
        } catch (e: Exception) {
            Log.e(TAG, "returnResultsでエラー発生", e)
            finishWithError()
        }
    }
    
    /**
     * エラー時の終了処理
     */
    private fun finishWithError() {
        Log.w(TAG, "★★★ finishWithError() 呼び出し ★★★")
        setResult(RESULT_CANCELED)
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
                c == '\t' -> builder.append("\\t")
                c < ' ' -> builder.append(String.format("\\u%04x", c.code))
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun decodeJsString(raw: String): String {
        var text = raw.removeSurrounding("\"")
        text = text.replace("\\n", "\n")
        text = text.replace("\\\"", "\"")
        text = text.replace("\\\\", "\\")
        
        val unicodePattern = Regex("""\\\\u([0-9a-fA-F]{4})""")
        text = unicodePattern.replace(text) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            val codePoint = hexCode.toInt(16)
            codePoint.toChar().toString()
        }
        
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
