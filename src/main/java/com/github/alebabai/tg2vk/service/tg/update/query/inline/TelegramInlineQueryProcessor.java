package com.github.alebabai.tg2vk.service.tg.update.query.inline;

import com.pengrad.telegrambot.model.InlineQuery;

@FunctionalInterface
public interface TelegramInlineQueryProcessor {
    void process(InlineQuery query);
}
