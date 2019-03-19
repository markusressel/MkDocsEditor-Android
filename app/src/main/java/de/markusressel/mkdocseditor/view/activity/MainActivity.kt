package de.markusressel.mkdocseditor.view.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.view.activity.base.NavigationDrawerActivity
import de.markusressel.mkdocseditor.view.fragment.FileBrowserFragmentDirections

class MainActivity : NavigationDrawerActivity() {

    override val style: Int
        get() = DEFAULT

    override fun onStart() {
        super.onStart()
        if (BuildConfig.DEBUG) {
            Dexter.withActivity(this)
                    .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(object : PermissionListener {
                        override fun onPermissionGranted(response: PermissionGrantedResponse?) {}
                        override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {}
                        override fun onPermissionDenied(response: PermissionDeniedResponse?) {}
                    }).check()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val appLinkIntent = intent
        val appLinkAction = appLinkIntent.action
        val appLinkData = appLinkIntent.data

        if (Intent.ACTION_VIEW == appLinkAction && appLinkData != null) {

//            val givenHost = (appLinkData.host ?: "")
//            val configuredHost = preferencesHolder.restConnectionHostnamePreference.persistedValue
//            if (!givenHost.startsWith(configuredHost)) {
//                Timber.d { "Ignoring deep link from other host: $givenHost" }
//                return
//            }

            appLinkData.lastPathSegment?.let { documentId ->
                val direction = FileBrowserFragmentDirections.actionFileBrowserPageToCodeEditorPage(documentId)
                navController.navigate(direction)
            }
        }
    }

}
