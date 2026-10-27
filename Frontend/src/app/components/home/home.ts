import { Component, AfterViewInit, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { NarratorDirective } from '../../directives/narrator.directive';
import { UsuarioService } from '../../services/usuario.service';
import { MedicoService } from '../../services/medico.service';

import * as L from 'leaflet';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule, NarratorDirective],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements AfterViewInit, OnInit {
  especialidades = [
    { titulo: 'Cardiología', descripcion: 'Especialistas en la salud de su corazón y sistema circulatorio.', imagen: 'assets/images/ServiceEmergency.webp' },
    { titulo: 'Pediatría', descripcion: 'Atención médica integral para el crecimiento sano de sus hijos.', imagen: 'assets/images/ServiceNurse.webp' },
    { titulo: 'Medicina General', descripcion: 'Chequeos preventivos y diagnóstico clínico de primer nivel.', imagen: 'assets/images/HomeDoctor.webp' },
    { titulo: 'Ginecología', descripcion: 'Cuidado especializado para la salud integral de la mujer.', imagen: 'assets/images/ServiceLab.webp' }
  ];

  doctores: any[] = [];

  sedes = [
    { nombre: 'Sede Miraflores', direccion: 'Av. Larco 123', telefono: '972 746 609', horario: 'Abre Lunes a las 8:00 a.m.', icono: 'fa-hospital', lat: -12.1227, lng: -77.0296, img: 'assets/images/SedeMiraflores.webp' },
    { nombre: 'Sede La Molina', direccion: 'Av. Javier Prado 456', telefono: '993 421 655', horario: 'Abre Lunes a las 8:00 a.m.', icono: 'fa-building', lat: -12.0722, lng: -76.9450, img: 'assets/images/SedeMolina.webp' },
    { nombre: 'Sede Surco', direccion: 'Av. Primavera 789', telefono: '904 042 627', horario: 'Abre Martes a las 9:00 a.m.', icono: 'fa-clinic-medical', lat: -12.1167, lng: -76.9833, img: 'assets/images/SedeSurco.webp' },
    { nombre: 'Sede Santa Anita', direccion: 'Av. Ruiseñores 120', telefono: '924 656 793', horario: 'Abre Lunes a las 8:30 a.m.', icono: 'fa-house-medical', lat: -12.0433, lng: -76.9536, img: 'assets/images/SedeSantaAnita.webp' }
  ];

  private map: any;
  private markers: any[] = [];

  constructor(
    private router: Router,
    private usuarioService: UsuarioService,
    private medicoService: MedicoService
  ) {}

  ngOnInit() {
    this.cargarDoctores();
  }

  cargarDoctores() {
    this.medicoService.top4Publico().subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.doctores = data.map((doc: any, index: number) => ({
            nombre: `Dr. ${doc.nombre} ${doc.apellido}`,
            cargo: doc.especialidad || 'Médico General',
            foto: doc.fotoUrl || 'assets/images/HomeDoctor.webp',
            totalCitas: doc.totalCitas || 0,
            ranking: index + 1
          }));
        } else {
          this.usarDoctoresFallback();
        }
      },
      error: () => {
        this.usarDoctoresFallback();
      }
    });
  }

  private usarDoctoresFallback() {
    this.doctores = [
      { nombre: 'Dr. Carlos Ruiz', cargo: 'Cardiólogo Senior', foto: 'assets/images/HomeDoctor.webp', totalCitas: 0, ranking: 1 },
      { nombre: 'Dra. Ana Torres', cargo: 'Pediatra Especialista', foto: 'assets/images/HomeDoctor.webp', totalCitas: 0, ranking: 2 },
      { nombre: 'Dr. Luis Mendez', cargo: 'Médico de Familia', foto: 'assets/images/HomeDoctor.webp', totalCitas: 0, ranking: 3 }
    ];
  }

  ngAfterViewInit() {
    this.initMap();
  }

  private initMap() {

    this.map = L.map('map', {
      scrollWheelZoom: false,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    this.sedes.forEach(sede => {
      const popupContent = `
        <div class="premium-popup">
          <div class="popup-header" style="background-image: url('${sede.img}')">
            <div class="popup-badge">Sede Oficial</div>
          </div>
          <div class="popup-content">
            <h3>${sede.nombre}</h3>
            <p><i class="fa-solid fa-location-dot"></i> ${sede.direccion}</p>
            <div class="popup-features">
              <span><i class="fa-solid fa-clock"></i> 24/7</span>
              <span><i class="fa-solid fa-shield-heart"></i> Seguro</span>
            </div>
            <button class="popup-btn-premium" onclick="window.dispatchEvent(new CustomEvent('agendar-cita-mapa'))">
              Agendar Cita
              <i class="fa-solid fa-arrow-right"></i>
            </button>
          </div>
        </div>
      `;

      const customIcon = L.divIcon({
        className: '',
        html: `
          <div style="
            width:38px;height:38px;
            background:linear-gradient(135deg,#0a7c4a,#0fbf6a);
            color:white;
            border-radius:50% 50% 50% 0;
            display:flex;align-items:center;justify-content:center;
            transform:rotate(-45deg);
            box-shadow:0 4px 15px rgba(15,191,106,0.6);
            position:relative;z-index:9999;
            border:2px solid white;
          ">
            <i class="fa-solid fa-heart-pulse" style="transform:rotate(45deg);font-size:1rem;"></i>
          </div>
          <div style="
            position:absolute;top:-8px;left:50%;
            width:46px;height:46px;margin-left:-23px;
            background:rgba(15,191,106,0.25);border-radius:50%;
            animation:markerPulse 2s infinite ease-out;pointer-events:none;
          "></div>
        `,
        iconSize: [38, 38],
        iconAnchor: [19, 38],
        popupAnchor: [0, -42]
      });

      const marker = L.marker([sede.lat, sede.lng], { 
        icon: customIcon, 
        alt: sede.nombre,
        title: sede.nombre 
      }).addTo(this.map);

      this.markers.push({ sede, marker });

      marker.bindPopup(popupContent, {
        closeButton: false,
        className: 'premium-glass-popup',
        minWidth: 280
      });
    });

    // Auto-encuadra el mapa para mostrar todas las sedes
    const bounds = L.latLngBounds(this.sedes.map(s => [s.lat, s.lng]));
    this.map.fitBounds(bounds, { padding: [50, 50] });
  }

  @HostListener('window:agendar-cita-mapa', ['$event'])
  onAgendarCitaMapa(event: any) {
    this.sacarCita();
  }

  centrarMapa(sede: any) {
    if (this.map) {
      this.map.flyTo([sede.lat, sede.lng], 15, {
        animate: true,
        duration: 1.5
      });
      // Find marker and open popup
      const markerObj = this.markers.find(m => m.sede.nombre === sede.nombre);
      if (markerObj) {
        setTimeout(() => markerObj.marker.openPopup(), 1500);
      }
    }
  }

  sacarCita() {
    const usuarioJson = localStorage.getItem('usuario');
    if (usuarioJson) {
      const usuario = JSON.parse(usuarioJson);

      if (usuario.rol === 'PACIENTE' || usuario.rol === 'ADMIN' || usuario.rol === 'RECEPCION') {
        this.router.navigate(['/mis-citas'], { queryParams: { accion: 'nueva-cita' } });
      } else if (usuario.rol === 'MEDICO') {
        this.router.navigate(['/medico/dashboard']);
      }
    } else {

      this.router.navigate(['/login']);
    }
  }

  scrollTo(sectionId: string, event: Event) {
    event.preventDefault();
    const element = document.querySelector(`.${sectionId}`) as HTMLElement || document.getElementById(sectionId);
    if (element) {
      const headerHeight = 90;
      const elementTop = element.getBoundingClientRect().top + window.scrollY;
      window.scrollTo({ top: elementTop - headerHeight, behavior: 'smooth' });
    } else if (sectionId === 'inicio') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
}
