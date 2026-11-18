package com.patobloxfruits.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patobloxfruits.bot.amplopay.AmploPayClient;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.TransactionResponse;
import com.patobloxfruits.bot.domain.Fruit;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import com.patobloxfruits.bot.service.PaymentWebhookService.Outcome;
import com.patobloxfruits.bot.service.PaymentWebhookService.PaymentNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {

    final ObjectMapper json = new ObjectMapper();
    OrderService orders;
    AmploPayClient amploPay;
    PaymentNotifier notifier;
    PaymentWebhookService service;
    PurchaseOrder order;

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        amploPay = mock(AmploPayClient.class);
        notifier = mock(PaymentNotifier.class);
        service = new PaymentWebhookService(orders, amploPay, notifier);

        order = new PurchaseOrder("PBF-1", new Fruit("Leopard", new BigDecimal("15"), 1), "user1", "PatoGamer");
        ReflectionTestUtils.setField(order, "id", 7L);
        order.setTransactionId("tx-123");
        order.setWebhookToken("segredo-do-pedido");
        when(orders.findByTransactionId("tx-123")).thenReturn(Optional.of(order));
    }

    JsonNode payload(String event, String token) throws Exception {
        return json.readTree("""
                {"event":"%s","token":"%s","transaction":{"id":"tx-123","status":"COMPLETED"}}
                """.formatted(event, token));
    }

    TransactionResponse tx(String status) {
        return new TransactionResponse("tx-123", "PBF-1", new BigDecimal("15"), status, "PIX", null);
    }

    @Test
    void pagamentoValidoLiberaOPedido() throws Exception {
        when(amploPay.getTransaction("tx-123")).thenReturn(tx("COMPLETED"));
        when(orders.markPaid(7L)).thenReturn(PaymentResult.CONFIRMED);

        assertThat(service.handle(payload("TRANSACTION_PAID", "segredo-do-pedido"))).isEqualTo(Outcome.PAID);
        verify(notifier).onPaid(7L, PaymentResult.CONFIRMED);
    }

    @Test
    void tokenErradoNaoLibera() throws Exception {
        assertThat(service.handle(payload("TRANSACTION_PAID", "token-falso"))).isEqualTo(Outcome.INVALID_TOKEN);
        verify(orders, never()).markPaid(any());
        verifyNoInteractions(amploPay, notifier);
    }

    @Test
    void naoConfiaSoNoWebhookConfereNaApi() throws Exception {
        when(amploPay.getTransaction("tx-123")).thenReturn(tx("PENDING"));

        assertThat(service.handle(payload("TRANSACTION_PAID", "segredo-do-pedido"))).isEqualTo(Outcome.NOT_COMPLETED);
        verify(orders, never()).markPaid(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void webhookRepetidoNaoAvisaDuasVezes() throws Exception {
        when(amploPay.getTransaction("tx-123")).thenReturn(tx("COMPLETED"));
        when(orders.markPaid(7L)).thenReturn(PaymentResult.ALREADY_PROCESSED);

        assertThat(service.handle(payload("TRANSACTION_PAID", "segredo-do-pedido"))).isEqualTo(Outcome.ALREADY_PROCESSED);
        verifyNoInteractions(notifier);
    }

    @Test
    void transacaoDesconhecidaEIgnorada() throws Exception {
        JsonNode other = json.readTree("""
                {"event":"TRANSACTION_PAID","token":"x","transaction":{"id":"outra"}}""");
        when(orders.findByTransactionId("outra")).thenReturn(Optional.empty());

        assertThat(service.handle(other)).isEqualTo(Outcome.IGNORED);
    }

    @Test
    void comparacaoDeToken() {
        assertThat(PaymentWebhookService.tokensMatch("abc", "abc")).isTrue();
        assertThat(PaymentWebhookService.tokensMatch("abc", "abd")).isFalse();
        assertThat(PaymentWebhookService.tokensMatch(null, "abc")).isFalse();
        assertThat(PaymentWebhookService.tokensMatch("", "")).isFalse();
    }
}
