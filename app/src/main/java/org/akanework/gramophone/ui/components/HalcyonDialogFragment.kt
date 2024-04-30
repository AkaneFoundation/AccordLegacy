package org.akanework.gramophone.ui.components

import android.graphics.BlendMode
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.ui.components.blurview.BlurView
import org.akanework.gramophone.ui.components.blurview.RenderEffectBlur
import org.akanework.gramophone.ui.components.blurview.RenderScriptBlur

class HalcyonDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.MyDialog)
    }

    // The system calls this to get the DialogFragment's layout, regardless of
    // whether it's being displayed as a dialog or an embedded fragment.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout to use as a dialog or embedded fragment.
        val rootView = inflater.inflate(R.layout.halcyon_dialog_item, container, false)
        val blurView: BlurView = rootView.findViewById(R.id.blur_view)
        setUpBlurView(blurView, requireActivity().findViewById(R.id.container), 32f)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val params: ViewGroup.LayoutParams = dialog!!.window!!.attributes
        params.width = 240.dpToPx(requireContext())
        params.height = FrameLayout.LayoutParams.WRAP_CONTENT
        dialog!!.window!!.setAttributes(params as WindowManager.LayoutParams)
        dialog!!.window!!.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    fun setUpBlurView(
        blurView: BlurView,
        rootView: ViewGroup,
        blurRadius: Float) {
        blurView.setupWith(
            rootView,
            if (Build.VERSION.SDK_INT >= 31)
                RenderEffectBlur()
            else
                RenderScriptBlur(requireContext()),
            BlendMode.OVERLAY
        )
            .setBlurRadius(blurRadius)
    }

}