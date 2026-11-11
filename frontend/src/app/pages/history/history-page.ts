import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ConversionHistoryApi } from '../../core/api/conversion-history-api';
import { ConversionHistoryResponse } from '../../core/models/conversion-history-response';

@Component({
  selector: 'app-history-page',
  imports: [DatePipe],
  templateUrl: './history-page.html',
  styleUrl: './history-page.css'
})
export class HistoryPage implements OnInit {
  private readonly historyApi = inject(ConversionHistoryApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pageSize = 20;

  readonly entries = signal<ConversionHistoryResponse[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  readonly paginationItems = computed(() => {
    const last = this.totalPages();
    if (last === 0) {
      return [];
    }

    const current = this.page() + 1;
    const firstVisible = Math.max(1, Math.min(current - 1, last - 2));
    const lastVisible = Math.min(last, firstVisible + 2);
    const visible = new Set([1, last]);

    for (let number = firstVisible; number <= lastVisible; number++) {
      visible.add(number);
    }

    const sorted = [...visible].sort((a, b) => a - b);
    const items: (number | 'ellipsis')[] = [];

    for (const number of sorted) {
      const previous = items.at(-1);
      if (typeof previous === 'number' && number > previous + 1) {
        items.push('ellipsis');
      }

      items.push(number);
    }

    return items;
  });

  ngOnInit(): void {
    this.loadPage(0);
  }

  loadPage(targetPage: number): void {
    if (targetPage < 0 || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    const offset = targetPage * this.pageSize;

    this.historyApi.findHistory(this.pageSize, offset)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({entries, totalCount}) => {
          this.entries.set(entries);
          this.totalPages.set(Math.ceil(totalCount / this.pageSize));
          this.page.set(targetPage);
        },
        error: () => {
          this.errorMessage.set(
            'Não foi possível carregar o histórico. Tente novamente.',
          );
        },
      });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'SUCCESS':
        return 'Concluída';
      case 'FAILED':
        return 'Falhou';
      case 'RUNNING':
        return 'Sem encerramento registrado';
      default:
        return 'Status desconhecido';
    }
  }
}
