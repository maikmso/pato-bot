package com.patobloxfruits.bot.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Um pedido de compra. ("Order" é palavra reservada em SQL, por isso PurchaseOrder.)
 */
@Entity
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_order_transaction", columnList = "transactionId"),
        @Index(name = "idx_order_status", columnList = "status")
})
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador único que NÓS enviamos para a AmploPay (campo "identifier"). */
    @Column(nullable = false, unique = true, length = 40)
    private String identifier;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Fruit fruit;

    /** Nome e preço "congelados" no momento da compra. */
    @Column(nullable = false, length = 60)
    private String fruitName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String discordUserId;

    @Column(length = 30)
    private String ticketChannelId;

    @Column(nullable = false, length = 40)
    private String robloxNick;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /** ID da transação retornado pela AmploPay. */
    @Column(length = 80)
    private String transactionId;

    /** Token exclusivo do pedido para validar o webhook (isStrictWebhookToken). */
    @Column(length = 120)
    private String webhookToken;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;
    private Instant deliveredAt;
    private Instant canceledAt;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String identifier, Fruit fruit, String discordUserId, String robloxNick) {
        this.identifier = identifier;
        this.fruit = fruit;
        this.fruitName = fruit.getName();
        this.amount = fruit.getPrice();
        this.discordUserId = discordUserId;
        this.robloxNick = robloxNick;
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void markCanceled() {
        this.status = OrderStatus.CANCELED;
        this.canceledAt = Instant.now();
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Fruit getFruit() {
        return fruit;
    }

    public String getFruitName() {
        return fruitName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public String getTicketChannelId() {
        return ticketChannelId;
    }

    public void setTicketChannelId(String ticketChannelId) {
        this.ticketChannelId = ticketChannelId;
    }

    public String getRobloxNick() {
        return robloxNick;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
