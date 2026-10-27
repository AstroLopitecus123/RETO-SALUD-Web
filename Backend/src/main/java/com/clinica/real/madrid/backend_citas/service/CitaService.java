package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.exception.HorarioNoDisponibleException;
import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.exception.ResourceNotFoundException;
import com.clinica.real.madrid.backend_citas.model.Pago;
import com.clinica.real.madrid.backend_citas.repository.ComprobanteRepository;
import com.clinica.real.madrid.backend_citas.repository.PagoRepository;
import com.clinica.real.madrid.backend_citas.model.Cita;
import com.clinica.real.madrid.backend_citas.model.Disponibilidad;
import com.clinica.real.madrid.backend_citas.model.EstadoCita;
import com.clinica.real.madrid.backend_citas.repository.CitaRepository;
import com.clinica.real.madrid.backend_citas.repository.DisponibilidadRepository;
import com.clinica.real.madrid.backend_citas.repository.HistorialRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CitaService {

    private static final java.util.concurrent.ExecutorService notifExecutor = java.util.concurrent.Executors.newFixedThreadPool(5);

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.DispositivoActivoRepository dispositivoActivoRepository;

    @Autowired
    private HistorialRepository historialRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ComprobanteRepository comprobanteRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TwilioService twilioService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.MedicoRepository medicoRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public List<Cita> obtenerCitasPorPacienteYMedico(Long pacienteId, Long usuarioMedicoId) {
        return citaRepository.findByPacienteIdAndMedico_Usuario_IdOrderByFechaDesc(pacienteId, usuarioMedicoId);
    }

    public boolean esMedicoPropio(Long medicoId, Long usuarioMedicoId) {
        return medicoRepository.findByUsuarioId(usuarioMedicoId)
                .map(m -> medicoId.equals(m.getId())).orElse(false);
    }

    private Cita bloquearCita(Long id) {
        Cita cita = citaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));
        // Controllers may already have loaded this entity into the OSIV persistence context.
        entityManager.refresh(cita, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return cita;
    }

    private void exigirEstado(Cita cita, EstadoCita estado) {
        if (cita.getEstado() != estado) throw new BadRequestException("Estado de cita no valido para esta operacion");
    }

    public List<Cita> listar() {
        return citaRepository.findAllByOrderByFechaDesc();
    }

    public Cita obtenerPorId(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!citaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cita no encontrada");
        }
        Cita cita = bloquearCita(id);
        pagoService.reembolsarPago(id);
        disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(
            cita.getMedico().getId(), 
            cita.getFecha().toLocalDate(), 
            cita.getFecha().toLocalTime()
        ).ifPresent(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.DISPONIBLE);
            disponibilidadRepository.save(disp);
        });
        historialRepository.deleteByCitaId(id);
        List<Pago> pagos = pagoRepository.findByCitaId(id);
        for (Pago p : pagos) {
            comprobanteRepository.deleteByPagoId(p.getId());
            pagoRepository.delete(p);
        }
        citaRepository.delete(cita);
    }

    public List<Cita> obtenerCitasPorPaciente(Long pacienteId) {
        return citaRepository.findByPacienteIdWithMedicoAndEspecialidad(pacienteId);
    }

    public void eliminarPorUsuario(Long usuarioId) {
        citaRepository.deleteByPacienteId(usuarioId);
    }

    @Transactional
    public void abandonarCheckout(Long id) {
        Cita cita = bloquearCita(id);

        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new BadRequestException("Solo se pueden abandonar reservas en estado PENDIENTE");
        }

        pagoService.reembolsarPago(id);

        disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(
            cita.getMedico().getId(), 
            cita.getFecha().toLocalDate(), 
            cita.getFecha().toLocalTime()
        ).ifPresent(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.DISPONIBLE);
            disponibilidadRepository.save(disp);
        });

        List<Pago> pagos = pagoRepository.findByCitaId(id);
        for (Pago p : pagos) {
            comprobanteRepository.deleteByPagoId(p.getId());
            pagoRepository.delete(p);
        }
        citaRepository.delete(cita);
    }

    public List<Cita> obtenerCitasPorMedico(Long idMedico) {
        return citaRepository.findByMedicoIdOrderByFechaDesc(idMedico);
    }

    @Transactional
    public Cita crearCita(Cita cita) {
        if (cita == null || cita.getMedico() == null || cita.getMedico().getId() == null) {
            throw new RuntimeException("Datos de médico inválidos para crear la cita");
        }
        if (cita.getFecha() == null) {
            throw new RuntimeException("La fecha de la cita es obligatoria");
        }
        if (cita.getFecha().isBefore(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))) {
            throw new RuntimeException("No puedes agendar una cita en una fecha u hora que ya pasó");
        }

        Disponibilidad disp = disponibilidadRepository.findSlotForUpdate(
            cita.getMedico().getId(),
            Disponibilidad.EstadoDisponibilidad.DISPONIBLE,
            cita.getFecha().toLocalDate(),
            cita.getFecha().toLocalTime()
        ).orElseThrow(() -> new HorarioNoDisponibleException("El horario seleccionado ya no está disponible"));

        disp.setEstado(Disponibilidad.EstadoDisponibilidad.NO_DISPONIBLE);
        disponibilidadRepository.save(disp);

        // Proteccion contra Mass Assignment e IDOR: toda nueva cita se crea con ID nuevo y estado PENDIENTE
        cita.setId(null);
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setFechaCreacion(LocalDateTime.now());
        cita.setFechaPropuesta(null);

        return citaRepository.save(cita);
    }

    public List<Cita> listarPorUsuario(Long usuarioId) {
        return citaRepository.findByPacienteIdOrderByFechaDesc(usuarioId);
    }

    public List<Cita> listarPorMedico(Long medicoId) {
        return citaRepository.findByMedicoIdOrderByFechaDesc(medicoId);
    }

    @Transactional
    public void confirmarCita(Long id) {
        Cita cita = bloquearCita(id);
        if (cita.getEstado() == EstadoCita.CONFIRMADA) return;
        exigirEstado(cita, EstadoCita.PENDIENTE);
        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);
        notificarCambioCita(cita, "confirmada");
    }

    @Transactional
    public void cancelarCita(Long id) {
        cancelarCitaInterno(id, true);
    }

    private void cancelarCitaInterno(Long id, boolean notificar) {
        Cita cita = bloquearCita(id);
        if (cita.getEstado() == EstadoCita.CANCELADA) return;
        pagoService.reembolsarPago(id);
        cita.setEstado(EstadoCita.CANCELADA);
        cita.setFechaPropuesta(null);
        citaRepository.save(cita);

        disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(
            cita.getMedico().getId(), 
            cita.getFecha().toLocalDate(), 
            cita.getFecha().toLocalTime()
        ).ifPresent(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.DISPONIBLE);
            disponibilidadRepository.save(disp);
        });

        if (notificar) {
            notificarCambioCita(cita, "cancelada");
        }
    }

    @Transactional
    public void solicitarReprogramacion(Long id, LocalDateTime nuevaFecha, String motivo) {
        Cita cita = bloquearCita(id);
        exigirEstado(cita, EstadoCita.CONFIRMADA);
        if (nuevaFecha == null || !nuevaFecha.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("La nueva fecha debe ser futura");
        }
        cita.setFechaPropuesta(nuevaFecha);
        cita.setEstado(EstadoCita.SOLICITUD_REPROGRAMACION);

        if (motivo != null && !motivo.trim().isEmpty()) {
            cita.setMotivo(motivo);
        }

        citaRepository.save(cita);

        notificarCambioCita(cita, "solicitud de reprogramación");
    }

    @Transactional
    public void solicitarCancelacion(Long id, String motivo) {
        Cita cita = bloquearCita(id);
        exigirEstado(cita, EstadoCita.CONFIRMADA);
        cita.setEstado(EstadoCita.SOLICITUD_CANCELACION);
        cita.setMotivo(motivo);
        citaRepository.save(cita);

        notificarCambioCita(cita, "solicitud de cancelación");
    }

    @Transactional
    public void confirmarReprogramacion(Long id) {
        Cita cita = bloquearCita(id);
        exigirEstado(cita, EstadoCita.SOLICITUD_REPROGRAMACION);

        if (cita.getFechaPropuesta() == null || !cita.getFechaPropuesta().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("No hay una fecha propuesta para esta cita");
        }

        LocalDateTime nuevaFecha = cita.getFechaPropuesta();

        disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(
            cita.getMedico().getId(), 
            cita.getFecha().toLocalDate(), 
            cita.getFecha().toLocalTime()
        ).ifPresent(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.DISPONIBLE);
            disponibilidadRepository.save(disp);
        });

        disponibilidadRepository.findSlotForUpdate(
            cita.getMedico().getId(), 
            Disponibilidad.EstadoDisponibilidad.DISPONIBLE,
            nuevaFecha.toLocalDate(), 
            nuevaFecha.toLocalTime()
        ).ifPresentOrElse(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.NO_DISPONIBLE);
            disponibilidadRepository.save(disp);
        }, () -> {
            throw new HorarioNoDisponibleException("El nuevo horario ya no está disponible o no existe en la agenda del médico");
        });

        cita.setFecha(nuevaFecha);
        cita.setFechaPropuesta(null);
        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        notificarCambioCita(cita, "reprogramación aprobada");
    }

    @Transactional
    public void rechazarReprogramacion(Long id) {
        Cita cita = bloquearCita(id);
        exigirEstado(cita, EstadoCita.SOLICITUD_REPROGRAMACION);

        cita.setFechaPropuesta(null);

        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        notificarCambioCita(cita, "solicitud de reprogramación rechazada");
    }

    @Transactional
    public void confirmarCancelacion(Long id) {
        Cita cita = bloquearCita(id);
        if (cita.getEstado() == EstadoCita.CANCELADA) return;
        cancelarCitaInterno(id, false);

        notificarCambioCita(cita, "cancelación aprobada y reembolso gestionado");
    }

    @Transactional
    public void rechazarCancelacion(Long id) {
        Cita cita = bloquearCita(id);
        exigirEstado(cita, EstadoCita.SOLICITUD_CANCELACION);

        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        notificarCambioCita(cita, "solicitud de cancelación rechazada. Su cita sigue vigente.");
    }

    @Transactional
    public void reprogramarCita(Long id, LocalDateTime nuevaFecha) {
        if (nuevaFecha == null || !nuevaFecha.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("La nueva fecha es requerida");
        }
        Cita cita = bloquearCita(id);
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new BadRequestException("No se puede reprogramar una cita cancelada");
        }
        EstadoCita estadoPosterior = cita.getEstado() == EstadoCita.PENDIENTE
                ? EstadoCita.PENDIENTE : EstadoCita.CONFIRMADA;

        disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(
            cita.getMedico().getId(), 
            cita.getFecha().toLocalDate(), 
            cita.getFecha().toLocalTime()
        ).ifPresent(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.DISPONIBLE);
            disponibilidadRepository.save(disp);
        });

        disponibilidadRepository.findSlotForUpdate(
            cita.getMedico().getId(), 
            Disponibilidad.EstadoDisponibilidad.DISPONIBLE,
            nuevaFecha.toLocalDate(), 
            nuevaFecha.toLocalTime()
        ).ifPresentOrElse(disp -> {
            disp.setEstado(Disponibilidad.EstadoDisponibilidad.NO_DISPONIBLE);
            disponibilidadRepository.save(disp);
        }, () -> {
            throw new HorarioNoDisponibleException("El nuevo horario ya no está disponible o no existe en la agenda del médico");
        });

        cita.setFecha(nuevaFecha);
        cita.setFechaPropuesta(null);
        cita.setEstado(estadoPosterior);
        citaRepository.save(cita);

        notificarCambioCita(cita, "reprogramada correctamente");

    }

    public void eliminarHistorialesPorUsuario(Long usuarioId) {

        List<Cita> citas = citaRepository.findByPacienteIdOrderByFechaDesc(usuarioId);
        for (Cita cita : citas) {
            historialRepository.deleteByCitaId(cita.getId());
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void enviarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now(java.time.ZoneId.systemDefault());
        LocalDateTime desde = ahora.plusHours(23);
        LocalDateTime hasta = ahora.plusHours(24);

        List<Cita> citas = citaRepository.findByFechaBetweenAndEstado(desde, hasta, EstadoCita.CONFIRMADA);

        for (Cita cita : citas) {
            try {
                enviarRecordatorio(cita);
                System.out.printf(" Recordatorio enviado para cita %d (%s)%n",
                        cita.getId(), cita.getFecha());
            } catch (Exception e) {
                System.err.printf("⚠️ Error al enviar recordatorio para cita %d: %s%n",
                        cita.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 300000)
    public void liberarCitasPendientesAbandonadas() {
        LocalDateTime limite = LocalDateTime.now(java.time.ZoneId.systemDefault()).minusMinutes(15);
        List<Long> abandonadas = citaRepository.findIdsAbandonadas(EstadoCita.PENDIENTE, limite);
        for (Long id : abandonadas) {
            try {
                // A separate transaction per reservation avoids self-invocation without a transaction.
                var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transaction.executeWithoutResult(status -> {
                    Cita actual = bloquearCita(id);
                    if (actual.getEstado() == EstadoCita.PENDIENTE && actual.getFechaCreacion() != null
                            && actual.getFechaCreacion().isBefore(limite)) {
                        cancelarCitaInterno(id, false);
                    }
                });
            } catch (Exception e) {
                System.err.println("Error al liberar cita abandonada ID " + id + ": " + e.getMessage());
            }
        }
    }

    private void enviarRecordatorio(Cita cita) {
        String fecha = cita.getFecha().toLocalDate().toString();
        String hora = cita.getFecha().toLocalTime().toString();

        SimpleMailMessage mailPaciente = new SimpleMailMessage();
        mailPaciente.setFrom("clinicarealmadrid32@gmail.com"); 
        mailPaciente.setTo(cita.getPaciente().getCorreo());
        mailPaciente.setSubject("📅 Recordatorio de cita médica");
        mailPaciente.setText(String.format(
                "Hola %s,\n\nEste es un recordatorio de su cita con el Dr. %s el %s a las %s.\n\nSaludos,\nClínica Real Madrid",
                cita.getPaciente().getNombre(),
                cita.getMedico().getUsuario().getNombre(),
                fecha,
                hora
        ));

        mailSender.send(mailPaciente);

        System.out.println(" Recordatorio enviado a paciente: " 
                + cita.getPaciente().getCorreo() 
                + " para la cita: " + cita.getFecha());

        SimpleMailMessage mailMedico = new SimpleMailMessage();
        mailMedico.setFrom("clinicarealmadrid32@gmail.com");
        mailMedico.setTo(cita.getMedico().getUsuario().getCorreo());
        mailMedico.setSubject("📅 Recordatorio de cita próxima");
        mailMedico.setText(String.format(
                "Hola Dr. %s,\n\nTiene una cita con el paciente %s el %s a las %s.\n\nSaludos,\nClínica Real Madrid",
                cita.getMedico().getUsuario().getNombre(),
                cita.getPaciente().getNombre(),
                fecha,
                hora
        ));
        mailSender.send(mailMedico);

        System.out.println(" Recordatorio enviado a médico: " 
                + cita.getMedico().getUsuario().getCorreo() 
                + " para la cita: " + cita.getFecha());

    }

    private void notificarCambioCita(Cita cita, String accion) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            enviarCambioCita(cita, accion);
                        }
                    });
        } else {
            enviarCambioCita(cita, accion);
        }
    }

    private void enviarCambioCita(Cita cita, String accion) {
        final String f = cita.getFecha().toLocalDate().toString();
        final String h = cita.getFecha().toLocalTime().toString();
        final String nDr = cita.getMedico().getUsuario().getNombre();
        final String nPc = cita.getPaciente().getNombre();
        final com.clinica.real.madrid.backend_citas.model.Usuario pUser = cita.getPaciente();
        final com.clinica.real.madrid.backend_citas.model.Usuario mUser = cita.getMedico().getUsuario();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String fecha = f;
            String hora = h;
            String nombreDr = nDr;
            String nombrePcte = nPc;

            String msgPaciente = "";
            String msgStaff = "";
            String asunto = "📅 Notificación de tu Cita - Clínica Real Madrid";

            if (accion.equals("solicitud de reprogramación")) {
                msgPaciente = String.format("Has solicitado cambiar la fecha de tu cita con el Dr. %s. Nueva propuesta: %s a las %s. Pendiente de aprobación.", nombreDr, fecha, hora);
                msgStaff = String.format("El paciente %s ha solicitado reprogramar su cita con el Dr. %s para el día %s.", nombrePcte, nombreDr, fecha);
            } else if (accion.equals("reprogramación aprobada")) {
                msgPaciente = String.format("¡Tu cambio de cita ha sido aprobado! Te esperamos el %s a las %s con el Dr. %s.", fecha, hora, nombreDr);
                msgStaff = String.format("Se ha aprobado la reprogramación de la cita del paciente %s con el Dr. %s.", nombrePcte, nombreDr);
            } else if (accion.equals("reprogramación rechazada")) {
                msgPaciente = String.format("Lo sentimos, no pudimos aprobar tu cambio de cita con el Dr. %s. Tu horario original o solicitud deben ser revisados.", nombreDr);
                msgStaff = String.format("Se ha rechazado una solicitud de reprogramación para el paciente %s.", nombrePcte);
            } else if (accion.equals("solicitud de cancelación")) {
                msgPaciente = "Tu solicitud de cancelación está siendo procesada por nuestro equipo administrativo.";
                msgStaff = String.format("El paciente %s solicita cancelar su cita con el Dr. %s. Motivo: %s", nombrePcte, nombreDr, cita.getMotivo());
            } else if (accion.equals("cancelación aprobada")) {
                msgPaciente = String.format("Tu cita con el Dr. %s ha sido cancelada exitosamente. El proceso de reembolso se ha iniciado.", nombreDr);
                msgStaff = String.format("Confirmado: Cita cancelada para el paciente %s (Dr. %s).", nombrePcte, nombreDr);
            } else if (accion.equals("cancelación rechazada")) {
                msgPaciente = String.format("Tu solicitud de cancelación para la cita con el Dr. %s no ha sido aprobada. Por favor, contáctanos.", nombreDr);
                msgStaff = String.format("Atención: Se ha rechazado la cancelación solicitada por %s.", nombrePcte);
            } else {
                msgPaciente = String.format("Tu cita con el Dr. %s ha tenido una actualización: %s.", nombreDr, accion);
                msgStaff = String.format("Actualización en cita de %s: %s.", nombrePcte, accion);
            }

            // 1. Prioridad Máxima: Enviar Push FCM al celular del paciente vía Firebase
            try {
                Boolean recibirPush = pUser.getRecibirNotificacionesPush();
                if (recibirPush == null || recibirPush) {
                    java.util.List<com.clinica.real.madrid.backend_citas.model.DispositivoActivo> dispositivos = dispositivoActivoRepository.findByUsuarioIdOrderByUltimaConexionDesc(pUser.getId());
                    if (dispositivos != null && !dispositivos.isEmpty()) {
                        String pushTitulo;
                        String pushMensaje;
                        String apellidoDr = cita.getMedico().getUsuario().getApellido();

                        if (accion.equals("confirmada")) {
                            pushTitulo = "Cita Confirmada ✅";
                            pushMensaje = String.format("Tu cita con el Dr. %s ha sido confirmada para el %s a las %s.", apellidoDr, fecha, hora);
                        } else if (accion.equals("cancelada")) {
                            pushTitulo = "Cita Cancelada ❌";
                            pushMensaje = String.format("Lo sentimos, tu cita con el Dr. %s programada para el %s a las %s ha sido cancelada. Por favor contáctanos para más información.", apellidoDr, fecha, hora);
                        } else if (accion.equals("reprogramación aprobada")) {
                            pushTitulo = "Reprogramación Aceptada 🔄";
                            pushMensaje = String.format("Tu cambio de horario fue aprobado. Tu nueva cita ha quedado agendada para el %s a las %s.", fecha, hora);
                        } else if (accion.equals("solicitud de reprogramación rechazada")) {
                            pushTitulo = "Reprogramación Rechazada ⚠️";
                            pushMensaje = String.format("No pudimos aceptar el cambio de horario para tu cita del %s a las %s.", fecha, hora);
                        } else if (accion.equals("cancelación aprobada y reembolso gestionado")) {
                            pushTitulo = "Cancelación Exitosa ✔️";
                            pushMensaje = String.format("Tu solicitud para cancelar la cita del %s a las %s ha sido procesada correctamente.", fecha, hora);
                        } else if (accion.equals("solicitud de cancelación rechazada. Su cita sigue vigente.")) {
                            pushTitulo = "Solicitud Denegada ⚠️";
                            pushMensaje = String.format("Tu solicitud para cancelar la cita del %s a las %s no pudo ser aprobada.", fecha, hora);
                        } else {
                            pushTitulo = null;
                            pushMensaje = null;
                        }

                        if (pushTitulo != null) {
                            for (com.clinica.real.madrid.backend_citas.model.DispositivoActivo disp : dispositivos) {
                                String fcmToken = disp.getFcmToken();
                                if (fcmToken != null && !fcmToken.trim().isEmpty()) {
                                    notificationService.sendNotification(fcmToken, pushTitulo, pushMensaje, "CITA", null);
                                    System.out.println("✅ Push FCM enviado al paciente: " + pUser.getCorreo() + " (Dispositivo: " + disp.getId() + ")");
                                }
                            }
                        }
                    } else {
                        System.out.println("⚠️ Paciente " + pUser.getCorreo() + " no tiene dispositivos activos para Push FCM.");
                    }
                } else {
                    System.out.println("🔇 Push FCM ignorado (El paciente apagó las notificaciones push)");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar Push FCM: " + e.getMessage());
            }

            // 2. Guardar Notificaciones en Base de Datos
            try {
                String tituloNotif = "Aviso de Cita: " + accion.toUpperCase();
                notificacionService.crearNotificacionParaUsuario(tituloNotif, msgPaciente, pUser, cita.getId());
                notificacionService.crearNotificacionParaUsuario("Gestión de Agenda", msgStaff, mUser, cita.getId());

                notificacionService.crearNotificacionParaRol("Aviso Staff", msgStaff, "RECEPCION", cita.getId());
                notificacionService.crearNotificacionParaRol("Aviso Staff", msgStaff, "ADMIN", cita.getId());

                System.out.println(" Notificaciones guardadas en DB para cita " + cita.getId());
            } catch (Exception e) {
                System.err.println("⚠️ No se pudo guardar la notificación en DB: " + e.getMessage());
            }

            // 3. Enviar Correos
            try {
                SimpleMailMessage mailPaciente = new SimpleMailMessage();
                mailPaciente.setFrom("clinicarealmadrid32@gmail.com");
                mailPaciente.setTo(cita.getPaciente().getCorreo());
                mailPaciente.setSubject(asunto);
                mailPaciente.setText(String.format("Hola %s,\n\n%s\n\nSaludos,\nClínica Real Madrid", nombrePcte, msgPaciente));
                mailSender.send(mailPaciente);
            } catch (Exception e) {
                System.err.println("⚠️ No se pudo enviar el correo al paciente: " + e.getMessage());
            }

            try {
                SimpleMailMessage mailMedico = new SimpleMailMessage();
                mailMedico.setFrom("clinicarealmadrid32@gmail.com");
                mailMedico.setTo(cita.getMedico().getUsuario().getCorreo());
                mailMedico.setSubject("🔔 Cambio en tu Agenda Médica");
                mailMedico.setText(String.format("Hola Dr. %s,\n\n%s\n\nSaludos,\nClínica Real Madrid", nombreDr, msgStaff));
                mailSender.send(mailMedico);
            } catch (Exception e) {
                System.err.println("⚠️ No se pudo enviar el correo al médico: " + e.getMessage());
            }
        }, notifExecutor);
    }

    public void notificarDoctorEnSala(Long citaId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                com.clinica.real.madrid.backend_citas.model.Cita cita = citaRepository.findById(citaId).orElse(null);
                if (cita == null) return;
                
                com.clinica.real.madrid.backend_citas.model.Usuario pUser = cita.getPaciente();
                Boolean recibirPush = pUser.getRecibirNotificacionesPush();
                if (recibirPush == null || recibirPush) {
                    java.util.List<com.clinica.real.madrid.backend_citas.model.DispositivoActivo> dispositivos = dispositivoActivoRepository.findByUsuarioIdOrderByUltimaConexionDesc(pUser.getId());
                    if (dispositivos != null && !dispositivos.isEmpty()) {
                        String pushTitulo = "¡El Doctor ya te espera! 👨‍⚕️";
                        String pushMensaje = "El Dr. " + cita.getMedico().getUsuario().getApellido() + " acaba de entrar a la sala de videollamada de tu cita. ¡Conéctate ahora!";
                        for (com.clinica.real.madrid.backend_citas.model.DispositivoActivo disp : dispositivos) {
                            String fcmToken = disp.getFcmToken();
                            if (fcmToken != null && !fcmToken.trim().isEmpty()) {
                                notificationService.sendNotification(fcmToken, pushTitulo, pushMensaje, "VIDEOLLAMADA", String.valueOf(cita.getId()));
                                System.out.println("✅ Push FCM Videollamada enviado al paciente: " + pUser.getCorreo() + " (Dispositivo: " + disp.getId() + ")");
                            }
                        }
                    } else {
                        System.out.println("⚠️ Paciente " + pUser.getCorreo() + " no tiene dispositivos activos para Push FCM Videollamada.");
                    }
                } else {
                    System.out.println("🔇 Push FCM Videollamada ignorado (El paciente apagó las notificaciones push)");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar Push FCM Videollamada: " + e.getMessage());
            }
        }, notifExecutor);
    }

    public void notificarHistorialActualizado(com.clinica.real.madrid.backend_citas.model.Cita cita) {
        if (cita == null) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                com.clinica.real.madrid.backend_citas.model.Usuario pUser = cita.getPaciente();
                Boolean recibirPush = pUser.getRecibirNotificacionesPush();
                if (recibirPush == null || recibirPush) {
                    java.util.List<com.clinica.real.madrid.backend_citas.model.DispositivoActivo> dispositivos = dispositivoActivoRepository.findByUsuarioIdOrderByUltimaConexionDesc(pUser.getId());
                    if (dispositivos != null && !dispositivos.isEmpty()) {
                        String pushTitulo = "Historial Médico Actualizado 📄";
                        String pushMensaje = "El Dr. " + cita.getMedico().getUsuario().getApellido() + " ha guardado tu receta y diagnóstico. Entra a tu aplicación para revisarlo.";
                        for (com.clinica.real.madrid.backend_citas.model.DispositivoActivo disp : dispositivos) {
                            String fcmToken = disp.getFcmToken();
                            if (fcmToken != null && !fcmToken.trim().isEmpty()) {
                                notificationService.sendNotification(fcmToken, pushTitulo, pushMensaje, "HISTORIAL", null);
                                System.out.println("✅ Push FCM Historial enviado al paciente: " + pUser.getCorreo() + " (Dispositivo: " + disp.getId() + ")");
                            }
                        }
                    } else {
                        System.out.println("⚠️ Paciente " + pUser.getCorreo() + " no tiene dispositivos activos para Push FCM Historial.");
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar Push FCM Historial: " + e.getMessage());
            }
        }, notifExecutor);
    }
}
