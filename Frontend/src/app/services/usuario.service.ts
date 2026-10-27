import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, throwError } from 'rxjs';
import { tap, map } from 'rxjs/operators';
import { LoginResponse, Usuario } from '../models/usuario.model';
import { UsuarioFull } from '../models/usuario-full.model';
import { environment } from '../../environments/environment';
import { Pais } from '../models/pais.model';

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {

  private usuariosUrl = `${environment.apiUrl}/api/usuarios`;
  private authUrl = `${environment.apiUrl}/auth`;

  private usuarioSubject = new BehaviorSubject<Usuario | null>(this.cargarUsuarioLocalStorage());
  usuario$ = this.usuarioSubject.asObservable();

  constructor(private http: HttpClient) {}

  limpiarSesion(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    sessionStorage.clear();
    this.usuarioSubject.next(null);
  }

  private cargarUsuarioLocalStorage(): Usuario | null {
    const user = localStorage.getItem('usuario');
    return user ? JSON.parse(user) : null;
  }

  listarUsuarios(): Observable<UsuarioFull[]> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    return this.http.get<UsuarioFull[]>(this.usuariosUrl, { headers });
  }

  crearUsuario(usuario: Usuario): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.authUrl}/registro`, usuario);
  }

  eliminarUsuario(id: number): Observable<string> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    return this.http.delete(`${this.usuariosUrl}/${id}`, { headers, responseType: 'text' });
  }

  actualizarEstado(id: number, nuevoEstado: string): Observable<any> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    return this.http.put(`${this.usuariosUrl}/${id}/estado`, { estado: nuevoEstado }, { headers });
  }

  actualizarRol(id: number, nuevoRol: string): Observable<any> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    return this.http.put(`${this.usuariosUrl}/${id}/rol`, { rol: nuevoRol }, { headers });
  }

  login(datos: { correo: string, contrasena: string }): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/login`, datos)
      .pipe(
        tap(res => {

          if (res.token) {
            localStorage.setItem('token', res.token);
          }

          if (res && res.token) {
            const usuario = new Usuario(res);

            const rolBackend = res.rol?.toUpperCase();
            if (rolBackend === 'ADMIN' || rolBackend === 'MEDICO' || rolBackend === 'RECEPCION') {
              usuario.rol = rolBackend as any;
            } else {
              usuario.rol = 'PACIENTE' as any;
            }

            localStorage.setItem('usuario', JSON.stringify(usuario));

            this.usuarioSubject.next(usuario);
          }
        })
      );
  }

  actualizarUsuario(usuario: UsuarioFull): Observable<UsuarioFull> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };

    return this.http.put<any>(`${this.usuariosUrl}/${usuario.id}`, usuario, { headers }).pipe(
      map(response => {
        if (response && response.usuario) {
          if (response.token) {
            localStorage.setItem('token', response.token);
          }
          return response.usuario as UsuarioFull;
        }
        return response as UsuarioFull;
      })
    );
  }

  listarPaises(): Observable<Pais[]> {
    return this.http.get<Pais[]>(`${environment.apiUrl}/api/paises`);
  }

  obtenerUsuario(id: number) {
    const token = localStorage.getItem('token');
    const headers = { 'Authorization': `Bearer ${token}` };

    return this.http.get<Usuario>(`${environment.apiUrl}/api/usuarios/${id}`, { headers });
  }

  recuperarContrasena(correo: string): Observable<any> {
    return this.http.post(`${this.authUrl}/recuperar`, { correo });
  }

  restablecerContrasena(token: string, nuevaContrasena: string): Observable<any> {
    return this.http.post(`${this.authUrl}/restablecer`, { token, nuevaContrasena });
  }

  cambiarPassword(id: number, actualPassword: string, nuevaPassword: string): Observable<string> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    const body = { actualPassword, nuevaPassword };
    return this.http.put(`${this.usuariosUrl}/${id}/password`, body, { headers, responseType: 'text' });
  }

  loginConGoogle(idToken: string): Observable<LoginResponse> {
    const nonce = sessionStorage.getItem('google_nonce');
    sessionStorage.removeItem('google_nonce');
    try {
      const parts = idToken.split('.');
      if (parts.length !== 3) throw new Error('Token invalido');
      const encoded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '=')));
      // Vincula la respuesta a esta pestana; la firma se verifica solo en backend.
      if (!nonce || payload.nonce !== nonce) throw new Error('Nonce invalido');
    } catch {
      return throwError(() => new Error('Respuesta de Google no valida. Inicia sesion nuevamente.'));
    }
    return this.http.post<LoginResponse>(`${this.authUrl}/google`, { idToken, nonce })
      .pipe(
        tap(res => {
          if (res.token) {
            localStorage.setItem('token', res.token);
          }
          if (res && res.token) {
            const usuario = new Usuario(res);
            const rolBackend = res.rol?.toUpperCase();
            if (rolBackend === 'ADMIN' || rolBackend === 'MEDICO' || rolBackend === 'RECEPCION') {
              usuario.rol = rolBackend as any;
            } else {
              usuario.rol = 'PACIENTE' as any;
            }
            localStorage.setItem('usuario', JSON.stringify(usuario));
            this.usuarioSubject.next(usuario);
          }
        })
      );
  }

  subirFoto(id: number, archivo: File): Observable<any> {
    const token = localStorage.getItem('token') || '';
    const headers = { Authorization: `Bearer ${token}` };
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post(`${this.usuariosUrl}/${id}/foto`, formData, { headers });
  }
}
