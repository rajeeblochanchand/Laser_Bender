package com.example.laserbender.presentation.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.laserbender.data.model.Flag
import com.example.laserbender.data.model.LightSource
import com.example.laserbender.data.model.Mirror
import com.example.laserbender.data.model.Selectable
import com.example.laserbender.utils.RayTracer
import java.util.Stack
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class LaserCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lights = mutableListOf<LightSource>()
    private var mirrors = mutableListOf<Mirror>()
    private var flags = mutableListOf<Flag>()

    private val undoStack = Stack<CanvasState>()
    private val redoStack = Stack<CanvasState>()

    private var selectedLight: LightSource? = null
    private var selectedMirror: Mirror? = null
    private var selectedFlag: Flag? = null

    private var isDragging = false
    private var isRotating = false
    private var isPanning = false

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f

    private var onSelectionChanged: ((Boolean, Boolean) -> Unit)? = null
    private var onHistoryChanged: (() -> Unit)? = null
    private var onZoomChanged: ((Float) -> Unit)? = null

    private val TAG = "LaserCanvasView"

    // Camera and Scaling
    private var scaleFactor = 1f
    private var translationX = 0f
    private var translationY = 0f
    private var isViewLocked = false
    private val scaleGestureDetector: ScaleGestureDetector

    enum class Trigger { ADD, DELETE, MOVE, ROTATE, COLOR_CHANGE, INITIAL }

    // Paint objects
    private val laserPaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#D934345c")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val majorGridPaint = Paint().apply {
        color = Color.parseColor("#D934345c")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val dottedBorderPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val laserGlowPaint = Paint().apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val iconGlowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val edgeGlowPaint = Paint().apply {
        style = Paint.Style.FILL
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
        laserGlowPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        iconGlowPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        edgeGlowPaint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)

        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        saveState(Trigger.INITIAL)
    }

    fun setOnSelectionChangedListener(listener: (Boolean, Boolean) -> Unit) {
        onSelectionChanged = listener
    }

    fun setOnHistoryChangedListener(listener: () -> Unit) {
        onHistoryChanged = listener
    }

    fun setOnZoomChangedListener(listener: (Float) -> Unit) {
        onZoomChanged = listener
    }

    fun resetView() {
        scaleFactor = 1f
        translationX = 0f
        translationY = 0f
        invalidate()
        onZoomChanged?.invoke(scaleFactor)
    }

    fun toggleViewLock(): Boolean {
        isViewLocked = !isViewLocked
        return isViewLocked
    }

    fun addLight() {
        val pos = findNextAvailablePosition(screenToWorld(width / 2f, height / 2f))
        lights.add(LightSource(position = pos))
        saveState(Trigger.ADD)
        invalidateAndNotifyHistory()
    }

    fun addMirror() {
        val pos = findNextAvailablePosition(screenToWorld(width / 2f, height / 2f))
        mirrors.add(Mirror(position = pos))
        saveState(Trigger.ADD)
        invalidateAndNotifyHistory()
    }

    fun addFlag() {
        val pos = findNextAvailablePosition(screenToWorld(width / 2f, height / 2f))
        flags.add(Flag(position = pos))
        saveState(Trigger.ADD)
        invalidateAndNotifyHistory()
    }

    private fun findNextAvailablePosition(initialPosition: PointF): PointF {
        var position = initialPosition.apply { 
            x = x.coerceIn(0f, width.toFloat())
            y = y.coerceIn(0f, height.toFloat())
        }
        val radius = 80f // A bit more than the gizmo radius
        if (!isPositionOccupied(position, radius)) {
            return position
        }

        var counter = 0
        while (isPositionOccupied(position, radius) && counter < 100) {
            val angle = Random.nextFloat() * 2 * Math.PI
            val offsetX = radius * cos(angle).toFloat()
            val offsetY = radius * sin(angle).toFloat()
            position = PointF(position.x + offsetX, position.y + offsetY)
            position.apply { 
                x = x.coerceIn(0f, width.toFloat())
                y = y.coerceIn(0f, height.toFloat())
            }
            counter++
        }
        return position
    }

    private fun isPositionOccupied(position: PointF, radius: Float): Boolean {
        val allObjects = lights.map { it.position } + mirrors.map { it.position } + flags.map { it.position }
        return allObjects.any { p -> distance(p.x, p.y, position.x, position.y) < radius }
    }

    fun deleteSelected() {
        selectedLight?.let { lights.remove(it) }
        selectedMirror?.let { mirrors.remove(it) }
        selectedFlag?.let { flags.remove(it) }
        clearSelection()
        saveState(Trigger.DELETE)
        invalidateAndNotifyHistory()
    }

    fun changeSelectedLightColor(color: Int) {
        selectedLight?.let {
            it.color = color
            saveState(Trigger.COLOR_CHANGE)
            invalidateAndNotifyHistory()
        }
    }

    fun undo() {
        if (canUndo()) {
            redoStack.push(undoStack.pop().deepCopy())
            restoreState(undoStack.peek())
            Log.d(TAG, "Undo: Undo stack size = ${undoStack.size}, Redo stack size = ${redoStack.size}")
            invalidateAndNotifyHistory()
        }
    }

    fun redo() {
        if (canRedo()) {
            val stateToRestore = redoStack.pop()
            undoStack.push(stateToRestore.deepCopy())
            restoreState(stateToRestore)
            Log.d(TAG, "Redo: Undo stack size = ${undoStack.size}, Redo stack size = ${redoStack.size}")
            invalidateAndNotifyHistory()
        }
    }

    fun canUndo(): Boolean = undoStack.size > 1

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun saveState(trigger: Trigger, start: PointF? = null, end: PointF? = null) {
        if (trigger != Trigger.INITIAL && undoStack.size >= 20) {
            undoStack.removeAt(0)
        }
        undoStack.push(CanvasState(lights, mirrors, flags).deepCopy())
        if (trigger != Trigger.INITIAL) redoStack.clear()
        
        var logMsg = "State Saved: [${trigger.name}] Undo = ${undoStack.size}, Redo = ${redoStack.size}"
        if (trigger == Trigger.MOVE && start != null && end != null) {
            logMsg += ", Start: (${start.x}, ${start.y}), End: (${end.x}, ${end.y})"
        }
        Log.d(TAG, logMsg)
    }

    private fun restoreState(state: CanvasState) {
        val restoredState = state.deepCopy()
        lights = restoredState.lights.toMutableList()
        mirrors = restoredState.mirrors.toMutableList()
        flags = restoredState.flags.toMutableList()
        clearSelection()
    }

    private fun invalidateAndNotifyHistory() {
        invalidate()
        onHistoryChanged?.invoke()
    }

    data class CanvasState(val lights: List<LightSource>, val mirrors: List<Mirror>, val flags: List<Flag>) {
        fun deepCopy(): CanvasState {
            return CanvasState(
                lights.map { it.deepCopy() },
                mirrors.map { it.deepCopy() },
                flags.map { it.deepCopy() }
            )
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
        invalidate()
    }

    private fun notifySelectionChanged() {
        val hasSelection = selectedLight != null || selectedMirror != null || selectedFlag != null
        val isLightSource = selectedLight != null
        onSelectionChanged?.invoke(hasSelection, isLightSource)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(translationX, translationY)
        canvas.scale(scaleFactor, scaleFactor)
        
        drawGrid(canvas)

        // Draw laser beams first
        for (light in lights) {
            val result = RayTracer.traceLightRay(
                light, mirrors, flags, width.toFloat(), height.toFloat()
            )
            for (segment in result.segments) {
                laserGlowPaint.color = segment.color
                laserGlowPaint.alpha = 100 // Subtle glow
                canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, laserGlowPaint)
                laserPaint.color = segment.color
                canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, laserPaint)
            }

            for (hit in result.edgeHits) {
                edgeGlowPaint.color = hit.color
                edgeGlowPaint.alpha = 150 // Subtle glow
                val x = hit.point.x
                val y = hit.point.y

                val rect: RectF
                if (x <= 1f || x >= width - 1f) {
                    // Hit left or right edge, draw a vertically stretched ellipse
                    rect = RectF(x - 10, y - 30, x + 10, y + 30)
                } else {
                    // Hit top or bottom edge, draw a horizontally stretched ellipse
                    rect = RectF(x - 30, y - 10, x + 30, y + 10)
                }
                canvas.drawOval(rect, edgeGlowPaint)
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
        
        if (scaleFactor > 1.0f || translationX != 0f || translationY != 0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dottedBorderPaint)
        }

        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 25f

        for (i in 1 until (width / gridSize).toInt()) {
            val paint = if (i % 10 == 0) majorGridPaint else gridPaint
            canvas.drawLine(i * gridSize, 0f, i * gridSize, height.toFloat(), paint)
        }

        for (i in 1 until (height / gridSize).toInt()) {
            val paint = if (i % 10 == 0) majorGridPaint else gridPaint
            canvas.drawLine(0f, i * gridSize, width.toFloat(), i * gridSize, paint)
        }
    }

    private fun drawLightSource(canvas: Canvas, light: LightSource) {
        // Draw the glow first
        iconGlowPaint.color = light.color
        iconGlowPaint.alpha = 150 // Subtle glow
        canvas.drawCircle(light.position.x, light.position.y, 22f, iconGlowPaint)

        // Draw a small circle for the light source
        iconPaint.color = light.color
        canvas.drawCircle(light.position.x, light.position.y, 18f, iconPaint)

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
        val handleDistance = radius + 35f
        val handleAngle = Math.toRadians(angle.toDouble())
        val handleX = center.x + handleDistance * Math.cos(handleAngle).toFloat()
        val handleY = center.y + handleDistance * Math.sin(handleAngle).toFloat()

        // Draw line to handle
        canvas.drawLine(center.x, center.y, handleX, handleY, gizmoPaint)

        // Draw handle circle
        val handlePaint = Paint().apply {
            color = Color.parseColor("#80FFFFFF")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(handleX, handleY, 25f, handlePaint)

        // Draw handle outline
        val handleOutlinePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawCircle(handleX, handleY, 25f, handleOutlinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isViewLocked) {
            scaleGestureDetector.onTouchEvent(event)
        }

        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                dragStartX = getSelectedObjectPosition().x
                dragStartY = getSelectedObjectPosition().y
                handleTouchDown(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                handleTouchMove(x, y, dx, dy)
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    saveState(Trigger.MOVE, PointF(dragStartX, dragStartY), getSelectedObjectPosition())
                } else if(isRotating) {
                    saveState(Trigger.ROTATE)
                }
                isDragging = false
                isRotating = false
                isPanning = false
            }
        }
        return true
    }

    private fun handleTouchDown(x: Float, y: Float) {
        // Check if we are touching the rotation handle of an already selected object
        if (isAnyObjectSelected() && isOnRotationHandle(x, y, getSelectedObjectPosition(), 60f, getSelectedObjectAngle())) {
            isRotating = true
            return
        }

        val objectToSelect = findObjectAt(x, y)
        if (objectToSelect != null) {
            if (!objectToSelect.isSelected) {
                clearSelection()
                selectObject(objectToSelect)
            }
            // Don't set isDragging here, wait for a move gesture
            return
        }
        
        // If we touched an empty area, clear the selection and start panning
        if (isAnyObjectSelected()) {
            clearSelection()
        }
        if (!isViewLocked) {
            isPanning = true
        }
    }

    private fun handleTouchMove(x: Float, y: Float, dx: Float, dy: Float) {
        if (!isRotating && !isPanning && isAnyObjectSelected()) {
            isDragging = true
        }
        
        if (isDragging) {
            selectedLight?.position?.offset(dx, dy)
            selectedMirror?.position?.offset(dx, dy)
            selectedFlag?.position?.offset(dx, dy)
            invalidate()
        } else if (isRotating) {
            val pos = getSelectedObjectPosition()
            val angle = Math.toDegrees(atan2((y - pos.y).toDouble(), (x - pos.x).toDouble())).toFloat()
            setSelectedObjectAngle(angle)
            invalidate()
        } else if (isPanning && !isViewLocked) {
            translationX += dx * scaleFactor
            translationY += dy * scaleFactor
            invalidate()
            onZoomChanged?.invoke(scaleFactor)
        }
    }

    private fun screenToWorld(screenX: Float, screenY: Float): PointF {
        return PointF((screenX - translationX) / scaleFactor, (screenY - translationY) / scaleFactor)
    }

    private fun isOnRotationHandle(
        x: Float, y: Float, center: PointF, radius: Float, angle: Float
    ): Boolean {
        val handleDistance = radius + 35f
        val handleAngle = Math.toRadians(angle.toDouble())
        val handleX = center.x + handleDistance * Math.cos(handleAngle).toFloat()
        val handleY = center.y + handleDistance * Math.sin(handleAngle).toFloat()

        return distance(x, y, handleX, handleY) < 45f
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    private fun findObjectAt(x: Float, y: Float): Selectable? {
        for (light in lights.reversed()) {
            if (distance(x, y, light.position.x, light.position.y) < 30f) {
                return light
            }
        }
        for (mirror in mirrors.reversed()) {
            if (distanceToLineSegment(PointF(x, y), mirror.getEndpoints()) < 40f) {
                return mirror
            }
        }
        for (flag in flags.reversed()) {
            if (distanceToLineSegment(PointF(x, y), flag.getEndpoints()) < 40f) {
                return flag
            }
        }
        return null
    }

    private fun selectObject(obj: Selectable) {
        when (obj) {
            is LightSource -> {
                obj.isSelected = true
                selectedLight = obj
            }
            is Mirror -> {
                obj.isSelected = true
                selectedMirror = obj
            }
            is Flag -> {
                obj.isSelected = true
                selectedFlag = obj
            }
        }
        notifySelectionChanged()
        invalidate()
    }

    private fun isAnyObjectSelected(): Boolean = selectedLight != null || selectedMirror != null || selectedFlag != null

    private fun getSelectedObjectPosition(): PointF = selectedLight?.position ?: selectedMirror?.position ?: selectedFlag?.position ?: PointF()

    private fun getSelectedObjectAngle(): Float = selectedLight?.angle ?: selectedMirror?.angle ?: selectedFlag?.angle ?: 0f

    private fun setSelectedObjectAngle(angle: Float) {
        selectedLight?.angle = angle
        selectedMirror?.angle = angle
        selectedFlag?.angle = angle
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if(isViewLocked) return false
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f))

            translationX = detector.focusX - (detector.focusX - translationX) * detector.scaleFactor
            translationY = detector.focusY - (detector.focusY - translationY) * detector.scaleFactor

            invalidate()
            onZoomChanged?.invoke(scaleFactor)
            return true
        }
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