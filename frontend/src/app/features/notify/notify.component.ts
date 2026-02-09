import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Client, NotificationRequest } from '../../core/api.service';

@Component({
  selector: 'app-notify',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notify.component.html',
  styleUrl: './notify.component.scss',
})
export class NotifyComponent implements OnInit {
  clients: Client[] = [];
  selectedClient: Client | null = null;
  recipient = '';
  message = '';
  loading = false;
  error = '';
  success = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getClients().subscribe({
      next: (list) => (this.clients = list),
      error: () => {},
    });
  }

  sendSms(): void {
    if (!this.selectedClient?.apiKey || !this.recipient.trim() || !this.message.trim()) return;
    this.loading = true;
    this.error = '';
    this.success = '';
    const body: NotificationRequest = { recipient: this.recipient.trim(), message: this.message.trim() };
    this.api.sendSms(this.selectedClient.apiKey, body).subscribe({
      next: (res) => {
        this.loading = false;
        this.success = `SMS sent. ID: ${res.id}`;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || err?.message || (err.status === 429 ? 'Rate limit exceeded. Retry later.' : 'Failed to send SMS');
      },
    });
  }

  sendEmail(): void {
    if (!this.selectedClient?.apiKey || !this.recipient.trim() || !this.message.trim()) return;
    this.loading = true;
    this.error = '';
    this.success = '';
    const body: NotificationRequest = { recipient: this.recipient.trim(), message: this.message.trim() };
    this.api.sendEmail(this.selectedClient.apiKey, body).subscribe({
      next: (res) => {
        this.loading = false;
        this.success = `Email sent. ID: ${res.id}`;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || err?.message || (err.status === 429 ? 'Rate limit exceeded. Retry later.' : 'Failed to send email');
      },
    });
  }
}
