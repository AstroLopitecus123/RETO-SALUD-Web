# Frontend - RETO SALUD

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="../assets/RETO%20SALUD%20FRONTEND-blanco.png">
    <source media="(prefers-color-scheme: light)" srcset="../assets/RETO%20SALUD%20FRONTEND.png">
    <img alt="Logo RETO SALUD Frontend" src="../assets/RETO%20SALUD%20FRONTEND.png" width="520">
  </picture>

  <h3>Single Page Application (SPA) Accesible, Inclusiva y de Alto Rendimiento</h3>

  [![Angular](https://img.shields.io/badge/Angular-19.1-DD0031?style=for-the-badge&logo=angular&logoColor=white)](https://angular.dev/)
  [![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
  [![Cypress](https://img.shields.io/badge/Cypress-15.x-69D3A7?style=for-the-badge&logo=cypress&logoColor=white)](https://www.cypress.io/)
  [![Axe-core](https://img.shields.io/badge/Axe--core-WCAG_2.1_AA-blueviolet?style=for-the-badge)](https://www.deque.com/axe/)
  [![Agora](https://img.shields.io/badge/Agora_RTC-4.24-099DFD?style=for-the-badge&logo=agora&logoColor=white)](https://www.agora.io/)
  [![Stripe](https://img.shields.io/badge/Stripe-Elements-635BFF?style=for-the-badge&logo=stripe&logoColor=white)](https://stripe.com/)

  <br/>

  **[Sitio Web](https://www.reto-salud.online)** • **[Arquitectura](#arquitectura-del-frontend)** • **[Stack Tecnológico](#stack-tecnológico)** • **[Estructura](#estructura-del-proyecto)** • **[Rutas](#rutas-de-la-aplicación)** • **[Accesibilidad A11y](#características-especiales-de-accesibilidad)** • **[Calidad y Testing](#calidad-de-software-y-pruebas)** • **[Instalación](#instalación-y-configuración)** • **[README Principal](../README.md)**

</div>

---

## Descripción General

El **Frontend de RETO SALUD** es una Single Page Application (SPA) modular y reactiva desarrollada con **Angular 19** y **TypeScript 5.7**. Diseñada bajo estándares de inclusión universal (**A11y WCAG 2.1 AA**), ofrece interfaces adaptativas para pacientes, médicos, personal de recepción y administradores, integrando telemedicina WebRTC en vivo, subtítulos asistidos por inteligencia artificial, checkout seguro con Stripe y asistente conversacional inteligente con Google Gemini.

---

## Arquitectura del Frontend

El cliente opera como una aplicación web desacoplada que consume la API REST del backend mediante servicios reactivos basados en **RxJS** e interceptores HTTP para el control de sesiones JWT:

<div align="center">
  <img alt="Arquitectura del Frontend RETO SALUD" src="../assets/arquitectura_frontend.png" width="850">
</div>

---

## Stack Tecnológico

| Componente | Tecnología | Versión | Propósito | Patrones / Decoradores Clave |
| :--- | :--- | :---: | :--- | :--- |
| **Framework Base** | <a href="https://angular.dev/" target="_blank"><img src="https://skillicons.dev/icons?i=angular" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Angular**](https://angular.dev/) | 19.1 | Arquitectura modular de componentes, directivas y servicios SPA | `@Component`, `@Injectable`, `@NgModule` |
| **Lenguaje Frontend** | <a href="https://www.typescriptlang.org/" target="_blank"><img src="https://skillicons.dev/icons?i=ts" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**TypeScript**](https://www.typescriptlang.org/) | 5.7 | Tipado estático estricto, interfaces DTO y seguridad en tiempo de compilación | Tipos genéricos, Interfaces, Enums |
| **Programación Reactiva** | <a href="https://rxjs.dev/" target="_blank"><img src="https://skillicons.dev/icons?i=rxjs" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**RxJS**](https://rxjs.dev/) | 7.8 | Manejo asíncrono de flujos de datos, eventos y estado compartido | `Observable`, `BehaviorSubject`, `pipe()`, `map()` |
| **Comunicaciones HTTP** | <a href="https://angular.dev/" target="_blank"><img src="https://skillicons.dev/icons?i=angular" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Angular HTTP Client**](https://angular.dev/) | 19.1 | Consumo de la API REST con inyección automática de tokens Bearer JWT | `HttpClient`, `HttpInterceptorFn` |
| **Enrutamiento y Seguridad**| <a href="https://angular.dev/" target="_blank"><img src="https://skillicons.dev/icons?i=angular" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Angular Router**](https://angular.dev/) | 19.1 | Navegación entre vistas y protección de rutas según roles (RBAC) | `Routes`, `CanActivateFn`, `RouterLink` |
| **Teleconsulta WebRTC** | <a href="https://www.agora.io/" target="_blank"><img src="https://cdn.simpleicons.org/agora/099DFD" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Agora RTC SDK NG**](https://www.agora.io/) | 4.24 | Conexión multimedia punto a punto de audio/video de baja latencia | `IAgoraRTCClient`, `createCameraVideoTrack` |
| **Transcripción por Voz** | <a href="https://deepgram.com/" target="_blank"><img src="https://cdn.simpleicons.org/deepgram/13EF93" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Deepgram SDK**](https://deepgram.com/) | 5.0 | Transcripción de voz a texto en vivo mediante WebSockets | Live Transcription, Audio Streaming |
| **Pasarela de Pagos** | <a href="https://stripe.com/" target="_blank"><img src="https://cdn.simpleicons.org/stripe/635BFF" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Stripe.js**](https://stripe.com/) | 9.2 | Checkout seguro con tarjeta validado por Stripe Elements | `loadStripe`, `StripeCardElement` |
| **Mapas Interactivos** | <a href="https://leafletjs.com/" target="_blank"><img src="https://cdn.simpleicons.org/leaflet/199900" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Leaflet**](https://leafletjs.com/) | 1.9 | Renderizado de mapas y geolocalización de sedes médicas | `L.map`, `L.tileLayer`, `L.marker` |
| **Generación de PDFs** | <a href="https://github.com/parallax/jsPDF" target="_blank"><img src="https://skillicons.dev/icons?i=js" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**jsPDF & AutoTable**](https://github.com/parallax/jsPDF) | 4.2 | Emisión y descarga en cliente de comprobantes y recetas médicas | `jsPDF`, `autoTable` |

---

## Estructura del Proyecto

La aplicación se organiza bajo una arquitectura modular limpia por responsabilidades:

<div align="center">
  <img alt="Estructura de Directorios del Frontend" src="../assets/estructura_frontend.png" width="850">
</div>

### Desglose de Carpetas Principales (`src/app/`)

* **`components/`:** Vistas y componentes visuales reutilizables categorizados por módulo (autenticación, teleconsulta, checkout, paneles de pacientes, médicos y administración).
* **`services/`:** Servicios singleton inyectables (`@Injectable`) que concentran la lógica de comunicación con la API REST (citas, autenticación, pagos, videollamadas, accesibilidad).
* **`guards/`:** Guardianes de ruta funcionales (`CanActivateFn`) que validan la autenticación y los roles permitidos antes de autorizar la navegación.
* **`config/`:** Configuraciones globales de clientes de terceros (claves de Stripe, parámetros de Agora).
* **`environments/`:** Definición de variables de entorno (URL base de la API y llaves públicas de servicios).

---

## Rutas de la Aplicación

La navegación está resguardada por guardianes perimetrales que previenen accesos no autorizados:

### 1. Rutas Públicas (Sin Autenticación)
| Ruta | Descripción | Acceso |
| :--- | :--- | :--- |
| `/` | Landing page oficial informativa de R.E.T.O Salud | Público |
| `/login` | Formulario de autenticación (correo/contraseña y Google Sign-In) | Público |
| `/registrar` | Registro interactivo para nuevos pacientes | Público |
| `/auth/google/callback` | Callback para el procesamiento del token OAuth de Google | Público |
| `/recuperar` | Solicitud de enlace para restablecimiento de contraseña | Público |
| `/restablecer` | Formulario para ingresar nueva contraseña con token | Público |
| `/trabaja-con-nosotros` | Formulario de postulación laboral médica y administrativa | Público |

### 2. Panel del Paciente (Protegido por `PacienteGuard` / `AuthGuard`)
| Ruta | Descripción | Guard |
| :--- | :--- | :--- |
| `/paciente/dashboard` | Tablero principal con accesos rápidos y estado de salud | `PacienteGuard` |
| `/mis-citas` | Agenda y gestión de citas programadas, pasadas o en reprogramación | `AuthGuard` |
| `/mi-perfil` | Edición de datos personales, avatar y configuración de accesibilidad | `AuthGuard` |
| `/historial-clinico` | Consulta de diagnósticos médicos, recetas emitidas y notas de evolución | `AuthGuard` |
| `/mi-historial` | Historial de transacciones y descarga de comprobantes | `AuthGuard` |
| `/notificaciones` | Centro de avisos y recordatorios de citas | `AuthGuard` |
| `/teleconsulta/:id` | Sala virtual de videollamada interactiva con subtitulado en vivo | `AuthGuard` |
| `/checkout/:id` | Resumen de servicio médico antes de procesar el pago | `AuthGuard` |
| `/pagar-tarjeta/:id` | Pasarela de pago en línea mediante Stripe Elements | `AuthGuard` |
| `/pagar-efectivo/:id` | Registro de promesa de pago presencial en sede | `AuthGuard` |

### 3. Panel del Médico (Protegido por `MedicoGuard`)
| Ruta | Descripción | Guard |
| :--- | :--- | :--- |
| `/medico/dashboard` | Resumen de consultas del día y métricas de atención | `MedicoGuard` |
| `/medico/agenda` | Calendario detallado de pacientes agendados y confirmados | `MedicoGuard` |
| `/medico/pagos` | Control de liquidación de honorarios e ingresos generados | `MedicoGuard` |
| `/gestionar-disponibilidad/:id` | Configuración y apertura de bloques de atención por fecha y hora | `MedicoGuard` |

### 4. Panel de Recepción (Protegido por `RecepcionGuard`)
| Ruta | Descripción | Guard |
| :--- | :--- | :--- |
| `/recepcion/dashboard` | Gestión ágil de llegada de pacientes y citas asistidas | `RecepcionGuard` |
| `/recepcion/pagos` | Validación y cobro manual de comprobantes en caja física | `RecepcionGuard` |

### 5. Panel Administrativo (Protegido por `AdminGuard`)
| Ruta | Descripción | Guard |
| :--- | :--- | :--- |
| `/admin` | Centro de control global de la clínica | `AdminGuard` |
| `/usuarios` | Mantenimiento, activación y bloqueo de cuentas de usuario | `AdminGuard` |
| `/medicos` | Alta y asignación de especialidades al staff médico | `AdminGuard` |
| `/especialidades` | Catálogo de especialidades médicas y tarifas asociadas | `AdminGuard` |
| `/admin/gestion-citas` | Supervisión general de citas y resolución de reprogramaciones | `AdminGuard` |
| `/admin/pagos` | Auditoría financiera global de transacciones | `AdminGuard` |
| `/admin/solicitudes` | Revisión y aprobación de postulaciones laborales | `AdminGuard` |
| `/admin/notificaciones` | Emisión de comunicados globales a todo el sistema | `AdminGuard` |

---

## Características Especiales de Accesibilidad

La plataforma está diseñada bajo un enfoque de **Inclusión Universal (A11y)**, implementando herramientas para garantizar autonomía a usuarios con diferentes capacidades:

| Categoría | Funcionalidades Principales | Beneficio y Estándar |
| :--- | :--- | :--- |
| **Visión y Daltonismo** | Filtros cromáticos integrados (Deuteranopia, Protanopia, Tritanopia, Escala de grises). Incluye un **Test de Ishihara** interactivo para calibrar el perfil ideal automáticamente. | Garantiza diferenciación de contrastes según pautas **WCAG 2.1**. |
| **Audio y Voz (IA)** | **Comandos de voz** para navegación, **narrador auditivo** de imágenes y contenido, y subtítulos automáticos en teleconsultas con **Deepgram AI**. | Facilita la operación a pacientes con discapacidad visual, auditiva o motriz. |
| **Asistente Virtual** | **Chatbot médico conversacional** (Google Gemini) para triage de consultas, orientación administrativa y soporte continuo 24/7. | Asistencia inmediata sin fricción cognitiva. |
| **Escalado de Interfaz** | Selector dinámico de zoom y escala tipográfica (hasta 200%+ sin pérdida de maquetación). | Lectura confortable para usuarios con baja visión o fatiga ocular. |
| **Modo Alto Contraste** | Paleta de contraste reforzado para maximizar la legibilidad de textos, íconos y bordes interactivos. | Cumple con ratio de contraste mínimo 4.5:1 exigido por WCAG AA. |
| **Navegación por Teclado Pro** | Resaltado de foco ultra-visible, orden de tabulación intuitivo (`Tab` / `Shift+Tab`) y confirmación con `Enter`. | Operabilidad total de la plataforma sin requerir ratón. |

---

## Calidad de Software y Pruebas

El frontend implementa una estrategia rigurosa de calidad que combina auditorías automatizadas de accesibilidad sobre el DOM y pruebas unitarias de componentes:

### 1. Matriz de Aseguramiento de Calidad en Frontend

| Dimensión de Prueba | Framework / Herramienta | Archivos / Especificación | Alcance y Criterio |
| :--- | :--- | :--- | :--- |
| **Accesibilidad Web (A11y)** | <a href="https://www.cypress.io/" target="_blank"><img src="https://cdn.simpleicons.org/cypress/69D3A7" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Cypress**](https://www.cypress.io/) + [**Axe-core**](https://www.deque.com/axe/) | `cypress/e2e/accesibilidad.cy.ts` | **7 suites automatizadas:** Landing Page, Iniciar Sesión, Dashboard Mi Panel, Mis Citas, Historial Clínico, Notificaciones y Mi Perfil, verificando **0 violaciones** bajo **WCAG 2.1 (Nivel AA)**. |
| **Pruebas Unitarias** | <a href="https://jasmine.github.io/" target="_blank"><img src="https://cdn.simpleicons.org/jasmine/8A4182" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Jasmine**](https://jasmine.github.io/) + [**Karma**](https://karma-runner.github.io/) | `src/**/*.spec.ts` | Pruebas unitarias de componentes, servicios e inyección de dependencias. |
| **Rendimiento y Core Web Vitals** | <a href="https://pagespeed.web.dev/" target="_blank"><img src="https://cdn.simpleicons.org/google/4285F4" width="20" height="20" align="absmiddle" /></a>&nbsp;&nbsp;[**Google Lighthouse**](https://pagespeed.web.dev/) | Pipeline CI/CD en producción | Puntuación **98/100 en Performance** y **100/100 en Accesibilidad** en [reto-salud.online](https://www.reto-salud.online). |

### 2. Comandos para Ejecución de Pruebas

* **Ejecutar auditoría de accesibilidad con interfaz gráfica (Cypress UI):**
  ```bash
  npm run test:a11y
  ```

* **Ejecutar auditoría de accesibilidad en modo headless (terminal / CI):**
  ```bash
  npm run test:a11y:headless
  ```
  *(O ejecutar directamente el spec: `npx cypress run --spec "cypress/e2e/accesibilidad.cy.ts"`)*

* **Ejecutar pruebas unitarias de componentes con Karma:**
  ```bash
  npm test
  ```

---

## Instalación y Configuración

### Prerrequisitos

* **Node.js 20.x o 22.x LTS** (`node -v`)
* **npm 10.x** o superior (`npm -v`)
* **Angular CLI 19** (`npm install -g @angular/cli`)

### 1. Clonar e Instalar Dependencias

Navegar a la carpeta del frontend e instalar los paquetes de `package.json`:

```bash
cd Frontend
npm install
```

### 2. Configurar Variables de Entorno

Revisar o editar el archivo `src/environments/environment.ts` con la URL del Backend:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080' // Base URL del servidor Spring Boot
};
```

*(Las credenciales públicas de Stripe se configuran en `src/app/config/stripe.config.ts`)*.

### 3. Ejecutar en Modo Desarrollo

Levantar el servidor local de desarrollo de Angular:

```bash
ng serve -o
```

La aplicación se compilará y abrirá automáticamente en tu navegador en:
```
http://localhost:4200
```

### 4. Compilar para Producción

Para generar el bundle optimizado para despliegue:

```bash
ng build --configuration production
```

Los artefactos listos para producción se generarán en la carpeta `dist/`.

---

<div align="center">
  <sub>Desarrollado para la plataforma <b>RETO SALUD</b> • Consulta el <a href="../README.md">README General del Proyecto</a></sub>
</div>
