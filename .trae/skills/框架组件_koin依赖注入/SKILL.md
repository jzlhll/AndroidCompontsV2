---
name: koin依赖注入
description: 当涉及到依赖注入的使用，阅读它
---


在Application类中申明了依赖注入的模块和类。你只需要从这里去阅读已经注入的类。

不要在 `Application` 文件中添加新的依赖注入模块或声明。引用外部类，优先阅读Application中是否存在声明的类。

# 注入方式
在使用的地方参考koin本身的逻辑，如by inject() 或 get()，或by viewModel()
Fragment中获取ViewModel默认使用by viewModel(ownerProducer= {requireActivity()})
