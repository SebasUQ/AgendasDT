# Agendas Doc-Tooth — backend

Java 21, Spring Boot, Maven y PostgreSQL. Desarrollo por tareas de Jira.

La gestión de citas con permisos por rol está documentada en
[docs/meetings.md](docs/meetings.md).
La creación, edición y pago de facturas está documentada en [docs/bills.md](docs/bills.md).
Las notificaciones de citas por correo y SMS están documentadas en [docs/notifications.md](docs/notifications.md).
La ampliación de contraseñas, permisos y estados está detallada en
[docs/user-passwords-and-meeting-status.md](docs/user-passwords-and-meeting-status.md).

## Conexión a la base de datos (Supabase)

Crear un archivo `.env` en la raíz del proyecto (está en `.gitignore`; nunca se sube):

```properties
URL=jdbc:postgresql://<host-del-pooler>.pooler.supabase.com:6543/postgres
USER=postgres.<referencia-del-proyecto>
PASS=<contraseña>
```

Sin comillas ni `;` al final de las líneas. La aplicación lo carga automáticamente
(`spring.config.import`); las variables de entorno del sistema con el mismo nombre tienen prioridad.
En macOS/Linux la variable `USER` del sistema ya existe, así que allí se deben exportar los tres valores.

El esquema de Supabase se alineó el 20/10/2026 con las migraciones `001` a `006` (antes tenía las
tablas antiguas `"User"`, `"Meeting"`, `"Bill"`, `"BillDetail"` con columnas entre comillas, que se
reemplazaron estando vacías). `006_supabase_rls.sql` activa RLS en las tablas nuevas para que la API
REST pública de Supabase no las exponga; el backend se conecta como dueño de las tablas y no se ve afectado.

## Módulo de usuarios: creación y edición

Implementa la administración de los datos de usuarios (HU-02, RN-04).
El módulo acepta `password` opcional en creación y edición y guarda únicamente su hash BCrypt
en `password_hash`. Omitirlo al editar conserva el hash anterior; las respuestas nunca incluyen
la contraseña ni su hash. El inicio de sesión con estas credenciales sigue siendo una tarea separada.
Aplicar primero `database/007_user_passwords_meeting_access.sql` para agregar la columna sin
alterar las cuentas existentes. Estas conservan un hash nulo hasta que se les asigne una contraseña.
No se habilita registro público, eliminación ni inactivación en esta entrega.

Capas: `controller` → `service` → `repository`, con contratos en `dto`, entidades en `domain`
y errores de API en `exception`. Las entidades no se exponen directamente como JSON.

### Preparación

1. Configurar un JDK y `JAVA_HOME` (el proyecto compila para Java 21).
2. Configurar las variables `URL` (URL JDBC de PostgreSQL), `USER` y `PASS`.
3. Verificar el esquema existente y aplicar manualmente `database/001_users.sql` en la base de desarrollo.
   La aplicación usa `ddl-auto=none`; el archivo no se ejecuta automáticamente.
4. Ejecutar `./mvnw spring-boot:run` (`.\mvnw.cmd spring-boot:run` en Windows).

El SQL supone una tabla con nombre exacto `"User"` y columnas sin comillas (`id`, `name`,
`lastname`, `birthdate`, `email`, `phone`, `rol`). Si el esquema real usa nombres diferentes,
ajustar el mapeo y el SQL antes de aplicarlo. La base existente no se ha inspeccionado.
La migración conserva registros, convierte teléfonos a texto y agrega versión, índice de correo
y auditoría. Los correos duplicados preexistentes deben resolverse antes de crear el índice;
el script falla y revierte si los encuentra. Revisar primero una copia de la base.

### Autorización e integración con autenticación

Crear, consultar y editar requieren la autoridad `ROLE_Administrador`.
La autorización se aplica en el servicio para proteger también otras llamadas internas.
El rol enviado en el cuerpo identifica al usuario administrado; no concede permisos al solicitante.

Se conserva la autenticación y protección CSRF predeterminadas de Spring Security.
Mientras se desarrolla la autenticación del proyecto, para pruebas locales se puede configurar
el usuario en memoria de Spring mediante `SPRING_SECURITY_USER_NAME`,
`SPRING_SECURITY_USER_PASSWORD` y `SPRING_SECURITY_USER_ROLES=Administrador`.
Esta cuenta local no equivale a las cuentas creadas en PostgreSQL. No usar esta configuración
como autenticación de producción ni guardar credenciales en el repositorio.

En Postman u otro cliente:

1. Configurar HTTP Basic con las credenciales locales.
2. Consultar `GET /api/csrf`; devuelve `headerName` y `token`.
3. Conservar la cookie de sesión recibida y enviar el token en el encabezado indicado para POST y PUT.
   Postman conserva las cookies automáticamente. Un token sin su cookie de sesión no es suficiente.

### API

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/users` | Crea usuario, devuelve 201 y encabezado Location |
| GET | `/api/users/{id}` | Consulta los datos y la versión para editar |
| PUT | `/api/users/{id}` | Reemplaza los datos editables, devuelve 200 |
| GET | `/api/csrf` | Obtiene un token CSRF para la sesión autenticada |

Creación:

```json
{
  "id": 123456789,
  "name": "Ana",
  "lastName": "Perez",
  "birthDate": "1995-05-12",
  "email": "ana@example.com",
  "phone": "+573001234567",
  "rol": "Paciente"
}
```

El ID se recibe como entero positivo, siguiendo el modelo existente, y no se puede cambiar.
Los campos del ejemplo son obligatorios; el campo adicional `password` es opcional.
Nombres: hasta 100 caracteres; correo válido: hasta 254;
teléfono: de 7 a 15 dígitos y prefijo `+` opcional. Se conservan ceros iniciales.
Fecha de nacimiento en formato ISO, anterior a hoy.
Roles admitidos: `Administrador`, `Odontologo`, `Recepcionista`, `Paciente`.

Edición: usar los mismos campos excepto `id` y agregar `"version": 0`,
tomando la versión de la última respuesta de creación o consulta. PUT exige todos los campos;
para conservar un dato hay que volver a enviarlo. Si otro administrador cambia el usuario,
se devuelve 409 y el cliente debe volver a consultar antes de reenviar la edición.

El correo se guarda en minúsculas y los nombres se guardan sin espacios en los extremos.
La búsqueda de duplicados ignora mayúsculas y espacios exteriores de los correos existentes.
Un índice único en PostgreSQL protege contra creaciones/ediciones concurrentes con el mismo correo.

Errores: 400 para datos inválidos, 401 para falta de autenticación, 403 para permisos insuficientes
o CSRF inválido, 404 para usuario inexistente y 409 para duplicados o conflictos de edición.
Los errores de negocio/validación usan ProblemDetail; los errores de seguridad los procesa
la cadena de filtros predeterminada de Spring Security. No se devuelven detalles de SQL.

Cada creación/edición registra en `user_audit` el usuario afectado, actor autenticado, acción,
fecha/hora UTC y rol anterior/nuevo. Los cambios de datos y su auditoría forman una sola transacción.
La bitácora no almacena copias de los datos personales ni acepta actor/fecha desde el cliente.

### Pruebas del módulo

```powershell
.\mvnw.cmd '-Dtest=UserServiceTests,UserControllerTests,UserAuthorizationTests' test
```

Cubren validación HTTP, duplicados, edición, auditoría, versiones obsoletas, autenticación,
CSRF y denegación por rol. No requieren una base externa: usan repositorios simulados.
Para ejecutar toda la suite, usar `.\mvnw.cmd test`. `contextLoads` y la prueba de persistencia
de usuarios/citas usan H2 con el perfil `test`. Las pruebas PostgreSQL se omiten si faltan
sus variables de conexión. Otros módulos todavía generan advertencias UUID/IDENTITY al crear
tablas en H2; esos mapeos anteriores no se han modificado en esta tarea.

Antes de integrar en un entorno compartido, verificar sobre PostgreSQL el esquema real,
el índice único, la persistencia y la reversión de ambas escrituras ante un error de auditoría.
