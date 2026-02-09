import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Client, CreateClientRequest, SubscriptionPlan } from '../../core/api.service';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  clients: Client[] = [];
  plans: SubscriptionPlan[] = [];
  loading = false;
  error = '';

  // Modal state
  modal = { open: false, mode: 'create' as 'create' | 'edit' };
  formData = { name: '', planId: '' };
  editingClient: Client | null = null;
  formLoading = false;
  formError = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.load();
    this.loadPlans();
  }

  private loadPlans(): void {
    this.api.getPlans().subscribe({
      next: (list) => (this.plans = list),
      error: () => {},
    });
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.getClients().subscribe({
      next: (list) => {
        this.clients = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to load clients';
        this.loading = false;
      },
    });
  }

  openCreateModal(): void {
    this.modal = { open: true, mode: 'create' };
    this.formData = { name: '', planId: '' };
    this.editingClient = null;
    this.formError = '';
  }

  openEditModal(client: Client): void {
    this.modal = { open: true, mode: 'edit' };
    this.editingClient = client;
    this.formData = { name: client.name, planId: client.subscriptionPlanId || '' };
    this.formError = '';
  }

  closeModal(): void {
    this.modal = { open: false, mode: 'create' };
    this.formData = { name: '', planId: '' };
    this.editingClient = null;
    this.formError = '';
  }

  submitForm(): void {
    if (!this.formData.name.trim() || !this.formData.planId) return;
    this.formLoading = true;
    this.formError = '';

    const req: CreateClientRequest = {
      name: this.formData.name.trim(),
      subscriptionPlanId: this.formData.planId,
    };

    const request$ = this.modal.mode === 'create'
      ? this.api.createClient(req)
      : this.api.updateClient(this.editingClient!.id, req);

    request$.subscribe({
      next: () => {
        this.closeModal();
        this.load();
      },
      error: (err) => {
        const action = this.modal.mode === 'create' ? 'create' : 'update';
        this.formError = err?.error?.message || err?.message || `Failed to ${action} client`;
        this.formLoading = false;
      },
    });
  }

  planName(planId: string | null): string {
    if (!planId) return '—';
    const p = this.plans.find((x) => x.id === planId);
    return p ? p.name : planId;
  }
}
