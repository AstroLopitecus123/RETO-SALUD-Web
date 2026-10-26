import { MedicoService } from '../services/medico.service';
import { EspecialidadService } from '../services/especialidad.service';
import { UsuarioService } from '../services/usuario.service';
import { UsuarioFull } from '../models/usuario-full.model';
import { Especialidad } from '../models/especialidad.model';
import { Router } from '@angular/router';
import { Injectable } from '@angular/core';

export interface UsuarioFullConIndice extends UsuarioFull {
  fila: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminMedicosController {
  medicos: UsuarioFull[] = [];
  medicosConIndice: UsuarioFullConIndice[] = [];
  listaEspecialidades: Especialidad[] = [];
  medicoSeleccionado: UsuarioFull = new UsuarioFull();
  especialidadSeleccionada?: Especialidad;
  mostrandoModalAsignar = false;
  
  // Profile edit properties
  mostrandoModalEditarPerfil = false;
  subiendoFoto = false;
  nombreEditado = '';
  apellidoEditado = '';
  correoEditado = '';
  telefonoEditado = '';
  dniEditado = '';
  fotoUrlEditado = '';
  
  // Cropper properties
  selectedImageFile: File | null = null;
  mostrandoCropper = false;

  error = '';
  cargando = false;

  constructor(
    private medicoService: MedicoService,
    private especialidadService: EspecialidadService,
    private usuarioService: UsuarioService,
    private router: Router
  ) { }

  inicializar() {
    this.cargarMedicos();
    this.cargarEspecialidades();
  }

  cargarMedicos() {
    this.cargando = true;
    this.medicoService.listarMedicos().subscribe({
      next: data => {
        this.medicos = data.map(d => new UsuarioFull(d));
        this.medicosConIndice = this.medicos.map((medico, index) => ({
          ...medico,
          fila: index + 1
        }));
        this.cargando = false;

        if (this.medicos.length > 0) {
          this.medicoSeleccionado = this.medicos[0];
          this.onMedicoChange();
        }
      },
      error: err => {
        console.error(err);
        this.error = 'Error al cargar médicos';
        this.cargando = false;
      }
    });
  }

  cargarEspecialidades() {
    this.especialidadService.listarEspecialidades().subscribe({
      next: data => this.listaEspecialidades = data,
      error: err => console.error('Error al cargar especialidades', err)
    });
  }

  onMedicoChange() {
    if (this.medicoSeleccionado) {
      this.especialidadSeleccionada = this.medicoSeleccionado.especialidad ?? undefined;
    }
  }

  asignarEspecialidades() {
    if (!this.medicoSeleccionado || !this.especialidadSeleccionada) return;

    this.medicoService
      .asignarEspecialidad(this.medicoSeleccionado.medicoId, this.especialidadSeleccionada.id)
      .subscribe({
        next: medicoActualizado => {
          console.log('Especialidad actualizada:', medicoActualizado);
          this.medicoSeleccionado.especialidad = medicoActualizado.especialidad;
          this.cerrarModal();
          this.cargarMedicos();
        },
        error: err => console.error('Error al actualizar especialidad:', err)
      });
  }

  abrirModalAsignar(medico: UsuarioFull) {
    this.medicoSeleccionado = medico;
    this.especialidadSeleccionada = medico.especialidad ?? undefined;
    this.mostrandoModalAsignar = true;
  }

  cerrarModal() {
    this.mostrandoModalAsignar = false;
  }

  modificarHorario(medico: UsuarioFull) {
    console.log('Modificar horario del médico:', medico.nombre, medico.id);
    this.router.navigate(['/gestionar-disponibilidad', medico.medicoId]);
  }

  abrirModalEditarPerfil(medico: UsuarioFull) {
    this.medicoSeleccionado = medico;
    this.nombreEditado = medico.nombre;
    this.apellidoEditado = medico.apellido;
    this.correoEditado = medico.correo;
    this.telefonoEditado = medico.telefono || '';
    this.dniEditado = medico.dni || '';
    this.fotoUrlEditado = medico.fotoUrl;
    this.mostrandoModalEditarPerfil = true;

    this.usuarioService.obtenerUsuario(medico.id).subscribe({
      next: (u: any) => {
        if (u) {
          if (u.telefono) this.telefonoEditado = u.telefono;
          if (u.dni) this.dniEditado = u.dni;
          if (u.nombre) this.nombreEditado = u.nombre;
          if (u.apellido) this.apellidoEditado = u.apellido;
          if (u.correo) this.correoEditado = u.correo;
          if (u.fotoUrl) this.fotoUrlEditado = u.fotoUrl;
          if (u.pais) this.medicoSeleccionado.pais = u.pais;
          if (u.fechaNacimiento) this.medicoSeleccionado.fechaNacimiento = u.fechaNacimiento;
        }
      },
      error: () => {}
    });
  }

  cerrarModalEditarPerfil() {
    this.mostrandoModalEditarPerfil = false;
  }

  onFotoSelected(event: any) {
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
    this.usuarioService.subirFoto(this.medicoSeleccionado.id, file).subscribe({
      next: (data) => {
        this.fotoUrlEditado = data.fotoUrl;
        this.subiendoFoto = false;
        this.mostrandoCropper = false;
        this.selectedImageFile = null;
        // La foto fue guardada — el modal de recorte cierra y el usuario
        // puede ver la nueva foto en el formulario y hacer clic en "Guardar Cambios"
      },
      error: (err) => {
        console.error('Error al subir foto:', err);
        this.subiendoFoto = false;
        this.mostrandoCropper = false;
      }
    });
  }

  guardarPerfilEditado() {
    if (!this.nombreEditado || !this.apellidoEditado || !this.correoEditado) {
      this.error = 'Los campos Nombre, Apellido y Correo son obligatorios';
      return;
    }

    const datosActualizados = new UsuarioFull(this.medicoSeleccionado);
    datosActualizados.nombre = this.nombreEditado.trim();
    datosActualizados.apellido = this.apellidoEditado.trim();
    datosActualizados.correo = this.correoEditado.trim();
    datosActualizados.telefono = (this.telefonoEditado || '').trim();
    datosActualizados.dni = (this.dniEditado || '').trim();
    datosActualizados.fotoUrl = this.fotoUrlEditado;
    if (this.medicoSeleccionado.pais && this.medicoSeleccionado.pais.id > 0) {
      datosActualizados.pais = this.medicoSeleccionado.pais;
    } else {
      delete (datosActualizados as any).pais;
    }

    this.usuarioService.actualizarUsuario(datosActualizados).subscribe({
      next: (updated) => {
        this.cerrarModalEditarPerfil();
        this.cargarMedicos();
      },
      error: (err) => {
        console.error('Error al actualizar médico:', err);
        this.error = 'Error al guardar los cambios del perfil';
      }
    });
  }
}
