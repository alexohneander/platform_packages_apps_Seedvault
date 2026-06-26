/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.backend.smb

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val storagePluginModuleSmb = module {
    single { SmbHandler(androidContext(), get(), get(), get()) }
}
