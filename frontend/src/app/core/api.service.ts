import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface Client {
  id: string;
  name: string;
  apiKey: string;
  active: boolean;
}

export interface CreateClientRequest {
  name: string;
}

export type RateLimitType = 'WINDOW' | 'MONTHLY' | 'GLOBAL';

export interface RateLimitRule {
  id: string;
  limitType: RateLimitType;
  limitValue: number;
  windowSeconds: number | null;
  globalWindowSeconds: number | null;
  clientId: string | null;
  active: boolean;
}

export interface CreateRateLimitRuleRequest {
  limitType: RateLimitType;
  limitValue: number;
  windowSeconds?: number | null;
  globalWindowSeconds?: number | null;
  clientId?: string | null;
}

export interface NotificationRequest {
  recipient: string;
  message: string;
}

export interface NotificationResponse {
  success: boolean;
  id: string;
  channel: string;
  timestamp: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // Clients
  createClient(body: CreateClientRequest): Observable<Client> {
    return this.http.post<Client>(`${this.base}/api/clients`, body);
  }

  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(`${this.base}/api/clients`);
  }

  getClient(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.base}/api/clients/${id}`);
  }

  // Rate limit rules
  createRule(body: CreateRateLimitRuleRequest): Observable<RateLimitRule> {
    return this.http.post<RateLimitRule>(`${this.base}/api/limits`, body);
  }

  getRules(): Observable<RateLimitRule[]> {
    return this.http.get<RateLimitRule[]>(`${this.base}/api/limits`);
  }

  getRulesByClient(clientId: string): Observable<RateLimitRule[]> {
    return this.http.get<RateLimitRule[]>(`${this.base}/api/limits/client/${clientId}`);
  }

  // Notifications (require X-API-Key header)
  sendSms(apiKey: string, body: NotificationRequest): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`${this.base}/api/notify/sms`, body, {
      headers: { 'X-API-Key': apiKey },
    });
  }

  sendEmail(apiKey: string, body: NotificationRequest): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`${this.base}/api/notify/email`, body, {
      headers: { 'X-API-Key': apiKey },
    });
  }
}
