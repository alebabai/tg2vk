package com.github.alebabai.tg2vk.service.tg.update.command.handler.impl;

import com.github.alebabai.tg2vk.service.tg.common.TelegramService;
import com.github.alebabai.tg2vk.service.tg.update.command.TelegramCommand;
import com.github.alebabai.tg2vk.service.tg.update.command.handler.TelegramCommandHandler;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;

@Service("unknownCommandHandler")
public class TelegramUnknownCommandHandler implements TelegramCommandHandler {

    private final TelegramService tgService;
    private final MessageSourceAccessor messages;

    public TelegramUnknownCommandHandler(TelegramService tgService, MessageSourceAccessor messages) {
        this.tgService = tgService;
        this.messages = messages;
    }

    @Override
    public void handle(TelegramCommand command) {
        SendMessage anyMessage = new SendMessage(command.context().chat().id(), messages.getMessage("tg.command.unknown.msg"));
        tgService.send(anyMessage);
    }
}
