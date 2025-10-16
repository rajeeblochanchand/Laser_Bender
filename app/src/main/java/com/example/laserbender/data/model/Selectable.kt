package com.example.laserbender.data.model

import android.graphics.PointF

interface Selectable {
    val id: String
    var position: PointF
    var isSelected: Boolean
}