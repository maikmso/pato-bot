package com.patobloxfruits.bot.service;

import com.patobloxfruits.bot.amplopay.AmploPayClient;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.TransactionResponse;
import com.patobloxfruits.bot.discord.ShopService;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto, cancela pedidos que passaram do prazo sem pagamento e devolve a fruta ao estoque.
 * Antes de cancelar, confere na AmploPay se o PIX não foi pago (caso o webhook tenha se perdido).
 */
@Component
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderService orders;
    private final ShopService shop;
    private final AmploPayClient amploPay;

    public OrderExpirationJob(OrderService orders, ShopService shop, AmploPayClient amploPay) {
        this.orders = orders;
        this.shop = shop;
        this.amploPay = amploPay;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        for (PurchaseOrder order : orders.findExpired(shop.orderExpiration())) {
            try {
                if (order.getTransactionId() != null) {
                    TransactionResponse tx = amploPay.getTransaction(order.getTransactionId());
                    if (tx != null && tx.isCompleted()) {
                        PaymentResult result = orders.markPaid(order.getId());
                        if (result != PaymentResult.ALREADY_PROCESSED) {
                            shop.onPaid(order.getId(), result);
                        }
                        continue;
                    }
                }
                shop.expire(order.getId());
            } catch (RuntimeException e) {
                log.error("Erro ao expirar pedido {}", order.getIdentifier(), e);
            }
        }
    }
}
