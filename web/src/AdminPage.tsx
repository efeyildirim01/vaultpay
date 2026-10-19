import { useCallback, useEffect, useState } from 'react';
import { AdminOverview, ApiError, AuditView, api, describeError } from './api';
import { formatPence } from './money';

type Props = { token: string; onExpired: () => void };

export function AdminPage({ token, onExpired }: Props) {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [rows, setRows] = useState<AuditView[]>([]);
  const [actions, setActions] = useState<string[]>([]);
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) onExpired();
    else setError(describeError(e));
  }, [onExpired]);

  const loadAudit = useCallback(async (filter: string, nextPage: number) => {
    try {
      const result = await api.adminAudit(token, filter, nextPage);
      setRows(prev => (nextPage === 0 ? result.items : [...prev, ...result.items]));
      setActions(result.actions);
      setPage(nextPage);
      setHasMore(result.hasMore);
    } catch (e) {
      fail(e);
    }
  }, [token, fail]);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      setOverview(await api.adminOverview(token));
    } catch (e) {
      fail(e);
    }
    void loadAudit(action, 0);
  }, [token, fail, loadAudit, action]);

  useEffect(() => { void refresh(); }, [refresh]);

  return (
    <>
      <section className="card wide">
        <div className="row">
          <h2>System overview</h2>
          <button className="link" onClick={() => void refresh()}>Refresh</button>
        </div>
        {error && <p className="error">{error}</p>}
        {overview && (
          <div className="stats">
            <Stat label="Users" value={overview.users} />
            <Stat label="Customer accounts" value={overview.customerAccounts} />
            <Stat label="Frozen accounts" value={overview.frozenAccounts} />
            <Stat label="Journal entries" value={overview.journalEntries} />
            <Stat label="Money in customer accounts" value={formatPence(overview.customerMoneyMinor)} />
            <Stat label="Flagged for review" value={overview.flaggedForReview} />
            <Stat label="Awaiting review" value={overview.pendingReviews} />
            <Stat label="Blocked transfers" value={overview.blockedTransfers} />
            <Stat label="Ledger" value={overview.ledgerBalanced ? 'Balanced' : 'UNBALANCED'}
                  tone={overview.ledgerBalanced ? 'ok' : 'error'} />
          </div>
        )}
      </section>

      <section className="card wide">
        <div className="row">
          <h2>Audit log</h2>
          <label className="filter">
            Action
            <select value={action} onChange={e => setAction(e.target.value)}>
              <option value="">All</option>
              {actions.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
          </label>
        </div>
        <p className="muted">Append-only. Every row was written in the same transaction as the change it describes.</p>
        {rows.length === 0 ? <p className="muted">No entries.</p> : (
          <table>
            <thead>
              <tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td className="nowrap">{new Date(r.createdAt).toLocaleString('en-GB')}</td>
                  <td>{r.actorEmail ?? <span className="muted">system</span>}</td>
                  <td><span className="reason">{r.action}</span></td>
                  <td className="nowrap">
                    {r.entityType}{r.entityId && <> <code className="id">{r.entityId.slice(0, 8)}</code></>}
                  </td>
                  <td className="details">{r.details ?? <span className="muted">-</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {hasMore && <button className="link" onClick={() => loadAudit(action, page + 1)}>Load older entries</button>}
      </section>
    </>
  );
}

function Stat({ label, value, tone }: { label: string; value: number | string; tone?: 'ok' | 'error' }) {
  return (
    <div className="stat">
      <span className="muted">{label}</span>
      <strong className={tone}>{typeof value === 'number' ? value.toLocaleString('en-GB') : value}</strong>
    </div>
  );
}
