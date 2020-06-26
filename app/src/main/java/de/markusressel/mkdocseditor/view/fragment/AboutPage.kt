/*
 * DataMunch by Markus Ressel
 * Copyright (c) 2018.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mikepenz.aboutlibraries.LibsBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import javax.inject.Inject

/**
 * The page presenting details about this app.
 *
 * The AboutLibraries library has no built in fragment class that can be referenced directly.
 * To work with the android navigation library this is necessary though. Using a
 * TabNavigationFragment as the base class for this page is a (when looking at sourc code)
 * simple workaround for this problem.
 */
@AndroidEntryPoint
class AboutPage : DaggerSupportFragmentBase() {

    override val layoutRes: Int
        get() = R.layout.fragment_about_page

    @Inject
    protected lateinit var preferencesHolder: KutePreferencesHolder

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        val context = context!!

        val themeVal = preferencesHolder
                .themePreference
                .persistedValue

//        val aboutLibTheme: ActivityStyle
//        aboutLibTheme = if (themeVal == context.getString(R.string.theme_light_value)) {
//            Libs.ActivityStyle.LIGHT_DARK_TOOLBAR
//        } else {
//            Libs.ActivityStyle.DARK
//        }

        val fragment = LibsBuilder()
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withAboutAppName(getString(R.string.app_name))
                .withAboutDescription(getString(R.string.app_description))
//                .withActivityStyle(aboutLibTheme)
//                .withActivityColor(
//                        Colors(ContextCompat.getColor(context, R.color.primary),
//                                ContextCompat.getColor(context, R.color.primary_dark)))
                .withActivityTitle(context.getString(R.string.menu_item_about))
                .supportFragment()

        val t = childFragmentManager.beginTransaction()
        t.replace(R.id.contentLayout, fragment)
        t.commit()

        return view
    }

}