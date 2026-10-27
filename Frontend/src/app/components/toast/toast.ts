import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, ToastMessage } from '../../services/notification.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container" *ngIf="toasts.length > 0">
      <div *ngFor="let toast of toasts" 
           class="toast-item" 
           [ngClass]="toast.type"
           (click)="removeToast(toast)">
        <div class="toast-icon">
          <i class="fa-solid" [ngClass]="getIcon(toast.type)"></i>
        </div>
        <div class="toast-content">
          <span class="toast-message">{{ toast.message }}</span>
        </div>
        <div class="toast-close">
           <i class="fa-solid fa-xmark"></i>
        </div>
        <div class="toast-progress" [style.animationDuration.ms]="toast.duration"></div>
      </div>
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      top: 25px;
      right: 25px;
      z-index: 999999 !important;
      display: flex;
      flex-direction: column;
      gap: 12px;
      pointer-events: none;
      width: min(420px, calc(100% - 50px));
      max-height: calc(100vh - 50px);
      max-height: calc(100dvh - 50px);
      overflow-y: auto;
      overflow-x: hidden;
    }

    .toast-item {
      pointer-events: auto;
      box-sizing: border-box;
      min-width: 0;
      width: 100%;
      flex-shrink: 0;
      padding: 16px 20px;
      border-radius: 16px;
      background: white;
      box-shadow: 0 15px 40px rgba(0,0,0,0.12);
      display: flex;
      align-items: center;
      gap: 15px;
      position: relative;
      overflow: hidden;
      cursor: pointer;
      animation: toastIn 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55) forwards;
      border-left: 5px solid #ccc;
    }

    .toast-item.success { border-left-color: #10b981; }
    .toast-item.error { border-left-color: #ef4444; }
    .toast-item.info { border-left-color: #3b82f6; }

    .toast-icon {
      flex-shrink: 0;
      width: 36px;
      height: 36px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
    }

    .success .toast-icon { background: rgba(16, 185, 129, 0.1); color: #10b981; }
    .error .toast-icon { background: rgba(239, 68, 68, 0.1); color: #ef4444; }
    .info .toast-icon { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }

    .toast-content { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .toast-message { font-weight: 600; color: #1e293b; font-size: 0.95rem; }

    .toast-close { color: #94a3b8; font-size: 0.9rem; opacity: 0.5; transition: 0.3s; }
    .toast-item:hover .toast-close { opacity: 1; }

    @media (max-width: 600px) {
      .toast-container {
        top: max(12px, env(safe-area-inset-top));
        right: 12px;
        width: calc(100% - 24px);
        max-height: calc(100dvh - 24px - env(safe-area-inset-top));
      }
      .toast-item { padding: 14px; gap: 10px; }
    }

    .toast-progress {
      position: absolute;
      bottom: 0;
      left: 0;
      height: 3px;
      width: 100%;
      background: rgba(0,0,0,0.05);
      transform-origin: left;
      animation: progress linear forwards;
    }

    .success .toast-progress { background: #10b98130; }
    .error .toast-progress { background: #ef444430; }
    .info .toast-progress { background: #3b82f630; }

    @keyframes toastIn {
      from { transform: translateX(100%); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }

    @keyframes progress {
      from { transform: scaleX(1); }
      to { transform: scaleX(0); }
    }
  `]
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: (ToastMessage & { id: number })[] = [];
  private subscription: Subscription | null = null;
  private idCounter = 0;

  constructor(private ns: NotificationService) {}

  ngOnInit() {
    this.subscription = this.ns.toastState$.subscribe(toast => {
      const id = this.idCounter++;
      const newToast = { ...toast, id };
      this.toasts.push(newToast);

      setTimeout(() => {
        this.removeToast(newToast);
      }, toast.duration || 4000);
    });
  }

  removeToast(toast: any) {
    this.toasts = this.toasts.filter(t => t.id !== toast.id);
  }

  getIcon(type: string): string {
    switch(type) {
      case 'success': return 'fa-circle-check';
      case 'error': return 'fa-circle-xmark';
      default: return 'fa-circle-info';
    }
  }

  ngOnDestroy() {
    this.subscription?.unsubscribe();
  }
}
