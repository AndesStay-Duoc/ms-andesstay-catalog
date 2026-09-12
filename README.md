# ms-andesstay-catalog

Microservicio de **catálogo y disponibilidad** del sistema AndesStay. Administra las unidades
—habitaciones, cabañas y lodges—, sus tarifas y los cupos por fecha.

## Responsabilidad

Es el servicio que resuelve el **overbooking**, que es el problema central del caso: la
disponibilidad deja de vivir en planillas separadas y se descuenta en el momento en que una
reserva se confirma.

| Módulo | Qué hace |
|---|---|
| Unidades | CRUD de habitaciones, cabañas y lodges, con capacidad y tarifa |
| Disponibilidad | Cupos por unidad y fecha |
| Reserva de cupo | `hold` al confirmar una reserva, `release` al cancelar o hacer checkout |

## Endpoints

| Método | Ruta | Rol |
|---|---|---|
| `GET` | `/api/catalog/units` | Admin, Recepcionista, Huésped |
| `POST` | `/api/catalog/units` | Admin |
| `PUT` | `/api/catalog/units/{id}` | Admin |
| `GET` | `/api/catalog/units/{id}/availability` | Admin, Recepcionista, Huésped |
| `POST` | `/api/catalog/units/{id}/hold` | interno |
| `POST` | `/api/catalog/units/{id}/release` | interno |

`hold` y `release` **no se publican en el API Gateway**: solo los llama
`ms-andesstay-reservations` dentro de la red privada, propagando el JWT del usuario para no
perder la trazabilidad en la auditoría.

Se accede siempre a través del BFF. Ver
[`rutas-gateway.md`](https://github.com/AndesStay-Duoc/infra/blob/develop/docs/contracts/rutas-gateway.md).

## Concurrencia: el detalle que evita el overbooking

`hold` usa **bloqueo optimista** (`@Version`) o `SELECT ... FOR UPDATE`. Sin eso, dos
confirmaciones simultáneas de la última unidad disponible pueden pasar ambas, que es
exactamente el defecto que el caso pide corregir.

`release` es **idempotente**: liberar dos veces el mismo `holdId` no devuelve cupo de más.

## Stack

Java 21 · Spring Boot 3.5.3 · Spring Data JPA · Oracle · Spring Security como resource server.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `DB_URL` | JDBC del esquema `CATALOG` |
| `DB_USER` | Usuario del esquema `CATALOG` |
| `DB_PASSWORD` | Contraseña del esquema |
| `JWT_ISSUER` | Issuer del tenant |
| `JWT_AUDIENCE_STAFF` | Audience de la aplicación del personal |
| `JWT_AUDIENCE_GUEST` | Audience de la aplicación de huéspedes |

Se configuran en un `.env` que **no se versiona**. Ver `.env.example`.

## Cómo levantarlo

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Requiere Oracle en marcha. El compose está en el repositorio
[`infra`](https://github.com/AndesStay-Duoc/infra).

## Contratos

El contrato REST y las reglas de disponibilidad son canónicos y viven en
[`infra/docs/contracts/`](https://github.com/AndesStay-Duoc/infra/tree/develop/docs/contracts):
[`openapi/catalog.yaml`](https://github.com/AndesStay-Duoc/infra/blob/develop/docs/contracts/openapi/catalog.yaml)
y [`estados.md`](https://github.com/AndesStay-Duoc/infra/blob/develop/docs/contracts/estados.md).

## Estado

Repositorio inicializado. La implementación corresponde a la Fase 5 del plan.

## Cómo contribuir

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md).
