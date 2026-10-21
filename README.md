<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/RETOSALUD-blanco.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/RETOSALUD.png">
  <img alt="Logo RETO SALUD" src="./assets/RETOSALUD.png" width="520">
</picture>

# RETO SALUD
### Plataforma Integral de Gestión de Citas Médicas, Telemedicina y Auditoría Clínica

[![Angular](https://img.shields.io/badge/Angular-19.1-DD0031?style=for-the-badge&logo=angular&logoColor=white)](https://angular.dev/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.5-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Railway](https://img.shields.io/badge/Railway-Cloud_Deploy-0B0D0E?style=for-the-badge&logo=railway&logoColor=white)](https://railway.app/)

<br/>

**[Sitio Web Oficial](https://www.reto-salud.online)** • **[Rendimiento](#rendimiento-y-auditoría-lighthouse)** • **[Arquitectura](#arquitectura-general)** • **[Stack Tecnológico](#stack-tecnológico)** • **[Roles](#roles-y-funcionalidades)** • **[Instalación](#instalación-y-configuración)** • **[Documentación](#documentación-adicional)**

</div>

---

## Rendimiento y Auditoría (Lighthouse)

<div align="center">
  <p>Análisis en tiempo real de <b><a href="https://www.reto-salud.online/" target="_blank">RETO Salud</a></b></p>
  <img src="./assets/pagespeed.svg?v=1" alt="Métricas PageSpeed RETO Salud" />
</div>

---

## Arquitectura General

La arquitectura de **RETO SALUD** está diseñada bajo un enfoque moderno y desacoplado **Cliente-Servidor (SPA + REST API)**. Se divide en dos capas principales que se comunican mediante una API REST en formato JSON, integradas con servicios especializados en la nube para telemedicina, inteligencia artificial, mapas y pagos.

<div align="center">
  <img src="./assets/arquitectura.png" alt="Arquitectura General RETO SALUD" width="850">
</div>

---

## Stack Tecnológico

| Capa | Tecnología | Propósito |
| :--- | :--- | :--- |
| **Frontend** | <img src="https://skillicons.dev/icons?i=angular" width="22" height="22" align="center" /> **Angular 19** | Framework SPA reactivo y modular para la interfaz de usuario |
| **Lenguaje Frontend** | <img src="https://skillicons.dev/icons?i=ts" width="22" height="22" align="center" /> **TypeScript 5.5** | Tipado estático y escalabilidad en lógica de cliente |
| **Backend** | <img src="https://skillicons.dev/icons?i=spring" width="22" height="22" align="center" /> **Spring Boot 3.2** | Framework REST API, seguridad e inyección de dependencias |
| **Runtime Backend** | <img src="https://skillicons.dev/icons?i=java" width="22" height="22" align="center" /> **Java 17 LTS** | Runtime OpenJDK con alto rendimiento y soporte empresarial |
| **Base de Datos** | <img src="https://skillicons.dev/icons?i=mysql" width="22" height="22" align="center" /> **MySQL 8.0** | Motor de base de datos relacional con bloqueo pesimista e índices |
| **ORM & Persistencia** | <img src="https://skillicons.dev/icons?i=hibernate" width="22" height="22" align="center" /> **Hibernate JPA** | Mapeo objeto-relacional y gestión transaccional |
| **Autenticación** | <img src="https://cdn.simpleicons.org/jsonwebtokens/D63AFF" width="22" height="22" align="center" /> **JWT (JSON Web Tokens)** | Autenticación stateless segura con expiración y roles |
| **Teleconsulta** | <img src="https://cdn.simpleicons.org/agora/099DFD" width="22" height="22" align="center" /> **Agora RTC SDK** | Videollamadas seguras WebRTC de alta definición en tiempo real |
| **IA / Accesibilidad** | <img src="https://cdn.simpleicons.org/deepgram/13EF93" width="22" height="22" align="center" /> **Deepgram AI** | Transcripción de voz a texto en vivo (subtítulos) y comandos |
| **Chatbot con IA** | <img src="https://cdn.simpleicons.org/googlegemini/8E75B2" width="22" height="22" align="center" /> **Google Gemini API** | Asistente de salud virtual inteligente 24/7 con IA generativa |
| **Mapas Interactivos** | <img src="https://cdn.simpleicons.org/leaflet/199900" width="22" height="22" align="center" /> **Leaflet / OSM** | Ubicación geográfica interactiva de clínicas y sedes |
| **Pasarela de Pagos** | <img src="https://cdn.simpleicons.org/stripe/635BFF" width="22" height="22" align="center" /> **Stripe** | Procesamiento seguro de pagos en línea con tarjeta |
| **Gestión de Imágenes** | <img src="https://cdn.simpleicons.org/cloudinary/3448C5" width="22" height="22" align="center" /> **Cloudinary** | Almacenamiento en la nube y optimización de fotos de perfil |
| **Correos y Notificaciones** | <img src="https://cdn.simpleicons.org/gmail/EA4335" width="22" height="22" align="center" /> **Gmail SMTP & Twilio** | Envío de confirmaciones por correo electrónico y WhatsApp |
| **Infraestructura Cloud** | <img src="https://cdn.simpleicons.org/railway/BD00FF" width="22" height="22" align="center" /> **Railway** | Despliegue en la nube con HTTPS/SSL y conexión gestionada |

---

## Roles y Funcionalidades

### Pacientes
* **Registro e Inicio de Sesión Seguro:** Autenticación por correo y contraseña o con Google Sign-In (OAuth 2.0).
* **Reserva de Citas:** Búsqueda de médicos por especialidad y disponibilidad horaria, seleccionando modalidad **Presencial** o **Virtual** (Teleconsulta).
* **Telemedicina en Vivo:** Videollamada interactiva con **Agora RTC SDK**.
* **Accesibilidad por Voz y Subtítulos:** Transcripción en tiempo real con **Deepgram AI** para personas con dificultades auditivas y comandos de navegación por voz.
* **Pasarela de Pagos:** Pagos en línea mediante tarjeta de crédito/débito con **Stripe** o confirmación de pago en efectivo.
* **Asistente Virtual con IA:** Chatbot inteligente potenciado por **Google Gemini API** para resolver consultas las 24 horas.
* **Mapa Interactivo:** Localización de sedes físicas mediante **Leaflet** y OpenStreetMap.
* **Historial Clínico y Recetas:** Visualización de diagnósticos, notas de evolución y recetas médicas.

### Médicos
* **Panel de Disponibilidad:** Apertura y configuración de bloques de atención por fecha y horario.
* **Sala de Teleconsulta:** Atención médica virtual uno a uno con el paciente con control de cámara y micrófono.
* **Emisión de Diagnósticos:** Registro de historial clínico y prescripción de recetas al culminar la cita.
* **Agenda Médica:** Visualización y control de citas programadas, confirmadas, reprogramadas o canceladas.

### Recepción / Operador
* **Citas Asistidas:** Búsqueda rápida de horarios y registro de citas para pacientes presenciales o por llamada telefónica.
* **Validación de Llegada:** Confirmación de asistencia presencial de los pacientes.
* **Cobro en Efectivo:** Emisión de comprobantes y validación de pagos en sede física.

### Administradores
* **Gestión Integral de Usuarios:** Creación, edición, activación y bloqueo de pacientes, médicos y recepcionistas.
* **Catálogo de Especialidades:** Creación y mantenimiento de especialidades médicas con sus tarifas base.
* **Métricas y Notificaciones:** Panel de supervisión global de citas, pagos y notificaciones del sistema.

---

## Características Principales

### <img src="https://cdn.simpleicons.org/agora/099DFD" width="20" height="20" align="center" /> Telemedicina Integrada
* Videollamadas seguras WebRTC punto a punto mediante **Agora SDK**.
* Opciones completas de interacción: mutear micrófono, apagar cámara y finalizar sesión de consulta.

### <img src="https://cdn.simpleicons.org/deepgram/13EF93" width="20" height="20" align="center" /> Accesibilidad Avanzada
* **Subtítulos en Vivo:** Transcripción de voz a texto en tiempo real con **Deepgram** orientada a personas con dificultades auditivas.
* **Navegación por Voz:** Dictado de comandos e información directamente desde el navegador.

### <img src="https://cdn.simpleicons.org/stripe/635BFF" width="20" height="20" align="center" /> Integración de Pagos
* Checkout integrado con **Stripe Elements** para pagos con tarjeta.
* Soporte nativo para registro y emisión de comprobantes en pagos en efectivo.

### <img src="https://cdn.simpleicons.org/googlegemini/8E75B2" width="20" height="20" align="center" /> Chatbot Inteligente
* Asistente virtual de salud impulsado por la API de **Google Gemini**.
* Respuestas contextuales inmediatas a preguntas frecuentes de los pacientes.

---

## Estructura del Repositorio

<div align="center">
  <img src="./assets/estructura.png" alt="Estructura del Proyecto RETO SALUD" width="850"/>
</div>

---

## Instalación y Configuración

### 1. Clonar el Repositorio

```bash
git clone https://github.com/AstroLopitecus123/RETO-SALUD-Web.git
cd RETO-SALUD-Web
```

### 2. Configurar Base de Datos

Crear la base de datos en MySQL y ejecutar el script SQL oficial [`Backend/reto_salud.sql`](./Backend/reto_salud.sql):

```sql
CREATE DATABASE IF NOT EXISTS db_reto_salud;
USE db_reto_salud;
```

### 3. Configurar Backend

1. **Navegar al directorio del backend:**
```bash
cd Backend
```

2. **Configurar variables de entorno:**
Crear o modificar el archivo `application.properties` en `src/main/resources/` con tus credenciales:

```properties
# Base de Datos
spring.datasource.url=jdbc:mysql://localhost:3306/db_reto_salud?serverTimezone=UTC
spring.datasource.username=tu_usuario
spring.datasource.password=tu_password

# JWT
jwt.secret=tu_clave_secreta_super_segura
jwt.expirationMs=86400000

# Stripe
stripe.secret-key=sk_test_tu_clave_secreta_stripe

# Correo (Gmail SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu_correo@gmail.com
spring.mail.password=tu_contraseña_de_aplicacion
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.required=true

# Twilio WhatsApp
twilio.account.sid=tu_account_sid
twilio.auth.token=tu_auth_token
twilio.whatsapp.from=whatsapp:+14155238886

# Teleconsulta (Agora & Deepgram)
agora.appId=tu_agora_app_id
agora.appCertificate=tu_agora_certificate
deepgram.apiKey=tu_deepgram_api_key

# IA (Chatbot)
GEMINI_API_KEY=tu_gemini_api_key

# Google OAuth
google.client.id=tu_google_client_id

# Cloudinary
cloudinary.cloud-name=tu_cloud_name
cloudinary.api-key=tu_api_key
cloudinary.api-secret=tu_api_secret
```

3. **Instalar dependencias y ejecutar:**
```bash
./mvnw clean install
./mvnw spring-boot:run
```
El backend estará disponible en: `http://localhost:8080`

### 4. Configurar Frontend

1. **Navegar al directorio del frontend:**
```bash
cd ../Frontend
```

2. **Configurar variables de entorno:**
Navega a `src/environments/` y edita `environment.ts`:
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080' // Base URL del Backend
};
```
*(Para pagos con Stripe, la clave pública se encuentra configurada en `src/app/config/stripe.config.ts`)*.

3. **Instalar dependencias:**
```bash
npm install
```

4. **Ejecutar en modo desarrollo:**
```bash
ng serve -o
```
El frontend estará disponible en: `http://localhost:4200`

---

## Configuración de Servicios Externos

### <img src="https://cdn.simpleicons.org/stripe/635BFF" width="20" height="20" align="center" /> Stripe (Pagos)
1. Crear cuenta en [Stripe](https://stripe.com/).
2. Obtener las claves de prueba (`Publishable key` y `Secret key`) desde el Dashboard.
3. Configurar `stripe.secret-key` en el Backend y la clave pública en `stripe.config.ts`.

### <img src="https://cdn.simpleicons.org/gmail/EA4335" width="20" height="20" align="center" /> Gmail SMTP (Correos)
1. Habilitar autenticación en 2 pasos en tu cuenta de Google.
2. Generar una **Contraseña de aplicación** desde la seguridad de la cuenta.
3. Configurar `spring.mail.username` y `spring.mail.password` en `application.properties`.

### <img src="https://cdn.simpleicons.org/cloudinary/3448C5" width="20" height="20" align="center" /> Cloudinary (Imágenes)
1. Crear cuenta gratuita en [Cloudinary](https://cloudinary.com/).
2. Obtener Cloud Name, API Key y API Secret desde la consola.
3. Configurar en `application.properties`.

### <img src="https://cdn.simpleicons.org/agora/099DFD" width="20" height="20" align="center" /> Agora SDK (Videollamadas)
1. Crear cuenta en [Agora Console](https://console.agora.io/).
2. Crear un proyecto y habilitar el modo **App Certificate**.
3. Configurar `agora.appId` y `agora.appCertificate` en `application.properties`.

### <img src="https://cdn.simpleicons.org/deepgram/13EF93" width="20" height="20" align="center" /> Deepgram (Transcripción por Voz)
1. Crear cuenta en [Deepgram](https://deepgram.com/).
2. Generar una **API Key** desde la consola.
3. Configurar `deepgram.apiKey` en `application.properties`.

### <img src="https://cdn.simpleicons.org/googlegemini/8E75B2" width="20" height="20" align="center" /> Gemini API (Chatbot)
1. Acceder a [Google AI Studio](https://aistudio.google.com/).
2. Generar una **API Key** para modelos Gemini.
3. Configurar `GEMINI_API_KEY` en `application.properties`.

### <img src="https://skillicons.dev/icons?i=gcp" width="20" height="20" align="center" /> Google Cloud Console (Autenticación)
1. Crear un proyecto en [Google Cloud Console](https://console.cloud.google.com/).
2. Configurar la pantalla de consentimiento y generar credenciales **OAuth 2.0 Client ID**.
3. Configurar `google.client.id` en `application.properties`.

---

## Seguridad del Sistema

* **Autenticación Stateless:** Tokens JWT con expiración controlada y validación en cada petición.
* **Encriptación Fuerte:** Almacenamiento seguro de contraseñas mediante algoritmo **BCrypt**.
* **Políticas CSP Estrictas:** Cabeceras HTTP en `server.js` con protección contra inyecciones arbitrarias (OWASP A05).
* **CORS Centralizado:** Configuración explícita de orígenes permitidos tanto en desarrollo como en producción.
* **Control de Concurrencia:** Bloqueo pesimista en base de datos (`@Lock(PESSIMISTIC_WRITE)`) para prevenir reservas duplicadas.
* **Auditoría Clínica (Ley N° 29733):** Integridad referencial con `ON DELETE RESTRICT` para salvaguardar historiales y pagos médicos.

---

## Comandos Útiles

### Backend
```bash
# Compilar proyecto
cd Backend
./mvnw clean compile

# Ejecutar pruebas unitarias
./mvnw test

# Iniciar servidor Spring Boot
./mvnw spring-boot:run
```

### Frontend
```bash
# Instalar dependencias
cd Frontend
npm install

# Iniciar servidor de desarrollo
ng serve -o

# Compilar para producción
ng build --configuration production

# Ejecutar auditoría de accesibilidad con Cypress + Axe-Core
npx cypress run --spec "cypress/e2e/accesibilidad.cy.ts"
```

---

## Documentación Adicional

* 📖 Ver [Backend/README.md](./Backend/README.md) para detalles técnicos y configuración de la API Spring Boot.
* 📖 Ver [Frontend/README.md](./Frontend/README.md) para detalles de componentes y arquitectura Angular.

---

## Soporte y Resolución de Problemas

1. **Error de conexión a la base de datos:**
   * Verificar que el servicio MySQL esté iniciado.
   * Confirmar que la base de datos `db_reto_salud` exista.
   * Validar credenciales de usuario y contraseña en `application.properties`.

2. **Error de CORS en Frontend:**
   * Asegurar que el origen del frontend (`http://localhost:4200` o dominio en producción) esté registrado en `SecurityConfig.java`.

3. **Falla en Videollamada o Subtítulos:**
   * Verificar las credenciales de `agora.appId` y `deepgram.apiKey`.
   * Asegurarse de haber concedido permisos de micrófono y cámara en el navegador web.

4. **Pagos de Stripe no procesados:**
   * Comprobar que las claves de prueba `pk_test_...` y `sk_test_...` coincidan entre frontend y backend.

---

## Licencia

Este proyecto está en desarrollo y creación continua futuras
