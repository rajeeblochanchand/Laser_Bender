package com.example.laserbender.utils

import android.graphics.PointF
import com.example.laserbender.data.model.Flag
import com.example.laserbender.data.model.LightSource
import com.example.laserbender.data.model.Mirror
import kotlin.math.sqrt

object RayTracer {

    data class RaySegment(
        val start: PointF,
        val end: PointF,
        val color: Int
    )

    data class Intersection(
        val point: PointF,
        val distance: Float,
        val mirror: Mirror? = null,
        val flag: Flag? = null
    )

    data class EdgeHit(
        val point: PointF,
        val color: Int
    )

    data class TraceResult(
        val segments: List<RaySegment>,
        val edgeHits: List<EdgeHit>
    )

    fun traceLightRay(
        light: LightSource,
        mirrors: List<Mirror>,
        flags: List<Flag>,
        canvasWidth: Float,
        canvasHeight: Float,
        maxReflections: Int = 20
    ): TraceResult {
        val segments = mutableListOf<RaySegment>()
        val edgeHits = mutableListOf<EdgeHit>()
        var currentPos = light.position
        var currentDir = light.getDirection()
        var currentColor = light.color

        for (i in 0 until maxReflections) {
            val intersection = findNearestIntersection(currentPos, currentDir, mirrors, flags)

            if (intersection == null) {
                val edgePoint = findCanvasEdgeIntersection(currentPos, currentDir, canvasWidth, canvasHeight)
                segments.add(RaySegment(currentPos, edgePoint, currentColor))
                edgeHits.add(EdgeHit(edgePoint, currentColor))
                break
            }

            segments.add(RaySegment(currentPos, intersection.point, currentColor))

            if (intersection.flag != null) {
                break // Stop at flags
            }

            if (intersection.mirror != null) {
                currentPos = intersection.point
                currentDir = reflect(currentDir, intersection.mirror.getNormal())
            } else {
                break
            }
        }

        return TraceResult(segments, edgeHits)
    }

    private fun findNearestIntersection(
        rayOrigin: PointF,
        rayDir: PointF,
        mirrors: List<Mirror>,
        flags: List<Flag>
    ): Intersection? {
        var nearestIntersection: Intersection? = null

        for (mirror in mirrors) {
            val intersectionPoint = getIntersectionPoint(rayOrigin, rayDir, mirror.getEndpoints())
            if (intersectionPoint != null) {
                if (isHittingFront(rayDir, mirror.getNormal())) {
                    val distance = distance(rayOrigin, intersectionPoint)
                    if (nearestIntersection == null || distance < nearestIntersection.distance) {
                        nearestIntersection = Intersection(intersectionPoint, distance, mirror = mirror)
                    }
                }
            }
        }

        for (flag in flags) {
            val intersectionPoint = getIntersectionPoint(rayOrigin, rayDir, flag.getEndpoints())
            if (intersectionPoint != null) {
                if (isHittingFront(rayDir, flag.getNormal())) {
                    val distance = distance(rayOrigin, intersectionPoint)
                    if (nearestIntersection == null || distance < nearestIntersection.distance) {
                        nearestIntersection = Intersection(intersectionPoint, distance, flag = flag)
                    }
                }
            }
        }

        return nearestIntersection
    }

    private fun getIntersectionPoint(rayOrigin: PointF, rayDir: PointF, segment: Pair<PointF, PointF>): PointF? {
        val (p1, p2) = segment
        val r_px = rayOrigin.x
        val r_py = rayOrigin.y
        val r_dx = rayDir.x
        val r_dy = rayDir.y

        val s_px = p1.x
        val s_py = p1.y
        val s_dx = p2.x - p1.x
        val s_dy = p2.y - p1.y

        val r_mag = sqrt(r_dx * r_dx + r_dy * r_dy)
        val s_mag = sqrt(s_dx * s_dx + s_dy * s_dy)

        if (r_mag == 0f || s_mag == 0f) return null

        val T2 = (r_dx * (s_py - r_py) + r_dy * (r_px - s_px)) / (s_dx * r_dy - s_dy * r_dx)
        val T1 = (s_px + s_dx * T2 - r_px) / r_dx

        if (T1 > 0 && T2 in 0.0..1.0) {
            return PointF(r_px + r_dx * T1, r_py + r_dy * T1)
        }

        return null
    }

    private fun isHittingFront(rayDir: PointF, normal: PointF): Boolean {
        // Dot product of the ray direction and the normal of the surface
        // If the result is negative, the ray is hitting the front of the surface
        return rayDir.x * normal.x + rayDir.y * normal.y < 0
    }

    private fun reflect(incident: PointF, normal: PointF): PointF {
        val dot = 2 * (incident.x * normal.x + incident.y * normal.y)
        return PointF(incident.x - dot * normal.x, incident.y - dot * normal.y)
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
    }

    private fun findCanvasEdgeIntersection(pos: PointF, dir: PointF, width: Float, height: Float): PointF {
        var t = Float.MAX_VALUE

        if (dir.x > 0) t = t.coerceAtMost((width - pos.x) / dir.x)
        if (dir.x < 0) t = t.coerceAtMost(-pos.x / dir.x)
        if (dir.y > 0) t = t.coerceAtMost((height - pos.y) / dir.y)
        if (dir.y < 0) t = t.coerceAtMost(-pos.y / dir.y)

        return PointF(pos.x + t * dir.x, pos.y + t * dir.y)
    }
}