package com.example.supermarketlayoutapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ActivityProductJsonManagerBinding
import com.example.supermarketlayoutapp.databinding.ItemProductJsonBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 商品JSON管理画面
 * 
 * 商品データの一覧・編集・削除・JSONインポート/エクスポート機能を提供。
 */
class ProductJsonManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductJsonManagerBinding
    private lateinit var repository: AppRepository
    private lateinit var adapter: ProductListAdapter
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductJsonManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadProducts()
    }

    private fun setupToolbar() {
        // ToolbarをActionBarとして設定せず、直接ナビゲーションを設定
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProductListAdapter(
            onEditClick = { product -> editProduct(product) },
            onDeleteClick = { product -> confirmDelete(product) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnExportJson.setOnClickListener {
            exportToJson()
        }

        binding.btnImportJson.setOnClickListener {
            // TODO: ファイル選択ダイアログを追加
            Toast.makeText(this, "JSONインポートは次のバージョンで実装予定", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 商品一覧を読み込み
     */
    private fun loadProducts() {
        lifecycleScope.launch {
            val products = repository.getAllProducts().first()
            adapter.submitList(products)
            
            binding.tvProductCount.text = "商品数: ${products.size}件"
            
            if (products.isEmpty()) {
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.tvEmptyMessage.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 商品を編集
     */
    private fun editProduct(product: ProductEntity) {
        val input = android.widget.EditText(this)
        input.setText(product.name)
        
        AlertDialog.Builder(this)
            .setTitle("商品名編集")
            .setMessage("JAN: ${product.jan}")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    lifecycleScope.launch {
                        repository.updateProduct(product.copy(name = newName))
                        loadProducts()
                        Toast.makeText(this@ProductJsonManagerActivity, "保存しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * 削除確認
     */
    private fun confirmDelete(product: ProductEntity) {
        AlertDialog.Builder(this)
            .setTitle("削除確認")
            .setMessage("「${product.name}」を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteProduct(product)
                    loadProducts()
                    Toast.makeText(this@ProductJsonManagerActivity, "削除しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * JSONエクスポート
     */
    private fun exportToJson() {
        lifecycleScope.launch {
            val products = repository.getAllProducts().first()
            
            if (products.isEmpty()) {
                Toast.makeText(this@ProductJsonManagerActivity, "エクスポートする商品がありません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val jsonData = products.map { product ->
                ProductJsonData(
                    jan = product.jan,
                    maker = product.maker,
                    name = product.name,
                    category = product.category,
                    min_price = product.minPrice,
                    max_price = product.maxPrice,
                    width_cm = product.widthMm?.toDouble()?.div(10),
                    height_cm = product.heightMm?.toDouble()?.div(10),
                    depth_cm = product.depthMm?.toDouble()?.div(10)
                )
            }
            
            val jsonString = json.encodeToString(jsonData)
            
            // クリップボードにコピー
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Product JSON", jsonString)
            clipboard.setPrimaryClip(clip)
            
            // ダイアログで表示
            AlertDialog.Builder(this@ProductJsonManagerActivity)
                .setTitle("JSONエクスポート")
                .setMessage("クリップボードにコピーしました\n\n${products.size}件の商品データ")
                .setPositiveButton("OK", null)
                .show()
            
            Toast.makeText(this@ProductJsonManagerActivity, "JSONをクリップボードにコピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 商品リスト用アダプター
     */
    private class ProductListAdapter(
        private val onEditClick: (ProductEntity) -> Unit,
        private val onDeleteClick: (ProductEntity) -> Unit
    ) : RecyclerView.Adapter<ProductListAdapter.ViewHolder>() {

        private var products: List<ProductEntity> = emptyList()

        fun submitList(newProducts: List<ProductEntity>) {
            products = newProducts
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemProductJsonBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(products[position])
        }

        override fun getItemCount(): Int = products.size

        inner class ViewHolder(private val binding: ItemProductJsonBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(product: ProductEntity) {
                binding.tvProductName.text = product.name
                binding.tvProductJan.text = "JAN: ${product.jan}"
                
                val details = buildString {
                    product.maker?.let { append("メーカー: $it | ") }
                    product.category?.let { append("カテゴリ: $it | ") }
                    if (product.minPrice != null && product.maxPrice != null) {
                        append("価格: ${product.minPrice}~${product.maxPrice}円")
                    }
                }
                binding.tvProductDetails.text = details.ifEmpty { "-" }
                
                val size = buildString {
                    if (product.widthMm != null && product.heightMm != null && product.depthMm != null) {
                        append("サイズ: ${product.widthMm/10.0}x${product.heightMm/10.0}x${product.depthMm/10.0} cm")
                    } else {
                        append("サイズ: 未設定")
                    }
                }
                binding.tvProductSize.text = size

                binding.btnEdit.setOnClickListener {
                    onEditClick(product)
                }

                binding.btnDelete.setOnClickListener {
                    onDeleteClick(product)
                }
            }
        }
    }

    /**
     * JSONエクスポート用データクラス
     */
    @Serializable
    data class ProductJsonData(
        val jan: String,
        val maker: String?,
        val name: String,
        val category: String?,
        val min_price: Int?,
        val max_price: Int?,
        val width_cm: Double?,
        val height_cm: Double?,
        val depth_cm: Double?
    )
}
