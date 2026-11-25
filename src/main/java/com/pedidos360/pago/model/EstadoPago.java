package com.pedidos360.pago.model;

public enum EstadoPago {
    // Pago iniciado en Webpay, a la espera de que el cliente vuelva del formulario del banco
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    BLOQUEADO_FRAUDE
}
