package com.wusper.debarm.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A handful of original-path Material-style icons. Avoids shipping the entire extended set. */
object DebArmIcons {
    val Refresh: ImageVector by lazy {
        material("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            reflectiveCurveTo(7.57f, 20f, 11.99f, 20f)
            curveTo(15.72f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            reflectiveCurveTo(8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            lineTo(17.65f, 6.35f)
            close()
        }
    }

    val Terminal: ImageVector by lazy {
        material("Terminal") {
            moveTo(20f, 4f)
            horizontalLineTo(4f)
            curveTo(2.89f, 4f, 2f, 4.89f, 2f, 6f)
            verticalLineTo(18f)
            curveTo(2f, 19.11f, 2.89f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.11f, 20f, 22f, 19.11f, 22f, 18f)
            verticalLineTo(6f)
            curveTo(22f, 4.89f, 21.11f, 4f, 20f, 4f)
            close()
            moveTo(20f, 18f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            close()
            moveTo(12f, 16f)
            horizontalLineTo(18f)
            verticalLineTo(14f)
            horizontalLineTo(12f)
            close()
            moveTo(6.08f, 11.59f)
            lineTo(7.5f, 13f)
            lineTo(6.08f, 14.41f)
            lineTo(7.5f, 15.83f)
            lineTo(10.33f, 13f)
            lineTo(7.5f, 10.17f)
            close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        material("FolderOpen") {
            moveTo(20f, 6f)
            horizontalLineTo(12f)
            lineTo(10f, 4f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            close()
            moveTo(20f, 18f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            close()
        }
    }

    val Delete: ImageVector by lazy {
        material("Delete") {
            moveTo(16f, 9f)
            verticalLineTo(19f)
            horizontalLineTo(8f)
            verticalLineTo(9f)
            horizontalLineTo(16f)
            moveTo(14.5f, 3f)
            horizontalLineTo(9.5f)
            lineTo(8.5f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(19f)
            verticalLineTo(4f)
            horizontalLineTo(15.5f)
            lineTo(14.5f, 3f)
            close()
            moveTo(18f, 7f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
            close()
        }
    }

    private fun material(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                pathBuilder = build,
            )
        }.build()
}
