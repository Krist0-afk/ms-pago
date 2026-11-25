package com.pedidos360.pago.config;

import com.pedidos360.pago.model.EstadoPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

// Hibernate crea un check con los valores del enum al crear la tabla, pero con
// ddl-auto: update no lo vuelve a generar si después se agregan estados (pasó con
// PENDIENTE de Webpay). Al arrancar se rehace el check con los estados actuales.
@Component
public class EstadoPagoConstraint implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EstadoPagoConstraint.class);

    private final JdbcTemplate jdbc;

    public EstadoPagoConstraint(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String producto = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (!"PostgreSQL".equalsIgnoreCase(producto)) {
                return;
            }
            String estados = Arrays.stream(EstadoPago.values())
                    .map(e -> "'" + e.name() + "'")
                    .collect(Collectors.joining(","));
            jdbc.execute("ALTER TABLE pagos DROP CONSTRAINT IF EXISTS pagos_estado_check");
            jdbc.execute("ALTER TABLE pagos ADD CONSTRAINT pagos_estado_check CHECK (estado IN (" + estados + "))");
            log.info("Check de estados de pago actualizado: {}", estados);
        } catch (RuntimeException e) {
            log.warn("No se pudo actualizar el check de estados de pago: {}", e.getMessage());
        }
    }
}
