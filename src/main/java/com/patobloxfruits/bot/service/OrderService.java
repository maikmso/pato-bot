package com.patobloxfruits.bot.service;

import com.patobloxfruits.bot.domain.Fruit;
import com.patobloxfruits.bot.domain.OrderStatus;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.repository.FruitRepository;
import com.patobloxfruits.bot.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de negócio dos pedidos (só banco de dados, nada de Discord aqui).
 */
@Service
public class OrderService {

    /** Resultado de tentar marcar um pedido como pago. */
    public enum PaymentResult {
        /** Estava pendente e agora está pago. */
        CONFIRMED,
        /** Já tinha sido processado antes (webhook repetido). */
        ALREADY_PROCESSED,
        /** O pedido tinha expirado/cancelado, mas o cliente pagou mesmo assim. Precisa de atenção manual. */
        PAID_AFTER_CANCEL
    }

    private final PurchaseOrderRepository orders;
    private final FruitRepository fruits;

    public OrderService(PurchaseOrderRepository orders, FruitRepository fruits) {
        this.orders = orders;
        this.fruits = fruits;
    }

    /** Reserva a fruta e cria o pedido pendente. */
    @Transactional
    public PurchaseOrder reserve(Long fruitId, String discordUserId, String robloxNick) {
        if (orders.existsByDiscordUserIdAndStatus(discordUserId, OrderStatus.PENDING)) {
            throw new UserFacingException("Você já tem um pedido aguardando pagamento. Pague ou cancele ele antes de abrir outro.");
        }
        Fruit fruit = fruits.findByIdForUpdate(fruitId)
                .orElseThrow(() -> new UserFacingException("Essa fruta não existe mais."));
        if (fruit.getStock() <= 0) {
            throw new UserFacingException("Acabou o estoque de **" + fruit.getName() + "** 😢");
        }
        fruit.reserveOne();
        String identifier = "PBF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return orders.save(new PurchaseOrder(identifier, fruit, discordUserId, robloxNick.trim()));
    }

    @Transactional
    public void attachTicket(Long orderId, String channelId) {
        load(orderId).setTicketChannelId(channelId);
    }

    @Transactional
    public void attachPayment(Long orderId, String transactionId, String webhookToken) {
        PurchaseOrder order = load(orderId);
        order.setTransactionId(transactionId);
        order.setWebhookToken(webhookToken);
    }

    /**
     * Cancela um pedido pendente e devolve a fruta ao estoque.
     *
     * @return true se cancelou agora; false se já não estava pendente.
     */
    @Transactional
    public boolean cancel(Long orderId) {
        PurchaseOrder order = load(orderId);
        if (!order.isPending()) {
            return false;
        }
        order.markCanceled();
        fruits.findByIdForUpdate(order.getFruit().getId()).ifPresent(Fruit::releaseOne);
        return true;
    }

    @Transactional
    public PaymentResult markPaid(Long orderId) {
        PurchaseOrder order = load(orderId);
        return switch (order.getStatus()) {
            case PENDING -> {
                order.markPaid();
                yield PaymentResult.CONFIRMED;
            }
            case CANCELED -> {
                // Pagou depois de expirar: a fruta já voltou pro estoque. Marcamos como pago
                // e tentamos reservar de novo; se não tiver estoque, a staff resolve (reembolso).
                fruits.findByIdForUpdate(order.getFruit().getId())
                        .filter(f -> f.getStock() > 0)
                        .ifPresent(Fruit::reserveOne);
                order.markPaid();
                yield PaymentResult.PAID_AFTER_CANCEL;
            }
            case PAID, DELIVERED -> PaymentResult.ALREADY_PROCESSED;
        };
    }

    @Transactional
    public boolean markDelivered(Long orderId) {
        PurchaseOrder order = load(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            return false;
        }
        order.markDelivered();
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findByTransactionId(String transactionId) {
        return orders.findByTransactionId(transactionId);
    }

    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findByTicketChannel(String channelId) {
        return orders.findByTicketChannelId(channelId);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long orderId) {
        return load(orderId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findExpired(Duration maxAge) {
        return orders.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, Instant.now().minus(maxAge));
    }

    private PurchaseOrder load(Long orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + orderId));
    }
}
