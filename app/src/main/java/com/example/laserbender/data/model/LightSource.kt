package com.example.laserbender.data.model

import android.graphics.Color
import android.graphics.PointF
import java.util.UUID

data class LightSource(
    val id: String = UUID.randomUUID().toString(),
    var position: PointF = PointF(0f, 0f),
    var angle: Float = 0f, // Angle in degrees (0 = right, 90 = down)
    var color: Int = Color.RED,
    var isSelected: Boolean = false
) {
    // Get direction vector from angle
    fun getDirection(): PointF {
        val radians = Math.toRadians(angle.toDouble())
        return PointF(
            Math.cos(radians).toFloat(),
            Math.sin(radians).toFloat()
        )
    }
}