package com.pedidos360.pago.dto;

import com.pedidos360.pago.model.Pago;

import java.time.Instant;

// Evento del tópico pedidos360.pagos. La clave del mensaje es tarjetaHash, así todos
// los intentos de una tarjeta quedan en la misma partición y en orden.
public record PagoEvent(
        String eventoId,
        Long pagoId,
        Long ordenId,
        String usuarioEmail,
        Double monto,
        String tarjetaHash,
        String ultimos4,
        String estado,
        String motivo,
        Instant ocurridoEn) {

    // eventoId estable por pago: si se reintenta el envío, el consumidor lo reconoce como duplicado
    public static PagoEvent de(Pago pago) {
        return new PagoEvent("pago-" + pago.getId(), pago.getId(), pago.getOrdenId(), pago.getUsuarioEmail(),
                pago.getMonto(), pago.getTarjetaHash(), pago.getUltimos4(), pago.getEstado().name(),
                pago.getMotivo(), pago.getCreadoEn());
    }
}
