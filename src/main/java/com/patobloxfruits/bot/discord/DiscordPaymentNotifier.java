package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import com.patobloxfruits.bot.service.PaymentWebhookService.PaymentNotifier;
import org.springframework.stereotype.Component;

/**
 * Liga o processamento do webhook às mensagens no Discord.
 */
@Component
public class DiscordPaymentNotifier implements PaymentNotifier {

    private final ShopService shop;

    public DiscordPaymentNotifier(ShopService shop) {
        this.shop = shop;
    }

    @Override
    public void onPaid(Long orderId, PaymentResult result) {
        shop.onPaid(orderId, result);
    }

    @Override
    public void onCanceled(Long orderId) {
        shop.expire(orderId);
    }
}
