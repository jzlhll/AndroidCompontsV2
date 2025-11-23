package com.au.jobstudy.words.domain

import com.au.jobstudy.words.data.entities.ImportVersionEntity
import com.au.jobstudy.words.domain.beans.RowInfo

interface IImportExcelRepository {
    suspend fun isImported() : Boolean

    suspend fun importVersionInfo() : ImportVersionEntity?

    suspend fun importVersion(version: ImportVersionEntity)

    /** 批量导入函数 */
    suspend fun importAllRows(rows: List<RowInfo>)
}