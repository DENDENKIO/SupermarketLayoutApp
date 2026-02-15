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
 * LanguagePracticeAPPの実装パターンを完全移植。
 * 
 * 主要機能:
 * - JavaScript Injection: Perplexity.ai専用最適化セレクタ
 * - DONE_SENTINEL方式: 生成完了の確実な検出
 * - 安定化判定: テキスト長7回連続不変チェック
 * - 状態管理: InjectionState enumによる厳密な制御
 * 
 * @see docs/AI_AUTO_INJECTION_SPEC.md 詳細仕様書
 */
class PerplexityWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerplexityWebviewBinding
    private lateinit var janCode: String
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = Handler(Looper.getMainLooper())
    
    // 注入状態管理
    private enum class InjectionState {
        NOT_STARTED,   // 未実行
        INJECTING,     // 注入実行中
        COMPLETED,     // 注入完了
        FAILED         // 入力欄検知失敗
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
    
    // DONE_SENTINEL関連
    private val DONE_SENTINEL = "⟦LP_DONE_9F3A2C⟧"
    private var promptSentinelCount = 0

    companion object {
        private const val TAG = "PerplexityWebView"
        private const val INITIAL_INJECTION_DELAY = 3000L
        private const val RETRY_INJECTION_DELAY = 2000L
        private const val SEND_BUTTON_DELAY = 500L  // Perplexity専用は500ms
        private const val MONITORING_INTERVAL = 1500L  // 1.5秒間隔
        private const val REQUIRED_STABLE_COUNT = 7  // 7回連続不変で完了判定
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerplexityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        janCode = intent.getStringExtra("JAN_CODE") ?: ""
        
        // promptSentinelCountを事前計算
        val prompt = buildPrompt(janCode)
        promptSentinelCount = countSentinel(prompt, DONE_SENTINEL)
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "Activity起動: JAN=$janCode")
        Log.d(TAG, "promptSentinelCount=$promptSentinelCount")
        Log.d(TAG, "========================================")
        Log.d(TAG, "注入予定プロンプト(全文):")
        Log.d(TAG, prompt)
        Log.d(TAG, "========================================")

        setupWebView()
        setupButtons()
        loadPerplexity()
    }

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        isMonitoring = false
    }

    /**
     * WebViewの初期設定
     */
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                
                // モバイルChromeとして認識させる (UserAgent最適化)
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
                        
                        // 初期ページ長を記録
                        view?.evaluateJavascript("document.body.innerText.length") { lengthStr ->
                            initialPageTextLength = lengthStr?.toIntOrNull() ?: 0
                            Log.d(TAG, "初期ページテキスト長: $initialPageTextLength")
                        }
                        
                        schedulePromptInjection()
                    }
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "WebViewエラー: ${error?.description}")
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
                        Log.d("$TAG-Console", "[$level] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
        }
    }

    /**
     * ボタンの設定
     */
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

    /**
     * Perplexity.aiを読み込む
     */
    private fun loadPerplexity() {
        Log.d(TAG, "Perplexity.aiを読み込み中...")
        binding.webView.loadUrl("https://www.perplexity.ai/")
    }

    /**
     * プロンプト注入をスケジュール
     */
    private fun schedulePromptInjection() {
        handler.postDelayed({ 
            attemptInjectionWithRetry() 
        }, INITIAL_INJECTION_DELAY)
    }

    /**
     * リトライ付き注入試行
     */
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
        
        // 失敗時はRETRY_INJECTION_DELAY後に再試行
        handler.postDelayed({ 
            if (injectionState != InjectionState.COMPLETED) {
                Log.d(TAG, "前回の注入が失敗。${RETRY_INJECTION_DELAY}ms後に再試行...")
                attemptInjectionWithRetry()
            }
        }, RETRY_INJECTION_DELAY)
    }

    /**
     * プロンプトをWebViewに注入
     * LanguagePracticeAPPの実装パターンを完全移植
     */
    private fun injectPrompt() {
        if (injectionState == InjectionState.INJECTING) {
            Log.d(TAG, "注入処理が既に実行中です。スキップします。")
            return
        }
        
        injectionState = InjectionState.INJECTING
        
        val prompt = buildPrompt(janCode)
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

    /**
     * Perplexity.ai専用の注入スクリプトを生成
     * Lexicalエディタ対応、Selection API、execCommandを使用
     */
    private fun buildPerplexityInjectionScript(safePrompt: String): String {
        return """
(function() {
  console.log('[Injection] ★★★ Perplexity.aiプロンプト注入開始 ★★★');
  var prompt = "$safePrompt";
  
  // Perplexity.ai専用セレクタ (優先順位順)
  var input = 
    document.querySelector('#ask-input[contenteditable="true"][data-lexical-editor="true"]') ||
    document.querySelector('#ask-input[contenteditable="true"]') ||
    document.querySelector('div[contenteditable="true"][role="textbox"]') ||
    document.querySelector('textarea');
  
  if (!input) {
    console.error('[Injection] INPUT_NOT_FOUND');
    return 'INPUT_NOT_FOUND';
  }
  
  console.log('[Injection] 入力欄検出成功: ' + input.tagName);
  input.focus();
  
  // Selection APIで既存テキストクリア
  try {
    var sel = window.getSelection();
    var range = document.createRange();
    range.selectNodeContents(input);
    sel.removeAllRanges();
    sel.addRange(range);
    document.execCommand('delete');
    console.log('[Injection] Selection APIでクリア完了');
  } catch(e) {
    console.warn('[Injection] Selection API失敗:', e);
  }
  
  // execCommand('insertText')でテキスト挿入
  var ok = false;
  try {
    ok = document.execCommand('insertText', false, prompt);
    console.log('[Injection] execCommand(insertText): ' + ok);
  } catch(e) {
    console.warn('[Injection] execCommand失敗:', e);
    ok = false;
  }
  
  // フォールバック
  if (!ok) {
    try {
      input.textContent = prompt;
      input.dispatchEvent(new InputEvent('input', { 
        bubbles: true, 
        data: prompt, 
        inputType: 'insertText' 
      }));
      console.log('[Injection] フォールバック: textContent + InputEvent');
    } catch(e) {
      console.error('[Injection] フォールバック失敗:', e);
    }
  }
  
  // 送信ボタンの自動クリック (${SEND_BUTTON_DELAY}ms後)
  setTimeout(function() {
    var sendBtn = 
      document.querySelector('button[aria-label="送信"]') ||
      document.querySelector('button[aria-label*="Send"]') ||
      document.querySelector('button[type="submit"]');
    
    if (sendBtn) {
      sendBtn.click();
      console.log('[Injection] ★ SENT - 送信完了! ★');
      return 'SENT';
    } else {
      console.warn('[Injection] 送信ボタン未検出。Enterキーを試行');
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

    /**
     * 自動監視ループを開始
     * 安定化判定アルゴリズム: テキスト長が7回連続不変で完了
     */
    private fun startAutoMonitoring() {
        Log.d(TAG, "========== 監視ループ開始 ==========")
        
        isMonitoring = true
        responseStarted = false
        lastTextLength = 0
        stableCount = 0
        
        // 2秒待機してから監視開始
        handler.postDelayed({
            monitoringLoop()
        }, 2000)
    }

    /**
     * 監視ループ本体
     */
    private fun monitoringLoop() {
        if (!isMonitoring) return
        
        binding.webView.evaluateJavascript("document.body.innerText") { rawText ->
            val currentText = decodeJsString(rawText)
            val currentLength = currentText.length
            val totalSentinelCount = countSentinel(currentText, DONE_SENTINEL)
            val requiredCount = promptSentinelCount + 1
            
            // [A] AI応答開始検出
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
            
            Log.d(TAG, "監視中: Sentinel=$totalSentinelCount/$requiredCount, " +
                       "長さ=$currentLength, 安定=$stableCount/$REQUIRED_STABLE_COUNT")
            
            // [B] DONE_SENTINEL数チェック
            if (totalSentinelCount < requiredCount) {
                stableCount = 0
                lastTextLength = currentLength
                handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
                return@evaluateJavascript
            }
            
            // [C] テキスト長安定化判定
            if (currentLength != lastTextLength) {
                stableCount = 0
                lastTextLength = currentLength
            } else {
                stableCount++
            }
            
            // [D] 生成完了判定
            if (stableCount >= REQUIRED_STABLE_COUNT) {
                isMonitoring = false
                Log.d(TAG, "★★★ 生成完了検出！結果を取得中... ★★★")
                
                handler.postDelayed({
                    binding.webView.evaluateJavascript("document.body.innerText") { finalRaw ->
                        val fullText = decodeJsString(finalRaw)
                        val resultText = extractAiResponse(
                            fullText, 
                            promptSentinelCount, 
                            DONE_SENTINEL
                        )
                        Log.d(TAG, "抽出完了。パース処理を開始...")
                        parseAndReturnData(resultText)
                    }
                }, 1500)
            } else {
                handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
            }
        }
    }

    /**
     * ページ全体からAI応答部分を抽出
     * LanguagePracticeAPPのextractAiResponse()を移植
     */
    private fun extractAiResponse(
        fullText: String, 
        promptSentinelCount: Int, 
        sentinel: String
    ): String {
        Log.d(TAG, "========== extractAiResponse 開始 ==========")
        Log.d(TAG, "fullText.length=${fullText.length}")
        Log.d(TAG, "promptSentinelCount=$promptSentinelCount")
        
        // ① プロンプト部分をスキップ
        var pos = 0
        for (i in 0 until promptSentinelCount) {
            pos = fullText.indexOf(sentinel, pos)
            if (pos == -1) {
                Log.w(TAG, "プロンプト内のSentinelが見つかりません: i=$i")
                return fullText
            }
            pos += sentinel.length
        }
        
        val afterPrompt = if (pos > 0) fullText.substring(pos) else fullText
        Log.d(TAG, "プロンプトスキップ後の長さ: ${afterPrompt.length}")
        
        // ② AI応答の終了位置を検索
        val aiSentinelPos = afterPrompt.indexOf(sentinel)
        val extractEndPos = if (aiSentinelPos != -1) {
            Log.d(TAG, "AI Sentinel検出: pos=$aiSentinelPos")
            aiSentinelPos
        } else {
            val leftBracketPos = afterPrompt.indexOf("⟦")
            if (leftBracketPos != -1) {
                Log.w(TAG, "完全なDONE_SENTINELが見つからないが、左カッコ⟦を検出: pos=$leftBracketPos")
                leftBracketPos
            } else {
                Log.w(TAG, "AI Sentinelも左カッコも見つかりません。全文を返します")
                afterPrompt.length
            }
        }
        
        // ③ 開始マーカーを検索 (<DATA_START>)
        val startMarker = "<DATA_START>"
        val markerPos = afterPrompt.indexOf(startMarker)
        
        val extracted = if (markerPos != -1 && markerPos < extractEndPos) {
            Log.d(TAG, "開始マーカー<DATA_START>を検出: pos=$markerPos")
            afterPrompt.substring(markerPos, extractEndPos)
        } else {
            Log.w(TAG, "開始マーカー未検出。終了位置から最大10000文字を抽出")
            val extractStart = maxOf(0, extractEndPos - 10000)
            afterPrompt.substring(extractStart, extractEndPos)
        }
        
        // ④ クリーニング
        val cleaned = extracted
            .replace(sentinel, "")
            .replace("⟦LP_DONE_9F3A2C⟧", "")
            .replace("⟦", "")
            .replace("⟧", "")
            .replace("LP_DONE_9F3A2C", "")
            .trim()
        
        Log.d(TAG, "抽出完了: length=${cleaned.length}")
        Log.d(TAG, "========== extractAiResponse 終了 ==========")
        
        return cleaned
    }

    /**
     * データ抽出処理 (手動ボタン用)
     */
    private fun extractData() {
        Log.d(TAG, "========== データ抽出開始 (手動) ==========")
        
        binding.webView.evaluateJavascript(
            "document.body.innerText"
        ) { result ->
            result?.let {
                Log.d(TAG, "evaluateJavascript後の生データ長: ${it.length}")
                val decoded = decodeJsString(it)
                Log.d(TAG, "デコード後のデータ長: ${decoded.length}")
                
                val extracted = extractAiResponse(decoded, promptSentinelCount, DONE_SENTINEL)
                parseAndReturnData(extracted)
            } ?: run {
                Log.e(TAG, "evaluateJavascriptがnullを返しました")
            }
        }
    }

    /**
     * プロンプトを構築
     */
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
${DONE_SENTINEL}
        """.trimIndent()
    }

    /**
     * データをパースして結果を返却
     */
    private fun parseAndReturnData(aiResponse: String) {
        try {
            val jsonString = extractJsonFromAiText(aiResponse)
            if (jsonString != null) {
                Log.d(TAG, "抽出されたJSON:")
                Log.d(TAG, jsonString)
                
                val productData = json.decodeFromString<ProductData>(jsonString)
                Log.d(TAG, "JSONパース成功: $productData")
                returnResult(productData)
            } else {
                Log.e(TAG, "JSON抽出に失敗しました")
                Toast.makeText(
                    this,
                    "JSONデータが見つかりませんでした。手動で再試行してください。",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSONパースエラー", e)
            Toast.makeText(
                this,
                "JSONパースエラー: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * AIテキストからJSONを抽出
     */
    private fun extractJsonFromAiText(text: String): String? {
        val startMarker = "<DATA_START>"
        val endMarker = "<DATA_END>"
        
        val lastStart = text.lastIndexOf(startMarker)
        if (lastStart == -1) {
            Log.w(TAG, "<DATA_START>マーカーが見つかりません")
            return null
        }
        
        val lastEnd = text.indexOf(endMarker, lastStart)
        if (lastEnd == -1) {
            Log.w(TAG, "<DATA_END>マーカーが見つかりません")
            return null
        }
        
        Log.d(TAG, "<DATA_START> 位置: $lastStart, <DATA_END> 位置: $lastEnd")
        
        val extracted = text.substring(lastStart + startMarker.length, lastEnd).trim()
        Log.d(TAG, "抽出されたテキスト長: ${extracted.length}")
        
        return extracted
    }

    /**
     * 結果をIntentで返してActivityを終了
     */
    private fun returnResult(data: ProductData) {
        Log.d(TAG, "========== 結果返却開始 ==========")
        
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
        
        Log.d(TAG, "返却データ: JAN=${data.jan}, 名称=${data.name}")
        Log.d(TAG, "onResultReceived呼び出し完了")
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * DONE_SENTINELの出現回数をカウント
     */
    private fun countSentinel(text: String, sentinel: String): Int {
        if (text.isEmpty() || sentinel.isEmpty()) return 0
        var count = 0
        var pos = 0
        while (true) {
            pos = text.indexOf(sentinel, pos)
            if (pos == -1) break
            count++
            pos += sentinel.length
        }
        return count
    }

    /**
     * JavaScript文字列として安全にエスケープ
     */
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

    /**
     * evaluateJavascript()から返される文字列をデコード
     */
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

    /**
     * 商品データクラス (JSONパース用)
     */
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
