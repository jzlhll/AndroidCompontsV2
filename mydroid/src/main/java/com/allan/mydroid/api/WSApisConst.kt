package com.allan.mydroid.api

import sp.EncryptString

@EncryptString
class WSApisConst {
    companion object {
val API_WS_SEND_FILE_LIST = /*Encrypted: s_sendFileList*/ sp.StringEncrypt.decrypt("7K2mwJcAxrhPptB/T+GfftZk82muEErYXjd9WIE9THMCFPYN0Vh+PlVF")
val API_WS_LEFT_SPACE = /*Encrypted: s_leftSpace*/ sp.StringEncrypt.decrypt("+WuJaIy+QeWuq0nmyHrXxCyRf1vtWieganG0KZ81uI2E0WBVcQ9f")
val API_WS_CLIENT_INIT_CALLBACK = /*Encrypted: s_clientInitBack*/ sp.StringEncrypt.decrypt("b2KcERESo3BdC5Sjlj+ye+x3iTuoNEV6Z2QgtIm4SWXKQFQ19eekH/6X0aM=")

val API_WS_INIT = /*Encrypted: c_wsInit*/ sp.StringEncrypt.decrypt("qSsu5EQIKjb86D+hEJ5ynu1MmUI7HWdq+3jMrxKvY+VC4C9+")
val API_WS_PING = /*Encrypted: c_ping*/ sp.StringEncrypt.decrypt("VZb1Tu+UrT2TZ71DczpD1ZAfdcf3wRPAIjnxXGVqo4iW5w==")

//textChat相关api
val API_WS_TEXT_CHAT_MSG = /*Encrypted: cs_text_chat_msg*/ sp.StringEncrypt.decrypt("p8c+qxtSoNE05RY2bjTxsJQ7Z58TNeAYiCFndCtxSyqZ7WHMSQ2OEFLtFtk=")
    }
}
