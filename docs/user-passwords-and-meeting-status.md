# Contraseñas y ampliación de citas

## Contraseña de usuario

POST `/api/users` y PUT `/api/users/{id}` admiten un campo adicional de solo entrada:

```json
{"password": "Una-clave-de-ejemplo-123"}
```

Se deben enviar también los demás campos de cada contrato; este ejemplo muestra solo la adición.
La contraseña es opcional para conservar los clientes existentes y preparar la autenticación posterior.
Si se proporciona, debe contener al menos 8 caracteres, no estar en blanco y ocupar como máximo
72 bytes UTF-8. No se recorta ni se normaliza; se almacena un hash BCrypt con costo 12 y sal aleatoria.
En edición, omitirla o enviar null conserva la anterior. Una cadena vacía es inválida.
Ni las respuestas, ni el `toString` de solicitudes, ni la auditoría incluyen contraseñas.
No se reciben hashes calculados por el cliente.

El bean `PasswordEncoder` está disponible en todos los perfiles y también lo reutiliza el perfil demo.
El futuro login deberá usar `PasswordEncoder.matches` y denegar cuentas sin hash. Esta actualización
no cambia el mecanismo actual de inicio de sesión.

## Lista de citas de odontólogo

```text
GET /api/meetings/doctors/987654321?page=0&size=20
```

Devuelve `content` (citas), `page`, `size`, `totalElements` y `totalPages`, ordenadas por inicio e ID.
La página empieza en 0 y el tamaño va de 1 a 100. Incluye los cuatro estados actuales, sin filtro de fecha.
Para un odontólogo sin citas, devuelve una lista vacía. Si el ID no existe devuelve 404;
si no corresponde a un odontólogo devuelve 400.

Administrador y recepción ven la agenda completa. Un odontólogo solo puede solicitar su propia agenda.
Un paciente puede consultar esta ruta, pero la consulta se filtra en la base de datos y solo devuelve
sus propias citas con ese odontólogo. El contenido y los totales respetan ese filtro.

## Cambio de estado

```text
PATCH /api/meetings/{uuid}/status
```

```json
{
  "status": "Confirmada",
  "version": 0
}
```

Usar la versión de la última consulta. La respuesta devuelve la cita y su nueva versión.

| Estado actual | Destinos permitidos |
| --- | --- |
| Pendiente | Confirmada, Cancelada |
| Confirmada | Cancelada, Completada |
| Cancelada | Ninguno |
| Completada | Ninguno |

Repetir el mismo estado con la versión vigente devuelve la cita sin escribir otra auditoría.
Una versión obsoleta devuelve 409 incluso si el estado solicitado coincide.
Solo se confirma una cita que no haya iniciado y solo se completa después de su inicio.
El paciente solo puede confirmar o cancelar sus propias citas; cancelar requiere al menos 24 horas.
El odontólogo solo cambia el estado de citas que tiene asignadas. Administración y recepción
pueden gestionar todas, respetando las transiciones. Las canceladas liberan disponibilidad gracias
a los filtros del servicio y las restricciones de PostgreSQL existentes.

Los estados anteriores/nuevos se registran en `meeting_audit` en la misma transacción.
Las notificaciones antiguas de citas canceladas se descartan al pasar por el despachador existente;
no se agrega un nuevo canal ni un mensaje automático de cancelación.

## Preparación y pruebas

Aplicar `database/007_user_passwords_meeting_access.sql` en la base correspondiente antes de desplegar.
No se modificó la base real. El script es aditivo: conserva usuarios sin contraseña y la auditoría anterior.

La suite incluye pruebas de BCrypt, confidencialidad de respuestas, permisos por propietario,
paginación, límite de 24 horas, transiciones, versión obsoleta e integración de usuarios/citas con H2.
Los tests PostgreSQL siguen siendo optativos y requieren sus variables de conexión a una base de pruebas.
La suite original muestra advertencias de creación de tablas de otros módulos en H2 por mapeos
UUID/IDENTITY anteriores; esos mapeos no se han modificado aquí.
