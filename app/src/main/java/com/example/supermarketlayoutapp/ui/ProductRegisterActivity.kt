package com.example.supermarketlayoutapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ActivityProductRegisterBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProductRegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProductRegisterBinding
    private lateinit var repository: AppRepository
    private val productResults = mutableListOf<ProductEntity>()
    private lateinit var adapter: ProductResultAdapter
    private var currentJanList = listOf<String>()
    private var currentJanIndex = 0
    
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
        binding.searchButton.setOnClickListener {
            val janInput = binding.janEditText.text.toString().trim()
            if (janInput.isEmpty()) {
                Snackbar.make(binding.root, "JANコードを入力してください", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            currentJanList = janInput.split(",").map { it.trim() }.filter { it.length >= 8 }
            
            if (currentJanList.isEmpty()) {
                Snackbar.make(binding.root, "8桁以上のJANコードを入力してください", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            productResults.clear()
            adapter.notifyDataSetChanged()
            currentJanIndex = 0
            processNextJan()
        }
    }
    
    private fun processNextJan() {
        if (currentJanIndex >= currentJanList.size) {
            showLoading(false)
            binding.statusText.text = "全${currentJanList.size}件の検索完了"
            binding.resultRecyclerView.visibility = View.VISIBLE
            return
        }
        
        val jan = currentJanList[currentJanIndex]
        showLoading(true)
        binding.statusText.text = "${currentJanIndex + 1}/${currentJanList.size}: $jan を検索中..."
        
        lifecycleScope.launch {
            try {
                val existingProduct = repository.getProductByJan(jan)
                
                if (existingProduct != null) {
                    productResults.add(existingProduct)
                    adapter.notifyItemInserted(productResults.size - 1)
                    binding.resultRecyclerView.visibility = View.VISIBLE
                    currentJanIndex++
                    processNextJan()
                } else {
                    searchWithPerplexity(jan)
                }
            } catch (e: Exception) {
                binding.statusText.text = "エラー: ${e.message}"
                currentJanIndex++
                processNextJan()
            }
        }
    }
    
    private fun searchWithPerplexity(jan: String) {
        val intent = Intent(this, PerplexityWebViewActivity::class.java)
        intent.putExtra("JAN_CODE", jan)
        startActivityForResult(intent, REQUEST_CODE_PERPLEXITY)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_PERPLEXITY) {
            if (resultCode == RESULT_OK && data != null) {
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
                
                productResults.add(product)
                adapter.notifyItemInserted(productResults.size - 1)
                binding.resultRecyclerView.visibility = View.VISIBLE
            }
            
            currentJanIndex++
            processNextJan()
        }
    }
    
    private fun saveProduct(product: ProductEntity) {
        lifecycleScope.launch {
            try {
                repository.insertProduct(product)
                Snackbar.make(binding.root, "${product.name} を保存しました", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "保存失敗: ${e.message}", Snackbar.LENGTH_LONG).show()
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
