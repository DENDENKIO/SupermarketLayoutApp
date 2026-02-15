package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 什器(フィクスチャー)エンティティ
 * 
 * 売場に配置される陳列什器の情報を管理します。
 * Phase 4で2Dレイアウト配置機能用に拡張されました。
 */
@Entity(tableName = "fixture")
data class FixtureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "type")
    val type: String, // GONDOLA, END, ISLAND, FREEZER, REGISTER
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "length_cm")
    val lengthCm: Float,  // 長さ(cm)
    
    @ColumnInfo(name = "width_cm")
    val widthCm: Float,   // 幅(cm)
    
    // === Phase 4: 2Dレイアウト用フィールド ===
    
    @ColumnInfo(name = "position_x")
    val positionX: Float = 0f,  // X座標(cm)
    
    @ColumnInfo(name = "position_y")
    val positionY: Float = 0f,  // Y座標(cm)
    
    @ColumnInfo(name = "rotation")
    val rotation: Float = 0f,   // 回転角度(0=横向き, 90=縦向き)
    
    @ColumnInfo(name = "color")
    val color: Int = 0xFF3F51B5.toInt(),  // 表示色(ARGB)
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 什器タイプの定数
 */
object FixtureType {
    const val GONDOLA = "GONDOLA"    // ゴンドラ(両面陳列棚)
    const val END = "END"            // エンド(棚の端)
    const val ISLAND = "ISLAND"      // アイランド(島型陳列台)
    const val FREEZER = "FREEZER"    // 冷凍冷蔵ケース
    const val REGISTER = "REGISTER"  // レジカウンター
    
    /**
     * すべての什器タイプのリスト
     */
    fun getAllTypes() = listOf(GONDOLA, END, ISLAND, FREEZER, REGISTER)
    
    /**
     * 什器タイプの表示名を取得
     */
    fun getDisplayName(type: String): String = when (type) {
        GONDOLA -> "ゴンドラ"
        END -> "エンド"
        ISLAND -> "アイランド"
        FREEZER -> "冷凍冷蔵ケース"
        REGISTER -> "レジカウンター"
        else -> "不明"
    }
    
    /**
     * 什器タイプのデフォルトサイズを取得(cm)
     */
    fun getDefaultSize(type: String): Pair<Float, Float> = when (type) {
        GONDOLA -> Pair(120f, 60f)   // 長さ120cm x 幅60cm
        END -> Pair(90f, 45f)        // 長さ90cm x 幅45cm
        ISLAND -> Pair(150f, 100f)   // 長さ150cm x 幅100cm
        FREEZER -> Pair(180f, 80f)   // 長さ180cm x 幅80cm
        REGISTER -> Pair(100f, 70f)  // 長さ100cm x 幅70cm
        else -> Pair(100f, 50f)
    }
    
    /**
     * 什器タイプのデフォルト色を取得(ARGB)
     */
    fun getDefaultColor(type: String): Int = when (type) {
        GONDOLA -> 0xFF3F51B5.toInt()   // Indigo
        END -> 0xFFE91E63.toInt()       // Pink
        ISLAND -> 0xFF4CAF50.toInt()    // Green
        FREEZER -> 0xFF00BCD4.toInt()   // Cyan
        REGISTER -> 0xFFFF9800.toInt()  // Orange
        else -> 0xFF9E9E9E.toInt()      // Gray
    }
}
