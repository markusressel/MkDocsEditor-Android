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

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import de.markusressel.commons.android.material.snack
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_recyclerview.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Created by Markus on 29.01.2018.
 */
abstract class MultiPersistableListFragmentBase : ListFragmentBase() {

    @Inject
    lateinit var restClient: MkDocsRestClient

    private var serverUnavailableSnackbar: Snackbar? = null

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(hostFragment = this,
                optionsMenuRes = R.menu.options_menu_list,
                onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
                    val refreshMenuItem = menu?.findItem(R.id.refresh)
                    refreshMenuItem?.icon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)

                    val searchMenuItem = menu?.findItem(R.id.search)
                    searchMenuItem?.icon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_search)

                    val searchView = searchMenuItem?.actionView as SearchView?
                    searchView?.let {
                        RxSearchView
                                .queryTextChanges(it)
                                .skipInitialValue()
                                .bindUntilEvent(this, Lifecycle.Event.ON_DESTROY)
                                .debounce(300, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(onNext = {
                                    currentSearchFilter = it.toString()

                                    // TODO: implement search
                                }, onError = {
                                    Timber
                                            .e(it) { "Error filtering list" }
                                })
                    }

                }, onOptionsMenuItemClicked = {
            when {
                it.itemId == R.id.refresh -> {
                    reload()
                    true
                }
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super.initComponents(context)
        optionsMenuComponent
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: this should only be done if the viewmodel is not already initialized
        //  as this resets the current position of the filebrowser, as well as any existing
        //  search term
        reload()
    }

    fun reload() {
        restClient
                .isHostAlive()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
                .subscribeBy(onSuccess = {
                    reloadDataFromSource()
                    serverUnavailableSnackbar?.dismiss()
                }, onError = {
                    if (it is CancellationException) {
                        Timber.d { "Reload cancelled" }
                    } else {
                        Timber.e(it)
                        serverUnavailableSnackbar?.dismiss()
                        serverUnavailableSnackbar = recyclerView.snack(
                                text = R.string.server_unavailable,
                                duration = Snackbar.LENGTH_INDEFINITE,
                                actionTitle = R.string.retry,
                                action = {
                                    reload()
                                })
                    }
                })
    }

    /**
     * Reload list data from it's original source, persist it and display it to the user afterwards
     */
    override fun reloadDataFromSource() {
        getLoadDataFromSourceFunction()
                .map {
                    mapToEntity(it)
                }
                .map {
                    persistListData(it)
                    it
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
                .subscribeBy(onSuccess = {
                    updateLastUpdatedFromSource()
                }, onError = {
                    if (it is CancellationException) {
                        Timber.d { "reload from source cancelled" }
                    }
                })
    }

    /**
     * Define a Single that returns the complete list of data from the (server) source
     */
    internal abstract fun getLoadDataFromSourceFunction(): Single<Any>

    /**
     * Map the source object to the persistence object
     */
    abstract fun mapToEntity(it: Any): IdentifiableListItem

    /**


     * Persist the current list data
     */
    internal abstract fun persistListData(data: IdentifiableListItem)

    private fun getLastUpdatedFromSource(): Long {
        // TODO:
        return 0
    }

    private fun updateLastUpdatedFromSource() {
        // TODO:
    }

}
