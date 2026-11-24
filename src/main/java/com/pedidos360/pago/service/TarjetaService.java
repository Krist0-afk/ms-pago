package com.pedidos360.pago.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.YearMonth;
import java.util.HexFormat;

// Validación y anonimización de tarjetas
@Service
public class TarjetaService {

    private final SecretKeySpec clave;

    public TarjetaService(@Value("${app.pagos.secreto-hash}") String secreto) {
        this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String normalizar(String numero) {
        return numero.replace(" ", "");
    }

    // HMAC en vez de un hash simple: sin el secreto no se puede probar números hasta dar con el hash
    public String hash(String numero) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            return HexFormat.of().formatHex(mac.doFinal(normalizar(numero).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la tarjeta", e);
        }
    }

    public String ultimos4(String numero) {
        String n = normalizar(numero);
        return n.substring(n.length() - 4);
    }

    // Algoritmo de Luhn: detecta números mal digitados
    public boolean luhnValido(String numero) {
        String n = normalizar(numero);
        int suma = 0;
        boolean doblar = false;
        for (int i = n.length() - 1; i >= 0; i--) {
            int d = n.charAt(i) - '0';
            if (doblar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    public boolean vencida(String vencimiento, YearMonth hoy) {
        int mes = Integer.parseInt(vencimiento.substring(0, 2));
        int anio = 2000 + Integer.parseInt(vencimiento.substring(3, 5));
        return YearMonth.of(anio, mes).isBefore(hoy);
    }
}
