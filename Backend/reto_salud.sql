-- 1. Crear base de datos
CREATE DATABASE IF NOT EXISTS db_reto_salud;
USE db_reto_salud;

-- 2. Crear tablas independientes primero
CREATE TABLE `paises` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `prefijo_telefono` varchar(10) DEFAULT NULL,
  `estado` enum('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `especialidades` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `descripcion` varchar(255) DEFAULT NULL,
  `precio_base` double DEFAULT 0.0,
  `estado` enum('ACTIVA','INACTIVA') NOT NULL DEFAULT 'ACTIVA',
  `fecha_creacion` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Crear tabla principal de Usuarios
CREATE TABLE `usuarios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `apellido` varchar(100) NOT NULL,
  `correo` varchar(100) NOT NULL UNIQUE,
  `contrasena` varchar(255) NOT NULL,
  `dni` varchar(20) UNIQUE DEFAULT NULL,
  `telefono` varchar(20) DEFAULT NULL,
  `fecha_nacimiento` date DEFAULT NULL,
  `rol` enum('ADMIN','MEDICO','PACIENTE','RECEPCION') NOT NULL,
  `estado` enum('ACTIVADO','DESACTIVADO') NOT NULL DEFAULT 'ACTIVADO',
  `fecha_registro` timestamp DEFAULT CURRENT_TIMESTAMP,
  `pais_id` bigint DEFAULT NULL,
  `token_recuperacion` varchar(255) DEFAULT NULL,
  `token_expira` datetime DEFAULT NULL,
  `configuracion_visual` varchar(50) DEFAULT 'NINGUNO',
  `foto_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`pais_id`) REFERENCES `paises` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Crear tabla puente de Especialidades y Usuarios (si aplica)
CREATE TABLE `medico_especialidades` (
  `usuario_id` bigint NOT NULL,
  `especialidad_id` bigint NOT NULL,
  PRIMARY KEY (`usuario_id`, `especialidad_id`),
  FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`especialidad_id`) REFERENCES `especialidades` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. Crear tabla de Médicos
CREATE TABLE `medicos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `usuario_id` bigint NOT NULL UNIQUE,
  `especialidad_id` bigint NOT NULL,
  `estado` enum('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
  `fecha_creacion` timestamp DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`especialidad_id`) REFERENCES `especialidades` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. Crear tabla de Citas y Disponibilidades
CREATE TABLE `disponibilidades` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `medico_id` bigint NOT NULL,
  `fecha` date NOT NULL,
  `hora_inicio` time NOT NULL,
  `hora_fin` time NOT NULL,
  `estado` enum('DISPONIBLE','NO_DISPONIBLE') DEFAULT 'DISPONIBLE',
  `fecha_creacion` timestamp DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_disp_bloque` UNIQUE (`medico_id`, `fecha`, `hora_inicio`),
  FOREIGN KEY (`medico_id`) REFERENCES `medicos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_disp_medico_fecha_estado ON disponibilidades (medico_id, fecha, estado);

CREATE TABLE `citas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paciente_id` bigint NOT NULL,
  `medico_id` bigint NOT NULL,
  `fecha` datetime NOT NULL,
  `estado` enum('PENDIENTE','CONFIRMADA','REPROGRAMADA','CANCELADA','SOLICITUD_REPROGRAMACION','SOLICITUD_CANCELACION','REPROGRAMACION_ACEPTADA','REPROGRAMACION_RECHAZADA') NOT NULL DEFAULT 'PENDIENTE',
  `motivo` varchar(255) DEFAULT NULL,
  `fecha_propuesta` datetime DEFAULT NULL,
  `fecha_creacion` timestamp DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`paciente_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`medico_id`) REFERENCES `medicos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_citas_medico_fecha ON citas (medico_id, fecha);

-- 7. Crear tablas derivadas de las Citas (Pagos e Historial Médico)
CREATE TABLE `pagos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cita_id` bigint NOT NULL,
  `usuario_id` bigint NOT NULL,
  `monto` decimal(10,2) NOT NULL,
  `metodo` enum('TARJETA','PAYPAL','EFECTIVO','TRANSFERENCIA') DEFAULT NULL,
  `estado_pago` enum('PENDIENTE','COMPLETADO','FALLIDO','REEMBOLSADO') NOT NULL DEFAULT 'PENDIENTE',
  `fecha_pago` datetime DEFAULT NULL,
  `transaccion_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`cita_id`) REFERENCES `citas` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `comprobantes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pago_id` bigint NOT NULL UNIQUE,
  `numero` varchar(255) NOT NULL UNIQUE,
  `fecha` datetime(6) NOT NULL,
  `archivo_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`pago_id`) REFERENCES `pagos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `historial` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cita_id` bigint NOT NULL UNIQUE,
  `diagnostico` text,
  `receta` text,
  `notas` text,
  `fecha_registro` timestamp DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`cita_id`) REFERENCES `citas` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 8. Crear otras tablas independientes
CREATE TABLE `notificaciones` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `titulo` varchar(255) DEFAULT NULL,
  `mensaje` varchar(255) NOT NULL,
  `fecha_creacion` timestamp DEFAULT CURRENT_TIMESTAMP,
  `leida` boolean DEFAULT FALSE,
  `rol_destino` varchar(50) DEFAULT NULL,
  `usuario_destino_id` bigint DEFAULT NULL,
  `referencia_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`usuario_destino_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `solicitudes_empleo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) NOT NULL,
  `apellido` varchar(255) NOT NULL,
  `correo` varchar(255) NOT NULL,
  `dni` varchar(255) NOT NULL,
  `telefono` varchar(255) NOT NULL,
  `puesto` enum('MEDICO','RECEPCION') NOT NULL,
  `estado` enum('PENDIENTE','APROBADA','RECHAZADA') NOT NULL DEFAULT 'PENDIENTE',
  `mensaje` text DEFAULT NULL,
  `fecha_solicitud` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
