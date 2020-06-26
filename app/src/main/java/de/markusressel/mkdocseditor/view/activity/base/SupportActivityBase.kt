package de.markusressel.mkdocseditor.view.activity.base

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import androidx.annotation.LayoutRes
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.IconHandler
import de.markusressel.mkdocseditor.view.ThemeHelper
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import kotlinx.android.synthetic.main.view_toolbar.*
import java.util.*
import javax.inject.Inject

/**
 * Created by Markus on 20.12.2017.
 */
abstract class SupportActivityBase : LifecycleActivityBase() {

    @Inject
    lateinit var iconHandler: IconHandler

    @Inject
    lateinit var themeHandler: ThemeHelper

    @Inject
    lateinit var restClient: MkDocsRestClient

    @Inject
    lateinit var preferencesDataProvider: KutePreferenceDataProvider

    @Inject
    lateinit var preferencesHolder: KutePreferencesHolder

    /**
     * @return true if this activity should use a dialog theme instead of a normal activity theme
     */
    @get:Style
    protected abstract val style: Int

    /**
     * The layout resource for this Activity
     */
    @get:LayoutRes
    protected abstract val layoutRes: Int

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        // apply forced locale (if set in developer options)
        //        initLocale()

        // set Theme before anything else in onCreate();
        initTheme()

        if (style == FULLSCREEN) {
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            hideStatusBar()
        } else if (style == DIALOG) {
            // Hide title on dialogs to use view_toolbar instead
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        super.onCreate(savedInstanceState)

        restClient.setHostname(preferencesHolder.restConnectionHostnamePreference.persistedValue)
        restClient.setPort(preferencesHolder.restConnectionPortPreference.persistedValue.toInt())
        restClient.setUseSSL(preferencesHolder.restConnectionSslPreference.persistedValue)
        restClient.setBasicAuthConfig(BasicAuthConfig(
                username = preferencesHolder.basicAuthUserPreference.persistedValue,
                password = preferencesHolder.basicAuthPasswordPreference.persistedValue))

        // inflate view manually so it can be altered in plugins
        val contentView = layoutInflater.inflate(layoutRes, null)
        setContentView(contentView)


        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initTheme() {
        val theme = preferencesDataProvider.getValueUnsafe(R.string.theme_key, getString(R.string.theme_dark_value))

        //        if (style == DIALOG) {
        //            themeHandler.applyDialogTheme(this, theme)
        //        } else {
        themeHandler.applyTheme(this, theme)
        //        }
    }

    /**
     * Show the status bar
     */
    protected fun showStatusBar() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    /**
     * Hide the status bar
     */
    protected fun hideStatusBar() {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun setLocale(locale: Locale) {
        val res = resources
        // Change locale settings in the app.
        val dm = res.displayMetrics
        val conf = res.configuration

        conf.locale = locale
        conf.setLocale(locale)
        res.updateConfiguration(conf, dm)

        onConfigurationChanged(conf)
    }

    @IntDef(DEFAULT, DIALOG)
    @kotlin.annotation.Retention
    annotation class Style

    companion object {

        /**
         * Normal activity style
         */
        const val DEFAULT = 0

        /**
         * Dialog style
         */
        const val DIALOG = 1

        /**
         * Fullscreen activity style
         */
        const val FULLSCREEN = 2
    }

}
