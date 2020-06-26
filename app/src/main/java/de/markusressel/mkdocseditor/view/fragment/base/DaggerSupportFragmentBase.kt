/*
 * DataMunch by Markus Ressel
 * Copyright (c) 2018.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.view.fragment.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import androidx.navigation.NavController
import androidx.navigation.Navigation
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.IconHandler
import javax.inject.Inject


/**
 * Base class for implementing a fragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerSupportFragmentBase : LifecycleFragmentBase() {

    @Inject
    protected lateinit var iconHandler: IconHandler

    protected val navController: NavController
        get() = Navigation.findNavController(requireActivity(), R.id.navHostFragment)

    /**
     * The layout resource for this Activity
     */
    @get:LayoutRes
    protected abstract val layoutRes: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val viewModel = createViewDataBinding(inflater, container, savedInstanceState)
        return if (viewModel != null) {
            viewModel.root
        } else {
            val newContainer = inflater.inflate(layoutRes, container, false) as ViewGroup
            val alternative = super.onCreateView(inflater, newContainer, savedInstanceState)
            alternative ?: newContainer
        }
    }

    /**
     * Optionally create and setup your ViewDataBinding and ViewModel in this method
     */
    open fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? = null

}