package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.NestedScrollView
import androidx.fluidrecyclerview.widget.LinearLayoutManager
import androidx.fluidrecyclerview.widget.LinearSnapHelper
import androidx.fluidrecyclerview.widget.RecyclerView
import androidx.fluidrecyclerview.widget.SnapHelper
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.HomepageCarouselAdapter
import org.akanework.gramophone.ui.components.ItemSnapHelper
import org.akanework.gramophone.ui.fragments.settings.MainSettingsFragment


class HomepageFragment : BaseFragment(null) {

    private lateinit var appBarLayout: AppBarLayout
    private val libraryViewModel: LibraryViewModel by activityViewModels()

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

        ItemSnapHelper().attachToRecyclerView(recyclerView)

        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.equalizer -> {
                    val intent = Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
                        .addCategory("android.intent.category.CATEGORY_CONTENT_MUSIC")
                    try {
                        (requireActivity() as MainActivity).startingActivity.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        // Let's show a toast here if no system inbuilt EQ was found.
                        Toast.makeText(
                            requireContext(),
                            R.string.equalizer_not_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                R.id.refresh -> {
                    val activity = requireActivity() as MainActivity
                    val playerLayout = activity.playerBottomSheet
                    activity.updateLibrary {
                        val snackBar =
                            Snackbar.make(
                                requireView(),
                                getString(
                                    R.string.refreshed_songs,
                                    libraryViewModel.mediaItemList.value!!.size,
                                ),
                                Snackbar.LENGTH_LONG,
                            )
                        snackBar.setAction(R.string.dismiss) {
                            snackBar.dismiss()
                        }

                        /*
                         * Let's override snack bar's color here so it would
                         * adapt dark mode.
                         */
                        snackBar.setBackgroundTint(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorSurface,
                            ),
                        )
                        snackBar.setActionTextColor(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorPrimary,
                            ),
                        )
                        snackBar.setTextColor(
                            MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorOnSurface,
                            ),
                        )

                        // Set an anchor for snack bar.
                        if (playerLayout.visible && playerLayout.actuallyVisible)
                            snackBar.anchorView = playerLayout
                        snackBar.show()
                    }
                }

                R.id.settings -> {
                    (activity as MainActivity).playerBottomSheet.shouldRetractBottomNavigation(true)
                    (requireActivity() as MainActivity).startFragment(MainSettingsFragment())
                }

                else -> throw IllegalStateException()
            }
            true
        }

        return rootView
    }
}