import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HistorialService } from '../../services/historial.service';
import { Historial } from '../../models/historial.model';
import { NotificationService } from '../../services/notification.service';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { defer } from 'rxjs';

/** Estructura agrupada por paciente (solo para roles no-PACIENTE) */
export interface PacienteAgrupado {
  pacienteId: number;
  pacienteNombre: string;
  pacienteApellido: string;
  fotoUrl?: string;
  historiales: Historial[];
  expandido: boolean;
}

@Component({
  selector: 'app-historial-medico',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './historial-medico.html',
  styleUrls: ['./historial-medico.css']
})
export class HistorialMedicoComponent implements OnInit {
  historiales: Historial[] = [];
  historialesFiltrados: Historial[] = [];

  /** Grupos agrupados por paciente (para admin/medico/recepcion) */
  pacientesAgrupados: PacienteAgrupado[] = [];
  pacientesAgrupadosFiltrados: PacienteAgrupado[] = [];

  filtroBusqueda = '';
  cargando = true;
  usuario: any = null;
  pacienteId: number | null = null;
  error = '';

  constructor(
    private historialService: HistorialService,
    private route: ActivatedRoute,
    private ns: NotificationService
  ) {}

  ngOnInit() {
    const usrString = localStorage.getItem('usuario');
    if (usrString) {
      this.usuario = JSON.parse(usrString);
      this.route.params.subscribe(params => {
        if (params['pacienteId']) {
          this.pacienteId = +params['pacienteId'];
        } else {
          this.pacienteId = this.usuario.id;
        }
        this.cargarHistorial();
      });
    } else {
      this.error = 'Sesión no válida.';
      this.cargando = false;
    }
  }

  cargarHistorial() {
    this.cargando = true;
    this.error = '';
    const request$ = defer(() => {
      if (this.usuario.rol === 'PACIENTE') {
        return this.historialService.obtenerHistorialPorPaciente(this.usuario.id);
      }
      if (this.pacienteId && this.pacienteId !== this.usuario.id) {
        return this.historialService.obtenerHistorialPorPaciente(this.pacienteId);
      }
      return this.historialService.listarTodasConCitas();
    });

    request$.subscribe({
      next: (data) => {
        this.historiales = data;
        // Para roles no-PACIENTE, agrupar por paciente
        if (this.usuario.rol !== 'PACIENTE') {
          this.agruparPorPaciente();
        } else {
          this.historialesFiltrados = this.historiales;
        }
        this.filtrarHistoriales();
        this.cargando = false;
      },
      error: (err) => {
        console.error(err);
        this.error = 'Error al cargar el historial clínico.';
        this.historiales = [];
        this.historialesFiltrados = [];
        this.pacientesAgrupados = [];
        this.pacientesAgrupadosFiltrados = [];
        this.cargando = false;
      }
    });
  }

  /** Agrupa el array plano de historiales en grupos por paciente */
  agruparPorPaciente() {
    const mapa = new Map<number, PacienteAgrupado>();
    for (const h of this.historiales) {
      const id = h.cita.paciente.id;
      if (!mapa.has(id)) {
        mapa.set(id, {
          pacienteId: id,
          pacienteNombre: h.cita.paciente.nombre,
          pacienteApellido: h.cita.paciente.apellido,
          fotoUrl: h.cita.paciente.fotoUrl,
          historiales: [],
          expandido: false
        });
      }
      mapa.get(id)!.historiales.push(h);
    }
    this.pacientesAgrupados = Array.from(mapa.values());
    this.pacientesAgrupadosFiltrados = [...this.pacientesAgrupados];
  }

  toggleExpandir(grupo: PacienteAgrupado) {
    grupo.expandido = !grupo.expandido;
  }

  filtrarHistoriales() {
    const q = this.filtroBusqueda.toLowerCase().trim();

    if (this.usuario.rol === 'PACIENTE') {
      if (!q) {
        this.historialesFiltrados = this.historiales;
      } else {
        this.historialesFiltrados = this.historiales.filter(h => {
          const nombreMedico = `${h.cita.medico.usuario.nombre} ${h.cita.medico.usuario.apellido}`.toLowerCase();
          const especialidad = h.cita.medico.especialidad ? h.cita.medico.especialidad.nombre.toLowerCase() : '';
          const diagnostico = h.diagnostico ? h.diagnostico.toLowerCase() : '';
          const receta = h.receta ? h.receta.toLowerCase() : '';
          return nombreMedico.includes(q) || especialidad.includes(q) || diagnostico.includes(q) || receta.includes(q);
        });
      }
    } else {
      if (!q) {
        this.pacientesAgrupadosFiltrados = [...this.pacientesAgrupados];
      } else {
        this.pacientesAgrupadosFiltrados = this.pacientesAgrupados.filter(grupo => {
          const nombrePaciente = `${grupo.pacienteNombre} ${grupo.pacienteApellido}`.toLowerCase();
          if (nombrePaciente.includes(q)) return true;
          // También filtra si alguna cita del paciente coincide
          return grupo.historiales.some(h => {
            const nombreMedico = `${h.cita.medico.usuario.nombre} ${h.cita.medico.usuario.apellido}`.toLowerCase();
            const especialidad = h.cita.medico.especialidad ? h.cita.medico.especialidad.nombre.toLowerCase() : '';
            const diagnostico = h.diagnostico ? h.diagnostico.toLowerCase() : '';
            const receta = h.receta ? h.receta.toLowerCase() : '';
            return nombreMedico.includes(q) || especialidad.includes(q) || diagnostico.includes(q) || receta.includes(q);
          });
        });
      }
    }
  }

  obtenerRutaVolver(): string {
    if (!this.usuario) return '/login';
    if (this.usuario.rol === 'ADMIN') return '/admin';
    if (this.usuario.rol === 'MEDICO') return '/medico/dashboard';
    if (this.usuario.rol === 'RECEPCION') return '/recepcion/dashboard';
    return '/paciente/dashboard';
  }

  /** Carga el logo como base64 y devuelve sus dimensiones originales */
  private cargarLogoBase64(): Promise<{ url: string; w: number; h: number }> {
    return new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.naturalWidth || 100;
          canvas.height = img.naturalHeight || 100;
          const ctx = canvas.getContext('2d');
          if (ctx) {
            ctx.drawImage(img, 0, 0);
            resolve({
              url: canvas.toDataURL('image/png'),
              w: img.naturalWidth,
              h: img.naturalHeight
            });
          } else { resolve({ url: '', w: 0, h: 0 }); }
        } catch { resolve({ url: '', w: 0, h: 0 }); }
      };
      img.onerror = () => resolve({ url: '', w: 0, h: 0 });
      img.src = '/assets/images/LogoSOLO2.webp';
    });
  }

  /** PDF completo de un paciente agrupado (todas sus citas) */
  async descargarPDFPaciente(grupo: PacienteAgrupado) {
    const doc = new jsPDF({ unit: 'mm', format: 'a4' });
    const PAGE_W = 210;
    const MARGIN = 15;
    const CONTENT_W = PAGE_W - MARGIN * 2;

    const verde:      [number,number,number] = [9,   74,  43];   // #094a2b
    const verdeClaro: [number,number,number] = [15, 191, 106];   // #0fbf6a
    const grisOscuro: [number,number,number] = [30,  41,  59];   // #1e293b
    const grisMedio:  [number,number,number] = [100,116, 139];   // #64748b
    const blanco:     [number,number,number] = [255,255, 255];

    // Cargar logo y dimensiones
    const logoData = await this.cargarLogoBase64();

    // ── Helper: encabezado de página ──────────────────────────────
    const dibujarEncabezado = () => {
      // Fondo header verde oscuro
      doc.setFillColor(...verde);
      doc.rect(0, 0, PAGE_W, 50, 'F');
      // Franja verde claro decorativa inferior
      doc.setFillColor(...verdeClaro);
      doc.rect(0, 46, PAGE_W, 4, 'F');

      let textX = MARGIN;

      // Logo con proporciones correctas
      if (logoData.url && logoData.w && logoData.h) {
        const maxH = 34; // alto máximo en mm
        const maxW = 55; // ancho máximo en mm
        let finalW = logoData.w;
        let finalH = logoData.h;

        // Escalar manteniendo proporción
        if (finalH > maxH) {
          finalW = (maxH / finalH) * finalW;
          finalH = maxH;
        }
        if (finalW > maxW) {
          finalH = (maxW / finalW) * finalH;
          finalW = maxW;
        }

        // Centrar verticalmente en el espacio disponible (46mm)
        const yOffset = (46 - finalH) / 2;
        doc.addImage(logoData.url, 'PNG', MARGIN, yOffset, finalW, finalH);
        
        textX = MARGIN + finalW + 5; // Dejar un espacio a la derecha del logo
      }

      // Nombre clínica
      doc.setTextColor(...blanco);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(17);
      doc.text('R.E.T.O. SALUD', textX, 21);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(180, 230, 205);
      doc.text('Sistema Integrado de Gestión Médica', textX, 28);

      // Título derecha
      doc.setTextColor(...blanco);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.text('HISTORIA CLÍNICA', PAGE_W - MARGIN, 18, { align: 'right' });
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9);
      doc.setTextColor(180, 230, 205);
      doc.text('EXPEDIENTE COMPLETO DEL PACIENTE', PAGE_W - MARGIN, 25, { align: 'right' });
      doc.setFontSize(7.5);
      const fechaEmision = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' });
      doc.text(`Emitido: ${fechaEmision}`, PAGE_W - MARGIN, 32, { align: 'right' });
    };

    // ── Helper: pie de página en todas las páginas ───────────────
    const agregarPiePagina = () => {
      const totalPages = (doc as any).internal.getNumberOfPages();
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);
        doc.setDrawColor(...verdeClaro);
        doc.setLineWidth(0.4);
        doc.line(MARGIN, 286, PAGE_W - MARGIN, 286);
        doc.setFontSize(7.5);
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(...grisMedio);
        doc.text('R.E.T.O Salud — Documento oficial de historia clínica del paciente', MARGIN, 291);
        doc.text(`Página ${i} de ${totalPages}`, PAGE_W - MARGIN, 291, { align: 'right' });
      }
    };

    // ── Página 1: encabezado + datos del paciente ─────────────────
    dibujarEncabezado();
    let yPos = 60;

    // Tarjeta datos del paciente
    doc.setFillColor(244, 253, 248);
    doc.setDrawColor(...verdeClaro);
    doc.setLineWidth(0.7);
    doc.roundedRect(MARGIN, yPos, CONTENT_W, 30, 4, 4, 'FD');
    // Barra izquierda verde
    doc.setFillColor(...verdeClaro);
    doc.roundedRect(MARGIN, yPos, 4, 30, 2, 2, 'F');
    // Nombre paciente
    doc.setTextColor(...grisOscuro);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(14);
    doc.text(`${grupo.pacienteNombre} ${grupo.pacienteApellido}`, MARGIN + 10, yPos + 12);
    // Subtexto
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...grisMedio);
    doc.text(`Total de consultas en este expediente: ${grupo.historiales.length}`, MARGIN + 10, yPos + 21);
    // Badge PACIENTE
    doc.setFillColor(...verde);
    doc.roundedRect(PAGE_W - MARGIN - 32, yPos + 9, 30, 11, 3, 3, 'F');
    doc.setTextColor(...blanco);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8);
    doc.text('PACIENTE', PAGE_W - MARGIN - 17, yPos + 16, { align: 'center' });

    yPos += 40;

    // ── Consultas ─────────────────────────────────────────────────
    grupo.historiales.forEach((h, index) => {
      // Salto de página si no hay espacio
      const altEst = 50 + (h.notas ? 15 : 0);
      if (yPos + altEst > 276) {
        doc.addPage();
        dibujarEncabezado();
        yPos = 60;
      }

      // Círculo numerado
      doc.setFillColor(...verde);
      doc.circle(MARGIN + 5, yPos + 5, 5, 'F');
      doc.setTextColor(...blanco);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(8.5);
      doc.text(`${index + 1}`, MARGIN + 5, yPos + 6.3, { align: 'center' });

      // Título consulta
      const fechaCita = h.cita.fecha
        ? new Date(h.cita.fecha).toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' })
        : 'Sin fecha';
      const horaCita = h.cita.fecha
        ? new Date(h.cita.fecha).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
        : '';
      doc.setTextColor(...grisOscuro);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(10.5);
      doc.text(`Consulta  —  ${fechaCita}  ${horaCita}`, MARGIN + 14, yPos + 6);
      yPos += 14;

      // Bloque médico (fondo verde suave)
      doc.setFillColor(240, 253, 244);
      doc.setDrawColor(187, 247, 208);
      doc.setLineWidth(0.3);
      doc.roundedRect(MARGIN, yPos, CONTENT_W, 15, 3, 3, 'FD');
      doc.setTextColor(22, 101, 52);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9.5);
      doc.text(
        `Dr. ${h.cita.medico.usuario.nombre} ${h.cita.medico.usuario.apellido}`,
        MARGIN + 6, yPos + 7
      );
      if (h.cita.medico.especialidad) {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8.5);
        doc.setTextColor(...grisMedio);
        doc.text(`Especialidad: ${h.cita.medico.especialidad.nombre}`, MARGIN + 6, yPos + 12.5);
      }
      yPos += 19;

      // Tabla clínica sin encabezado, filas alternas
      const filas: any[] = [
        [
          { content: 'Diagnóstico', styles: { fontStyle: 'bold', textColor: grisOscuro, fillColor: [248, 250, 252] } },
          { content: h.diagnostico || 'Pendiente de registro médico', styles: { fillColor: [248, 250, 252] } }
        ],
        [
          { content: 'Receta / Tratamiento', styles: { fontStyle: 'bold', textColor: [29, 78, 216] as any, fillColor: [239, 246, 255] } },
          { content: h.receta || 'Sin receta aún', styles: { textColor: [30, 58, 138] as any, fillColor: [239, 246, 255] } }
        ],
      ];
      if (h.notas) {
        filas.push([
          { content: 'Notas Adicionales', styles: { fontStyle: 'bold', textColor: grisOscuro, fillColor: [248, 250, 252] } },
          { content: h.notas, styles: { fillColor: [248, 250, 252] } }
        ]);
      }

      autoTable(doc, {
        startY: yPos,
        body: filas,
        styles: {
          cellPadding: { top: 4, bottom: 4, left: 6, right: 6 },
          fontSize: 9,
          valign: 'middle',
          lineColor: [226, 232, 240] as any,
          lineWidth: 0.25,
        },
        columnStyles: {
          0: { cellWidth: 50 },
          1: { cellWidth: CONTENT_W - 50 }
        },
        margin: { left: MARGIN, right: MARGIN },
        tableLineColor: [226, 232, 240] as any,
        tableLineWidth: 0.3,
      });

      yPos = (doc as any).lastAutoTable.finalY + 7;

      // Línea separadora punteada entre consultas
      if (index < grupo.historiales.length - 1) {
        doc.setDrawColor(203, 213, 225);
        doc.setLineWidth(0.35);
        doc.setLineDashPattern([2, 2], 0);
        doc.line(MARGIN + 8, yPos, PAGE_W - MARGIN - 8, yPos);
        doc.setLineDashPattern([], 0);
        yPos += 8;
      }
    });

    agregarPiePagina();

    const nombreArchivo = `Historia_Clinica_${grupo.pacienteNombre}_${grupo.pacienteApellido}.pdf`
      .replace(/\s+/g, '_');
    doc.save(nombreArchivo);
    this.ns.success(`Historia clínica de ${grupo.pacienteNombre} generada con éxito`);
  }

  /** Descarga el PDF con el historial completo del propio paciente */
  descargarHistorialCompletoPaciente() {
    if (!this.usuario) return;
    
    const grupo: PacienteAgrupado = {
      pacienteId: this.usuario.id,
      pacienteNombre: this.usuario.nombre,
      pacienteApellido: this.usuario.apellido,
      fotoUrl: this.usuario.fotoUrl,
      historiales: this.historialesFiltrados,
      expandido: true
    };

    this.descargarPDFPaciente(grupo);
  }
}
