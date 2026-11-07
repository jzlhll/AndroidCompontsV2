package com.au.agit

import com.au.agit.data.*
import com.au.agit.git.*
import com.au.agit.ui.AppViewModel
import com.au.agit.ui.RepositoryViewModel
import com.au.agit.ui.LogsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

val agitModule = module {
    single { CatalogStore(androidContext()) }
    single { TokenVault() }
    single { UserFeedback() }
    single { LogRepository(androidContext()) }
    single { RepositoryPaths(File(androidContext().filesDir, "shared")) }
    single { AndroidGitEnvironment(androidContext()) }
    single { JGitEngine(get(), get()) }
    single { AGitRepository(androidContext(), get(), get(), get()) }
    viewModel { AppViewModel(get(), get()) }
    viewModel { parameters -> RepositoryViewModel(parameters.get(), get(), get()) }
    viewModel { parameters -> LogsViewModel(get(), get(), parameters.getOrNull<String>()) }
}
