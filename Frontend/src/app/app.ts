import { Component, ViewEncapsulation } from '@angular/core';
import { Router, NavigationStart, NavigationEnd, NavigationCancel, NavigationError, RouterOutlet, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Usuario, LoginResponse, RolUsuario } from './models/tipos';
import { Observable, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ChatbotComponent } from './components/chatbot/chatbot';
import { ToastComponent } from './components/toast/toast';
import { AccesibilidadComponent } from './components/accesibilidad/accesibilidad';
import { NotificationService } from './services/notification.service';
import { NotificacionService } from './services/notificacion.service';

import { ViewChild } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule, ChatbotComponent, ToastComponent, AccesibilidadComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
  encapsulation: ViewEncapsulation.None
})
export class AppComponent {
  @ViewChild(AccesibilidadComponent) accesibilidad!: AccesibilidadComponent;
  title = 'RETO Salud';
  usuario: Usuario | null = null;
  isLoggedIn = false;

  get isTeleconsultaRoute(): boolean {
    return this.router.url.includes('/teleconsulta');
  }
  mostrarModal = false;
  loading = false;
  isMenuOpen = false;
  loginActualizado$ = new Subject<void>();
  isPanelRoute = false;
  notificacionesNoLeidas = 0;
  inactividadTimer: any;
  countdownTimer: any;
  mostrarModalInactividad = false;
  inactividadCountdown = 30;

  constructor(
    private router: Router, 
    private http: HttpClient,
    private ns: NotificationService,
    private notificacionService: NotificacionService
  ) {
    this.verificarLogin();

    this.actualizarEstadoPanel(window.location.pathname);

    this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {

        const willBePanelRoute = this.checkIfUrlIsPanel(event.url);

        if (this.isPanelRoute !== willBePanelRoute || event.id === 1) {
          this.loading = true;
          console.log(' Cambio de contexto detectado — Activando Preloader Premium');
        }
      } 
      else if (event instanceof NavigationEnd) {

        this.actualizarEstadoPanel(event.urlAfterRedirects);
        setTimeout(() => {
          this.loading = false;
        }, 800);
      } 
      else if (event instanceof NavigationCancel || event instanceof NavigationError) {
        this.loading = false;
      }
    });

    this.loginActualizado$.subscribe(() => {
      console.log(" Login detectado — actualizando header sin recargar");
      this.verificarLogin();
      this.cargarConteoNotificaciones();
    });

    this.notificacionService.refreshObservable.subscribe(() => {
      this.cargarConteoNotificaciones();
    });

    setInterval(() => {
      if (this.isLoggedIn && this.isPanelRoute) {
        this.cargarConteoNotificaciones();
      }
    }, 30000);

    // Detectar cambios en el usuario desde otras partes de la app (ej: actualización de foto en mi-perfil)
    window.addEventListener('usuarioActualizado', (event: Event) => {
      const customEvent = event as CustomEvent;
      if (customEvent.detail) {
        try {
          const parsedUsuario = customEvent.detail;
          parsedUsuario.rol = this.normalizarRol(parsedUsuario.rol);
          this.usuario = { ...parsedUsuario };
        } catch (e) {
          console.error('Error al actualizar usuario desde evento', e);
        }
      }
    });
  }

  abrirModalCerrarSesion() {
    this.mostrarModal = true;
    this.isMenuOpen = false; 
  }

  cancelarCerrarSesion() {
    this.mostrarModal = false;
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  cerrarSesion() {
    this.mostrarModal = false;
    this.detenerMonitoreoInactividad();
    this.ns.info('Cerrando sesión...');

    this.router.navigate(['/']).then(() => {
      localStorage.removeItem('usuario');
      localStorage.removeItem('token');
      sessionStorage.clear();

      this.usuario = null;
      this.isLoggedIn = false;
      if (typeof document !== 'undefined') {
        document.body.classList.remove('usuario-logueado');
        document.body.classList.remove('en-panel');
      }
      console.log(' Sesión limpiada correctamente tras navegación');
    });
  }

  private checkIfUrlIsPanel(url: string): boolean {
    const panelKeywords = [
      '/admin', '/medico', '/recepcion', '/paciente', 
      '/mis-citas', '/mi-historial', '/mi-perfil', '/notificaciones',
      '/usuarios', '/medicos', '/especialidades', '/historial-clinico',
      '/checkout', '/pagar-tarjeta', '/gestionar-disponibilidad'
    ];
    return panelKeywords.some(keyword => url.includes(keyword));
  }

  private actualizarEstadoPanel(url: string) {
    this.isPanelRoute = this.checkIfUrlIsPanel(url);
    if (typeof document !== 'undefined') {
      if (this.isPanelRoute) {
        document.body.classList.add('en-panel');
      } else {
        document.body.classList.remove('en-panel');
      }
      if (this.isLoggedIn) {
        document.body.classList.add('usuario-logueado');
      } else {
        document.body.classList.remove('usuario-logueado');
      }
    }
    console.log(' Estado Panel Actualizado:', this.isPanelRoute, 'para URL:', url);
  }

  getDashboardRoute(): string {
    if (!this.usuario) return '/login';
    const rol = this.usuario.rol?.toUpperCase();
    if (rol === 'ADMIN') return '/admin';
    if (rol === 'MEDICO') return '/medico/dashboard';
    if (rol === 'RECEPCION') return '/recepcion/dashboard';
    if (rol === 'PACIENTE') return '/paciente/dashboard';
    return '/paciente/dashboard'; 
  }

  verificarLogin() {
    const usuarioStr = localStorage.getItem('usuario');
    // Verificando sesion de usuario

    if (usuarioStr) {
      try {
        const parsedUsuario: Usuario = JSON.parse(usuarioStr);
        

        parsedUsuario.rol = this.normalizarRol(parsedUsuario.rol);
        

        this.usuario = parsedUsuario;
        this.isLoggedIn = true;
        if (typeof document !== 'undefined') {
          document.body.classList.add('usuario-logueado');
        }

        if (this.usuario['configuracionVisual']) {
          this.aplicarFiltroGlobal(this.usuario['configuracionVisual']);
        }
      } catch (e) {
        console.error('❌ Error al parsear usuario desde localStorage', e);
        this.usuario = null;
        this.isLoggedIn = false;
      }
    } else {
      console.log('⚠️ No hay usuario en localStorage');
      this.usuario = null;
      this.isLoggedIn = false;
      if (typeof document !== 'undefined') {
        document.body.classList.remove('usuario-logueado');
      }

      const filtroAnon = localStorage.getItem('accesibilidad-filtro');
      if (filtroAnon) this.aplicarFiltroGlobal(filtroAnon);
    }
    if (this.isLoggedIn) {
      this.iniciarMonitoreoInactividad();
    } else {
      this.detenerMonitoreoInactividad();
    }
  }

  private aplicarFiltroGlobal(tipo: string) {
    const filters: any = {
      'DEUTERANOPIA': 'url(#deuteranopia)',
      'PROTANOPIA': 'url(#protanopia)',
      'TRITANOPIA': 'url(#tritanopia)',
      'DEUTERANOMALIA': 'url(#deuteranomalia)',
      'PROTANOMALIA': 'url(#protanomalia)',
      'ACROMATOPSIA': 'grayscale(100%)',
      'NINGUNO': 'none'
    };
    document.documentElement.style.filter = filters[tipo] || 'none';
  }

  private normalizarRol(rol?: string): RolUsuario {
    console.log(' Rol recibido para normalizar:', rol);
    const rolUpper = rol?.toUpperCase();
    if (
      rolUpper === 'ADMIN' ||
      rolUpper === 'MEDICO' ||
      rolUpper === 'RECEPCION' ||
      rolUpper === 'PACIENTE'
    ) {
      console.log(' Rol válido:', rolUpper);
      return rolUpper as RolUsuario;
    }
    console.log('⚠️ Rol inválido, asignando PACIENTE');
    return 'PACIENTE';
  }

  login(datos: { correo: string; contrasena: string }): Observable<LoginResponse> {
  // Proceso de autenticacion seguro

  return this.http.post<LoginResponse>(`/api/auth/login`, datos).pipe(
    tap(res => {
      // Sesion iniciada correctamente

      if (res.token) {
        localStorage.setItem('token', res.token);
        // Token almacenado de forma segura
      }

      if (res.usuario) {
        

        res.usuario.rol = this.normalizarRol(res.usuario.rol);
        console.log(' Rol normalizado antes de guardar:', res.usuario.rol);

        localStorage.setItem('usuario', JSON.stringify(res.usuario));

        this.usuario = { ...res.usuario };
        this.isLoggedIn = true;

        this.loginActualizado$.next();

        if (!res.usuario['configuracionVisual'] || res.usuario['configuracionVisual'] === 'NINGUNO') {
          setTimeout(() => {
            if (this.accesibilidad) {
              console.log(' Sugiriendo Asistente de Accesibilidad a nuevo usuario');
              this.accesibilidad.abrirAsistente(true);
            }
          }, 2000);
        }
      }
    })
  );
}

  onRouteChange(component: any) {

    if (component?.loginExitoso) {
      component.loginExitoso.subscribe(() => {
        console.log(" Login detectado — actualizando usuario sin recargar");
        this.verificarLogin();
      });
    }
  }

  irAHome(e: Event) {
    e.preventDefault();
    this.isMenuOpen = false;
    this.router.navigate(['/']);
  }

  cargarConteoNotificaciones() {
    if (!this.isLoggedIn) return;
    this.notificacionService.contarNoLeidas().subscribe({
      next: (res) => { this.notificacionesNoLeidas = res.cantidad; },
      error: () => {  }
    });
  }

  iniciarMonitoreoInactividad() {
    this.detenerMonitoreoInactividad();
    const eventos = ['mousemove', 'click', 'keypress', 'touchstart', 'scroll'];
    eventos.forEach(evt => {
      window.addEventListener(evt, this.resetearInactividad);
    });
    this.resetearInactividad();
  }

  detenerMonitoreoInactividad() {
    const eventos = ['mousemove', 'click', 'keypress', 'touchstart', 'scroll'];
    eventos.forEach(evt => {
      window.removeEventListener(evt, this.resetearInactividad);
    });
    if (this.inactividadTimer) clearTimeout(this.inactividadTimer);
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.mostrarModalInactividad = false;
  }

  resetearInactividad = () => {
    if (this.mostrarModalInactividad) return;
    if (this.inactividadTimer) clearTimeout(this.inactividadTimer);
    if (this.isLoggedIn) {
      this.inactividadTimer = setTimeout(() => {
        this.mostrarAdvertenciaInactividad();
      }, 180000);
    }
  }

  mostrarAdvertenciaInactividad() {
    this.mostrarModalInactividad = true;
    this.inactividadCountdown = 30;
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.countdownTimer = setInterval(() => {
      this.inactividadCountdown--;
      if (this.inactividadCountdown <= 0) {
        clearInterval(this.countdownTimer);
        this.cerrarSesionInactividad();
      }
    }, 1000);
  }

  mantenerSesion() {
    this.mostrarModalInactividad = false;
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.resetearInactividad();
  }

  cerrarSesionInactividad() {
    this.mostrarModalInactividad = false;
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.cerrarSesion();
    this.ns.info('Tu sesión ha expirado por inactividad.');
  }
}
