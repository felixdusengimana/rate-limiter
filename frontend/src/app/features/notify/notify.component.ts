import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, Client, NotificationRequest } from '../../core/api.service';
import { formatDuration } from '../../core/time-format.util';

interface ErrorDetail {
  title: string;
  message: string;
  retryAfter?: number;
  retryAfterFormatted?: string;
}

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
  notificationType: 'sms' | 'email' = 'email';
  sendingType: 'sms' | 'email' | null = null;
  errorDetail: ErrorDetail | null = null;
  successMessage = '';
  successId = '';
  rateLimit = { limit: 0, remaining: 0 };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadClients();
  }

  /**
   * Called when client selection changes.
   * Clears all state from previous client's message sending attempt.
   */
  onClientChange(): void {
    this.errorDetail = null;
    this.successMessage = '';
    this.successId = '';
    this.recipient = '';
    this.message = '';
  }

  private loadClients(): void {
    this.api.getClients().subscribe({
      next: (list) => (this.clients = list),
      error: () => {
        this.errorDetail = {
          title: 'Failed to load clients',
          message: 'Could not fetch the list of clients. Please refresh the page.',
        };
      },
    });
  }

  send(): void {
    if (!this.validateForm()) return;
    this.sendNotification(this.notificationType);
  }

  isValidRecipient(): boolean {
    const trimmed = this.recipient.trim();
    if (this.notificationType === 'email') {
      return this.isValidEmail(trimmed);
    } else {
      return this.isValidPhone(trimmed);
    }
  }

  private validateForm(): boolean {
    if (!this.selectedClient?.apiKey || !this.recipient.trim() || !this.message.trim()) {
      return false;
    }
    return this.isValidRecipient();
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  private isValidPhone(phone: string): boolean {
    // Accept international format: +250788123456 or +1234567890
    const phoneRegex = /^\+\d{1,3}\d{6,14}$/;
    return phoneRegex.test(phone);
  }

  private sendNotification(type: 'sms' | 'email'): void {
    this.loading = true;
    this.sendingType = type;
    this.errorDetail = null;
    this.successMessage = '';
    this.successId = '';

    const body: NotificationRequest = {
      recipient: this.recipient.trim(),
      message: this.message.trim(),
    };

    const request$ = type === 'sms'
      ? this.api.sendSms(this.selectedClient!.apiKey, body)
      : this.api.sendEmail(this.selectedClient!.apiKey, body);

    request$.subscribe({
      next: (res) => {
        this.loading = false;
        this.sendingType = null;
        this.successMessage = `${type === 'sms' ? 'SMS' : 'Email'} successfully sent!`;
        this.successId = res.id;
        this.resetForm();
      },
      error: (err) => {
        this.loading = false;
        this.sendingType = null;
        this.handleError(err, type);
      },
    });
  }

  private handleError(err: HttpErrorResponse, type: string): void {
    const errorData = err.error || {};

    if (err.status === 429) {
      const retryAfter = parseInt(err.headers.get('retry-after') || '60', 10);
      const limitType = errorData.limitType || 'unknown';
      const limit = errorData.limit || 'N/A';
      const current = errorData.current || 'N/A';
      const formattedDuration = errorData.retryAfterFormatted || formatDuration(retryAfter);
      
      let detailMessage = '';
      if (limitType === 'GLOBAL') {
        detailMessage = `Global system limit reached (${current}/${limit} requests). `;
      } else {
        detailMessage = `Your subscription plan limit exhausted (${current}/${limit} requests). `;
      }
      detailMessage += `Wait ${formattedDuration} before retrying.`;
      
      this.errorDetail = {
        title: 'Rate Limit Exceeded',
        message: detailMessage,
        retryAfter,
        retryAfterFormatted: formattedDuration,
      };
    } else if (err.status === 401) {
      this.errorDetail = {
        title: 'Unauthorized',
        message: errorData.message || 'Invalid or missing API key. Please select a valid client.',
      };
    } else if (err.status === 403) {
      this.errorDetail = {
        title: 'Forbidden',
        message: errorData.message || 'This client is inactive. Please contact support.',
      };
    } else if (err.status === 400) {
      this.errorDetail = {
        title: 'Invalid Input',
        message: errorData.message || `Invalid ${type} format. Please check the recipient field.`,
      };
    } else if (err.status === 0) {
      this.errorDetail = {
        title: 'Connection Error',
        message: 'Cannot reach the server. Please check your connection and try again.',
      };
    } else {
      this.errorDetail = {
        title: `Failed to send ${type}`,
        message: errorData.message || `An error occurred while sending your ${type}. Please try again.`,
      };
    }
  }

  private resetForm(): void {
    this.message = '';
    this.recipient = '';
  }

  clearError(): void {
    this.errorDetail = null;
  }

  clearSuccess(): void {
    this.successMessage = '';
    this.successId = '';
  }
}
