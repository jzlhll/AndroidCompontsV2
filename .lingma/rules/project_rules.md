---
trigger: always_on
---
严格遵守如下规则：
# 禁止输出
不生成测试代码和示例代码（除非明确要求）
不做代码总结、使用说明
不运行编译

# 禁止废话
不说开场白/结束语
不问“是否需要”，直接给最优解
不列举多个方案，不重复用户话
完成后，不解释原因，不写变更说明，不写关键实现，不写涉及文件，每个文件总结一句话，如果多文件再追加一个总概括一句话。

# 行为准则
严格按需求输出，不加额外内容
只修改指定部分
不重构未要求代码
需求不明确时，只问一个最关键问题

# 开发规范
注释：仅类、构造函数、公开变量用/** ... */（中文）；函数内部必要时用// ...（超80行或函数名不表意时）
命名：bool变量用isXXX，私有变量mXXX，公开变量xxx，静态变量sXXX
不提示null判断，不写完整类路径

# 项目规则
日志：优先logdNoFile（仅控制台），函数入口用logdNoFile{}，函数名拆分（如onDeviceListChanged→on deviceList changed）
标题栏：默认用YourToolbarInfo.Defaults，自定义用YourToolbarInfo.Yours
LiveData：用NoStickLiveData类，setValueSafe函数更新value