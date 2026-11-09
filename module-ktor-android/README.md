# module-ktor-android

Android library，通过 `api(project(":module-ktor-base"))` 暴露基础契约。只提供 OkHttp、File／Uri、日志、存储和 TLS 平台适配，不依赖 Module-Okhttp，不添加 Glide Module 或编译器。完整请求规则见 [module-ktor-base](../module-ktor-base/README.md)。

## 创建与资源所有权

```kotlin
val origin = Origin.from(Url("https://example.com"))
val owner = AndroidKtorClientFactory().create(
    KtorClientConfig(
        targetPolicy = TargetPolicy.FixedOrigins(setOf(origin), allowQuery = true),
        credentialPolicy = CredentialPolicy.FixedOrigins(
            mapOf(origin to CredentialRequirement.None),
        ),
    ),
)
val executor = owner.executor
```

`create` 是挂起接口，内部阻塞操作切到 IO。按服务、认证会话和 TLS 配置长期复用 owner；不要每次请求重新创建。每个 owner 自建 OkHttpClient、Dispatcher、ConnectionPool 和可选 Cache，不跨 owner 共享这些资源。关闭时先取消或等待请求、关闭并等待 Ktor 和引擎，再释放连接池及线程池，最后关闭 Cache。

工厂通过 `preconfigured` 提供完整 OkHttpClient，关闭两层自动重定向、连接重试和业务 Authenticator。Ktor 3.6.1 的默认 [OkHttpConfig](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpConfig.kt) 会将连接重试重新设为 true，因此唯一的 `engine.config` 校准是再次指定 `retryOnConnectionFailure(false)`；TLS、代理、DNS、基础超时、连接池及 Cache 均只在自建 builder 上设置。

基础连接、读、写超时来自 `config.timeout`。逐请求 HttpTimeout 由引擎派生配置，socket timeout 同时映射到 OkHttp read／write timeout，不修改共享客户端。`TimeoutPolicy.Short`、`Normal`、`LongWait` 保留 15 秒、45 秒、5 分钟等待预设；`requestMillis = null` 表示不设整个请求总超时。

工厂组合调用方分类器和 `AndroidTransportFailureClassifier`，识别 DNS、连接、连接丢失、超时、证书、握手及协议异常；不按消息文本分类。默认日志走 `AndroidNetworkLogger`，调试使用 `logdNoFile`，显式开启诊断落盘时使用 `logd`，失败只向 `loge` 输出受控摘要。

## 上传与文件下载

`FileUploadSource`、`UriUploadSource` 的打开和读取使用 IO。长度未知保持 null，不预读整个文件；Uri 无权限或无输入流时明确抛存储错误。`uriUploadSource` 复用项目 Uri 元数据工具。来源默认不可重放；仅在内容稳定、保证可重复打开时指定 `replayable = true`。文件来源还会核对各次打开的长度和修改时间。

```kotlin
val file = executor.downloadToFile(
    url = Url("https://example.com/archive.zip"),
    target = destination,
    options = RequestOptions(
        timeout = TimeoutPolicy(30_000, 180_000, requestMillis = null),
        acceptedStatuses = setOf(200),
        onProgress = { progress -> consumeProgress(progress.transferredBytes) },
    ),
    replaceExisting = true,
)
```

`FileDownloadSink` 惰性创建同目录唯一 `.ktor-*.part` 文件，同一目标并发直接拒绝。默认不覆盖最终文件：使用同文件系统硬链接原子发布，目标已存在则失败；显式替换使用原子 move 加 REPLACE_EXISTING，不能先删旧文件。不支持原子提交时默认失败，只有明确指定 `allowNonAtomicCommit = true` 才退回非原子 move。

成功前先关闭流、完成提交；失败或取消等待传输及写入退出，再关闭并删除本次临时文件。`keepPartialFile` 只保留本次部分文件，不代表支持续传。模块不删除业务临时目录；上层应在下载、解压和相关子任务全部结束后清理自己的目录。

## Cookie 与缓存

`AndroidCookiesStorage` 使用 OkHttp 的 Cookie 域名及路径规则，处理 host-only、domain、path、secure、过期、删除及同名不同路径。持久 Cookie 按 namespace 写入限容 JSON，session Cookie 只存在当前实例，不跨重启保存。`AndroidApiCacheStore` 提供有有效期、容量和最近使用淘汰的持久响应缓存。两者写入使用同目录临时文件原子替换，并在所属请求内等待完成。

```kotlin
val namespace = StorageNamespace("service", principalId)
val cookies = AndroidCookiesStorage(storageDirectory, namespace)
val apiCache = AndroidApiCacheStore(storageDirectory, namespace)
val config = baseConfig.copy(
    namespace = namespace,
    cookies = cookies,
    apiCache = ApiCacheConfig(apiCache, ttlMillis = 60_000),
)
val owner = AndroidKtorClientFactory().create(
    config,
    cache = AndroidHttpCacheConfig(storageDirectory, namespace),
)
```

HTTP Cache 默认关闭，向工厂传入 `cache` 即启用，namespace 必须与 `config.namespace` 一致，Transfer 客户端禁止启用；同一 Cache 目录只能由一个活跃 owner 持有。敏感请求和流式传输强制发送 `Cache-Control: no-store`，不安装 Ktor HttpCache。namespace 目录名与业务缓存键使用 SHA-256，文件名不保存 Token 或 URL。存储根目录应使用应用私有目录。切换账号或凭据先关闭并等待旧工作，再清理或替换存储；不共享活跃 owner 的存储实例。

## TLS 与 LAN

`AndroidTlsConfig` 的证书信任与 hostname 校验独立配置：

| 设置 | 行为 |
| --- | --- |
| `CertificateTrust.System()` | 默认系统信任 |
| `AppendCertificates` | 系统信任加指定证书 |
| `ReplaceCertificates` | 仅信任指定证书 |
| `All()` | 跳过证书链校验 |
| `HostnamePolicy.Verify` | 默认校验 hostname |
| `HostnamePolicy.Off` | 显式关闭 hostname 校验 |

无匹配 IP SAN 的 LAN 自签名证书可使用 ReplaceCertificates 加 Off，保留证书链约束。有匹配 SAN 时保持 Verify。TLS 变化应创建新 owner 并关闭旧 owner；pinning 使用 `certificatePinner` 参数。明文 LAN 的 Network Security Config 由 app 按访问范围设置，模块不放宽全局安全配置。

## R8 与接入边界

`consumer-rules.pro` 已通过 consumerProguardFiles 注册。已核对 serialization 1.11.0 的 core JVM JAR 包含 `META-INF/proguard` 及 `META-INF/com.android.tools/r8` 规则，普通 DTO、ApiEnvelope、Page 和 Cookie 记录均使用编译期或显式 serializer，不复制整包保留规则。消费方合并结果、minify 构建与实际反序列化仍待验证。

当前没有 app 声明新模块依赖。agit 为后续唯一接入方：归档传输迁移到本模块，JGit clone／fetch／push 保持原链路；其现有 SLF4J NOP provider 在接入时应与 Ktor 的 2.0.19 API 对齐，不在 library 中强制静默所有消费方的日志。
