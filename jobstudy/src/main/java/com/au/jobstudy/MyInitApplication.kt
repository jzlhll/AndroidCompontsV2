package com.au.jobstudy

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.au.jobstudy.api.Api
import com.au.jobstudy.check.AppDatabase
import com.au.jobstudy.check.CheckConsts
import com.au.jobstudy.check.api.SummerGeneratorApi
import com.au.jobstudy.completed.CompletedViewModel
import com.au.jobstudy.star.StarConsts
import com.au.jobstudy.utils.Dayer
import com.au.jobstudy.utils.IFactoryDayer
import com.au.jobstudy.utils.ISingleDayer
import com.au.module_android.Globals
import com.au.module_android.InitApplication
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_cached.AppDataStore
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.beans.OkhttpInitParams
import com.au.module_okhttp.creator.AbsCookieJar
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
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
                    "jobstudy_db"
                )
                    //.enableMultiInstanceInvalidation() 多进程启用
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }
                    })
                    .build()
            }
            single { get<AppDatabase>().getWorkDao() }
            single { get<AppDatabase>().getStarDao() }
            single { get<AppDatabase>().getCompletedDao() }

            //仓库层
//            singleOf(::AlbumDatabaseRepository)
        }


        startKoin {
            modules(appModule, dbModule )
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