import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AppComponent } from '../../app';
import { NotificationService } from '../../services/notification.service';
import { UsuarioService } from '../../services/usuario.service';
import { MiPerfilComponent } from './mi-perfil';

describe('Profile password session revocation', () => {
  let component: MiPerfilComponent;
  let service: UsuarioService;
  let http: jasmine.SpyObj<HttpClient>;
  let router: jasmine.SpyObj<Router>;
  let app: jasmine.SpyObj<AppComponent>;
  let notifications: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    localStorage.setItem('token', 'old-token');
    localStorage.setItem('usuario', JSON.stringify({ id: 1 }));
    localStorage.setItem('DOCGOSI_VOICE_ACTIVE', 'true');
    sessionStorage.setItem('google_nonce', 'pending');
    http = jasmine.createSpyObj('HttpClient', ['put']);
    service = new UsuarioService(http);
    router = jasmine.createSpyObj('Router', ['navigate']);
    app = jasmine.createSpyObj('AppComponent', ['verificarLogin']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    component = new MiPerfilComponent(service, notifications, router, app);
    component.usuario.id = 1;
    component.pwdActual = 'ActualClave123';
    component.pwdNueva = component.pwdConfirmar = 'NuevaClave123';
    component.mostrarModalPassword = true;
  });

  afterEach(() => {
    service.limpiarSesion();
    localStorage.removeItem('DOCGOSI_VOICE_ACTIVE');
  });

  it('clears only after server success, updates global state and navigates explicitly to login', () => {
    const response = new Subject<string>();
    http.put.and.returnValue(response);
    let user: unknown;
    service.usuario$.subscribe(value => { user = value; });
    component.ejecutarCambioPassword();
    expect(localStorage.getItem('token')).toBe('old-token');
    expect(router.navigate).not.toHaveBeenCalled();
    response.next('ok');
    response.complete();
    expect(http.put.calls.mostRecent().args as unknown[]).toEqual([jasmine.stringMatching('/1/password$'), {
      actualPassword: 'ActualClave123', nuevaPassword: 'NuevaClave123'
    }, { headers: { Authorization: 'Bearer old-token' }, responseType: 'text' }]);
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('usuario')).toBeNull();
    expect(sessionStorage.getItem('google_nonce')).toBeNull();
    expect(localStorage.getItem('DOCGOSI_VOICE_ACTIVE')).toBe('true');
    expect(user).toBeNull();
    expect(component.pwdActual + component.pwdNueva + component.pwdConfirmar).toBe('');
    expect(component.mostrarModalPassword).toBeFalse();
    expect(app.verificarLogin).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledOnceWith(['/login'], { replaceUrl: true });
  });

  it('does not clear the session if the password change fails', () => {
    http.put.and.returnValue(throwError(() => ({ error: { message: 'Contrasena incorrecta' } })));
    component.ejecutarCambioPassword();
    expect(localStorage.getItem('token')).toBe('old-token');
    expect(app.verificarLogin).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith('Contrasena incorrecta');
  });

  it('rejects mismatched passwords without requesting or logging out', () => {
    component.pwdConfirmar = 'OtraClave123';
    http.put.and.returnValue(of('ok'));
    component.ejecutarCambioPassword();
    expect(http.put).not.toHaveBeenCalled();
    expect(localStorage.getItem('token')).toBe('old-token');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
