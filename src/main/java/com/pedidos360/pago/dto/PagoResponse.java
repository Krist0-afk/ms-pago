package com.pedidos360.pago.dto;

import com.pedidos360.pago.model.Pago;

import java.time.Instant;

public record PagoResponse(Long pagoId, Long ordenId, Double monto, String ultimos4, String estado,
                           String motivo, Instant fecha, String metodo) {

    public static PagoResponse de(Pago pago) {
        return new PagoResponse(pago.getId(), pago.getOrdenId(), pago.getMonto(), pago.getUltimos4(),
                pago.getEstado().name(), pago.getMotivo(), pago.getCreadoEn(), pago.getMetodo());
    }
}
