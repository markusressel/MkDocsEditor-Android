package de.markusressel.mkdocseditor.view.activity.base

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.get
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.mikepenz.materialdrawer.R.string.material_drawer_close
import com.mikepenz.materialdrawer.R.string.material_drawer_open
import com.mikepenz.materialdrawer.interfaces.OnCheckedChangeListener
import com.mikepenz.materialdrawer.model.*
import com.mikepenz.materialdrawer.model.interfaces.*
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.event.ThemeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.isTablet
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.About
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.FileBrowser
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.OfflineMode
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.Settings
import de.markusressel.mkdocseditor.navigation.DrawerMenuItem
import de.markusressel.mkdocseditor.view.fragment.FileBrowserFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_toolbar.*
import java.util.*
import javax.inject.Inject

/**
 * A base activity that provides the full navigation navigationDrawer implementation
 *
 * Created by Markus on 07.01.2018.
 */
abstract class NavigationDrawerActivity : SupportActivityBase() {

    override val layoutRes: Int
        get() = R.layout.activity_main

    protected val navController by lazy { Navigation.findNavController(this, R.id.navHostFragment) }

    @Inject
    protected lateinit var offlineModeManager: OfflineModeManager

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val menuItemList = initDrawerMenuItems()
        slider.itemAdapter.add(menuItemList)
        initAccountHeader(slider)

        actionBarDrawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, material_drawer_open, material_drawer_close)
        val appBarConfiguration = AppBarConfiguration(
                navGraph = navController.graph,
                drawerLayout = drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            // update selected drawer item accordingly
            DrawerItemHolder.fromId(destination.id)?.let {
                slider.setSelection(it.id.toLong(), false)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        Bus.observe<ThemeChangedEvent>()
                .subscribe {
                    recreate()
                }
                .registerInBus(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        actionBarDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        actionBarDrawerToggle.syncState()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return actionBarDrawerToggle.onOptionsItemSelected(item)
    }

    private fun initAccountHeader(slider: MaterialDrawerSliderView): AccountHeaderView {
        val profiles: MutableList<IProfile> = getProfiles()

        AccountHeaderView(this).apply {
            attachToSliderView(slider) // attach to the slider
            addProfiles(*profiles.toTypedArray())
            onAccountHeaderListener = { view, profile, current ->
                // react to profile changes
                false
            }
//            withSavedInstance(savedInstanceState)
        }


        return AccountHeaderView(this).apply {
            this.profiles = profiles
            dividerBelowHeader = true
            onAccountHeaderListener = { view: View?, profile: IProfile, current: Boolean ->
                Timber.d { "Pressed profile: '$profile' with current: '$current'" }
                false
            }
        }
    }

    private fun getProfiles(): MutableList<IProfile> {
        val profiles: MutableList<IProfile> = LinkedList()

        profiles.add(ProfileDrawerItem().apply {
            nameText = "Markus Ressel"
            descriptionText = "mail@markusressel.de"
            iconRes = R.mipmap.ic_launcher
        })

        profiles.add(ProfileDrawerItem().apply {
            nameText = "Max Mustermann"
            descriptionText = ""
            iconRes = R.mipmap.ic_launcher
        })

        return profiles
    }

    private fun initDrawerMenuItems(): MutableList<IDrawerItem<*>> {
        val menuItemList: MutableList<IDrawerItem<*>> = LinkedList()

        val clickListener = { view: View?, drawerItem: IDrawerItem<*>, i: Int ->
            var consume = false
            val drawerMenuItem = DrawerItemHolder.fromId(
                    drawerItem.identifier.toInt())

            drawerMenuItem?.let {
                if (it.id == R.id.none) {
                    return@let
                }

                navController.navigate(it.id)

                if (drawerItem.isSelectable) {
                    // set new title
                    setTitle(drawerMenuItem.title)
                }

                if (!isTablet()) {
                    drawerLayout.closeDrawer(slider)
                }
                consume = true
            }

            consume
        }

        menuItemList.addAll(
                arrayOf(
                        createPrimaryMenuItem(FileBrowser, clickListener),
                        DividerDrawerItem(),
                        createPrimaryMenuItem(Settings, clickListener),
                        createSecondaryMenuItem(About, clickListener),
                        DividerDrawerItem(),
                        createOfflineModeMenuItem(OfflineMode, clickListener,
                                defaultValue = offlineModeManager.isEnabled())))

        return menuItemList
    }

    private fun createPrimaryMenuItem(menuItem: DrawerMenuItem, clickListener: ((v: View?, item: IDrawerItem<*>, position: Int) -> Boolean)?): PrimaryDrawerItem {
        return PrimaryDrawerItem().apply {
            nameRes = menuItem.title
            identifier = menuItem.id.toLong()
            iconDrawable = menuItem.getIcon(iconHandler)
            isSelectable = menuItem.selectable
            onDrawerItemClickListener = clickListener
        }
    }

    private fun createSecondaryMenuItem(menuItem: DrawerMenuItem, clickListener: ((v: View?, item: IDrawerItem<*>, position: Int) -> Boolean)?): SecondaryDrawerItem {
        return SecondaryDrawerItem().apply {
            nameRes = menuItem.title
            identifier = menuItem.id.toLong()
            iconDrawable = menuItem.getIcon(iconHandler)
            isSelectable = menuItem.selectable
            onDrawerItemClickListener = clickListener
        }
    }

    private fun createOfflineModeMenuItem(menuItem: DrawerMenuItem, clickListener: ((v: View?, item: IDrawerItem<*>, position: Int) -> Boolean)?, defaultValue: Boolean = false): IDrawerItem<*> {

        val onCheckedChangeListener = object : OnCheckedChangeListener {
            override fun onCheckedChanged(drawerItem: IDrawerItem<*>, buttonView: CompoundButton, isChecked: Boolean) {
                offlineModeManager.setEnabled(isChecked)
                val parentView = buttonView.parent as ViewGroup
                val iconView = parentView[0] as AppCompatImageView
                iconView.setColorFilter(offlineModeManager.getColor())
            }
        }

        return SwitchDrawerItem().apply {
            nameRes = menuItem.title
            identifier = menuItem.id.toLong()
            iconDrawable = menuItem.getIcon(iconHandler)
            isSelectable = menuItem.selectable
            onDrawerItemClickListener = clickListener
            this.onCheckedChangeListener = onCheckedChangeListener
            isChecked = defaultValue
            iconColor = ColorStateList.valueOf(offlineModeManager.getColor())
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(slider)) {
            drawerLayout.closeDrawer(slider)
            return
        }

        // pass onBack event to fragments
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        val currentlyVisibleFragment = navHost?.childFragmentManager?.primaryNavigationFragment
        when (currentlyVisibleFragment) {
            is PreferencesFragment -> if (currentlyVisibleFragment.onBackPressed()) {
                return
            }
            is FileBrowserFragment -> if (currentlyVisibleFragment.onBackPressed()) {
                return
            }
        }

        if (navController.navigateUp()) {
            return
        }

        super.onBackPressed()
    }
}