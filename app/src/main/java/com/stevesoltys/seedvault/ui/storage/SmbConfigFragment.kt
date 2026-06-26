/*
 * SPDX-FileCopyrightText: 2024 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.ui.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager.beginDelayedTransition
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import com.google.android.material.textfield.TextInputEditText
import com.stevesoltys.seedvault.R
import com.stevesoltys.seedvault.backend.smb.SmbConfigState
import com.stevesoltys.seedvault.ui.INTENT_EXTRA_IS_RESTORE
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.getActivityViewModel
import app.grapheneos.seedvault.core.backends.smb.SmbConfig

class SmbConfigFragment : Fragment(), View.OnClickListener {

    companion object {
        fun newInstance(isRestore: Boolean): SmbConfigFragment {
            val f = SmbConfigFragment()
            f.arguments = Bundle().apply {
                putBoolean(INTENT_EXTRA_IS_RESTORE, isRestore)
            }
            return f
        }
    }

    private lateinit var viewModel: StorageViewModel

    private lateinit var hostInput: TextInputEditText
    private lateinit var shareInput: TextInputEditText
    private lateinit var pathInput: TextInputEditText
    private lateinit var userInput: TextInputEditText
    private lateinit var passInput: TextInputEditText
    private lateinit var button: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val v: View = inflater.inflate(R.layout.fragment_smb_config, container, false)
        hostInput = v.requireViewById(R.id.smbHostInput)
        shareInput = v.requireViewById(R.id.smbShareInput)
        pathInput = v.requireViewById(R.id.smbPathInput)
        userInput = v.requireViewById(R.id.smbUserInput)
        passInput = v.requireViewById(R.id.smbPassInput)
        button = v.requireViewById(R.id.webdavButton)
        button.setOnClickListener(this)
        progressBar = v.requireViewById(R.id.progressBar)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = if (requireArguments().getBoolean(INTENT_EXTRA_IS_RESTORE)) {
            getActivityViewModel<RestoreStorageViewModel>()
        } else {
            getActivityViewModel<BackupStorageViewModel>()
        }
        lifecycleScope.launch {
            viewModel.smbConfigState.flowWithLifecycle(lifecycle, STARTED).collect {
                onConfigStateChanged(it)
            }
        }
    }

    override fun onClick(v: View) {
        val host = hostInput.text?.toString().orEmpty().trim()
        if (host.isBlank()) {
            Snackbar.make(
                requireView(),
                R.string.storage_webdav_config_malformed_url,
                LENGTH_LONG
            ).setAnchorView(button).show()
        } else {
            viewModel.onSmbConfigReceived(
                config = SmbConfig(
                    host = host,
                    share = shareInput.text?.toString().orEmpty().trim(),
                    path = pathInput.text?.toString().orEmpty().trim(),
                    username = userInput.text?.toString().orEmpty().trim(),
                    password = passInput.text?.toString().orEmpty().trim(),
                )
            )
        }
    }

    override fun onDestroy() {
        viewModel.resetSmbConfig()
        super.onDestroy()
    }

    private fun onConfigStateChanged(state: SmbConfigState) {
        when (state) {
            SmbConfigState.Empty -> {
            }

            SmbConfigState.Checking -> {
                beginDelayedTransition(requireView() as ViewGroup)
                progressBar.visibility = VISIBLE
                button.visibility = INVISIBLE
            }

            is SmbConfigState.Success -> {
                viewModel.onSmbConfigSuccess(state.properties, state.backend)
            }

            is SmbConfigState.Error -> {
                val s = if (state.e == null) {
                    getString(R.string.storage_check_fragment_backup_error)
                } else {
                    getString(R.string.storage_check_fragment_backup_error) +
                        " ${state.e::class.java.simpleName} ${state.e.message}"
                }
                Snackbar.make(requireView(), s, LENGTH_LONG).setAnchorView(button).show()

                beginDelayedTransition(requireView() as ViewGroup)
                progressBar.visibility = INVISIBLE
                button.visibility = VISIBLE
            }
        }
    }
}

