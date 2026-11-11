import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ConversionHistoryApi } from './conversion-history-api';

describe('ConversionHistoryApi', () => {
  it('shouldRequestHistoryWithLimitAndOffset', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const api = TestBed.inject(ConversionHistoryApi);
    const httpTesting = TestBed.inject(HttpTestingController);

    const responsePromise = firstValueFrom(api.findHistory(20, 20));

    const request = httpTesting.expectOne(
      (pending) => pending.url === '/api/conversions/history',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('limit')).toBe('20');
    expect(request.request.params.get('offset')).toBe('20');

    request.flush([], { headers: { 'X-Total-Count': '41' } });

    expect(await responsePromise).toEqual({ entries: [], totalCount: 41,});
    httpTesting.verify();
  });
});
