import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Client, CreateClientRequest } from '../../core/api.service';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  clients: Client[] = [];
  loading = false;
  error = '';
  createName = '';
  creating = false;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.load();
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

  create(): void {
    if (!this.createName.trim()) return;
    this.creating = true;
    this.error = '';
    const req: CreateClientRequest = { name: this.createName.trim() };
    this.api.createClient(req).subscribe({
      next: () => {
        this.createName = '';
        this.creating = false;
        this.load();
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Failed to create client';
        this.creating = false;
      },
    });
  }
}
