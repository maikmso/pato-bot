package com.patobloxfruits.bot.service;

import com.patobloxfruits.bot.domain.Fruit;
import com.patobloxfruits.bot.domain.OrderStatus;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.repository.FruitRepository;
import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(OrderService.class)
class OrderServiceTest {

    @Autowired
    OrderService orders;

    @Autowired
    FruitRepository fruits;

    Fruit leopard;

    @BeforeEach
    void setUp() {
        leopard = fruits.save(new Fruit("Leopard", new BigDecimal("15.00"), 1));
    }

    @Test
    void reservaTiraDoEstoqueECongelaOPreco() {
        PurchaseOrder order = orders.reserve(leopard.getId(), "user1", "PatoGamer");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getAmount()).isEqualByComparingTo("15.00");
        assertThat(order.getIdentifier()).startsWith("PBF-");
        assertThat(fruits.findById(leopard.getId()).orElseThrow().getStock()).isZero();
    }

    @Test
    void naoVendeSemEstoque() {
        orders.reserve(leopard.getId(), "user1", "PatoGamer");

        assertThatThrownBy(() -> orders.reserve(leopard.getId(), "user2", "Outro"))
                .isInstanceOf(UserFacingException.class)
                .hasMessageContaining("estoque");
    }

    @Test
    void naoDeixaDoisPedidosPendentesDoMesmoCliente() {
        Fruit dragon = fruits.save(new Fruit("Dragon", new BigDecimal("25.00"), 5));
        orders.reserve(dragon.getId(), "user1", "PatoGamer");

        assertThatThrownBy(() -> orders.reserve(leopard.getId(), "user1", "PatoGamer"))
                .isInstanceOf(UserFacingException.class);
    }

    @Test
    void cancelarDevolveAoEstoqueSoUmaVez() {
        PurchaseOrder order = orders.reserve(leopard.getId(), "user1", "PatoGamer");

        assertThat(orders.cancel(order.getId())).isTrue();
        assertThat(orders.cancel(order.getId())).isFalse();
        assertThat(fruits.findById(leopard.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void pagamentoEIdempotente() {
        PurchaseOrder order = orders.reserve(leopard.getId(), "user1", "PatoGamer");

        assertThat(orders.markPaid(order.getId())).isEqualTo(PaymentResult.CONFIRMED);
        assertThat(orders.markPaid(order.getId())).isEqualTo(PaymentResult.ALREADY_PROCESSED);
        assertThat(orders.markDelivered(order.getId())).isTrue();
        assertThat(orders.markDelivered(order.getId())).isFalse();
    }

    @Test
    void pagoDepoisDeExpirarReservaDeNovoSeTiverEstoque() {
        PurchaseOrder order = orders.reserve(leopard.getId(), "user1", "PatoGamer");
        orders.cancel(order.getId());

        assertThat(orders.markPaid(order.getId())).isEqualTo(PaymentResult.PAID_AFTER_CANCEL);
        assertThat(orders.get(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(fruits.findById(leopard.getId()).orElseThrow().getStock()).isZero();
    }
}
