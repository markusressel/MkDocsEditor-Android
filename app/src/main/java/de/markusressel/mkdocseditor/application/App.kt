package de.markusressel.mkdocseditor.application

import android.app.Application
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import dagger.hilt.android.HiltAndroidApp
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.application.log.FileTree
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.event.BasicAuthPasswordChangedEvent
import de.markusressel.mkdocseditor.event.BasicAuthUserChangedEvent
import de.markusressel.mkdocseditor.event.HostChangedEvent
import de.markusressel.mkdocseditor.event.PortChangedEvent
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.reactivex.plugins.RxJavaPlugins
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by Markus on 20.12.2017.
 */
@HiltAndroidApp
class App : Application() {

    @Inject
    internal lateinit var restClient: MkDocsRestClient

    @Inject
    internal lateinit var offlineModeManager: OfflineModeManager

    @Inject
    internal lateinit var documentPersistenceManager: DocumentPersistenceManager

    override fun onCreate() {
        super.onCreate()
        // register app lifecycle
        registerActivityLifecycleCallbacks(AppLifecycleTracker())

        // Clear DB entirely
//        BoxStore.deleteAllFiles(applicationContext, null)

        plantTimberTrees()

        setupErrorHandlers()

        createListeners()

        initializeEmojiCompat()

        initOfflineMode()
    }

    private fun setupErrorHandlers() {
        RxJavaPlugins.setErrorHandler {
            Timber.e(it)
        }
    }

    private fun initOfflineMode() {
        offlineModeManager.scheduleOfflineCacheUpdate()
    }

    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileTree(this))
        }
    }

    private fun createListeners() {
        Bus.observe<HostChangedEvent>().subscribe {
            restClient.setHostname(it.host)
        }.registerInBus(this)

        Bus.observe<PortChangedEvent>().subscribe {
            restClient.setPort(it.port)
        }.registerInBus(this)

        Bus.observe<BasicAuthUserChangedEvent>().subscribe {
            val oldConfig = restClient.getBasicAuthConfig()
            restClient.setBasicAuthConfig(BasicAuthConfig(it.user, oldConfig?.password ?: ""))
        }.registerInBus(this)

        Bus.observe<BasicAuthPasswordChangedEvent>().subscribe {
            val oldConfig = restClient.getBasicAuthConfig()
            restClient.setBasicAuthConfig(
                    BasicAuthConfig(oldConfig?.username ?: "", it.password))
        }.registerInBus(this)
    }

    private fun initializeEmojiCompat() {
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }

}
