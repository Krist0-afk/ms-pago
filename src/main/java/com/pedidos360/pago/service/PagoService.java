package com.pedidos360.pago.service;

import com.pedidos360.pago.dto.PagoRequest;
import com.pedidos360.pago.messaging.producer.PagoEventPublisher;
import com.pedidos360.pago.model.AlertaFraude;
import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.repository.AlertaFraudeRepository;
import com.pedidos360.pago.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Pago simulado: valida la tarjeta, aplica la regla antifraude y publica el resultado en Kafka.
// Tarjetas de prueba: cualquier número válido (Luhn) se aprueba, salvo las terminadas en 0002
// (fondos insuficientes), que se rechazan.
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);
    static final String SUFIJO_SIN_FONDOS = "0002";

    private final PagoRepository pagoRepository;
    private final AlertaFraudeRepository alertaRepository;
    private final TarjetaService tarjetaService;
    private final AntifraudeService antifraudeService;
    private final PagoEventPublisher publisher;
    private final Clock clock;

    // Un candado por tarjeta: dos intentos simultáneos con la misma tarjeta se evalúan en orden
    private final ConcurrentHashMap<String, Object> candados = new ConcurrentHashMap<>();

    public PagoService(PagoRepository pagoRepository, AlertaFraudeRepository alertaRepository,
                       TarjetaService tarjetaService, AntifraudeService antifraudeService,
                       PagoEventPublisher publisher, Clock clock) {
        this.pagoRepository = pagoRepository;
        this.alertaRepository = alertaRepository;
        this.tarjetaService = tarjetaService;
        this.antifraudeService = antifraudeService;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Pago pagar(PagoRequest request, String usuarioEmail) {
        if (!tarjetaService.luhnValido(request.numeroTarjeta())) {
            throw new PagoInvalidoException("El número de tarjeta no es válido");
        }
        if (tarjetaService.vencida(request.vencimiento(), YearMonth.now(clock.withZone(ZoneId.of("America/Santiago"))))) {
            throw new PagoInvalidoException("La tarjeta está vencida");
        }
        if (pagoRepository.existsByOrdenIdAndEstado(request.ordenId(), EstadoPago.APROBADO)) {
            throw new PagoInvalidoException("La orden #" + request.ordenId() + " ya está pagada");
        }

        String hash = tarjetaService.hash(request.numeroTarjeta());
        String ultimos4 = tarjetaService.ultimos4(request.numeroTarjeta());

        Pago pago;
        synchronized (candados.computeIfAbsent(hash, k -> new Object())) {
            long previos = antifraudeService.intentosRecientes(hash);
            pago = new Pago(request.ordenId(), usuarioEmail, request.monto(), hash, ultimos4, clock.instant());

            if (antifraudeService.esFraude(previos)) {
                pago.resolver(EstadoPago.BLOQUEADO_FRAUDE, "Tarjeta usada " + (previos + 1) + " veces en "
                        + antifraudeService.getVentanaSegundos() + " segundos");
                alertaRepository.save(new AlertaFraude(hash, ultimos4, usuarioEmail, request.ordenId(),
                        previos + 1, antifraudeService.getVentanaSegundos()));
                log.warn("[ANTIFRAUDE] Bloqueado el pago de la orden #{}: tarjeta ****{} usada {} veces en {} s (usuario {})",
                        request.ordenId(), ultimos4, previos + 1, antifraudeService.getVentanaSegundos(), usuarioEmail);
            } else if (ultimos4.equals(SUFIJO_SIN_FONDOS)) {
                pago.resolver(EstadoPago.RECHAZADO, "Fondos insuficientes");
            } else {
                pago.resolver(EstadoPago.APROBADO, null);
            }
            pago = pagoRepository.save(pago);
        }

        log.info("Pago #{} de la orden #{} por ${}: {}", pago.getId(), pago.getOrdenId(), pago.getMonto(), pago.getEstado());
        publisher.publicar(pago);
        return pago;
    }

    public List<Pago> misPagos(String usuarioEmail) {
        return pagoRepository.findByUsuarioEmailOrderByCreadoEnDesc(usuarioEmail);
    }

    public List<AlertaFraude> alertas() {
        return alertaRepository.findTop100ByOrderByCreadoEnDesc();
    }
}
