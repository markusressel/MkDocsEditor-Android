package de.markusressel.mkdocseditor.view.activity.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.markusressel.mkdocseditor.view.InstanceStateProvider

/**
 * Created by Markus on 21.02.2018.
 */
abstract class StateActivityBase : AppCompatActivity() {

    private val stateBundle = Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            stateBundle.putAll(savedInstanceState.getBundle(KEY_BUNDLE))
        }

        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(KEY_BUNDLE, stateBundle)

        super.onSaveInstanceState(outState)
    }

    /**
     * Bind a nullable property
     */
    protected fun <T> savedInstanceState() = InstanceStateProvider.Nullable<T>(stateBundle)

    /**
     * Bind a non-null property
     */
    protected fun <T> savedInstanceState(defaultValue: T) = InstanceStateProvider.NotNull(stateBundle, defaultValue)

    companion object {
        const val KEY_BUNDLE = "saved_state"
    }

}