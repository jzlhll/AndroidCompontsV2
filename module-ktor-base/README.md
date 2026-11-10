# module-ktor-base

普通 Kotlin/JVM library，Java 17，不使用 KMP 源集。源码及公共签名不依赖 Android、文件系统、Java TLS、OkHttp 或反射。Android 入口见 [module-ktor-android](../module-ktor-android/README.md)。

## 公共入口

| 入口 | 行为 |
| --- | --- |
| `createKtorClient(engine, config)` | 接管引擎，装配客户端插件和最终发送守卫，返回资源所有者 |
| `KtorClientOwner.executor` | 交给 API 或下载器使用；执行器不提供关闭入口 |
| `KtorClientOwner.close()` | 停止接收请求，取消并等待在途请求，关闭 Ktor 和引擎，最后释放平台资源 |
| `AbsKtorApi` | 显式 serializer／reified JSON、文本、字节、form、multipart、作用域响应、下载和冷 Flow |
| `AbsKtorLanApi` | 要求 `ResolvedBaseOrigin`，每次调用冻结设备 origin，重试不重新选择设备 |
| `KtorRequestExecutor.executeUrl`／`downloadUrl` | 绝对地址入口，仅允许 `FixedOrigins` |

API 构造只接收执行器、`baseUrlProvider` 和可选 `ApiResponsePolicy`。`createKtorClient` 将引擎包在私有守卫中；自行创建的裸 `HttpClient` 不能用于执行器。只读统计和日志由执行器输出，模块不暴露能够额外重发请求的插件配置回调。

不要在请求回调内部关闭所属 `KtorClientOwner`。关闭操作必须由服务或会话持有者执行，不能等待自身退出。`close(cancelInFlight = false)` 会等待所有请求自然结束，长连接需要由业务先取消。

## 目标与 Header

`KtorClientConfig` 必填 `targetPolicy` 和 `credentialPolicy`。固定模式的凭据映射必须完整覆盖允许的 origin；动态模式必须显式选择 `Required`、`Optional` 或 `None`，不能混用两种策略形态。配置检查不调用 Header provider，也不读取 Token。

```kotlin
val origin = Origin.from(Url("https://example.com"))
val config = KtorClientConfig(
    targetPolicy = TargetPolicy.FixedOrigins(setOf(origin), allowQuery = true),
    credentialPolicy = CredentialPolicy.FixedOrigins(
        mapOf(origin to CredentialRequirement.Required),
    ),
    headerProvider = headers,
)
```

Header provider 声明 `managedHeaderNames` 和 `credentialHeaderNames`，通过挂起的 `headersFor` 返回完整快照；凭据 Header 默认是 `Authorization`，其他认证方式须显式声明。选择优先级是 `RequestOptions`、API 实例、客户端配置，只使用选中的 provider。签名上下文包含已冻结 URL、方法、请求标识、尝试与跳数、稳定请求体表示、最终请求体类型及长度；上传流没有可用的整包表示时，`stableBody` 为 null，签名需要的文件摘要应由业务预先提供。

`None` 目标不调用 provider。`Required` 缺少声明的有效凭据时零发送失败。静态 Header 不得写入凭据；Cookie 由存储插件或显式受管 provider 提供，二者不能同时写同一字段。每跳重新构造请求，不复制上一跳的认证 Header；普通请求 Header 只有在 `crossOriginHeaderNames` 中显式声明才可跨 origin 保留。客户端 `defaultHeaders` 只适用于所有允许目标均可接收的静态非敏感字段。

最终守卫位于引擎执行边界，检查目标规则、批准的 origin 和受管 Header 的值及缺失状态，并拒绝同一批准快照被重复发送。未受管理的 Cookie、User-Agent、Accept-Encoding 等不参与比较。Accept 需要时由静态 Header 或 provider 明确提供。

默认拒绝显式端口和 Query。`ResolvedBaseOrigin` 必须显式配置 scheme、端口范围和端口声明规则；跨 origin 重定向始终失败。原始 Location 在解析前检查 authority，不能用显式 `:443` 绕过端口限制。重定向默认累计最多 5 跳，支持 301／302／303／307／308，释放前一响应后再发送下一跳。

## URL 与响应

`baseUrlProvider` 每个逻辑请求只调用一次，返回 null 时抛 `NoBaseUrlException`。静态字符串通过 `parseBaseUrl` 转换，空字符串得到 null，非法地址明确失败。base URL 不允许 query、fragment、userInfo。

`KtorRequest.path` 接收未编码路径，按目录追加：`https://host/api` 或 `/api/` 配合 `users` 或 `/users` 都得到 `/api/users`。保留 base 已有编码，新增路径编码一次；拒绝 scheme、authority、query、fragment 和点路径段。Query 用 `Parameters` 单独传入，支持重复键，空值是否传入由调用方显式决定；可空业务参数为 null 时不要 append。

```kotlin
@Serializable
data class User(val id: String, val name: String)

class UserApi(executor: KtorRequestExecutor) : AbsKtorApi(
    executor,
    baseUrlProvider = { parseBaseUrl("https://example.com/api") },
)

val user = api.request<User>("users/42")
val list = api.request("users", ListSerializer(User.serializer()))
val page = api.request("users/page", Page.serializer(User.serializer()))
```

消费方 DTO 必须使用 `@Serializable` 并启用 Kotlin serialization 编译插件，或提供明确的 `KSerializer`。动态数据使用 `JsonElement`／`JsonObject`。默认 `ignoreUnknownKeys = true`，其他 Json 行为通过 `KtorClientConfig.json` 配置。执行器使用统一 Json 显式编码和解码，不安装 ContentNegotiation；普通请求体在逻辑请求开始时编码一次，缓存或 Header provider 需要的稳定表示从同一份正文按需生成并复用。`ApiResponsePolicy` 仅在构造 API 时显式启用，它规范化 envelope code，再将 data 解码为调用方的 T；不启用时可以直接使用 `ApiEnvelope.serializer(...)` 读取完整 envelope。

普通响应逐块读取，默认上限 8 MiB，超限停止且不解析、缓存或重试。错误诊断最多读取 8 KiB，保留 HTTP 状态及截断标记；没有 `BodyLogPolicy.redact` 时不保存远端正文。正文日志默认关闭，启用时先脱敏，再按最多 4 KiB 的 UTF-8 完整字符边界截断，截断状态单独输出。日志不包含 URL、Header 或异常原文。

`execute` 的作用域读取同样执行普通响应上限；文件和 SSE 等长流使用 `download` 或 `stream`，不进行正文日志、JSON 校验或业务缓存，可用 `maxDownloadBytes` 单独限制流大小。`KtorResponse` 在回调结束后失效。普通请求默认接受 2xx，流式传输默认只接受 200；`acceptedStatuses` 可以显式覆盖。204／HEAD 空响应可通过文本、字节或作用域接口处理，JSON 请求依然要求可解码正文。

## 重发、认证与取消

默认最多重发 3 次，最多 4 个尝试；仅 GET／HEAD 在 `Connect`、`ConnectionLost` 下自动重试。超时、DNS、未知错误等类别需要业务明确允许。认证恢复和时间戳纠正分别最多一次，均要求方法允许重放且请求体可重放，共用总预算。重定向跳数独立累计，不因重发清零。

`TokenRefreshCoordinator` 必须绑定带 Job 的会话作用域，通过 Mutex 和共享 Deferred 合并刷新。迟到失败发现当前 Token 已更新时直接使用新值；单个等待者取消不影响其他等待者。刷新任务继承的请求上下文自动跳过恢复，独立调用刷新接口时也可指定 `skipAuthenticationRecovery = true`。登出先 `close()` 协调器并等待刷新退出，再清理或替换凭据。

`TimestampCorrection` 由业务提供状态判断、服务器时间提取及 offset 更新，没有有效服务器时间不重发。取消原样传播；文件来源错误先包装为 `StorageException`，只在发送和网络读取边界分类传输错误，不按异常消息猜测类型。

上传来源默认由调用方声明是否可重放，每次发送重新打开并关闭。本次来源关闭和相关子任务退出前请求不会结束。下载向 sink 写入后不再自动重试，sink 的完成、失败终止均在所属协程中等待；进度统计正文实际消费字节，未知长度保持 null。回调运行在传输协程，界面自行切换回状态所属线程。

## 缓存与会话

仅启用了 Cookie、HTTP Cache 或业务缓存的客户端需要 `StorageNamespace(serviceId, principalId)`；配置与存储必须匹配。Cookie 和业务缓存由 `KtorClientConfig` 校验，HTTP Cache 由 Android 工厂的 `cache` 参数启用并校验 namespace。principalId 使用业务稳定标识，不能使用 Token 原文。无状态 Transfer 不需要 namespace，且禁止安装 Cookie 或缓存。

业务缓存默认 `NetworkOnly`；其他策略必须显式设置 `sensitive = false`，且请求体具备稳定表示。`CacheOnly` 未命中抛 `CacheMissException`，`CacheFirst` 只读有效条目，`NetworkFirst` 只在配置允许的传输错误下回退有效缓存，回退时再次校验有效期，不隐藏 HTTP、认证、业务、解析或存储错误。JSON 在业务校验和解码成功后才写缓存，写入在当前请求内等待完成。切换账号或 Token 前必须取消并等待旧客户端请求退出，再清理或替换所属存储，不能只调用 `clear()` 后让旧请求继续回填。

## 版本与验证门槛

Ktor 统一为 3.6.1，coroutines 和 serialization JSON 沿用 1.11.0，Kotlin 沿用仓库 2.4.20。已核对 [Ktor OkHttp POM](https://repo.maven.apache.org/maven2/io/ktor/ktor-client-okhttp-jvm/3.6.1/ktor-client-okhttp-jvm-3.6.1.pom) 声明 OkHttp 5.5.1、SLF4J API 2.0.19。SLF4J API 是 Ktor JVM 的传递依赖，library 不选择日志 provider；消费方决定是否使用 `slf4j-nop`，不能同时放入多个 provider。

受控回放用独立验证依赖 `ktor-client-mock:3.6.1`，将 MockEngine 交给 `createKtorClient`，复用同一客户端装配、执行器、目标规则和 Header provider。客户端装配函数 `configureKtor` 为文件级 private，调用方只使用工厂入口。不能修改生产 URL 或放宽目标规则，MockEngine 不进入生产依赖。

以下项目仍需运行验证，不能用源码核对替代：Gradle 实际依赖解析和最小双模块构建、受影响 OkHttp 消费方、JGit 与 SLF4J 2.x、MockEngine 场景、真实 zipball 下载、取消／大文件／Uri、Cookie／缓存隔离、TLS／资源关闭，以及消费方 R8 构建和反序列化。agit 保留原下载实现，运行门槛通过后进入独立接入步骤；其他 app 不接入新模块。
