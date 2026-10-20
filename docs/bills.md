# Creación y edición de facturas

Entrega de **SCRUM-16** para **Administrador y Recepcionista** (Proceso 3: facturación de la atención).
Esta tarea implementa la lógica de negocio en `BillService`. Los endpoints REST se exponen en SCRUM-24.
Los abonos parciales, la anulación, el descuento automático de inventario, las tarifas fijas por
tratamiento y el envío del comprobante por correo pertenecen a tareas posteriores.

## Operaciones del servicio

| Operación | Resultado |
| --- | --- |
| `create` | Crea la factura de una cita en estado `Pendiente` |
| `get` / `getByMeeting` | Consulta por ID de factura o por ID de cita, con la versión para editar |
| `update` | Reemplaza todas las líneas y recalcula el total |
| `pay` | Registra el método de pago y pasa la factura a `Pagada` |

Todas exigen `ROLE_Administrador` o `ROLE_Recepcionista`. Como en citas y usuarios, la autorización
se aplica en el servicio, de modo que también protege llamadas internas.

### Creación

```json
{
  "meetingId": "00000000-0000-4000-8000-000000000001",
  "lines": [
    { "description": "Limpieza", "quantity": 1, "unitPrice": 80000 },
    { "description": "Resina", "quantity": 2, "unitPrice": 45000.50 }
  ]
}
```

- La cita debe existir y no estar `Cancelada`. Cada cita admite como máximo **una** factura.
- Entre 1 y 50 líneas. Descripción: 1 a 200 caracteres. Cantidad: 1 a 999.
  Precio unitario: de 0 a 99.999.999,99, con máximo dos decimales (se admite 0 para servicios sin costo).
- El precio lo digita recepción en cada línea; todavía no existe un catálogo de tarifas.
- **El subtotal de cada línea y el total los calcula el servidor**; el cliente no los envía.
- La factura nace `Pendiente`, sin método ni fecha de pago. `issuedAt` es la fecha de emisión (UTC).

### Edición

```json
{
  "lines": [ { "description": "Valoración", "quantity": 1, "unitPrice": 30000 } ],
  "version": 0
}
```

Se envían **todas** las líneas: las anteriores se reemplazan y el total se recalcula.
La cita asociada no se puede cambiar. Solo se editan facturas `Pendiente`.

### Pago

```json
{ "paymentMethod": "Tarjeta", "version": 1 }
```

Métodos: `Efectivo`, `Tarjeta`, `Transferencia`. Una factura pagada ya no se edita ni se vuelve a pagar.
La respuesta funciona como comprobante: ID de la factura, líneas, total, método y fecha de pago.

### Respuesta

```json
{
  "id": "…", "meetingId": "…", "patientId": 123456789, "issuedAt": "2026-10-17T15:00:00Z",
  "lines": [ { "lineNumber": 1, "description": "Limpieza", "quantity": 1,
               "unitPrice": 80000.00, "subtotal": 80000.00 } ],
  "total": 80000.00, "status": "Pendiente", "paymentMethod": null, "paidAt": null, "version": 0
}
```

## Concurrencia, errores y auditoría

Editar y pagar exigen la última `version`. Si otro operador modificó la factura, se rechaza
con conflicto: volver a consultar antes de reenviar. Dos facturas simultáneas para la misma cita
las rechaza el índice único de PostgreSQL.

| Excepción | HTTP (vía `ApiExceptionHandler`) | Caso |
| --- | --- | --- |
| `ConstraintViolationException` | 400 | Datos inválidos |
| `InvalidBillException` | 400 | Cita cancelada |
| `BillNotFoundException` | 404 | Factura, cita o factura de la cita inexistente |
| `BillConflictException` | 409 | Cita ya facturada, versión desactualizada o factura ya pagada |

Cada creación, edición y pago registra en `bill_audit` la factura, el actor autenticado,
la acción (`CREATE`, `UPDATE`, `PAY`) y la fecha/hora UTC, en la misma transacción.
El actor queda en la auditoría porque las cuentas en PostgreSQL llegan con SCRUM-15;
por eso ya no se escribe la columna heredada `RecepcionistID`.

## Cambios al modelo anterior

El mapeo original de `Bill`/`BillDetail` no podía funcionar y se corrigió:

- UUID con `GenerationType.IDENTITY` (incompatible) → `GenerationType.UUID`.
- `@OneToOne` hacia el tratamiento: un tratamiento solo habría podido facturarse una vez en toda la historia.
  Las líneas ahora describen el servicio cobrado; el vínculo con tratamientos llegará con la historia clínica.
- `Double` para dinero (errores de redondeo) → `BigDecimal` / `NUMERIC`.
- `java.util.Date` → `Instant` (UTC), igual que en citas.

## Preparación de PostgreSQL

Aplicar **una vez** `database/004_bills.sql` después de 001, 002 y 003, revisando antes una copia del esquema.
Si las tablas `bill`/`billdetail` ya existen con la forma anterior, el script las convierte
(fechas con la misma suposición de hora colombiana que 002). Si hay facturas antiguas sin cita,
sin estado o sin líneas completas, falla y revierte: completarlas con datos verificados primero.

La base refuerza las reglas aunque se escriba sin pasar por el servicio: una factura por cita,
total no negativo, estado y método válidos, pagada si y solo si tiene método y fecha de pago,
y en cada línea cantidad 1–999, precio no negativo y `subtotal = precio × cantidad`.

## Pruebas

Sin base de datos externa:

```powershell
.\mvnw.cmd '-Dtest=BillServiceTests,BillValidationTests,BillAuthorizationTests' test
```

Persistencia real, migración y restricciones (se omiten si no se configura la base de pruebas):

```powershell
.\mvnw.cmd '-Dtest=BillPostgresTests' test
```

Usan las mismas variables `MEETING_TEST_DB_URL`, `MEETING_TEST_DB_USER` y `MEETING_TEST_DB_PASSWORD`
descritas en [meetings.md](meetings.md), con un esquema temporal que se crea y se elimina.
