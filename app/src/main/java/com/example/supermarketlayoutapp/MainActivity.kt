package com.example.supermarketlayoutapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivityMainBinding
import com.example.supermarketlayoutapp.ui.LocationManagerActivity
import com.example.supermarketlayoutapp.ui.ProductJsonManagerActivity
import com.example.supermarketlayoutapp.ui.ProductRegisterActivity
import com.example.supermarketlayoutapp.ui.SettingsActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupButtons()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupButtons() {
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
