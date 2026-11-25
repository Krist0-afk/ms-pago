package com.pedidos360.pago.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WebpayRequest(@NotNull @Positive Long ordenId, @NotNull @Positive Double monto) {
}
