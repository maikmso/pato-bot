package com.patobloxfruits.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e URLs da AmploPay.
 */
@ConfigurationProperties(prefix = "amplopay")
public record AmploPayProperties(
        String baseUrl,
        String publicKey,
        String secretKey,
        String callbackUrl
) {
}
