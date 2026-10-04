package childmonitor.platform

/** 查询应用持久目录所在卷的可用容量，由平台承担阻塞 IO。 */
fun interface StorageCapacity {
    suspend fun availableBytes(): Long
}
