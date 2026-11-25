package com.pedidos360.pago.service;

// Intento bloqueado por la regla antifraude (el controlador responde 403)
public class FraudeException extends RuntimeException {

    public FraudeException(String mensaje) {
        super(mensaje);
    }
}
