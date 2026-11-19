# Backend del odontólogo

Implementa el flujo de atención del odontólogo y los contratos para integrarlo con el frontend.
Usa la autenticación JWT existente (`Authorization: Bearer <token>`) y la autoridad `ROLE_Odontologo`.
El nombre autenticado debe ser el correo del usuario persistido con rol `Odontologo`.
La API determina el profesional a partir del token; no recibe un `doctorId` para decidir permisos.

## Preparación

1. Aplicar las migraciones previas y después **una vez** `database/008_doctor_clinical_module.sql`.
   No se modificó la base real durante el desarrollo.
2. Iniciar sesión con una cuenta de odontólogo y completar su registro profesional mediante el perfil.
3. Registrar el tarifario institucional y sus kits en PostgreSQL con los precios, vigencias e insumos reales.
   El catálogo no contiene precios de demostración ni permite al odontólogo enviarlos desde el cliente.
4. Para finalizar una atención, la cita debe estar confirmada y haber alcanzado su hora de inicio.

La migración agrega tablas clínicas, auditoría, perfil profesional y tarifario. Añade a `bill`
el indicador `clinical_order` y a `billdetail` la referencia opcional `procedure_id`.
Las facturas anteriores conservan su comportamiento y datos.
Activa RLS sin políticas públicas en las tablas nuevas, siguiendo la configuración existente de Supabase.
El backend debe seguir usando su conexión de propietario; un rol restringido requiere permisos y políticas propios.

Los modelos antiguos `ClinicalHistory`, `Treatment` y `TDetail` quedan intactos.
Este flujo guarda las nuevas atenciones en `clinical_record`; no inventa médico, fecha o cita
para migrar registros antiguos incompletos. Si existen datos clínicos en las tablas antiguas,
su migración debe prepararse con esos datos verificados antes de usarlo como historial consolidado.

## Rutas

Prefijo: `/api/doctor`.

| Método | Ruta | Función |
| --- | --- | --- |
| GET | `/profile` | Usuario y perfil profesional propio |
| PUT | `/profile` | Registro profesional y especialidad propia |
| GET | `/agenda?date=2026-11-18&status=Confirmada&page=0&size=20` | Agenda del día, solo del odontólogo autenticado |
| GET | `/patients?search=María&page=0&size=20` | Pacientes asignados, búsqueda por nombre, apellido o documento |
| GET | `/patients/{patientId}/history` | Datos del paciente e historia clínica cronológica paginada |
| GET | `/patients/{patientId}/odontogram` | Estado dental consolidado por pieza y cara |
| POST | `/meetings/{meetingId}/records` | Finaliza la evolución y completa la cita |
| GET | `/records/{id}` | Consulta una evolución inmutable |
| POST | `/records/{id}/addenda` | Añade una corrección sin modificar la evolución |
| GET | `/records/{id}/addenda` | Lista paginada de adendas |
| POST | `/records/{id}/prescriptions` | Emite una prescripción o su corrección |
| GET | `/records/{id}/prescriptions` | Lista paginada de recetas y sus referencias de sustitución |
| GET | `/prescriptions/{id}` | Consulta una receta |
| GET | `/prescriptions/{id}/pdf` | Descarga PDF |
| GET | `/procedures` | Tarifario vigente y kits asociados |
| POST | `/meetings/{meetingId}/charge-order` | Emite una orden de cobro pendiente en caja |
| GET | `/meetings/{meetingId}/charge-order` | Consulta la orden y sus insumos planificados |

Se conservan las rutas de citas existentes para consultar, editar y cambiar el estado de las citas asignadas.

## Agenda, pacientes y permisos

La agenda usa fechas ISO. Si se omite `date`, muestra el día actual de `America/Bogota`.
El rango es desde el inicio del día hasta el siguiente, excluyendo el límite superior.
Si se omite `status`, incluye los cuatro estados existentes. Los horarios se devuelven en UTC.

Las listas usan `{content, page, size, totalElements, totalPages}`; `page` inicia en 0.
Agenda, pacientes y tarifario permiten `size` entre 1 y 100. Historia, adendas y recetas permiten entre 1 y 50.
La historia se ordena de más reciente a más antigua. Todas las rutas del módulo responden con `Cache-Control: no-store`.

Un paciente se considera asignado cuando existe una cita no cancelada entre él y el odontólogo,
incluyendo atenciones completadas. El odontólogo puede revisar el historial de sus pacientes,
también las evoluciones previas escritas por otros profesionales.
Solo el autor original puede agregar adendas y recetas a una evolución.
Recepción, pacientes y administradores no tienen acceso a estas rutas clínicas.

## Perfil profesional

`GET /profile` devuelve el usuario y, si aún no se ha completado, datos profesionales nulos.
Para guardar:

```json
{
  "professionalRegistration": "REGISTRO-DEL-PROFESIONAL",
  "specialty": "Odontología general",
  "version": null
}
```

El registro es obligatorio y admite hasta 100 caracteres; la especialidad es opcional, hasta 150.
En ediciones se puede enviar la última versión para detectar cambios concurrentes.
El número se registra tal como lo declara el profesional; no se implementa una verificación externa de credenciales.
Su nombre y registro se copian a los documentos emitidos para conservar la información histórica.

## Finalización de consulta

Ejemplo de `POST /meetings/{meetingId}/records`:

```json
{
  "meetingVersion": 0,
  "reason": "Motivo consignado por el odontólogo",
  "findings": "Hallazgos de la atención",
  "diagnosis": "Diagnóstico documentado",
  "performedProcedure": "Procedimiento realizado",
  "plan": "Plan e indicaciones de seguimiento",
  "medicalBackground": "Antecedentes documentados",
  "allergies": "Alergias documentadas o ausencia verificada",
  "chronicConditions": "Condiciones crónicas documentadas",
  "teeth": [
    {"tooth": 11, "surface": "Vestibular", "type": "Patologia", "description": "Hallazgo dental"},
    {"tooth": 55, "surface": "Oclusal", "type": "TratamientoRealizado", "description": "Tratamiento registrado"}
  ]
}
```

Los primeros seis campos y `teeth` son obligatorios; `teeth` puede ser una lista vacía.
Motivo y diagnóstico: hasta 2000 caracteres; hallazgos, procedimiento y plan: hasta 4000.
Los antecedentes, alergias y condiciones son opcionales y se conservan como parte de cada evolución.
Un valor vacío o ausente no significa que se haya verificado ausencia de alergias.

La cita se bloquea durante la operación; se comprueba autor, estado y versión.
Solo se permite una evolución finalizada por cita. La evolución, odontograma, cambio a `Completada`
y auditoría se guardan en una transacción. El frontend debe volver a consultar la cita tras finalizarla
para obtener su estado y versión actualizados.

No existen PUT, PATCH o DELETE de evoluciones, adendas ni recetas.
Hibernate trata esos registros como inmutables y la migración PostgreSQL agrega triggers que rechazan UPDATE y DELETE,
también en los detalles y en la bitácora clínica. Las consultas de historia, documentos y odontograma quedan auditadas.

### Odontograma FDI

Se admiten 32 piezas permanentes: cuadrantes 1-4 con posiciones 1-8;
y 20 temporales: cuadrantes 5-8 con posiciones 1-5. Ejemplos válidos: 11, 48, 55, 85.
Piezas como 19, 50 o 99 son inválidas. El registro puede contener ambas denticiones.

Caras: `Vestibular`, `Lingual`, `Mesial`, `Distal`, `Oclusal`.
Tipos: `Patologia` (respuesta `color: Rojo`) y `TratamientoRealizado` (`color: Azul`).
No se puede repetir la misma pieza/cara dentro de una evolución. El máximo es 260 observaciones.
La vista consolidada conserva el último registro de cada pieza/cara y devuelve su evolución y fecha de origen.
El historial conserva todas las marcas anteriores, sin sobrescribirlas.

### Adendas

```json
{
  "reason": "Motivo de la aclaración",
  "text": "Texto de la corrección o información adicional"
}
```

Motivo obligatorio, hasta 1000 caracteres; texto obligatorio, hasta 8000.
La corrección se presenta junto al documento original y tiene autor y fecha propios.
No reescribe campos originales ni recalcula automáticamente las observaciones estructuradas del odontograma.
Los nuevos hallazgos estructurados se registran en una evolución de una nueva atención.

## Prescripciones y PDF

`POST /records/{id}/prescriptions`:

```json
{
  "items": [
    {
      "medicine": "Medicamento indicado por el profesional",
      "dose": "Dosis consignada por el profesional",
      "frequency": "Frecuencia consignada",
      "duration": "Duración consignada",
      "instructions": "Indicaciones adicionales"
    }
  ]
}
```

Entre 1 y 30 medicamentos por receta. Los primeros cuatro campos son obligatorios y admiten
hasta 200 caracteres cada uno; las instrucciones son opcionales y admiten hasta 1000.
El backend documenta lo indicado por el odontólogo; no calcula tratamientos ni recomienda dosis.

Para corregir una receta, emitir otra con `supersedesId` y `correctionReason` obligatorio.
La anterior debe pertenecer a la misma evolución y no tener ya una corrección.
La respuesta incluye `supersededById` si una receta fue reemplazada, y el PDF anterior muestra esa advertencia.
El original se conserva y la corrección no altera su fecha, nombre ni registro profesional.

El PDF se entrega como archivo adjunto, incluye marca textual Doc-Tooth, paciente/documento,
profesional/registro, fecha colombiana, medicamentos e indicaciones, y pagina textos extensos.
Utiliza una tipografía embebida para conservar tildes. Símbolos que la tipografía no soporte devuelven 400.
Incluye un espacio para firma del profesional: no genera una firma digital ni simula una firma manuscrita.

## Orden de cobro y kits

Primero consultar `/procedures`. Luego, con la cita ya completada y su evolución guardada:

```json
{
  "lines": [
    {"procedureId": "UUID-DEL-PROCEDIMIENTO-VIGENTE", "quantity": 1}
  ]
}
```

Entre 1 y 50 líneas; cantidades enteras entre 1 y 1000. El cliente no envía precios, descuentos ni totales.
Se bloquean las tarifas utilizadas para crear una fotografía consistente de precios e insumos.
Un procedimiento debe estar activo, haber iniciado su vigencia y no estar vencido.
El catálogo y sus kits se parametrizan administrativamente; el odontólogo solo los consulta y selecciona.

La respuesta contiene `bill` (contrato de factura existente) y `supplies` (código, nombre, unidad y unidades).
La orden se representa como una factura `Pendiente` con `clinical_order=true`, disponible en las rutas
de caja existentes para consultar y pagar. Solo puede existir una orden/factura por cita.
Las líneas copian el precio vigente y el ID del procedimiento; los kits se multiplican por la cantidad
y se guardan como insumos planificados en `clinical_bill_supply`.
Caja puede pagar la orden, pero no cambiar sus líneas o precios mediante la edición genérica de facturas.

Los kits conservan la asociación clínica de insumos. Esta entrega no agrega lotes, vencimientos ni
descuento FIFO/FEFO de stock al pagar: esa parte requiere integrar el módulo de inventario.
Tampoco modifica cuotas, abonos o cierres de caja, que pertenecen a sus módulos correspondientes.

## Errores y verificación

400: campos, piezas o cantidades inválidas, o registro profesional incompleto.
401: token ausente o inválido. 403: rol, paciente o autor no autorizado.
404: documento/recurso inexistente. 409: versión, estado, duplicados o tarifa sin vigencia.

Ejecutar `.\mvnw.cmd test` con las variables de configuración de pruebas que requiere la autenticación.
Si hace falta para el entorno local, usar `CORS_ALLOWED_ORIGINS=http://localhost:3000`.
Las pruebas nuevas comprueban permisos, persistencia H2, finalización, inmutabilidad, adendas,
prescripciones, PDF Unicode/paginación, precios del servidor, kits y pago por recepción.
Las pruebas PostgreSQL son optativas; requieren sus variables de conexión a una base de pruebas.
Los mapeos antiguos ajenos al nuevo flujo todavía generan advertencias UUID/IDENTITY al crear tablas en H2.

Se ajustaron dos datos simulados de `AuthSecurityTests` al constructor actual de seis campos de `AuthResponse`
para que la suite completa compile; no se cambió la autenticación del proyecto.
