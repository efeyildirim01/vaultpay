// Thin client for the VaultPay REST API. Errors arrive as RFC 9457 problem details.

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

export type AccountView = {
  id: string;
  name: string;
  currency: string;
  status: 'ACTIVE' | 'FROZEN' | 'CLOSED';
  balanceMinor: number;
  createdAt: string;
};

export type MovementResult = {
  journalEntryId: string;
  type: 'DEPOSIT' | 'TRANSFER';
  amountMinor: number;
  replayed: boolean;
};

export type PendingReview = {
  assessmentId: string;
  userId: string;
  score: number;
  reasons: string;
  fromAccountId: string;
  toAccountId: string;
  amountMinor: number;
  flaggedAt: string;
};

export type ReviewOutcome = 'CLEARED' | 'CONFIRMED_FRAUD';

type Options = { method?: string; token?: string; body?: unknown; idempotencyKey?: string };

async function request<T>(path: string, { method = 'GET', token, body, idempotencyKey }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  const json = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    const retryAfter = response.headers.get('Retry-After');
    throw new ApiError(
      response.status,
      json?.detail ?? json?.title ?? `Request failed (${response.status})`,
      retryAfter ? Number(retryAfter) : undefined,
    );
  }
  return json as T;
}

export const api = {
  register: (email: string, password: string, fullName: string) =>
    request<{ id: string }>('/api/auth/register', { method: 'POST', body: { email, password, fullName } }),

  login: (email: string, password: string) =>
    request<{ accessToken: string }>('/api/auth/login', { method: 'POST', body: { email, password } }),

  accounts: (token: string) => request<AccountView[]>('/api/accounts', { token }),

  openAccount: (token: string, name: string) =>
    request<AccountView>('/api/accounts', { method: 'POST', token, body: { name, currency: 'GBP' } }),

  deposit: (token: string, key: string, accountId: string, amountMinor: number) =>
    request<MovementResult>(`/api/accounts/${accountId}/deposits`, {
      method: 'POST', token, idempotencyKey: key, body: { amountMinor, reference: 'Simulated deposit' },
    }),

  transfer: (token: string, key: string, fromAccountId: string, toAccountId: string, amountMinor: number,
             reference: string) =>
    request<MovementResult>('/api/transfers', {
      method: 'POST', token, idempotencyKey: key,
      body: { fromAccountId, toAccountId, amountMinor, reference: reference || null },
    }),

  pendingReviews: (token: string) => request<PendingReview[]>('/api/fraud/reviews/pending', { token }),

  review: (token: string, assessmentId: string, outcome: ReviewOutcome, note: string) =>
    request<{ accountFrozen: boolean }>(`/api/fraud/assessments/${assessmentId}/review`, {
      method: 'POST', token, body: { outcome, note: note || null },
    }),
};

/** Reads (does not verify) the JWT payload. The server verifies; the UI only decides which tabs to show. */
export function tokenClaims(token: string): { email?: string; scope?: string; exp?: number } {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(payload));
}

export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 429) return `Too many requests. Try again in ${e.retryAfterSeconds ?? 60} seconds.`;
    return e.message;
  }
  return 'Could not reach the server. It is safe to press the button again: the same request is retried.';
}
