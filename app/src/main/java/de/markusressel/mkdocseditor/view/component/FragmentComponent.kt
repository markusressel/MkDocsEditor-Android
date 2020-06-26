package de.markusressel.mkdocseditor.view.component

import android.content.Context
import de.markusressel.mkdocseditor.view.fragment.base.LifecycleFragmentBase

abstract class FragmentComponent(private val hostFragment: LifecycleFragmentBase) {

    protected val fragment
        get() = hostFragment

    val context: Context?
        get() = hostFragment.context

}