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

/**
 * Perplexity.ai連携WebView Activity
 * 
 * AIへのプロンプト自動注入、応答監視、JSONデータ抽出を実装。
 * LanguagePracticeAPPの実装パターンを参考に最適化。
 * 
 * @see docs/AI_AUTO_INJECTION_SPEC.md 詳細仕様書
 */
class PerplexityWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerplexityWebviewBinding
    private lateinit var janCode: String
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = Handler(Looper.getMainLooper())
    
    // 注入状態管理
    private var promptInjected = false
    private var injectionAttempts = 0
    private val MAX_INJECTION_ATTEMPTS = 5
    
    // 監視状態管理
    private var monitoringAttempts = 0
    private val MAX_MONITORING_ATTEMPTS = 30  // 60秒間
    private var monitoringRunnable: Runnable? = null

    companion object {
        private const val TAG = "PerplexityWebView"
        private const val INITIAL_INJECTION_DELAY = 4000L
        private const val RETRY_INJECTION_DELAY = 2000L
        private const val SEND_BUTTON_DELAY = 500L  // Perplexity専用は500ms
        private const val MONITORING_START_DELAY = 5000L
        private const val MONITORING_INTERVAL = 2000L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerplexityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        janCode = intent.getStringExtra("JAN_CODE") ?: ""
        Log.d(TAG, "Activity起動: JAN=$janCode")

        setupWebView()
        setupButtons()
        loadPerplexity()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 監視ループのクリーンアップ
        monitoringRunnable?.let { handler.removeCallbacks(it) }
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
                databaseEnabled = true  // 互換性のため
                loadWithOverviewMode = true
                useWideViewPort = true
                
                // モバイルChromeとして認識させる
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "ページ読み込み完了: $url")
                    
                    if (url?.contains("perplexity.ai") == true && !promptInjected) {
                        Log.d(TAG, "${INITIAL_INJECTION_DELAY}ms後に注入スケジュール")
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
        if (promptInjected || injectionAttempts >= MAX_INJECTION_ATTEMPTS) {
            if (!promptInjected) {
                Log.w(TAG, "${MAX_INJECTION_ATTEMPTS}回の注入試行がすべて失敗しました")
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
            if (!promptInjected) {
                Log.d(TAG, "前回の注入が失敗。${RETRY_INJECTION_DELAY}ms後に再試行...")
                attemptInjectionWithRetry()
            }
        }, RETRY_INJECTION_DELAY)
    }

    /**
     * プロンプトをWebViewに注入
     * LanguagePracticeAPPの実装パターンを参考に、Perplexity.ai専用に最適化
     */
    private fun injectPrompt() {
        val prompt = buildPrompt(janCode)
        val safePrompt = jsEscape(prompt)
        
        Log.d(TAG, "注入予定プロンプト (最初500文字):")
        Log.d(TAG, prompt.take(500))
        
        // Perplexity.ai専用の最適化注入スクリプト
        val script = buildPerplexityInjectionScript(safePrompt)

        binding.webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "注入結果: $result")
            
            when {
                result?.contains("INPUT_NOT_FOUND") == true -> {
                    Log.w(TAG, "入力欄が見つかりません。再試行します...")
                }
                result?.contains("INPUT_SET") == true || result?.contains("SENT") == true -> {
                    promptInjected = true
                    Log.d(TAG, "★ プロンプト注入成功! ★")
                    Toast.makeText(this, "プロンプトを注入しました", Toast.LENGTH_SHORT).show()
                    startAutoMonitoring()
                }
                result?.contains("PROCESSING") == true -> {
                    Log.d(TAG, "注入処理中... 送信ボタンは後で押されます")
                    // PROCESSINGの場合も2秒後に成功とみなす
                    handler.postDelayed({
                        if (!promptInjected) {
                            promptInjected = true
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
    var selectors = [
        // Lexicalエディタ専用 (最優先)
        '#ask-input[contenteditable="true"][data-lexical-editor="true"]',
        
        // ID + contenteditable 汎用
        '#ask-input[contenteditable="true"]',
        
        // role属性による検知
        'div[contenteditable="true"][role="textbox"]',
        
        // フォールバック
        'textarea[placeholder*="Ask"]',
        'textarea[placeholder*="質問"]',
        'div[contenteditable="true"]',
        'textarea',
        'input[type="text"]'
    ];
    
    var input = null;
    for (var i = 0; i < selectors.length; i++) {
        input = document.querySelector(selectors[i]);
        if (input) {
            console.log('[Injection] 入力欄検出成功: ' + selectors[i]);
            break;
        }
    }
    
    if (!input) {
        console.error('[Injection] INPUT_NOT_FOUND - 入力欄が見つかりません');
        return 'INPUT_NOT_FOUND';
    }
    
    // フォーカスを当てる
    input.focus();
    console.log('[Injection] フォーカス完了');
    
    // contentEditable要素の場合、Selection APIで既存テキストをクリア
    if (input.contentEditable === 'true') {
        try {
            var sel = window.getSelection();
            var range = document.createRange();
            range.selectNodeContents(input);
            sel.removeAllRanges();
            sel.addRange(range);
            document.execCommand('delete');
            console.log('[Injection] Selection APIで既存テキストをクリア');
        } catch(e) {
            console.warn('[Injection] Selection API失敗:', e);
        }
        
        // execCommand('insertText')でテキストを挿入 (Lexicalエディタ対応)
        var ok = false;
        try {
            ok = document.execCommand('insertText', false, prompt);
            console.log('[Injection] execCommand(insertText): ' + ok);
        } catch(e) {
            console.warn('[Injection] execCommand失敗:', e);
            ok = false;
        }
        
        // execCommand失敗時のフォールバック
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
    } 
    // textarea/inputの場合
    else if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
        input.value = prompt;
        console.log('[Injection] valueプロパティで設定');
    }
    
    // イベントディスパッチ (複数種類でReact/Vue対応)
    try {
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
        input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
        input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
        console.log('[Injection] イベントディスパッチ完了');
    } catch(e) {
        console.error('[Injection] イベントディスパッチエラー:', e);
    }
    
    // 送信ボタンの自動クリック (${SEND_BUTTON_DELAY}ms後)
    setTimeout(function() {
        var btnSelectors = [
            'button[aria-label="送信"]',           // 日本語完全一致
            'button[aria-label*="Send"]',          // 英語部分一致
            'button[type="submit"]',               // 標準submitボタン
            'button[aria-label*="Submit"]',        // Submitラベル
            'button svg[data-icon="arrow-up"]'    // 矢印アイコン
        ];
        
        var sendBtn = null;
        for (var i = 0; i < btnSelectors.length; i++) {
            sendBtn = document.querySelector(btnSelectors[i]);
            if (sendBtn && !sendBtn.disabled) {
                console.log('[Injection] 送信ボタン検出: ' + btnSelectors[i]);
                sendBtn.click();
                console.log('[Injection] ★ SENT - 送信完了! ★');
                return 'SENT';
            }
        }
        
        // 送信ボタンが見つからない場合はEnterキーを送信
        console.warn('[Injection] 送信ボタン未検出。Enterキーを試行...');
        input.dispatchEvent(new KeyboardEvent('keydown', { 
            bubbles: true, 
            key: 'Enter', 
            code: 'Enter', 
            keyCode: 13 
        }));
        return 'INPUT_SET';
    }, ${SEND_BUTTON_DELAY});
    
    return 'PROCESSING';
})();
        """.trimIndent()
    }

    /**
     * 自動監視ループを開始
     */
    private fun startAutoMonitoring() {
        Log.d(TAG, "========== 自動監視ループ開始 (${MAX_MONITORING_ATTEMPTS}回/${MONITORING_INTERVAL}ms) ==========")
        
        monitoringAttempts = 0
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (monitoringAttempts >= MAX_MONITORING_ATTEMPTS) {
                    Log.w(TAG, "${MAX_MONITORING_ATTEMPTS}回の監視後、タイムアウトしました")
                    Toast.makeText(
                        this@PerplexityWebViewActivity,
                        "タイムアウトしました。手動で「データ抽出」ボタンを押してください。",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                
                monitoringAttempts++
                Log.d(TAG, "監視中... #$monitoringAttempts/$MAX_MONITORING_ATTEMPTS")
                
                binding.webView.evaluateJavascript(
                    "(function() { return document.body.innerText; })();"
                ) { result ->
                    if (result != null && 
                        result.contains("<DATA_START>") && 
                        result.contains("<DATA_END>")) {
                        Log.d(TAG, "★★★ マーカー検出! データ抽出を開始します ★★★")
                        extractData()
                    } else {
                        Log.d(TAG, "マーカー未検出。${MONITORING_INTERVAL}ms後に再チェック...")
                        handler.postDelayed(this, MONITORING_INTERVAL)
                    }
                }
            }
        }
        
        // 初回はMONITORING_START_DELAY後に開始
        handler.postDelayed(monitoringRunnable!!, MONITORING_START_DELAY)
    }

    /**
     * データ抽出処理
     */
    private fun extractData() {
        Log.d(TAG, "========== データ抽出開始 ==========")
        
        binding.webView.evaluateJavascript(
            "(function() { return document.body.innerText; })();"
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
        """.trimIndent()
    }

    /**
     * データをパースして結果を返却
     */
    private fun parseAndReturnData(htmlContent: String) {
        try {
            val jsonString = extractJsonFromAiText(htmlContent)
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
        
        // 最後の<DATA_START>を探す (AIが複数回出力した場合の対策)
        val lastStart = text.lastIndexOf(startMarker)
        if (lastStart == -1) {
            Log.w(TAG, "<DATA_START>マーカーが見つかりません")
            return null
        }
        
        // その後の<DATA_END>を探す
        val lastEnd = text.indexOf(endMarker, lastStart)
        if (lastEnd == -1) {
            Log.w(TAG, "<DATA_END>マーカーが見つかりません")
            return null
        }
        
        Log.d(TAG, "<DATA_START> 位置: $lastStart, <DATA_END> 位置: $lastEnd")
        
        // マーカー間のテキストを抽出
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
            // cm → mm変換 (×10)
            putExtra("width_mm", data.width_cm?.times(10)?.toInt() ?: 0)
            putExtra("height_mm", data.height_cm?.times(10)?.toInt() ?: 0)
            putExtra("depth_mm", data.depth_cm?.times(10)?.toInt() ?: 0)
        }
        
        Log.d(TAG, "返却データ: JAN=${data.jan}, 名称=${data.name}")
        setResult(RESULT_OK, resultIntent)
        finish()
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
        
        // \\uXXXX → Unicode文字
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
