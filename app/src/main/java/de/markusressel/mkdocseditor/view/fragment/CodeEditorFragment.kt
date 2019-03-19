package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.*
import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.otaliastudios.zoom.ZoomEngine
import com.trello.rxlifecycle2.LifecycleProvider
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.snack
import de.markusressel.kodeeditor.library.view.CodeEditorLayout
import de.markusressel.kodeeditor.library.view.SelectionChangedListener
import de.markusressel.kodehighlighter.language.markdown.MarkdownSyntaxHighlighter
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.databinding.FragmentEditorBinding
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.kotlin.query
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Created by Markus on 07.01.2018.
 */
class CodeEditorFragment : DaggerSupportFragmentBase(), SelectionChangedListener {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

    @Inject
    lateinit var preferencesHolder: KutePreferencesHolder

    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager

    @Inject
    lateinit var chromeCustomTabManager: ChromeCustomTabManager

    @Inject
    lateinit var networkManager: NetworkManager

    private lateinit var codeEditorLayout: CodeEditorLayout

    private val safeArgs: CodeEditorFragmentArgs by lazy {
        CodeEditorFragmentArgs.fromBundle(arguments!!)
    }

    private val documentId: String
        get() = safeArgs.documentId

    private val backendHostname: String
        get() = safeArgs.host ?: preferencesHolder.restConnectionHostnamePreference.persistedValue

    private val backendPort: Int
        get() {
            return if (safeArgs.port != -1) {
                safeArgs.port
            } else {
                preferencesHolder.restConnectionPortPreference.persistedValue.toInt()
            }
        }

    private val backendUsesSsl: Boolean
        get() {
            // TODO:
            return preferencesHolder.restConnectionSslPreference.persistedValue
        }

    private var noConnectionSnackbar: Snackbar? = null

    private val viewModel: CodeEditorViewModel by lazy { ViewModelProviders.of(this).get(CodeEditorViewModel::class.java) }

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    private var currentText: String? by savedInstanceState()

    private var currentPosition by savedInstanceState(PointF())
    private var currentZoom: Float by savedInstanceState(1F)

    private var initialTextLoaded = false

    private lateinit var documentSyncManager: DocumentSyncManager

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            menu?.findItem(R.id.refresh)?.apply {
                val refreshIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                icon = refreshIcon
            }

            // set open in browser icon
            menu?.findItem(R.id.open_in_browser)?.apply {
                val openInBrowserIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
                icon = openInBrowserIcon
                if (preferencesHolder.webUriPreference.persistedValue.isBlank()) {
                    isVisible = false
                    isEnabled = false
                }

            }
        }, onOptionsMenuItemClicked = {
            val webBaseUri = preferencesHolder
                    .webUriPreference
                    .persistedValue

            when {
                it.itemId == R.id.open_in_browser -> {
                    val documentEntity = documentPersistenceManager
                            .standardOperation()
                            .query {
                                equal(DocumentEntity_.id, documentId)
                            }.findUnique()

                    documentEntity?.let { document ->
                        val host = preferencesHolder
                                .webUriPreference.persistedValue

                        val pagePath = when {
                            document.url == "index/" -> ""
                            else -> document.url // this value is already url encoded
                        }

                        val url = "$host/$pagePath"
                        chromeCustomTabManager.openChromeCustomTab(url)
                    }

                    true
                }
                webBaseUri.isBlank() -> false
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super.initComponents(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        val binding: FragmentEditorBinding = DataBindingUtil.inflate(layoutInflater, layoutRes, container, false)
        viewModel.getEntity(documentPersistenceManager, documentId).observe(this, Observer<List<DocumentEntity>> {
            if (it.isEmpty()) {
                Timber.w { "No document with id '$documentId' was found in offline cache" }
            } else {
                val entity = it.first()
                viewModel.documentId.value = entity.id
            }
        })

        binding.let {
            it.lifecycleOwner = this
            it.viewModel = viewModel
        }

        return binding
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        documentSyncManager = DocumentSyncManager(
                context = context(),
                hostname = backendHostname,
                port = backendPort,
                ssl = backendUsesSsl,
                basicAuthConfig = BasicAuthConfig(
                        preferencesHolder.basicAuthUserPreference.persistedValue,
                        preferencesHolder.basicAuthPasswordPreference.persistedValue),
                documentId = documentId,
                currentText = {
                    codeEditorLayout.codeEditorView.codeEditText.text?.toString() ?: ""
                },
                onConnectionStatusChanged = ::handleConnectionStatusChange,
                onInitialText = {
                    runOnUiThread {
                        restoreEditorState(text = it, editable = true)
                        loadingComponent.showContent()
                    }
                }, onTextChanged = ::onTextChanged)
    }

    private var textDisposable: Disposable? = null

    private fun handleConnectionStatusChange(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        if (connected) {
            watchTextChanges()

            runOnUiThread {
                codeEditorLayout.snack(R.string.connected, LENGTH_SHORT)
            }
        } else {
            noConnectionSnackbar?.dismiss()
            runOnUiThread {
                codeEditorLayout.editable = false
            }

            textDisposable?.dispose()

            saveEditorState()

            if (throwable != null) {
                Timber.e(throwable) { "Websocket error code: $errorCode" }
                noConnectionSnackbar = codeEditorLayout.snack(
                        text = R.string.server_unavailable,
                        duration = LENGTH_INDEFINITE,
                        actionTitle = R.string.retry,
                        action = {
                            reconnectToServer()
                        })
            } else {
                noConnectionSnackbar = codeEditorLayout.snack(
                        text = R.string.not_connected,
                        duration = LENGTH_INDEFINITE,
                        actionTitle = R.string.connect,
                        action = {
                            reconnectToServer()
                        })
            }
        }

        runOnUiThread {
            loadingComponent.showContent()
            updateOfflineBanner()
        }
    }

    private fun watchTextChanges() {
        textDisposable?.dispose()

        textDisposable = Observable.interval(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter { codeEditorLayout.editable }
                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
                .subscribeBy(onNext = {
                    documentSyncManager.sync()
                }, onError = {
                    // TODO: proper error handling for the user
                    Timber.e(it)
                    disconnect("Error in client sync code")
                })

//        textDisposable = RxTextView
//                .textChanges(codeEditorLayout.codeEditorView.codeEditText)
//                .skipInitialValue()
//                .debounce(100, TimeUnit.MILLISECONDS)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
//                .subscribeBy(onNext = {
//                    sendPatchIfChanged()
//                }, onError = {
//                    context?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
//                })
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeEditorLayout = view.findViewById(R.id.codeEditorView)
        codeEditorLayout.syntaxHighlighter = MarkdownSyntaxHighlighter()
        codeEditorLayout.codeEditorView.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {
                saveEditorState()
            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
//                val totalWidth = codeEditorLayout.contentLayout.width
//
//                val panX = engine.panX
//                val panY = engine.panY
//
//                val offsetX = engine.computeHorizontalScrollOffset()
//                val totalRangeX = engine.computeHorizontalScrollRange()
//                val percentage = engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange()
//
//                val offsetXMaybe = totalRangeX * percentage
//                val offsetFromPan = -panX
//                val test = 1
            }
        })
        codeEditorLayout.codeEditorView.selectionChangedListener = this

        // disable user input by default, it will be enabled automatically once connected to the server
        codeEditorLayout.editable = false
    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep [currentText]
     * @param editable when true the editor is editable, otherwise it is not
     */
    private fun restoreEditorState(text: String? = null, editable: Boolean) {
        val entity = getCachedEditorState()
        if (entity != null) {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                entity.text = text
                documentContentPersistenceManager.insertOrUpdate(
                        documentId = documentId, text = text)
                currentText = text
            } else {
                // restore values from cache
                currentText = entity.text
            }
            currentZoom = entity.zoomLevel
        } else {
            currentText = text
        }

        setEditorText(currentText ?: "")
        codeEditorLayout.editable = editable
        entity?.let {
            codeEditorLayout.codeEditorView.codeEditText.setSelection(entity.selection.coerceIn(0, currentText!!.length))
            codeEditorLayout.post {
                // zoom to last saved state
                val absolutePosition = computeAbsolutePosition(PointF(entity.panX, entity.panY))
                currentPosition.set(absolutePosition.x, absolutePosition.y)

                codeEditorLayout.codeEditorView.moveTo(currentZoom, absolutePosition.x, absolutePosition.y, true)
            }
        }

        initialTextLoaded = true
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setEditorText(text: String, selectionStart: Int? = null, selectionEnd: Int? = null) {
        // we don't listen to selection changes when the text is changed via code
        // because the selection will be restored from persistence anyway
        // and the listener would override this
        val editor = codeEditorLayout.codeEditorView
        editor.selectionChangedListener = null
        editor.text = text
        if (selectionStart != null) {
            val endIndex = selectionEnd ?: selectionStart
            editor.codeEditText.setSelection(selectionStart.coerceIn(0, text.length), endIndex.coerceIn(0, text.length))
        }
        editor.selectionChangedListener = this
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    private fun loadTextFromPersistence() {
        val entity = getCachedEditorState()

        if (entity != null) {
            restoreEditorState(editable = false)
        } else {
            MaterialDialog(context()).show {
                title(R.string.no_offline_version)
                negativeButton(android.R.string.ok, click = {
                    it.dismiss()
                })
                onDismiss {
                    navController.navigateUp()
                }
            }
        }

        loadingComponent.showContent()
    }

    /**
     * Returns the cache entity for this editor and document
     */
    private fun getCachedEditorState(): DocumentContentEntity? {
        return documentContentPersistenceManager.standardOperation().query {
            equal(DocumentContentEntity_.documentId, documentId)
        }.findUnique()
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    private fun reconnectToServer() {
        loadingComponent.showLoading()
        if (documentSyncManager.isConnected) {
            disconnect(reason = "Editor want's to refresh connection")
            documentSyncManager.connect()
        } else {
            documentSyncManager.connect()
        }
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        val oldSelectionStart = codeEditorLayout.codeEditorView.codeEditText.selectionStart
        val oldSelectionEnd = codeEditorLayout.codeEditorView.codeEditText.selectionEnd
        currentText = newText

        // set new cursor position
        val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, patches)
                .coerceIn(0, currentText?.length)
        val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, patches)
                .coerceIn(0, currentText?.length)

        setEditorText(currentText ?: "", newSelectionStart, newSelectionEnd)
        saveEditorState()
    }

    override fun onSelectionChanged(start: Int, end: Int, hasSelection: Boolean) {
        saveEditorState()
    }

    /**
     * Saves the current editor state in a persistent cache
     */
    private fun saveEditorState() {
        if (currentText == null) {
            // skip if there is no text to save
            return
        }

        currentPosition.set(codeEditorLayout.codeEditorView.panX, codeEditorLayout.codeEditorView.panY)
        currentZoom = codeEditorLayout.codeEditorView.zoom

        val positioningPercentage = getCurrentPositionPercentage()

        documentContentPersistenceManager.insertOrUpdate(
                documentId = documentId,
                text = currentText,
                selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
                zoomLevel = currentZoom,
                panX = positioningPercentage.x,
                panY = positioningPercentage.y
        )
    }

    /**
     * Calculates the positioning percentages for x and y axis
     *
     * @return a point with horizontal (x) and vertical (y) positioning percentages
     */
    private fun getCurrentPositionPercentage(): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange(),
                engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange())
    }

    /**
     * Takes a point with percentage values and returns a point with the actual absolute coordinates
     *
     * @return a point with horizontal (x) and vertical (y) absolute, scale-independent, positioning coordinate values
     */
    private fun computeAbsolutePosition(percentage: PointF): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(-1 * percentage.x * (engine.computeHorizontalScrollRange() / engine.realZoom),
                -1 * percentage.y * (engine.computeVerticalScrollRange() / engine.realZoom)
        )
    }

    private fun calculateNewSelectionIndex(oldSelection: Int, patches: LinkedList<diff_match_patch.Patch>): Int {
        var newSelection = oldSelection

        var currentIndex: Int
        // calculate how many characters have been inserted before the cursor
        patches.forEach { patch ->
            currentIndex = patch.start1

            patch.diffs.forEach { diff ->
                when (diff.operation) {
                    diff_match_patch.Operation.INSERT -> {
                        if (currentIndex < newSelection) {
                            newSelection += diff.text.length
                        }
                    }
                    diff_match_patch.Operation.DELETE -> {
                        if (currentIndex < newSelection) {
                            newSelection -= diff.text.length
                        }
                    }
                    else -> {
                        currentIndex += diff.text.length
                    }
                }
            }
        }

        return newSelection
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent.onCreateView(inflater, parent, savedInstanceState)
    }

    private val offlineModeObserver = Observer<Boolean> { enabled ->
        updateOfflineBanner()
        if (enabled) {
            disconnect(reason = "Offline mode was activated")
            loadTextFromPersistence()
        } else {
            reconnectToServer()
        }
    }

    private fun updateOfflineBanner() {
        runOnUiThread {
            viewModel.offlineModeEnabled.value =
                    !documentSyncManager.isConnected || offlineModeManager.isEnabled()
        }
    }

    override fun onStart() {
        super.onStart()

        offlineModeManager.isEnabled.observe(viewLifecycleOwner, offlineModeObserver)
    }

    override fun onPause() {
        super.onPause()

        saveEditorState()
        offlineModeManager.isEnabled.removeObserver(offlineModeObserver)
    }

    override fun onStop() {
        disconnect(reason = "Editor was closed")

        super.onStop()
    }

    private fun disconnect(reason: String = "None") {
        updateOfflineBanner()
        noConnectionSnackbar?.dismiss()
        codeEditorLayout.editable = false
        documentSyncManager.disconnect(1000, reason)
        textDisposable?.dispose()
    }

}