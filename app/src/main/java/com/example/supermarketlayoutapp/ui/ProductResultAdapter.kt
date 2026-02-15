package com.example.supermarketlayoutapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import com.example.supermarketlayoutapp.databinding.ItemProductResultBinding

class ProductResultAdapter(
    private val products: List<ProductEntity>,
    private val onSaveClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<ProductResultAdapter.ViewHolder>() {
    
    inner class ViewHolder(private val binding: ItemProductResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductEntity) {
            binding.productNameText.text = product.name
            binding.productJanText.text = "JAN: ${product.jan}"
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
            
            binding.saveButton.setOnClickListener {
                onSaveClick(product)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position])
    }
    
    override fun getItemCount() = products.size
}
