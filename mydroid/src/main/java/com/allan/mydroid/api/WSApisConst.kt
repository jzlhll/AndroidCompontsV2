package com.allan.mydroid.api

import sp.EncryptString

@EncryptString
class WSApisConst {
    companion object {
        val API_WS_LEFT_SPACE = /*Encrypted: s_leftSpace*/ sp.StringEncrypt.decrypt("+WuJaIy+QeWuq0nmyHrXxCyRf1vtWieganG0KZ81uI2E0WBVcQ9f")
        val API_WS_CLIENT_INIT_CALLBACK = /*Encrypted: s_clientInitBack*/
            sp.StringEncrypt.decrypt("b2KcERESo3BdC5Sjlj+ye+x3iTuoNEV6Z2QgtIm4SWXKQFQ19eekH/6X0aM=")

        val API_WS_INIT = /*Encrypted: c_wsInit*/ sp.StringEncrypt.decrypt("qSsu5EQIKjb86D+hEJ5ynu1MmUI7HWdq+3jMrxKvY+VC4C9+")
        val API_WS_PING = /*Encrypted: c_ping*/ sp.StringEncrypt.decrypt("VZb1Tu+UrT2TZ71DczpD1ZAfdcf3wRPAIjnxXGVqo4iW5w==")
        val API_WS_TEXT_CHAT_SEND = "c_textChat"
        val API_WS_TEXT_CHAT_CALLBACK = "s_textChat"
    }
}
