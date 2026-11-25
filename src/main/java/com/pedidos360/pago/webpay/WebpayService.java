package com.pedidos360.pago.webpay;

import com.pedidos360.pago.messaging.producer.PagoEventPublisher;
import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.repository.PagoRepository;
import com.pedidos360.pago.service.PagoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.Optional;

// Pago con Webpay Plus:
// 1. iniciar(): se registra el pago PENDIENTE y se crea la transacción en Transbank.
// 2. El frontend envía al cliente al formulario de Webpay (POST con token_ws).
// 3. retorno(): Transbank devuelve al cliente a este microservicio; se confirma la transacción,
//    el pago queda APROBADO o RECHAZADO, se publica en Kafka y el cliente vuelve al frontend.
@Service
public class WebpayService {

    private static final Logger log = LoggerFactory.getLogger(WebpayService.class);

    private final WebpayCliente cliente;
    private final PagoRepository pagoRepository;
    private final PagoEventPublisher publisher;
    private final Clock clock;
    private final String urlRetorno;
    private final String urlFrontend;

    public WebpayService(WebpayCliente cliente, PagoRepository pagoRepository, PagoEventPublisher publisher, Clock clock,
                         @Value("${app.webpay.url-retorno}") String urlRetorno,
                         @Value("${app.webpay.url-frontend}") String urlFrontend) {
        this.cliente = cliente;
        this.pagoRepository = pagoRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.urlRetorno = urlRetorno;
        this.urlFrontend = urlFrontend;
    }

    public record Inicio(Long pagoId, String token, String url) {
    }

    public Inicio iniciar(Long ordenId, Double monto, String usuarioEmail) {
        if (pagoRepository.existsByOrdenIdAndEstado(ordenId, EstadoPago.APROBADO)) {
            throw new PagoInvalidoException("La orden #" + ordenId + " ya está pagada");
        }
        Pago pago = pagoRepository.save(Pago.webpay(ordenId, usuarioEmail, monto, clock.instant()));
        // buy_order admite hasta 26 caracteres
        String ordenCompra = "P360-" + ordenId + "-" + pago.getId();
        try {
            WebpayCliente.Creacion creacion = cliente.crear(ordenCompra, "s" + pago.getId(), Math.round(monto), urlRetorno);
            pago.iniciarWebpay(creacion.token(), ordenCompra);
            pagoRepository.save(pago);
            log.info("Webpay iniciado para la orden #{} (pago #{}, orden de compra {})", ordenId, pago.getId(), ordenCompra);
            return new Inicio(pago.getId(), creacion.token(), creacion.url());
        } catch (RestClientException e) {
            pago.resolver(EstadoPago.RECHAZADO, "Webpay no respondió");
            pagoRepository.save(pago);
            log.error("No se pudo crear la transacción Webpay de la orden #{}: {}", ordenId, e.getMessage());
            throw new PagoInvalidoException("Webpay no está disponible, intenta más tarde");
        }
    }

    // Devuelve la URL del frontend a la que se redirige al cliente
    public String retorno(String tokenWs, String tbkToken, String tbkOrdenCompra) {
        // Flujo normal: solo llega token_ws
        if (tokenWs != null && tbkToken == null) {
            return confirmar(tokenWs);
        }
        // Anulado por el cliente (TBK_TOKEN), tiempo agotado (solo TBK_ORDEN_COMPRA) o error del formulario
        Optional<Pago> pago = tbkToken != null ? pagoRepository.findByWebpayToken(tbkToken)
                : tokenWs != null ? pagoRepository.findByWebpayToken(tokenWs)
                : Optional.ofNullable(tbkOrdenCompra).flatMap(pagoRepository::findByOrdenCompra);
        String motivo = tbkToken != null ? "Pago anulado en Webpay" : "Tiempo agotado en Webpay";
        return pago.map(p -> cerrar(p, EstadoPago.RECHAZADO, motivo, "anulado"))
                .orElse(urlFrontend + "/ordenes?pago=error");
    }

    private String confirmar(String token) {
        Optional<Pago> encontrado = pagoRepository.findByWebpayToken(token);
        if (encontrado.isEmpty()) {
            log.warn("Retorno de Webpay con un token desconocido");
            return urlFrontend + "/ordenes?pago=error";
        }
        Pago pago = encontrado.get();
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            // El cliente recargó la página de retorno: el pago ya estaba resuelto
            return urlFrontend + "/ordenes?pago=" + (pago.getEstado() == EstadoPago.APROBADO ? "aprobado" : "rechazado")
                    + "&orden=" + pago.getOrdenId();
        }
        try {
            WebpayCliente.Confirmacion confirmacion = cliente.confirmar(token);
            if (confirmacion != null && confirmacion.aprobada()) {
                pago.autorizarWebpay(confirmacion.ultimos4(), confirmacion.codigoAutorizacion());
                return cerrar(pago, EstadoPago.APROBADO, null, "aprobado");
            }
            String codigo = confirmacion != null ? String.valueOf(confirmacion.codigoRespuesta()) : "sin respuesta";
            return cerrar(pago, EstadoPago.RECHAZADO, "Rechazado por el banco (código " + codigo + ")", "rechazado");
        } catch (RestClientException e) {
            log.error("Error al confirmar el pago #{} en Webpay: {}", pago.getId(), e.getMessage());
            return cerrar(pago, EstadoPago.RECHAZADO, "No se pudo confirmar en Webpay", "rechazado");
        }
    }

    private String cerrar(Pago pago, EstadoPago estado, String motivo, String resultado) {
        if (pago.getEstado() == EstadoPago.PENDIENTE) {
            pago.resolver(estado, motivo);
            pago = pagoRepository.save(pago);
            log.info("Pago Webpay #{} de la orden #{}: {}", pago.getId(), pago.getOrdenId(), estado);
            publisher.publicar(pago);
        }
        return urlFrontend + "/ordenes?pago=" + resultado + "&orden=" + pago.getOrdenId();
    }
}
