package de.markusressel.mkdocsrestclient.sync

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonParseException
import de.markusressel.commons.android.core.doAsync
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.websocket.SocketEntityBase
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import timber.log.Timber
import java.util.*

/**
 * Class used to manage document changes from client- and server.
 */
class DocumentSyncManager(
        url: String,
        basicAuthConfig: BasicAuthConfig,
        private val documentId: String,
        private val onConnectionStatusChanged: ((connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit),
        private val onInitialText: ((initialText: String) -> Unit),
        private val onTextChanged: ((newText: String, patches: LinkedList<diff_match_patch.Patch>) -> Unit)) : WebsocketConnectionListener {

    private val websocketConnectionHandler = WebsocketConnectionHandler(url, basicAuthConfig)

    /**
     * The URL used for the websocket connection
     */
    val url: String
        get() = websocketConnectionHandler.url

    /**
     * @return true if a there is an open connection to the server, false otherwise
     */
    val isConnected: Boolean
        get() = websocketConnectionHandler.isConnected

    private var previouslySentPatches: MutableMap<String, String> = mutableMapOf()

    private var clientShadow: String = ""
    private var currentText: String = ""

    /**
     * Connect to the given URL
     */
    fun connect() {
        websocketConnectionHandler.setListener(this)
        websocketConnectionHandler.connect()
    }

    /**
     * Notify this manager that the text of the document has changed on this client
     * and changes need to be synced with the server.
     */
    fun notifyTextChanged(newText: String) {
        currentText = newText
        sendPatch(newText = currentText)
    }

    /**
     * Send a patch to the server
     *
     * @param previousText the text to use as the previous version
     * @param newText the new and (presumably) changed text
     * @return id of the EditRequest sent to the server
     */
    private fun sendPatch(previousText: String = clientShadow, newText: String): String {
        // compute diff to current shadow
        val diffs = DIFF_MATCH_PATCH.diff_main(previousText, newText)
        // take a checksum of the client shadow before the diff has been applied
        val clientShadowChecksumBeforePatch = clientShadow.hashCode()
        // update client shadow with the new text
        clientShadow = newText

        // create patch from diffs
        val patches = DIFF_MATCH_PATCH.patch_make(diffs)

        // parse to json
        val requestId = UUID.randomUUID().toString()
        val editRequestModel = EditRequestEntity(
                requestId = requestId,
                documentId = documentId,
                patches = DIFF_MATCH_PATCH.patch_toText(patches),
                shadowChecksum = clientShadowChecksumBeforePatch)

        // send to server
        websocketConnectionHandler.send(GSON.toJson(editRequestModel))

        // remember that this request has been sent
        previouslySentPatches[requestId] = "sent"

        return requestId
    }

    /**
     * Disconnect a from the server
     */
    fun disconnect(code: Int, reason: String) = websocketConnectionHandler.disconnect(code, reason)

    override fun onConnectionChanged(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        onConnectionStatusChanged.invoke(connected, errorCode, throwable)
    }

    override fun onMessage(text: String) {
        doAsync {
            try {
                processIncomingMessage(text)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    @WorkerThread
    private fun processIncomingMessage(text: String) {
        val entity = GSON.fromJson(text, SocketEntityBase::class.java)
        if (entity.documentId != documentId) {
            // ignore requests for other documents
            return
        }

        when (entity.type) {
            "initial-content" -> {
                val initialContentEntity = GSON.fromJson(text, InitialContentRequestEntity::class.java)

                currentText = initialContentEntity.content
                clientShadow = initialContentEntity.content

                onInitialText(initialContentEntity.content)
            }
            "edit-request" -> {
                val editRequest = GSON.fromJson(text, EditRequestEntity::class.java)
                        ?: throw JsonParseException("result was null!")

                if (previouslySentPatches.containsKey(editRequest.requestId)) {
                    // remember if this edit request is the answer to a previously sent patch from us
                    previouslySentPatches.remove(editRequest.requestId)
                    return
                }

                // parse and apply patches
                val patches: LinkedList<diff_match_patch.Patch> = DIFF_MATCH_PATCH.patch_fromText(editRequest.patches) as LinkedList<diff_match_patch.Patch>
                if (fragilePatchShadow(editRequest, patches)) {
                    // if shadow patch was successful patch the actual text
                    val patchResult = DIFF_MATCH_PATCH.patch_apply(patches, clientShadow)
                    val patchedText = patchResult[0] as String

                    currentText = patchedText
                }

                onTextChanged(currentText, patches)
            }
        }
    }

    /**
     * Fragile patch the current shadow.
     *
     * @return true if the patch was successful, false otherwise
     */
    private fun fragilePatchShadow(editRequest: EditRequestEntity, patches: LinkedList<diff_match_patch.Patch>): Boolean {
        // fragile patch shadow
        val patchResult = DIFF_MATCH_PATCH.patch_apply(patches, clientShadow)
        val patchedText = patchResult[0] as String
        val patchesApplied = patchResult[1] as BooleanArray

        // make sure current shadow matches the server shadow before the patch
        return when {
            patchedText.hashCode() != editRequest.shadowChecksum -> {
                resyncWithServer()
                false
            }
            else -> true
        }
    }

    private fun resyncWithServer() {
        disconnect(1000, "Sync was broken, need to restart.")
        connect()
    }

    /**
     * Shutdown the websocket client (and all websockets)
     */
    fun shutdown() {
        websocketConnectionHandler.shutdown()
    }

    companion object {
        private val DIFF_MATCH_PATCH = diff_match_patch()
        private var GSON = Gson()
    }

}