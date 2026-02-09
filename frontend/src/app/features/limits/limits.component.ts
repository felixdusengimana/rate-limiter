import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ApiService,
  CreateRateLimitRuleRequest,
  RateLimitRule,
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
  loading = false;
  error = '';
  creating = false;

  newRule: CreateRateLimitRuleRequest = {
    limitType: 'GLOBAL',
    limitValue: 10000,
    windowSeconds: null,
    globalWindowSeconds: 60,
    clientId: null,
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadRules();
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

  create(): void {
    this.creating = true;
    this.error = '';
    const body: CreateRateLimitRuleRequest = {
      limitType: 'GLOBAL',
      limitValue: this.newRule.limitValue,
      globalWindowSeconds: this.newRule.globalWindowSeconds ?? undefined,
      clientId: null,
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
      limitType: 'GLOBAL',
      limitValue: 10000,
      windowSeconds: null,
      globalWindowSeconds: 60,
      clientId: null,
    };
  }

  ruleLabel(r: RateLimitRule): string {
    if (r.globalWindowSeconds) return `${r.limitValue} / ${r.globalWindowSeconds}s (global)`;
    return `${r.limitValue} / month (global)`;
  }
}
