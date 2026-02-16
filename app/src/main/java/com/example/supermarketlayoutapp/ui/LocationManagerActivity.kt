package com.example.supermarketlayoutapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supermarketlayoutapp.R
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.LocationEntity
import com.example.supermarketlayoutapp.databinding.ActivityLocationManagerBinding
import com.example.supermarketlayoutapp.databinding.ItemLocationBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 陳列場所管理画面
 * 
 * 場所の一覧・追加・編集・削除を行います。
 */
class LocationManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationManagerBinding
    private lateinit var repository: AppRepository
    private lateinit var adapter: LocationListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadLocations()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_location_manager, menu)
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

    private fun setupRecyclerView() {
        adapter = LocationListAdapter(
            onItemClick = { location -> openPlanogram(location) },
            onEditClick = { location -> showEditDialog(location) },
            onDeleteClick = { location -> confirmDelete(location) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddLocation.setOnClickListener {
            showAddDialog()
        }
    }

    /**
     * 場所一覧を読み込み
     */
    private fun loadLocations() {
        lifecycleScope.launch {
            val locations = repository.getAllLocations().first()
            adapter.submitList(locations)
        }
    }

    /**
     * 場所追加ダイアログ
     */
    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_location, null)
        val etName = dialogView.findViewById<EditText>(R.id.etLocationName)
        val etWidth = dialogView.findViewById<EditText>(R.id.etShelfWidth)
        val etHeight = dialogView.findViewById<EditText>(R.id.etShelfHeight)
        val etLevels = dialogView.findViewById<EditText>(R.id.etShelfLevels)

        AlertDialog.Builder(this)
            .setTitle("場所追加")
            .setView(dialogView)
            .setPositiveButton("追加") { _, _ ->
                val name = etName.text.toString()
                val width = etWidth.text.toString().toFloatOrNull()
                val height = etHeight.text.toString().toFloatOrNull()
                val levels = etLevels.text.toString().toIntOrNull()

                if (name.isNotBlank() && width != null && height != null && levels != null) {
                    lifecycleScope.launch {
                        val location = LocationEntity(
                            name = name,
                            shelfWidth = width,
                            shelfHeight = height,
                            shelfLevels = levels
                        )
                        repository.insertLocation(location)
                        loadLocations()
                        Toast.makeText(this@LocationManagerActivity, "追加しました", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "すべての項目を入力してください", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * 場所編集ダイアログ
     */
    private fun showEditDialog(location: LocationEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_location, null)
        val etName = dialogView.findViewById<EditText>(R.id.etLocationName)
        val etWidth = dialogView.findViewById<EditText>(R.id.etShelfWidth)
        val etHeight = dialogView.findViewById<EditText>(R.id.etShelfHeight)
        val etLevels = dialogView.findViewById<EditText>(R.id.etShelfLevels)

        etName.setText(location.name)
        etWidth.setText(location.shelfWidth.toString())
        etHeight.setText(location.shelfHeight.toString())
        etLevels.setText(location.shelfLevels.toString())

        AlertDialog.Builder(this)
            .setTitle("場所編集")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString()
                val width = etWidth.text.toString().toFloatOrNull()
                val height = etHeight.text.toString().toFloatOrNull()
                val levels = etLevels.text.toString().toIntOrNull()

                if (name.isNotBlank() && width != null && height != null && levels != null) {
                    lifecycleScope.launch {
                        val updated = location.copy(
                            name = name,
                            shelfWidth = width,
                            shelfHeight = height,
                            shelfLevels = levels
                        )
                        repository.updateLocation(updated)
                        loadLocations()
                        Toast.makeText(this@LocationManagerActivity, "更新しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * 削除確認
     */
    private fun confirmDelete(location: LocationEntity) {
        AlertDialog.Builder(this)
            .setTitle("削除確認")
            .setMessage("「${location.name}」を削除しますか？\n陳列商品もすべて削除されます。")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteLocation(location)
                    loadLocations()
                    Toast.makeText(this@LocationManagerActivity, "削除しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /**
     * 棚割り画面を開く
     */
    private fun openPlanogram(location: LocationEntity) {
        val intent = Intent(this, PlanogramActivity::class.java)
        intent.putExtra("location_id", location.id)
        intent.putExtra("location_name", location.name)
        startActivity(intent)
    }

    /**
     * 場所リスト用アダプター
     */
    private class LocationListAdapter(
        private val onItemClick: (LocationEntity) -> Unit,
        private val onEditClick: (LocationEntity) -> Unit,
        private val onDeleteClick: (LocationEntity) -> Unit
    ) : RecyclerView.Adapter<LocationListAdapter.ViewHolder>() {

        private var locations: List<LocationEntity> = emptyList()

        fun submitList(newLocations: List<LocationEntity>) {
            locations = newLocations
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLocationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(locations[position])
        }

        override fun getItemCount(): Int = locations.size

        inner class ViewHolder(private val itemBinding: ItemLocationBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(location: LocationEntity) {
                itemBinding.tvLocationName.text = location.name
                itemBinding.tvLocationDetails.text = "幅: ${location.shelfWidth}cm | 高さ: ${location.shelfHeight}cm | 段数: ${location.shelfLevels}"

                itemBinding.root.setOnClickListener {
                    onItemClick(location)
                }

                itemBinding.btnEdit.setOnClickListener {
                    onEditClick(location)
                }

                itemBinding.btnDelete.setOnClickListener {
                    onDeleteClick(location)
                }
            }
        }
    }
}
