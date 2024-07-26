package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener

class AboutContributorFragment : BaseElevatedFragment(null) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_about_inner, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        val materialToolbar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val nestedScrollView = rootView.findViewById<NestedScrollView>(R.id.nested)

        appBarLayout.enableEdgeToEdgePaddingListener()
        nestedScrollView.enableEdgeToEdgePaddingListener()

        materialToolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }
}