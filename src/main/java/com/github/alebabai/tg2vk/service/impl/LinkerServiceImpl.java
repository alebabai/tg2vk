package com.github.alebabai.tg2vk.service.impl;

import com.github.alebabai.tg2vk.domain.ChatSettings;
import com.github.alebabai.tg2vk.domain.User;
import com.github.alebabai.tg2vk.service.*;
import com.github.alebabai.tg2vk.util.constants.EnvConstants;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.objects.messages.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.springframework.beans.propertyeditors.ResourceBundleEditor.BASE_NAME_SEPARATOR;

@Service
public class LinkerServiceImpl implements LinkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkerServiceImpl.class);
    private static final String PRIVATE_MESSAGE_TEMPLATE = "telegram/private_message.html";
    private static final String GROUP_MESSAGE_TEMPLATE = "telegram/group_message.html";

    private final Environment env;
    private final UserService userService;
    private final TelegramService tgService;
    private final VkService vkService;
    private final TemplateRendererService templateRenderer;
    private final Map<Integer, AtomicBoolean> daemonStates;

    @Autowired
    public LinkerServiceImpl(Environment env,
                             UserService userService,
                             VkService vkService,
                             TelegramService tgService,
                             TemplateRendererService templateRenderer) {
        this.env = env;
        this.userService = userService;
        this.vkService = vkService;
        this.tgService = tgService;
        this.templateRenderer = templateRenderer;
        this.daemonStates = new HashMap<>();
    }

    @PostConstruct
    protected void init() {
        if (env.getProperty(EnvConstants.PROP_VK_AUTO_INIT_POOL, Boolean.TYPE, true)) {
            userService.findAllStarted().forEach(this::start);
        }
    }

    @Transactional
    @Override
    public void start(User user) {
        final boolean isStarted = daemonStates.keySet()
                .stream()
                .anyMatch(id -> Objects.equals(id, user.getId()));
        if (!isStarted) {
            LOGGER.debug("Start messages linking for {}", user);
            final UserActor actor = new UserActor(user.getVkId(), user.getVkToken());
            final AtomicBoolean isDaemonActive = vkService.fetchMessages(actor, getVkMessageHandler(user));
            daemonStates.put(user.getId(), isDaemonActive);
            user.getSettings().started(isDaemonActive.get());
            userService.updateUserSettings(user.getSettings());
        }
    }

    @Transactional
    @Override
    public void stop(User user) {
        Optional.ofNullable(daemonStates.get(user.getId()))
                .ifPresent(daemonState -> {
                    final boolean state = false;
                    daemonState.lazySet(state);
                    daemonStates.remove(user.getId());
                    user.getSettings().started(state);
                    userService.updateUserSettings(user.getSettings());
                    LOGGER.debug("Stop messages linking for {}", user);
                });
    }

    private BiConsumer<? super com.vk.api.sdk.objects.users.User, ? super Message> getVkMessageHandler(User user) {
        return (profile, message) -> {
            try {
                final Integer vkChatId = getVkChatId(message);
                Optional.of(userService
                        .findChatSettings(user, vkChatId)
                        .orElse(new ChatSettings()
                                .setTgChatId(user.getTgId())
                                .setVkChatId(vkChatId)
                                .started(true)))
                        .filter(ChatSettings::isStarted)
                        .map(ChatSettings::getTgChatId)
                        .ifPresent(getChatTypeHandler(message, profile));
            } catch (Exception e) {
                LOGGER.error("Error during vk message handling: ", e);
            }
        };
    }

    private Integer getVkChatId(Message message) {
        return Optional.ofNullable(message.getChatId()).orElse(message.getUserId());
    }

    private Consumer<Integer> getChatTypeHandler(Message message, com.vk.api.sdk.objects.users.User profile) {
        return tgChatId -> {
            if (Objects.isNull(message.getChatId())) {
                handleMessage(tgChatId, PRIVATE_MESSAGE_TEMPLATE, createPrivateMessageContext(profile, message));
            } else {
                handleMessage(tgChatId, GROUP_MESSAGE_TEMPLATE, createGroupMessageContext(profile, message));
            }
        };
    }

    private void handleMessage(Object tgChatId, String templateName, Map<String, Object> context) {
        final SendMessage sendMessage = new SendMessage(tgChatId, templateRenderer.render(templateName, context))
                .parseMode(ParseMode.HTML);
        tgService.send(sendMessage);
    }

    private static Map<String, Object> createPrivateMessageContext(com.vk.api.sdk.objects.users.User profile, Message message) {
        final Map<String, Object> context = new HashMap<>();
        context.put("user", String.join(StringUtils.SPACE, profile.getFirstName(), profile.getLastName()));
        context.put("status", profile.isOnline() ? "online" : "offline");
        context.put("body", message.getBody());
        return context;
    }

    private static Map<String, Object> createGroupMessageContext(com.vk.api.sdk.objects.users.User profile, Message message) {
        final Map<String, Object> context = createPrivateMessageContext(profile, message);
        context.put("chat", createValidHashTag(message.getTitle()));
        context.put("online_count", message.getChatActive().size());
        return context;
    }

    private static String createValidHashTag(String title) {
        return StringUtils.replacePattern(title, "[^\\p{L}\\d]+", BASE_NAME_SEPARATOR);
    }
}
