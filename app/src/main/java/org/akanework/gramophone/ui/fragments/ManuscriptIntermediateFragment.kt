package org.akanework.gramophone.ui.fragments

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R

class ManuscriptIntermediateFragment : BaseFragment(false) {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.manuscript_dialog, container, false)
        val continueButton: MaterialButton = rootView.findViewById(R.id.continue_btn)
        continueButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            withContext(Dispatchers.Main) {
                val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
                valueAnimator.duration = 300
                valueAnimator.addUpdateListener {
                    val alpha = it.animatedValue as Float
                    continueButton.alpha = alpha
                }
                valueAnimator.start()
            }
        }
        return rootView
    }
}