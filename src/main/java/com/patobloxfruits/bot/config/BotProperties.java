package com.patobloxfruits.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações do Discord, lidas do application.yml / variáveis de ambiente.
 */
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        String token,
        String guildId,
        String ownerId,
        String deliveredChannelId,
        String reviewsChannelId,
        String logChannelId,
        String ticketCategoryName,
        int orderExpirationMinutes
) {
    public boolean hasOwner() {
        return ownerId != null && !ownerId.isBlank();
    }
}
