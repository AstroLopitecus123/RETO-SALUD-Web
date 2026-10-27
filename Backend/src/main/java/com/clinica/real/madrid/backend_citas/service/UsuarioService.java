package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.dto.UsuarioRegistroRequest;
import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.exception.ResourceNotFoundException;
import com.clinica.real.madrid.backend_citas.model.Especialidad;
import com.clinica.real.madrid.backend_citas.model.EstadoUsuario;
import com.clinica.real.madrid.backend_citas.model.Medico;
import com.clinica.real.madrid.backend_citas.model.Pais;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.model.Rol;
import com.clinica.real.madrid.backend_citas.repository.EspecialidadRepository;
import com.clinica.real.madrid.backend_citas.repository.MedicoRepository;
import com.clinica.real.madrid.backend_citas.repository.PaisRepository;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import com.clinica.real.madrid.backend_citas.repository.PagoRepository;
import com.clinica.real.madrid.backend_citas.repository.NotificacionRepository;
import com.clinica.real.madrid.backend_citas.repository.ComprobanteRepository;
import com.clinica.real.madrid.backend_citas.model.Pago;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.DispositivoActivoRepository dispositivoActivoRepository;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private MedicoRepository medicoRepository;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ComprobanteRepository comprobanteRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.CitaRepository citaRepository;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.DisponibilidadRepository disponibilidadRepository;

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${app.frontend.url:https://www.reto-salud.online}")
    private String frontendUrl;

    private final JavaMailSender mailSender;

    @Autowired
    public UsuarioService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CitaService citaService;

    public List<Usuario> listarUsuarios() {
        return usuarioRepository.findAll();
    }

    public boolean existePorCorreo(String correo) {
        return usuarioRepository.existsByCorreo(correo);
    }

    public Usuario obtenerUsuarioPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con ID " + id + " no encontrado"));
    }

    private void validarFortalezaContrasena(String contrasena) {
        if (contrasena == null || contrasena.length() < 8) {
            throw new BadRequestException("La contrasena debe tener al menos 8 caracteres");
        }
        if (contrasena.length() > 72) {
            throw new BadRequestException("La contrasena no puede exceder los 72 caracteres");
        }
        if (!contrasena.matches(".*[A-Z].*")) {
            throw new BadRequestException("La contrasena debe incluir al menos una letra mayuscula");
        }
        if (!contrasena.matches(".*[a-z].*")) {
            throw new BadRequestException("La contrasena debe incluir al menos una letra minuscula");
        }
        if (!contrasena.matches(".*[0-9].*")) {
            throw new BadRequestException("La contrasena debe incluir al menos un numero");
        }
    }

    public Usuario registrarUsuario(UsuarioRegistroRequest request) {

        if (request.getNombre() == null || request.getNombre().isBlank() ||
            request.getApellido() == null || request.getApellido().isBlank() ||
            request.getCorreo() == null || request.getCorreo().isBlank() ||
            request.getContrasena() == null || request.getContrasena().isBlank() ||
            request.getTelefono() == null || request.getTelefono().isBlank() ||
            request.getFechaNacimiento() == null ||
            request.getDni() == null || request.getDni().isBlank() ||
            request.getPaisId() == null) {
            throw new BadRequestException("Todos los campos son obligatorios");
        }

        validarFortalezaContrasena(request.getContrasena());

        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BadRequestException("Ya existe un usuario con ese correo");
        }

        if (usuarioRepository.existsByDni(request.getDni())) {
            throw new BadRequestException("Ya existe un usuario con ese DNI");
        }

        Pais pais = paisRepository.findById(request.getPaisId())
                .orElseThrow(() -> new ResourceNotFoundException("País no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setApellido(request.getApellido());
        usuario.setCorreo(request.getCorreo());
        usuario.setTelefono(request.getTelefono());
        usuario.setDni(request.getDni());
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setPais(pais);
        usuario.setContrasena(passwordEncoder.encode(request.getContrasena()));
        usuario.setRol(Rol.PACIENTE);

        if (request.getConfiguracionVisual() != null) {
            usuario.setConfiguracionVisual(request.getConfiguracionVisual());
        }

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarConfiguracionVisual(Long id, String config) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        usuario.setConfiguracionVisual(config);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void eliminarUsuario(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuario con ID " + id + " no existe");
        }

        dispositivoActivoRepository.deleteByUsuarioId(id);
        medicoRepository.findByUsuarioId(id).ifPresent(medico -> {
            if (citaRepository.existsByMedicoId(medico.getId())) {
                throw new BadRequestException("No se puede eliminar el usuario porque tiene citas registradas como medico");
            }
            disponibilidadRepository.deleteByMedicoId(medico.getId());
            medicoRepository.delete(medico);
        });

        notificacionRepository.deleteByUsuarioDestinoId(id);
        
        List<Pago> pagos = pagoRepository.findByUsuarioId(id);
        for(Pago p : pagos) {
            comprobanteRepository.deleteByPagoId(p.getId());
        }
        
        pagoRepository.deleteByUsuarioId(id);
        citaService.eliminarHistorialesPorUsuario(id);
        citaService.eliminarPorUsuario(id);
        usuarioRepository.deleteById(id);
    }

    public Usuario login(String correo, String contrasenaPlain) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .filter(u -> passwordEncoder.matches(contrasenaPlain, u.getContrasena()))
                .orElseThrow(() -> new BadRequestException("Correo o contraseña incorrectos"));
        if (usuario.getEstado() == EstadoUsuario.DESACTIVADO) {
            throw new BadRequestException("Su cuenta se encuentra desactivada. Comuníquese con la administración.");
        }
        return usuario;
    }

    public Usuario loginConGoogle(String idToken, String expectedNonce) throws Exception {
        if (idToken == null || idToken.isBlank() || expectedNonce == null || expectedNonce.isBlank()
                || expectedNonce.length() > 256) {
            throw new BadRequestException("Token y nonce de Google requeridos");
        }
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken token = verifier.verify(idToken);
        if (token == null) {
            throw new BadRequestException("Token de Google inválido");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        validarNonceGoogle(payload, expectedNonce);
        String email = payload.getEmail();
        String nombre = (String) payload.get("given_name");
        String apellido = (String) payload.get("family_name");

        Usuario usuarioGoogle = usuarioRepository.findByCorreo(email)
                .orElseGet(() -> {
                    Usuario nuevo = new Usuario();
                    nuevo.setCorreo(email);

                    nuevo.setNombre(nombre != null ? nombre : "Usuario");
                    nuevo.setApellido(apellido != null ? apellido : "Google");
                    nuevo.setRol(Rol.PACIENTE);
                    nuevo.setContrasena(passwordEncoder.encode(UUID.randomUUID().toString()));
                    nuevo.setEstado(EstadoUsuario.ACTIVADO);

                    nuevo.setDni("G-" + System.currentTimeMillis() / 1000); 
                    nuevo.setTelefono("000000000");
                    nuevo.setFechaNacimiento(java.time.LocalDate.of(2000, 1, 1));
                    nuevo.setPais(paisRepository.findById(1L).orElse(null));

                    return usuarioRepository.save(nuevo);
                });
        if (usuarioGoogle.getEstado() == EstadoUsuario.DESACTIVADO) {
            throw new BadRequestException("Su cuenta se encuentra desactivada. Comuníquese con la administración.");
        }
        return usuarioGoogle;
    }

    void validarNonceGoogle(GoogleIdToken.Payload payload, String expectedNonce) {
        if (expectedNonce == null || expectedNonce.isBlank() || !expectedNonce.equals(payload.get("nonce"))
                || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadRequestException("Respuesta de Google no valida");
        }
    }

    public Usuario actualizarEstado(Long id, EstadoUsuario nuevoEstado) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        usuario.setEstado(nuevoEstado);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarRol(Long id, String nuevoRol) {
        if (nuevoRol == null || nuevoRol.isBlank()) {
            throw new BadRequestException("El rol no puede estar vacío");
        }
        nuevoRol = nuevoRol.toUpperCase();

        Rol rolEnum;
        try {
            rolEnum = Rol.valueOf(nuevoRol);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol no permitido: " + nuevoRol);
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Rol rolAnterior = usuario.getRol(); 
        usuario.setRol(rolEnum);
        usuarioRepository.save(usuario);

        if (rolEnum == Rol.MEDICO && rolAnterior != Rol.MEDICO) {
            boolean existeMedico = medicoRepository.existsByUsuarioId(id);
            if (!existeMedico) {
                Especialidad defaultEsp = especialidadRepository.findById(1L).orElseGet(() -> especialidadRepository.findAll().stream().findFirst().orElse(null));
                Medico medico = new Medico();
                medico.setUsuario(usuario);
                medico.setEspecialidad(defaultEsp);
                medicoRepository.save(medico);
            }
        }

        if (rolAnterior == Rol.MEDICO && rolEnum != Rol.MEDICO) {
            medicoRepository.findByUsuarioId(id).ifPresent(medico -> {
                if (citaRepository.existsByMedicoId(medico.getId())) {
                    throw new BadRequestException("No se puede cambiar el rol de un medico con citas asociadas");
                }
                disponibilidadRepository.deleteByMedicoId(medico.getId());
                medicoRepository.delete(medico);
            });
        }

        return usuario;
    }

    public Usuario actualizarUsuario(Long id, Usuario datos) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (datos.getNombre() != null && !datos.getNombre().trim().isEmpty()) {
            usuario.setNombre(datos.getNombre().trim());
        }
        if (datos.getApellido() != null && !datos.getApellido().trim().isEmpty()) {
            usuario.setApellido(datos.getApellido().trim());
        }
        if (datos.getCorreo() != null && !datos.getCorreo().trim().equalsIgnoreCase(usuario.getCorreo().trim())) {
            if (usuarioRepository.existsByCorreo(datos.getCorreo().trim().toLowerCase())) {
                throw new BadRequestException("El correo electronico ya esta registrado por otra cuenta");
            }
            usuario.setCorreo(datos.getCorreo().trim().toLowerCase());
        }
        if (datos.getTelefono() != null) {
            usuario.setTelefono(datos.getTelefono().trim());
        }
        if (datos.getFechaNacimiento() != null) {
            usuario.setFechaNacimiento(datos.getFechaNacimiento());
        }
        if (datos.getDni() != null) {
            String dniStr = datos.getDni().trim();
            if (!dniStr.isEmpty()) {
                if (!dniStr.equalsIgnoreCase(usuario.getDni()) && usuarioRepository.existsByDni(dniStr)) {
                    throw new BadRequestException("El DNI ya esta registrado por otra cuenta");
                }
                usuario.setDni(dniStr);
            }
        }
        
        if (datos.getFotoUrl() != null && !datos.getFotoUrl().trim().isEmpty()) {
            usuario.setFotoUrl(datos.getFotoUrl().trim());
        }

        if (datos.getPais() != null && datos.getPais().getId() != null && datos.getPais().getId() > 0) {
            Pais pais = paisRepository.findById(datos.getPais().getId())
                    .orElseThrow(() -> new RuntimeException("País no encontrado"));
            usuario.setPais(pais);
        }

        return usuarioRepository.save(usuario);
    }

    public void enviarCorreoRecuperacion(String correo) {
        if (correo == null || correo.isBlank()) {
            return;
        }
        java.util.Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(correo.toLowerCase().trim());
        if (usuarioOpt.isEmpty()) {
            return;
        }
        Usuario usuario = usuarioOpt.get();
        if (usuario.getEstado() == EstadoUsuario.DESACTIVADO) {
            return;
        }

        String token = UUID.randomUUID().toString();
        usuario.setTokenRecuperacion(token);
        usuario.setTokenExpira(LocalDateTime.now(java.time.ZoneId.systemDefault()).plusHours(1));
        usuarioRepository.save(usuario);

        String enlace = frontendUrl + "/restablecer?token=" + token;

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(usuario.getCorreo());
        mensaje.setSubject("Recuperación de contraseña - Clínica Real Madrid");
        mensaje.setText("""
                Hola, %s.
                Has solicitado restablecer tu contraseña.
                Ingresa al siguiente enlace para continuar (válido por 1 hora):
                %s

                Si no solicitaste esto, ignora este mensaje.
                """.formatted(usuario.getNombre(), enlace));

        mailSender.send(mensaje);
    }

    @Transactional
    public void restablecerContrasena(String token, String nuevaContrasena) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Token requerido");
        }
        Usuario usuario = usuarioRepository.findByTokenRecuperacion(token)
                .orElseThrow(() -> new RuntimeException("Token inválido o expirado."));

        if (usuario.getTokenExpira() == null || !usuario.getTokenExpira().isAfter(LocalDateTime.now(java.time.ZoneId.systemDefault()))) {
            throw new RuntimeException("El token ha expirado.");
        }
        validarFortalezaContrasena(nuevaContrasena);

        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuario.revocarTokens();
        usuario.setTokenRecuperacion(null);
        usuario.setTokenExpira(null);

        usuarioRepository.save(usuario);
    }

    @Transactional
    public void cambiarPassword(Long id, String actualPassword, String nuevaPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(actualPassword, usuario.getContrasena())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        validarFortalezaContrasena(nuevaPassword);
        usuario.setContrasena(passwordEncoder.encode(nuevaPassword));
        usuario.revocarTokens();
        usuario.setTokenRecuperacion(null);
        usuario.setTokenExpira(null);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarFoto(Long id, String url) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        usuario.setFotoUrl(url);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarFcmToken(Long id, String fcmToken) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                
        com.clinica.real.madrid.backend_citas.model.DispositivoActivo dispositivo = dispositivoActivoRepository.findByFcmToken(fcmToken)
                .orElse(new com.clinica.real.madrid.backend_citas.model.DispositivoActivo(usuario, fcmToken));
                
        dispositivo.setUsuario(usuario);
        dispositivo.setUltimaConexion(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        dispositivoActivoRepository.save(dispositivo);
        
        java.util.List<com.clinica.real.madrid.backend_citas.model.DispositivoActivo> dispositivos = dispositivoActivoRepository.findByUsuarioIdOrderByUltimaConexionDesc(id);
        if (dispositivos.size() > 5) {
            for (int i = 5; i < dispositivos.size(); i++) {
                dispositivoActivoRepository.delete(dispositivos.get(i));
                System.out.println("🧹 Limpieza inteligente: Borrando dispositivo excedente para el usuario ID " + id);
            }
        }
        return usuario;
    }

    @Transactional
    public void borrarFcmToken(String fcmToken) {
        borrarFcmToken(fcmToken, null);
    }

    @Transactional
    public void borrarFcmToken(String fcmToken, Long usuarioId) {
        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            if (usuarioId != null) {
                dispositivoActivoRepository.deleteByFcmTokenAndUsuarioId(fcmToken, usuarioId);
            } else {
                dispositivoActivoRepository.deleteByFcmToken(fcmToken);
            }
        }
    }

    @Transactional
    public void borrarTokenMuerto(String fcmToken) {
        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            dispositivoActivoRepository.deleteByFcmToken(fcmToken);
            System.out.println("🧹 Limpieza automática: Token FCM muerto borrado.");
        }
    }

    @Transactional
    public Usuario actualizarPreferenciasPush(Long id, boolean recibir) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        usuario.setRecibirNotificacionesPush(recibir);
        return usuarioRepository.save(usuario);
    }
}

