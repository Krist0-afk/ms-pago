# ms-pago

Microservicio de pagos y antifraude de Pedidos360. Spring Boot 4.1 · Java 21 · puerto **8089** ·
base `pagos_db` (PostgreSQL).

## Qué hace

1. Recibe el pago de una orden y valida la tarjeta (algoritmo de Luhn y vencimiento).
2. **Regla antifraude:** si la misma tarjeta se usa **más de 3 veces en 10 segundos**, el pago se
   bloquea de forma preventiva (`BLOQUEADO_FRAUDE`) y queda una alerta en `alertas_fraude`.
   La decisión es síncrona, antes de aprobar.
3. Simula la respuesta del emisor: se aprueba cualquier tarjeta válida, salvo las terminadas en
   `0002` (fondos insuficientes).
4. Publica el resultado en el tópico Kafka **`pedidos360.pagos`** (clave = hash de la tarjeta, así
   los intentos de una tarjeta quedan en orden en la misma partición). Si Kafka no responde, el pago
   queda pendiente de publicar y se reintenta cada 30 segundos.

Nunca se guarda el número de la tarjeta: solo un HMAC-SHA256 (con `PAGO_HASH_SECRET`) y los
últimos 4 dígitos.

Tarjetas de prueba: `4111 1111 1111 1111` (aprobada), `4000 0000 0000 0002` (rechazada).

## Webpay Plus (Transbank)

Ambiente de integración con el comercio de prueba público de Transbank (no hay cobros reales):

1. `POST /api/v1/pagos/webpay` registra el pago `PENDIENTE` y crea la transacción; responde `token` y `url`.
2. El frontend envía al cliente a `url` con un POST que lleva el campo `token_ws`.
3. Transbank devuelve al cliente a `/api/v1/pagos/webpay/retorno` (GET o POST, ruta pública).
   ms-pago confirma la transacción (`AUTHORIZED` y `response_code` 0 = aprobado), publica el
   resultado en Kafka y redirige al frontend a `/ordenes?pago=aprobado|rechazado|anulado&orden=<id>`.

Tarjeta de prueba VISA `4051 8856 0044 6623`, CVV `123`; en el banco simulado, RUT `11.111.111-1` y clave `123`.

## Endpoints (`/api/v1/pagos`)

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/` | 201 aprobado · 402 rechazado · 403 bloqueado por antifraude · 400 datos inválidos |
| POST | `/webpay` | Inicia el pago con Webpay (`ordenId`, `monto`) |
| GET/POST | `/webpay/retorno` | Retorno desde Transbank (público) |
| GET | `/mios` | Pagos del usuario del token |
| GET | `/alertas` | Alertas de fraude (rol Admin) |
| GET | `/health` | Estado (público) |

## Evento `PagoEvent`

`eventoId`, `pagoId`, `ordenId`, `usuarioEmail`, `monto`, `tarjetaHash`, `ultimos4`, `estado`,
`motivo`, `ocurridoEn`. Lo consumen ordenes (grupo `ordenes-pagos`, marca la orden PAGADA) y
analítica (grupo `analytics-pagos`, métricas y alertas en vivo).

## Variables de entorno

| Variable | Ejemplo |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS` | Endpoint de RDS, `pagos_db` |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka1:29092,kafka2:29092,kafka3:29092` |
| `PAGO_HASH_SECRET` | Cadena larga aleatoria |
| `WEBPAY_URL_RETORNO` | `https://<api-gateway>/api/v1/pagos/webpay/retorno` |
| `FRONT_URL` | `https://<ip-del-front>` |
| `WEBPAY_URL`, `WEBPAY_CODIGO_COMERCIO`, `WEBPAY_API_KEY` | Por defecto, integración de Transbank |
| `COGNITO_ISSUER`, `COGNITO_CLIENT_ID` | Pool de AWS Cognito |
| `CORS_ALLOWED_ORIGINS` | `https://<ip-del-front>` |

## Comandos

```bash
./mvnw spring-boot:run
./mvnw test        # incluye las pruebas de la regla antifraude
docker build -t pedidos360-pago .
```
