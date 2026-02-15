package com.example.supermarketlayoutapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supermarketlayoutapp.BuildConfig
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.dao.DisplayProductWithProduct
import com.example.supermarketlayoutapp.data.entity.DisplayProductEntity
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ActivityPlanogramBinding
import com.example.supermarketlayoutapp.databinding.ItemDisplayProductBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 棚割り（プラノグラム）画面
 * 
 * 特定の場所に陳列する商品を管理し、AIで棚割りを生成します。
 */
class PlanogramActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlanogramBinding
    private lateinit var repository: AppRepository
    private lateinit var adapter: DisplayProductListAdapter
    private var locationId: Long = 0
    private var locationName: String = ""
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanogramBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationId = intent.getLongExtra("location_id", 0)
        locationName = intent.getStringExtra("location_name") ?: ""

        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadDisplayProducts()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "$locationName - 棚割り"
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = DisplayProductListAdapter(
            onDeleteClick = { displayProduct -> confirmDelete(displayProduct) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddProduct.setOnClickListener {
            showProductSelectionDialog()
        }
        
        binding.btnGeneratePlanogram.setOnClickListener {
            generatePlanogramWithAI()
        }
    }

    /**
     * 陳列商品一覧を読み込み
     */
    private fun loadDisplayProducts() {
        lifecycleScope.launch {
            val displayProducts = repository.getDisplayProductsWithProductByLocation(locationId).first()
            adapter.submitList(displayProducts)
            
            if (displayProducts.isEmpty()) {
                binding.tvEmptyMessage.visibility = android.view.View.VISIBLE
                binding.recyclerView.visibility = android.view.View.GONE
            } else {
                binding.tvEmptyMessage.visibility = android.view.View.GONE
                binding.recyclerView.visibility = android.view.View.VISIBLE
            }
        }
    }

    /**
     * 商品選択ダイアログ
     */
    private fun showProductSelectionDialog() {
        lifecycleScope.launch {
            val products = repository.getAllProducts().first()
            
            if (products.isEmpty()) {
                Toast.makeText(this@PlanogramActivity, "商品マスターに商品が登録されていません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val productNames = products.map { it.name }.toTypedArray()
            
            AlertDialog.Builder(this@PlanogramActivity)
                .setTitle("商品を選択")
                .setItems(productNames) { _, which ->
                    showQuantityDialog(products[which])
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
    }

    /**
     * 数量入力ダイアログ
     */
    private fun showQuantityDialog(product: ProductEntity) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "陳列数量"
        
        AlertDialog.Builder(this)
            .setTitle("「${product.name}」の陳列数量")
            .setView(input)
            .setPositiveButton("追加") { _, _ ->
                val quantity = input.text.toString().toIntOrNull()
                if (quantity != null && quantity > 0) {
                    addDisplayProduct(product, quantity)
                } else {
                    Toast.makeText(this, "正しい数量を入力してください", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * 陳列商品を追加
     */
    private fun addDisplayProduct(product: ProductEntity, quantity: Int) {
        lifecycleScope.launch {
            val displayProduct = DisplayProductEntity(
                locationId = locationId,
                productId = product.id,
                quantity = quantity,
                facings = 1
            )
            repository.insertDisplayProduct(displayProduct)
            loadDisplayProducts()
            Toast.makeText(this@PlanogramActivity, "追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 削除確認
     */
    private fun confirmDelete(displayProductWithProduct: DisplayProductWithProduct) {
        AlertDialog.Builder(this)
            .setTitle("削除確認")
            .setMessage("「${displayProductWithProduct.product.name}」を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteDisplayProduct(displayProductWithProduct.displayProduct)
                    loadDisplayProducts()
                    Toast.makeText(this@PlanogramActivity, "削除しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * AIで棚割りを生成
     */
    private fun generatePlanogramWithAI() {
        lifecycleScope.launch {
            val displayProducts = repository.getDisplayProductsWithProductByLocation(locationId).first()
            
            if (displayProducts.isEmpty()) {
                Toast.makeText(this@PlanogramActivity, "陳列商品がありません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val location = repository.getLocationById(locationId)
            if (location == null) {
                Toast.makeText(this@PlanogramActivity, "場所情報が見つかりません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            binding.btnGeneratePlanogram.isEnabled = false
            binding.btnGeneratePlanogram.text = "AI生成中..."
            
            try {
                val prompt = buildPlanogramPrompt(location.shelfWidth, location.shelfHeight, location.shelfLevels, displayProducts)
                val aiResponse = callPerplexityAPI(prompt)
                
                // AIのレスポンスをパースして棚割りを更新
                parsePlanogramResponse(aiResponse, displayProducts)
                
                loadDisplayProducts()
                Toast.makeText(this@PlanogramActivity, "棚割りを生成しました！", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PlanogramActivity, "エラー: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnGeneratePlanogram.isEnabled = true
                binding.btnGeneratePlanogram.text = "AI棚割り生成"
            }
        }
    }

    /**
     * 棚割りプロンプトを構築
     */
    private fun buildPlanogramPrompt(
        shelfWidth: Float,
        shelfHeight: Float,
        shelfLevels: Int,
        displayProducts: List<DisplayProductWithProduct>
    ): String {
        val productsInfo = displayProducts.joinToString("\n") { dp ->
            val p = dp.product
            "- ${p.name}: 幅${p.widthMm?.div(10) ?: "?"}cm, 高さ${p.heightMm?.div(10) ?: "?"}cm, 奥行${p.depthMm?.div(10) ?: "?"}cm, 数量${dp.displayProduct.quantity}"
        }
        
        return """
以下の棚に商品を配置する棚割り（プラノグラム）を考えてください。

棚のサイズ：
- 幅: ${shelfWidth}cm
- 高さ: ${shelfHeight}cm
- 段数: $shelfLevels

陳列商品：
$productsInfo

条件：
1. 各商品のサイズを考慮して配置してください
2. 重い商品は下段に配置してください
3. 人気商品や目立たせたい商品は目の高さに配置してください
4. 棚の幅を超えないようにしてください

JSON形式で各商品の配置を返してください：
{
  "placements": [
    {
      "product_name": "商品名",
      "level": 0,  // 0が上段、下にいくほど数値が大きくなる
      "position_x": 10.0,  // 左端からの距離(cm)
      "facings": 2  // 横に並べる数
    }
  ]
}
        """.trimIndent()
    }

    /**
     * Perplexity APIを呼び出す
     */
    private suspend fun callPerplexityAPI(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.PERPLEXITY_API_KEY
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val requestBody = """
        {
            "model": "llama-3.1-sonar-small-128k-online",
            "messages": [
                {
                    "role": "system",
                    "content": "あなたは棚割り（プラノグラム）の専門家です。JSON形式で回答してください。"
                },
                {
                    "role": "user",
                    "content": "$prompt"
                }
            ]
        }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("https://api.perplexity.ai/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        
        if (!response.isSuccessful) {
            throw Exception("API Error: ${response.code} - $responseBody")
        }
        
        // レスポンスからコンテンツを抽出
        val jsonResponse = json.decodeFromString<PerplexityResponse>(responseBody)
        jsonResponse.choices.firstOrNull()?.message?.content ?: throw Exception("No content in response")
    }

    /**
     * AIレスポンスをパースして棚割りを更新
     */
    private suspend fun parsePlanogramResponse(
        aiResponse: String,
        displayProducts: List<DisplayProductWithProduct>
    ) {
        try {
            // JSON部分を抽出
            val jsonStart = aiResponse.indexOf("{")
            val jsonEnd = aiResponse.lastIndexOf("}") + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                throw Exception("JSON not found in response")
            }
            
            val jsonString = aiResponse.substring(jsonStart, jsonEnd)
            val planogramData = json.decodeFromString<PlanogramData>(jsonString)
            
            // 各商品の配置を更新
            planogramData.placements.forEach { placement ->
                val matchingProduct = displayProducts.find { 
                    it.product.name == placement.product_name 
                }
                
                matchingProduct?.let { dpWithProduct ->
                    val updated = dpWithProduct.displayProduct.copy(
                        level = placement.level,
                        positionX = placement.position_x,
                        facings = placement.facings
                    )
                    repository.updateDisplayProduct(updated)
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse AI response: ${e.message}")
        }
    }

    /**
     * 陳列商品リスト用アダプター
     */
    private class DisplayProductListAdapter(
        private val onDeleteClick: (DisplayProductWithProduct) -> Unit
    ) : RecyclerView.Adapter<DisplayProductListAdapter.ViewHolder>() {

        private var displayProducts: List<DisplayProductWithProduct> = emptyList()

        fun submitList(newProducts: List<DisplayProductWithProduct>) {
            displayProducts = newProducts
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDisplayProductBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(displayProducts[position])
        }

        override fun getItemCount(): Int = displayProducts.size

        inner class ViewHolder(private val itemBinding: ItemDisplayProductBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(dpWithProduct: DisplayProductWithProduct) {
                val dp = dpWithProduct.displayProduct
                val product = dpWithProduct.product
                
                itemBinding.tvProductName.text = product.name
                itemBinding.tvQuantity.text = "数量: ${dp.quantity}"
                
                val positionText = if (dp.level != null && dp.positionX != null) {
                    "段: ${dp.level}, X: ${dp.positionX}cm, フェイス: ${dp.facings}"
                } else {
                    "未配置"
                }
                itemBinding.tvPosition.text = positionText

                itemBinding.btnDelete.setOnClickListener {
                    onDeleteClick(dpWithProduct)
                }
            }
        }
    }

    // === APIレスポンスデータクラス ===
    
    @Serializable
    data class PerplexityResponse(
        val choices: List<Choice>
    )
    
    @Serializable
    data class Choice(
        val message: Message
    )
    
    @Serializable
    data class Message(
        val content: String
    )
    
    @Serializable
    data class PlanogramData(
        val placements: List<ProductPlacement>
    )
    
    @Serializable
    data class ProductPlacement(
        val product_name: String,
        val level: Int,
        val position_x: Float,
        val facings: Int
    )
}
