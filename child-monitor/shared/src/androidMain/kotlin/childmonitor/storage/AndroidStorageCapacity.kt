package childmonitor.storage

import android.content.Context
import android.os.StatFs
import childmonitor.platform.StorageCapacity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 容量统计包含当前应用卷上的暂存内容，不创建正式监控目录。 */
class AndroidStorageCapacity(context: Context) : StorageCapacity {
    private val filesDir = context.applicationContext.filesDir

    override suspend fun availableBytes(): Long = withContext(Dispatchers.IO) {
        StatFs(filesDir.absolutePath).availableBytes
    }
}
