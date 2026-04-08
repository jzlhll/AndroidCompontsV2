---
name: websocket-commands
description: 规定 WebSocket/MQTT 指令的请求与响应类命名、字段注释与 SerialName 规则。在实现 devices/command 风格的新 Command/Response 类时使用。
---

参考ImagechoReframe/src/main/java/imagecho/reframe/devices/command/liveconnection实现方式，实现一个新的XXXCommand类和XXXResponse类。
要求：
- 类名命名规则，如果名字超过25个字符，XXCommand就用XXCmd，XXResponse就用XXResp
- 将字段的一些描述性注释，也追加到代码对应的字段上
- 如果请求格式中，提供的字段注释为required by MQTT或者MQTT require，则：
   - 类申明构造函数追加isMqtt的参数和父类传参
   - 对于MQTT required字段，使用if(isMqtt)判断追加，但msgID字段例外（始终无条件添加），例如："userID": "12345", // MQTT required，我们就if(isMqtt) put("userID", userId)
   - 响应格式中的字段不管该规则，列出使用可空字段比如String?
- 响应类中，只有字段名包含下划线（如people_and_pets）时才需要@SerialName注解，字段名本身没有下划线则不需要添加@SerialName
- 请求类中，因为是直接put到JSONObject，所以字段名是直接写的，入参则用驼峰命名
