package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.applyGeneralMenuItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.utils.RecommendationFactory
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.HomepageCarouselAdapter
import org.akanework.gramophone.ui.adapters.RecommendAdapter
import org.akanework.gramophone.ui.components.ItemSnapHelper


class HomepageFragment : BaseFragment(null), Observer<RecommendationFactory.RecommendList> {

    private lateinit var appBarLayout: AppBarLayout
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var recommendTitle: TextView
    private lateinit var recommendRecyclerView: RecyclerView
    private lateinit var recommendAdapter: RecommendAdapter

    @SuppressLint("StringFormatInvalid", "StringFormatMatches")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = layoutInflater.inflate(R.layout.fragment_homepage, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview_top)
        val nestedScrollView = rootView.findViewById<NestedScrollView>(R.id.nested)

        recommendRecyclerView = rootView.findViewById(R.id.rv_r)
        recommendTitle = rootView.findViewById(R.id.recommend)
        recommendAdapter = RecommendAdapter(requireActivity() as MainActivity)

        libraryViewModel.recommendList.observeForever(this)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(
                resources.getColor(R.color.contrast_themeColor, null)
            )
        }

        nestedScrollView.enableEdgeToEdgePaddingListener()

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = HomepageCarouselAdapter(requireContext())

        recommendRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recommendRecyclerView.adapter = recommendAdapter

        ItemSnapHelper().attachToRecyclerView(recyclerView)
        ItemSnapHelper().attachToRecyclerView(recommendRecyclerView)

        ViewCompat.setNestedScrollingEnabled(recyclerView, false)
        ViewCompat.setNestedScrollingEnabled(recommendRecyclerView, false)

        topAppBar.applyGeneralMenuItem(this, libraryViewModel)

        return rootView
    }

    override fun onDestroy() {
        super.onDestroy()
        libraryViewModel.recommendList.removeObserver(this)
    }

    override fun onChanged(value: RecommendationFactory.RecommendList) {
        value.getTitle(libraryViewModel).let {
            recommendTitle.text = it
        }
        recommendAdapter.updateList(value.recommendationList.toMutableList())
    }
}