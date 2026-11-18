package com.patobloxfruits.bot.amplopay;

import com.patobloxfruits.bot.amplopay.AmploPayDtos.PixReceiveRequest;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.PixReceiveResponse;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.TransactionResponse;
import com.patobloxfruits.bot.config.AmploPayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Cliente HTTP da AmploPay. Autenticação pelos headers x-public-key e x-secret-key.
 */
@Component
public class AmploPayClient {

    private static final Logger log = LoggerFactory.getLogger(AmploPayClient.class);

    private final RestClient http;

    public AmploPayClient(AmploPayProperties props, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.http = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("x-public-key", props.publicKey())
                .defaultHeader("x-secret-key", props.secretKey())
                .build();
    }

    /** Cria a cobrança PIX e devolve o código copia-e-cola. */
    public PixReceiveResponse createPix(PixReceiveRequest request) {
        try {
            PixReceiveResponse response = http.post()
                    .uri("/gateway/pix/receive")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PixReceiveResponse.class);

            if (response == null || response.pix() == null || response.pix().code() == null) {
                String reason = response != null ? response.errorDescription() : "resposta vazia";
                throw new AmploPayException("AmploPay não gerou o PIX: " + reason);
            }
            if ("FAILED".equalsIgnoreCase(response.status())) {
                throw new AmploPayException("AmploPay recusou a cobrança: " + response.errorDescription());
            }
            return response;
        } catch (RestClientResponseException e) {
            // Não loga headers (têm a chave secreta), só o corpo do erro.
            log.error("Erro AmploPay ao criar PIX ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AmploPayException("Erro ao gerar PIX na AmploPay (" + e.getStatusCode().value() + ")", e);
        }
    }

    /** Consulta o status real de uma transação (usado para confirmar o webhook). */
    public TransactionResponse getTransaction(String transactionId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/gateway/transactions").queryParam("id", transactionId).build())
                    .retrieve()
                    .body(TransactionResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Erro AmploPay ao consultar transação {} ({}): {}",
                    transactionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AmploPayException("Erro ao consultar transação na AmploPay", e);
        }
    }
}
