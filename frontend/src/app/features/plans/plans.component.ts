import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ApiService,
  CreateSubscriptionPlanRequest,
  SubscriptionPlan,
} from '../../core/api.service';

@Component({
  selector: 'app-plans',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './plans.component.html',
  styleUrl: './plans.component.scss',
})
export class PlansComponent implements OnInit {
  plans: SubscriptionPlan[] = [];
  loading = false;
  error = '';

  // Modal state
  modal = { open: false, mode: 'create' as 'create' | 'edit' };
  formData = {
    name: '',
    monthlyLimit: 1000,
    windowLimit: null as number | null,
    windowSeconds: null as number | null,
  };
  editingPlan: SubscriptionPlan | null = null;
  formLoading = false;
  formError = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.getPlans().subscribe({
      next: (list) => {
        this.plans = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to load plans';
        this.loading = false;
      },
    });
  }

  openCreateModal(): void {
    this.modal = { open: true, mode: 'create' };
    this.formData = {
      name: '',
      monthlyLimit: 1000,
      windowLimit: null,
      windowSeconds: null,
    };
    this.editingPlan = null;
    this.formError = '';
  }

  openEditModal(plan: SubscriptionPlan): void {
    this.modal = { open: true, mode: 'edit' };
    this.editingPlan = plan;
    this.formData = {
      name: plan.name,
      monthlyLimit: plan.monthlyLimit,
      windowLimit: plan.windowLimit,
      windowSeconds: plan.windowSeconds,
    };
    this.formError = '';
  }

  closeModal(): void {
    this.modal = { open: false, mode: 'create' };
    this.formData = {
      name: '',
      monthlyLimit: 1000,
      windowLimit: null,
      windowSeconds: null,
    };
    this.editingPlan = null;
    this.formError = '';
  }

  submitForm(): void {
    if (!this.formData.name.trim()) return;
    this.formLoading = true;
    this.formError = '';

    const body: CreateSubscriptionPlanRequest = {
      name: this.formData.name.trim(),
      monthlyLimit: this.formData.monthlyLimit,
      windowLimit: this.formData.windowLimit ?? undefined,
      windowSeconds: this.formData.windowSeconds ?? undefined,
    };

    const request$ = this.modal.mode === 'create'
      ? this.api.createPlan(body)
      : this.api.updatePlan(this.editingPlan!.id, body);

    request$.subscribe({
      next: () => {
        this.closeModal();
        this.load();
      },
      error: (err) => {
        const action = this.modal.mode === 'create' ? 'create' : 'update';
        this.formError = err?.error?.message || err?.message || `Failed to ${action} plan`;
        this.formLoading = false;
      },
    });
  }

  planSummary(p: SubscriptionPlan): string {
    const parts = [`${p.monthlyLimit.toLocaleString()} / month`];
    if (p.windowLimit != null && p.windowSeconds != null) {
      parts.push(`${p.windowLimit} / ${p.windowSeconds}s`);
    }
    return parts.join(' · ');
  }
}
