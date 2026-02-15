package com.example.supermarketlayoutapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivityMainBinding
import com.example.supermarketlayoutapp.ui.LocationManagerActivity
import com.example.supermarketlayoutapp.ui.ProductJsonManagerActivity
import com.example.supermarketlayoutapp.ui.ProductRegisterActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 商品マスター登録
        binding.btnProductRegister.setOnClickListener {
            startActivity(Intent(this, ProductRegisterActivity::class.java))
        }
        
        // 商品JSON管理
        binding.btnProductJsonManager.setOnClickListener {
            startActivity(Intent(this, ProductJsonManagerActivity::class.java))
        }
        
        // 棚割り管理（新機能）
        binding.btnPlanogramManager.setOnClickListener {
            startActivity(Intent(this, LocationManagerActivity::class.java))
        }
    }
}
