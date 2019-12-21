package com.senderman.lastkatkabot.admincommands

import com.senderman.CommandExecutor
import com.senderman.lastkatkabot.DBService
import com.senderman.lastkatkabot.LastkatkaBotHandler
import org.telegram.telegrambots.meta.api.objects.Message

class BadNeko constructor(val handler: LastkatkaBotHandler) : CommandExecutor {

    override val forAllAdmins: Boolean
        get() = true
    override val desc: String
        get() = "(reply) добавить юзера в чс бота"
    override val command: String
        get() = "/badneko"

    override fun execute(message: Message) {
        if (!message.isReply) return

        UserAdder.addUser(handler, message, DBService.UserType.BLACKLIST)
    }
}