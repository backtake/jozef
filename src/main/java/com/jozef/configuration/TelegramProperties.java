package com.jozef.configuration;

import java.util.List;

/**
 * Phone access through a private Telegram bot.
 *
 * <pre>
 * jozef:
 *   telegram:
 *     enabled: true
 *     token-env: TELEGRAM_BOT_TOKEN     # env var with the bot token from @BotFather (never inline)
 *     allowed-chat-ids: [123456789]     # only these chats may command Jozef
 * </pre>
 */
public record TelegramProperties(
        boolean enabled,
        String tokenEnv,
        List<Long> allowedChatIds
) {
    public TelegramProperties {
        if (tokenEnv == null || tokenEnv.isBlank()) tokenEnv = "TELEGRAM_BOT_TOKEN";
        allowedChatIds = allowedChatIds == null ? List.of() : List.copyOf(allowedChatIds);
    }
}
