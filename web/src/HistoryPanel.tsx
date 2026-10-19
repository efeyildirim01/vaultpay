import { useEffect, useState } from 'react';
import { AccountView, ApiError, TransactionView, api, describeError } from './api';
import { formatPence } from './money';

type Props = {
  token: string;
  account: AccountView | undefined;
  /** The customer's own accounts, to show "Savings" instead of an id when money moved between them. */
  ownAccounts: AccountView[];
  /** Changes whenever the account list reloads (e.g. after a transfer), so the statement reloads too. */
  refreshKey: unknown;
  onExpired: () => void;
};

export function HistoryPanel({ token, account, ownAccounts, refreshKey, onExpired }: Props) {
  const [items, setItems] = useState<TransactionView[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const accountId = account?.id;

  async function load(nextPage: number) {
    if (!accountId) return;
    try {
      const slice = await api.transactions(token, accountId, nextPage);
      setItems(prev => (nextPage === 0 ? slice.items : [...prev, ...slice.items]));
      setPage(nextPage);
      setHasMore(slice.hasMore);
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onExpired();
      else setError(describeError(e));
    }
  }

  // Reload from the first page when the account changes or the account list was refreshed.
  useEffect(() => { void load(0); }, [accountId, refreshKey]);

  if (!account) return null;

  return (
    <section className="card wide">
      <h2>History: {account.name}</h2>
      {error && <p className="error">{error}</p>}
      {items.length === 0 ? <p className="muted">No transactions yet.</p> : (
        <table>
          <thead>
            <tr><th>Date</th><th>Type</th><th>Counterparty</th><th>Reference</th><th className="num">Amount</th></tr>
          </thead>
          <tbody>
            {items.map(t => (
              <tr key={t.journalEntryId}>
                <td className="nowrap">{new Date(t.createdAt).toLocaleString('en-GB')}</td>
                <td>{t.type === 'DEPOSIT' ? 'Deposit' : t.amountMinor < 0 ? 'Transfer out' : 'Transfer in'}</td>
                <td>{t.counterpartyLabel
                     ?? ownAccounts.find(a => a.id === t.counterpartyAccountId)?.name
                     ?? <code className="id">{t.counterpartyAccountId.slice(0, 8)}</code>}</td>
                <td>{t.reference ?? <span className="muted">-</span>}</td>
                <td className={`num ${t.amountMinor < 0 ? 'out' : 'in'}`}>
                  {t.amountMinor > 0 ? '+' : ''}{formatPence(t.amountMinor)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {hasMore && <button className="link" onClick={() => load(page + 1)}>Load older transactions</button>}
    </section>
  );
}
