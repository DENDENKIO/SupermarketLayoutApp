package com.example.supermarketlayoutapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.supermarketlayoutapp.R
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ActivityProductRegisterBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProductRegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProductRegisterBinding
    private lateinit var repository: AppRepository
    private var currentProduct: ProductEntity? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)
        
        setupListeners()
    }
    
    private fun setupListeners() {
        binding.searchButton.setOnClickListener {
            val jan = binding.janEditText.text.toString()
            if (jan.length < 8) {
                Snackbar.make(binding.root, "JANコードは8桁以上入力してください", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchProduct(jan)
        }
        
        binding.saveButton.setOnClickListener {
            saveProduct()
        }
    }
    
    private fun searchProduct(jan: String) {
        showLoading(true)
        binding.statusText.text = "ローカルDBを検索中..."
        
        lifecycleScope.launch {
            try {
                // ローカルDB検索
                val existingProduct = repository.getProductByJan(jan)
                
                if (existingProduct != null) {
                    // 既存商品が見つかった
                    showLoading(false)
                    binding.statusText.text = "商品が見つかりました"
                    displayProduct(existingProduct)
                } else {
                    // 見つからない場合はPerplexity.aiで検索
                    binding.statusText.text = "AI検索を開始します..."
                    searchWithPerplexity(jan)
                }
            } catch (e: Exception) {
                showLoading(false)
                binding.statusText.text = "エラーが発生しました: ${e.message}"
            }
        }
    }
    
    private fun searchWithPerplexity(jan: String) {
        // PerplexityWebViewActivityを起動
        val intent = Intent(this, PerplexityWebViewActivity::class.java)
        intent.putExtra("JAN_CODE", jan)
        startActivityForResult(intent, REQUEST_CODE_PERPLEXITY)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_PERPLEXITY && resultCode == RESULT_OK) {
            data?.let {
                val product = ProductEntity(
                    jan = it.getStringExtra("jan") ?: "",
                    maker = it.getStringExtra("maker"),
                    name = it.getStringExtra("name") ?: "不明",
                    category = it.getStringExtra("category"),
                    minPrice = it.getIntExtra("min_price", 0).takeIf { it > 0 },
                    maxPrice = it.getIntExtra("max_price", 0).takeIf { it > 0 },
                    widthMm = it.getIntExtra("width_mm", 0).takeIf { it > 0 },
                    heightMm = it.getIntExtra("height_mm", 0).takeIf { it > 0 },
                    depthMm = it.getIntExtra("depth_mm", 0).takeIf { it > 0 },
                    priceUpdatedAt = System.currentTimeMillis()
                )
                
                showLoading(false)
                binding.statusText.text = "AI検索完了"
                displayProduct(product)
            }
        } else {
            showLoading(false)
            binding.statusText.text = "検索がキャンセルされました"
        }
    }
    
    private fun displayProduct(product: ProductEntity) {
        currentProduct = product
        binding.resultCard.visibility = View.VISIBLE
        
        binding.productNameText.text = product.name
        binding.productMakerText.text = "メーカー: ${product.maker ?: "不明"}"
        binding.productCategoryText.text = "カテゴリ: ${product.category ?: "不明"}"
        
        val priceText = if (product.minPrice != null && product.maxPrice != null) {
            "価格: ¥${product.minPrice} - ¥${product.maxPrice}"
        } else {
            "価格: 不明"
        }
        binding.productPriceText.text = priceText
        
        val sizeText = if (product.widthMm != null && product.heightMm != null && product.depthMm != null) {
            "サイズ: ${product.widthMm}mm × ${product.heightMm}mm × ${product.depthMm}mm"
        } else {
            "サイズ: 不明"
        }
        binding.productSizeText.text = sizeText
    }
    
    private fun saveProduct() {
        val product = currentProduct ?: return
        
        showLoading(true)
        binding.statusText.text = "保存中..."
        
        lifecycleScope.launch {
            try {
                repository.insertProduct(product)
                showLoading(false)
                binding.statusText.text = "保存完了"
                Snackbar.make(binding.root, "商品を保存しました", Snackbar.LENGTH_LONG).show()
                
                // 入力欄をクリア
                binding.janEditText.text?.clear()
                binding.resultCard.visibility = View.GONE
                currentProduct = null
            } catch (e: Exception) {
                showLoading(false)
                binding.statusText.text = "保存失敗: ${e.message}"
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.searchButton.isEnabled = !show
    }
    
    companion object {
        private const val REQUEST_CODE_PERPLEXITY = 1001
    }
}
