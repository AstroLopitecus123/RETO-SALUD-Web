import { Component, HostListener, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UsuarioService } from '../../services/usuario.service';
import { NotificationService } from '../../services/notification.service';
import { UsuarioFull } from '../../models/usuario-full.model';
import { Pais } from '../../models/pais.model';
import { AvatarCropperComponent } from '../avatar-cropper/avatar-cropper.component';
import { Router } from '@angular/router';
import { AppComponent } from '../../app';

@Component({
  selector: 'app-mi-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule, AvatarCropperComponent],
  templateUrl: './mi-perfil.html',
  styleUrls: ['./mi-perfil.css']
})
export class MiPerfilComponent implements OnInit {
  @ViewChild('cropperPerfil') cropperRef?: AvatarCropperComponent;
  usuario: UsuarioFull = new UsuarioFull();
  usuarioEditado: UsuarioFull = new UsuarioFull();
  listaPaises: Pais[] = [];
  editando = false;
  cargando = true;
  subiendoFoto = false;
  mostrarFotoGrande = false;

  // Cropper properties
  selectedImageFile: File | null = null;
  mostrandoCropper = false;

  mostrarModalPassword = false;
  pwdActual = '';
  pwdNueva = '';
  pwdConfirmar = '';

  verPadActual = false;
  verPadNueva = false;
  verPadConfirmar = false;

  constructor(
    private usuarioService: UsuarioService,
    private ns: NotificationService,
    private router: Router,
    private app: AppComponent
  ) {}

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos() {
    const usuarioLocal = localStorage.getItem('usuario');
    const id = usuarioLocal ? JSON.parse(usuarioLocal).id : null;

    if (id) {
      this.usuarioService.obtenerUsuario(id).subscribe({
        next: data => {
          this.usuario = new UsuarioFull(data);
          this.usuario.pais = new Pais(this.usuario.pais ?? {});
          this.usuarioEditado = new UsuarioFull(this.usuario);
          this.cargarPaises();
        },
        error: err => {
          this.ns.error('No se pudo cargar la información del perfil');
          this.cargando = false;
        }
      });
    } else {
      this.cargando = false;
    }
  }

  cargarPaises() {
    this.usuarioService.listarPaises().subscribe({
      next: paises => {
        this.listaPaises = paises ?? [];
        this.cargando = false;
      },
      error: () => this.cargando = false
    });
  }

  activarEdicion() {
    this.editando = true;
    this.usuarioEditado = new UsuarioFull(this.usuario);
  }

  cancelarEdicion() {
    this.editando = false;
  }

  abrirFotoGrande(): void {
    if (this.usuario?.fotoUrl) {
      this.mostrarFotoGrande = true;
    }
  }

  cerrarFotoGrande(): void {
    this.mostrarFotoGrande = false;
  }

  @HostListener('document:keydown.escape')
  onEscapeFotoGrande(): void {
    if (this.mostrarFotoGrande) {
      this.cerrarFotoGrande();
    }
  }

  guardarCambios() {
    const nombre = this.usuarioEditado.nombre?.trim() || '';
    const apellido = this.usuarioEditado.apellido?.trim() || '';
    const correo = this.usuarioEditado.correo?.trim() || '';
    const telefono = this.usuarioEditado.telefono?.trim() || '';
    const fechaNacimiento = this.usuarioEditado.fechaNacimiento;
    const pais = this.usuarioEditado.pais;

    // Nombre
    if (!/^[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,50}$/.test(nombre)) {
      this.ns.error(
        'El nombre es obligatorio, debe tener entre 2 y 50 caracteres y solo puede contener letras.'
      );
      return;
    }

    // Apellido
    if (!/^[A-Za-zÁÉÍÓÚáéíóúÑñ ]{2,50}$/.test(apellido)) {
      this.ns.error(
        'El apellido es obligatorio, debe tener entre 2 y 50 caracteres y solo puede contener letras y espacios.'
      );
      return;
    }

    // Correo
    if (
      !/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(correo) ||
      correo.length < 5 ||
      correo.length > 80
    ) {
      this.ns.error('El formato del correo electrónico no es válido.');
      return;
    }

    // Teléfono
    if (!/^\d{9}$/.test(telefono)) {
      this.ns.error(
        'El teléfono debe contener exactamente 9 dígitos numéricos.'
      );
      return;
    }

    // Fecha de nacimiento
    const fecha = new Date(fechaNacimiento);
    const hoy = new Date();
    const limitePasado = new Date(1900, 0, 1);

    if (!fechaNacimiento || isNaN(fecha.getTime()) || fecha >= hoy) {
      this.ns.error(
        'La fecha de nacimiento debe ser válida y anterior a la fecha actual.'
      );
      return;
    }

    // Límite de año 1900
    if (fecha < limitePasado) {
      this.ns.error('La fecha de nacimiento no puede ser anterior al año 1900.');
      return;
    }

    // Edad mínima 18 años
    const edadMinima = new Date(hoy.getFullYear() - 18, hoy.getMonth(), hoy.getDate());
    if (fecha > edadMinima) {
      this.ns.error('Debes ser mayor de 18 años para actualizar tu perfil.');
      return;
    }

    // País
    if (!pais) {
      this.ns.error('Debes seleccionar un país.');
      return;
    }

    // Si todo es válido
    this.guardarDatosFormulario();
  }

  /** Llamado desde el botón "Guardar Foto" dentro del modal de recorte */
  async guardarFoto() {
    if (!this.cropperRef) return;
    this.subiendoFoto = true;
    const blob = await this.cropperRef.getBlob();
    if (blob) {
      const file = new File([blob], 'perfil_recortado.png', { type: 'image/png' });
      this.usuarioService.subirFoto(this.usuario.id, file).subscribe({
        next: (data) => {
          this.usuario.fotoUrl = data.fotoUrl;
          this.usuarioEditado.fotoUrl = data.fotoUrl;
          const localUser = localStorage.getItem('usuario');
          if (localUser) {
            const userObj = JSON.parse(localUser);
            userObj.fotoUrl = data.fotoUrl;
            localStorage.setItem('usuario', JSON.stringify(userObj));
            window.dispatchEvent(new CustomEvent('usuarioActualizado', { detail: userObj }));
          }
          this.subiendoFoto = false;
          this.mostrandoCropper = false;
          this.selectedImageFile = null;
          this.ns.success('Foto de perfil actualizada correctamente');
        },
        error: () => {
          this.subiendoFoto = false;
          this.ns.error('No se pudo subir la foto de perfil');
        }
      });
    } else {
      this.subiendoFoto = false;
    }
  }

  private guardarDatosFormulario() {
    this.usuarioService.actualizarUsuario(this.usuarioEditado).subscribe({
      next: updated => {
        this.usuario = new UsuarioFull(updated);
        const localUser = localStorage.getItem('usuario');
        const prevObj = localUser ? JSON.parse(localUser) : {};
        const merged = { ...prevObj, ...this.usuario };
        if (prevObj.medicoId) merged.medicoId = prevObj.medicoId;
        if (prevObj.recibirNotificacionesPush !== undefined) merged.recibirNotificacionesPush = prevObj.recibirNotificacionesPush;
        localStorage.setItem('usuario', JSON.stringify(merged));
        this.editando = false;
        this.ns.success('Perfil actualizado correctamente');
      },
      error: () => this.ns.error('Error al guardar los cambios')
    });
  }

  comparePais(p1: Pais | null, p2: Pais | null): boolean {
    return !!p1 && !!p2 ? p1.id === p2.id : p1 === p2;
  }

  abrirModalPassword() {
    this.pwdActual = '';
    this.pwdNueva = '';
    this.pwdConfirmar = '';
    this.mostrarModalPassword = true;
  }

  cerrarModalPassword() {
    this.mostrarModalPassword = false;
    this.pwdActual = '';
    this.pwdNueva = '';
    this.pwdConfirmar = '';
  }

  ejecutarCambioPassword() {
  const actual = this.pwdActual?.trim() || '';
  const nueva = this.pwdNueva?.trim() || '';
  const confirmar = this.pwdConfirmar?.trim() || '';

  // Campos obligatorios
  if (!actual || !nueva || !confirmar) {
    this.ns.error('Todos los campos son obligatorios.');
    return;
  }

  if (nueva !== confirmar) {
    this.ns.error('Las nuevas contraseñas no coinciden.');
    return;
  }

  if (nueva.length < 8 || nueva.length > 72) {
    this.ns.error('La nueva contraseña debe tener entre 8 y 72 caracteres.');
    return;
  }

  if (!/[A-Z]/.test(nueva)) {
    this.ns.error('La nueva contraseña debe contener al menos una letra mayúscula.');
    return;
  }

  if (!/[a-z]/.test(nueva)) {
    this.ns.error('La nueva contraseña debe contener al menos una letra minúscula.');
    return;
  }

  if (!/\d/.test(nueva)) {
    this.ns.error('La nueva contraseña debe contener al menos un número.');
    return;
  }

  this.usuarioService
    .cambiarPassword(this.usuario.id, actual, nueva)
    .subscribe({
      next: () => {
        this.cerrarModalPassword();
        this.usuarioService.limpiarSesion();
        this.app.verificarLogin();
        this.usuario = new UsuarioFull();
        this.usuarioEditado = new UsuarioFull();
        this.ns.success('Contrasena actualizada. Tu sesion se cerro por seguridad; inicia sesion nuevamente.');
        this.router.navigate(['/login'], { replaceUrl: true });
      },
      error: (err) => {
        const msg =
          err.error?.message || 'Error al cambiar la contraseña';
        this.ns.error(msg);
      }
    });
}

  onFileSelected(event: any) {
    if (event.target.files && event.target.files.length > 0) {
      this.selectedImageFile = event.target.files[0];
      this.mostrandoCropper = true;
    }
  }

  cancelarRecorte() {
    this.mostrandoCropper = false;
    this.selectedImageFile = null;
  }

  onCropBlob(blob: Blob) {
    this.subiendoFoto = true;
    const file = new File([blob], 'perfil_recortado.png', { type: 'image/png' });
    this.usuarioService.subirFoto(this.usuario.id, file).subscribe({
      next: (data) => {
        this.usuario.fotoUrl = data.fotoUrl;
        this.usuarioEditado.fotoUrl = data.fotoUrl;

        const localUser = localStorage.getItem('usuario');
        if (localUser) {
          const userObj = JSON.parse(localUser);
          userObj.fotoUrl = data.fotoUrl;
          localStorage.setItem('usuario', JSON.stringify(userObj));
          window.dispatchEvent(new CustomEvent('usuarioActualizado', { detail: userObj }));
        }

        this.subiendoFoto = false;
        this.mostrandoCropper = false;
        this.selectedImageFile = null;
        this.ns.success('Foto de perfil actualizada correctamente');
      },
      error: (err) => {
        console.error('Error al subir la foto:', err);
        this.subiendoFoto = false;
        this.mostrandoCropper = false;
        this.ns.error('No se pudo subir la foto de perfil');
      }
    });
  }


}
