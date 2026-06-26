/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package app.grapheneos.seedvault.core.backends.smb

public data class SmbConfig(
    val host: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String,
    val domain: String? = null
)

