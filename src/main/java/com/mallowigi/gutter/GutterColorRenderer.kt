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
package com.mallowigi.gutter

import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.ui.ColorChooserService
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.mallowigi.ColorHighlighterBundle.message
import com.mallowigi.gutter.actions.*
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.datatransfer.StringSelection
import java.util.*
import javax.swing.Icon

class GutterColorRenderer(private val color: Color?, private val range: TextRange) : GutterIconRenderer() {
  override fun getIcon(): Icon = when {
    color != null -> {
      EditorColorsManager.getInstance().globalScheme.defaultForeground
        .let { ColorIcon(ICON_SIZE, ICON_SIZE, ICON_SIZE - 2, ICON_SIZE - 2, color, it, 3) }
        .let { JBUIScale.scaleIcon(it) }
    }

    else          -> JBUIScale.scaleIcon(EmptyIcon.create(ICON_SIZE))
  }

  override fun getTooltipText(): String = message("choose.color")

  override fun getPopupMenuActions(): ActionGroup {
    return DefaultActionGroup(
      CopyAndroidArgb(color),
      CopyAndroidRgb(color),
      CopyHexAction(color),
      CopyRgbAction(color),
      CopyRgbaAction(color),
      CopyHslAction(color),
      CopyHslaAction(color),
      CopyJavaColorResource(color),
      CopyKotlinColorResource(color),
      CopyJavaRgb(color),
      CopyJavaRgba(color),
      CopyKotlinRgb(color),
      CopyKotlinRgba(color),
      CopyNetRgb(color),
      CopyNetArgb(color),
      CopyNSColorHsb(color),
      CopyNSColorHsba(color),
      CopyUIColorHsb(color),
      CopyUIColorHsba(color),
      CopySwiftHsb(color),
      CopySwiftHsba(color)
    )
  }

  override fun getClickAction(): @NonNls AnAction = object : AnAction(message("choose.color1")) {
    override fun actionPerformed(e: AnActionEvent) {
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return
      val currentColor = color ?: return

      val rangeMarker = when {
        canReplaceColor(editor) -> editor.document.createRangeMarker(range).also {
          it.isGreedyToLeft = true
          it.isGreedyToRight = true
        }

        else                    -> null
      }

      ColorChooserService.getInstance().showPopup(
        project = editor.project,
        currentColor = currentColor,
        editor = editor,
        listener = { newColor, _ ->
          applyColor(
            editor = editor,
            rangeMarker = rangeMarker,
            currentColor = currentColor,
            newColor = newColor
          )
        },
        showAlpha = currentColor.alpha != 255,
        showAlphaAsPercent = false,
        popupCloseListener = null
      )
    }

    /**
     * Make sure the document is writable before applying color
     */
    private fun canReplaceColor(editor: Editor): Boolean {
      if (editor.isViewer || !editor.document.isWritable) return false

      val project = editor.project ?: return false
      val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
      if (!virtualFile.isWritable) return false

      // Reader Mode is visual and does not necessarily make the editor or document read-only.
      val readerModeSettings = ReaderModeSettings.getInstance(project)
      return !readerModeSettings.enabled || !ReaderModeSettings.matchMode(project, virtualFile, editor)
    }

    private fun applyColor(editor: Editor, rangeMarker: RangeMarker?, currentColor: Color, newColor: Color?) {
      if (newColor == null || newColor == currentColor) return

      if (rangeMarker != null && canReplaceColor(editor)) {
        replaceColor(editor, rangeMarker, newColor)
      } else {
        copyColor(currentColor, newColor)
      }
    }

    private fun replaceColor(editor: Editor, rangeMarker: RangeMarker, newColor: Color) {
      if (!rangeMarker.isValid) return

      val document = rangeMarker.document
      val startOffset = rangeMarker.startOffset
      val endOffset = rangeMarker.endOffset

      if (startOffset < 0 || endOffset > document.textLength || startOffset >= endOffset) return
      val currentText = document.getText(TextRange(startOffset, endOffset))
      val replacement = formatReplacement(currentText, newColor)
      if (replacement == currentText) return

      WriteCommandAction.runWriteCommandAction(editor.project) {
        document.replaceString(startOffset, endOffset, replacement)
      }
    }

    private fun formatReplacement(original: String, color: Color): String {
      val hexColor = Regex("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")
      val quote = original.firstOrNull()?.takeIf { (it == '\'' || it == '\"') && original.lastOrNull() == it }

      val value = when (quote) {
        null -> original
        else -> original.substring(1, original.length - 1)
      }

      val prefix = when {
        value.startsWith("#")  -> "#"
        value.startsWith("0x") -> "0x"
        value.startsWith("0X") -> "0X"
        else                   -> ""
      }
      val digits = value.removePrefix(prefix)

      val formatted = when {
        hexColor.matches(digits) -> prefix + formatHex(color, digits)
        else                     -> (if (quote == null) "#" else "") + formatHex(color, "000000")
      }

      return when (quote) {
        null -> formatted
        else -> "$quote$formatted$quote"
      }
    }

    private fun formatHex(color: Color, template: String): String {
      val rgb = "%02x%02x%02x".format(color.red, color.green, color.blue)
      val alpha = "%02x".format(color.alpha)

      var result = when (template.length) {
        8    -> rgb + alpha
        3    -> rgb.chunked(2).takeIf { pairs -> pairs.all { it.first() == it.last() } }
          ?.joinToString("") { it.first().toString() }
          ?: rgb

        else -> rgb
      }

      if (template.any(Char::isLetter) && template.filter(Char::isLetter).all(Char::isUpperCase)) {
        result = result.uppercase(Locale.ROOT)
      }
      return result
    }

    private fun copyColor(currentColor: Color, newColor: Color?) {
      if (newColor == null || newColor == currentColor) return

      @Suppress("UsePropertyAccessSyntax")
      CopyPasteManager.getInstance().setContents(StringSelection(ColorUtil.toHex(newColor, false)))
    }
  }

  override fun isNavigateAction(): Boolean = true

  override fun equals(other: Any?): Boolean {
    return when {
      this === other                                -> true
      other == null || javaClass != other.javaClass -> false
      else                                          -> {
        val renderer = other as GutterColorRenderer
        color == renderer.color
      }
    }
  }

  override fun hashCode(): Int = Objects.hash(color)

  override fun getAlignment(): Alignment = Alignment.LEFT

  companion object {
    private const val ICON_SIZE = 12
  }
}
