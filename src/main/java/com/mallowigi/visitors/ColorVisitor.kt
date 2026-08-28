/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Elior "Mallowigi" Boukhobza
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 *
 */
package com.mallowigi.visitors

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.mallowigi.config.home.ColorHighlighterState.Companion.instance
import com.mallowigi.config.home.HighlightingStyles
import com.mallowigi.highlighters.RoundedBackgroundPainter
import com.mallowigi.highlighters.RoundedHighlight
import com.mallowigi.highlighters.RoundedPaintStyle
import com.mallowigi.search.ColorMatch
import com.mallowigi.search.parsers.ColorParser
import java.awt.Color

/** Color visitor: This is the class that will colorize the texts representing colors. */
abstract class ColorVisitor : HighlightVisitor, LangVisitor, DumbAware {

  private var highlightInfoHolder: HighlightInfoHolder? = null
  internal val config = instance
  private val roundedStyles = setOf(
    HighlightingStyles.BACKGROUND,
    HighlightingStyles.BORDER,
    HighlightingStyles.UNDERLINE_PILL,
    HighlightingStyles.GLOW
  )

  /**
   * Highlight the element with the given color.
   *
   * @param element element representing a color
   * @param color color
   */
  fun highlight(element: PsiElement?, color: Color) {
    if (!instance.isEnabled) return
    assert(highlightInfoHolder != null)
    highlightInfoHolder!!.add(ColorHighlighter.highlightColor(element, color))
  }

  fun highlight(color: Color, range: IntRange) {
    if (!instance.isEnabled) return
    assert(highlightInfoHolder != null)
    highlightInfoHolder!!.add(ColorHighlighter.highlightColor(range, color))
  }

  /**
   * Analyze: runs the annotate action on the file.
   *
   * @param file the file to annotate
   * @param updateWholeFile whether to update the whole file
   * @param holder highlighting info holder
   * @param action action to run
   * @return
   */
  override fun analyze(
    file: PsiFile,
    updateWholeFile: Boolean,
    holder: HighlightInfoHolder,
    action: Runnable
  ): Boolean {
    highlightInfoHolder = holder
    val visitorKey = this::class.qualifiedName ?: this::class.java.name

    try {
      action.run()
    } finally {
      val style = instance.highlightingStyle
      if (instance.isEnabled && style in roundedStyles) {
        RoundedBackgroundPainter.apply(
          file = file,
          visitorKey = visitorKey,
          highlights = collectRoundedHighlights(file, style.toRoundedPaintStyle()),
          arcRadius = instance.roundedArcRadius
        )
      } else {
        RoundedBackgroundPainter.clear(
          file = file,
          visitorKey = visitorKey
        )
      }
      highlightInfoHolder = null
    }
    return true
  }

  /** Walks the whole file to compute the current, complete set of rounded highlights. */
  private fun collectRoundedHighlights(file: PsiFile, paintStyle: RoundedPaintStyle): List<RoundedHighlight> {
    val result = mutableListOf<RoundedHighlight>()

    file.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        when {
          canAcceptMultiple() -> acceptMultiple(element)?.forEach { match ->
            val absoluteRange = IntRange(
              element.textRange.startOffset + match.range.first,
              element.textRange.startOffset + match.range.last
            )
            result += RoundedHighlight(range = absoluteRange, color = match.color, paintStyle = paintStyle)
          }

          else                -> accept(element)?.let { color ->
            val textRange = element.textRange
            result += RoundedHighlight(
              range = IntRange(textRange.startOffset, textRange.endOffset),
              color = color,
              paintStyle = paintStyle
            )
          }
        }
        super.visitElement(element)
      }
    })

    return result
  }

  override fun visit(element: PsiElement) {
    when {
      this.canAcceptMultiple() -> {
        val colors = this.acceptMultiple(element)
        colors?.forEach { match ->
          val absoluteRange = IntRange(
            element.textRange.startOffset + match.range.first,
            element.textRange.startOffset + match.range.last
          )
          highlight(match.color, absoluteRange)
        }
      }

      else                     -> {
        val color = this.accept(element)
        color?.let { highlight(element, it) }
      }
    }
  }

  /**
   * Clones the visitor. This method is mandatory.
   *
   * @return instance of HighlightVisitor
   */
  abstract override fun clone(): HighlightVisitor

  override fun getParser(text: String): ColorParser? = null

  override fun shouldParseText(text: String): Boolean = false

  override fun shouldVisit(): Boolean = true

  override fun accept(element: PsiElement): Color? = null

  override fun canAcceptMultiple(): Boolean = false

  override fun acceptMultiple(element: PsiElement): List<ColorMatch>? = null

  private fun HighlightingStyles.toRoundedPaintStyle(): RoundedPaintStyle = when (this) {
    HighlightingStyles.BACKGROUND -> RoundedPaintStyle.BACKGROUND
    HighlightingStyles.BORDER -> RoundedPaintStyle.BORDER
    HighlightingStyles.UNDERLINE_PILL -> RoundedPaintStyle.UNDERLINE_PILL
    HighlightingStyles.GLOW -> RoundedPaintStyle.GLOW
    else -> RoundedPaintStyle.BACKGROUND
  }
}
