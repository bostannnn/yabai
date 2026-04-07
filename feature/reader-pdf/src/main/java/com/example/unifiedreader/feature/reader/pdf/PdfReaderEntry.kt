package com.example.unifiedreader.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(
    item: ReadableItem,
    documentUri: Uri? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val renderedPage by produceState<PdfRenderState>(
        initialValue = if (documentUri == null) {
            PdfRenderState.Empty
        } else {
            PdfRenderState.Loading
        },
        key1 = item.id,
        key2 = documentUri?.toString(),
    ) {
        value = if (documentUri == null) {
            PdfRenderState.Empty
        } else {
            withContext(Dispatchers.IO) {
                renderPdfPreview(
                    context = context.applicationContext,
                    documentUri = documentUri,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = when (renderedPage) {
                PdfRenderState.Empty ->
                    "PDF reader shell. Local imported PDFs now render the first page; sample content still uses a placeholder until a bundled PDF exists."
                PdfRenderState.Loading ->
                    "Opening local PDF preview…"
                is PdfRenderState.Error ->
                    "Unable to open this PDF."
                is PdfRenderState.Ready ->
                    "Local PDF import preview. Next step is multi-page navigation, zoom, and persistent PDF progress."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            when (val state = renderedPage) {
                PdfRenderState.Empty,
                PdfRenderState.Loading,
                is PdfRenderState.Error -> PdfPlaceholder(
                    message = when (state) {
                        PdfRenderState.Empty -> "PDF page viewport placeholder"
                        PdfRenderState.Loading -> "Loading PDF preview…"
                        is PdfRenderState.Error -> state.message
                        is PdfRenderState.Ready -> error("Unreachable")
                    },
                )

                is PdfRenderState.Ready -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.WHITE)
                        }
                    },
                    update = { imageView ->
                        imageView.setImageBitmap(state.bitmap)
                    },
                )
            }
        }
    }
}

@Composable
private fun PdfPlaceholder(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun renderPdfPreview(
    context: Context,
    documentUri: Uri,
): PdfRenderState = runCatching {
    context.contentResolver.openFileDescriptor(documentUri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount <= 0) {
                return PdfRenderState.Error("This PDF has no pages.")
            }
            renderer.openPage(0).use { page ->
                val width = page.width.coerceAtLeast(1)
                val height = page.height.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfRenderState.Ready(bitmap)
            }
        }
    } ?: PdfRenderState.Error("Unable to open this PDF.")
}.getOrElse {
    PdfRenderState.Error("Unable to open this PDF.")
}

private sealed interface PdfRenderState {
    data object Empty : PdfRenderState
    data object Loading : PdfRenderState
    data class Ready(val bitmap: Bitmap) : PdfRenderState
    data class Error(val message: String) : PdfRenderState
}
