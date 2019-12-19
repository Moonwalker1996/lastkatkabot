package com.senderman.lastkatkabot.handlers

import com.annimon.tgbotsmodule.api.methods.Methods
import com.senderman.TgUser
import com.senderman.lastkatkabot.LastkatkaBot
import com.senderman.lastkatkabot.LastkatkaBotHandler
import com.senderman.lastkatkabot.Services
import com.senderman.lastkatkabot.tempobjects.BnCPlayer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.telegram.telegrambots.meta.api.objects.ChatMember
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.logging.BotLogger
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class UsercommandsHandler(private val handler: LastkatkaBotHandler) {

    fun marryme(message: Message) {
        val marryById = message.text.trim().matches(Regex("/marryme\\s+\\d+"))
        val chatId = message.chatId
        val userId = message.from.id
        val text: String
        val loverId: Int
        if (Services.db.getLover(userId) != 0) {
            handler.sendMessage(chatId, "Всмысле? Вы что, хотите изменить своей второй половинке?!")
            return
        }

        if (!marryById) {
            if (!message.isReply
                    || message.from.id == message.replyToMessage.from.id || message.replyToMessage.from.bot) return
            loverId = message.replyToMessage.from.id
            val user = TgUser(Methods.getChatMember(chatId, userId).call(handler).user)
            text = "Пользователь " + user.link + " предлагает вам руку, сердце и шавуху. Вы согласны?"

        } else {
            if (message.isUserMessage) return
            loverId = try {
                message.text.split(" ")[1].toInt()
            } catch (e: NumberFormatException) {
                handler.sendMessage(chatId, "Неверный формат!")
                return
            }
            val user = TgUser(Methods.getChatMember(chatId, userId).call(handler).user)
            val lover = TgUser(Methods.getChatMember(chatId, loverId).call(handler).user)
            text = "${lover.link}, пользователь ${user.link} предлагает вам руку, сердце и шавуху. Вы согласны?"
        }
        if (Services.db.getLover(loverId) != 0) {
            handler.sendMessage(chatId, "У этого пользователя уже есть своя вторая половинка!")
            return
        }
        val markup = InlineKeyboardMarkup()
        markup.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    this.text = "Принять"
                    callbackData = LastkatkaBot.CALLBACK_ACCEPT_MARRIAGE + "$userId $loverId"

                },
                InlineKeyboardButton().apply {
                    this.text = "Отказаться"
                    callbackData = LastkatkaBot.CALLBACK_DENY_MARRIAGE + "$userId $loverId"
                }
        ))
        val sm = Methods.sendMessage()
                .setChatId(chatId)
                .setText(text)
                .setReplyMarkup(markup)
        if (!marryById) {
            sm.replyToMessageId = message.replyToMessage.messageId
        }
        handler.sendMessage(sm)
    }

    fun divorce(message: Message) {
        val chatId = message.chatId
        val userId = message.from.id
        val loverId = Services.db.getLover(userId)
        if (loverId == 0) {
            handler.sendMessage(chatId, "У вас и так никого нет!")
            return
        }
        Services.db.divorce(userId)
        handler.sendMessage(chatId, "Вы расстались со своей половинкой! А ведь так все хорошо начиналось...")
        val user = TgUser(Methods.getChatMember(userId.toLong(), userId).call(handler).user)
        handler.sendMessage(loverId, "Ваша половинка (${user.link}) покинула вас... Теперь вы одни...")
    }

    fun stats(message: Message) {
        val player = if (!message.isReply) message.from else message.replyToMessage.from
        if (player.bot) {
            handler.sendMessage(message.chatId, "Но это же просто бот, имитация человека! " +
                    "Разве может бот написать симфонию, иметь статистику, играть в BnC, любить?")
            return
        }
        val user = TgUser(player)
        val stats = Services.db.getStats(player.id)
        val (_, duelWins, totalDuels, bnc, loverId) = stats
        val winRate = if (totalDuels == 0) 0 else 100 * duelWins / totalDuels
        var text = """
            📊 Статистика ${user.name}:

            Дуэлей выиграно: $duelWins
            Всего дуэлей: $totalDuels
            Винрейт: $winRate%
            
            🐮 Баллов за быки и коровы: $bnc
        """.trimIndent()
        if (loverId != 0) {
            val lover = TgUser(Methods.getChatMember(loverId.toLong(), loverId).call(handler).user)
            text += "\n❤️ Вторая половинка: "
            text += if (message.isUserMessage) lover.link else lover.name
        }
        handler.sendMessage(message.chatId, text)
    }

    fun pinList(message: Message) {
        if (!isFromWwBot(message)) return
        Methods.Administration.pinChatMessage(message.chatId, message.replyToMessage.messageId)
                .setNotificationEnabled(false).call(handler)
        Methods.deleteMessage(message.chatId, message.messageId).call(handler)
    }

    fun weather(message: Message) {
        val chatId = message.chatId
        var city: String? = message.text.trim().replace("/weather[_\\d\\w@]*\\s*".toRegex(), "")
        if (city!!.isBlank()) { // city is not specified
            city = Services.db.getUserCity(message.from.id)
            if (city == null) {
                handler.sendMessage(chatId, "Вы не указали город!")
                return
            }
        } else { // find a city
            try {
                val searchPage = Jsoup.parse(URL("https://yandex.ru/pogoda/search?request=" + URLEncoder.encode(city, StandardCharsets.UTF_8)), 10000)
                val table = searchPage.selectFirst("div.grid")
                val searchResult = table.selectFirst("li.place-list__item")
                city = searchResult.selectFirst("a").attr("href")
            } catch (e: NullPointerException) {
                handler.sendMessage(chatId, "Город не найден")
                return
            } catch (e: IOException) {
                handler.sendMessage(chatId, "Ошибка запроса")
            }
        }
        Services.db.setUserCity(message.from.id, city!!)
        val weatherPage: Document
        weatherPage = try {
            Jsoup.parse(URL("https://yandex.ru$city"), 10000)
        } catch (e: IOException) {
            handler.sendMessage(chatId, "Ошибка запроса")
            return
        }
        // parse weather
        val table = weatherPage.selectFirst("div.card_size_big")
        val title = weatherPage.selectFirst("h1.header-title__title").text()
        val temperature = table.selectFirst("div.fact__temp span.temp__value").text()
        val feelsLike = table.selectFirst("div.fact__feels-like div.term__value").text()
        val feelings = table.selectFirst("div.fact__feelings div.link__condition").text()
        val wind = table.selectFirst("div.fact__wind-speed div.term__value").text()
        val humidity = table.selectFirst("div.fact__humidity div.term__value").text()
        val pressure = table.selectFirst("div.fact__pressure div.term__value").text()
        val forecast = """
            <b>$title</b>
            
            $feelings
            🌡: $temperature °C
            🤔 Ощущается как $feelsLike
            💨: $wind
            💧: $humidity
            🧭: $pressure
            """.trimIndent()

        handler.sendMessage(chatId, forecast)
    }

    fun feedback(message: Message) {
        val report = message
                .text.trim()
                .replace("/feedback(:?@${handler.botUsername})?\\s*".toRegex(), "")
        if (report.isBlank() && !message.isReply) return

        val user = TgUser(message.from)
        val markup = InlineKeyboardMarkup()
        markup.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "Ответить"
                    callbackData = "${LastkatkaBot.CALLBACK_ANSWER_FEEDBACK}${message.chatId} ${message.messageId}"
                },
                InlineKeyboardButton().apply {
                    text = "Заблокировать"
                    callbackData = "${LastkatkaBot.CALLBACK_BLOCK_USER} ${message.from.id}"
                }
        ))

        val bugreport = ("⚠️ <b>Фидбек</b>\n\n" +
                "От: ${user.link}\n\n$report")
        handler.sendMessage(Methods.sendMessage()
                .setChatId(Services.botConfig.mainAdmin.toLong())
                .setText(bugreport)
                .setReplyMarkup(markup))
        if (message.isReply) {
            Methods.forwardMessage(
                    Services.botConfig.mainAdmin.toLong(),
                    message.replyToMessage.chatId,
                    message.replyToMessage.messageId
            ).call(handler)
        }

        handler.sendMessage(Methods.sendMessage()
                .setChatId(message.chatId)
                .setText("✅ Отправлено разрабу бота!")
                .setReplyToMessageId(message.messageId))
    }

    fun bncTop(message: Message) {
        val chatId = message.chatId
        val top = Services.db.getTop()
        val text = StringBuilder("<b>Топ-10 задротов в bnc:</b>\n\n")
        var counter = 1
        for ((playerId, score) in top) {
            val member = Methods.getChatMember(playerId.toLong(), playerId).call(handler)
            val player = BnCPlayer(playerId, member.user.firstName, score)
            text.append(counter).append(": ")
            if (message.isUserMessage) text.append(player.link) else text.append(player.name)
            text.append(" (${player.score})\n")
            counter++
        }
        handler.sendMessage(chatId, text.toString())
    }

    fun pair(message: Message) {
        if (message.isUserMessage) return

        val chatId = message.chatId
        // check for existing pair
        if (Services.db.pairExistsToday(chatId)) {
            var pair = Services.db.getPairOfTheDay(chatId)
            pair = "Пара дня: $pair"
            handler.sendMessage(chatId, pair)
            return
        }
        // remove users without activity for 2 weeks and get list of actual users
        Services.db.removeOldUsers(chatId, message.date - 1209600)
        val userIds = Services.db.getChatMemebersIds(chatId)
        // generate 2 different random users
        val user1: TgUser
        val user2: TgUser
        val isTrueLove: Boolean
        try {
            user1 = getUserForPair(chatId, userIds)
            userIds.remove(user1.id)
            val lover = getSecondUserForPair(chatId, userIds, user1)
            user2 = lover.user
            isTrueLove = lover.isTrueLover
        } catch (e: Exception) {
            handler.sendMessage(chatId, "Недостаточно пользователей для создания пары! Подождите, пока кто-то еще напишет в чат!")
            return
        }
        // get a random text and set up a pair
        val loveArray = Services.botConfig.loveStrings
        val loveStrings = loveArray.random().trim().split("\n")
        try {
            for (i in 0 until loveStrings.lastIndex) {
                handler.sendMessage(chatId, loveStrings[i])
                Thread.sleep(1500)
            }
        } catch (e: InterruptedException) {
            BotLogger.error("PAIR", "Ошибка таймера")
        }
        val pair = if (isTrueLove) "${user1.name} \uD83D\uDC96 ${user2.name}" else "${user1.name} ❤ ${user2.name}"
        Services.db.setPair(chatId, pair)
        handler.sendMessage(chatId, java.lang.String.format(loveStrings.last(), user1.link, user2.link))
    }

    data class Lover(val user: TgUser, val isTrueLover: Boolean)

    @Throws(Exception::class)
    private fun getSecondUserForPair(chatId: Long, userIds: MutableList<Int>, first: TgUser): Lover {
        val loverId = Services.db.getLover(first.id)
        return if (loverId in userIds) {
            Lover(TgUser(Methods.getChatMember(chatId, loverId).call(handler).user), true)
        } else Lover(getUserForPair(chatId, userIds), false)
    }

    @Throws(Exception::class)
    private fun getUserForPair(chatId: Long, userIds: MutableList<Int>): TgUser {
        var member: ChatMember?
        while (userIds.size > 2) {
            val userId = userIds.random()
            member = Methods.getChatMember(chatId, userId).call(handler)
            if (member != null)
                return TgUser(member.user)
            Services.db.removeUserFromChatDB(userId, chatId)
            userIds.remove(userId)
        }
        throw Exception("Not enough users")
    }

    fun lastpairs(message: Message) {
        if (message.isUserMessage) return
        val chatId = message.chatId
        val history = Services.db.getPairsHistory(chatId)
        handler.sendMessage(chatId,
                history?.let {
                    "<b>Последние 10 пар:</b>\n\n$it"
                } ?: "В этом чате еще никогда не запускали команду /pair!"
        )
    }

    private fun isFromWwBot(message: Message): Boolean {
        return message.replyToMessage.from.userName in Services.botConfig.wwBots &&
                message.replyToMessage.text.startsWith("#players")
    }

}