package com.tristan.spotifyfast.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Vector icons compiled from VectorCraft SVGs in design/icons/.
 */
object SpotifyIcons {
    private var _home: ImageVector? = null
    val Home: ImageVector
        get() = _home ?: ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.0f, 3.0f)
                lineTo(3.0f, 10.0f)
                lineTo(3.0f, 21.0f)
                lineTo(9.0f, 21.0f)
                lineTo(9.0f, 14.0f)
                lineTo(15.0f, 14.0f)
                lineTo(15.0f, 21.0f)
                lineTo(21.0f, 21.0f)
                lineTo(21.0f, 10.0f)
                close()
            }
        }.build().also { _home = it }

    private var _search: ImageVector? = null
    val Search: ImageVector
        get() = _search ?: ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(10.5f, 3.0f)
                curveTo(6.36f, 3.0f, 3.0f, 6.36f, 3.0f, 10.5f)
                curveTo(3.0f, 14.64f, 6.36f, 18.0f, 10.5f, 18.0f)
                curveTo(12.2f, 18.0f, 13.78f, 17.43f, 15.04f, 16.46f)
                lineTo(20.29f, 21.71f)
                lineTo(21.71f, 20.29f)
                lineTo(16.46f, 15.04f)
                curveTo(17.43f, 13.78f, 18.0f, 12.2f, 18.0f, 10.5f)
                curveTo(18.0f, 6.36f, 14.64f, 3.0f, 10.5f, 3.0f)
                close()
                moveTo(10.5f, 5.0f)
                curveTo(13.54f, 5.0f, 16.0f, 7.46f, 16.0f, 10.5f)
                curveTo(16.0f, 13.54f, 13.54f, 16.0f, 10.5f, 16.0f)
                curveTo(7.46f, 16.0f, 5.0f, 13.54f, 5.0f, 10.5f)
                curveTo(5.0f, 7.46f, 7.46f, 5.0f, 10.5f, 5.0f)
                close()
            }
        }.build().also { _search = it }

    private var _library: ImageVector? = null
    val Library: ImageVector
        get() = _library ?: ImageVector.Builder(
            name = "Library",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(4.0f, 4.0f)
                lineTo(6.0f, 4.0f)
                lineTo(6.0f, 20.0f)
                lineTo(4.0f, 20.0f)
                close()
                moveTo(9.0f, 4.0f)
                lineTo(11.0f, 4.0f)
                lineTo(11.0f, 20.0f)
                lineTo(9.0f, 20.0f)
                close()
                moveTo(14.0f, 4.5f)
                lineTo(16.0f, 4.0f)
                lineTo(20.5f, 19.5f)
                lineTo(18.5f, 20.0f)
                close()
            }
        }.build().also { _library = it }

    private var _heart: ImageVector? = null
    val Heart: ImageVector
        get() = _heart ?: ImageVector.Builder(
            name = "Heart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.0f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2.0f, 12.28f, 2.0f, 8.5f)
                curveTo(2.0f, 5.42f, 4.42f, 3.0f, 7.5f, 3.0f)
                curveTo(9.24f, 3.0f, 10.91f, 3.81f, 12.0f, 5.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3.0f, 16.5f, 3.0f)
                curveTo(19.58f, 3.0f, 22.0f, 5.42f, 22.0f, 8.5f)
                curveTo(22.0f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                close()
            }
        }.build().also { _heart = it }

    private var _shuffle: ImageVector? = null
    val Shuffle: ImageVector
        get() = _shuffle ?: ImageVector.Builder(
            name = "Shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(10.59f, 9.17f)
                lineTo(5.41f, 4.0f)
                lineTo(4.0f, 5.41f)
                lineTo(9.17f, 10.58f)
                close()
                moveTo(14.5f, 4.0f)
                lineTo(16.54f, 6.04f)
                lineTo(4.0f, 18.59f)
                lineTo(5.41f, 20.0f)
                lineTo(17.96f, 7.46f)
                lineTo(20.0f, 9.5f)
                lineTo(20.0f, 4.0f)
                close()
                moveTo(14.83f, 13.41f)
                lineTo(13.42f, 14.82f)
                lineTo(16.55f, 17.95f)
                lineTo(14.5f, 20.0f)
                lineTo(20.0f, 20.0f)
                lineTo(20.0f, 14.5f)
                lineTo(17.96f, 16.54f)
                close()
            }
        }.build().also { _shuffle = it }

    private var _repeat: ImageVector? = null
    val Repeat: ImageVector
        get() = _repeat ?: ImageVector.Builder(
            name = "Repeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(7.0f, 7.0f)
                lineTo(17.0f, 7.0f)
                lineTo(17.0f, 10.0f)
                lineTo(21.0f, 6.0f)
                lineTo(17.0f, 2.0f)
                lineTo(17.0f, 5.0f)
                lineTo(5.0f, 5.0f)
                lineTo(5.0f, 11.0f)
                lineTo(7.0f, 11.0f)
                close()
                moveTo(17.0f, 17.0f)
                lineTo(7.0f, 17.0f)
                lineTo(7.0f, 14.0f)
                lineTo(3.0f, 18.0f)
                lineTo(7.0f, 22.0f)
                lineTo(7.0f, 19.0f)
                lineTo(19.0f, 19.0f)
                lineTo(19.0f, 13.0f)
                lineTo(17.0f, 13.0f)
                close()
            }
        }.build().also { _repeat = it }

    private var _play: ImageVector? = null
    val Play: ImageVector
        get() = _play ?: ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(8.0f, 5.0f)
                lineTo(19.0f, 12.0f)
                lineTo(8.0f, 19.0f)
                close()
            }
        }.build().also { _play = it }

    private var _pause: ImageVector? = null
    val Pause: ImageVector
        get() = _pause ?: ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(6.0f, 5.0f)
                lineTo(10.0f, 5.0f)
                lineTo(10.0f, 19.0f)
                lineTo(6.0f, 19.0f)
                close()
                moveTo(14.0f, 5.0f)
                lineTo(18.0f, 5.0f)
                lineTo(18.0f, 19.0f)
                lineTo(14.0f, 19.0f)
                close()
            }
        }.build().also { _pause = it }

    private var _queue: ImageVector? = null
    val Queue: ImageVector
        get() = _queue ?: ImageVector.Builder(
            name = "Queue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(3.0f, 6.0f)
                lineTo(15.0f, 6.0f)
                lineTo(15.0f, 8.0f)
                lineTo(3.0f, 8.0f)
                close()
                moveTo(3.0f, 10.0f)
                lineTo(15.0f, 10.0f)
                lineTo(15.0f, 12.0f)
                lineTo(3.0f, 12.0f)
                close()
                moveTo(3.0f, 14.0f)
                lineTo(11.0f, 14.0f)
                lineTo(11.0f, 16.0f)
                lineTo(3.0f, 16.0f)
                close()
                moveTo(17.0f, 6.0f)
                lineTo(17.0f, 14.18f)
                curveTo(16.69f, 14.07f, 16.35f, 14.0f, 16.0f, 14.0f)
                curveTo(14.34f, 14.0f, 13.0f, 15.34f, 13.0f, 17.0f)
                curveTo(13.0f, 18.66f, 14.34f, 20.0f, 16.0f, 20.0f)
                curveTo(17.66f, 20.0f, 19.0f, 18.66f, 19.0f, 17.0f)
                lineTo(19.0f, 8.0f)
                lineTo(22.0f, 8.0f)
                lineTo(22.0f, 6.0f)
                close()
            }
        }.build().also { _queue = it }

    private var _devices: ImageVector? = null
    val Devices: ImageVector
        get() = _devices ?: ImageVector.Builder(
            name = "Devices",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(4.0f, 6.0f)
                lineTo(22.0f, 6.0f)
                lineTo(22.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                lineTo(2.0f, 17.0f)
                lineTo(0.0f, 17.0f)
                lineTo(0.0f, 20.0f)
                lineTo(14.0f, 20.0f)
                lineTo(14.0f, 17.0f)
                lineTo(4.0f, 17.0f)
                close()
                moveTo(23.0f, 8.0f)
                lineTo(17.0f, 8.0f)
                curveTo(16.45f, 8.0f, 16.0f, 8.45f, 16.0f, 9.0f)
                lineTo(16.0f, 19.0f)
                curveTo(16.0f, 19.55f, 16.45f, 20.0f, 17.0f, 20.0f)
                lineTo(23.0f, 20.0f)
                curveTo(23.55f, 20.0f, 24.0f, 19.55f, 24.0f, 19.0f)
                lineTo(24.0f, 9.0f)
                curveTo(24.0f, 8.45f, 23.55f, 8.0f, 23.0f, 8.0f)
                close()
                moveTo(22.0f, 17.0f)
                lineTo(18.0f, 17.0f)
                lineTo(18.0f, 10.0f)
                lineTo(22.0f, 10.0f)
                close()
            }
        }.build().also { _devices = it }

    private var _lyrics: ImageVector? = null
    val Lyrics: ImageVector
        get() = _lyrics ?: ImageVector.Builder(
            name = "Lyrics",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.0f, 3.0f)
                lineTo(12.0f, 13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13.0f, 10.0f, 13.0f)
                curveTo(7.79f, 13.0f, 6.0f, 14.79f, 6.0f, 17.0f)
                curveTo(6.0f, 19.21f, 7.79f, 21.0f, 10.0f, 21.0f)
                curveTo(12.21f, 21.0f, 14.0f, 19.21f, 14.0f, 17.0f)
                lineTo(14.0f, 7.0f)
                lineTo(18.0f, 7.0f)
                lineTo(18.0f, 3.0f)
                close()
            }
        }.build().also { _lyrics = it }

    private var _speed: ImageVector? = null
    val Speed: ImageVector
        get() = _speed ?: ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                moveTo(10.0f, 8.0f)
                lineTo(10.0f, 16.0f)
                lineTo(17.0f, 12.0f)
                close()
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                curveTo(2.0f, 17.52f, 6.48f, 22.0f, 12.0f, 22.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveTo(7.59f, 20.0f, 4.0f, 16.41f, 4.0f, 12.0f)
                curveTo(4.0f, 7.59f, 7.59f, 4.0f, 12.0f, 4.0f)
                curveTo(16.41f, 4.0f, 20.0f, 7.59f, 20.0f, 12.0f)
                curveTo(20.0f, 16.41f, 16.41f, 20.0f, 12.0f, 20.0f)
                close()
            }
        }.build().also { _speed = it }

    private var _skipnext: ImageVector? = null
    val SkipNext: ImageVector
        get() = _skipnext ?: ImageVector.Builder(
            name = "SkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6.0f, 18.0f)
                lineTo(14.5f, 12.0f)
                lineTo(6.0f, 6.0f)
                lineTo(6.0f, 18.0f)
                close()
                moveTo(16.0f, 6.0f)
                lineTo(16.0f, 18.0f)
                lineTo(18.0f, 18.0f)
                lineTo(18.0f, 6.0f)
                lineTo(16.0f, 6.0f)
                close()
            }
        }.build().also { _skipnext = it }

    private var _skipprevious: ImageVector? = null
    val SkipPrevious: ImageVector
        get() = _skipprevious ?: ImageVector.Builder(
            name = "SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6.0f, 6.0f)
                lineTo(8.0f, 6.0f)
                lineTo(8.0f, 18.0f)
                lineTo(6.0f, 18.0f)
                close()
                moveTo(9.5f, 12.0f)
                lineTo(18.0f, 18.0f)
                lineTo(18.0f, 6.0f)
                lineTo(9.5f, 12.0f)
                close()
            }
        }.build().also { _skipprevious = it }

    private var _back: ImageVector? = null
    val Back: ImageVector
        get() = _back ?: ImageVector.Builder(
            name = "Back",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20.0f, 11.0f)
                lineTo(7.83f, 11.0f)
                lineTo(13.42f, 5.41f)
                lineTo(12.0f, 4.0f)
                lineTo(4.0f, 12.0f)
                lineTo(12.0f, 20.0f)
                lineTo(13.41f, 18.59f)
                lineTo(7.83f, 13.0f)
                lineTo(20.0f, 13.0f)
                lineTo(20.0f, 11.0f)
                close()
            }
        }.build().also { _back = it }

    private var _replay15: ImageVector? = null
    val Replay15: ImageVector
        get() = _replay15 ?: ImageVector.Builder(
            name = "Replay15",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 5.0f)
                lineTo(12.0f, 1.0f)
                lineTo(7.0f, 6.0f)
                lineTo(12.0f, 11.0f)
                lineTo(12.0f, 7.0f)
                curveTo(15.31f, 7.0f, 18.0f, 9.69f, 18.0f, 13.0f)
                curveTo(18.0f, 16.31f, 15.31f, 19.0f, 12.0f, 19.0f)
                curveTo(8.69f, 19.0f, 6.0f, 16.31f, 6.0f, 13.0f)
                lineTo(4.0f, 13.0f)
                curveTo(4.0f, 17.42f, 7.58f, 21.0f, 12.0f, 21.0f)
                curveTo(16.42f, 21.0f, 20.0f, 17.42f, 20.0f, 13.0f)
                curveTo(20.0f, 8.58f, 16.42f, 5.0f, 12.0f, 5.0f)
                close()
            }
        }.build().also { _replay15 = it }

    private var _forward15: ImageVector? = null
    val Forward15: ImageVector
        get() = _forward15 ?: ImageVector.Builder(
            name = "Forward15",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 5.0f)
                lineTo(12.0f, 1.0f)
                lineTo(17.0f, 6.0f)
                lineTo(12.0f, 11.0f)
                lineTo(12.0f, 7.0f)
                curveTo(8.69f, 7.0f, 6.0f, 9.69f, 6.0f, 13.0f)
                curveTo(6.0f, 16.31f, 8.69f, 19.0f, 12.0f, 19.0f)
                curveTo(15.31f, 19.0f, 18.0f, 16.31f, 18.0f, 13.0f)
                lineTo(20.0f, 13.0f)
                curveTo(20.0f, 17.42f, 16.42f, 21.0f, 12.0f, 21.0f)
                curveTo(7.58f, 21.0f, 4.0f, 17.42f, 4.0f, 13.0f)
                curveTo(4.0f, 8.58f, 7.58f, 5.0f, 12.0f, 5.0f)
                close()
            }
        }.build().also { _forward15 = it }

    private var _heartoutline: ImageVector? = null
    val HeartOutline: ImageVector
        get() = _heartoutline ?: ImageVector.Builder(
            name = "HeartOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16.5f, 3.0f)
                curveTo(14.76f, 3.0f, 13.09f, 3.81f, 12.0f, 5.09f)
                curveTo(10.91f, 3.81f, 9.24f, 3.0f, 7.5f, 3.0f)
                curveTo(4.42f, 3.0f, 2.0f, 5.42f, 2.0f, 8.5f)
                curveTo(2.0f, 12.28f, 5.4f, 15.36f, 10.55f, 20.04f)
                lineTo(12.0f, 21.35f)
                lineTo(13.45f, 20.03f)
                curveTo(18.6f, 15.36f, 22.0f, 12.28f, 22.0f, 8.5f)
                curveTo(22.0f, 5.42f, 19.58f, 3.0f, 16.5f, 3.0f)
                close()
                moveTo(12.1f, 18.55f)
                lineTo(12.0f, 18.65f)
                lineTo(11.9f, 18.55f)
                curveTo(7.14f, 14.24f, 4.0f, 11.39f, 4.0f, 8.5f)
                curveTo(4.0f, 6.5f, 5.5f, 5.0f, 7.5f, 5.0f)
                curveTo(9.04f, 5.0f, 10.54f, 5.99f, 11.07f, 7.36f)
                lineTo(12.94f, 7.36f)
                curveTo(13.46f, 5.99f, 14.96f, 5.0f, 16.5f, 5.0f)
                curveTo(18.5f, 5.0f, 20.0f, 6.5f, 20.0f, 8.5f)
                curveTo(20.0f, 11.39f, 16.86f, 14.24f, 12.1f, 18.55f)
                close()
            }
        }.build().also { _heartoutline = it }

    private var _add: ImageVector? = null
    val Add: ImageVector
        get() = _add ?: ImageVector.Builder(
            name = "Add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.0f, 13.0f)
                lineTo(13.0f, 13.0f)
                lineTo(13.0f, 19.0f)
                lineTo(11.0f, 19.0f)
                lineTo(11.0f, 13.0f)
                lineTo(5.0f, 13.0f)
                lineTo(5.0f, 11.0f)
                lineTo(11.0f, 11.0f)
                lineTo(11.0f, 5.0f)
                lineTo(13.0f, 5.0f)
                lineTo(13.0f, 11.0f)
                lineTo(19.0f, 11.0f)
                lineTo(19.0f, 13.0f)
                close()
            }
        }.build().also { _add = it }

    private var _check: ImageVector? = null
    val Check: ImageVector
        get() = _check ?: ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9.0f, 16.17f)
                lineTo(4.83f, 12.0f)
                lineTo(3.41f, 13.41f)
                lineTo(9.0f, 19.0f)
                lineTo(21.0f, 7.0f)
                lineTo(19.59f, 5.59f)
                lineTo(9.0f, 16.17f)
                close()
            }
        }.build().also { _check = it }

    private var _morevert: ImageVector? = null
    val MoreVert: ImageVector
        get() = _morevert ?: ImageVector.Builder(
            name = "MoreVert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 8.0f)
                curveTo(13.1f, 8.0f, 14.0f, 7.1f, 14.0f, 6.0f)
                curveTo(14.0f, 4.9f, 13.1f, 4.0f, 12.0f, 4.0f)
                curveTo(10.9f, 4.0f, 10.0f, 4.9f, 10.0f, 6.0f)
                curveTo(10.0f, 7.1f, 10.9f, 8.0f, 12.0f, 8.0f)
                close()
                moveTo(12.0f, 10.0f)
                curveTo(10.9f, 10.0f, 10.0f, 10.9f, 10.0f, 12.0f)
                curveTo(10.0f, 13.1f, 10.9f, 14.0f, 12.0f, 14.0f)
                curveTo(13.1f, 14.0f, 14.0f, 13.1f, 14.0f, 12.0f)
                curveTo(14.0f, 10.9f, 13.1f, 10.0f, 12.0f, 10.0f)
                close()
                moveTo(12.0f, 16.0f)
                curveTo(10.9f, 16.0f, 10.0f, 16.9f, 10.0f, 18.0f)
                curveTo(10.0f, 19.1f, 10.9f, 20.0f, 12.0f, 20.0f)
                curveTo(13.1f, 20.0f, 14.0f, 19.1f, 14.0f, 18.0f)
                curveTo(14.0f, 16.9f, 13.1f, 16.0f, 12.0f, 16.0f)
                close()
            }
        }.build().also { _morevert = it }

    private var _close: ImageVector? = null
    val Close: ImageVector
        get() = _close ?: ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }.build().also { _close = it }

    private var _grid: ImageVector? = null
    val Grid: ImageVector
        get() = _grid ?: ImageVector.Builder(
            name = "Grid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3.0f, 3.0f)
                lineTo(11.0f, 3.0f)
                lineTo(11.0f, 11.0f)
                lineTo(3.0f, 11.0f)
                close()
                moveTo(13.0f, 3.0f)
                lineTo(21.0f, 3.0f)
                lineTo(21.0f, 11.0f)
                lineTo(13.0f, 11.0f)
                close()
                moveTo(3.0f, 13.0f)
                lineTo(11.0f, 13.0f)
                lineTo(11.0f, 21.0f)
                lineTo(3.0f, 21.0f)
                close()
                moveTo(13.0f, 13.0f)
                lineTo(21.0f, 13.0f)
                lineTo(21.0f, 21.0f)
                lineTo(13.0f, 21.0f)
                close()
            }
        }.build().also { _grid = it }

    private var _list: ImageVector? = null
    val List: ImageVector
        get() = _list ?: ImageVector.Builder(
            name = "List",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3.0f, 5.0f)
                lineTo(21.0f, 5.0f)
                lineTo(21.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                close()
                moveTo(3.0f, 11.0f)
                lineTo(21.0f, 11.0f)
                lineTo(21.0f, 13.0f)
                lineTo(3.0f, 13.0f)
                close()
                moveTo(3.0f, 17.0f)
                lineTo(21.0f, 17.0f)
                lineTo(21.0f, 19.0f)
                lineTo(3.0f, 19.0f)
                close()
            }
        }.build().also { _list = it }

}