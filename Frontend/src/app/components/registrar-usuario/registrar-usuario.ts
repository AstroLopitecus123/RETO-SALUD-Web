import { Component, OnInit, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { RegistrarUsuarioController } from '../../controller/registrar-usuario.controller';
import { NotificationService } from '../../services/notification.service';
import { AppComponent } from '../../app';
import { NarratorDirective } from '../../directives/narrator.directive';
import { UsuarioService } from '../../services/usuario.service';

@Component({
  selector: 'app-registrar-usuario',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterModule, NarratorDirective],
  templateUrl: './registrar-usuario.html',
  styleUrls: ['./registrar-usuario.css'],
  providers: [RegistrarUsuarioController]
})
export class RegistrarUsuarioComponent implements OnInit {

  private readonly GOOGLE_CLIENT_ID = '473447043826-0d5crfghn3m7cug1ibfefnr24lsmp5g8.apps.googleusercontent.com';
  private readonly REDIRECT_URI = `${window.location.origin}/auth/google/callback`;

  constructor(
    public ctrl: RegistrarUsuarioController,
    private router: Router,
    private ns: NotificationService,
    private app: AppComponent,
    private usuarioService: UsuarioService,
    private ngZone: NgZone
  ) {
    this.ctrl.setUtils(this.router, this.ns, this.app);
  }

  ngOnInit(): void {
    this.ctrl.precargarRecaptcha();
  }

  registrarConGoogle(): void {
    const nonce = crypto.randomUUID();
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

