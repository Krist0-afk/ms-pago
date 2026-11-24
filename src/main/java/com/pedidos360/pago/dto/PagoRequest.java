package com.pedidos360.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PagoRequest(
        @NotNull @Positive Long ordenId,
        @NotNull @Positive Double monto,
        @NotBlank @Pattern(regexp = "^[0-9 ]{13,23}$", message = "Número de tarjeta inválido") String numeroTarjeta,
        @NotBlank @Size(max = 80) String titular,
        @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Vencimiento con formato MM/AA") String vencimiento,
        @NotBlank @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV inválido") String cvv) {

    // Evita que el número de la tarjeta o el CVV terminen en un log
    @Override
    public String toString() {
        return "PagoRequest[ordenId=" + ordenId + ", monto=" + monto + "]";
    }
}
