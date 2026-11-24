package com.pedidos360.pago.service;

import com.pedidos360.pago.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

// Regla antifraude: una misma tarjeta usada más de N veces dentro de la ventana se bloquea.
// La decisión es síncrona (antes de aprobar) porque el bloqueo debe ser preventivo.
@Service
public class AntifraudeService {

    private final PagoRepository pagoRepository;
    private final Clock clock;
    private final int maxIntentos;
    private final int ventanaSegundos;

    public AntifraudeService(PagoRepository pagoRepository, Clock clock,
                             @Value("${app.pagos.antifraude.max-intentos}") int maxIntentos,
                             @Value("${app.pagos.antifraude.ventana-segundos}") int ventanaSegundos) {
        this.pagoRepository = pagoRepository;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.ventanaSegundos = ventanaSegundos;
    }

    // Intentos previos de la tarjeta dentro de la ventana, sin contar el actual
    public long intentosRecientes(String tarjetaHash) {
        Instant desde = clock.instant().minusSeconds(ventanaSegundos);
        return pagoRepository.countByTarjetaHashAndCreadoEnAfter(tarjetaHash, desde);
    }

    // Con el intento actual, ¿la tarjeta supera el máximo permitido?
    public boolean esFraude(long intentosPrevios) {
        return intentosPrevios + 1 > maxIntentos;
    }

    public int getVentanaSegundos() {
        return ventanaSegundos;
    }
}
