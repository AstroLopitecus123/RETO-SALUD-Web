import { Component, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ChatbotService } from '../../services/chatbot.service';

interface ChatMessage {
  role: 'bot' | 'user';
  text: string;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './chatbot.html',
  styleUrls: ['./chatbot.css']
})
export class ChatbotComponent implements AfterViewChecked {
  isOpen = false;
  isLoading = false;
  userInput = '';

  messages: ChatMessage[] = [
    {
      role: 'bot',
      text: '¡Hola! Soy el asistente virtual de R.E.T.O Salud. Pregúntame lo que desees en el recuadro de texto o elige una de las opciones rápidas.'
    }
  ];

  @ViewChild('chatScroll') private chatScrollContainer!: ElementRef;
  @ViewChild('chatLauncher') private chatLauncher!: ElementRef<HTMLButtonElement>;
  @ViewChild('chatInput') private chatInput!: ElementRef<HTMLInputElement>;
  private renderedMessageCount = 0;
  private nearBottom = true;

  constructor(private chatbotService: ChatbotService) {}

  ngAfterViewChecked() {
    if (this.renderedMessageCount !== this.messages.length) {
      this.renderedMessageCount = this.messages.length;
      if (this.isOpen && this.nearBottom) this.scrollToBottom();
    }
  }

  onScroll() {
    const el = this.chatScrollContainer.nativeElement;
    this.nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }

  toggleChat() {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      setTimeout(() => {
        this.chatInput.nativeElement.focus();
        if (this.nearBottom) this.scrollToBottom();
      });
    } else {
      this.chatLauncher.nativeElement.focus();
    }
  }

  scrollToBottom() {
    try {
      if (this.chatScrollContainer) {
        this.chatScrollContainer.nativeElement.scrollTop = this.chatScrollContainer.nativeElement.scrollHeight;
      }
    } catch(err) { }
  }

  sendQuickAction(action: string) {
    this.userInput = action;
    this.sendMessage();
  }

  sendMessage() {
    if (!this.userInput.trim() || this.isLoading) return;

    const query = this.userInput.trim();
    this.messages.push({ role: 'user', text: query });
    this.userInput = '';
    this.isLoading = true;

    this.chatbotService.enviarPregunta(query).subscribe({
      next: (res) => {
        if (res.respuesta) {
          this.messages.push({ role: 'bot', text: res.respuesta });
        } else if (res.error) {
          this.messages.push({ role: 'bot', text: 'Lo siento, ha ocurrido un error: ' + res.error });
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.messages.push({ role: 'bot', text: 'Lo siento, no pude conectar con nuestros servidores en este momento. Intenta más tarde.' });
        this.isLoading = false;
        console.error(err);
      }
    });
  }
}
