import { Routes } from '@angular/router';


import { AuthGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';
import { MedicoGuard } from './guards/medico.guard';
import { RecepcionGuard } from './guards/recepcion.guard';
import { PacienteGuard } from './guards/paciente.guard';

export const routes: Routes = [

  { path: '', loadComponent: () => import('./components/home/home').then(m => m.HomeComponent), title: 'R.E.T.O Salud' },
  { path: 'login', loadComponent: () => import('./components/login/login').then(m => m.LoginComponent), title: 'Iniciar Sesión' },
  { path: 'registrar', loadComponent: () => import('./components/registrar-usuario/registrar-usuario').then(m => m.RegistrarUsuarioComponent), title: 'Registro' },
  { path: 'auth/google/callback', loadComponent: () => import('./components/google-callback/google-callback').then(m => m.GoogleCallbackComponent), title: 'Google Auth' },
  { path: 'recuperar', loadComponent: () => import('./components/recuperar/recuperar').then(m => m.RecuperarComponent), title: 'Recuperar Contraseña' },
  { path: 'restablecer', loadComponent: () => import('./components/restablecer/restablecer').then(m => m.RestablecerComponent), title: 'Restablecer Contraseña' },
  { path: 'trabaja-con-nosotros', loadComponent: () => import('./components/trabaja-con-nosotros/trabaja-con-nosotros').then(m => m.TrabajaConNosotrosComponent), title: 'Trabaja con Nosotros' },

  { path: 'admin', loadComponent: () => import('./components/admin-dashboard/admin-dashboard').then(m => m.AdminDashboardComponent), title: 'Panel Administrador', canActivate: [AdminGuard] },
  { path: 'usuarios', loadComponent: () => import('./components/lista-usuarios/lista-usuarios').then(m => m.ListaUsuariosComponent), title: 'Gestión de Usuarios', canActivate: [AdminGuard] },
  { path: 'medicos', loadComponent: () => import('./components/admin-medicos/admin-medicos').then(m => m.AdminMedicos), title: 'Gestión de Médicos', canActivate: [AdminGuard] },
  { path: 'especialidades', loadComponent: () => import('./components/admin-especialidades/admin-especialidades').then(m => m.AdminEspecialidadesComponent), title: 'Especialidades', canActivate: [AdminGuard] },
  { path: 'admin/pagos', loadComponent: () => import('./components/mis-pagos/mis-pagos').then(m => m.MisPagosComponent), title: 'Control de Pagos', canActivate: [AdminGuard] },
  { path: 'admin/notificaciones', loadComponent: () => import('./components/admin-notificaciones/admin-notificaciones').then(m => m.AdminNotificacionesComponent), title: 'Avisos y Alertas', canActivate: [AdminGuard] },
  { path: 'admin/gestion-citas', loadComponent: () => import('./components/mis-citas/mis-citas').then(m => m.MisCitasComponent), title: 'Gestión de Citas', canActivate: [AdminGuard] },

  { path: 'admin/solicitudes', loadComponent: () => import('./components/admin-solicitudes/admin-solicitudes').then(m => m.AdminSolicitudesComponent), title: 'Gestión de Aspirantes', canActivate: [AdminGuard] },

  { path: 'medico/dashboard',
    loadComponent: () => import('./components/medico-dashboard/medico-dashboard').then(m => m.MedicoDashboardComponent),
    title: 'Panel Médico', canActivate: [MedicoGuard]
  },
  { path: 'medico/agenda', loadComponent: () => import('./components/mis-citas/mis-citas').then(m => m.MisCitasComponent), title: 'Mi Agenda de Citas', canActivate: [MedicoGuard] },
  { path: 'medico/pagos', loadComponent: () => import('./components/mis-pagos/mis-pagos').then(m => m.MisPagosComponent), title: 'Mis Ingresos', canActivate: [MedicoGuard] },

  { path: 'gestionar-disponibilidad/:id', loadComponent: () => import('./components/gestionar-disponibilidad/gestionar-disponibilidad').then(m => m.GestionarDisponibilidadComponent), title: 'Disponibilidad', canActivate: [MedicoGuard] },

  { path: 'recepcion/dashboard',
    loadComponent: () => import('./components/recepcion-dashboard/recepcion-dashboard').then(m => m.RecepcionDashboardComponent),
    title: 'Panel Recepción', canActivate: [RecepcionGuard]
  },
  { path: 'recepcion/pagos', loadComponent: () => import('./components/mis-pagos/mis-pagos').then(m => m.MisPagosComponent), title: 'Pagos Recepción', canActivate: [RecepcionGuard] },
  { path: 'recepcion/medicos', loadComponent: () => import('./components/recepcion-medicos/recepcion-medicos').then(m => m.RecepcionMedicosComponent), title: 'Consulta de Médicos', canActivate: [RecepcionGuard] },


  { path: 'paciente/dashboard',
    loadComponent: () => import('./components/paciente-dashboard/paciente-dashboard').then(m => m.PacienteDashboardComponent),
    title: 'Mi Panel', canActivate: [PacienteGuard]
  },
  { path: 'mis-citas', loadComponent: () => import('./components/mis-citas/mis-citas').then(m => m.MisCitasComponent), title: 'Mis Citas', canActivate: [AuthGuard] },
  { path: 'mis-citas/:id', loadComponent: () => import('./components/mis-citas/mis-citas').then(m => m.MisCitasComponent), title: 'Detalle de Cita', canActivate: [AuthGuard] },

  { path: 'mi-perfil', loadComponent: () => import('./components/mi-perfil/mi-perfil').then(m => m.MiPerfilComponent), title: 'Mi Perfil', canActivate: [AuthGuard] },
  { path: 'historial-clinico', loadComponent: () => import('./components/historial-medico/historial-medico').then(m => m.HistorialMedicoComponent), title: 'Mi Historial Clínico', canActivate: [AuthGuard] },
  { path: 'historial-clinico/:pacienteId', loadComponent: () => import('./components/historial-medico/historial-medico').then(m => m.HistorialMedicoComponent), title: 'Expediente del Paciente', canActivate: [AuthGuard] },
  { path: 'mi-historial', loadComponent: () => import('./components/mis-pagos/mis-pagos').then(m => m.MisPagosComponent), title: 'Historial de Pagos', canActivate: [AuthGuard] },
  { path: 'notificaciones', loadComponent: () => import('./components/notificaciones/notificaciones').then(m => m.NotificacionesComponent), title: 'Mis Notificaciones', canActivate: [AuthGuard] },
  { path: 'teleconsulta/:id', loadComponent: () => import('./components/teleconsulta/teleconsulta').then(m => m.TeleconsultaComponent), title: 'Sala de Teleconsulta', canActivate: [AuthGuard] },
  { path: 'checkout/:id', loadComponent: () => import('./components/checkout/checkout').then(m => m.CheckoutComponent), title: 'Checkout', canActivate: [AuthGuard] },
  { path: 'pagar-efectivo/:id', loadComponent: () => import('./components/pagar-efectivo/pagar-efectivo').then(m => m.PagarEfectivoComponent), title: 'Pago en Efectivo', canActivate: [AuthGuard] },
  { path: 'pagar-tarjeta/:id', loadComponent: () => import('./components/pagar-tarjeta/pagar-tarjeta').then(m => m.PagarTarjetaComponent), title: 'Pago con Tarjeta', canActivate: [AuthGuard] },

  { path: '**', redirectTo: '' }
];
