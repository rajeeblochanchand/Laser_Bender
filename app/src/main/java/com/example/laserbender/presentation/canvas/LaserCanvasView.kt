package com.example.laserbender.presentation.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.laserbender.data.model.Flag
import com.example.laserbender.data.model.LightSource
import com.example.laserbender.data.model.Mirror
import com.example.laserbender.utils.RayTracer
import kotlin.math.atan2
import kotlin.math.sqrt

class LaserCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lights = mutableListOf<LightSource>()
    private val mirrors = mutableListOf<Mirror>()
    private val flags = mutableListOf<Flag>()

    private var selectedLight: LightSource? = null
    private var selectedMirror: Mirror? = null
    private var selectedFlag: Flag? = null

    private var isDragging = false
    private var isRotating = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var onSelectionChanged: ((Boolean, Boolean) -> Unit)? = null

    // Paint objects
    private val laserPaint = Paint().apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val mirrorPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val mirrorBackPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val flagPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val flagBackPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val gizmoPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gizmoFillPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val iconPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    init {
        setBackgroundColor(Color.parseColor("#1a1a2e"))
    }

    fun setOnSelectionChangedListener(listener: (Boolean, Boolean) -> Unit) {
        onSelectionChanged = listener
    }

    fun addLight() {
        val pos = findNextAvailablePosition(PointF(width / 2f, height / 2f))
        lights.add(LightSource(position = pos))
        invalidate()
    }

    fun addMirror() {
        val pos = findNextAvailablePosition(PointF(width / 2f, height / 2f))
        mirrors.add(Mirror(position = pos))
        invalidate()
    }

    fun addFlag() {
        val pos = findNextAvailablePosition(PointF(width / 2f, height / 2f))
        flags.add(Flag(position = pos))
        invalidate()
    }

    private fun findNextAvailablePosition(initialPosition: PointF): PointF {
        var position = initialPosition
        val offset = 60f // The radius of the gizmo
        var counter = 0
        while (isPositionOccupied(position) && counter < 100) {
            position = PointF(position.x + offset, position.y)
            counter++
        }
        return position
    }

    private fun isPositionOccupied(position: PointF): Boolean {
        val allObjects = lights.map { it.position } + mirrors.map { it.position } + flags.map { it.position }
        return allObjects.any { it.x == position.x && it.y == position.y }
    }

    fun deleteSelected() {
        selectedLight?.let { lights.remove(it) }
        selectedMirror?.let { mirrors.remove(it) }
        selectedFlag?.let { flags.remove(it) }
        clearSelection()
        invalidate()
    }

    fun changeSelectedLightColor(color: Int) {
        selectedLight?.let {
            it.color = color
            invalidate()
        }
    }

    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    private fun clearSelection() {
        lights.forEach { it.isSelected = false }
        mirrors.forEach { it.isSelected = false }
        flags.forEach { it.isSelected = false }
        selectedLight = null
        selectedMirror = null
        selectedFlag = null
        onSelectionChanged?.invoke(false, false)
    }

    private fun notifySelectionChanged() {
        val hasSelection = selectedLight != null || selectedMirror != null || selectedFlag != null
        val isLightSource = selectedLight != null
        onSelectionChanged?.invoke(hasSelection, isLightSource)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw laser beams first
        for (light in lights) {
            val segments = RayTracer.traceLightRay(
                light, mirrors, flags, width.toFloat(), height.toFloat()
            )
            for (segment in segments) {
                laserPaint.color = segment.color
                canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, laserPaint)
            }
        }

        // Draw mirrors
        for (mirror in mirrors) {
            val (start, end) = mirror.getEndpoints()
            val normal = mirror.getNormal()
            val offset = 4f

            // Draw the back (white) line first, offset in the direction of the normal
            canvas.drawLine(start.x + normal.x * offset, start.y + normal.y * offset, end.x + normal.x * offset, end.y + normal.y * offset, mirrorBackPaint)
            
            // Draw the front (cyan) reflective line
            canvas.drawLine(start.x, start.y, end.x, end.y, mirrorPaint)

            if (mirror.isSelected) {
                drawGizmo(canvas, mirror.position, 60f, mirror.angle)
            }
        }

        // Draw flags
        for (flag in flags) {
            val (start, end) = flag.getEndpoints()
            val normal = flag.getNormal()
            val offset = 4f

            // Draw the back (white) line first, offset in the direction of the normal
            canvas.drawLine(start.x + normal.x * offset, start.y + normal.y * offset, end.x + normal.x * offset, end.y + normal.y * offset, flagBackPaint)

            // Draw the front (black) blocking line
            canvas.drawLine(start.x, start.y, end.x, end.y, flagPaint)

            if (flag.isSelected) {
                drawGizmo(canvas, flag.position, 60f, flag.angle)
            }
        }

        // Draw light sources on top
        for (light in lights) {
            drawLightSource(canvas, light)
            if (light.isSelected) {
                drawGizmo(canvas, light.position, 60f, light.angle)
            }
        }
    }

    private fun drawLightSource(canvas: Canvas, light: LightSource) {
        // Draw a small circle for the light source
        iconPaint.color = light.color
        canvas.drawCircle(light.position.x, light.position.y, 15f, iconPaint)

        // Draw direction indicator (arrow)
        val dir = light.getDirection()
        val arrowLength = 30f
        val endX = light.position.x + dir.x * arrowLength
        val endY = light.position.y + dir.y * arrowLength

        val arrowPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.drawLine(light.position.x, light.position.y, endX, endY, arrowPaint)

        // Draw arrowhead
        val arrowHeadLength = 10f
        val arrowAngle = Math.toRadians(30.0)

        val angle = Math.atan2(dir.y.toDouble(), dir.x.toDouble())

        val arrowX1 = endX - arrowHeadLength * Math.cos(angle - arrowAngle).toFloat()
        val arrowY1 = endY - arrowHeadLength * Math.sin(angle - arrowAngle).toFloat()

        val arrowX2 = endX - arrowHeadLength * Math.cos(angle + arrowAngle).toFloat()
        val arrowY2 = endY - arrowHeadLength * Math.sin(angle + arrowAngle).toFloat()

        canvas.drawLine(endX, endY, arrowX1, arrowY1, arrowPaint)
        canvas.drawLine(endX, endY, arrowX2, arrowY2, arrowPaint)
    }

    private fun drawGizmo(canvas: Canvas, center: PointF, radius: Float, angle: Float) {
        // Draw semi-transparent fill circle
        canvas.drawCircle(center.x, center.y, radius, gizmoFillPaint)

        // Draw circle outline
        canvas.drawCircle(center.x, center.y, radius, gizmoPaint)

        // Draw rotation handle
        val handleAngle = Math.toRadians(angle.toDouble())
        val handleX = center.x + radius * Math.cos(handleAngle).toFloat()
        val handleY = center.y + radius * Math.sin(handleAngle).toFloat()

        // Draw line to handle
        canvas.drawLine(center.x, center.y, handleX, handleY, gizmoPaint)

        // Draw handle circle
        val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(handleX, handleY, 12f, handlePaint)

        // Draw handle outline
        val handleOutlinePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawCircle(handleX, handleY, 12f, handleOutlinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(x, y)
                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(x, y)
                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                isRotating = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        // Check if touched on rotation handle
        selectedLight?.let {
            if (isOnRotationHandle(x, y, it.position, 60f, it.angle)) {
                isRotating = true
                return
            }
        }
        selectedMirror?.let {
            if (isOnRotationHandle(x, y, it.position, 60f, it.angle)) {
                isRotating = true
                return
            }
        }
        selectedFlag?.let {
            if (isOnRotationHandle(x, y, it.position, 60f, it.angle)) {
                isRotating = true
                return
            }
        }

        // Check if touched inside gizmo (for dragging)
        selectedLight?.let {
            if (distance(x, y, it.position.x, it.position.y) < 60f) {
                isDragging = true
                return
            }
        }
        selectedMirror?.let {
            if (distance(x, y, it.position.x, it.position.y) < 60f) {
                isDragging = true
                return
            }
        }
        selectedFlag?.let {
            if (distance(x, y, it.position.x, it.position.y) < 60f) {
                isDragging = true
                return
            }
        }

        // Try to select an object
        clearSelection()

        for (light in lights) {
            if (distance(x, y, light.position.x, light.position.y) < 30f) {
                light.isSelected = true
                selectedLight = light
                notifySelectionChanged()
                invalidate()
                return
            }
        }

        for (mirror in mirrors) {
            if (distanceToLineSegment(PointF(x, y), mirror.getEndpoints()) < 40f) {
                mirror.isSelected = true
                selectedMirror = mirror
                notifySelectionChanged()
                invalidate()
                return
            }
        }

        for (flag in flags) {
            if (distanceToLineSegment(PointF(x, y), flag.getEndpoints()) < 40f) {
                flag.isSelected = true
                selectedFlag = flag
                notifySelectionChanged()
                invalidate()
                return
            }
        }

        invalidate()
    }

    private fun handleTouchMove(x: Float, y: Float) {
        if (isDragging) {
            val dx = x - lastTouchX
            val dy = y - lastTouchY

            selectedLight?.let {
                it.position.x += dx
                it.position.y += dy
            }
            selectedMirror?.let {
                it.position.x += dx
                it.position.y += dy
            }
            selectedFlag?.let {
                it.position.x += dx
                it.position.y += dy
            }

            invalidate()
        } else if (isRotating) {
            selectedLight?.let {
                val angle = Math.toDegrees(
                    atan2((y - it.position.y).toDouble(), (x - it.position.x).toDouble())
                ).toFloat()
                it.angle = angle
            }
            selectedMirror?.let {
                val angle = Math.toDegrees(
                    atan2((y - it.position.y).toDouble(), (x - it.position.x).toDouble())
                ).toFloat()
                it.angle = angle
            }
            selectedFlag?.let {
                val angle = Math.toDegrees(
                    atan2((y - it.position.y).toDouble(), (x - it.position.x).toDouble())
                ).toFloat()
                it.angle = angle
            }

            invalidate()
        }
    }

    private fun isOnRotationHandle(
        x: Float, y: Float, center: PointF, radius: Float, angle: Float
    ): Boolean {
        val handleAngle = Math.toRadians(angle.toDouble())
        val handleX = center.x + radius * Math.cos(handleAngle).toFloat()
        val handleY = center.y + radius * Math.sin(handleAngle).toFloat()

        return distance(x, y, handleX, handleY) < 20f
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun distanceToLineSegment(point: PointF, segment: Pair<PointF, PointF>): Float {
        val (start, end) = segment
        val px = point.x
        val py = point.y
        val x1 = start.x
        val y1 = start.y
        val x2 = end.x
        val y2 = end.y

        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0f) return distance(px, py, x1, y1)

        var t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
        t = t.coerceIn(0f, 1f)

        val projX = x1 + t * dx
        val projY = y1 + t * dy

        return distance(px, py, projX, projY)
    }
}