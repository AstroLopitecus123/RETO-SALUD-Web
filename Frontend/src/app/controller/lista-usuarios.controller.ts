import { Injectable } from '@angular/core';
import { UsuarioService } from '../services/usuario.service';
import { UsuarioFull } from '../models/usuario-full.model';
import { Router } from '@angular/router';
import { NotificationService } from '../services/notification.service';

@Injectable({
  providedIn: 'root'
})
export class ListaUsuariosController {

  usuarios: UsuarioFull[] = [];
  usuariosFiltrados: UsuarioFull[] = [];
  usuariosPaginados: (UsuarioFull | null)[] = [];

  filtro: string = '';
  paginaActual: number = 1;
  itemsPorPagina: number = 10;
  totalPaginas: number = 1;
  menuAbierto: number | null = null;

  mostrandoConfirmarEliminar = false;
  usuarioSeleccionadoAEliminar: UsuarioFull | null = null;

  mostrandoModalRol = false;
  usuarioSeleccionadoRol: UsuarioFull | null = null;
  nuevoRolVisual: string = '';

  constructor(
    private usuarioService: UsuarioService,
    private router: Router,
    private ns: NotificationService
  ) { }

  cargarUsuarios(): void {
    this.usuarioService.listarUsuarios().subscribe({
      next: (data) => {
        this.usuarios = data.map(u => new UsuarioFull(u));

        this.aplicarFiltroYPaginacion();
      },
      error: (err) => console.error('Error al cargar usuarios:', err)
    });
  }

  filtrarUsuarios(): void {
    this.paginaActual = 1;
    this.aplicarFiltroYPaginacion();
  }
  private aplicarFiltroYPaginacion(): void {
    const texto = this.filtro.toLowerCase().trim();

    this.usuariosFiltrados = this.usuarios.filter(u =>
      u.nombre.toLowerCase().includes(texto) ||
      u.apellido.toLowerCase().includes(texto) ||
      u.correo.toLowerCase().includes(texto) ||
      u.rol.toLowerCase().includes(texto) ||
      u.pais.nombre.toLowerCase().includes(texto)
    );

    this.totalPaginas = Math.ceil(this.usuariosFiltrados.length / this.itemsPorPagina) || 1;

    this.actualizarPagina();
  }

  actualizarPagina(): void {
    this.totalPaginas = Math.max(1, Math.ceil(this.usuariosFiltrados.length / this.itemsPorPagina));
    this.paginaActual = Math.max(1, Math.min(this.paginaActual, this.totalPaginas));
    const inicio = (this.paginaActual - 1) * this.itemsPorPagina;
    const fin = inicio + this.itemsPorPagina;

    const pagina: (UsuarioFull | null)[] = this.usuariosFiltrados.slice(inicio, fin);

    while (pagina.length < this.itemsPorPagina) {
      pagina.push(null);
    }

    this.usuariosPaginados = pagina;
  }

  siguientePagina(): void {
    if (this.paginaActual < this.totalPaginas) {
      this.paginaActual++;
      this.actualizarPagina();
    }
  }

  anteriorPagina(): void {
    if (this.paginaActual > 1) {
      this.paginaActual--;
      this.actualizarPagina();
    }
  }

  toggleMenu(usuario: UsuarioFull | null): void {
    if (!usuario) return;
    this.menuAbierto = this.menuAbierto === usuario.id ? null : usuario.id;
  }

  eliminarUsuario(usuario: UsuarioFull | null): void {
    if (!usuario) return;
    this.usuarioSeleccionadoAEliminar = usuario;
    this.mostrandoConfirmarEliminar = true;
  }

  confirmarEliminacionFinal(): void {
    if (!this.usuarioSeleccionadoAEliminar) return;
    const u = this.usuarioSeleccionadoAEliminar;

    this.usuarioService.eliminarUsuario(u.id).subscribe({
      next: () => {
        this.usuarios = this.usuarios.filter(user => user.id !== u.id);
        this.aplicarFiltroYPaginacion();
        this.ns.success('Usuario eliminado correctamente');
        this.cerrarModalConfirmar();
      },
      error: (err) => {
        console.error('Error al eliminar usuario:', err);
        this.ns.error('No se pudo eliminar el usuario. Puede tener citas asociadas.');
        this.cerrarModalConfirmar();
      }
    });
  }

  cerrarModalConfirmar(): void {
    this.mostrandoConfirmarEliminar = false;
    this.usuarioSeleccionadoAEliminar = null;
  }

  toggleEstado(usuario: UsuarioFull): void {
    const nuevoEstado = usuario.estado === 'ACTIVADO' ? 'DESACTIVADO' : 'ACTIVADO';
    this.actualizarEstado(usuario, nuevoEstado);
  }

  actualizarEstado(usuario: any, nuevoEstado: string): void {
    if (!usuario) return;

    this.usuarioService.actualizarEstado(usuario.id, nuevoEstado).subscribe({
      next: (res) => {
        usuario.estado = nuevoEstado;
        console.log(`Estado de ${usuario.nombre} actualizado a ${nuevoEstado}`);
      },
      error: (err) => console.error('Error al actualizar estado', err)
    });
  }

  abrirModalRol(usuario: UsuarioFull | null): void {
    if (!usuario) return;
    this.menuAbierto = null;
    this.usuarioSeleccionadoRol = usuario;
    this.nuevoRolVisual = usuario.rol;
    this.mostrandoModalRol = true;
  }

  cerrarModalRol(): void {
    this.mostrandoModalRol = false;
    this.usuarioSeleccionadoRol = null;
  }

  guardarRolUsuario(): void {
    if (!this.usuarioSeleccionadoRol) return;
    const u = this.usuarioSeleccionadoRol;

    this.usuarioService.actualizarRol(u.id, this.nuevoRolVisual).subscribe({
      next: (res) => {
        u.rol = this.nuevoRolVisual as any;
        this.aplicarFiltroYPaginacion();
        this.ns.success(`Rol de ${u.nombre} actualizado a ${this.nuevoRolVisual}`);
        this.cerrarModalRol();
      },
      error: (err) => {
        console.error('Error al actualizar rol', err);
        this.ns.error('Error al actualizar el rol del usuario.');
      }
    });
  }
}
