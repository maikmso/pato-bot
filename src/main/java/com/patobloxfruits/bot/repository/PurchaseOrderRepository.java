package com.patobloxfruits.bot.repository;

import com.patobloxfruits.bot.domain.OrderStatus;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByTransactionId(String transactionId);

    Optional<PurchaseOrder> findByTicketChannelId(String ticketChannelId);

    boolean existsByDiscordUserIdAndStatus(String discordUserId, OrderStatus status);

    List<PurchaseOrder> findByStatusAndCreatedAtBefore(OrderStatus status, Instant before);
}
