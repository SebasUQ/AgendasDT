# Notificaciones de citas

Entrega de **SCRUM-17**: confirmaciones, avisos de reprogramación y recordatorios por **correo y SMS**
para reducir el ausentismo (Proceso 1 del documento del proyecto).
El envío es **simulado**: los mensajes se escriben en el log de la aplicación y no se conecta
ningún proveedor real. La consulta por API se expone en SCRUM-24.

## Cuándo se genera cada aviso

| Tipo | Cuándo | Contenido |
| --- | --- | --- |
| `Confirmacion` | Al crear una cita | Fecha, hora, odontólogo y código de la cita |
| `Reprogramacion` | Al cambiar horario, odontólogo o sillón | Nuevos datos de la cita |
| `Recordatorio` | Cuando faltan 24 horas o menos para la cita | Fecha, hora, odontólogo y aviso de contacto |

Cada aviso se genera **dos veces**: uno por `Correo` (correo del paciente) y otro por `SMS` (teléfono del paciente).
Las horas se muestran en hora de Colombia (`America/Bogota`).

- Cambiar solo el motivo de la cita no genera aviso.
- Al reprogramar, los avisos todavía pendientes de esa cita se descartan antes de crear los nuevos.
- No se envía recordatorio si el paciente ya recibió confirmación o reprogramación dentro de las 24 horas
  previas a la cita (por ejemplo, una cita agendada para dentro de 3 horas), ni si ya existe un recordatorio
  para ese mismo horario. Si la confirmación falló, sí se genera el recordatorio.

## Cómo funciona

1. `MeetingService` publica un evento al crear o reprogramar una cita.
2. `NotificationService` lo recibe **dentro de la misma transacción** y guarda los avisos como `Pendiente`:
   la cita y sus avisos se guardan juntos o no se guarda ninguno.
3. `NotificationScheduler` ejecuta periódicamente:
   - `createReminders` (cada 5 minutos): crea los recordatorios de citas `Pendiente`/`Confirmada` que empiezan en las próximas 24 horas.
   - `dispatchDue` (cada minuto): `NotificationDispatcher` toma hasta 50 avisos pendientes cuya hora llegó y los envía.
4. Envío:
   - Éxito → `Enviada`, con fecha de envío.
   - Error → se reintenta a los 5 y luego a los 10 minutos; al tercer error queda `Fallida` con el motivo.
   - Si la cita se canceló, se completó, se reprogramó o ya comenzó, el aviso pasa a `Descartada` sin enviarse.

El enviador es la interfaz `NotificationSender`. La implementación actual, `LoggingNotificationSender`,
escribe el mensaje en el log y oculta parte del destinatario (`a***@example.com`, `*********4567`).
Para conectar un proveedor real basta con otra implementación de esa interfaz; el resto no cambia.

El envío no ocurre dentro de las peticiones de los usuarios, así que un proveedor lento o caído
no afecta el agendamiento.

## Consulta

`NotificationService.listByMeeting(meetingId)` devuelve los avisos de una cita (tipo, canal, destinatario,
mensaje, estado, intentos, fechas y último error). Exige `ROLE_Administrador` o `ROLE_Recepcionista`;
si la cita no existe se lanza `MeetingNotFoundException` (404). Los procesos automáticos no requieren usuario.

## Configuración

Valores opcionales; se pueden definir en `application.properties` o como variables de entorno
(por ejemplo `NOTIFICATIONS_DISPATCH_DELAY_MS`):

| Propiedad | Valor por defecto | Uso |
| --- | --- | --- |
| `notifications.scheduling.enabled` | `true` | `false` desactiva recordatorios y envíos automáticos |
| `notifications.reminder-delay-ms` | `300000` | Pausa entre búsquedas de recordatorios |
| `notifications.dispatch-delay-ms` | `60000` | Pausa entre envíos de pendientes |

## Preparación de PostgreSQL

Aplicar **una vez** `database/005_notifications.sql` después de 001, 002 y 003 (no depende de 004).
La clase `Notification` anterior nunca estuvo mapeada a una tabla, así que el script crea `notification` desde cero.

La base refuerza las reglas aunque se escriba sin pasar por el servicio: tipos, canales y estados válidos,
máximo 3 intentos, fecha de envío presente solo en avisos entregados, y un único recordatorio activo
por canal para cada horario de cita. El envío toma los avisos con `FOR UPDATE SKIP LOCKED`, de modo que
un mismo aviso no lo procesan dos envíos a la vez.

## Pruebas

Sin base de datos externa:

```powershell
.\mvnw.cmd '-Dtest=Notification*Tests,LoggingNotificationSenderTests,MeetingNotificationWiringTests,MeetingServiceTests' test
```

Persistencia, bloqueo y restricciones en PostgreSQL (se omiten sin la base de pruebas):

```powershell
.\mvnw.cmd '-Dtest=NotificationPostgresTests' test
```

Usan las variables `MEETING_TEST_DB_URL`, `MEETING_TEST_DB_USER` y `MEETING_TEST_DB_PASSWORD`
descritas en [meetings.md](meetings.md).
