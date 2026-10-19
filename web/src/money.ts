// Money is integer pence everywhere, as in the backend. User input is parsed as text,
// never through parseFloat, so "0.1 + 0.2" style rounding errors cannot happen.

const POUNDS = /^(\d{1,7})(?:\.(\d{1,2}))?$/;

/** "12.3" -> 1230, "12" -> 1200. Returns null for anything that is not a positive amount with at most 2 decimals. */
export function parsePounds(input: string): number | null {
  const match = POUNDS.exec(input.trim());
  if (!match) {
    return null;
  }
  const pence = Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'));
  return pence > 0 ? pence : null;
}

/** 123456 -> "£1,234.56" */
export function formatPence(pence: number): string {
  const sign = pence < 0 ? '-' : '';
  const abs = Math.abs(pence);
  const pounds = Math.floor(abs / 100).toLocaleString('en-GB');
  return `${sign}£${pounds}.${String(abs % 100).padStart(2, '0')}`;
}
