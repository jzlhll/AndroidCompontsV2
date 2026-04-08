---
name: copyright-header
description: 规定源码文件头 Copyright 的固定模板与占位符来源。在用户要求添加版权声明或文件头时使用。
---

$email: 从项目根目录的AiUserConfig.properties文件中获取
$time：请根据当前时间填写，格式为yyyy/MM/dd
$year: 请根据当前时间填写，格式为yyyy

格式如下，不得做任何字符修改，只填入以上3个变量：

```
/* 
* Created by $email on $time.
*
* Copyright (C) $year [allan]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@allan]
*/
```
