---
name: koin-di
description: 规定 Koin 的模块注册与在 ViewModel、生命周期类、普通类中的注入方式。在添加依赖注入或新单例注册时使用。
---

Application类

# 注入规则
- 如果一个新类仅被同package下的其他类使用，那么不添加到 Application 类中，采用传统new class的方式
- 从 Application 类中阅读已经注入的类，是否存在，存在就通过后面的方法引入

# 方法1：注入到ViewModel或其他普通类中

在构造函数中注入，即kt的类名括号中。
比如：
```kotlin
 XXXViewModel(
    val xx: XXXHelper
 )

XXXHelper(
    val dao: XXXDao,
    val scope:BackAppScope
)
```

# 方法2：注入到生命周期类中
生命周期无法在构造函数中注入。因此：

- 一般的类：在Activity/Fragment中通过如by inject() 或 get()来注入
- ViewModel类：
    - 先import org.koin.androidx.viewmodel.ext.android.viewModel
    - 在Activity/Fragment中通过使用`by viewModels()`注入
    - 如果ViewModel是跨多文件共享，需通过`by viewModels(ownerProducer= {requireActivity()})`注入

# 方法3：注入到没有在 Application 中声明的类中

给该类追加实现KoinComponent接口，即可在该类中通过by inject()或get()来注入。
