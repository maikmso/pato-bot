package com.patobloxfruits.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.patobloxfruits.bot.amplopay.AmploPayClient;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.TransactionResponse;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Processa as notificações (webhooks) da AmploPay.
 *
 * Segurança em duas camadas:
 * 1. o campo "token" precisa ser igual ao webhookToken exclusivo do pedido;
 * 2. antes de liberar, consultamos a transação na API para confirmar que está COMPLETED.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    /** Avisa o Discord. Interface separada para facilitar o teste. */
    public interface PaymentNotifier {
        void onPaid(Long orderId, PaymentResult result);

        /** Cancela o pedido pendente, devolve o estoque e avisa no ticket. */
        void onCanceled(Long orderId);
    }

    public enum Outcome { IGNORED, INVALID_TOKEN, PAID, ALREADY_PROCESSED, NOT_COMPLETED, CANCELED }

    private final OrderService orders;
    private final AmploPayClient amploPay;
    private final PaymentNotifier notifier;

    public PaymentWebhookService(OrderService orders, AmploPayClient amploPay, PaymentNotifier notifier) {
        this.orders = orders;
        this.amploPay = amploPay;
        this.notifier = notifier;
    }

    public Outcome handle(JsonNode payload) {
        String event = payload.path("event").asText("");
        String token = payload.path("token").asText("");
        String transactionId = payload.path("transaction").path("id").asText("");

        if (transactionId.isEmpty()) {
            log.warn("Webhook sem transaction.id (evento {})", event);
            return Outcome.IGNORED;
        }

        Optional<PurchaseOrder> found = orders.findByTransactionId(transactionId);
        if (found.isEmpty()) {
            log.info("Webhook {} de transação desconhecida {} (ignorado)", event, transactionId);
            return Outcome.IGNORED;
        }
        PurchaseOrder order = found.get();

        if (!tokensMatch(order.getWebhookToken(), token)) {
            log.warn("Webhook com token inválido para o pedido {}", order.getIdentifier());
            return Outcome.INVALID_TOKEN;
        }

        switch (event) {
            case "TRANSACTION_PAID" -> {
                // Confirma direto na API: nunca liberamos só com base no que chegou no webhook.
                TransactionResponse tx = amploPay.getTransaction(transactionId);
                if (tx == null || !tx.isCompleted()) {
                    log.warn("Webhook PAID mas a API diz status {} para {}", tx == null ? null : tx.status(), transactionId);
                    return Outcome.NOT_COMPLETED;
                }
                PaymentResult result = orders.markPaid(order.getId());
                if (result == PaymentResult.ALREADY_PROCESSED) {
                    return Outcome.ALREADY_PROCESSED;
                }
                log.info("Pedido {} pago", order.getIdentifier());
                notifier.onPaid(order.getId(), result);
                return Outcome.PAID;
            }
            case "TRANSACTION_CANCELED" -> {
                // O notifier cancela o pedido (devolve o estoque) e fecha o ticket.
                if (order.isPending()) {
                    notifier.onCanceled(order.getId());
                }
                return Outcome.CANCELED;
            }
            default -> {
                log.info("Evento {} do pedido {} (sem ação)", event, order.getIdentifier());
                return Outcome.IGNORED;
            }
        }
    }

    /** Comparação em tempo constante, para não vazar o token por timing. */
    static boolean tokensMatch(String expected, String received) {
        if (expected == null || expected.isEmpty() || received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
