package com.example.laserbender.data.model

import android.graphics.PointF
import java.util.UUID

data class Mirror(
    val id: String = UUID.randomUUID().toString(),
    var position: PointF = PointF(0f, 0f),
    var angle: Float = 0f, // Angle in degrees
    var length: Float = 150f, // Length of the mirror
    var isSelected: Boolean = false
) {
    // Get the two endpoints of the mirror
    fun getEndpoints(): Pair<PointF, PointF> {
        val radians = Math.toRadians(angle.toDouble())
        val halfLength = length / 2

        val dx = (Math.cos(radians) * halfLength).toFloat()
        val dy = (Math.sin(radians) * halfLength).toFloat()

        val start = PointF(position.x - dx, position.y - dy)
        val end = PointF(position.x + dx, position.y + dy)

        return Pair(start, end)
    }

    // Get normal vector (perpendicular to mirror surface)
    fun getNormal(): PointF {
        val radians = Math.toRadians(angle.toDouble() + 90)
        return PointF(
            Math.cos(radians).toFloat(),
            Math.sin(radians).toFloat()
        )
    }

    fun deepCopy(): Mirror {
        return this.copy(position = PointF(this.position.x, this.position.y))
    }
}