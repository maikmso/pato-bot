package com.patobloxfruits.bot.amplopay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Objetos de requisição/resposta da API AmploPay.
 * Documentação: https://app.amplopay.com/docs/v1/endpoint/pix/receive
 */
public final class AmploPayDtos {

    private AmploPayDtos() {
    }

    /** Corpo do POST /gateway/pix/receive */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PixReceiveRequest(
            String identifier,
            BigDecimal amount,
            Client client,
            List<Product> products,
            Map<String, String> metadata,
            String callbackUrl,
            Boolean isStrictWebhookToken
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Client(String name, String email, String phone, String document) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Product(String id, String name, Integer quantity, BigDecimal price, Boolean physical) {
    }

    /** Resposta 200 do POST /gateway/pix/receive */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PixReceiveResponse(
            String transactionId,
            String status,
            String transactionStatus,
            String webhookToken,
            BigDecimal fee,
            Pix pix,
            String errorDescription
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pix(String code, String image, String base64, String expiresAt) {
    }

    /** Resposta do GET /gateway/transactions */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionResponse(
            String id,
            String clientIdentifier,
            BigDecimal amount,
            String status,
            String paymentMethod,
            String payedAt
    ) {
        public boolean isCompleted() {
            return "COMPLETED".equalsIgnoreCase(status);
        }

        public boolean isFailed() {
            return "FAILED".equalsIgnoreCase(status);
        }
    }
}
