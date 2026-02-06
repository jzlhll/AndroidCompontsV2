---
name: 日志注释和变量命名规范
description: 当涉及到开发日志打印，注释，和变量命名规范时，遵守它。
---

# 开发规范
注释：仅类、构造函数、公开变量用/** ... */；函数内部必要时用// ...（超80行或函数名不表意时）
当一组类似变量或函数定义后，使用//region 和 //endregion来注释
命名：bool变量用isXXX，私有变量mXXX，公开变量xxx，静态变量sXXX
不提示null判断，不写完整类路径

# 日志规范
优先logdNoFile（仅控制台）
函数入口用logdNoFile{}，函数名拆分（如onDeviceListChanged→on deviceList changed）
严重错误使用loge{}/logEx(throwable){}