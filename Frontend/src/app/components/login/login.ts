import { Component, OnInit, OnDestroy, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { UsuarioService } from '../../services/usuario.service';
import { LoginController } from '../../controller/login.controller';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterModule, CommonModule],
  templateUrl: './login.html',
  styleUrls: ['./login.css']
})
export class LoginComponent implements OnInit, OnDestroy {
  @Output() loginExitoso = new EventEmitter<void>();
  ctrl: LoginController;

  private readonly doctoresLogin: string[] = [
    '/assets/images/DoctorLogin.webp',
    '/assets/images/DoctorLogin2.webp',
    '/assets/images/DoctorLogin3.webp'
  ];

  doctorActual: string = '/assets/images/DoctorLogin.webp';
  desvanecerDoctor: boolean = false;
  private intervaloCambio: any = null;
  private indiceActual: number = 0;

  private readonly GOOGLE_CLIENT_ID = '473447043826-0d5crfghn3m7cug1ibfefnr24lsmp5g8.apps.googleusercontent.com';
  private readonly REDIRECT_URI = `${window.location.origin}/auth/google/callback`;

  constructor(
    private usuarioService: UsuarioService,
    private router: Router,
    private ns: NotificationService
  ) {
    this.ctrl = new LoginController(this.usuarioService, this.router, this.loginExitoso, this.ns);
  }

  ngOnInit(): void {
    // 1. Seleccionar un doctor aleatorio de inicio
    this.indiceActual = Math.floor(Math.random() * this.doctoresLogin.length);
    this.doctorActual = this.doctoresLogin[this.indiceActual];

    // 2. Pre-cargar los demás doctores en segundo plano sin competir con LCP inicial
    this.precargarDoctores();

    // 3. Iniciar rotación periódica respetando preferencias de accesibilidad
    this.iniciarRotacion();
  }

  ngOnDestroy(): void {
    this.detenerRotacion();
  }

  private precargarDoctores(): void {
    if (typeof window !== 'undefined') {
      setTimeout(() => {
        this.doctoresLogin.forEach(src => {
          if (src !== this.doctorActual) {
            const img = new Image();
            img.src = src;
          }
        });
      }, 1500);
    }
  }

  private iniciarRotacion(): void {
    if (typeof window === 'undefined') return;

    // Accesibilidad WCAG (Axe/WAVE): si el usuario tiene activo "reducir movimiento", no rotar automáticamente
    const prefiereMovimientoReducido = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefiereMovimientoReducido) return;

    this.intervaloCambio = setInterval(() => {
      this.cambiarDoctor();
    }, 8000); // Rota suavemente cada 8 segundos
  }

  private detenerRotacion(): void {
    if (this.intervaloCambio) {
      clearInterval(this.intervaloCambio);
      this.intervaloCambio = null;
    }
  }

  private cambiarDoctor(): void {
    this.desvanecerDoctor = true;
    setTimeout(() => {
      this.indiceActual = (this.indiceActual + 1) % this.doctoresLogin.length;
      this.doctorActual = this.doctoresLogin[this.indiceActual];
      setTimeout(() => {
        this.desvanecerDoctor = false;
      }, 50);
    }, 600); // 600ms coincidiendo con la transición CSS
  }

  loginConGoogle(): void {
    const nonce = Array.from(crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join('');
    sessionStorage.setItem('google_nonce', nonce);

    const params = new URLSearchParams({
      client_id: this.GOOGLE_CLIENT_ID,
      redirect_uri: this.REDIRECT_URI,
      response_type: 'id_token',
      scope: 'openid email profile',
      nonce: nonce,
      prompt: 'select_account'
    });

    window.location.href = `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
  }
}
