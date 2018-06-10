package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.arch.lifecycle.LifecycleOwner
import android.content.Context
import android.os.Bundle
import android.support.annotation.CallSuper
import android.view.*
import android.widget.Toast
import androidx.core.view.postDelayed
import androidx.core.widget.toast
import com.jakewharton.rxbinding2.widget.RxTextView
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindToLifecycle
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.prettyPrint
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.view.CodeEditorView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit


/**
 * Server Status fragment
 *
 * Created by Markus on 07.01.2018.
 */
class EditorFragment : DaggerSupportFragmentBase() {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

    private lateinit var codeEditorView: CodeEditorView

    private var currentText: String by savedInstanceState("")

    private var currentXPosition by savedInstanceState(0F)
    private var currentYPosition by savedInstanceState(0F)
    private var currentZoom by savedInstanceState(9F)

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            val refreshIcon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
            menu
                    ?.findItem(R.id.refresh)
                    ?.icon = refreshIcon
        }, onOptionsMenuItemClicked = {
            when {
                it.itemId == R.id.refresh -> {
                    true
                }
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super
                .initComponents(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super
                .onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent
                .onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        return optionsMenuComponent
                .onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent
                .onCreateView(inflater, parent, savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super
                .onViewCreated(view, savedInstanceState)

        currentText = getString(R.string.markdown_demo_text)

        codeEditorView = view
                .findViewById(R.id.codeEditorView)

        RxTextView
                .textChanges(codeEditorView.editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this as LifecycleOwner)
                .subscribeBy(onNext = {
                    currentText = it
                            .toString()
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })

        codeEditorView
                .setText(currentText)

        // zoom in
        codeEditorView
                .postDelayed(500) {
                    codeEditorView
                            .moveTo(currentZoom, currentXPosition, currentYPosition, true)

                    // remember zoom and pan
                    Observable
                            .interval(500, TimeUnit.MILLISECONDS)
                            .bindToLifecycle(codeEditorView)
                            .subscribeBy(onNext = {
                                currentXPosition = codeEditorView
                                        .panX
                                currentYPosition = codeEditorView
                                        .panY
                                currentZoom = codeEditorView
                                        .zoom
                            })
                }

        loadingComponent
                .showContent()
    }

    companion object {

        private const val KEY_ID = "KEY_ID"
        private const val KEY_CONTENT = "KEY_CONTENT"

        fun newInstance(id: String, content: String): EditorFragment {
            val fragment = EditorFragment()
            val bundle = Bundle()
            bundle
                    .putString(KEY_ID, id)
            bundle
                    .putString(KEY_CONTENT, content)

            fragment
                    .arguments = bundle

            return fragment
        }
    }
}