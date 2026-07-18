package com.allan.mydroid.nanohttp

import com.au.module_okhttp.api.ResultBean
import com.au.module_gson.toGsonString
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse

const val MIME_TYPE_JSON = "application/json; charset=utf-8"

const val CODE_SUC = "0"
const val CODE_FAIL = "-1"
const val CODE_FAIL_RECEIVER_CHUNK = "-101"
const val CODE_FAIL_MERGE_CHUNK = "-102"
const val CODE_FAIL_MD5_CHECK = "-103"

fun ResultBean<*>.okJsonResponse() : Response{
    return newFixedLengthResponse(
        Status.OK,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}

fun ResultBean<*>.badRequestJsonResponse() : Response{
    return newFixedLengthResponse(
        Status.BAD_REQUEST,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}

fun ResultBean<*>.jsonResponse(status: Response.IStatus) : Response{
    return newFixedLengthResponse(
        status,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}
