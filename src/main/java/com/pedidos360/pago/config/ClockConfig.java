package com.pedidos360.pago.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // Reloj inyectable: permite probar la ventana antifraude sin esperar segundos reales
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
