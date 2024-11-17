/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.applyGeneralMenuItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter

/**
 * ViewPagerFragment:
 *   A fragment that's in charge of displaying tabs
 * and is connected to the drawer.
 *
 * @author AkaneTan
 */
@androidx.annotation.OptIn(UnstableApi::class)
class BrowseFragment : BaseFragment(null) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    lateinit var appBarLayout: AppBarLayout
        private set

    @SuppressLint("StringFormatInvalid", "StringFormatMatches")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_browse, container, false)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val viewPager2 = rootView.findViewById<ViewPager2>(R.id.fragment_viewpager)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(
                resources.getColor(R.color.contrast_themeColor, null)
            )
        }

        topAppBar.applyGeneralMenuItem(this, libraryViewModel)

        // Connect ViewPager2.

        // Set this to 9999 so it won't lag anymore.
        viewPager2.offscreenPageLimit = 9999
        val adapter = ViewPager2Adapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        viewPager2.adapter = adapter
        TabLayoutMediator(
            tabLayout,
            viewPager2
        ) { tab, position ->
            tab.text = getString(adapter.getLabelResId(position))
        }.attach()

        /*
         * Add margin to last and first tab.
         * There's no attribute to let you set margin
         * to the last tab.
         */
        val lastTab = tabLayout.getTabAt(tabLayout.tabCount - 1)!!.view
        val firstTab = tabLayout.getTabAt(0)!!.view
        val lastParam = lastTab.layoutParams as ViewGroup.MarginLayoutParams
        val firstParam = firstTab.layoutParams as ViewGroup.MarginLayoutParams
        lastParam.marginEnd = resources.getDimension(R.dimen.tab_layout_content_padding).toInt()
        firstParam.marginStart = resources.getDimension(R.dimen.tab_layout_content_padding).toInt()
        lastTab.layoutParams = lastParam
        firstTab.layoutParams = firstParam

        return rootView
    }
}
