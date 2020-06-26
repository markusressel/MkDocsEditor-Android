package de.markusressel.mkdocseditor.view.fragment.preferences

import android.content.Context
import com.eightbitlab.rxbus.Bus
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.commons.android.material.toast
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.kutepreferences.core.preference.action.KuteAction
import de.markusressel.kutepreferences.core.preference.category.KuteCategory
import de.markusressel.kutepreferences.core.preference.section.KuteSection
import de.markusressel.kutepreferences.preference.bool.KuteBooleanPreference
import de.markusressel.kutepreferences.preference.number.KuteNumberPreference
import de.markusressel.kutepreferences.preference.selection.single.KuteSingleSelectStringPreference
import de.markusressel.kutepreferences.preference.text.KuteTextPreference
import de.markusressel.kutepreferences.preference.text.password.KutePasswordPreference
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.event.*
import de.markusressel.mkdocseditor.view.IconHandler
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocseditor.view.preferences.LastOfflineCacheUpdatePreferenceItem
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Holder for KutePreference items for easy access to preference values across the application
 */
@Singleton
class KutePreferencesHolder @Inject constructor(
        private val context: Context,
        private val iconHelper: IconHandler,
        private val dataProvider: KutePreferenceDataProvider,
        private val sectionPersistenceManager: SectionPersistenceManager,
        private val documentPersistenceManager: DocumentPersistenceManager,
        private val documentContentPersistenceManager: DocumentContentPersistenceManager,
        private val resourcePersistenceManager: ResourcePersistenceManager,
        private val offlineModeManager: OfflineModeManager) {

    val connectionCategory by lazy {
        KuteCategory(
                key = R.string.category_connection_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_wifi),
                title = context.getString(R.string.category_connection_title),
                description = context.getString(R.string.category_connection_description),
                children = listOf(KuteSection(
                        key = R.string.section_rest_server_key,
                        title = context.getString(R.string.section_rest_server_title),
                        children = listOf(
                                restConnectionHostnamePreference,
                                restConnectionPortPreference,
                                restConnectionSslPreference
                        )
                ), KuteSection(
                        key = R.string.divider_basic_auth_key,
                        title = context.getString(R.string.divider_basic_auth_title),
                        children = listOf(
                                basicAuthUserPreference,
                                basicAuthPasswordPreference
                        )
                ), KuteSection(
                        key = R.string.section_web_key,
                        title = context.getString(R.string.section_web_title),
                        children = listOf(
                                webUriPreference
                        )
                ))
        )
    }

    val offlineCacheCategory by lazy {
        KuteCategory(
                key = R.string.category_offline_cache_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_airplane),
                title = context.getString(R.string.category_offline_cache_title),
                description = context.getString(R.string.category_offline_cache_description),
                children = listOf(KuteSection(
                        key = R.string.section_background_sync_key,
                        title = context.getString(R.string.section_background_sync_title),
                        children = listOf(
                                lastOfflineCacheUpdate,
                                forceOfflineCacheUpdatePreference,
                                clearOfflineCache
                        )
                )))
    }

    val forceOfflineCacheUpdatePreference = KuteAction(
            key = R.string.action_force_offline_cache_update_key,
            title = context.getString(R.string.action_force_offline_cache_update_title),
            description = "",
            onClickAction = { context, action ->
                offlineModeManager.scheduleOfflineCacheUpdate(evenInOfflineMode = true)
            })

    val clearOfflineCache by lazy {
        KuteAction(key = R.string.clear_offline_cache_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_delete),
                title = context.getString(R.string.clear_offline_cache_title),
                description = "",
                onClickAction = { context, _ ->
                    documentContentPersistenceManager.standardOperation().removeAll()
                    resourcePersistenceManager.standardOperation().removeAll()
                    documentPersistenceManager.standardOperation().removeAll()
                    sectionPersistenceManager.standardOperation().removeAll()
                    context.toast("DB cleared")
                })
    }

    val lastOfflineCacheUpdate by lazy {
        LastOfflineCacheUpdatePreferenceItem()
    }

    val restConnectionHostnamePreference by lazy {
        KuteTextPreference(key = R.string.connection_host_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_battery),
                title = context.getString(R.string.connection_host_title),
                defaultValue = "",
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(HostChangedEvent(new))
                })
    }

    val restConnectionPortPreference by lazy {
        KuteNumberPreference(key = R.string.connection_port_key,
                title = context.getString(R.string.connection_port_title),
                defaultValue = 8080,
                minimum = 0,
                maximum = 65535,
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(PortChangedEvent(new.toInt()))
                })
    }

    val restConnectionSslPreference by lazy {
        KuteBooleanPreference(key = R.string.connection_ssl_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_lock),
                title = context.getString(R.string.connection_ssl_title),
                defaultValue = true,
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                })
    }

    val basicAuthUserPreference by lazy {
        KuteTextPreference(key = R.string.connection_basic_auth_user_key,
                title = context.getString(R.string.connection_basic_auth_user_title),
                defaultValue = "",
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(BasicAuthUserChangedEvent(new))
                })
    }

    val basicAuthPasswordPreference by lazy {
        KutePasswordPreference(key = R.string.connection_basic_auth_password_key,
                title = context.getString(R.string.connection_basic_auth_password_title),
                defaultValue = "",
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(BasicAuthPasswordChangedEvent(new))
                })
    }

    val webUriPreference by lazy {
        KuteTextPreference(key = R.string.connection_web_url_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_battery),
                title = context.getString(R.string.connection_web_url_title),
                defaultValue = "",
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(HostChangedEvent(new))
                })
    }

    val themePreference by lazy {
        KuteSingleSelectStringPreference(context = context,
                key = R.string.theme_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
                title = context.getString(R.string.theme_title),
                possibleValues = mapOf(R.string.theme_dark_value to R.string.theme_dark_value_name, R.string.theme_light_value to R.string.theme_light_value_name),
                defaultValue = R.string.theme_dark_value,
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(ThemeChangedEvent(new))
                })
    }

    val offlineModePreference by lazy {
        KuteBooleanPreference(key = R.string.offline_mode_key,
                icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
                title = context.getString(R.string.theme_title),
                defaultValue = false,
                dataProvider = dataProvider,
                onPreferenceChangedListener = { old, new ->
                    Bus.send(OfflineModeChangedEvent(new))
                })
    }

}

