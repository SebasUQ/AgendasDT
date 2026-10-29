# Creación y edición de citas

Acceso para **Administrador, Recepcionista, Odontologo y Paciente** con restricciones por identidad.
La confirmación por enlace, sala de espera y reglas de inasistencia no forman parte de esta entrega.
Las notificaciones existentes continúan funcionando; el despachador descarta mensajes de citas canceladas.

## API

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/meetings` | 201, cita creada y encabezado Location |
| GET | `/api/meetings/{uuid}` | 200, datos y versión para editar |
| PUT | `/api/meetings/{uuid}` | 200, cita actualizada |
| GET | `/api/meetings/doctors/{doctorId}?page=0&size=20` | 200, lista paginada de citas asignadas |
| PATCH | `/api/meetings/{uuid}/status` | 200, cambio de estado |

Las operaciones admiten las cuatro autoridades `ROLE_Administrador`, `ROLE_Recepcionista`,
`ROLE_Odontologo` y `ROLE_Paciente`, tanto en controlador como en servicio.
Administrador y recepción gestionan todas las citas. El odontólogo solo gestiona citas asignadas
a él y no puede reasignarlas a otro profesional. El paciente solo crea, consulta o modifica sus
propias citas y no puede marcarlas como completadas.

Para los roles de paciente y odontólogo, `Authentication.getName()` debe ser el correo del usuario
persistido (se normalizan mayúsculas y espacios). El rol autenticado debe coincidir con el almacenado.
Una identidad sin correspondencia recibe 403; nunca se acepta el ID enviado en la solicitud como prueba
de identidad. Este contrato queda listo para el módulo de autenticación; añadir `password_hash` no
implementa un login. El perfil `demo` conserva su administrador en memoria y su configuración previa.
Fuera de ese perfil, se conserva CSRF: obtener el token con `GET /api/csrf` y conservar su cookie.

### Creación

```json
{
  "patientId": 123456789,
  "doctorId": 987654321,
  "chairId": 1,
  "startsAt": "2030-01-10T09:00:00-05:00",
  "endsAt": "2030-01-10T10:00:00-05:00",
  "reason": "Valoración inicial"
}
```

Usar identificadores existentes y fechas futuras reales al probar.
Todos los campos son obligatorios. Los ID de recursos son enteros positivos.
El paciente debe tener rol `Paciente` y el profesional rol `Odontologo`.
El sillón debe existir y estar activo; el motivo admite entre 1 y 500 caracteres.

Las fechas requieren un desplazamiento horario (`-05:00` para Colombia, o `Z` para UTC).
Se comparan y almacenan como instantes; la API responde en UTC.
El inicio debe ser futuro y el fin estrictamente posterior. No se impone una duración fija.

La cita nace en estado `Pendiente`. El UUID generado también se devuelve como
`confirmationCode`: es el código único de referencia, no un token que autorice acciones.

### Edición

Primero consultar la cita y usar su última `version`:

```json
{
  "doctorId": 987654321,
  "chairId": 1,
  "startsAt": "2030-01-10T11:00:00-05:00",
  "endsAt": "2030-01-10T12:00:00-05:00",
  "reason": "Valoración reprogramada",
  "version": 0
}
```

PUT exige todos los campos editables. El ID de la cita y el paciente son inmutables.
Solo se editan citas pendientes o confirmadas que aún no hayan comenzado.
Cambiar horario, odontólogo o sillón devuelve el estado a `Pendiente`; cambiar únicamente
el motivo conserva el estado. El cliente no puede asignar estados mediante estos contratos.
Administración y recepción pueden reprogramar con menos de 24 horas de antelación.
El paciente necesita al menos 24 horas de anticipación respecto al inicio actual para editar
o cancelar, conforme a RN-01; exactamente 24 horas sí se permite. La confirmación no exige esa anticipación.

Si otro operador modifica la cita, se devuelve 409. Volver a consultar y revisar los cambios
antes de reenviar una edición; no sobrescribir la versión automáticamente.

## Disponibilidad, concurrencia y auditoría

Se rechaza cualquier solapamiento de paciente, odontólogo o sillón con citas `Pendiente`
o `Confirmada`, incluso cuando tienen distintas horas de inicio. Se permite que una cita
empiece exactamente cuando termina otra: el intervalo es `[inicio, fin)`.
Las canceladas y completadas no reservan cupos. Al editar, se excluye la propia cita de la búsqueda.

El servicio comprueba disponibilidad y PostgreSQL la refuerza con tres restricciones de
exclusión de rangos. Estas restricciones son necesarias para rechazar reservas simultáneas
que hayan superado al mismo tiempo la comprobación del servicio. No desplegar sin aplicar 003.
La consulta de agenda laboral del odontólogo, horarios de apertura y filtros por especialidad
no se implementan aquí; requieren el modelo de disponibilidad de la tarea correspondiente.

Cada operación registra cita, actor autenticado, acción y fecha/hora UTC en `meeting_audit`.
Se guardan además el estado anterior y el nuevo en las operaciones de cambio.
La cita y su auditoría se guardan en la misma transacción. El motivo no se copia en la bitácora.

Errores de negocio: 400 (horario/datos/roles de recursos inválidos), 404 (cita o recurso inexistente),
409 (solapamiento, versión desactualizada o estado no editable). Los conflictos detectados por
la base también devuelven 409 sin detalles SQL. Seguridad devuelve 401 o 403, según corresponda.

## Preparación de PostgreSQL

No se han ejecutado migraciones sobre la base real. Con `ddl-auto=none`, hay que aplicarlas
antes de usar los endpoints, después de revisar una copia del esquema existente.

1. Haber aplicado `database/001_users.sql`.
2. Revisar y aplicar **una vez** `database/002_meetings_structure.sql`.
3. Registrar los sillones reales en `dental_chair` con sus ID, nombre y estado.
4. Si hay citas antiguas, completar `ends_at`, `chair_id` y `reason` con información verificada.
5. Revisar y resolver solapamientos existentes; aplicar **una vez** `database/003_meetings_constraints.sql`.
6. Para esta actualización, aplicar `database/007_user_passwords_meeting_access.sql`
   después de las migraciones previas del proyecto. Agrega columnas de contraseña y auditoría
   e índices para consultar la agenda; no modifica el estado de las citas existentes.

El mapeo conserva la tabla original sin comillas `meeting` y las columnas `id`, `patientid`,
`doctorid`, `date` y `status`. Si la base utiliza `"Meeting"` o columnas entrecomilladas con
mayúsculas, ajustar SQL y anotaciones primero. Los usuarios se referencian mediante `"User"`.

002 requiere la extensión `btree_gist` en `public`; su instalación puede requerir permisos
del administrador de PostgreSQL. Si ya está instalada en otro esquema, revisar el search_path.
La conversión de una columna `date` sin zona asume que los datos antiguos representan hora
colombiana. Verificar esa suposición y modificar la zona del script si se guardaron en UTC.
Los datos existentes con zona no se reinterpretan.

003 vuelve obligatorios los nuevos campos, valida intervalos y elimina únicamente las restricciones
UNIQUE de una sola columna sobre paciente/odontólogo heredadas de los `@OneToOne` anteriores.
Si hay índices únicos independientes que no sean restricciones, revisarlos por separado.
No inventa duraciones, asignaciones de sillón ni motivos para completar registros antiguos.
Si encuentra datos incompatibles, falla y revierte esa fase.

## Pruebas

Pruebas de servicio, HTTP y permisos de ambos módulos, sin PostgreSQL externa:

```powershell
.\mvnw.cmd '-Dtest=UserServiceTests,UserControllerTests,UserAuthorizationTests,MeetingServiceTests,MeetingControllerTests,MeetingAuthorizationTests' test
```

Pruebas adicionales de persistencia, consulta real y restricciones concurrentes:

```powershell
.\mvnw.cmd '-Dtest=MeetingPostgresTests' test
```

Estas últimas solo se habilitan al configurar `MEETING_TEST_DB_URL` (JDBC),
`MEETING_TEST_DB_USER` y `MEETING_TEST_DB_PASSWORD` hacia una **base PostgreSQL de pruebas**.
Crean y eliminan un esquema temporal exclusivo con nombre aleatorio y aplican los tres scripts.
El usuario de pruebas necesita permisos para crear esquemas y acceso a `btree_gist`.
No usar las credenciales de producción. Si no se configuran esas variables, las pruebas se omiten.

La suite completa se ejecuta con `.\mvnw.cmd test`; `contextLoads` usa H2 mediante el perfil `test`.
El contrato de contraseñas, la identidad por correo y los cambios de estado se explican con ejemplos
en [user-passwords-and-meeting-status.md](user-passwords-and-meeting-status.md).
