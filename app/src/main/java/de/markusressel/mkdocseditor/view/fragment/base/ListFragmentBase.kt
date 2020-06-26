/*
 * Copyright (C) 2018 Markus Ressel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.view.fragment.base

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.airbnb.epoxy.Typed3EpoxyController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import kotlinx.android.synthetic.main.fragment_recyclerview.*
import kotlinx.android.synthetic.main.layout_empty_list.*


/**
 * List base class
 */
abstract class ListFragmentBase : DaggerSupportFragmentBase() {

    override val layoutRes: Int
        get() = R.layout.fragment_recyclerview

    protected var lastScrollPosition by savedInstanceState(0)

    protected open val fabConfig: FabConfig = FabConfig(left = mutableListOf(), right = mutableListOf())
    private val fabButtonViews = mutableListOf<FloatingActionButton>()

    internal val epoxyController by lazy { createEpoxyController() }

    internal var currentSearchFilter: String by savedInstanceState("")

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.setController(epoxyController)
        recyclerView.layoutManager = StaggeredGridLayoutManager(
                resources.getInteger(R.integer.list_column_count),
                StaggeredGridLayoutManager.VERTICAL
        )

        setupFabs()
    }

    override fun onStop() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager != null && layoutManager is StaggeredGridLayoutManager) {
            val visibleItems = layoutManager.findFirstVisibleItemPositions(null)
            lastScrollPosition = if (visibleItems.isNotEmpty()) {
                visibleItems.first()
            } else {
                0
            }
        }

        super.onStop()
    }

    internal fun scrollToItemPosition(itemPosition: Int) {
        val layoutManager = recyclerView
                .layoutManager
        // this always returns 0 :(
//        if (itemPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION && layoutManager.childCount > 0) {
//            layoutManager
//                    .scrollToPosition((itemPosition.coerceIn(0, layoutManager.childCount - 1)))
//        }
    }

    /**
     * Create the epoxy controller here.
     * The epoxy controller defines what information is displayed.
     */
    abstract fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>

    /**EntityType
     * Reload list data from it's original source, persist it and display it to the user afterwards
     */
    abstract fun reloadDataFromSource()

    /**
     * Override this in subclasses if necessary
     */
    protected open fun getLeftFabs(): List<FabConfig.Fab> {
        return emptyList()
    }

    /**
     * Override this in subclasses if necessary
     */
    protected open fun getRightFabs(): List<FabConfig.Fab> {
        return emptyList()
    }

    private fun setupFabs() {
        speedDial.apply {
            setMainFabClosedDrawable(iconHandler.getFabIcon(MaterialDesignIconic.Icon.gmi_plus))
            setMainFabOpenedDrawable(iconHandler.getFabIcon(MaterialDesignIconic.Icon.gmi_close))

            val fabItems = (getLeftFabs() + getRightFabs()).map {
                SpeedDialActionItem.Builder(it.id, iconHandler.getFabIcon(it.icon))
                        .setLabel(it.description)
                        .create()
            }
            addAllActionItems(fabItems)

            setOnActionSelectedListener { actionItem ->
                val fabConfig = (getRightFabs() + getLeftFabs()).find { it.id == actionItem.id }
                if (fabConfig != null) {
                    fabConfig.onClick?.invoke()
                    // close the speed dial
                    false
                } else {
                    // keep the speed dial open
                    true
                }
            }
        }
    }

    /**
     * Show the "empty" screen
     */
    internal fun showEmpty() {
        recyclerView.visibility = View.INVISIBLE
        layoutEmpty.visibility = View.VISIBLE
    }

    /**
     * Hide the "empty" screen
     */
    internal fun hideEmpty() {
        recyclerView.visibility = View.VISIBLE
        layoutEmpty.visibility = View.INVISIBLE
    }

}