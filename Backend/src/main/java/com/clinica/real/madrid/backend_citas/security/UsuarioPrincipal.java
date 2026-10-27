package com.clinica.real.madrid.backend_citas.security;

import com.clinica.real.madrid.backend_citas.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean activo;
    private long tokenVersion;

    public long getTokenVersion() { return tokenVersion; }

    public UsuarioPrincipal(Long id, String email, String password, Collection<? extends GrantedAuthority> authorities) {
        this(id, email, password, authorities, true);
    }

    public UsuarioPrincipal(Long id, String email, String password, Collection<? extends GrantedAuthority> authorities, boolean activo) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.activo = activo;
    }

    public static UsuarioPrincipal fromUsuario(Usuario usuario) {
        String rol = usuario.getRol() != null ? usuario.getRol().name() : "PACIENTE";

        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + rol)
        );

        boolean activo = usuario.getEstado() == null || usuario.getEstado() != com.clinica.real.madrid.backend_citas.model.EstadoUsuario.DESACTIVADO;
        UsuarioPrincipal principal = new UsuarioPrincipal(
                usuario.getId(),
                usuario.getCorreo(),
                usuario.getContrasena(),
                authorities,
                activo
        );
        principal.tokenVersion = usuario.getTokenVersion();
        return principal;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.activo;
    }
}
