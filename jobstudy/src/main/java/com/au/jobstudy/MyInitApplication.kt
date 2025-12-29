package com.au.jobstudy

import androidx.room.Room
import com.au.jobstudy.api.Api
import com.au.jobstudy.check.AppDatabase
import com.au.jobstudy.check.CheckConsts
import com.au.jobstudy.check.api.SummerGeneratorApi
import com.au.jobstudy.check.dao.CompletedDao
import com.au.jobstudy.check.dao.StarDao
import com.au.jobstudy.check.dao.WorkDao
import com.au.jobstudy.completed.CompletedViewModel
import com.au.jobstudy.star.StarConsts
import com.au.jobstudy.utils.Dayer
import com.au.jobstudy.utils.IFactoryDayer
import com.au.jobstudy.utils.ISingleDayer
import com.au.jobstudy.words.constants.WordsManager
import com.au.jobstudy.words.data.ImportExcelRepositoryImpl
import com.au.jobstudy.words.data.WordsDatabase
import com.au.jobstudy.words.data.WordsRepositoryImpl
import com.au.jobstudy.words.domain.IImportExcelRepository
import com.au.jobstudy.words.domain.IWordRepository
import com.au.jobstudy.words.ui.CheckViewModel
import com.au.jobstudy.words.ui.EnglishCheckFragment
import com.au.jobstudy.words.ui.ExcelLoadingFragment
import com.au.jobstudy.words.ui.LoadingTest
import com.au.jobstudy.words.ui.LoadingViewModel
import com.au.jobstudy.words.usecase.LoadingUseCase
import com.au.module_android.Globals
import com.au.module_android.InitApplication
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.ui.base.AbsFragment
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_cached.AppDataStore
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.beans.OkhttpInitParams
import com.au.module_okhttp.creator.AbsCookieJar
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.scope.dsl.activityScope
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * @author au
 * @date :2023/11/14 14:05
 * @description:
 */
class MyInitApplication : InitApplication() {
    override fun onCreate() {
        super.onCreate()

        val appModule = module {
            //app架构单例
            single { Globals.mainScope }        //两个单例的scope
            single { Globals.backgroundScope }  //两个单例的scope

            singleOf(::Api)
            singleOf(::SummerGeneratorApi)
            singleOf(::StarConsts)
            singleOf(::CheckConsts)

            singleOf(::WordsManager)

            //每次创建的配置类
            factoryOf(::AndroidSdkMapping)

            single<ISingleDayer> {
                Dayer()
            }
            factory<IFactoryDayer> {
                Dayer()
            }
            factory<IFactoryDayer> { (anyDay:String)->
                Dayer(anyDay)
            }
            factory<IFactoryDayer> { (anyDay:Int)->
                Dayer(anyDay)
            }
            viewModelOf(::CompletedViewModel)
        }

        val dbModule = module {
            //数据层
            single {
                Room.databaseBuilder(
                    androidApplication(),
                    AppDatabase::class.java,
                    "job_study_db"
                )
                    //.enableMultiInstanceInvalidation() 多进程启用
                    .build()
            }
            single<WorkDao> {
                get<AppDatabase>().getWorkDao()
            }
            single<StarDao> {
                get<AppDatabase>().getStarDao()
            }
            single<CompletedDao> {
                get<AppDatabase>().getCompletedDao()
            }

            single {
                Room.databaseBuilder(
                    androidApplication(),
                    WordsDatabase::class.java,
                    "job_study_words_db"
                )
                    //.enableMultiInstanceInvalidation() 多进程启用
                    .build()
            }
            single {
                get<WordsDatabase>().wordsDao()
            }
            //仓库层
//            singleOf(::AlbumDatabaseRepository)
        }

        val uiModule = module {
            //UI层
            factory<AbsFragment>(named(UiNames.ENGLISH_CHECK)) {
                EnglishCheckFragment(get())
            }
            factory<AbsFragment>(named(UiNames.EXCEL_LOADING)) {
                ExcelLoadingFragment()
            }

            factoryOf(::ImportExcelRepositoryImpl) bind IImportExcelRepository::class
            factoryOf(::WordsRepositoryImpl) bind IWordRepository::class
            factoryOf(::LoadingUseCase)
            viewModelOf(::LoadingViewModel)
            viewModelOf(::CheckViewModel)

            activityScope {
                scoped { LoadingTest() }
            }
//
//            fragmentScope {
//                scoped { LoadingTest() }
//            }
//            fragmentScope {
//                factory { LoadingTest() }
//            }
//
//            scope<ExcelLoadingFragment> {
//                scoped { LoadingTest() }
//            }
        }

        startKoin {
            modules(appModule, dbModule, uiModule)
            androidContext(this@MyInitApplication)
        }

        OkhttpGlobal.initBeforeAnyRequest(OkhttpInitParams().also {
            it.okHttpCookieJar = object : AbsCookieJar() {
                override fun saveToDisk(host: String, data: String) {
                    AppDataStore.save("okhttp_cookie_$host", data)
                }

                override fun loadFromDisk(host: String): String {
                    return AppDataStore.readBlocked("okhttp_cookie_$host", "")
                }
            }
        })

        GlobalBackgroundCallback.addListener(object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                logd { "update SummerConst when foreground $it" }
                if (!isBackground) {
                    Globals.mainScope.launchOnThread {
                        get<StarConsts>().onlyInitOnce()
                        get<ISingleDayer>().update()
                        get<CheckConsts>().whenTrigger()
                    }
                }
            }
        })

    }
}