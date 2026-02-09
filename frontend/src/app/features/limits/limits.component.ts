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

  // Modal state
  modal = { open: false, mode: 'create' as 'create' | 'edit' };
  formData = {
    limitValue: 10000,
    globalWindowSeconds: 60 as number | null,
  };
  editingRule: RateLimitRule | null = null;
  formLoading = false;
  formError = '';

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

  openCreateModal(): void {
    this.modal = { open: true, mode: 'create' };
    this.formData = {
      limitValue: 10000,
      globalWindowSeconds: 60,
    };
    this.editingRule = null;
    this.formError = '';
  }

  openEditModal(rule: RateLimitRule): void {
    this.modal = { open: true, mode: 'edit' };
    this.editingRule = rule;
    this.formData = {
      limitValue: rule.limitValue,
      globalWindowSeconds: rule.globalWindowSeconds,
    };
    this.formError = '';
  }

  closeModal(): void {
    this.modal = { open: false, mode: 'create' };
    this.formData = {
      limitValue: 10000,
      globalWindowSeconds: 60,
    };
    this.editingRule = null;
    this.formError = '';
  }

  submitForm(): void {
    if (this.formData.limitValue <= 0) return;
    this.formLoading = true;
    this.formError = '';

    const body: CreateRateLimitRuleRequest = {
      limitType: 'GLOBAL',
      limitValue: this.formData.limitValue,
      globalWindowSeconds: this.formData.globalWindowSeconds ?? undefined,
      clientId: null,
    };

    const request$ = this.modal.mode === 'create'
      ? this.api.createRule(body)
      : this.api.updateRule(this.editingRule!.id, body);

    request$.subscribe({
      next: () => {
        this.closeModal();
        this.loadRules();
      },
      error: (err) => {
        this.formError = err?.error?.message || err?.message || 'Failed to save rule';
        this.formLoading = false;
      },
    });
  }

  ruleLabel(r: RateLimitRule): string {
    if (r.globalWindowSeconds) return `${r.limitValue} / ${r.globalWindowSeconds}s (global)`;
    return `${r.limitValue} / month (global)`;
  }
}
