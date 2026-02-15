package com.example.supermarketlayoutapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.supermarketlayoutapp.databinding.ActivityMainBinding
import com.example.supermarketlayoutapp.ui.ProductRegisterActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnProductRegister.setOnClickListener {
            startActivity(Intent(this, ProductRegisterActivity::class.java))
        }
    }
}
