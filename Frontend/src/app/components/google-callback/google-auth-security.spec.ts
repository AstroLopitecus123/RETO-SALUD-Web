import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { UsuarioService } from '../../services/usuario.service';
import { GoogleCallbackComponent } from './google-callback';
import { AppComponent } from '../../app';
import { NotificationService } from '../../services/notification.service';

describe('Google auth nonce', () => {
  let http: jasmine.SpyObj<HttpClient>;
  let service: UsuarioService;
  const token = (nonce: string) => `header.${btoa(JSON.stringify({ nonce }))}.signature`;

  beforeEach(() => {
    localStorage.removeItem('usuario');
    sessionStorage.removeItem('google_nonce');
    http = jasmine.createSpyObj('HttpClient', ['post']);
    http.post.and.returnValue(of({}));
    service = new UsuarioService(http);
  });

  afterEach(() => {
    sessionStorage.removeItem('google_nonce');
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
  });

  it('sends the expected nonce once and consumes it before sending', () => {
    sessionStorage.setItem('google_nonce', 'expected');
    service.loginConGoogle(token('expected')).subscribe();
    expect(http.post).toHaveBeenCalledWith(jasmine.stringMatching('/auth/google$'), {
      idToken: token('expected'), nonce: 'expected'
    });
    expect(sessionStorage.getItem('google_nonce')).toBeNull();
    http.post.calls.reset();
    service.loginConGoogle(token('expected')).subscribe({ error: () => {} });
    expect(http.post).not.toHaveBeenCalled();
  });

  it('rejects missing, mismatched and malformed responses without a backend request', () => {
    for (const invalid of [token('wrong'), 'malformed', 'a.invalid.c']) {
      sessionStorage.setItem('google_nonce', 'expected');
      let rejected = false;
      service.loginConGoogle(invalid).subscribe({ error: () => { rejected = true; } });
      expect(rejected).toBeTrue();
      expect(sessionStorage.getItem('google_nonce')).toBeNull();
    }
    expect(http.post).not.toHaveBeenCalled();
  });

  it('rejects a valid-looking token without a nonce stored in this tab', () => {
    let rejected = false;
    service.loginConGoogle(token('expected')).subscribe({ error: () => { rejected = true; } });
    expect(rejected).toBeTrue();
    expect(http.post).not.toHaveBeenCalled();
  });

  it('preserves login and registration callbacks while removing tokens from the URL', () => {
    const previousUrl = window.location.href;
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const ns = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);
    const app = { loginActualizado$: new Subject<void>() } as AppComponent;
    const component = new GoogleCallbackComponent(router, service, ns, app);
    const log = spyOn(console, 'log');
    try {
      for (const nonce of ['a'.repeat(64), 'registration-nonce']) {
        http.post.calls.reset();
        http.post.and.returnValue(of({ token: 'app-jwt', nombre: 'Usuario', rol: 'PACIENTE' }));
        sessionStorage.setItem('google_nonce', nonce);
        window.history.replaceState(null, '', `/auth/google/callback#id_token=${token(nonce)}`);
        component.ngOnInit();
        expect(window.location.hash).toBe('');
        expect(window.location.search).toBe('');
        expect(http.post).toHaveBeenCalledWith(jasmine.stringMatching('/auth/google$'), { idToken: token(nonce), nonce });
        expect(localStorage.getItem('token')).toBe('app-jwt');
        expect(sessionStorage.getItem('google_nonce')).toBeNull();
        expect(router.navigate).toHaveBeenCalledWith(['/paciente/dashboard']);
      }
      expect(log).not.toHaveBeenCalled();
    } finally {
      window.history.replaceState(null, '', previousUrl);
    }
  });
});
