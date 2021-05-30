package de.markusressel.mkdocseditor.feature.editor

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.lifecycle.Transformations.switchMap
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.DataRepository
import de.markusressel.mkdocseditor.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.editor.CodeEditorViewModel.CodeEditorEvent.*
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    val dataRepository: DataRepository,
    val preferencesHolder: KutePreferencesHolder,
    val networkManager: NetworkManager,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    val events = MutableLiveData<CodeEditorEvent>()

    val documentId = savedStateHandle.getLiveData<String>("documentId")

    val documentEntity: LiveData<Resource<DocumentEntity?>> = switchMap(documentId) { documentId ->
        dataRepository.getDocument(documentId).asLiveData()
    }

    /**
     * Indicates whether the edit mode can be activated or not
     */
    val editable = MediatorLiveData<Boolean>().apply {
        addSource(offlineModeManager.isEnabled) { value ->
            setValue(value.not())
        }
    }

    /**
     * Indicates whether the CodeEditor is in "edit" mode or not
     */
    val editModeActive = MutableLiveData(false)

    val offlineModeBannerVisibility = MediatorLiveData<Int>().apply {
        addSource(offlineModeManager.isEnabled) { value ->
            when (value) {
                true -> setValue(View.VISIBLE)
                else -> setValue(View.GONE)
            }
        }
    }

    val loading = MutableLiveData(true)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    var currentText: MutableLiveData<String?> = MutableLiveData(null)

    val currentPosition = PointF()
    val currentZoom = MutableLiveData(1F)

    private val documentSyncManager = DocumentSyncManager(
        hostname = preferencesHolder.restConnectionHostnamePreference.persistedValue,
        port = preferencesHolder.restConnectionPortPreference.persistedValue.toInt(),
        ssl = preferencesHolder.restConnectionSslPreference.persistedValue,
        basicAuthConfig = BasicAuthConfig(
            preferencesHolder.basicAuthUserPreference.persistedValue,
            preferencesHolder.basicAuthPasswordPreference.persistedValue
        ),
        documentId = documentId.value!!,
        currentText = {
            currentText.value.orEmpty()
        },
        onConnectionStatusChanged = { connected, errorCode, throwable ->
            runOnUiThread {
                events.value = ConnectionStatus(connected, errorCode, throwable)
                if (connected) {
                    editModeActive.value =
                        preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue
                }
            }
        },
        onInitialText = {
            runOnUiThread {
                currentText.value = it
                events.value = InitialText(it)
                loading.value = false
            }

            // when an entity exists and a new text is given update the entity
            documentId.value?.let { documentId ->
                updateDocumentContentInCache(
                    documentId = documentId,
                    text = it
                )
            }

            // launch coroutine to continuously watch for changes
            watchTextChanges()
        }, onTextChanged = ::onTextChanged
    )

    init {
        documentEntity.observeForever {
            when (it) {
                is Success -> {
                    reconnectToServer()
                }
            }
        }
    }

    private fun watchTextChanges() {
        val syncInterval = preferencesHolder.codeEditorSyncIntervalPreference.persistedValue

        viewModelScope.launch {
            try {
                try {
                    while (documentSyncManager.isConnected) {
                        documentSyncManager.sync()
                        delay(syncInterval)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex)
                    disconnect("Error in client sync code")
                    events.postValue(Error(throwable = ex))
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    fun loadTextFromPersistence() {
        loading.value = false
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        events.value = TextChange(newText, patches)
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    fun reconnectToServer() {
        loading.value = true
        if (documentSyncManager.isConnected) {
            documentSyncManager.disconnect(1000, reason = "Editor want's to refresh connection")
        }
        documentSyncManager.connect()
    }

    fun disconnect(reason: String = "None") {
        editable.value = false
        editModeActive.value = false

        documentSyncManager.disconnect(1000, reason)
        events.value = ConnectionStatus(connected = false)
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) {
        viewModelScope.launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }
    }

    fun saveEditorState(selection: Int, panX: Float, panY: Float) {
        viewModelScope.launch {
            dataRepository.saveEditorState(
                documentId.value!!,
                currentText.value,
                selection,
                currentZoom.value!!,
                panX,
                panY
            )
        }
    }

    fun onOpenInBrowserClicked(): Boolean {
        val webBaseUri = preferencesHolder.webUriPreference.persistedValue
        if (webBaseUri.isBlank()) {
            return false
        }

        documentEntity.value?.data?.let { document ->
            val pagePath = when (document.url) {
                "index/" -> ""
                else -> document.url
                // this value is already url encoded
            }

            val url = "$webBaseUri/$pagePath"
            events.value = OpenWebView(url)
        }

        return true
    }

    /**
     * Called when the user activates the edit mode
     */
    fun onEditClicked(): Boolean {
        // invert state of edit mode
        editModeActive.value = editModeActive.value != true
        return true
    }

    /**
     * Called when the user wants to connect to the server
     */
    fun onConnectClicked() {
        reconnectToServer()
    }

    /**
     * Called when the user wants to reconnect to the server
     * after a previous connection (attempt) has failed
     */
    fun onRetryClicked() {
        reconnectToServer()
    }

    sealed class CodeEditorEvent {
        data class ConnectionStatus(
            val connected: Boolean,
            val errorCode: Int? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()

        data class InitialText(val text: String) : CodeEditorEvent()

        data class TextChange(
            val newText: String,
            val patches: LinkedList<diff_match_patch.Patch>
        ) : CodeEditorEvent()

        data class OpenWebView(
            val url: String
        ) : CodeEditorEvent()

        data class Error(
            val message: String? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()
    }

}