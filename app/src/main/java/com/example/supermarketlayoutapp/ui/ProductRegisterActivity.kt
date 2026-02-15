package com.example.supermarketlayoutapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ActivityProductRegisterBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 商品マスター登録 Activity
 * 
 * 機能:
 * - 複数JANコードのカンマ区切り/改行区切り入力
 * - テキストファイルからの一括読み込み
 * - **複数JANコードを一括でPerplexity.aiに送信**
 * - **1回の通信で複数商品情報を取得** (高速化)
 */
class ProductRegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProductRegisterBinding
    private lateinit var repository: AppRepository
    private val productResults = mutableListOf<ProductEntity>()
    private lateinit var adapter: ProductResultAdapter
    
    companion object {
        private const val TAG = "ProductRegisterActivity"
        private const val REQUEST_CODE_PERPLEXITY = 1001
        private const val MAX_BATCH_SIZE = 10 // 一度に送信する最大JAN数
    }
    
    /**
     * ファイルピッカー用のActivityResultLauncher
     */
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "ファイル選択: $uri")
            loadJanCodesFromFile(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)
        
        setupRecyclerView()
        setupListeners()
    }
    
    private fun setupRecyclerView() {
        adapter = ProductResultAdapter(productResults) { product ->
            saveProduct(product)
        }
        binding.resultRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.resultRecyclerView.adapter = adapter
    }
    
    private fun setupListeners() {
        // ファイル読み込みボタン
        binding.loadFileButton.setOnClickListener {
            Log.d(TAG, "ファイル読み込みボタンが押されました")
            openFilePicker()
        }
        
        // 検索開始ボタン
        binding.searchButton.setOnClickListener {
            val janInput = binding.janEditText.text.toString().trim()
            if (janInput.isEmpty()) {
                Snackbar.make(binding.root, "JANコードを入力するか、ファイルから読み込んでください", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            startSearch(janInput)
        }
    }
    
    /**
     * ファイルピッカーを開く
     */
    private fun openFilePicker() {
        try {
            openDocumentLauncher.launch(arrayOf("text/plain", "*/*"))
        } catch (e: Exception) {
            Log.e(TAG, "ファイルピッカー起動エラー", e)
            Snackbar.make(
                binding.root, 
                "ファイル選択エラー: ${e.message}", 
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * ファイルからJANコードを読み込む
     */
    private fun loadJanCodesFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val janCodes = mutableListOf<String>()
                
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.trim()?.let { trimmedLine ->
                                if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                                    // カンマ区切りをサポート
                                    val codes = trimmedLine.split(",")
                                        .map { it.trim() }
                                        .filter { it.length >= 8 && it.all { c -> c.isDigit() } }
                                    janCodes.addAll(codes)
                                }
                            }
                        }
                    }
                }
                
                Log.d(TAG, "ファイルから${janCodes.size}件のJANコードを読み込みました")
                
                if (janCodes.isEmpty()) {
                    Snackbar.make(
                        binding.root, 
                        "有効なJANコードが見つかりませんでした", 
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                // EditTextに表示 (改行区切りで)
                binding.janEditText.setText(janCodes.joinToString("\n"))
                
                Snackbar.make(
                    binding.root, 
                    "${janCodes.size}件のJANコードを読み込みました", 
                    Snackbar.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "ファイル読み込みエラー", e)
                Snackbar.make(
                    binding.root, 
                    "ファイル読み込みエラー: ${e.message}", 
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 検索を開始
     */
    private fun startSearch(janInput: String) {
        // カンマ区切りと改行区切り両方に対応
        val janList = janInput
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.length >= 8 && it.all { c -> c.isDigit() } }
        
        if (janList.isEmpty()) {
            Snackbar.make(
                binding.root, 
                "8桁以上の有効なJANコードを入力してください", 
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        
        Log.d(TAG, "★★★ 検索開始: ${janList.size}件のJANコード ★★★")
        Log.d(TAG, "JANリスト: $janList")
        
        productResults.clear()
        adapter.notifyDataSetChanged()
        
        processBatch(janList)
    }
    
    /**
     * 複数JANコードをバッチ処理
     */
    private fun processBatch(janList: List<String>) {
        showLoading(true)
        binding.statusText.text = "${janList.size}件のJANコードを処理中..."
        
        lifecycleScope.launch {
            try {
                val existingProducts = mutableListOf<ProductEntity>()
                val missingJans = mutableListOf<String>()
                
                // 既存商品と未登録JANを仕分け
                for (jan in janList) {
                    val existing = repository.getProductByJan(jan)
                    if (existing != null) {
                        existingProducts.add(existing)
                        Log.d(TAG, "☆ 既存商品: JAN=$jan, 名称=${existing.name}")
                    } else {
                        missingJans.add(jan)
                        Log.d(TAG, "→ 未登録: JAN=$jan")
                    }
                }
                
                // 既存商品を結果に追加
                productResults.addAll(existingProducts)
                adapter.notifyDataSetChanged()
                binding.resultRecyclerView.visibility = View.VISIBLE
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "既存商品: ${existingProducts.size}件")
                Log.d(TAG, "未登録JAN: ${missingJans.size}件")
                Log.d(TAG, "========================================")
                
                if (missingJans.isEmpty()) {
                    showLoading(false)
                    binding.statusText.text = "全${janList.size}件の検索完了（すべてデータベースから取得）"
                    Log.d(TAG, "★★★ 全件既存商品でした。検索完了 ★★★")
                    return@launch
                }
                
                // 未登録JANをバッチごとにPerplexity.aiで検索
                val batches = missingJans.chunked(MAX_BATCH_SIZE)
                Log.d(TAG, "未登録JANを${batches.size}バッチに分割して検索します")
                
                // 最初のバッチを送信
                searchWithPerplexity(batches[0], batchIndex = 1, totalBatches = batches.size)
                
            } catch (e: Exception) {
                Log.e(TAG, "バッチ処理エラー", e)
                binding.statusText.text = "エラー: ${e.message}"
                showLoading(false)
            }
        }
    }
    
    /**
     * Perplexity.aiで複数JANコードを一括検索
     */
    private fun searchWithPerplexity(
        janList: List<String>,
        batchIndex: Int,
        totalBatches: Int
    ) {
        Log.d(TAG, "★★★ Perplexity.ai起動: バッチ$batchIndex/$totalBatches (${janList.size}件) ★★★")
        Log.d(TAG, "JANリスト: $janList")
        
        binding.statusText.text = "Perplexity.aiで検索中... (バッチ$batchIndex/$totalBatches)"
        
        val intent = Intent(this, PerplexityWebViewActivity::class.java)
        intent.putExtra("JAN_CODE_LIST", janList.toTypedArray())
        startActivityForResult(intent, REQUEST_CODE_PERPLEXITY)
    }
    
    /**
     * Perplexity.aiからの複数結果を受け取る
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == REQUEST_CODE_PERPLEXITY) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "★ Perplexity.aiから成功結果を受信 ★")
                
                val productCount = data.getIntExtra("product_count", 0)
                Log.d(TAG, "受信した商品数: $productCount")
                
                // 複数商品をループで解析
                for (i in 0 until productCount) {
                    val prefix = "product_$i"
                    val product = ProductEntity(
                        jan = data.getStringExtra("${prefix}_jan") ?: "",
                        maker = data.getStringExtra("${prefix}_maker"),
                        name = data.getStringExtra("${prefix}_name") ?: "不明",
                        category = data.getStringExtra("${prefix}_category"),
                        minPrice = data.getIntExtra("${prefix}_min_price", 0).takeIf { it > 0 },
                        maxPrice = data.getIntExtra("${prefix}_max_price", 0).takeIf { it > 0 },
                        widthMm = data.getIntExtra("${prefix}_width_mm", 0).takeIf { it > 0 },
                        heightMm = data.getIntExtra("${prefix}_height_mm", 0).takeIf { it > 0 },
                        depthMm = data.getIntExtra("${prefix}_depth_mm", 0).takeIf { it > 0 },
                        priceUpdatedAt = System.currentTimeMillis()
                    )
                    
                    Log.d(TAG, "[$i] 取得した商品: JAN=${product.jan}, 名称=${product.name}")
                    
                    productResults.add(product)
                }
                
                adapter.notifyDataSetChanged()
                binding.resultRecyclerView.visibility = View.VISIBLE
                
            } else {
                Log.w(TAG, "⚠ Perplexity.aiからの結果が無いまたはキャンセルされました")
            }
            
            // 検索完了
            showLoading(false)
            binding.statusText.text = "全${productResults.size}件の検索完了"
            Log.d(TAG, "★★★ 全検索完了 ★★★")
        }
    }
    
    /**
     * 商品をデータベースに保存
     */
    private fun saveProduct(product: ProductEntity) {
        lifecycleScope.launch {
            try {
                repository.insertProduct(product)
                Log.d(TAG, "★ 商品をデータベースに保存: ${product.name}")
                Snackbar.make(
                    binding.root, 
                    "${product.name} を保存しました", 
                    Snackbar.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "保存エラー", e)
                Snackbar.make(
                    binding.root, 
                    "保存失敗: ${e.message}", 
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * ローディング表示制御
     */
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.searchButton.isEnabled = !show
        binding.loadFileButton.isEnabled = !show
    }
}
