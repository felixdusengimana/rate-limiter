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
  creating = false;

  newPlan: CreateSubscriptionPlanRequest = {
    name: '',
    monthlyLimit: 1000,
    windowLimit: null,
    windowSeconds: null,
  };

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

  create(): void {
    if (!this.newPlan.name.trim()) return;
    this.creating = true;
    this.error = '';
    const body: CreateSubscriptionPlanRequest = {
      name: this.newPlan.name.trim(),
      monthlyLimit: this.newPlan.monthlyLimit,
      windowLimit: this.newPlan.windowLimit ?? undefined,
      windowSeconds: this.newPlan.windowSeconds ?? undefined,
    };
    this.api.createPlan(body).subscribe({
      next: () => {
        this.newPlan = { name: '', monthlyLimit: 1000, windowLimit: null, windowSeconds: null };
        this.creating = false;
        this.load();
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to create plan';
        this.creating = false;
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
