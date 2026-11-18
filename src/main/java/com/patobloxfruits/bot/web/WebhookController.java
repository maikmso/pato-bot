package com.patobloxfruits.bot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.patobloxfruits.bot.service.PaymentWebhookService;
import com.patobloxfruits.bot.service.PaymentWebhookService.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentWebhookService webhooks;

    public WebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /**
     * URL que a AmploPay chama quando o status de um PIX muda.
     * Respondemos 2xx quando processamos (ou ignoramos de propósito); 5xx faz a AmploPay reenviar.
     */
    @PostMapping("/webhooks/amplopay")
    public ResponseEntity<Map<String, String>> amploPay(@RequestBody JsonNode payload) {
        try {
            Outcome outcome = webhooks.handle(payload);
            if (outcome == Outcome.INVALID_TOKEN) {
                return ResponseEntity.status(401).body(Map.of("result", outcome.name()));
            }
            return ResponseEntity.ok(Map.of("result", outcome.name()));
        } catch (RuntimeException e) {
            log.error("Erro ao processar webhook da AmploPay", e);
            return ResponseEntity.internalServerError().body(Map.of("result", "ERROR"));
        }
    }

    /** Para checar se o bot está no ar (e para serviços de "manter acordado"). */
    @GetMapping({"/", "/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
