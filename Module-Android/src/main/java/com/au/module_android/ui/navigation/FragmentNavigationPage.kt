package com.au.module_android.ui.navigation

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import kotlin.jvm.java

@Keep
data class FragmentNavigationPage(
    val pageId:String,
    val fragmentClass:Class<out Fragment>,
    val params: Bundle? = null,
    val isStartPage: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readSerializable() as Class<out Fragment>,
        parcel.readBundle(Bundle::class.java.classLoader),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(pageId)
        parcel.writeSerializable(fragmentClass)
        parcel.writeBundle(params)
        parcel.writeByte(if (isStartPage) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FragmentNavigationPage> {
        override fun createFromParcel(parcel: Parcel): FragmentNavigationPage {
            return FragmentNavigationPage(parcel)
        }

        override fun newArray(size: Int): Array<FragmentNavigationPage?> {
            return arrayOfNulls(size)
        }
    }
}