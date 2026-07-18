package com.allan.mydroid.globals

import com.au.module_android.Globals
import java.io.File

const val CACHE_IMPORT_COPY_DIR = "nanoImport"
fun cacheImportCopyDir() = Globals.goodCacheDir.absolutePath + File.separatorChar + CACHE_IMPORT_COPY_DIR

private const val TEMP_CACHE_DIR = "nanoTmp"
fun nanoTempCacheDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_DIR

private const val TEMP_CACHE_CHUNKS_DIR = "nanoChunksTmp"
fun nanoTempCacheChunksDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_CHUNKS_DIR

private const val TEMP_CACHE_MERGED_DIR = "nanoMerged"
fun nanoTempCacheMergedDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_MERGED_DIR
