package com.allan.mydroid.beansinner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LEGACY_IMPORT_BATCH_ID = "legacy"

fun formatImportBatchTime(importedAt: Long): String {
    return if (importedAt > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(importedAt))
    } else {
        "--"
    }
}

interface ImportBatchItem {
    val importBatchId: String?
    val importedAt: Long
    val importOrder: Int
}

/** 本机列表和手机客户端共用批次顺序，旧数据统一归入历史批次。 */
fun <T : ImportBatchItem> groupImportBatches(files: List<T>): List<List<T>> {
    return files.groupBy { it.importBatchId?.ifBlank { null } ?: LEGACY_IMPORT_BATCH_ID }
        .values.sortedWith(compareByDescending<List<T>> { it.first().importedAt }
            .thenBy { it.first().importBatchId.orEmpty() })
        .map { batch -> batch.sortedBy { it.importOrder } }
}
