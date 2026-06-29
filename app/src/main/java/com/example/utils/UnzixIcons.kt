package com.example.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object UnzixIcons {
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 4f)
                lineTo(4f, 4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                lineTo(20f, 20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                lineTo(22f, 8f)
                curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                lineTo(12f, 6f)
                lineTo(10f, 4f)
                close()
            }
        }.build()
    }

    val Zip: ImageVector by lazy {
        ImageVector.Builder(
            name = "ZipArchive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Drawn folder outline with a zipper box in the middle representing compression
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 6f)
                lineTo(12f, 6f)
                lineTo(10f, 4f)
                lineTo(4f, 4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineTo(18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(8f)
                curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                close()
                // Zipper teeth block
                moveTo(14f, 16f)
                horizontalLineTo(10f)
                verticalLineTo(14f)
                horizontalLineTo(14f)
                verticalLineTo(16f)
                close()
                moveTo(14f, 12f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                verticalLineTo(12f)
                close()
            }
        }.build()
    }
}
