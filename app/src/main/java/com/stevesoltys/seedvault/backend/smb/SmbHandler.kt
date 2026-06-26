/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.backend.smb

import android.content.Context
import app.grapheneos.seedvault.core.backends.Backend
import app.grapheneos.seedvault.core.backends.BackendFactory
import app.grapheneos.seedvault.core.backends.smb.SmbConfig
import app.grapheneos.seedvault.core.backends.smb.SmbProperties
import com.stevesoltys.seedvault.R
import com.stevesoltys.seedvault.backend.BackendManager
import com.stevesoltys.seedvault.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface SmbConfigState {
    object Empty : SmbConfigState
    object Checking : SmbConfigState
    class Success(
        val properties: SmbProperties,
        val backend: Backend,
    ) : SmbConfigState

    class Error(val e: Exception?) : SmbConfigState
}
internal class SmbHandler(
    private val context: Context,
    private val backendFactory: BackendFactory,
    private val settingsManager: SettingsManager,
    private val backendManager: BackendManager,
) {

    companion object {
        fun createSmbProperties(
            context: Context,
            config: SmbConfig,
        ): SmbProperties {
            val host = config.host
            return SmbProperties(
                config = config,
                name = context.getString(R.string.storage_smb_name, host),
            )
        }
    }

    private val mConfigState = MutableStateFlow<SmbConfigState>(SmbConfigState.Empty)
    val configState = mConfigState.asStateFlow()
}
