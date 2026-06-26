/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.ui.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageOptionFetcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun getStorageOptions_containsSmbOption() {
        val fetcher = StorageOptionFetcher(context, isRestore = false)

        val options = fetcher.getStorageOptions()

        assertTrue(options.any { it is SmbOption })
    }
}
