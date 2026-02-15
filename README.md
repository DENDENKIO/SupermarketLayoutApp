# SupermarketLayoutApp

スーパーマーケットの現場における「売場レイアウトの設計」と「棚割（陳列）のシミュレーション」を効率化するAndroidアプリケーションです。

## 主要機能

### Phase 1-2: データベース基盤 【完了】

- Room Databaseによるデータ管理
- Product, Fixture, Shelf, Facing Entityの定義
- Repository PatternによるCRUD操作

### Phase 3: 商品マスター・AI統合機能 【完了・高速化】

#### 複数JANコード対応

✅ **カンマ区切り入力**
```
4901234567890,4901234567891,4901234567892
```

✅ **改行区切り入力**
```
4901234567890
4901234567891
4901234567892
```

✅ **テキストファイルからの一括読み込み**
- 「ファイルから読み込み」ボタンでテキストファイル（.txt）を選択
- ファイル内のJANコードを自動読み込み
- カンマ区切り・改行区切り両方に対応
- コメント行（`#`で始まる）を無視

**ファイル例** (`jan_codes.txt`):
```
# サンプルJANコードリスト
4901234567890
4901234567891
4901234567892
# カンマ区切りもOK
4901234567893,4901234567894
```

#### AI WebView自動化（Perplexity.ai統合）

✅ **複数JANコードの一括処理** 🚀
- **1回のプロンプトで最大10件のJANコードを同時送信**
- **1回の生成で複数商品情報を一括取得**
- 既存商品と未登録商品を自動仕分け
- 未登録のみをPerplexity.aiで検索
- **処理速度が大幅に向上** （例: 10件の商品が約30秒で完了）

**一括処理の仕組み**:
```
入力: JAN1, JAN2, JAN3, JAN4, JAN5
  ↓
既存チェック
  │
  ├─ JAN1, JAN3 → データベースから即座に取得
  └─ JAN2, JAN4, JAN5 → Perplexity.aiで一括検索
       ↓
   プロンプト: "JANコードリスト: JAN2, JAN4, JAN5"
       ↓
   JSON配列で返却: [{ jan: JAN2, ... }, { jan: JAN4, ... }, { jan: JAN5, ... }]
       ↓
   3件を一度に取得完了
```

✅ **自動プロンプト注入**
- Lexicalエディタ対応の最適化セレクタ
- Selection APIによる既存テキストクリア
- `execCommand('insertText')`によるLexical互換入力
- 自動送信ボタン検知とクリック

✅ **自動監視・抽出**
- **DONE_SENTINEL方式**: `⟦LP_DONE_9F3A2C⟧`マーカーによる生成完了検出
- **安定化判定**: テキスト長が7回連続（10.5秒間）不変で完了判定
- **自動JSON配列抽出**: `<DATA_START>`と`<DATA_END>`間のJSON配列を自動抽出
- **自動Activity終了**: 抽出完了後、結果を返して自動で閉じる

✅ **スマートバッチ処理**
- 既存商品はデータベースから即座に取得
- 未登録商品のみをPerplexity.aiで検索
- 最大10件ずつのバッチ処理
- 進捗状況をリアルタイム表示

#### 技術詳細

**プロンプト形式**
```kotlin
"""
JANコードリスト: 4901234567890, 4901234567891, 4901234567892

# 出力形式
<DATA_START>
[
  { "jan": "4901234567890", "name": "...", "maker": "...", ... },
  { "jan": "4901234567891", "name": "...", "maker": "...", ... },
  { "jan": "4901234567892", "name": "...", "maker": "...", ... }
]
<DATA_END>
"""
```

**UserAgent最適化**
```kotlin
userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
```

**状態管理**
```kotlin
private enum class InjectionState {
    NOT_STARTED,   // 未実行
    INJECTING,     // 注入実行中
    COMPLETED,     // 注入完了
    FAILED         // 入力欄検知失敗
}
```

**生成監視ループ**
```kotlin
private fun monitoringLoop() {
    // DONE_SENTINELカウントとテキスト長安定化をチェック
    val totalSentinelCount = countSentinel(currentText, DONE_SENTINEL)
    val requiredCount = promptSentinelCount + 1
    
    if (stableCount >= REQUIRED_STABLE_COUNT) {
        // 生成完了 → 自動抽出
    }
}
```

**複数結果のパース**
```kotlin
val productDataList = json.decodeFromString<List<ProductData>>(jsonString)
productDataList.forEachIndexed { index, data ->
    val prefix = "product_$index"
    resultIntent.putExtra("${prefix}_jan", data.jan)
    resultIntent.putExtra("${prefix}_name", data.name)
    // ...
}
```

### UI/UX

- RecyclerViewを用いた検索結果リスト表示
- 個別保存機能（「保存」ボタン）
- 進捗状況表示（「10件のJANコードを処理中...」）

### Phase 4: 売場レイアウト配置（2D Canvas）【次ステップ】

- 2D平面上での什器配置 UI
- 什器（ゴンドラ、エンド、アイランド）のドラッグ＆ドロップ移動
- 什器の向き（縦・横）とサイズの視覚的反映

### Phase 5: 棚割編集（シミュレーション）

- 選択した什器に対する詳細な棚割設定
- 保存した商品マスターからのドラッグ＆ドロップによる陳列配置
- フェイス数、積み数の設定

### Phase 6: 出力・共有機能

- 作成したレイアウト図の画像出力（PNG/PDF）
- 棚割表のデータ書き出し

## 技術スタック

| カテゴリ | 使用技術・内容 |
|----------|------------------|
| 言語 / フレームワーク | Kotlin / Android SDK (AppCompat方式) |
| データベース | Room Persistence Library (SQLite) |
| JSON処理 | Kotlinx Serialization |
| AI連携 | WebViewによるPerplexity.ai統合 (JS Injection方式) |
| アーキテクチャ | Repository Pattern, Handler監視ループ, バッチ処理 |

## プロジェクト構造

```
app/
├── src/main/
│   ├── java/com/example/supermarketlayoutapp/
│   │   ├── data/
│   │   │   ├── entity/           # Product, Fixture, Shelf, Facing
│   │   │   ├── dao/              # Data Access Objects
│   │   │   ├── AppDatabase.kt
│   │   │   └── AppRepository.kt
│   │   └── ui/
│   │       ├── MainActivity.kt
│   │       ├── ProductRegisterActivity.kt
│   │       └── PerplexityWebViewActivity.kt
│   └── res/
│       └── layout/           # XMLレイアウトファイル
└── build.gradle.kts

docs/
└── AI_AUTO_INJECTION_SPEC.md  # AI自動貼り付け送信機能仕様書
```

## セットアップ

### 前提条件

- Android Studio Koala | 2024.1.1 以降
- Kotlin 1.9.0 以降
- Android SDK 24 (Android 7.0) 以降

### ビルド手順

1. リポジトリをクローン
```bash
git clone https://github.com/DENDENKIO/SupermarketLayoutApp.git
cd SupermarketLayoutApp
```

2. Android Studioでプロジェクトを開く

3. Gradle Syncを実行

4. エミュレータまたは実機で実行

## 使用方法

### 商品マスター登録

#### 方法1: 直接入力

1. 「商品マスター登録」画面を開く
2. JANコードを入力（カンマまたは改行で区切る）
3. 「商品検索開始」ボタンをクリック
4. **AIが自動で複数商品情報を一括取得** 🚀
5. 検索結果が表示されたら、各商品の「保存」ボタンをクリック

#### 方法2: ファイルから読み込み (推奨)

1. JANコードを記載したテキストファイルを用意
   ```
   4901234567890
   4901234567891
   4901234567892
   4901234567893
   4901234567894
   ```
2. 「ファイルから読み込み」ボタンをクリック
3. テキストファイルを選択
4. 自動でJANコードが入力欄に読み込まれる
5. 「商品検索開始」ボタンで検索開始
6. **約30秒で自動的に全件取得完了** ✨

### 処理速度の比較

| 入力JAN数 | 旧バージョン (順次処理) | 新バージョン (一括処理) |
|----------|------------------|------------------|
| 5件 | 約5分 | **約30秒** |
| 10件 | 約10分 | **約30秒** |
| 20件 | 約20分 | **約1分** |

🚀 **最大10倍の高速化を実現！**

## ドキュメント

- [AI自動貼り付け送信機能 完全仕様書](docs/AI_AUTO_INJECTION_SPEC.md)

## ライセンス

MIT License

## 作者

DENDENKIO

## 謝辞

本アプリのAI自動化機能は、LanguagePracticeAPPの実装パターンを参考にしています。
