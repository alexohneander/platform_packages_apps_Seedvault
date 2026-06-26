/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package app.grapheneos.seedvault.core.backends.smb

import app.grapheneos.seedvault.core.backends.Backend
import app.grapheneos.seedvault.core.backends.BackendId
import app.grapheneos.seedvault.core.backends.BackendSaver
import app.grapheneos.seedvault.core.backends.FileHandle
import app.grapheneos.seedvault.core.backends.FileInfo
import app.grapheneos.seedvault.core.backends.TopLevelFolder
import java.io.InputStream
import kotlin.reflect.KClass

public class SmbBackend(
    public val config: SmbConfig,
) : Backend {
    override val id: BackendId = BackendId.SMB

    override suspend fun test(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getFreeSpace(): Long? {
        TODO("Not yet implemented")
    }

    override suspend fun save(
        handle: FileHandle,
        saver: BackendSaver
    ): Long {
        TODO("Not yet implemented")
    }

    override suspend fun load(handle: FileHandle): InputStream {
        TODO("Not yet implemented")
    }

    override suspend fun list(
        topLevelFolder: TopLevelFolder?,
        vararg fileTypes: KClass<out FileHandle>,
        callback: (FileInfo) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun remove(handle: FileHandle) {
        TODO("Not yet implemented")
    }

    override suspend fun rename(
        from: TopLevelFolder,
        to: TopLevelFolder
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun removeAll() {
        TODO("Not yet implemented")
    }

    override fun isTransientException(e: Exception): Boolean {
        TODO("Not yet implemented")
    }

    override val providerPackageName: String? = null // 100% built-in plugin

}
