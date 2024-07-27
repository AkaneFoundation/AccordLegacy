package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fluidviewpager2.widget.ViewPager2
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.MainPageAdapter

class ViewPagerFragment : BaseFragment(true) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = layoutInflater.inflate(R.layout.fragment_viewpager, container, false)
        val mViewPager2: ViewPager2 = rootView.findViewById(R.id.viewpager2)
        val adapter = MainPageAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)

        mViewPager2.adapter = adapter
        mViewPager2.isUserInputEnabled = false
        mViewPager2.offscreenPageLimit = 9999

        val bottomNavigationView = (requireActivity() as MainActivity).bottomNavigationView

        // Set up bottomNavigationView
        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> mViewPager2.setCurrentItem(0, true)
                R.id.browse -> mViewPager2.setCurrentItem(1, true)
                R.id.library -> mViewPager2.setCurrentItem(2, true)
                R.id.search -> mViewPager2.setCurrentItem(3, true)
                else -> throw IllegalArgumentException("Illegal itemId: ${it.itemId}")
            }
            true
        }

        // Set up viewPager2
        mViewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> bottomNavigationView.selectedItemId = R.id.home
                    1 -> bottomNavigationView.selectedItemId = R.id.browse
                    2 -> bottomNavigationView.selectedItemId = R.id.library
                    3 -> bottomNavigationView.selectedItemId = R.id.search
                }
            }
        })

        return rootView
    }
}