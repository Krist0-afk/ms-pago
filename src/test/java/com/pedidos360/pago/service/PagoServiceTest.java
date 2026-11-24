package com.pedidos360.pago.service;

import com.pedidos360.pago.dto.PagoRequest;
import com.pedidos360.pago.messaging.producer.PagoEventPublisher;
import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.repository.AlertaFraudeRepository;
import com.pedidos360.pago.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Prueba la regla antifraude con la base H2 y un reloj controlado
@SpringBootTest
class PagoServiceTest {

    // Tarjetas de prueba que cumplen Luhn
    private static final String VISA = "4111 1111 1111 1111";
    private static final String SIN_FONDOS = "4000 0000 0000 0002";
    private static final String OTRA = "5555 5555 5555 4444";

    static final AtomicReference<Instant> AHORA = new AtomicReference<>();

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        Clock relojControlado() {
            return new Clock() {
                public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                public Clock withZone(java.time.ZoneId zone) { return this; }
                public Instant instant() { return AHORA.get(); }
            };
        }
    }

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private AlertaFraudeRepository alertaRepository;

    @MockitoBean
    private PagoEventPublisher publisher;

    private long orden = 100;

    @BeforeEach
    void limpiar() {
        pagoRepository.deleteAll();
        alertaRepository.deleteAll();
        AHORA.set(Instant.parse("2026-11-21T15:00:00Z"));
    }

    private Pago pagar(String tarjeta) {
        return pagoService.pagar(new PagoRequest(orden++, 7990.0, tarjeta, "Ana Pérez", "12/30", "123"), "ana@duocuc.cl");
    }

    private void avanzar(long segundos) {
        AHORA.set(AHORA.get().plusSeconds(segundos));
    }

    @Test
    void tresIntentosEnDiezSegundosSeAprueban() {
        assertThat(pagar(VISA).getEstado()).isEqualTo(EstadoPago.APROBADO);
        avanzar(2);
        assertThat(pagar(VISA).getEstado()).isEqualTo(EstadoPago.APROBADO);
        avanzar(2);
        assertThat(pagar(VISA).getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void cuartoIntentoEnDiezSegundosSeBloqueaYRegistraAlerta() {
        pagar(VISA);
        avanzar(2);
        pagar(VISA);
        avanzar(2);
        pagar(VISA);
        avanzar(2);

        Pago cuarto = pagar(VISA);

        assertThat(cuarto.getEstado()).isEqualTo(EstadoPago.BLOQUEADO_FRAUDE);
        assertThat(alertaRepository.findAll()).hasSize(1);
        assertThat(alertaRepository.findAll().get(0).getIntentos()).isEqualTo(4);
        // Todos los resultados, incluido el bloqueo, se publican en Kafka
        verify(publisher, times(4)).publicar(any());
    }

    @Test
    void pasadaLaVentanaLaTarjetaVuelveAPasar() {
        pagar(VISA);
        pagar(VISA);
        pagar(VISA);
        avanzar(11);

        assertThat(pagar(VISA).getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void laReglaEsPorTarjeta() {
        pagar(VISA);
        pagar(VISA);
        pagar(VISA);

        assertThat(pagar(OTRA).getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void tarjetaSinFondosSeRechaza() {
        assertThat(pagar(SIN_FONDOS).getEstado()).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    void noSeGuardaElNumeroDeLaTarjeta() {
        Pago pago = pagar(VISA);

        assertThat(pago.getUltimos4()).isEqualTo("1111");
        assertThat(pago.getTarjetaHash()).hasSize(64).doesNotContain("4111111111111111");
    }

    @Test
    void numeroQueNoCumpleLuhnSeRechazaSinRegistrarPago() {
        assertThatThrownBy(() -> pagar("4111 1111 1111 1112")).isInstanceOf(PagoInvalidoException.class);
        assertThat(pagoRepository.count()).isZero();
    }

    @Test
    void ordenYaPagadaNoSePuedePagarDeNuevo() {
        pagoService.pagar(new PagoRequest(500L, 1000.0, VISA, "Ana", "12/30", "123"), "ana@duocuc.cl");

        assertThatThrownBy(() -> pagoService.pagar(new PagoRequest(500L, 1000.0, OTRA, "Ana", "12/30", "123"), "ana@duocuc.cl"))
                .isInstanceOf(PagoInvalidoException.class);
    }
}
