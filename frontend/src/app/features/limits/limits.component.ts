import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ApiService,
  Client,
  CreateRateLimitRuleRequest,
  RateLimitRule,
  RateLimitType,
} from '../../core/api.service';

@Component({
  selector: 'app-limits',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './limits.component.html',
  styleUrl: './limits.component.scss',
})
export class LimitsComponent implements OnInit {
  rules: RateLimitRule[] = [];
  clients: Client[] = [];
  loading = false;
  error = '';
  creating = false;

  newRule: CreateRateLimitRuleRequest = {
    limitType: 'WINDOW',
    limitValue: 100,
    windowSeconds: 60,
    globalWindowSeconds: null,
    clientId: null,
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadRules();
    this.loadClients();
  }

  loadRules(): void {
    this.loading = true;
    this.error = '';
    this.api.getRules().subscribe({
      next: (list) => {
        this.rules = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to load rules';
        this.loading = false;
      },
    });
  }

  loadClients(): void {
    this.api.getClients().subscribe({
      next: (list) => (this.clients = list),
      error: () => {},
    });
  }

  get isGlobal(): boolean {
    return this.newRule.limitType === 'GLOBAL';
  }

  create(): void {
    this.creating = true;
    this.error = '';
    const body: CreateRateLimitRuleRequest = {
      limitType: this.newRule.limitType,
      limitValue: this.newRule.limitValue,
      windowSeconds: this.newRule.limitType === 'WINDOW' ? this.newRule.windowSeconds ?? 60 : undefined,
      globalWindowSeconds:
        this.newRule.limitType === 'GLOBAL' ? this.newRule.globalWindowSeconds ?? undefined : undefined,
      clientId: this.isGlobal ? null : this.newRule.clientId || undefined,
    };
    this.api.createRule(body).subscribe({
      next: () => {
        this.creating = false;
        this.loadRules();
        this.resetForm();
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to create rule';
        this.creating = false;
      },
    });
  }

  resetForm(): void {
    this.newRule = {
      limitType: 'WINDOW',
      limitValue: 100,
      windowSeconds: 60,
      globalWindowSeconds: null,
      clientId: this.clients[0]?.id ?? null,
    };
  }

  ruleLabel(r: RateLimitRule): string {
    if (r.limitType === 'WINDOW')
      return `${r.limitValue} / ${r.windowSeconds ?? 60}s` + (r.clientId ? ` (client)` : '');
    if (r.limitType === 'MONTHLY') return `${r.limitValue} / month` + (r.clientId ? ' (client)' : '');
    if (r.limitType === 'GLOBAL') {
      if (r.globalWindowSeconds) return `${r.limitValue} / ${r.globalWindowSeconds}s (global)`;
      return `${r.limitValue} / month (global)`;
    }
    return `${r.limitType} ${r.limitValue}`;
  }
}
