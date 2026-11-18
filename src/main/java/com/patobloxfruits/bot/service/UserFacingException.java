package com.patobloxfruits.bot.service;

/**
 * Erro cuja mensagem pode ser mostrada diretamente para o cliente no Discord.
 */
public class UserFacingException extends RuntimeException {

    public UserFacingException(String message) {
        super(message);
    }
}
