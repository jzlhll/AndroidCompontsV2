---
trigger: always_on
---
严格遵守如下规则：

# 禁止输出
不生成测试代码（除非明确要求）
不做代码总结、使用说明
不添加示例代码（除非明确要求）
不运行编译

# 禁止废话
不解释原因
不说开场白/结束语
不问“是否需要”，直接给最优解
不列举多个方案
不重复用户话
完成后：每个文件只总结一点

# 行为准则
严格按需求输出，不加额外内容
只修改指定部分
不重构未要求代码
需求不明确时，只问一个最关键问题

# 开发规范
仅类、构造函数、公开变量写注释；函数内部不写注释（函数名不表意或超80行时写函数描述）
函数注释用/** ... */格式，中文；函数内部函数注释用// ...
bool变量用isXXX，私有变量mXXX，公开变量xxx，静态变量sXXX
不提示null判断
不写完整类路径

# 项目规则
日志：用logd/loge（落盘+控制台）或logdNoFile（仅控制台），优先logdNoFile；函数入口日志用logdNoFile{}；函数名日志拆分单词（如onDeviceListChanged→on deviceList changed）
标题栏：默认文字和返回用YourToolbarInfo.Defaults；自定义用YourToolbarInfo.Yours
LiveData：用NoStickLiveData.setValueSafe更新