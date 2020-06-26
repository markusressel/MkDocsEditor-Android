package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.airbnb.epoxy.Typed3EpoxyController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.material.toast
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.*
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.listItemDocument
import de.markusressel.mkdocseditor.listItemResource
import de.markusressel.mkdocseditor.listItemSection
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocseditor.view.viewmodel.FileBrowserViewModel
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class FileBrowserFragment : MultiPersistableListFragmentBase() {

    @Inject
    lateinit var sectionPersistenceManager: SectionPersistenceManager
    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager
    @Inject
    lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager
    @Inject
    lateinit var resourcePersistenceManager: ResourcePersistenceManager

    private val fileBrowserViewModel: FileBrowserViewModel by lazy {
        ViewModelProviders.of(this).get(FileBrowserViewModel::class.java)
    }

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        if (fileBrowserViewModel.sectionPersistenceManager == null) {
            fileBrowserViewModel.sectionPersistenceManager = sectionPersistenceManager
            fileBrowserViewModel.documentPersistenceManager = documentPersistenceManager
            fileBrowserViewModel.resourcePersistenceManager = resourcePersistenceManager
            if (fileBrowserViewModel.currentSectionId.value == null) {
                fileBrowserViewModel.currentSectionId.value = FileBrowserViewModel.ROOT_SECTION_ID
            }

            // search
            fileBrowserViewModel.currentSearchResults.observe(this, Observer {
                if (it.isEmpty()) {
                    showEmpty()
                } else {
                    hideEmpty()
                }
                epoxyController.setData(it.filterByExpectedType(), it.filterByExpectedType(), it.filterByExpectedType())
            })

            // normal navigation
            fileBrowserViewModel.currentSection.observe(this, Observer {
                if (it.isNotEmpty()) {
                    it.first().let {
                        if (it.subsections.isEmpty() and it.documents.isEmpty() and it.resources.isEmpty()) {
                            showEmpty()
                        } else {
                            hideEmpty()
                        }
                        epoxyController.setData(it.subsections, it.documents, it.resources)
                    }
                } else {
                    // in theory this will navigate back until a section is found
                    // or otherwise show the "empty" screen
                    if (!fileBrowserViewModel.navigateUp()) {
                        showEmpty()
                    }
                }
            })

            fileBrowserViewModel.currentSearchFilter.observe(this, Observer {
                searchView?.setQuery(it, false)
            })
            fileBrowserViewModel.isSearchExpanded.observe(this, Observer { isExpanded ->
                if (!isExpanded) {
                    searchView?.clearFocus()
                    searchMenuItem?.collapseActionView()
                }
            })
        }

        return super.createViewDataBinding(inflater, container, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(de.markusressel.kutepreferences.core.R.menu.kutepreferences__menu, menu)

        searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem?.apply {
            icon = ContextCompat.getDrawable(context as Context, de.markusressel.kutepreferences.core.R.drawable.ic_search_24px)
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    val oldValue = fileBrowserViewModel.isSearchExpanded.value
                    if (oldValue == null || !oldValue) {
                        fileBrowserViewModel.isSearchExpanded.value = true
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    val oldValue = fileBrowserViewModel.isSearchExpanded.value
                    if (oldValue == null || oldValue) {
                        fileBrowserViewModel.isSearchExpanded.value = false
                    }
                    return true
                }
            })
        }

        searchView = searchMenuItem?.actionView as SearchView
        searchView?.let {
            RxSearchView
                    .queryTextChanges(it)
                    .skipInitialValue()
                    .bindUntilEvent(this, Lifecycle.Event.ON_DESTROY)
                    .debounce(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onNext = { text ->
                        fileBrowserViewModel.setSearch(text.toString())
                    }, onError = { error ->
                        Timber.e(error) { "Error filtering list" }
                    })
        }
    }

    override fun getLoadDataFromSourceFunction(): Single<Any> {
        return restClient.getItemTree() as Single<Any>
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity(documentContentPersistenceManager)
            else -> throw IllegalArgumentException("Cant map object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        // update existing entities
        val rootSection = data as SectionEntity

        sectionPersistenceManager.insertOrUpdateRoot(rootSection)
        fileBrowserViewModel.currentSectionId.postValue(fileBrowserViewModel.currentSectionId.value)
    }

    override fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>> {
        return object : Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>() {
            override fun buildModels(sections: List<SectionEntity>, documents: List<DocumentEntity>, resources: List<ResourceEntity>) {
                sections.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemSection {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            fileBrowserViewModel.openSection(model.item().id)
                        }
                    }
                }

                documents.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemDocument {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openDocumentEditor(model.item().id)
                        }
                    }
                }

                resources.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemResource {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openResourceDetailPage(model.item())
                        }
                    }
                }
            }
        }
    }

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(
                FabConfig.Fab(id = 0,
                        description = R.string.speed_dial_create_document,
                        icon = MaterialDesignIconic.Icon.gmi_file_add,
                        onClick = {
                            openCreateDocumentDialog()
                        }),
                FabConfig.Fab(id = 1,
                        description = R.string.speed_dial_create_section,
                        icon = MaterialDesignIconic.Icon.gmi_folder,
                        onClick = {
                            openCreateSectionDialog()
                        }))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Bus.observe<OfflineModeChangedEvent>()
                .subscribe {
                    //                    fileBrowserViewModel.setOfflineMode(it.enabled)
                }
                .registerInBus(this)
    }

    private fun openDocumentEditor(documentId: String) {
        Timber.d { "Opening Document '$documentId'" }

        navController.navigate(
                FileBrowserFragmentDirections.actionFileBrowserPageToCodeEditorPage(documentId,
                        null)
        )
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber.d { "Opening Resource '${resource.name}'" }
        context?.toast("Resources are not yet supported :(", Toast.LENGTH_LONG)
    }

    private fun openCreateSectionDialog() {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = sectionPersistenceManager.findById(currentSectionId)!!
        val existingSections = parentSection.subsections.map { it.name }

        MaterialDialog(context()).show {
            title(R.string.speed_dial_create_section)
            input(waitForPositiveButton = false,
                    allowEmpty = false,
                    hintRes = R.string.hint_new_section,
                    inputType = InputType.TYPE_CLASS_TEXT) { dialog, text ->

                val trimmedText = text.toString().trim()

                val inputField = dialog.getInputField()
                val isValid = !existingSections.contains(trimmedText)

                inputField.error = when (isValid) {
                    true -> null
                    false -> getString(R.string.error_section_already_exists)
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
            }

            positiveButton(android.R.string.ok, click = {
                createNewSection(getInputField().text.toString().trim())
            })
            negativeButton(android.R.string.cancel)
        }
    }

    private fun openCreateDocumentDialog() {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = sectionPersistenceManager.findById(currentSectionId)!!
        val existingDocuments = parentSection.documents.map { it.name }

        MaterialDialog(context()).show {
            title(R.string.speed_dial_create_document)
            input(waitForPositiveButton = false,
                    allowEmpty = false,
                    hintRes = R.string.hint_new_document,
                    inputType = InputType.TYPE_CLASS_TEXT) { dialog, text ->

                val trimmedText = text.toString().trim()

                val inputField = dialog.getInputField()
                val isValid = !existingDocuments.contains(trimmedText)

                inputField.error = when (isValid) {
                    true -> null
                    false -> getString(R.string.error_document_already_exists)
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
            }

            positiveButton(android.R.string.ok, click = {
                createNewDocument(getInputField().text.toString().trim())
            })
            negativeButton(android.R.string.cancel)
        }
    }

    private fun createNewSection(name: String) {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = sectionPersistenceManager.findById(currentSectionId)
        if (parentSection == null) {
            Timber.e { "Parent section could not be found in persistence while trying to create a new section in it" }
            return
        }

        restClient.createSection(currentSectionId, name)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onSuccess = {
                    val createdSection = it.asEntity(documentContentPersistenceManager)
                    parentSection.subsections.add(createdSection)
                    // insert it into persistence
                    sectionPersistenceManager.standardOperation().put(parentSection)
                }, onError = { error ->
                    Timber.e(error) { "Error creating section" }
                    context().toast("There was an error :(")
                })
    }

    private fun createNewDocument(name: String) {
        val documentName = if (name.isEmpty()) "New Document" else name
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = sectionPersistenceManager.findById(currentSectionId)
        if (parentSection == null) {
            Timber.e { "Parent section could not be found in persistence while trying to create a new document in it" }
            return
        }

        val d = restClient.createDocument(
                fileBrowserViewModel.currentSectionId.value!!, documentName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onSuccess = {
                    // insert it into persistence
                    documentPersistenceManager.standardOperation().put(
                            it.asEntity(parentSection = parentSection))
                    // and open the editor right away
                    openDocumentEditor(it.id)
                }, onError = { error ->
                    Timber.e(error) { "Error creating document" }
                    context().toast("There was an error :(")
                })
    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        return fileBrowserViewModel.navigateUp()
    }

}