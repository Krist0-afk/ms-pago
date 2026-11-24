package com.pedidos360.pago.controller;

import com.pedidos360.pago.dto.PagoRequest;
import com.pedidos360.pago.dto.PagoResponse;
import com.pedidos360.pago.model.AlertaFraude;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.service.PagoInvalidoException;
import com.pedidos360.pago.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "ms-pago", "status", "UP");
    }

    // 201 aprobado, 402 rechazado por el emisor, 403 bloqueado por antifraude
    @PostMapping
    public ResponseEntity<PagoResponse> pagar(@Valid @RequestBody PagoRequest request, Authentication auth) {
        Pago pago = pagoService.pagar(request, auth.getName());
        HttpStatus estado = switch (pago.getEstado()) {
            case APROBADO -> HttpStatus.CREATED;
            case RECHAZADO -> HttpStatus.PAYMENT_REQUIRED;
            case BLOQUEADO_FRAUDE -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(estado).body(PagoResponse.de(pago));
    }

    @GetMapping("/mios")
    public List<PagoResponse> misPagos(Authentication auth) {
        return pagoService.misPagos(auth.getName()).stream().map(PagoResponse::de).toList();
    }

    @GetMapping("/alertas")
    public List<AlertaFraude> alertas() {
        return pagoService.alertas();
    }

    @ExceptionHandler(PagoInvalidoException.class)
    public ResponseEntity<Map<String, String>> pagoInvalido(PagoInvalidoException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException ex) {
        var campo = ex.getBindingResult().getFieldError();
        String mensaje = campo != null ? campo.getField() + ": " + campo.getDefaultMessage() : "Datos inválidos";
        return ResponseEntity.badRequest().body(Map.of("error", mensaje));
    }
}
