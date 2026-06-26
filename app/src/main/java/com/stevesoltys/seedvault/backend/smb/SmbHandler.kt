/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.backend.smb

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.grapheneos.seedvault.core.backends.Backend
import app.grapheneos.seedvault.core.backends.BackendFactory
import app.grapheneos.seedvault.core.backends.smb.SmbConfig
import app.grapheneos.seedvault.core.backends.smb.SmbProperties
import com.stevesoltys.seedvault.R
import com.stevesoltys.seedvault.backend.BackendManager
import com.stevesoltys.seedvault.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

internal sealed interface SmbConfigState {
    object Empty : SmbConfigState
    object Checking : SmbConfigState
    class Success(
        val properties: SmbProperties,
        val backend: Backend,
    ) : SmbConfigState

    class Error(val e: Exception?) : SmbConfigState
}
private val TAG = SmbHandler::class.java.simpleName

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

    suspend fun onConfigReceived(config: SmbConfig) {
        mConfigState.value = SmbConfigState.Checking
        val backend = backendFactory.createSmbBackend(config)
        try {
            if (backend.test()) {
                val properties = createSmbProperties(context, config)
                mConfigState.value = SmbConfigState.Success(properties, backend)
            } else {
                mConfigState.value = SmbConfigState.Error(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing SMB config at ${config.host}", e)
            mConfigState.value = SmbConfigState.Error(e)
        }
    }

    fun resetConfigState() {
        mConfigState.value = SmbConfigState.Empty
    }

    @WorkerThread
    @Throws(IOException::class)
    suspend fun hasBackup(backend: Backend): Boolean {
        return backend.getAvailableBackupFileHandles().isNotEmpty()
    }

    fun save(properties: SmbProperties) {
        settingsManager.saveSmbConfig(properties.config)
    }

    @WorkerThread
    fun setPlugin(properties: SmbProperties, backend: Backend) {
        backendManager.changePlugins(
            backend = backend,
            storageProperties = properties,
        )
    }
}
