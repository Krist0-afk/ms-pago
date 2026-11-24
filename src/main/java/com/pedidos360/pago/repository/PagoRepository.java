package com.pedidos360.pago.repository;

import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Intentos con la misma tarjeta dentro de la ventana antifraude
    long countByTarjetaHashAndCreadoEnAfter(String tarjetaHash, Instant desde);

    List<Pago> findByUsuarioEmailOrderByCreadoEnDesc(String usuarioEmail);

    List<Pago> findByOrdenIdOrderByCreadoEnDesc(Long ordenId);

    boolean existsByOrdenIdAndEstado(Long ordenId, EstadoPago estado);

    // Pagos cuyo evento todavía no se confirmó en Kafka
    List<Pago> findTop50ByEventoPublicadoFalseAndCreadoEnBeforeOrderByCreadoEn(Instant antesDe);
}
