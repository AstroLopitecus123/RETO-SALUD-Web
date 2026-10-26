package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.dto.PasswordChangeRequest;
import com.clinica.real.madrid.backend_citas.dto.UsuarioRegistroRequest;
import com.clinica.real.madrid.backend_citas.model.EstadoUsuario;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.UsuarioService;
import com.clinica.real.madrid.backend_citas.service.CloudinaryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.clinica.real.madrid.backend_citas.security.JwtUtil;

@RestController
@RequestMapping("/api/usuarios")
 
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final CloudinaryService cloudinaryService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    public UsuarioController(UsuarioService usuarioService, CloudinaryService cloudinaryService) {
        this.usuarioService = usuarioService;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Usuario>> listarUsuarios() {
        List<Usuario> usuarios = usuarioService.listarUsuarios();
        usuarios.forEach(u -> u.setContrasena(null)); 
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<Usuario> obtenerUsuario(@PathVariable Long id) {
        Usuario usuario = usuarioService.obtenerUsuarioPorId(id);
        usuario.setContrasena(null);
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/registrar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Usuario> registrarUsuario(@RequestBody UsuarioRegistroRequest request) {
        Usuario usuario = usuarioService.registrarUsuario(request);
        usuario.setContrasena(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional  
    public ResponseEntity<String> eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.ok("Usuario y sus citas eliminados correctamente");
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Usuario> actualizarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String estadoStr = body.get("estado");

        EstadoUsuario nuevoEstado = EstadoUsuario.valueOf(estadoStr.toUpperCase());
        Usuario usuarioActualizado = usuarioService.actualizarEstado(id, nuevoEstado);

        return ResponseEntity.ok(usuarioActualizado);
    }

    @PutMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Usuario> actualizarRol(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nuevoRol = body.get("rol");
        Usuario usuarioActualizado = usuarioService.actualizarRol(id, nuevoRol);
        return ResponseEntity.ok(usuarioActualizado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody Usuario usuario) {
            
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario oldUser = usuarioService.obtenerUsuarioPorId(id);
        boolean isOwner = auth != null && auth.getName().equals(oldUser.getCorreo());
        
        Usuario actualizado = usuarioService.actualizarUsuario(id, usuario);
        
        Map<String, Object> response = new HashMap<>();
        response.put("usuario", actualizado);
        
        if (isOwner) {
            String nuevoToken = jwtUtil.generateToken(actualizado.getCorreo());
            response.put("token", nuevoToken);
        }
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/configuracion-visual")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<Usuario> actualizarConfiguracionVisual(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String config = body.get("configuracionVisual");
        Usuario usuario = usuarioService.actualizarConfiguracionVisual(id, config);
        return ResponseEntity.ok(usuario);
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("#id == authentication.principal.id")
    public ResponseEntity<String> cambiarPassword(
            @PathVariable Long id,
            @RequestBody PasswordChangeRequest request) {
        usuarioService.cambiarPassword(id, request.getActualPassword(), request.getNuevapassword());
        return ResponseEntity.ok("Contraseña actualizada exitosamente");
    }

    @PostMapping("/{id}/foto")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<?> subirFoto(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo no puede estar vacio.");
        }
        String contentType = archivo.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") && !contentType.equalsIgnoreCase("image/png") && !contentType.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest().body("Solo se permiten imagenes en formato JPEG, PNG o WebP.");
        }
        if (archivo.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body("La imagen no debe superar los 5 MB.");
        }
            Map uploadResult = cloudinaryService.upload(archivo);
            String secureUrl = (String) uploadResult.get("secure_url");
            Usuario usuario = usuarioService.actualizarFoto(id, secureUrl);
            usuario.setContrasena(null);
            return ResponseEntity.ok(usuario);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al subir la imagen a Cloudinary: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/fcm-token")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<Usuario> actualizarFcmToken(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String fcmToken = body.get("fcmToken");
        Usuario usuarioActualizado = usuarioService.actualizarFcmToken(id, fcmToken);
        usuarioActualizado.setContrasena(null);
        return ResponseEntity.ok(usuarioActualizado);
    }

    @DeleteMapping("/fcm-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> borrarFcmToken(@RequestParam("token") String token, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            usuarioService.borrarFcmToken(token, principal.getId());
        } else {
            usuarioService.borrarFcmToken(token);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/preferencias-push")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<Usuario> actualizarPreferenciasPush(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean recibir = body.get("recibirNotificacionesPush");
        if (recibir == null) recibir = true;
        Usuario usuarioActualizado = usuarioService.actualizarPreferenciasPush(id, recibir);
        usuarioActualizado.setContrasena(null);
        return ResponseEntity.ok(usuarioActualizado);
    }
}

