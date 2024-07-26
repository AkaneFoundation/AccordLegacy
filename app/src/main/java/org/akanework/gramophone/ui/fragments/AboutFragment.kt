package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener

class AboutFragment : BaseElevatedFragment(null) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_about, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        val tagTextView = rootView.findViewById<TextView>(R.id.version_tag)
        val versionTag = BuildConfig.MY_VERSION_NAME + " · " + BuildConfig.RELEASE_TYPE
        val materialToolbar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val nestedScrollView = rootView.findViewById<NestedScrollView>(R.id.nested)
        val contributorCardView = rootView.findViewById<MaterialCardView>(R.id.contributor_frag)

        tagTextView.text = versionTag

        appBarLayout.enableEdgeToEdgePaddingListener()
        nestedScrollView.enableEdgeToEdgePaddingListener()

        materialToolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        contributorCardView.setOnClickListener {
            val supportFragmentManager = requireActivity().supportFragmentManager
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(System.currentTimeMillis().toString())
                .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                .add(R.id.container, AboutContributorFragment())
                .commit()
        }

        return rootView
    }
}