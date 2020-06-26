package de.markusressel.mkdocseditor.view.fragment.base

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon

/**
 * Created by Markus on 13.02.2018.
 */
data class FabConfig(val left: MutableList<Fab>, val right: MutableList<Fab>) {

    data class Fab(val id: Int,
                   @StringRes val description: Int,
                   val icon: IIcon,
                   @ColorRes val color: Int? = null,
                   val onClick: (() -> Unit)? = null,
                   val onLongClick: (() -> Boolean)? = null)

}