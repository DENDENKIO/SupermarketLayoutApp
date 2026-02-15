package com.example.supermarketlayoutapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.supermarketlayoutapp.data.entity.FixtureEntity
import kotlin.math.roundToInt

/**
 * 2D売場レイアウトキャンバスビュー
 * 
 * 什器をドラッグ&ドロップで配置し、レイアウトを設計します。
 */
class LayoutCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // === 什器リスト ===
    private val fixtures = mutableListOf<FixtureEntity>()
    private var selectedFixture: FixtureEntity? = null
    
    // === ペイント ===
    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    
    private val fixturePaint = Paint().apply {
        style = Paint.Style.FILL
    }
    
    private val fixtureStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.BLACK
    }
    
    private val selectedStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.RED
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    // === グリッド設定 ===
    private var gridSize = 50f  // 50cm単位
    private var cmToPixel = 2f  // 1cm = 2pixel
    
    // === ズームとパン ===
    private var scaleFactor = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f
    private val scaleDetector: ScaleGestureDetector
    
    // === ドラッグ操作 ===
    private var isDragging = false
    private var draggedFixture: FixtureEntity? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    
    // === リスナー ===
    var onFixtureSelectedListener: ((FixtureEntity?) -> Unit)? = null
    var onFixtureMovedListener: ((FixtureEntity) -> Unit)? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        
        // グリッド描画
        drawGrid(canvas)
        
        // 什器描画
        for (fixture in fixtures) {
            drawFixture(canvas, fixture)
        }
        
        canvas.restore()
    }

    /**
     * グリッドを描画
     */
    private fun drawGrid(canvas: Canvas) {
        val pixelGridSize = gridSize * cmToPixel
        
        // 縦線
        var x = 0f
        while (x < width / scaleFactor) {
            canvas.drawLine(x, 0f, x, height / scaleFactor, gridPaint)
            x += pixelGridSize
        }
        
        // 横線
        var y = 0f
        while (y < height / scaleFactor) {
            canvas.drawLine(0f, y, width / scaleFactor, y, gridPaint)
            y += pixelGridSize
        }
    }

    /**
     * 什器を描画
     */
    private fun drawFixture(canvas: Canvas, fixture: FixtureEntity) {
        canvas.save()
        
        // 座標と回転を適用
        val centerX = fixture.positionX * cmToPixel
        val centerY = fixture.positionY * cmToPixel
        canvas.translate(centerX, centerY)
        canvas.rotate(fixture.rotation)
        
        // 什器の矩形を計算
        val width = fixture.lengthCm * cmToPixel
        val height = fixture.widthCm * cmToPixel
        val rect = RectF(-width / 2, -height / 2, width / 2, height / 2)
        
        // 填りつぶし
        fixturePaint.color = fixture.color
        canvas.drawRect(rect, fixturePaint)
        
        // 枠線
        val strokePaint = if (fixture == selectedFixture) selectedStrokePaint else fixtureStrokePaint
        canvas.drawRect(rect, strokePaint)
        
        // テキスト(什器名)
        canvas.drawText(
            fixture.name,
            0f,
            textPaint.textSize / 2 - 8f,
            textPaint
        )
        
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ピンチズーム処理（マルチタッチ時）
        if (event.pointerCount > 1) {
            scaleDetector.onTouchEvent(event)
            return true
        }
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragStartX = event.x
                dragStartY = event.y
                
                // タップされた什器を検出
                val canvasX = (event.x - offsetX) / scaleFactor
                val canvasY = (event.y - offsetY) / scaleFactor
                
                val tappedFixture = findFixtureAt(canvasX, canvasY)
                
                if (tappedFixture != null) {
                    // 什器を選択
                    draggedFixture = tappedFixture
                    selectedFixture = tappedFixture
                    isDragging = true
                    onFixtureSelectedListener?.invoke(selectedFixture)
                    invalidate()
                } else {
                    // 空白をタップ = 選択解除
                    selectedFixture = null
                    draggedFixture = null
                    onFixtureSelectedListener?.invoke(null)
                    invalidate()
                }
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                
                // 移動距離が小さい場合はタップとみなす
                val totalDx = event.x - dragStartX
                val totalDy = event.y - dragStartY
                val distance = Math.sqrt((totalDx * totalDx + totalDy * totalDy).toDouble())
                
                if (distance < 10) {
                    // タップとみなす
                    return true
                }
                
                if (isDragging && draggedFixture != null) {
                    // 什器をドラッグ
                    val fixture = draggedFixture!!
                    
                    // スクリーン座標の移動量をキャンバス座標に変換
                    val canvasDx = dx / scaleFactor / cmToPixel
                    val canvasDy = dy / scaleFactor / cmToPixel
                    
                    val newX = fixture.positionX + canvasDx
                    val newY = fixture.positionY + canvasDy
                    
                    // グリッドスナップ
                    val snappedX = snapToGrid(newX)
                    val snappedY = snapToGrid(newY)
                    
                    // 什器の位置を更新
                    val updatedFixture = fixture.copy(
                        positionX = snappedX,
                        positionY = snappedY
                    )
                    
                    draggedFixture = updatedFixture
                    updateFixture(updatedFixture)
                    invalidate()
                }
                
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && draggedFixture != null) {
                    // ドラッグ終了を通知
                    onFixtureMovedListener?.invoke(draggedFixture!!)
                }
                
                isDragging = false
                draggedFixture = null
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }

    /**
     * 指定座標にある什器を検出
     */
    private fun findFixtureAt(canvasX: Float, canvasY: Float): FixtureEntity? {
        // 後ろから検索(前面の什器を優先)
        for (i in fixtures.size - 1 downTo 0) {
            val fixture = fixtures[i]
            
            // 回転を考慮した当たり判定
            val dx = (canvasX / cmToPixel) - fixture.positionX
            val dy = (canvasY / cmToPixel) - fixture.positionY
            
            // 簡易的な矩形当たり判定(回転は考慮しない)
            val halfWidth = fixture.lengthCm / 2
            val halfHeight = fixture.widthCm / 2
            
            if (dx >= -halfWidth && dx <= halfWidth && 
                dy >= -halfHeight && dy <= halfHeight) {
                return fixture
            }
        }
        
        return null
    }

    /**
     * グリッドにスナップ
     */
    private fun snapToGrid(value: Float): Float {
        return (value / gridSize).roundToInt() * gridSize
    }

    /**
     * 什器リストを設定
     */
    fun setFixtures(fixtureList: List<FixtureEntity>) {
        fixtures.clear()
        fixtures.addAll(fixtureList)
        invalidate()
    }

    /**
     * 什器を追加
     */
    fun addFixture(fixture: FixtureEntity) {
        fixtures.add(fixture)
        invalidate()
    }

    /**
     * 什器を更新
     */
    fun updateFixture(updatedFixture: FixtureEntity) {
        val index = fixtures.indexOfFirst { it.id == updatedFixture.id }
        if (index != -1) {
            fixtures[index] = updatedFixture
            if (selectedFixture?.id == updatedFixture.id) {
                selectedFixture = updatedFixture
            }
            if (draggedFixture?.id == updatedFixture.id) {
                draggedFixture = updatedFixture
            }
        }
    }

    /**
     * 選択中の什器を削除
     */
    fun deleteSelectedFixture(): FixtureEntity? {
        val fixture = selectedFixture
        if (fixture != null) {
            fixtures.remove(fixture)
            selectedFixture = null
            draggedFixture = null
            invalidate()
            onFixtureSelectedListener?.invoke(null)
        }
        return fixture
    }

    /**
     * 選択中の什器を回転
     */
    fun rotateSelectedFixture(degrees: Float) {
        selectedFixture?.let { fixture ->
            val rotated = fixture.copy(rotation = (fixture.rotation + degrees) % 360)
            updateFixture(rotated)
            selectedFixture = rotated
            invalidate()
            onFixtureMovedListener?.invoke(rotated)
        }
    }

    /**
     * ズームレベルをリセット
     */
    fun resetZoom() {
        scaleFactor = 1.0f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    /**
     * スケールジェスチャーリスナー
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)  // 0.5x ~ 3.0x
            invalidate()
            return true
        }
    }
}
