package com.au.module_android.simplelivedata

import com.au.module_android.simplelivedata.ReadOnlyMustNoStickLiveData
import com.au.module_android.simplelivedata.ReadOnlyNoStickLiveData
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.isMainThread
import java.lang.Iterable
import java.util.Map
import kotlin.concurrent.Volatile

/**
 * @author allan
 * @date :2024/6/20 11:20
 * @description:
 * 1. 添加支持真实的数据；
 * 2. 添加noStick(去除粘性)；
 * 3. 增加safe操作。
 */
open class NoStickLiveData<T> : RealValueLiveData<T> {

    /**
     * 本身来讲，默认支持NoStick即可。
     */
    var isSupportNoStick: Boolean = true

    private var mVersion: Long

    /**
     * 是否有值。不能判断为null。因为第一次设置成null，也算有值。这样的话，只能判断mVersion
     */
    fun isHasValue(): Boolean {
        return mVersion >= 0
    }

    constructor(value: T) : super(value) {
        mVersion = 0
    }

    constructor() : super() {
        mVersion = -1
    }

    //////////////////////////////////
    /////// safe处理 & realValue
    //////////////////////////////////

    fun setValueSafe(value : T?) {
        val isPosting = mIsPosting
        val isMainThread = Looper.getMainLooper() === Looper.myLooper()
        if (isPosting && isMainThread) {
            Log.w("NoStickLiveData", "When posting must postValue!")
            postValue(value)
        } else if (!isPosting && isMainThread) {
            setValue(value)
        } else {
            postValue(value)
        }
    }

    @Deprecated("如非必要，推荐使用setValueSafe。")
    override fun setValue(value: T?) {
        mVersion++
        mIsPosting = false
        super.setValue(value)
    }

    @Volatile
    private var mIsPosting = false

    @Deprecated("如非必要，推荐使用setValueSafe。")
    override fun postValue(value: T?) {
        mIsPosting = true
        super.postValue(value)
    }

    //////////////////////////////////
    /////// no stick
    //////////////////////////////////

    override fun removeObserver(observer: Observer<in T>) {
        super.removeObserver(observer)
        if(isSupportNoStick) removeObserverUnStick(observer)
    }

    /**
     * 追加不处理粘性的方式
     */
    fun observeUnStick(owner: LifecycleOwner, observer: Observer<in T>) {
        if (!isSupportNoStick) {
            throw RuntimeException("Please set isSupportNoStick = true.")
        }
        super.observe(owner, NoStickWrapObserver(this, mVersion, observer = observer))
    }

    fun observeForeverUnStick(observer: Observer<in T>) {
        super.observeForever(NoStickWrapObserver(this, mVersion, observer = observer))
    }

    //反射拿到父类的field mObservers。
    private var mObservers:(java.lang.Iterable<java.util.Map.Entry<*, *>>)? = null

    private fun requireMObservers() : java.lang.Iterable<java.util.Map.Entry<*, *>> {
        val mOb = mObservers
        if (mOb == null) {
            var superClass: Class<*>? = javaClass.superclass
            while (superClass != null && superClass != Any::class.java) {
                if (superClass == LiveData::class.java) {
                    val field = superClass.getDeclaredField("mObservers")
                    field.isAccessible = true
                    val o = field.get(this)
                    mObservers = o as java.lang.Iterable<java.util.Map.Entry<*, *>>
                    break
                }
                superClass = superClass.superclass
            }
            return mObservers!!
        }
        return mOb
    }

    fun removeObserverUnStick(observer: Observer<*>) {
        requireMObservers().let { iter->
            val foundList = ArrayList<Observer<in T>>()
            for (entry in iter) {
                val wrap = entry.key as? NoStickWrapObserver<*>
                if (wrap != null && wrap.observer == observer) {
                    wrap.asOrNull<Observer<in T>>()?.apply {
                        foundList.add(this)
                    }
                }
            }
            foundList.forEach {
                super.removeObserver(it)
            }
        }
    }

    /**
     * 新增一个移除所有观察者的方法
     */
    fun removeAllObservers() {
        assert(isMainThread)
        for (entry in requireMObservers()) {
            super.removeObserver(entry.key as Observer<in T>)
        }
    }

    private class NoStickWrapObserver<D>(val self: NoStickLiveData<D>,
                                         val initVersion:Long,
                                         val observer: Observer<in D>)
            : Observer<D> {
        override fun onChanged(value: D) {
            if (initVersion < self.mVersion) {
                observer.onChanged(value)
            }
        }
    }
}

inline fun <reified B> LiveData<B>.asNoStickLiveData() : NoStickLiveData<B> = this as NoStickLiveData<B>
inline fun <reified B> LiveData<B>.realValue() : B? = if(this is NoStickLiveData) this.realValue else this.value

/**
 * 变成只读
 */
fun <T> NoStickLiveData<T>.asReadOnly() : ReadOnlyNoStickLiveData<T> {
    val src = this
    return object : ReadOnlyNoStickLiveData<T> {
        override val value: T?
            get() = src.value

        override val realValue: T?
            get() = src.realValue

        override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
            src.observe(owner, observer)
        }

        override fun observeForever(observer: Observer<in T>) {
            src.observeForever(observer)
        }

        override fun observeUnStick(owner: LifecycleOwner, observer: Observer<in T>) {
            src.observeUnStick(owner, observer)
        }

        override fun observeForeverUnStick(observer: Observer<in T>) {
            src.observeForeverUnStick(observer)
        }

        override fun removeObserver(observer: Observer<in T>) {
            src.removeObserver(observer)
        }
    }
}

/**
 * 仅支持无粘性观察者
 */
fun <T> NoStickLiveData<T>.asReadOnlyMustNoStick() : ReadOnlyMustNoStickLiveData<T> {
    val src = this
    return object : ReadOnlyMustNoStickLiveData<T> {
        override val value: T?
            get() = src.value

        override val realValue: T?
            get() = src.realValue

        override fun observeUnStick(owner: LifecycleOwner, observer: Observer<in T>) {
            src.observeUnStick(owner, observer)
        }

        override fun observeForeverUnStick(observer: Observer<in T>) {
            src.observeForeverUnStick(observer)
        }

        override fun removeObserver(observer: Observer<in T>) {
            src.removeObserver(observer)
        }
    }
}
