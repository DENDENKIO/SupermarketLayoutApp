# AI自動貼り付け送信機能 完全仕様書

## 概要

スーパーマーケット売場レイアウトアプリにおける**AI自動貼り付け送信機能**は、Android WebView内でPerplexity.aiに自動的にプロンプトを入力・送信し、生成された応答を監視・抽出する機構です。

**実装ファイル**: `PerplexityWebViewActivity.kt`  
**パッケージ**: `com.example.supermarketlayoutapp.ui`

---

## システムアーキテクチャ

### コンポーネント構成

```
┌─────────────────────────────────────────┐
│ PerplexityWebViewActivity              │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ WebView Container               │ │
│ │ (Perplexity.ai)                 │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ JavaScript Injection Engine             │
│ - injectPrompt()                        │
│ - buildPrompt()                         │
│ - jsEscape()                            │
├─────────────────────────────────────────┤
│ Retry Mechanism                         │
│ - attemptInjectionWithRetry()           │
│ - Max 5 attempts with 2s interval      │
├─────────────────────────────────────────┤
│ Response Monitoring Loop                │
│ - startAutoMonitoring()                 │
│ - Polling interval: 2 seconds           │
│ - Max 30 attempts (60 seconds total)    │
│ - <DATA_START>/<DATA_END> detection     │
├─────────────────────────────────────────┤
│ Response Extraction Engine              │
│ - extractData()                         │
│ - extractJsonFromAiText()               │
│ - decodeJsString()                      │
├─────────────────────────────────────────┤
│ Data Parsing & Return                   │
│ - parseAndReturnData()                  │
│ - returnResult()                        │
└─────────────────────────────────────────┘
```

---

## データフロー詳細

```
JANコード受信 (Intent)
    ↓
WebView初期化
    ↓
Perplexity.ai URL読み込み
    ↓ (onPageFinished)
4秒待機
    ↓
プロンプト注入試行 (自動リトライ最大5回)
    ├─ 入力欄検知 (8種類のセレクタを順番に試行)
    ├─ テキスト設定 (contentEditable/value)
    ├─ イベントディスパッチ (input/change/keydown/keyup)
    └─ 送信ボタンクリック (1.5秒後、6種類のセレクタを試行)
    ↓
注入成功判定
    ↓
自動監視ループ開始 (2秒間隔、最大30回)
    ├─ document.body.innerText取得
    ├─ <DATA_START> マーカー検索
    ├─ <DATA_END> マーカー検索
    └─ 両方検出 → データ抽出へ
    ↓
JSON抽出 (extractJsonFromAiText)
    ├─ 最後の<DATA_START>位置を特定
    ├─ その後の<DATA_END>位置を特定
    └─ 範囲内のJSONを抽出
    ↓
JSONパース (Kotlinx Serialization)
    ↓
ProductData生成
    ↓
Intent経由で結果を返却
    ↓
Activity終了
```

---

## 1. プロンプト注入処理

### 1.1 注入状態管理

```kotlin
private var promptInjected = false     // 注入完了フラグ
private var injectionAttempts = 0      // 試行回数カウンタ
```

**状態遷移図:**

```
NOT_INJECTED (promptInjected = false, injectionAttempts = 0)
    ↓
INJECTING (promptInjected = false, injectionAttempts = 1..5)
    ↓
SUCCESS (promptInjected = true)
または
FAILED (promptInjected = false, injectionAttempts >= 5)
```

### 1.2 リトライメカニズム

```kotlin
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
```

**リトライ戦略:**

| 試行回数 | タイミング | 説明 |
|---------|-----------|------|
| 1回目 | ページ読み込み後4秒 | 初回試行 |
| 2回目 | 1回目の2秒後 | 自動リトライ |
| 3回目 | 2回目の2秒後 | 自動リトライ |
| 4回目 | 3回目の2秒後 | 自動リトライ |
| 5回目 | 4回目の2秒後 | 最終試行 |
| 失敗 | 5回目完了後 | ユーザーに手動操作を促す |

### 1.3 JavaScriptエスケープ処理

```kotlin
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
```

**エスケープ対応表:**

| 入力文字 | 出力形式 | 説明 |
|---------|---------|------|
| `\` | `\\` | バックスラッシュ |
| `"` | `\"` | ダブルクォート |
| `\n` | `\n` | 改行 |
| `\r` | `\r` | キャリッジリターン |

---

## 2. Perplexity.ai 専用検知実装

### 2.1 入力欄検知セレクタ (優先順位順)

```javascript
var selectors = [
    'textarea[placeholder*="Ask"]',           // ① 英語UI (placeholder="Ask anything...")
    'textarea[placeholder*="質問"]',          // ② 日本語UI
    'div[contenteditable="true"][role="textbox"]',  // ③ contentEditable + role
    'div[contenteditable="true"]',            // ④ contentEditable汎用
    'textarea',                               // ⑤ textarea汎用
    '[data-testid="search-input"]',          // ⑥ data-testid指定
    '[data-testid="ask-input"]',             // ⑦ data-testid指定 (別パターン)
    'input[type="text"]'                      // ⑧ input汎用 (最終手段)
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
```

**検知ロジックの詳細:**

#### ① `textarea[placeholder*="Ask"]`
- **対象**: 英語版Perplexity.aiの標準入力欄
- **DOM構造例**:
  ```html
  <textarea placeholder="Ask anything..." rows="1"></textarea>
  ```

#### ② `textarea[placeholder*="質問"]`
- **対象**: 日本語版Perplexity.aiの標準入力欄
- **DOM構造例**:
  ```html
  <textarea placeholder="質問を入力してください" rows="1"></textarea>
  ```

#### ③ `div[contenteditable="true"][role="textbox"]`
- **対象**: リッチテキスト入力欄（アクセシビリティ対応）
- **特徴**: `role="textbox"`はWAI-ARIA標準属性で削除されにくい

#### ④ `div[contenteditable="true"]`
- **対象**: role属性が削除された場合のフォールバック

#### ⑤ `textarea`
- **対象**: 最も基本的なHTML要素
- **理由**: UI構造が完全に変更された場合の最終手段

#### ⑥⑦ `[data-testid="..."]`
- **対象**: テスト用ID属性（開発者が残している可能性）
- **理由**: テストコードで使われる属性は比較的安定

#### ⑧ `input[type="text"]`
- **対象**: 標準的なテキスト入力欄
- **理由**: 最も汎用的なフォールバック

### 2.2 テキスト入力処理

```javascript
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
```

**入力方法の使い分け:**

| 要素タイプ | プロパティ | 理由 |
|-----------|-----------|------|
| `div[contenteditable="true"]` | `innerText`, `textContent` | リッチテキスト編集には両方設定 |
| `textarea` | `value` | 標準HTML要素の値プロパティ |
| `input[type="text"]` | `value` | 標準HTML要素の値プロパティ |

### 2.3 イベントディスパッチ処理

```javascript
try {
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
    input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
    console.log('[Injection] Events dispatched');
} catch(e) {
    console.error('[Injection] Event dispatch error:', e);
}
```

**イベントの役割:**

| イベント | 役割 | `bubbles: true`の意味 |
|---------|------|----------------------|
| `input` | テキストが入力されたことをReact/Vueに通知 | 親要素へイベント伝播 |
| `change` | 値が変更されたことを通知 | フォームバリデーショントリガー |
| `keydown` | キーボード入力開始を通知 | IME制御やショートカット検知 |
| `keyup` | キーボード入力終了を通知 | 入力完了の検知 |

**なぜ4種類のイベントが必要か:**
- **Reactアプリケーション**: `input`イベントで状態更新
- **ネイティブJavaScript**: `change`イベントでフォーム処理
- **キーボードイベント**: Enterキー送信などのショートカットを検知

### 2.4 送信ボタン検知・実行

```javascript
setTimeout(function() {
    var btnSelectors = [
        'button[aria-label*="送信"]',          // ① 日本語UI
        'button[aria-label*="Send"]',          // ② 英語UI
        'button[aria-label*="Submit"]',        // ③ 英語UI (別表現)
        'button[type="submit"]',               // ④ HTML標準
        'button svg[data-icon="arrow-up"]',    // ⑤ SVGアイコン検知
        'button:has(svg)'                      // ⑥ SVG含むボタン汎用
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
```

**送信ボタン検知セレクタ詳細:**

| 優先度 | セレクタ | 説明 |
|-------|---------|------|
| 1 | `button[aria-label*="送信"]` | 日本語UI完全対応 |
| 2 | `button[aria-label*="Send"]` | 英語UI (部分一致) |
| 3 | `button[aria-label*="Submit"]` | 英語UI (別表現) |
| 4 | `button[type="submit"]` | HTML標準フォーム送信 |
| 5 | `button svg[data-icon="arrow-up"]` | Perplexity特有の矢印アイコン |
| 6 | `button:has(svg)` | SVGを含むボタン汎用 |

**1.5秒遅延の理由:**
1. **Reactの状態更新**: 入力イベント後、Reactがstateを更新するまで待機
2. **再レンダリング**: 入力内容に応じて送信ボタンの有効/無効が切り替わる
3. **バリデーション**: 入力内容の検証処理完了を待つ

---

## 3. 応答監視システム

### 3.1 監視ループ実装

```kotlin
private fun startAutoMonitoring() {
    handler.postDelayed(object : Runnable {
        var attempts = 0
        override fun run() {
            if (attempts > 30) {
                Log.w("WebView", "Auto-monitoring timeout after 30 attempts")
                return
            }
            
            binding.webView.evaluateJavascript(
                "(function() { return document.body.innerText; })();"
            ) { result ->
                if (result != null && 
                    result.contains("<DATA_START>") && 
                    result.contains("<DATA_END>")) {
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
```

**監視ループの仕様:**

| パラメータ | 値 | 説明 |
|-----------|---|------|
| 初回待機時間 | 5秒 | AI応答生成開始を待つ |
| ポーリング間隔 | 2秒 | ページ全体のテキストを取得 |
| 最大試行回数 | 30回 | 60秒間監視 (30回 × 2秒) |
| 検出条件 | `<DATA_START>` と `<DATA_END>` 両方存在 | JSONデータの完全性確認 |

**タイミング図:**

```
0秒    注入成功
↓
5秒    監視開始 (1回目)
↓
7秒    2回目チェック
↓
9秒    3回目チェック
↓
...
↓
65秒   30回目チェック (最終)
↓
タイムアウト
```

### 3.2 マーカー検出システム

**プロンプト内のマーカー定義:**

```kotlin
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
```

**マーカーの役割:**

| マーカー | 役割 | 重要性 |
|---------|------|--------|
| `<DATA_START>` | JSON開始位置を明示 | 必須 (AIへの指示 + 抽出処理の目印) |
| `<DATA_END>` | JSON終了位置を明示 | 必須 (AIへの指示 + 抽出処理の目印) |

**なぜマーカーが必要か:**
1. **AIの回答制御**: 「このマーカーで囲んでください」と明示的に指示
2. **確実な抽出**: ページ全体から正確にJSON部分だけを取り出す
3. **説明文の除外**: AIが補足説明を追加しても、マーカー範囲外なら無視できる

---

## 4. 応答抽出処理

### 4.1 JavaScriptレスポンスのデコード

```kotlin
private fun decodeJsString(raw: String): String {
    var text = raw.removeSurrounding("\"")
    text = text.replace("\\n", "\n")
    text = text.replace("\\\"", "\"")
    return text
}
```

**デコード対応表:**

| 入力 | 出力 | 説明 |
|-----|------|------|
| `\"テキスト\"` | `テキスト` | 両端のダブルクォート除去 |
| `\\n` | `\n` | 改行文字変換 |
| `\\"` | `"` | ダブルクォート変換 |

### 4.2 JSON抽出ロジック

```kotlin
private fun extractJsonFromAiText(text: String): String? {
    val startMarker = "<DATA_START>"
    val endMarker = "<DATA_END>"
    
    // 最後の<DATA_START>を探す (AIが複数回出力した場合の対策)
    val lastStart = text.lastIndexOf(startMarker)
    if (lastStart == -1) return null
    
    // その後の<DATA_END>を探す
    val lastEnd = text.indexOf(endMarker, lastStart)
    if (lastEnd == -1) return null
    
    // マーカー間のテキストを抽出
    return text.substring(lastStart + startMarker.length, lastEnd).trim()
}
```

**抽出フロー図:**

```
[fullText]
"Perplexityのページヘッダー\n質問: JANコードから商品情報を...\n\n<DATA_START>\n{\"jan\":\"4901234567890\",...}\n<DATA_END>\n\nこの商品は..."

↓ lastIndexOf("<DATA_START>")

lastStart = 78 (最後の<DATA_START>位置)

↓ indexOf("<DATA_END>", lastStart)

lastEnd = 156 (<DATA_END>位置)

↓ substring(lastStart + "<DATA_START>".length, lastEnd)

"{\"jan\":\"4901234567890\",...}"

↓ trim()

"{\"jan\":\"4901234567890\",...}"
```

**なぜ`lastIndexOf`を使うか:**
- AIが誤って複数回マーカーを出力する可能性がある
- 最後の (= 最新の) JSON出力を優先的に採用
- プロンプト内のサンプルJSONと区別できる

### 4.3 JSONパース処理

```kotlin
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
```

**ProductDataスキーマ:**

| フィールド | 型 | 必須 | 説明 |
|-----------|---|------|------|
| `jan` | String | ○ | JANコード (13桁) |
| `maker` | String? | - | メーカー名 |
| `name` | String | ○ | 商品名 |
| `category` | String? | - | カテゴリ (例: "清涼飲料") |
| `min_price` | Int? | - | 最低価格 (円) |
| `max_price` | Int? | - | 最高価格 (円) |
| `width_cm` | Double? | - | 幅 (cm) |
| `height_cm` | Double? | - | 高さ (cm) |
| `depth_cm` | Double? | - | 奥行き (cm) |

**nullable (`?`) の意味:**
- AIが情報を取得できない場合、`null`を返す
- 必須フィールド (`jan`, `name`) 以外は省略可能

### 4.4 単位変換と結果返却

```kotlin
private fun returnResult(data: ProductData) {
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
    setResult(RESULT_OK, resultIntent)
    finish()
}
```

**単位変換の理由:**
- **AI側**: cmで出力 (人間が理解しやすい)
- **DB側**: mmで保存 (精度向上、整数演算)
- **変換式**: `mm = cm × 10`

---

## 5. エラーハンドリング

### 5.1 入力欄検知失敗

```kotlin
when {
    result?.contains("INPUT_NOT_FOUND") == true -> {
        Log.w("WebView", "Input element not found. Will retry...")
    }
}
```

**対策:**
1. 自動的に2秒後に再試行
2. 最大5回までリトライ
3. 全失敗時にToastでユーザーに通知
4. 手動注入ボタンが利用可能

### 5.2 送信ボタン検知失敗

```javascript
if (sendBtn && !sendBtn.disabled) {
    sendBtn.click();
} else {
    console.log('[Injection] Send button not found or disabled');
    return 'INPUT_SET';
}
```

**対策:**
1. 6種類のセレクタを順番に試行
2. ボタンが無効化されている場合はスキップ
3. 全て失敗した場合は `'INPUT_SET'` を返す
4. ユーザーが手動で送信ボタンを押す

### 5.3 監視タイムアウト

```kotlin
if (attempts > 30) {
    Log.w("WebView", "Auto-monitoring timeout after 30 attempts")
    return
}
```

**対策:**
1. 60秒 (30回 × 2秒) で監視を停止
2. ログに警告を出力
3. ユーザーが手動で「データ抽出」ボタンを押せる

### 5.4 JSONパースエラー

```kotlin
try {
    val productData = json.decodeFromString<ProductData>(jsonString)
    returnResult(productData)
} catch (e: Exception) {
    Log.e("PerplexityWebView", "JSON parse error", e)
}
```

**エラー原因:**
- AIが不正なJSON形式で出力
- マーカーの位置が不正
- 必須フィールドが欠落

**対策:**
1. エラーログを出力
2. Activityは終了しない (ユーザーがWebViewを確認可能)
3. 手動で「データ抽出」ボタンを再試行できる

---

## 6. WebView設定

```kotlin
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
```

**設定解説:**

| 設定項目 | 値 | 理由 |
|---------|---|------|
| `javaScriptEnabled` | `true` | JavaScript実行必須 |
| `domStorageEnabled` | `true` | localStorage等の使用を許可 |
| `userAgentString` | モバイルChrome | Perplexity.aiがモバイル版UIを提供 |

**UserAgent の重要性:**
- デフォルトのWebView UAだとデスクトップ版が表示される可能性
- モバイル版UIの方がDOM構造がシンプル
- `"; wv"` (WebView識別子) を含まないUAでブラウザと同じ動作

---

## 7. UI構成

### 7.1 レイアウトファイル (activity_perplexity_webview.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp">

        <Button
            android:id="@+id/btnInjectPrompt"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="手動注入" />

        <Button
            android:id="@+id/btnExtractData"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="データ抽出" />
    </LinearLayout>

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</LinearLayout>
```

### 7.2 ボタン機能

**手動注入ボタン:**
- 自動注入が失敗した場合にユーザーが手動で実行
- `injectPrompt()` を再度呼び出す
- リトライカウンタはリセットされない

**データ抽出ボタン:**
- AI応答が完了したがマーカーが検出されない場合に使用
- `extractData()` を手動で実行
- ページ全体のテキストを強制的に取得して抽出試行

---

## 8. ログ出力仕様

### 8.1 主要ログポイント

```kotlin
// ページ読み込み完了
Log.d("WebView", "Page finished: $url")

// 注入試行
Log.d("WebView", "Injection attempt #$injectionAttempts")

// 注入結果
Log.d("WebView", "Injection result: $result")

// JavaScript内ログ
console.log('[Injection] Starting prompt injection...');
console.log('[Injection] Found input with selector: ' + selectors[i]);
console.log('[Injection] Input focused');
console.log('[Injection] Set via innerText/textContent');
console.log('[Injection] Events dispatched');
console.log('[Injection] Found send button with: ' + btnSelectors[i]);
console.log('[Injection] SENT');

// 監視タイムアウト
Log.w("WebView", "Auto-monitoring timeout after 30 attempts")

// データ検出
Log.d("WebView", "Data markers found! Extracting...")

// JSON抽出成功
Log.d("WebView", "Extracted JSON: $jsonString")

// JSONパースエラー
Log.e("PerplexityWebView", "JSON parse error", e)
```

### 8.2 WebChromeClientによるJavaScriptログ転送

```kotlin
webChromeClient = object : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
        message?.let {
            Log.d("WebView-Console", "${it.message()} (${it.sourceId()}:${it.lineNumber()})")
        }
        return true
    }
}
```

**利点:**
- JavaScript内の `console.log()` がKotlin側のLogcatに表示される
- ブラウザDevToolsなしでデバッグ可能
- エラーの原因特定が容易

---

## 9. 完全な実装コード

### 9.1 Perplexity.ai専用JavaScript注入スクリプト

```javascript
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
```

---

## 10. 改善案と今後の拡張

### 10.1 現在の課題

1. **Perplexity.aiのUI変更に脆弱**
   - セレクタが変更されると注入失敗
   - 定期的なメンテナンスが必要

2. **監視タイムアウトの固定値**
   - 長い応答 (1分以上) に対応できない
   - AIが遅い場合、タイムアウトで失敗

3. **エラーハンドリングの不足**
   - JSONパース失敗時の詳細なエラー情報がない
   - ユーザーへのフィードバックが限定的

### 10.2 推奨改善案

#### ① MutationObserverによる動的検知

```javascript
// DOM変更を監視して入力欄を自動検出
var observer = new MutationObserver(function(mutations) {
    var input = document.querySelector('textarea, div[contenteditable="true"]');
    if (input && !promptInjected) {
        injectPromptToElement(input);
    }
});

observer.observe(document.body, {
    childList: true,
    subtree: true
});
```

**利点:**
- ページ読み込みタイミングに依存しない
- 動的に生成される要素にも対応

#### ② 応答長ベースの完了判定

```kotlin
// テキスト長が3回連続で変化しない = 生成完了
var stableCount = 0
var lastLength = 0

if (currentLength == lastLength) {
    stableCount++
    if (stableCount >= 3) {
        // 完了とみなす
    }
} else {
    stableCount = 0
    lastLength = currentLength
}
```

**利点:**
- マーカーが出力されなくても検出可能
- 安定化判定でより確実

#### ③ ステータス表示の強化

```kotlin
// プログレスバーとステータステキストを追加
binding.statusText.text = "AI応答待機中... (${attempts}/30)"
binding.progressBar.progress = (attempts * 100) / 30
```

**利点:**
- ユーザーが進捗状況を把握できる
- 「フリーズしていない」ことを視覚的に伝える

---

## 11. まとめ

本仕様書で解説したAI自動貼り付け送信機能は、以下の技術を組み合わせて実装されています。

### 核心技術

1. **JavaScript注入**: `evaluateJavascript()` による双方向通信
2. **複数セレクタ**: 8種類の入力欄検知パターン
3. **イベントディスパッチ**: React/Vue対応のイベント送信
4. **リトライメカニズム**: 最大5回の自動再試行
5. **ポーリング監視**: 2秒間隔、60秒間の応答監視
6. **マーカーベース抽出**: `<DATA_START>`/`<DATA_END>` によるJSON抽出

### 設計原則

1. **フォールバックの多重化**: 複数の検知方法を優先順位付けて実装
2. **状態管理の厳密化**: `promptInjected` フラグによる二重送信防止
3. **ログ出力の充実**: デバッグとトラブルシューティングの容易性
4. **ユーザーフレンドリー**: 自動失敗時の手動ボタン提供

### 今後の拡張性

1. **新しいAIサイトへの対応**: セレクタパターンを追加
2. **UI変更への追従**: セレクタの優先順位を調整
3. **エラーハンドリングの強化**: より詳細なエラーメッセージ
4. **MutationObserver導入**: 動的DOM変更への対応

---

## 参考情報

- **LanguagePracticeAPP**: 本実装の参考元となった擬古文学習アプリ
- **Perplexity.ai**: 対象AIサイト
- **Android WebView**: [公式ドキュメント](https://developer.android.com/reference/android/webkit/WebView)
- **Kotlinx Serialization**: [公式ドキュメント](https://kotlinlang.org/docs/serialization.html)

---

**作成日**: 2026年2月16日  
**バージョン**: 1.0  
**対象アプリ**: スーパーマーケット売場レイアウトアプリ  
**実装ファイル**: `PerplexityWebViewActivity.kt`
