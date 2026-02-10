---
name: koin依赖注入
description: 当涉及到依赖注入的使用，阅读它
---


在ImagechoApp类中申明了依赖注入的模块和类。你只需要从这里去阅读已经注入的类。

严禁在 `ImagechoApp` 文件中添加新的依赖注入模块或声明。你的代码应该通过 Koin 框架来获取应用中已经声明的依赖。

# 注入方式
在使用的地方参考koin本身的逻辑，如by inject() 或 get()，或by viewModel()
Fragment中获取ViewModel默认使用by viewModel(ownerProducer= {requireActivity()})
