package com.pedidos360.pago.repository;

import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Intentos con la misma tarjeta dentro de la ventana antifraude
    long countByTarjetaHashAndCreadoEnAfter(String tarjetaHash, Instant desde);

    List<Pago> findByUsuarioEmailOrderByCreadoEnDesc(String usuarioEmail);

    List<Pago> findByOrdenIdOrderByCreadoEnDesc(Long ordenId);

    boolean existsByOrdenIdAndEstado(Long ordenId, EstadoPago estado);

    // Pagos ya resueltos cuyo evento todavía no se confirmó en Kafka
    List<Pago> findTop50ByEventoPublicadoFalseAndEstadoNotAndCreadoEnBeforeOrderByCreadoEn(EstadoPago estado, Instant antesDe);

    Optional<Pago> findByWebpayToken(String webpayToken);

    Optional<Pago> findByOrdenCompra(String ordenCompra);
}
