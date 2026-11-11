import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ConversionHistoryResponse } from '../models/conversion-history-response';

@Injectable({ providedIn: 'root' })
export class ConversionHistoryApi {
  private readonly http = inject(HttpClient);

  findHistory(
    limit = 20,
    offset = 0,
  ): Observable<{ entries: ConversionHistoryResponse[]; totalCount: number }> {
    return this.http.get<ConversionHistoryResponse[]>(
      '/api/conversions/history',
      { params: { limit, offset }, observe: 'response' },
    ).pipe(
      map((response) => ({
        entries: response.body ?? [],
        totalCount: Number(response.headers.get('X-Total-Count')),
      })),
    );
  }
}
