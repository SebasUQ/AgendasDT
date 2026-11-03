# Autenticación con token (JWT)

Entrega de **SCRUM-15**. El usuario inicia sesión con correo y contraseña y recibe un token firmado.
El cliente lo envía en cada petición con el encabezado `Authorization: Bearer <token>`.
No hay sesiones ni cookies, por eso ya no se usa CSRF ni HTTP Basic (se eliminó `GET /api/csrf`).

## API pública

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/auth/login` | 200 con el token; 401 si el correo o la contraseña no coinciden |
| POST | `/api/auth/register` | 201 con el token; crea siempre una cuenta de **Paciente** |

Todas las demás rutas exigen token: sin token o con uno vencido, alterado o mal formado responden 401,
y con un rol sin permiso responden 403.

### Login

```json
{ "email": "ana@example.com", "password": "Una-clave-segura-1" }
```

Respuesta (formato que usa el frontend en `auth.model.ts`):

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "rol": "Recepcionista", "nombre": "Ana Pérez", "email": "ana@example.com" }
```

- El correo no distingue mayúsculas.
- Correo inexistente, contraseña incorrecta o cuenta sin contraseña reciben el **mismo** mensaje
  ("Correo o contraseña incorrectos."), así no se revela qué correos están registrados.
- Las contraseñas se comparan contra el hash BCrypt guardado por el módulo de usuarios; nunca se devuelven ni se registran.

### Registro de pacientes

```json
{
  "documento": 1094956322,
  "nombre": "Luis",
  "apellido": "Gómez",
  "fechaNacimiento": "2000-03-04",
  "email": "luis@example.com",
  "telefono": "+573001112233",
  "password": "Una-clave-segura-2"
}
```

Mismas reglas que la creación de usuarios: documento positivo y único, correo único, teléfono de 7 a 15
dígitos, fecha anterior a hoy y contraseña de 8 a 72 caracteres. El rol no se puede elegir: siempre es
`Paciente`. Los demás roles los crea un administrador con `POST /api/users` (incluyendo `password`).

> El formulario de registro del frontend tiene un solo campo de nombre completo y no pide apellido ni
> fecha de nacimiento; debe enviarse con los campos de arriba.

## El token

Firmado con HMAC-SHA256 y válido por 8 horas (`security.jwt.expiration`). Contiene:

| Dato | Uso |
| --- | --- |
| `sub` | Correo del usuario; es la identidad que usan citas, facturas y auditoría |
| `rol` | Rol del usuario; se convierte en la autoridad `ROLE_<rol>` |
| `nombre` | Nombre completo para mostrar |
| `uid` | Documento del usuario |
| `exp` | Vencimiento; el frontend cierra la sesión cuando expira |

Si a un usuario se le cambia el rol, el cambio aplica desde su siguiente inicio de sesión.

## Configuración

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `JWT_SECRET` | Sí | Clave para firmar los tokens, mínimo 32 caracteres. La aplicación no arranca sin ella |
| `CORS_ALLOWED_ORIGINS` | No | Direcciones del frontend separadas por comas (por defecto `http://localhost:4200`) |

Cada integrante la agrega a su `.env`. En Azure deben configurarse como *Application settings*,
junto con `URL`, `USER` y `PASS`. Cambiar `JWT_SECRET` invalida todos los tokens emitidos.
Generar una clave nueva, por ejemplo en PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

El perfil `demo` de pruebas con Postman mantiene su propia configuración.

## Primer administrador

Las cuentas creadas antes de esta entrega no tienen contraseña y no pueden iniciar sesión hasta que un
administrador se la asigne con `PUT /api/users/{id}`. Para el primer administrador se registró la cuenta
`admin@doctooth.example` (documento `1000000001`) mediante `/api/auth/register`; para darle el rol hay que
ejecutar una vez en Supabase:

```sql
UPDATE "User" SET rol = 'Administrador', version = version + 1
WHERE id = 1000000001 AND email = 'admin@doctooth.example';
```

## Pruebas

```powershell
.\mvnw.cmd '-Dtest=AuthServiceTests,AuthSecurityTests' test
```

Cubren login, mensajes iguales para errores, cuentas sin contraseña, registro, duplicados, firma y
vencimiento del token, tokens alterados, permisos por rol, ausencia de CSRF y CORS del frontend.
