/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package app.grapheneos.seedvault.core.backends.smb

import android.content.Context
import app.grapheneos.seedvault.core.backends.BackendProperties

public data class SmbProperties(
    override val config: SmbConfig,
    override val name: String,
) : BackendProperties<SmbConfig>() {
    override val isUsb: Boolean = false
    override val requiresNetwork: Boolean = true
    override fun isUnavailableUsb(context: Context): Boolean = false
}
