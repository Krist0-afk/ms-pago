package com.pedidos360.pago.webpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Cliente REST de Webpay Plus (Transbank). En integración usa el comercio de prueba público.
@Component
public class WebpayCliente {

    private static final String RUTA = "/rswebpaytransaction/api/webpay/v1.2/transactions";

    private final RestClient restClient;

    public WebpayCliente(RestClient.Builder builder,
                         @Value("${app.webpay.url}") String url,
                         @Value("${app.webpay.codigo-comercio}") String codigoComercio,
                         @Value("${app.webpay.api-key}") String apiKey) {
        this.restClient = builder.baseUrl(url)
                .defaultHeader("Tbk-Api-Key-Id", codigoComercio)
                .defaultHeader("Tbk-Api-Key-Secret", apiKey)
                .build();
    }

    // Crea la transacción: Transbank devuelve el token y la URL de su formulario de pago
    public Creacion crear(String ordenCompra, String sesion, long monto, String urlRetorno) {
        return restClient.post().uri(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("buy_order", ordenCompra, "session_id", sesion, "amount", monto, "return_url", urlRetorno))
                .retrieve()
                .body(Creacion.class);
    }

    // Confirma la transacción cuando el cliente vuelve del formulario del banco
    public Confirmacion confirmar(String token) {
        return restClient.put().uri(RUTA + "/{token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Confirmacion.class);
    }

    public record Creacion(String token, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confirmacion(
            String status,
            @JsonProperty("response_code") Integer codigoRespuesta,
            Long amount,
            @JsonProperty("buy_order") String ordenCompra,
            @JsonProperty("authorization_code") String codigoAutorizacion,
            @JsonProperty("card_detail") Tarjeta tarjeta) {

        // Aprobado solo si Transbank autorizó y el código de respuesta es 0
        public boolean aprobada() {
            return "AUTHORIZED".equals(status) && Integer.valueOf(0).equals(codigoRespuesta);
        }

        public String ultimos4() {
            return tarjeta != null ? tarjeta.numero() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tarjeta(@JsonProperty("card_number") String numero) {
    }
}
