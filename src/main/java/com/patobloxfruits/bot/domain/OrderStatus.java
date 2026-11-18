package com.patobloxfruits.bot.domain;

public enum OrderStatus {
    /** PIX gerado, aguardando pagamento. A fruta fica reservada. */
    PENDING,
    /** Pagamento confirmado pela AmploPay. Falta você fazer o trade. */
    PAID,
    /** Trade feito, pedido concluído. */
    DELIVERED,
    /** Cancelado pelo cliente/staff ou expirado. A fruta volta ao estoque. */
    CANCELED
}
