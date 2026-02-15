package com.example.supermarketlayoutapp.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.supermarketlayoutapp.data.AppDatabase
import com.example.supermarketlayoutapp.data.AppRepository
import com.example.supermarketlayoutapp.data.entity.FixtureEntity
import com.example.supermarketlayoutapp.data.entity.FixtureType
import com.example.supermarketlayoutapp.databinding.ActivityLayoutEditorBinding
import kotlinx.coroutines.launch

/**
 * 2D売場レイアウトエディター Activity
 * 
 * 什器を配置して売場のレイアウトを設計します。
 */
class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLayoutEditorBinding
    private lateinit var repository: AppRepository
    private var nextFixtureCounter = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Repository初期化
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(
            database.productDao(),
            database.fixtureDao(),
            database.shelfDao(),
            database.facingDao()
        )

        setupToolbar()
        setupCanvas()
        setupFixtureButtons()
        loadFixtures()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupCanvas() {
        // 什器が選択されたときのリスナー
        binding.canvasView.onFixtureSelectedListener = { fixture ->
            if (fixture != null) {
                showPropertiesPanel(fixture)
            } else {
                hidePropertiesPanel()
            }
        }

        // 什器が移動されたときのリスナー
        binding.canvasView.onFixtureMovedListener = { fixture ->
            updateFixture(fixture)
        }
    }

    private fun setupFixtureButtons() {
        binding.btnAddGondola.setOnClickListener {
            addFixture(FixtureType.GONDOLA)
        }

        binding.btnAddEnd.setOnClickListener {
            addFixture(FixtureType.END)
        }

        binding.btnAddIsland.setOnClickListener {
            addFixture(FixtureType.ISLAND)
        }

        binding.btnAddFreezer.setOnClickListener {
            addFixture(FixtureType.FREEZER)
        }

        binding.btnAddRegister.setOnClickListener {
            addFixture(FixtureType.REGISTER)
        }

        // プロパティパネルのボタン
        binding.btnRotateLeft.setOnClickListener {
            binding.canvasView.rotateSelectedFixture(-90f)
        }

        binding.btnRotateRight.setOnClickListener {
            binding.canvasView.rotateSelectedFixture(90f)
        }

        binding.btnDeleteFixture.setOnClickListener {
            val deleted = binding.canvasView.deleteSelectedFixture()
            if (deleted != null) {
                deleteFixture(deleted)
            }
        }
    }

    /**
     * 什器を追加
     */
    private fun addFixture(type: String) {
        val (length, width) = FixtureType.getDefaultSize(type)
        val color = FixtureType.getDefaultColor(type)
        val displayName = FixtureType.getDisplayName(type)
        
        val fixture = FixtureEntity(
            type = type,
            name = "$displayName $nextFixtureCounter",
            lengthCm = length,
            widthCm = width,
            positionX = 200f,  // 中央付近に配置
            positionY = 200f,
            rotation = 0f,
            color = color
        )
        
        nextFixtureCounter++
        
        lifecycleScope.launch {
            val id = repository.insertFixture(fixture)
            val inserted = fixture.copy(id = id)
            binding.canvasView.addFixture(inserted)
        }
    }

    /**
     * 什器を更新
     */
    private fun updateFixture(fixture: FixtureEntity) {
        lifecycleScope.launch {
            repository.updateFixture(fixture)
            updatePropertiesPanel(fixture)
        }
    }

    /**
     * 什器を削除
     */
    private fun deleteFixture(fixture: FixtureEntity) {
        lifecycleScope.launch {
            repository.deleteFixture(fixture)
        }
    }

    /**
     * 保存済み什器を読み込み
     */
    private fun loadFixtures() {
        lifecycleScope.launch {
            val fixtures = repository.getAllFixtures()
            binding.canvasView.setFixtures(fixtures)
            
            // 次の什器番号を設定
            if (fixtures.isNotEmpty()) {
                nextFixtureCounter = fixtures.size + 1
            }
        }
    }

    /**
     * プロパティパネルを表示
     */
    private fun showPropertiesPanel(fixture: FixtureEntity) {
        binding.propertiesPanel.visibility = View.VISIBLE
        updatePropertiesPanel(fixture)
    }

    /**
     * プロパティパネルを更新
     */
    private fun updatePropertiesPanel(fixture: FixtureEntity) {
        binding.tvSelectedFixtureName.text = fixture.name
        binding.tvSelectedFixtureInfo.text = 
            "${fixture.lengthCm.toInt()}cm x ${fixture.widthCm.toInt()}cm | " +
            "位置: (${fixture.positionX.toInt()}, ${fixture.positionY.toInt()}) | " +
            "回転: ${fixture.rotation.toInt()}°"
    }

    /**
     * プロパティパネルを隠す
     */
    private fun hidePropertiesPanel() {
        binding.propertiesPanel.visibility = View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
