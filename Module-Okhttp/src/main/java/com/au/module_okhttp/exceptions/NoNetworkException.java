package com.au.module_okhttp.exceptions;

import androidx.annotation.Keep;

import com.au.module_android.Globals;
import com.au.module_okhttp.R;

/**
 * Description 定义了一个没有网络的Exception
 */
@Keep
public final class NoNetworkException extends Exception {
    public NoNetworkException() {
        super(Globals.INSTANCE.getString(R.string.network_not_available));
    }

    public NoNetworkException(String s) {
        super(s);
    }
}