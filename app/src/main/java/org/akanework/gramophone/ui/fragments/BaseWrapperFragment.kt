package org.akanework.gramophone.ui.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import org.akanework.gramophone.R

class BaseWrapperFragment : BaseFragment {

    private var fragmentType: Int = 0

    constructor() : super()

    constructor(fragmentType: Int) : super() {
        this.fragmentType = fragmentType
    }

    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_wrapper, container, false)
        if (childFragmentManager.fragments.size == 0) {
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.wrapper_container,
                    when (fragmentType) {
                        0 -> HomepageFragment()
                        1 -> BrowseFragment()
                        2 -> LibraryFragment()
                        3 -> SearchFragment()
                        else -> throw IllegalArgumentException()
                    }
                )
                .commit()
        }
        return rootView
    }

    fun replaceFragment(frag: BaseFragment, args: (Bundle.() -> Unit)? = null) {
        Log.d("TAG", "B4ADD, ${childFragmentManager.fragments.size}")
        childFragmentManager.beginTransaction()
            .addToBackStack(System.currentTimeMillis().toString())
            .hide(childFragmentManager.fragments.let { it[it.size - 1] })
            .add(
                R.id.wrapper_container,
                frag.apply { args?.let { arguments = Bundle().apply(it) } })
            .commit()
        backCallback!!.isEnabled = true
        Log.d("TAG", "ADD, ${childFragmentManager.fragments.size}")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Remove all fragments from the childFragmentManager,
                // but exclude the first added child fragment.
                // This child fragment will be deleted with its parent.
                Log.d("TAG", "BASEWRAPPED!")
                childFragmentManager.popBackStack()
                if (childFragmentManager.backStackEntryCount == 1) {
                    backCallback!!.isEnabled = false
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    override fun onDetach() {
        super.onDetach()
        backCallback!!.remove()
    }
}