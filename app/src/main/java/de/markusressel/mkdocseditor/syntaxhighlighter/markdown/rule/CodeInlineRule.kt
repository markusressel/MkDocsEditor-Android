package de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule

import android.text.Editable

class CodeInlineRule : CodeRule() {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(`{1,3})([^`]+?)\\1"
                .toRegex()
    }

}