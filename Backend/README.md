# Backend - RETO SALUD

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="../assets/RETO%20SALUD%20BACKEND-blanco.png">
    <source media="(prefers-color-scheme: light)" srcset="../assets/RETO%20SALUD%20BACKEND.png">
    <img alt="Logo RETO SALUD Backend" src="../assets/RETO%20SALUD%20BACKEND.png" width="520">
  </picture>

  <h3>API REST Empresarial para Gestión Hospitalaria, Telemedicina y Auditoría Clínica</h3>

  [![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
  [![Java](https://img.shields.io/badge/Java-17_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
  [![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
  [![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
  [![Hibernate](https://img.shields.io/badge/Hibernate-ORM_JPA-59666C?style=for-the-badge&logo=hibernate&logoColor=white)](https://hibernate.org/)
  [![JWT](https://img.shields.io/badge/JWT-Stateless_Auth-black?style=for-the-badge&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)

  <br/>

  **[Sitio Web](https://www.reto-salud.online)** • **[Arquitectura](#arquitectura-del-backend)** • **[Stack Tecnológico](#stack-tecnológico)** • **[Estructura de Capas](#estructura-de-capas)** • **[Seguridad y Concurrencia](#seguridad-y-control-de-concurrencia)** • **[Base de Datos](#modelo-de-datos-relacional)** • **[Endpoints](#catálogo-de-endpoints-rest)** • **[Pruebas y Calidad](#calidad-de-software-y-pruebas)** • **[Instalación](#configuración-y-ejecución)** • **[README Principal](../README.md)**

</div>

---

## Descripción General

El **Backend de RETO SALUD** es un servicio empresarial robusto desarrollado sobre el ecosistema **Spring Boot 3** y **Java 17 LTS**. Implementa una arquitectura desacoplada orientada a servicios RESTful con autenticación sin estado (JWT), protección perimetral contra ataques de fuerza bruta, control de concurrencia en la reserva de citas médicas para evitar condiciones de carrera, trazabilidad y conservación estricta de historiales clínicos bajo la normativa de salud, e integraciones en la nube para telemedicina WebRTC, transcripción por voz con IA, triage automatizado con Google Gemini y procesamiento seguro de pagos con Stripe.

---

## Actualizacion de sesiones y pagos

- La revocacion de sesiones requiere la columna nullable `usuarios.token_version` (`BIGINT`). Con la configuracion actual `spring.jpa.hibernate.ddl-auto=update`, Hibernate la agrega al arrancar si dispone de permisos. Si el entorno gestiona el esquema manualmente, aplicar una sola vez, solo si la columna no existe:

```sql
ALTER TABLE usuarios ADD COLUMN token_version BIGINT NULL DEFAULT 0;
```

- Los valores `NULL` y los JWT anteriores sin version equivalen a version cero. Cambiar o restablecer la contrasena incrementa la version y obliga a iniciar sesion de nuevo. Desplegar frontend y backend juntos para incluir la validacion del nonce de Google y el cierre de sesion tras cambiar la contrasena.
- Configurar un `JWT_SECRET` estable y compartido entre replicas. Los limites en memoria son por instancia; tras un proxy, configurar la IP remota mediante proxies de confianza antes de ajustar sus cuotas.
- Las operaciones de pago y cancelacion bloquean la cita. Un error de Stripe no debe anotarse como reembolso exitoso; reintentar la misma operacion permite conciliar el resultado. Los duplicados historicos requieren revision manual. Los tests unitarios no sustituyen una prueba de concurrencia con MySQL y Stripe en modo de pruebas.
- `/api/teleconsulta/config` sin canal ya no entrega credenciales Deepgram. La teleconsulta autorizada conserva su contrato; sigue pendiente sustituir la clave permanente de sus participantes por credenciales temporales. El control publico de voz utiliza Web Speech API y comunica la falta de soporte del navegador.
- Pruebas locales sin arrancar la aplicacion ni contactar proveedores:

```powershell
.\mvnw.cmd "-Dtest=*ServiceTest,CitaPagoBolaTest,CitaIntegridadTest,PagoIntegridadTest,NotificacionIntegridadTest,UsuarioAuthSecurityTest,AuthBoundarySecurityTest,JwtAuthSecurityTest" test
```

## Arquitectura del Backend

El backend centraliza toda la lógica de dominio, la integridad transaccional y la orquestación con proveedores externos:

<div align="center">
  <img src="../assets/arquitectura_backend.png" alt="Arquitectura del Backend RETO SALUD" width="850"/>
</div>

---

## Stack Tecnológico

| Componente | Tecnología | Propósito | Anotaciones / Patrones Clave |
| :--- | :--- | :--- | :--- |
| **Framework Base** | <a href="https://spring.io/projects/spring-boot" target="_blank"><img src="https://skillicons.dev/icons?i=spring" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Spring Boot 3.2**](https://spring.io/projects/spring-boot) | Inversión de control, inyección de dependencias y configuración REST | `@SpringBootApplication`, `@RestController` |
| **Lenguaje de Programación** | <a href="https://openjdk.org/" target="_blank"><img src="https://skillicons.dev/icons?i=java" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Java 17 LTS**](https://openjdk.org/) | Runtime OpenJDK empresarial con Records, Pattern Matching y Streams | Records, DTOs inmutables, Optional |
| **Módulo de Seguridad** | <a href="https://spring.io/projects/spring-security" target="_blank"><img src="https://skillicons.dev/icons?i=spring" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Spring Security 6.x**](https://spring.io/projects/spring-security) | Control de acceso basado en roles (RBAC) y filtros perimetrales | `@EnableWebSecurity`, `@PreAuthorize` |
| **Autenticación** | <a href="https://jwt.io/" target="_blank"><img src="https://cdn.simpleicons.org/jsonwebtokens/D63AFF" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**JWT (JSON Web Tokens)**](https://jwt.io/) | Sesiones sin estado (stateless) firmadas con algoritmo HMAC-SHA256 | `@Component`, `OncePerRequestFilter` |
| **Persistencia / ORM** | <a href="https://hibernate.org/" target="_blank"><img src="https://skillicons.dev/icons?i=hibernate" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Hibernate JPA**](https://hibernate.org/) | Mapeo objeto-relacional, transacciones y control de concurrencia | `@Entity`, `@Repository`, `@Lock`, `@Transactional` |
| **Motor de Base de Datos** | <a href="https://www.mysql.com/" target="_blank"><img src="https://skillicons.dev/icons?i=mysql" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**MySQL 8.0**](https://www.mysql.com/) | Base de datos relacional con índices compuestos y bloqueos de fila | Motor InnoDB, `FOR UPDATE`, Constraints |
| **Procesamiento de Pagos** | <a href="https://stripe.com/" target="_blank"><img src="https://cdn.simpleicons.org/stripe/635BFF" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Stripe Java SDK**](https://stripe.com/) | Creación de Payment Intents y cobros seguros con tarjeta | `PaymentIntentCreateParams`, `@Value` |
| **Teleconsulta WebRTC** | <a href="https://www.agora.io/" target="_blank"><img src="https://cdn.simpleicons.org/agora/099DFD" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Agora RTC SDK**](https://www.agora.io/) | Emisión dinámica de tokens criptográficos temporales de videollamada | `RtcTokenBuilder2`, Privilegios por Canal |
| **Transcripción por Voz** | <a href="https://deepgram.com/" target="_blank"><img src="https://cdn.simpleicons.org/deepgram/13EF93" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Deepgram AI**](https://deepgram.com/) | Generación de credenciales efímeras para subtitulado en vivo | `@Service`, `@Value` |
| **Asistente Virtual** | <a href="https://ai.google.dev/" target="_blank"><img src="https://cdn.simpleicons.org/googlegemini/8E75B2" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Google Gemini API**](https://ai.google.dev/) | Chatbot inteligente (modelo `gemini-2.5-flash`) para triage de consultas | `RestTemplate`, `@PostMapping` |
| **Gestión Multimedia** | <a href="https://cloudinary.com/" target="_blank"><img src="https://cdn.simpleicons.org/cloudinary/3448C5" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Cloudinary SDK**](https://cloudinary.com/) | Almacenamiento y optimización de fotos de perfil en la nube | `@Configuration`, `Cloudinary` Bean |
| **Notificaciones por Correo** | <a href="https://mail.google.com/" target="_blank"><img src="https://cdn.simpleicons.org/gmail/EA4335" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Gmail SMTP**](https://mail.google.com/) | Envío de correos transaccionales (confirmación, reprogramación, claves) | `JavaMailSender`, `MimeMessageHelper` |

---

## Estructura de Capas

El backend se organiza bajo una arquitectura limpia por capas (**Layered Architecture**):

<div align="center">
  <img src="../assets/estructura_backend.png" alt="Estructura de Capas del Backend" width="850"/>
</div>

### 1. Capa de Controladores (`controller/`)
* **Controladores REST:** Exponen las rutas protegidas por roles (`@PreAuthorize`) y procesan solicitudes HTTP.
* **Transferencia de Datos (`dto/`):** Objetos de entrada y salida (`UsuarioRegistroRequest`, `PaymentIntentRequest`, `PagoResponse`, etc.) que encapsulan los datos e impiden la sobreexposición de entidades.

### 2. Capa de Negocio (`service/`)
* **Lógica del Dominio:** Reglas para agendamiento de citas, flujos de reprogramación y cancelación, generación de comprobantes y validación de horarios médicos.
* **Manejo Transaccional (`@Transactional`):** Garantiza atomicidad en operaciones de reserva de citas, actualización de disponibilidades y registros de pagos.

### 3. Capa de Acceso a Datos (`repository/` & `model/`)
* **Entidades de Base de Datos (`model/`):** Mapeo de tablas con soporte de enums nativos (`ModalidadCita`, `EstadoCita`, `EstadoPago`, `Rol`).
* **Repositorios JPA (`repository/`):** Interfaces con consultas personalizadas `@Query` y bloqueos pesimistas `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

### 4. Capa Transversal (`security/`, `config/`, `exception/`)
* **Seguridad Perimetral (`security/`):** Filtro `JwtAuthenticationFilter`, utilidad `JwtUtil` y configuración `SecurityConfig`.
* **Manejador Global de Excepciones (`exception/`):** `@RestControllerAdvice` que captura `HorarioNoDisponibleException` (HTTP 409 Conflict), errores de autenticación (HTTP 401) y validaciones (HTTP 400).
* **Configuración del Sistema (`config/`):** Definición de beans de infraestructura y configuración de CORS.

---

## Seguridad y Control de Concurrencia

```
                                      FLUJO DE CONCURRENCIA EN CITAS
               Paciente A (10:00:00.100) ----\
                                              ===> [ @Lock(PESSIMISTIC_WRITE) ] ===> 1° Reserva Exitosa (HTTP 201)
               Paciente B (10:00:00.120) ----/               |
                                                             v
                                                  HorarioNoDisponibleException ====> 2° Conflicto Evitado (HTTP 409)
```

1. **Control de Concurrencia con Bloqueo Pesimista (`PESSIMISTIC_WRITE`):**
   * En `DisponibilidadRepository`, la consulta `findByIdConBloqueo()` ejecuta una instrucción SQL `SELECT ... FOR UPDATE` a nivel de fila sobre el motor MySQL InnoDB.
   * Si dos pacientes intentan agendar el mismo horario simultáneamente, la base de datos bloquea el registro para la primera transacción. Al confirmarse, el segundo intento detecta que el estado ya no es `DISPONIBLE` y lanza inmediatamente `HorarioNoDisponibleException`.

2. **Excepción de Negocio `HorarioNoDisponibleException` (HTTP 409 Conflict):**
   * Mapeada a través del manejador global de errores para emitir una respuesta JSON estándar con código `409 Conflict` (`"El horario seleccionado ya no se encuentra disponible"`), protegiendo la consistencia transaccional sin caídas del servicio.

3. **Liberación Automática de Horarios de Checkout Abandonados:**
   * Endpoint `DELETE /api/citas/{id}/abandonar` que restaura el estado del bloque de disponibilidad a `DISPONIBLE` si el paciente cancela o abandona la pasarela de pago.

4. **Auditoría Clínica Estricta (Ley N° 29733 de Protección de Datos Personales en Salud):**
   * Llaves foráneas con regla `ON DELETE RESTRICT` en las tablas `citas`, `historial` y `pagos`.
   * Prohíbe legalmente el borrado físico de usuarios o citas que cuenten con recetas médicas, notas de evolución o registros de pago emitidos.

5. **Protección Anti-Fuerza Bruta en Autenticación:**
   * `AuthController` implementa un control en memoria mediante `ConcurrentHashMap` que cuenta los intentos fallidos de inicio de sesión.
   * Al alcanzar **5 intentos fallidos consecutivos**, la cuenta entra en bloqueo temporal automático durante **3 minutos**, registrando un evento de seguridad y retornando HTTP 401 con el tiempo exacto restante.

6. **Índices Compuestos de Alta Velocidad:**
   * `idx_disp_medico_fecha_estado` en `disponibilidades`: Optimiza la búsqueda de horarios filtrando de forma simultánea por médico, fecha y estado disponible.
   * `idx_citas_medico_fecha` en `citas`: Acelera la verificación de agenda médica y previene solapamientos.

7. **Restricción de Unicidad Física:**
   * Restricción `uq_disp_bloque UNIQUE (medico_id, fecha, hora_inicio)` en `disponibilidades`, impidiendo a nivel de base de datos la existencia de bloques horarios idénticos para el mismo especialista.

---

## Modelo de Datos Relacional

La base de datos MySQL 8 cuenta con 12 tablas con integridad referencial, enums tipados e índices compuestos:

| Tabla | Propósito Principal | Columnas Clave | Índices y Restricciones Físicas |
| :--- | :--- | :--- | :--- |
| **`paises`** | Catálogo internacional de países y códigos telefónicos | `id`, `nombre`, `prefijo_telefono`, `estado` | `PRIMARY KEY (id)` |
| **`especialidades`** | Especialidades médicas y costos base de atención | `id`, `nombre`, `precio_base`, `descripcion`, `estado` | `PRIMARY KEY (id)` |
| **`usuarios`** | Entidad de usuarios con autenticación, rol y perfil | `id`, `correo`, `contrasena`, `dni`, `rol`, `estado`, `pais_id`, `token_recuperacion`, `configuracion_visual`, `foto_url` | `PRIMARY KEY (id)`, `UNIQUE (correo)`, `UNIQUE (dni)`, `FK (pais_id) REFERENCES paises(id)` |
| **`medicos`** | Extensión de perfil para médicos colegiados | `id`, `usuario_id`, `especialidad_id`, `estado` | `PRIMARY KEY (id)`, `UNIQUE (usuario_id)`, `FK (usuario_id) ON DELETE CASCADE`, `FK (especialidad_id)` |
| **`medico_especialidades`** | Tabla puente para médicos con múltiples especialidades | `usuario_id`, `especialidad_id` | `PRIMARY KEY (usuario_id, especialidad_id)`, `FK (usuario_id) ON DELETE CASCADE`, `FK (especialidad_id) ON DELETE CASCADE` |
| **`disponibilidades`** | Bloques de horario abiertos por médicos para atención | `id`, `medico_id`, `fecha`, `hora_inicio`, `hora_fin`, `estado` (`DISPONIBLE`, `NO_DISPONIBLE`) | `PRIMARY KEY (id)`, `CONSTRAINT uq_disp_bloque UNIQUE (medico_id, fecha, hora_inicio)`, `INDEX idx_disp_medico_fecha_estado (medico_id, fecha, estado)`, `FK (medico_id) ON DELETE CASCADE` |
| **`citas`** | Gestión de citas médicas, estado y modalidad | `id`, `paciente_id`, `medico_id`, `fecha`, `modalidad` (`PRESENCIAL`, `VIRTUAL`), `estado` (flujo completo con solicitudes y confirmaciones), `fecha_propuesta`, `motivo` | `PRIMARY KEY (id)`, `INDEX idx_citas_medico_fecha (medico_id, fecha)`, `FK (paciente_id) REFERENCES usuarios(id) ON DELETE RESTRICT`, `FK (medico_id) REFERENCES medicos(id) ON DELETE RESTRICT` |
| **`historial`** | Ficha clínica, diagnóstico y prescripción médica | `id`, `cita_id`, `diagnostico`, `receta`, `notas`, `fecha_registro` | `PRIMARY KEY (id)`, `UNIQUE (cita_id)`, `FK (cita_id) REFERENCES citas(id) ON DELETE RESTRICT` (Ley N° 29733) |
| **`pagos`** | Transacciones procesadas (Stripe o Efectivo) | `id`, `cita_id`, `usuario_id`, `monto`, `metodo` (`TARJETA`, `PAYPAL`, `EFECTIVO`, `TRANSFERENCIA`), `estado_pago` (`PENDIENTE`, `COMPLETADO`, `FALLIDO`, `REEMBOLSADO`), `transaccion_id` | `PRIMARY KEY (id)`, `FK (cita_id) REFERENCES citas(id) ON DELETE RESTRICT`, `FK (usuario_id) REFERENCES usuarios(id) ON DELETE RESTRICT` |
| **`comprobantes`** | Boletas y comprobantes electrónicos generados | `id`, `pago_id`, `numero`, `fecha`, `archivo_url` | `PRIMARY KEY (id)`, `UNIQUE (pago_id)`, `UNIQUE (numero)`, `FK (pago_id) REFERENCES pagos(id) ON DELETE CASCADE` |
| **`notificaciones`** | Avisos y alertas del sistema dirigidas por rol o usuario | `id`, `usuario_destino_id`, `rol_destino`, `titulo`, `mensaje`, `leida` | `PRIMARY KEY (id)`, `FK (usuario_destino_id) REFERENCES usuarios(id) ON DELETE CASCADE` |
| **`solicitudes_empleo`** | Postulaciones de profesionales médicos o recepcionistas | `id`, `nombre`, `apellido`, `correo`, `dni`, `telefono`, `puesto`, `estado`, `mensaje` | `PRIMARY KEY (id)` |

---

## Catálogo de Endpoints REST

Rutas oficiales implementadas en los controladores del backend:

### 1. Autenticación y Cuentas (`/auth/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/auth/login` | `POST` | Inicia sesión con correo y contraseña. Bloqueo tras 5 intentos fallidos | Público |
| `/auth/registro` | `POST` | Registra una nueva cuenta de paciente y emite token JWT | Público |
| `/auth/google` | `POST` | Autenticación con Google Sign-In mediante Google OAuth 2.0 | Público |
| `/auth/recuperar` | `POST` | Genera token y envía correo SMTP para restablecimiento de contraseña | Público |
| `/auth/restablecer` | `POST` | Valida token de seguridad y actualiza la contraseña del usuario | Público |

### 2. Gestión de Citas Médicas (`/api/citas/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/citas` | `POST` | Registra una cita con modalidad `PRESENCIAL` o `VIRTUAL` | Autenticado |
| `/api/citas/usuario/{id}` | `GET` | Obtiene el historial de citas del paciente | Autenticado |
| `/api/citas/medico/{id}` | `GET` | Obtiene la agenda de citas programadas del médico | `MEDICO`, `ADMIN` |
| `/api/citas/{id}/abandonar` | `DELETE` | Cancela reserva en proceso de checkout y libera el horario inmediatamente | `PACIENTE` |
| `/api/citas/{id}/confirmar` | `PUT` | Confirma la atención de una cita médica | `ADMIN`, `RECEPCION` |
| `/api/citas/{id}/cancelar` | `PUT` | Cancela directamente una cita agendada | `ADMIN`, `RECEPCION` |
| `/api/citas/{id}/solicitar-reprogramar` | `PUT` | Paciente solicita cambio de fecha proponiendo un nuevo horario | `PACIENTE` |
| `/api/citas/{id}/confirmar-reprogramar` | `PUT` | Médico o recepción aprueba la reprogramación solicitada | `MEDICO`, `RECEPCION`, `ADMIN` |
| `/api/citas/{id}/rechazar-reprogramar` | `PUT` | Rechaza la propuesta de reprogramación | `MEDICO`, `RECEPCION`, `ADMIN` |
| `/api/citas/{id}/solicitar-cancelar` | `PUT` | Paciente solicita anulación de cita con motivo | `PACIENTE` |
| `/api/citas/{id}/confirmar-cancelar` | `PUT` | Aprueba la cancelación de la cita e inicia el reembolso | `ADMIN`, `RECEPCION` |

### 3. Disponibilidad Médica (`/api/disponibilidades/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/disponibilidades/medico/{id}` | `GET` | Lista todos los bloques horarios configurados por el médico | Autenticado |
| `/api/disponibilidades/medico/{id}/rango` | `GET` | Filtra horarios disponibles por rango de fechas (`inicio`, `fin`) | Autenticado |
| `/api/disponibilidades/medico/{id}` | `POST` | Crea nuevos bloques de atención con control de unicidad (`uq_disp_bloque`) | `MEDICO`, `ADMIN` |
| `/api/disponibilidades/medico/{id}/rango` | `DELETE` | Elimina bloques de horarios en un rango de fechas | `MEDICO`, `ADMIN` |

### 4. Telemedicina y Subtítulos en Vivo (`/api/teleconsulta/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/teleconsulta/config` | `GET` | Genera token dinámico RTC de Agora y provee clave efímera de Deepgram | `PACIENTE`, `MEDICO` |

### 5. Pasarela de Pagos y Facturación (`/api/pagos/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/pagos/crear-intent` | `POST` | Crea un `PaymentIntent` en Stripe para pagos con tarjeta | `PACIENTE` |
| `/api/pagos/tarjeta` | `POST` | Registra el pago completado tras el cobro exitoso en Stripe | `PACIENTE` |
| `/api/pagos/promesa-efectivo` | `POST` | Registra intención de pago en efectivo en sede presencial | `PACIENTE` |
| `/api/pagos/efectivo` | `POST` | Cobra y emite comprobante en recepción física | `RECEPCION`, `ADMIN` |
| `/api/pagos/completar/{id}` | `PUT` | Valida y finaliza un pago físico pendiente | `RECEPCION`, `ADMIN` |
| `/api/pagos/cita/{citaId}` | `GET` | Obtiene el estado y comprobantes de pago asociados a una cita | Autenticado |

### 6. Historial Clínico y Recetas (`/api/historiales/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/historiales/{citaId}` | `POST` | Emite diagnóstico, receta médica y notas de evolución | `MEDICO` |
| `/api/historiales/cita/{citaId}` | `GET` | Consulta la ficha clínica vinculada a la cita | Autenticado |
| `/api/historiales/paciente/{pacienteId}` | `GET` | Lista el historial clínico completo del paciente | `MEDICO`, `PACIENTE`, `ADMIN` |

### 7. Asistente Virtual con IA (`/api/chatbot/**`)

| Endpoint | Método | Descripción | Nivel de Acceso |
| :--- | :---: | :--- | :--- |
| `/api/chatbot/preguntar` | `POST` | Consulta médica y administrativa al asistente inteligente (`gemini-2.5-flash`) | Público / Autenticado |

---

## Calidad de Software y Pruebas

El backend cuenta con una suite integral de aseguramiento de calidad (QA) que combina pruebas unitarias de lógica de negocio, pruebas funcionales automatizadas punta a punta y análisis estático continuo:

### 1. Matriz de Pruebas del Backend

| Dimensión de Prueba | Framework / Herramienta | Ubicación / Paquete | Alcance y Propósito |
| :--- | :--- | :--- | :--- |
| **Pruebas Unitarias** | <a href="https://junit.org/junit5/" target="_blank"><img src="https://cdn.simpleicons.org/junit5/25A162" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**JUnit 5**](https://junit.org/junit5/) + [**Mockito**](https://site.mockito.org/) | `src/test/.../service/` | Validación aislada de lógica de negocio en `EspecialidadService`, `EstadoCitaService`, `RolService`, estados de médicos y usuarios. |
| **Pruebas E2E y Funcionales** | <a href="https://www.selenium.dev/" target="_blank"><img src="https://cdn.simpleicons.org/selenium/43B02A" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Selenium WebDriver**](https://www.selenium.dev/) | `src/test/.../selenium/` | Automatización de flujos completos en navegador: login, registro de pacientes, creación de citas, reprogramación y cancelación. |
| **Análisis Estático y Deuda Técnica** | <a href="https://www.sonarsource.com/products/sonarqube/" target="_blank"><img src="https://cdn.simpleicons.org/sonar/4E9BCD" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**SonarQube**](https://www.sonarsource.com/products/sonarqube/) | `pom.xml` (SonarScanner) | Inspección continua del código fuente Java para detección de Bugs, Code Smells, Hotspots y cumplimiento de Quality Gates. |

### 2. Comandos para Ejecución de Pruebas

* **Ejecutar toda la suite de pruebas del backend:**
  ```bash
  ./mvnw test
  ```

* **Ejecutar únicamente las pruebas unitarias de servicios:**
  ```bash
  ./mvnw test -Dtest=*ServiceTest
  ```

* **Ejecutar las pruebas automatizadas de Selenium:**
  ```bash
  ./mvnw test -Dtest=*SeleniumTest
  ```

* **Ejecutar análisis estático con SonarQube:**
  ```bash
  ./mvnw clean verify sonar:sonar
  ```

---

## Configuración y Ejecución

### Prerrequisitos

* **Java JDK 17 LTS** o superior (`java -version`)
* **MySQL 8.0** o superior en ejecución (`mysql -u root -p`)
* **Maven 3.8+** (o utilizar el wrapper `./mvnw` incluido)

### 1. Inicializar la Base de Datos

1. Ingresar a MySQL y crear la base de datos:
   ```sql
   CREATE DATABASE IF NOT EXISTS db_reto_salud CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE db_reto_salud;
   ```

2. Ejecutar el script completo con la estructura oficial [`reto_salud.sql`](./reto_salud.sql):
   ```bash
   mysql -u root -p db_reto_salud < reto_salud.sql
   ```

### 2. Configurar Variables de Entorno

Crear o modificar el archivo `src/main/resources/application.properties` con las credenciales correspondientes:

```properties
# ==============================================================================
# CONFIGURACIÓN GENERAL DEL SERVIDOR
# ==============================================================================
server.port=8080
spring.application.name=backend_citas

# ==============================================================================
# BASE DE DATOS (MySQL 8)
# ==============================================================================
spring.datasource.url=jdbc:mysql://localhost:3306/db_reto_salud?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=tu_password_aqui
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Hibernate JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# ==============================================================================
# SEGURIDAD Y JWT (JSON Web Tokens)
# ==============================================================================
jwt.secret=tu_clave_secreta_super_segura_de_minimo_256_bits_aqui
jwt.expirationMs=86400000

# ==============================================================================
# CORREOS ELECTRÓNICOS (Gmail SMTP)
# ==============================================================================
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu_correo@gmail.com
spring.mail.password=tu_contraseña_de_aplicacion_google
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# ==============================================================================
# PASARELA DE PAGOS (Stripe)
# ==============================================================================
stripe.secret-key=sk_test_tu_clave_secreta_stripe_aqui

# ==============================================================================
# CDN Y GESTIÓN DE IMÁGENES (Cloudinary)
# ==============================================================================
cloudinary.cloud-name=tu_cloud_name
cloudinary.api-key=tu_cloudinary_api_key
cloudinary.api-secret=tu_cloudinary_api_secret

# ==============================================================================
# TELEMEDICINA Y ACCESIBILIDAD (Agora & Deepgram)
# ==============================================================================
agora.appId=tu_agora_app_id
agora.appCertificate=tu_agora_certificate
deepgram.apiKey=tu_deepgram_api_key

# ==============================================================================
# ASISTENTE VIRTUAL IA (Google Gemini)
# ==============================================================================
GEMINI_API_KEY=tu_gemini_api_key
gemini.api.model=gemini-2.5-flash

# ==============================================================================
# AUTENTICACIÓN GOOGLE OAUTH 2.0
# ==============================================================================
google.client.id=tu_google_client_id.apps.googleusercontent.com
```

### 3. Compilación y Ejecución

* **En entornos Linux / macOS:**
  ```bash
  ./mvnw clean install
  ./mvnw spring-boot:run
  ```

* **En entornos Windows (CMD / PowerShell):**
  ```powershell
  .\mvnw.cmd clean install
  .\mvnw.cmd spring-boot:run
  ```

* La API REST estará lista y escuchando en:
  ```
  http://localhost:8080
  ```

---

<div align="center">
  <sub>Desarrollado para la plataforma <b>RETO SALUD</b> • Consulta el <a href="../README.md">README General del Proyecto</a></sub>
</div>
