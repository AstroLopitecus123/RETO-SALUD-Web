import { Injectable } from '@angular/core';
import { UsuarioService } from '../services/usuario.service';
import { PaisService } from '../services/pais.service';
import { RecaptchaService } from '../services/recaptcha.service';
import { Usuario } from '../models/usuario.model';
import { Pais } from '../models/pais.model';
import { Router } from '@angular/router';
import { NotificationService } from '../services/notification.service';
import { AppComponent } from '../app';

@Injectable()
export class RegistrarUsuarioController {

  private readonly MENSAJES = {
    registroExitoso: 'Usuario registrado correctamente',
    completarCampos: 'Por favor completa correctamente todos los campos'
  };
  confirmarContrasena: string = '';
  private readonly defaultUsuario = {
    nombre: '',
    apellido: '',
    correo: '',
    correoUsuario: '',
    contrasena: '',
    rol: 'PACIENTE' as const,
    paisId: undefined,
    fechaNacimiento: '',
    dni: '',
    telefono: '',
    configuracionVisual: 'NINGUNO' as const
  };

  private readonly defaultTouched = {
    nombre: false,
    apellido: false,
    correo: false,
    contrasena: false,
    confirmarContrasena: false,
    dni: false,
    telefono: false,
    fechaNacimiento: false,
    paisId: false
  };

  usuario: Usuario = new Usuario(this.defaultUsuario);
  mostrarContrasena = false;
  mostrarConfirmarContrasena = false;

  pasoActual: number = 1;
  registrando = false;

  touched: typeof this.defaultTouched = { ...this.defaultTouched };

  mensaje: string = '';
  error: string = '';
  correoError: string = '';
  dniError: string = '';
  telefonoError: string = '';

  paises: Pais[] = [];

  private router?: Router;
  private ns?: NotificationService;
  private app?: AppComponent;

  constructor(
    private usuarioService: UsuarioService,
    private paisService: PaisService,
    private recaptchaService: RecaptchaService
  ) {
    this.cargarPaises();
  }

  precargarRecaptcha(): void {
    this.recaptchaService.cargarScript();
  }

  get nombreValido(): boolean {
    const n = this.usuario.nombre?.trim() || '';

    return (
      n.length >= 2 &&
      n.length <= 50 &&
      /^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$/.test(n)
    );
  }

  get apellidoValido(): boolean {
    const a = this.usuario.apellido?.trim() || '';

    return (
      a.length >= 2 &&
      a.length <= 50 &&
      /^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$/.test(a)
    );
  }

  get emailValido(): boolean {
    const correoUsuario = this.usuario.correoUsuario?.trim() || '';

    return (
      correoUsuario.length >= 2 &&
      correoUsuario.length <= 50 &&
      /^[a-zA-Z0-9._-]+$/.test(correoUsuario)
    );
  }

  get fechaFutura(): boolean {
    if (!this.usuario.fechaNacimiento) return false;
    const hoy = new Date();
    const fecha = new Date(this.usuario.fechaNacimiento);
    return fecha > hoy;
  }

  get menorDeEdad(): boolean {
    if (!this.usuario.fechaNacimiento) return false;
    const hoy = new Date();
    const fecha = new Date(this.usuario.fechaNacimiento);
    const edadMinima = new Date(hoy.getFullYear() - 18, hoy.getMonth(), hoy.getDate());
    return fecha > edadMinima;
  }

  get fechaMuyPasada(): boolean {
    if (!this.usuario.fechaNacimiento) return false;
    const fecha = new Date(this.usuario.fechaNacimiento);
    const limite = new Date(1900, 0, 1);
    return fecha < limite;
  }

  get dniValido(): boolean {
    const dniStr = (this.usuario.dni || '').toString().trim();

    return /^\d{8}$/.test(dniStr);
  }

  get telefonoValido(): boolean {
    const telStr = (this.usuario.telefono || '').toString().trim();

    return /^\d{9}$/.test(telStr);
  }

  get passwordChecks() {
    const pass = this.usuario.contrasena || '';
    const confirm = this.confirmarContrasena || '';
    const camposTocados = Object.values(this.touched).some(v => v);

    return {
      length: pass.length >= 8 && pass.length <= 15,
      uppercase: /[A-Z]/.test(pass),
      number: /\d/.test(pass),
      noSpecial: pass.length > 0 ? /^[a-zA-Z0-9]+$/.test(pass) : false,
      empty: camposTocados && !pass && !confirm,
      match: camposTocados && pass === confirm && !!pass
    };
  }

  get formularioValido(): boolean {
    return !!(
      this.nombreValido &&
      this.apellidoValido &&
      this.usuario.correoUsuario &&
      this.emailValido &&
      this.dniValido &&
      this.telefonoValido &&
      this.usuario.fechaNacimiento &&
      !this.fechaFutura &&
      !this.fechaMuyPasada &&
      !this.menorDeEdad &&
      this.usuario.paisId &&
      this.passwordChecks.length &&
      this.passwordChecks.uppercase &&
      this.passwordChecks.number &&
      this.passwordChecks.noSpecial &&
      this.passwordChecks.match
    );
  }

  get mostrarErrorCampos(): boolean {
    return !!this.error && !this.formularioValido;
  }

  inicializarFormulario() {
    this.usuario = new Usuario(this.defaultUsuario);
    this.confirmarContrasena = '';
    this.touched = { ...this.defaultTouched };
    this.mensaje = '';
    this.error = '';
  }

  cargarPaises() {
    this.paisService.listar().subscribe({
      next: (data) => {
        this.paises = data.map(paisObj => new Pais(paisObj));
      },
      error: (err) => console.error('Error al cargar países:', err)
    });
  }

  togglePassword() {
    this.mostrarContrasena = !this.mostrarContrasena;
  }

  toggleConfirmarPassword() {
    this.mostrarConfirmarContrasena = !this.mostrarConfirmarContrasena;
  }

  setTouched(field: keyof typeof this.touched) {
    this.touched[field] = true;
  }

  setUtils(router: Router, ns: NotificationService, app: AppComponent) {
    this.router = router;
    this.ns = ns;
    this.app = app;
  }

  abrirAsistenteVisual() {
    if (this.app?.accesibilidad) {
      this.app.accesibilidad.abrirAsistente();
      const sub = this.app.accesibilidad.confirmadoWizard.subscribe((tipo: any) => {
        this.usuario.configuracionVisual = tipo;
        if (this.ns) this.ns.info(`Configuración aplicada: ${this.getEtiquetaVisual(tipo)}`);
        sub.unsubscribe();
      });
    }
  }

  getEtiquetaVisual(tipo?: string): string {
    const t = tipo || this.usuario.configuracionVisual || 'NINGUNO';
    const mapping: any = {
      'DEUTERANOPIA': 'Deuteranopia',
      'PROTANOPIA': 'Protanopia',
      'TRITANOPIA': 'Tritanopia',
      'DEUTERANOMALIA': 'Deuteranomalía',
      'PROTANOMALIA': 'Protanomalía',
      'ACROMATOPSIA': 'Escala de Grises',
      'NINGUNO': 'Sin filtro'
    };
    return mapping[t] || 'Sin filtro';
  }

  irAPaso1() {
    this.pasoActual = 1;
  }

  setFiltro(tipo: string) {
    this.usuario.configuracionVisual = tipo;
    document.documentElement.style.filter = this.getFiltroStyle(tipo);
  }

  private getFiltroStyle(tipo: string): string {
    const filters: any = {
      'DEUTERANOPIA': 'url(#deuteranopia)',
      'PROTANOPIA': 'url(#protanopia)',
      'TRITANOPIA': 'url(#tritanopia)',
      'DEUTERANOMALIA': 'url(#deuteranomalia)',
      'PROTANOMALIA': 'url(#protanomalia)',
      'ACROMATOPSIA': 'grayscale(100%)',
      'NINGUNO': 'none'
    };
    return filters[tipo] || 'none';
  }

  registrar(): void {
    if (this.registrando) return;
    if (this.usuario.correoUsuario) {
      this.usuario.correo = this.usuario.correoUsuario + '@gmail.com';
    }

    Object.keys(this.touched).forEach(k => this.touched[k as keyof typeof this.touched] = true);

    this.correoError = '';
    this.dniError = '';
    this.telefonoError = '';
    this.error = '';
    this.mensaje = '';

    if (!this.formularioValido) {
      if (this.ns) {
        if (!this.nombreValido) {
          this.ns.error('El nombre es obligatorio, debe tener entre 2 y 50 caracteres y solo puede contener letras.');
        } else if (!this.apellidoValido) {
          this.ns.error('El apellido es obligatorio, debe tener entre 2 y 50 caracteres y solo puede contener letras y espacios.');
        } else if (!this.usuario.correoUsuario || !this.emailValido) {
          this.ns.error('El correo debe tener entre 2 y 50 caracteres y solo puede contener letras, números, puntos, guiones y guiones bajos.');
        } else if (!this.passwordChecks.length || !this.passwordChecks.uppercase || !this.passwordChecks.number || !this.passwordChecks.noSpecial) {
          this.ns.error('La contraseña debe tener entre 8 y 15 caracteres, incluir 1 mayúscula, 1 número y NO debe contener caracteres especiales.');
        } else if (!this.passwordChecks.match) {
          this.ns.error('Las contraseñas no coinciden.');
        } else if (!this.dniValido) {
          this.ns.error('El DNI debe contener exactamente 8 dígitos numéricos, sin letras ni caracteres especiales.');
        } else if (!this.telefonoValido) {
          this.ns.error('El teléfono debe contener exactamente 9 dígitos numéricos, sin letras ni caracteres especiales.');
        } else if (this.fechaMuyPasada) {
          this.ns.error('La fecha de nacimiento no puede ser anterior al año 1900.');
        } else if (!this.usuario.fechaNacimiento || this.fechaFutura) {
          this.ns.error('La fecha de nacimiento debe ser válida y anterior a la fecha actual.');
        } else if (this.menorDeEdad) {
          this.ns.error('Debes ser mayor de 18 años para registrarte.');
        } else if (!this.usuario.paisId) {
          this.ns.error('Debes seleccionar un país de residencia.');
        } else {
          this.ns.error('Por favor revisa que todos los campos estén correctos.');
        }
      }
      return;
    }

    const enviarRegistro = (token?: string) => {
      const payload: Usuario = new Usuario(this.usuario);
      delete payload.correoUsuario;
      if (token) {
        payload.recaptchaToken = token;
      }
      this.usuarioService.crearUsuario(payload).subscribe({
        next: () => {
          this.registrando = false;
          if (this.ns) this.ns.success('¡Cuenta creada con éxito! Bienvenido a R.E.T.O Salud.');
          this.inicializarFormulario();

          if (this.router) {
            setTimeout(() => {
              this.router?.navigate(['/login']);
            }, 2000);
          }
        },
        error: (err) => {
          this.registrando = false;
          if (err.status === 409 && err.error?.field) {
            const field = err.error.field;
            const msg = err.error.message;
            if (field === 'correo') this.correoError = msg;
            else if (field === 'dni') this.dniError = msg;
            else if (field === 'telefono') this.telefonoError = msg;

            if (this.ns) this.ns.error(`${msg}`);
          } else {
            const errMsg = err.error?.message || err.message || 'Error desconocido';
            this.error = 'Error del servidor: ' + errMsg;
            if (this.ns) this.ns.error(`Error: ${errMsg}`);
            console.error(err);
          }
        }
      });
    };

    this.recaptchaService.obtenerToken('registro')
      .then((token: string) => {
        enviarRegistro(token);
      })
      .catch(() => {
        this.registrando = false;
        if (this.ns) {
          this.ns.error('No se pudo verificar el captcha de seguridad. Por favor, revisa tu conexión e inténtalo nuevamente.');
        }
      });
  }

  trackByPaisId(index: number, pais: Pais): number {
    return pais.id;
  }

  soloNumeros(event: KeyboardEvent) {
    const charCode = event.which ? event.which : event.keyCode;
    if (charCode > 31 && (charCode < 48 || charCode > 57)) {
      event.preventDefault();
    }
  }
}
