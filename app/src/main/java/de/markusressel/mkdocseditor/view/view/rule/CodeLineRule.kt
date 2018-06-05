package de.markusressel.mkdocseditor.view.view.rule

import android.text.Editable

class CodeLineRule : CodeRule() {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(?m)^ {4}.+"
                .toRegex()
    }

}