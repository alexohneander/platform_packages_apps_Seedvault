/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package app.grapheneos.seedvault.core.backends.smb

import app.grapheneos.seedvault.core.backends.AppBackupFileType
import app.grapheneos.seedvault.core.backends.Backend
import app.grapheneos.seedvault.core.backends.BackendId
import app.grapheneos.seedvault.core.backends.BackendSaver
import app.grapheneos.seedvault.core.backends.Constants
import app.grapheneos.seedvault.core.backends.FileBackupFileType
import app.grapheneos.seedvault.core.backends.FileHandle
import app.grapheneos.seedvault.core.backends.FileInfo
import app.grapheneos.seedvault.core.backends.LegacyAppBackupFile
import app.grapheneos.seedvault.core.backends.TopLevelFolder
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.share.DiskShare
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import kotlin.reflect.KClass

public class SmbBackend(
    public val config: SmbConfig,
) : Backend {
    override val id: BackendId = BackendId.SMB

    private val rootPath: String = config.path.trim('/')

    override suspend fun test(): Boolean {
        return withShare { share ->
            if (rootPath.isNotEmpty()) {
                ensureDirectoryExists(share, rootPath)
            }
            true
        }
    }

    override suspend fun getFreeSpace(): Long? {
        return withShare { share ->
            try {
                share.getShareInformation().freeSpace
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun save(
        handle: FileHandle,
        saver: BackendSaver
    ): Long {
        return withShare { share ->
            val remotePath = toRemotePath(handle.relativePath)
            val parentPath = handle.relativePath.substringBeforeLast('/', "")
            if (parentPath.isNotEmpty()) {
                ensureDirectoryExists(share, parentPath)
            }

            share.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(
                    SMB2ShareAccess.FILE_SHARE_READ,
                    SMB2ShareAccess.FILE_SHARE_WRITE,
                    SMB2ShareAccess.FILE_SHARE_DELETE,
                ),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
            ).use { file ->
                file.outputStream.use { saver.save(it) }
            }
        }
    }

    override suspend fun load(handle: FileHandle): InputStream {
        val client = SMBClient()
        val connection = client.connect(config.host)
        val session = connection.authenticate(createAuthContext())
        val share = session.connectShare(config.share) as DiskShare

        try {
            val remotePath = toRemotePath(handle.relativePath)
            val file = share.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(
                    SMB2ShareAccess.FILE_SHARE_READ,
                    SMB2ShareAccess.FILE_SHARE_WRITE,
                    SMB2ShareAccess.FILE_SHARE_DELETE,
                ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
            )
            val delegate = file.inputStream
            return object : FilterInputStream(delegate) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        file.close()
                        share.close()
                        connection.close()
                        client.close()
                    }
                }
            }
        } catch (e: Exception) {
            share.close()
            connection.close()
            client.close()
            throw e
        }
    }

    override suspend fun list(
        topLevelFolder: TopLevelFolder?,
        vararg fileTypes: KClass<out FileHandle>,
        callback: (FileInfo) -> Unit
    ) {
        if (TopLevelFolder::class in fileTypes) throw UnsupportedOperationException()
        if (LegacyAppBackupFile::class in fileTypes) throw UnsupportedOperationException()
        if (LegacyAppBackupFile.IconsFile::class in fileTypes) throw UnsupportedOperationException()
        if (LegacyAppBackupFile.Blob::class in fileTypes) throw UnsupportedOperationException()

        withShare { share ->
            val listRoot = if (topLevelFolder == null) rootPath else toRemotePath(topLevelFolder.relativePath)
            if (listRoot.isNotEmpty() && !share.folderExists(listRoot)) return@withShare

            listFilesRecursive(share, listRoot) { relativePath, size ->
                val parentDir = relativePath.substringBeforeLast('/', "")
                val parentName = parentDir.substringAfterLast('/', "")
                val fileName = relativePath.substringAfterLast('/')

                if (AppBackupFileType.Snapshot::class in fileTypes || AppBackupFileType::class in fileTypes) {
                    val match = Constants.appSnapshotRegex.matchEntire(fileName)
                    if (match != null && Constants.repoIdRegex.matches(parentName)) {
                        callback(
                            FileInfo(
                                AppBackupFileType.Snapshot(
                                    repoId = parentName,
                                    hash = match.groupValues[1],
                                ),
                                size,
                            ),
                        )
                    }
                }
                if ((AppBackupFileType.Blob::class in fileTypes || AppBackupFileType::class in fileTypes)) {
                    val grandParent = parentDir.substringBeforeLast('/', "")
                    val repoId = grandParent.substringAfterLast('/', "")
                    if (Constants.repoIdRegex.matches(repoId) && Constants.blobFolderRegex.matches(parentName)) {
                        if (Constants.blobRegex.matches(fileName)) {
                            callback(
                                FileInfo(
                                    AppBackupFileType.Blob(
                                        repoId = repoId,
                                        name = fileName,
                                    ),
                                    size,
                                ),
                            )
                        }
                    }
                }
                if (FileBackupFileType.Snapshot::class in fileTypes || FileBackupFileType::class in fileTypes) {
                    val match = Constants.fileSnapshotRegex.matchEntire(fileName)
                    if (match != null) {
                        callback(
                            FileInfo(
                                FileBackupFileType.Snapshot(
                                    androidId = parentName.substringBefore('.'),
                                    time = match.groupValues[1].toLong(),
                                ),
                                size,
                            ),
                        )
                    }
                }
                if ((FileBackupFileType.Blob::class in fileTypes || FileBackupFileType::class in fileTypes)) {
                    val grandParent = parentDir.substringBeforeLast('/', "")
                    val androidIdSv = grandParent.substringAfterLast('/', "")
                    if (Constants.fileFolderRegex.matches(androidIdSv) && Constants.chunkFolderRegex.matches(parentName)) {
                        if (Constants.chunkRegex.matches(fileName)) {
                            callback(
                                FileInfo(
                                    FileBackupFileType.Blob(
                                        androidId = androidIdSv.substringBefore('.'),
                                        name = fileName,
                                    ),
                                    size,
                                ),
                            )
                        }
                    }
                }
                if (LegacyAppBackupFile.Metadata::class in fileTypes &&
                    fileName == Constants.FILE_BACKUP_METADATA &&
                    parentName.matches(Constants.tokenRegex)
                ) {
                    callback(
                        FileInfo(
                            LegacyAppBackupFile.Metadata(parentName.toLong()),
                            size,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun remove(handle: FileHandle) {
        withShare { share ->
            val remotePath = toRemotePath(handle.relativePath)
            if (!share.fileExists(remotePath)) return@withShare
            share.rm(remotePath)
        }
    }

    override suspend fun rename(
        from: TopLevelFolder,
        to: TopLevelFolder
    ) {
        withShare { share ->
            val fromPath = toRemotePath(from.relativePath)
            val toPath = toRemotePath(to.relativePath)
            share.openDirectory(
                fromPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                EnumSet.of(
                    SMB2ShareAccess.FILE_SHARE_READ,
                    SMB2ShareAccess.FILE_SHARE_WRITE,
                    SMB2ShareAccess.FILE_SHARE_DELETE,
                ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE),
            ).use { directory ->
                directory.rename(toPath)
            }
        }
    }

    override suspend fun removeAll() {
        withShare { share ->
            if (rootPath.isNotEmpty() && !share.folderExists(rootPath)) return@withShare
            removeDirectoryContents(share, rootPath)
        }
    }

    override fun isTransientException(e: Exception): Boolean {
        return when (e) {
            is IOException,
            is SMBApiException,
            is TransportException -> true
            else -> false
        }
    }

    override val providerPackageName: String? = null // 100% built-in plugin

    private inline fun <T> withShare(block: (DiskShare) -> T): T {
        val client = SMBClient()
        client.use {
            val connection = client.connect(config.host)
            try {
                val session = connection.authenticate(createAuthContext())
                val share = session.connectShare(config.share) as DiskShare
                share.use {
                    return block(it)
                }
            } finally {
                connection.close()
            }
        }
    }

    private fun createAuthContext(): AuthenticationContext {
        return AuthenticationContext(
            config.username,
            config.password.toCharArray(),
            config.domain ?: "",
        )
    }

    private fun toRemotePath(relativePath: String): String {
        val normalized = relativePath.trim('/')
        return if (rootPath.isEmpty()) normalized else if (normalized.isEmpty()) rootPath else "$rootPath/$normalized"
    }

    private fun ensureDirectoryExists(share: DiskShare, relativePath: String) {
        val normalized = relativePath.trim('/')
        if (normalized.isEmpty()) return

        var current = ""
        for (segment in normalized.split('/')) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            val remote = toRemotePath(current)
            if (!share.folderExists(remote)) {
                share.mkdir(remote)
            }
        }
    }

    private fun listFilesRecursive(
        share: DiskShare,
        basePath: String,
        callback: (relativePath: String, size: Long) -> Unit,
    ) {
        val path = if (basePath.isEmpty()) "" else basePath
        share.list(path).forEach { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@forEach
            val relativePath = if (path.isEmpty()) name else "$path/$name"
            if ((info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L) {
                listFilesRecursive(share, relativePath, callback)
            } else {
                callback(relativePath, info.endOfFile)
            }
        }
    }

    private fun removeDirectoryContents(share: DiskShare, basePath: String) {
        val path = if (basePath.isEmpty()) "" else basePath
        share.list(path).forEach { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@forEach
            val childPath = if (path.isEmpty()) name else "$path/$name"
            if ((info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L) {
                removeDirectoryContents(share, childPath)
                share.rmdir(childPath, false)
            } else {
                share.rm(childPath)
            }
        }
    }
}
