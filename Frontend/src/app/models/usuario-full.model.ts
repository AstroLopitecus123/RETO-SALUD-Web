import { RolUsuario, EstadoUsuario } from './tipos';
import { Pais } from './pais.model';
import { Especialidad } from './especialidad.model';

export class UsuarioFull {
  medicoId: number = 0;               
  id: number = 0;                      
  nombre: string = '';
  apellido: string = '';
  correo: string = '';
  rol: RolUsuario = 'PACIENTE';
  fechaRegistro: string = new Date().toISOString();
  estado: EstadoUsuario = 'ACTIVADO';
  telefono: string = '';
  fechaNacimiento: string = '';
  dni: string = '';
  pais: Pais = new Pais();
  especialidad?: Especialidad;         
  especialidadSeleccionada?: Especialidad; 
  fotoUrl: string = '';

  constructor(data?: any) {
    if (data) {

      const usuario = data.usuario ?? data;

      this.medicoId = data.medicoId ?? (data.usuario ? data.id : 0);   
      this.id = usuario.id ?? 0;      

      this.nombre = usuario.nombre ?? '';
      this.apellido = usuario.apellido ?? '';
      this.correo = usuario.correo ?? '';
      this.rol = usuario.rol ?? 'PACIENTE';
      this.fechaRegistro = usuario.fechaRegistro ?? new Date().toISOString();
      this.estado = usuario.estado ?? 'ACTIVADO';
      this.telefono = usuario.telefono ?? '';
      this.fechaNacimiento = usuario.fechaNacimiento ?? '';
      this.dni = usuario.dni ?? '';
      this.pais = new Pais(usuario.pais);
      this.fotoUrl = usuario.fotoUrl ?? '';

      this.especialidad = data.especialidad ?? undefined;
      this.especialidadSeleccionada = this.especialidad;
    }
  }
}
