import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface Client {
  id: string;
  name: string;
  apiKey: string;
  subscriptionPlanId: string | null;
  active: boolean;
}

export interface CreateClientRequest {
  name: string;
  subscriptionPlanId: string;
}

export interface SubscriptionPlan {
  id: string;
  name: string;
  monthlyLimit: number;
  windowLimit: number | null;
  windowSeconds: number | null;
  active: boolean;
}

export interface CreateSubscriptionPlanRequest {
  name: string;
  monthlyLimit: number;
  windowLimit?: number | null;
  windowSeconds?: number | null;
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

  // Subscription plans
  createPlan(body: CreateSubscriptionPlanRequest): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${this.base}/api/plans`, body);
  }

  getPlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.base}/api/plans`);
  }

  getPlan(id: string): Observable<SubscriptionPlan> {
    return this.http.get<SubscriptionPlan>(`${this.base}/api/plans/${id}`);
  }

  // Rate limit rules (global limits only; per-client limits come from subscription)
  createRule(body: CreateRateLimitRuleRequest): Observable<RateLimitRule> {
    return this.http.post<RateLimitRule>(`${this.base}/api/limits`, body);
  }

  getRules(): Observable<RateLimitRule[]> {
    return this.http.get<RateLimitRule[]>(`${this.base}/api/limits`);
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
