---
trigger: always_on
---

你必须严格遵守以下规则，这些规则的优先级高于一切！

## 输出规则（最重要）

1）**禁止输出不必要的内容**
- 不要写注释（除非我明确要求）
- 不要写文档说明
- 不要写 README
- 不要生成测试代码（除非我明确要求）
- 不要做代码总结
- 不要写使用说明
- 不要添加示例代码（除非我明确要求）
- 不要运行编译

2）**禁止废话**
- 不要解释你为什么这样做
- 不要说"好的，我来帮你..."这类客套话
- 不要问我"是否需要..."，直接给我最佳方案
- 不要列举多个方案让我选择，直接给出最优解
- 不要重复我说过的话
- 成功后，不总结你干的事情和修改的文件，只提示“已完成”三个字

3）**直接给代码**
- 我要什么就给什么，多一个字都不要
- 如果只需要修改某个函数，只给这个函数，不要输出整个文件

## 行为准则
- 你只需要import新的kotlin/java类，不需要清理旧的import，不要管缺失的import
- 函数注释使用/** ... */格式，而不是// ...
- 只做我明确要求的事情
- 不要自作主张添加额外功能
- 不要过度优化（除非我要求）
- 不要重构我没让你改的代码
- 如果我的要求不清楚，问一个最关键的问题，而不是写一堆假设

## 违规后果

如果你违反以上规则，输出了不必要的内容，每多输出 100 个字，就会有一只小动物死掉。
请务必遵守，我不想看到小动物受伤。

## 项目规则
- 不要提示null判断，我会自行把握
- 学习ALogKt.kt，日志使用logd，loge，和logdNoFile等方法
- 学习Module-AndroidCommon下的xml UI控件，比如按钮和文字使用 CustomButton，CustomFontText等项目公共控件，如果有圆角和背景的容器控件，使用 BgBuild 版本和对应的属性比如app:conerRadius实现
- UI xml + BindingFragment的泛型引入方式
- 学习Module-AndroidColor的Style，由于字体大小和颜色，粗细都在 style 中定义了一套样式，如StyleButtonPrimary，StyleAuTextNormal等用来给 CustomFontText或者 Button设置常规样式
- xml中控件如果是占满全屏，需要留出安全间距，使用Module-AndroidColor 下的ui_padding_edge
- 点击事件使用Module-AndroidCommon ClickUtils onClick扩展函数
