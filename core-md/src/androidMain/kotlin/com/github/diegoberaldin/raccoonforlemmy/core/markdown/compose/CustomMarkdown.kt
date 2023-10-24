package com.github.diegoberaldin.raccoonforlemmy.core.markdown.compose

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import com.github.diegoberaldin.raccoonforlemmy.core.markdown.di.getMarkwonProvider
import com.github.diegoberaldin.raccoonforlemmy.core.markdown.model.MarkdownColors
import com.github.diegoberaldin.raccoonforlemmy.core.markdown.model.MarkdownPadding
import com.github.diegoberaldin.raccoonforlemmy.core.markdown.model.MarkdownTypography
import com.github.diegoberaldin.raccoonforlemmy.core.markdown.model.ReferenceLinkHandlerImpl
import io.noties.markwon.image.AsyncDrawableSpan
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor

/*
 * CREDITS:
 * https://github.com/dessalines/jerboa/blob/main/app/src/main/java/com/jerboa/ui/components/common/MarkdownHelper.kt
 */
@Composable
actual fun CustomMarkdown(
    content: String,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    padding: MarkdownPadding,
    modifier: Modifier,
    flavour: MarkdownFlavourDescriptor,
    onOpenUrl: ((String) -> Unit)?,
    inlineImages: Boolean,
    autoLoadImages: Boolean,
    onOpenImage: ((String) -> Unit)?,
    onClick: (() -> Unit)?,
) {
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides ReferenceLinkHandlerImpl(),
        LocalMarkdownPadding provides padding,
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
    ) {
        val markwonProvider = getMarkwonProvider(
            onOpenUrl = onOpenUrl,
            onOpenImage = onOpenImage,
        )
        BoxWithConstraints(
            modifier = modifier.clickable { onClick?.invoke() }
        ) {
            val style = LocalMarkdownTypography.current.text
            val fontScale = LocalDensity.current.fontScale * 1.25f
            val canvasWidthMaybe = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            val textSizeMaybe = with(LocalDensity.current) { (style.fontSize * fontScale).toPx() }
            val defaultColor = LocalMarkdownColors.current.text
            val resolver: FontFamily.Resolver = LocalFontFamilyResolver.current
            val typeface: Typeface = remember(resolver, style) {
                resolver.resolve(
                    fontFamily = style.fontFamily,
                    fontWeight = style.fontWeight ?: FontWeight.Normal,
                    fontStyle = style.fontStyle ?: FontStyle.Normal,
                    fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
                )
            }.value as Typeface

            AndroidView(
                factory = { ctx ->
                    createTextView(
                        context = ctx,
                        textColor = defaultColor,
                        style = style,
                        typeface = typeface,
                        fontSize = style.fontSize * fontScale,
                    ).apply {
                        setOnClickListener {
                            onClick?.invoke()
                        }
                    }
                },
                update = { textView ->
                    val md = markwonProvider.markwon.toMarkdown(content)
                    for (img in md.getSpans(0, md.length, AsyncDrawableSpan::class.java)) {
                        img.drawable.initWithKnownDimensions(canvasWidthMaybe, textSizeMaybe)
                    }
                    markwonProvider.markwon.setParsedMarkdown(textView, md)
                },
            )
        }
    }
}

private fun createTextView(
    context: Context,
    textColor: Color,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    typeface: Typeface? = null,
    style: TextStyle,
    @IdRes viewId: Int? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: ((View) -> Boolean)? = null,
): TextView {
    val mergedStyle = style.merge(
        TextStyle(
            color = textColor,
            fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize,
            textAlign = textAlign,
        ),
    )
    return TextView(context).apply {
        onClick?.let { setOnClickListener { onClick() } }
        onLongClick?.let { setOnLongClickListener(it) }
        setTextColor(textColor.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, mergedStyle.fontSize.value)
        width = maxWidth

        viewId?.let { id = viewId }
        textAlign?.let { align ->
            textAlignment = when (align) {
                TextAlign.Left, TextAlign.Start -> View.TEXT_ALIGNMENT_TEXT_START
                TextAlign.Right, TextAlign.End -> View.TEXT_ALIGNMENT_TEXT_END
                TextAlign.Center -> View.TEXT_ALIGNMENT_CENTER
                else -> View.TEXT_ALIGNMENT_TEXT_START
            }
        }

        this.typeface = typeface
    }
}