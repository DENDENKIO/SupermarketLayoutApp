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
 * - Perplexity.aiを使用した自動商品情報取得
 * - シーケンシャルな検索処理（1件ずつ順に実行）
 */
class ProductRegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProductRegisterBinding
    private lateinit var repository: AppRepository
    private val productResults = mutableListOf<ProductEntity>()
    private lateinit var adapter: ProductResultAdapter
    private var currentJanList = listOf<String>()
    private var currentJanIndex = 0
    
    companion object {
        private const val TAG = "ProductRegisterActivity"
        private const val REQUEST_CODE_PERPLEXITY = 1001
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
        currentJanList = janInput
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.length >= 8 && it.all { c -> c.isDigit() } }
        
        if (currentJanList.isEmpty()) {
            Snackbar.make(
                binding.root, 
                "8桁以上の有効なJANコードを入力してください", 
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        
        Log.d(TAG, "検索開始: ${currentJanList.size}件のJANコード")
        Log.d(TAG, "JANリスト: $currentJanList")
        
        productResults.clear()
        adapter.notifyDataSetChanged()
        currentJanIndex = 0
        processNextJan()
    }
    
    /**
     * 次のJANコードを処理
     */
    private fun processNextJan() {
        if (currentJanIndex >= currentJanList.size) {
            showLoading(false)
            binding.statusText.text = "全${currentJanList.size}件の検索完了"
            binding.resultRecyclerView.visibility = View.VISIBLE
            Log.d(TAG, "★★★ 全検索完了 ★★★")
            return
        }
        
        val jan = currentJanList[currentJanIndex]
        showLoading(true)
        binding.statusText.text = "${currentJanIndex + 1}/${currentJanList.size}: $jan を検索中..."
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "処理中: [${currentJanIndex + 1}/${currentJanList.size}] JAN=$jan")
        Log.d(TAG, "========================================")
        
        lifecycleScope.launch {
            try {
                val existingProduct = repository.getProductByJan(jan)
                
                if (existingProduct != null) {
                    Log.d(TAG, "☆ 既存商品をデータベースから取得: ${existingProduct.name}")
                    productResults.add(existingProduct)
                    adapter.notifyItemInserted(productResults.size - 1)
                    binding.resultRecyclerView.visibility = View.VISIBLE
                    currentJanIndex++
                    processNextJan()
                } else {
                    Log.d(TAG, "→ Perplexity.aiで検索します...")
                    searchWithPerplexity(jan)
                }
            } catch (e: Exception) {
                Log.e(TAG, "検索エラー: JAN=$jan", e)
                binding.statusText.text = "エラー: ${e.message}"
                currentJanIndex++
                processNextJan()
            }
        }
    }
    
    /**
     * Perplexity.aiで検索
     */
    private fun searchWithPerplexity(jan: String) {
        Log.d(TAG, "★★★ Perplexity.ai起動: JAN=$jan ★★★")
        val intent = Intent(this, PerplexityWebViewActivity::class.java)
        intent.putExtra("JAN_CODE", jan)
        startActivityForResult(intent, REQUEST_CODE_PERPLEXITY)
    }
    
    /**
     * Perplexity.aiからの結果を受け取る
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == REQUEST_CODE_PERPLEXITY) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "★ Perplexity.aiから成功結果を受信 ★")
                
                val product = ProductEntity(
                    jan = data.getStringExtra("jan") ?: "",
                    maker = data.getStringExtra("maker"),
                    name = data.getStringExtra("name") ?: "不明",
                    category = data.getStringExtra("category"),
                    minPrice = data.getIntExtra("min_price", 0).takeIf { it > 0 },
                    maxPrice = data.getIntExtra("max_price", 0).takeIf { it > 0 },
                    widthMm = data.getIntExtra("width_mm", 0).takeIf { it > 0 },
                    heightMm = data.getIntExtra("height_mm", 0).takeIf { it > 0 },
                    depthMm = data.getIntExtra("depth_mm", 0).takeIf { it > 0 },
                    priceUpdatedAt = System.currentTimeMillis()
                )
                
                Log.d(TAG, "取得した商品: $product")
                
                productResults.add(product)
                adapter.notifyItemInserted(productResults.size - 1)
                binding.resultRecyclerView.visibility = View.VISIBLE
            } else {
                Log.w(TAG, "⚠ Perplexity.aiからの結果が無いまたはキャンセルされました")
            }
            
            // 次のJANコードへ
            currentJanIndex++
            processNextJan()
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
