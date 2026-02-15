# AI自動貼り付け送信機能 完全仕様書

## 概要

SupermarketLayoutAppにおける**AI自動貼り付け送信機能**は、Android WebView内でPerplexity.aiにJANコードを含むプロンプトを自動的に入力・送信し、生成された商品情報JSONを監視・抽出する機構です。

**実装ファイル**: `PerplexityWebViewActivity.kt`

---

## システムアーキテクチャ

### コンポーネント構成

```
┌─────────────────────────────────────────┐
│ PerplexityWebViewActivity          │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ WebView Container              │ │
│ │ (Perplexity.ai)               │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ JavaScript Injection Engine      │
│ - buildPerplexityInjectionScript()│
│ - jsEscape()                      │
├─────────────────────────────────────────┤
│ Response Monitoring Loop          │
│ - monitoringLoop()                │
│ - DONE_SENTINEL detection         │
│ - Text length stability check    │
├─────────────────────────────────────────┤
│ Response Extraction Engine        │
│ - extractAiResponse()             │
│ - decodeJsString()                │
└─────────────────────────────────────────┘
```

---

## データフロー詳細

```
JANコード受信
↓
WebView初期化 + URL読み込み
↓ (onPageFinished)
3秒待機
↓
JavaScript注入実行
├─ 入力欄検知
├─ 既存テキストクリア (Selection API)
├─ プロンプト入力 (execCommand)
└─ 送信ボタンクリック (500ms後)
↓
初期ページ長を記録
↓
生成監視ループ開始 (1.5秒間隔)
├─ document.body.innerText取得
├─ DONE_SENTINEL カウント
├─ テキスト長変化監視
└─ 安定化判定 (7回連続不変)
↓
生成完了検出
↓
最終テキスト取得 (1.5秒後)
↓
AI応答抽出 (extractAiResponse)
├─ プロンプト部分スキップ
├─ マーカー検索
└─ DONE_SENTINEL除去
↓
JSONパースと結果返却
```

---

## 1. プロンプト注入処理

### 1.1 注入状態管理

```kotlin
private enum class InjectionState {
    NOT_STARTED,   // 未実行
    INJECTING,     // 注入実行中
    COMPLETED,     // 注入完了
    FAILED         // 入力欄検知失敗
}
```

**状態遷移図**:
```
NOT_STARTED → INJECTING → COMPLETED
     ↓                         
   FAILED (入力欄未検出時)
```

### 1.2 JavaScriptエスケープ処理

```kotlin
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
```

**エスケープ対応表**:

| 入力文字 | 出力形式 | 説明 |
|----------|----------|------|
| `\` | `\\` | バックスラッシュ |
| `"` | `\"` | ダブルクォート |
| `\n` | `\n` | 改行 |
| `\r` | `\r` | キャリッジリターン |
| `\t` | `\t` | タブ |
| 制御文字 | `\uXXXX` | Unicode形式 |

---

## 2. Perplexity.ai 専用検知実装

### 2.1 入力欄検知セレクタ (優先順位順)

```javascript
var input = 
  // ① Lexicalエディタ専用セレクタ (最優先)
  document.querySelector('#ask-input[contenteditable="true"][data-lexical-editor="true"]') ||
  
  // ② ID + contenteditable 汎用
  document.querySelector('#ask-input[contenteditable="true"]') ||
  
  // ③ role属性による検知
  document.querySelector('div[contenteditable="true"][role="textbox"]') ||
  
  // ④ フォールバック (通常のtextarea)
  document.querySelector('textarea');
```

#### 検知ロジックの詳細

##### ① Lexicalエディタ検知

```javascript
#ask-input[contenteditable="true"][data-lexical-editor="true"]
```

- **対象**: Perplexity.aiの標準入力欄
- **特徴**: `data-lexical-editor`属性により、Lexical Frameworkを使用していることを明示
- **DOM構造例**:
  ```xml
  <div id="ask-input" 
       contenteditable="true" 
       data-lexical-editor="true"
       role="textbox"
       aria-label="質問を入力">
  </div>
  ```

##### ② ID汎用検知

```javascript
#ask-input[contenteditable="true"]
```

- **対象**: Lexical属性が削除された場合のフォールバック
- **理由**: サイト更新で`data`属性が変更される可能性に対応

##### ③ role属性検知

```javascript
div[contenteditable="true"][role="textbox"]
```

- **対象**: IDが変更された場合の最終手段
- **理由**: アクセシビリティ対応として`role="textbox"`は維持される可能性が高い

##### ④ textarea フォールバック

```javascript
textarea
```

- **対象**: 完全にUI構造が変更された場合
- **理由**: 最も基本的なHTML要素

### 2.2 既存テキストクリア処理

```javascript
input.focus();

try {
  var sel = window.getSelection();
  var range = document.createRange();
  range.selectNodeContents(input);
  sel.removeAllRanges();
  sel.addRange(range);
  document.execCommand('delete');
} catch(e) {}
```

**Selection API動作フロー**:
1. `input.focus()`: 入力欄にフォーカスを当てる
2. `window.getSelection()`: 現在の選択範囲オブジェクトを取得
3. `createRange()`: 新しい選択範囲を作成
4. `selectNodeContents(input)`: 入力欄の全コンテンツを選択
5. `removeAllRanges()`: 既存選択をクリア
6. `addRange(range)`: 新しい選択を適用
7. `execCommand('delete')`: 選択範囲を削除

**なぜSelection APIが必要か**:
- `textContent = ""`や`innerHTML = ""`では、Lexicalエディタの内部状態がリセットされない
- `execCommand('delete')`は、エディタのイベントハンドラを正しくトリガーする

### 2.3 プロンプト入力処理

```javascript
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
```

**`execCommand('insertText')`の重要性**:
- Lexicalエディタがリッスンしているイベントチェーンを自動的にトリガー
- React/Vueなどのフレームワークが内部状態を更新
- 非推奨(deprecated)だが、実際のブラウザサポートは維持されている

**フォールバック処理**:
- `textContent`で直接テキストを設定
- `InputEvent`を手動でディスパッチしてイベントリスナーをトリガー
- `bubbles: true`により親要素へイベントを伝播

### 2.4 送信ボタン検知・実行

```javascript
setTimeout(function() {
  var sendBtn = 
    document.querySelector('button[aria-label="送信"]') ||
    document.querySelector('button[aria-label*="Send"]') ||
    document.querySelector('button[type="submit"]');
  
  if (sendBtn) sendBtn.click();
  else {
    input.dispatchEvent(new KeyboardEvent('keydown', { 
      bubbles: true, 
      key: 'Enter', 
      code: 'Enter', 
      keyCode: 13 
    }));
  }
}, 500);
```

**送信ボタン検知セレクタ詳細**:

| 優先度 | セレクタ | 説明 |
|------|---------|------|
| 1 | `button[aria-label="送信"]` | 日本語UI完全一致 |
| 2 | `button[aria-label*="Send"]` | 英語UI部分一致 |
| 3 | `button[type="submit"]` | HTML標準フォーム送信 |

**500ms遅延の理由**:
- Lexicalエディタの内部状態更新を待つ
- Reactの再レンダリングサイクルを考慮
- 送信ボタンの有効/無効状態が更新されるまで待機

**Enterキー送信 (フォールバック)**:
```javascript
new KeyboardEvent('keydown', {
  bubbles: true,    // 親要素へ伝播
  key: 'Enter',     // キー識別子
  code: 'Enter',    // 物理キーコード
  keyCode: 13       // レガシー数値コード
})
```

---

## 3. 生成監視システム

### 3.1 監視ループ実装

```kotlin
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
            } else {
                handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
                return@evaluateJavascript
            }
        }
        
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
            // 結果取得...
        } else {
            handler.postDelayed({ monitoringLoop() }, MONITORING_INTERVAL)
        }
    }
}
```

### 3.2 DONE_SENTINEL 仕様

**定義**:
```kotlin
private val DONE_SENTINEL = "⟦LP_DONE_9F3A2C⟧"
```

**役割**:
- プロンプトと応答の境界を明確にするマーカー
- 生成完了を検出するためのシグナル

**カウント方法**:
```kotlin
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
```

**判定ロジック**:
```
プロンプト内のSENTINEL数を事前カウント → promptSentinelCount
ページ全体のSENTINEL数が (promptSentinelCount + 1) 以上
→ AI応答にもSENTINELが含まれた = 生成完了
```

### 3.3 安定化判定アルゴリズム

**目的**: ストリーミング生成の途切れと完了を区別

**条件**:
- テキスト長が **7回連続** (= 10.5秒間) 変化しない
- かつ、DONE_SENTINELが必要数出現している

**実装**:
```kotlin
if (currentLength != lastTextLength) {
    stableCount = 0  // 変化があればリセット
    lastTextLength = currentLength
} else {
    stableCount++    // 変化なしでインクリメント
}

if (stableCount >= REQUIRED_STABLE_COUNT) {
    // 生成完了
}
```

**タイミング図**:
```
時刻  テキスト長  安定カウント
0秒   1000       0
1.5秒 1200       0 (変化)
3.0秒 1400       0 (変化)
4.5秒 1400       1 (不変)
6.0秒 1400       2 (不変)
7.5秒 1400       3 (不変)
9.0秒 1400       4 (不変)
10.5秒 1400      5 (不変)
12.0秒 1400      6 (不変)
13.5秒 1400      7 (完了判定)
```

---

## 4. 応答抽出処理

### 4.1 JavaScriptレスポンスのデコード

```kotlin
private fun decodeJsString(raw: String): String {
    var text = raw.removeSurrounding("\"")
    
    text = text.replace("\\n", "\n")
    text = text.replace("\\\"", "\"")
    
    // \\uXXXX → Unicode文字
    val unicodePattern = Regex("""\\\\u([0-9a-fA-F]{4})""")
    text = unicodePattern.replace(text) { matchResult ->
        val hexCode = matchResult.groupValues[1]
        val codePoint = hexCode.toInt(16)
        codePoint.toChar().toString()
    }
    
    return text
}
```

**デコード対応表**:

| 入力 | 出力 | 説明 |
|------|------|------|
| `\\n` | `\n` | 改行 |
| `\\"` | `"` | ダブルクォート |
| `\\u3042` | `あ` | Unicode文字 |

### 4.2 AI応答抽出ロジック

```kotlin
private fun extractAiResponse(
    fullText: String, 
    promptSentinelCount: Int, 
    sentinel: String
): String {
    // ① プロンプト部分をスキップ
    var pos = 0
    for (i in 0 until promptSentinelCount) {
        pos = fullText.indexOf(sentinel, pos)
        if (pos == -1) return fullText
        pos += sentinel.length
    }

    val afterPrompt = if (pos > 0) fullText.substring(pos) else fullText

    // ② AI応答の終了位置を検索
    val aiSentinelPos = afterPrompt.indexOf(sentinel)
    val extractEndPos = if (aiSentinelPos != -1) {
        aiSentinelPos
    } else {
        val leftBracketPos = afterPrompt.indexOf("⟦")
        if (leftBracketPos != -1) leftBracketPos
        else afterPrompt.length
    }

    // ③ 開始マーカーを検索
    val startMarker = "<DATA_START>"
    val markerPos = afterPrompt.indexOf(startMarker)

    val extracted = if (markerPos != -1 && markerPos < extractEndPos) {
        afterPrompt.substring(markerPos, extractEndPos)
    } else {
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
    
    return cleaned
}
```

**抽出フロー図**:
```
[fullText]
"プロンプトテキスト⟦LP_DONE_9F3A2C⟧ページ内容<DATA_START>AI生成結果<DATA_END>⟦LP_DONE_9F3A2C⟧その他"

↓  promptSentinelCount回分スキップ

"ページ内容<DATA_START>AI生成結果<DATA_END>⟦LP_DONE_9F3A2C⟧その他"

↓  AI応答終了位置検索 (次のSENTINEL)

extractEndPos = ⟦LP_DONE_9F3A2C⟧の位置

↓  開始マーカー検索

markerPos = <DATA_START>の位置

↓  抽出

"<DATA_START>AI生成結果<DATA_END>"

↓  クリーニング (SENTINEL除去)

"<DATA_START>AI生成結果<DATA_END>"
```

---

## 5. エラーハンドリング

### 5.1 入力欄検知失敗

```kotlin
if (result?.contains("INPUT_NOT_FOUND") == true) {
    injectionState = InjectionState.FAILED
    Toast.makeText(
        this, 
        "自動注入に失敗しました。手動ボタンをお試しください。", 
        Toast.LENGTH_LONG
    ).show()
}
```

**ユーザーアクション**:
- 手動注入ボタンが有効化される
- 手動で入力欄を操作した後、再度ボタンを押せる

### 5.2 リトライメカニズム

```kotlin
private fun attemptInjectionWithRetry() {
    if (injectionState == InjectionState.COMPLETED || 
        injectionAttempts >= MAX_INJECTION_ATTEMPTS) {
        return
    }
    
    injectionAttempts++
    injectPrompt()
    
    handler.postDelayed({ 
        if (injectionState != InjectionState.COMPLETED) {
            attemptInjectionWithRetry()
        }
    }, RETRY_INJECTION_DELAY)
}
```

**リトライ仕様**:
- 最大5回まで自動リトライ
- 各リトライ間隔2秒の遅延
- 成功時は即座にリトライ停止

---

## 6. UI構成

### 6.1 手動ボタン

```kotlin
binding.btnInjectPrompt.setOnClickListener {
    injectionState = InjectionState.NOT_STARTED
    injectPrompt()
}

binding.btnExtractData.setOnClickListener {
    extractData()
}
```

**ボタン機能**:
- **プロンプト注入**: 状態をリセットして再注入
- **データ抽出**: 現在のページ内容から即座にJSON抽出

---

## 7. WebView設定

```kotlin
binding.webView.apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        
        // UserAgent最適化
        userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
```

**設定解説**:

| 設定項目 | 値 | 理由 |
|----------|-----|------|
| `javaScriptEnabled` | `true` | JavaScript実行必須 |
| `domStorageEnabled` | `true` | localStorage等の使用を許可 |
| `databaseEnabled` | `true` | Web SQL Database許可(非推奨だが互換性維持) |
| `loadWithOverviewMode` | `true` | コンテンツを画面幅に合わせる |
| `useWideViewPort` | `true` | ビューポートメタタグを有効化 |
| `userAgentString` | Mobile Chrome | モバイルUIを表示 |

---

## 8. ログ出力仕様

### 8.1 主要ログポイント

```kotlin
// 起動時
Log.d(TAG, "Activity起動: JAN=$janCode")
Log.d(TAG, "promptSentinelCount=$promptSentinelCount")
Log.d(TAG, "注入予定プロンプト(全文):")

// 注入開始時
Log.d(TAG, "★★★ プロンプト注入開始 ★★★")

// ページ読み込み完了時
Log.d(TAG, "ページ読み込み完了: $url, injectionState=$injectionState")

// 監視ループ
Log.d(TAG, "監視中: Sentinel=$totalSentinelCount/$requiredCount, 長さ=$currentLength, 安定=$stableCount/$REQUIRED_STABLE_COUNT")

// 生成完了時
Log.d(TAG, "★★★ 生成完了検出！結果を取得中... ★★★")

// 抽出処理
Log.d(TAG, "========== extractAiResponse 開始 ==========")
Log.d(TAG, "fullText.length=${fullText.length}")
Log.d(TAG, "抽出完了: length=${cleaned.length}")
```

### 8.2 デバッグ時の確認項目

1. **プロンプトが正しく注入されているか**
   - `★★★ プロンプト注入開始 ★★★`ログで確認

2. **DONE_SENTINELが正しくカウントされているか**
   - `promptSentinelCount`の値を確認

3. **生成監視が動作しているか**
   - `監視中: Sentinel=X/Y`のログを確認

4. **抽出処理が正しく実行されているか**
   - `extractAiResponse`の詳細ログを確認

---

## 9. 完全な実装コード (Perplexity専用)

### JavaScript注入スクリプト

```kotlin
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
  }, 500);
  
  return 'PROCESSING';
})();
    """.trimIndent()
}
```

---

## 10. まとめ

本仕様書で解説したAI自動貼り付け送信機能は、以下の技術を組み合わせて実装されています:

### 核心技術

1. **JavaScript注入**: `evaluateJavascript()`による双方向通信
2. **Selection API**: contenteditable要素の確実なテキストクリア
3. **execCommand**: Lexicalエディタ互換の入力メソッド
4. **Handler監視ループ**: 非同期でのポーリング処理
5. **DONE_SENTINEL**: 生成完了の確実な検出
6. **安定化判定**: ストリーミング生成の完了判定

### 設計原則

1. **フォールバックの多重化**: 複数の検知方法を優先順位付けて実装
2. **状態管理の厳密化**: `InjectionState`による二重送信防止
3. **ログ出力の充実**: デバッグとトラブルシューティングの容易性
4. **Perplexity専用最適化**: Lexicalエディタ対応のセレクタ

### 今後の拡張性

- 新しいAIサイトへの対応: `buildInjectionScript()`に分岐を追加
- UI変更への追従: セレクタの優先順位を調整
- エラーハンドリングの強化: より詳細なエラーメッセージ

---

## 参考資料

- LanguagePracticeAPP: 元となった実装パターン
- Perplexity.ai: 対象サイトのUI構造
- Lexical Framework: Facebook製リッチテキストエディタ
- WebView JavaScript Bridge: Android公式ドキュメント
