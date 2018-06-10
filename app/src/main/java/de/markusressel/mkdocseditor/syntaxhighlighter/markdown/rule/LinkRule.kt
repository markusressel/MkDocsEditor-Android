package de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule

import android.graphics.Color
import android.text.Editable
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan

class LinkRule : HighlighterRuleBase() {

    override val styles = setOf<() -> CharacterStyle>({ ForegroundColorSpan(COLOR) })

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(?<!!)\\[.*?]\\(.*?\\)"
                .toRegex()
        val COLOR = Color
                .parseColor("#7C4DFF")
    }

}