package ru.spgsroot.peachy.ui.gallery

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.db.PhotoEntity
import java.io.File
import kotlin.math.min

/** Separate window covers player/system chrome without changing the Activity or playback state. */
@Composable
fun PhotoViewer(photo: PhotoEntity, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { /* This dialog owns its window; the Activity's bars are untouched. */ }
        }
        var viewport by remember { mutableStateOf(IntSize.Zero) }
        // Use Coil's decoded, EXIF-oriented dimensions, not encoded PhotoEntity dimensions.
        var imageSize by remember(photo.filePath) { mutableStateOf(Size.Zero) }
        var transform by remember(photo.filePath, viewport) { mutableStateOf(PhotoTransform()) }
        val context = LocalContext.current
        val request = remember(context, photo.filePath) {
            ImageRequest.Builder(context).data(File(photo.filePath)).build()
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black).clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(photo.filePath, viewport, imageSize) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (imageSize.width > 0f && imageSize.height > 0f) {
                            val width = viewport.width.toFloat()
                            val height = viewport.height.toFloat()
                            val fit = min(width / imageSize.width, height / imageSize.height)
                            transform = transform.change(
                                zoom, pan.x, pan.y, centroid.x - width / 2f, centroid.y - height / 2f,
                                width, height, imageSize.width * fit, imageSize.height * fit
                            )
                        }
                    }
                }
        ) {
            // Coil's image decoder applies EXIF rotation and mirroring to the original file.
            AsyncImage(
                model = request,
                contentDescription = photo.title,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val drawable = state.result.drawable
                    imageSize = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.x
                    translationY = transform.y
                }
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
                Icon(Icons.Default.Close, stringResource(R.string.gallery_photo_close), tint = Color.White)
            }
        }
    }
}
