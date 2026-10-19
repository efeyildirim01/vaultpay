import { useMemo } from 'react';

/**
 * One Idempotency-Key per distinct request. The key only changes when the form content changes
 * (or after a success, via `round`), so pressing "Send" again after a timeout retries the same
 * request and the server moves the money at most once.
 */
export function useIdempotencyKey(round: number, ...formValues: unknown[]): string {
  return useMemo(() => crypto.randomUUID(), [round, ...formValues]);
}
