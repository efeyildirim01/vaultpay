import { describe, expect, it } from 'vitest';
import { formatPence, parsePounds } from './money';

describe('parsePounds', () => {
  it('parses whole pounds and pence without floating point', () => {
    expect(parsePounds('12')).toBe(1200);
    expect(parsePounds('12.3')).toBe(1230);
    expect(parsePounds('0.07')).toBe(7);
    expect(parsePounds(' 1900.00 ')).toBe(190000);
  });

  it('rejects zero, negatives, extra decimals and junk', () => {
    for (const bad of ['', '0', '0.00', '-5', '1.234', '1,000', 'abc', '1e3']) {
      expect(parsePounds(bad)).toBeNull();
    }
  });
});

describe('formatPence', () => {
  it('formats pence as pounds', () => {
    expect(formatPence(0)).toBe('£0.00');
    expect(formatPence(7)).toBe('£0.07');
    expect(formatPence(123456)).toBe('£1,234.56');
    expect(formatPence(-2500)).toBe('-£25.00');
  });
});
