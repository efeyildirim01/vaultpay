import { useCallback, useEffect, useState } from 'react';
import { ApiError, PendingReview, ReviewOutcome, api, describeError } from './api';
import { formatPence } from './money';

type Props = { token: string; onExpired: () => void };

export function ReviewPage({ token, onExpired }: Props) {
  const [items, setItems] = useState<PendingReview[]>([]);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const reload = useCallback(async () => {
    try {
      setItems(await api.pendingReviews(token));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onExpired();
      else setMessage({ ok: false, text: describeError(e) });
    }
  }, [token, onExpired]);

  useEffect(() => { void reload(); }, [reload]);

  async function decide(item: PendingReview, outcome: ReviewOutcome) {
    try {
      const result = await api.review(token, item.assessmentId, outcome, notes[item.assessmentId] ?? '');
      setMessage({ ok: true, text: result.accountFrozen ? 'Confirmed as fraud: the source account is now frozen.'
                                                        : 'Cleared.' });
    } catch (e) {
      // 409: another reviewer decided first. 422: e.g. your own transfer (four-eyes rule).
      setMessage({ ok: false, text: describeError(e) });
    }
    void reload();
  }

  return (
    <section className="card wide">
      <h2>Fraud review queue</h2>
      <p className="muted">Transfers scored REVIEW went through. Clear them, or confirm fraud to freeze the sending account.</p>
      {message && <p className={message.ok ? 'ok' : 'error'}>{message.text}</p>}
      {items.length === 0 ? <p className="muted">Nothing to review.</p> : (
        <table>
          <thead>
            <tr><th>Flagged</th><th className="num">Score</th><th>Reasons</th><th className="num">Amount</th>
                <th>From → To</th><th>Note</th><th /></tr>
          </thead>
          <tbody>
            {items.map(item => (
              <tr key={item.assessmentId}>
                <td className="nowrap">{new Date(item.flaggedAt).toLocaleString('en-GB')}</td>
                <td className="num">{item.score}</td>
                <td>{item.reasons.split(',').map(r => <span key={r} className="reason">{r}</span>)}</td>
                <td className="num">{formatPence(item.amountMinor)}</td>
                <td className="nowrap"><code className="id">{item.fromAccountId.slice(0, 8)}</code> → <code className="id">{item.toAccountId.slice(0, 8)}</code></td>
                <td><input value={notes[item.assessmentId] ?? ''} maxLength={500}
                           onChange={e => setNotes({ ...notes, [item.assessmentId]: e.target.value })} /></td>
                <td className="actions">
                  <button onClick={() => decide(item, 'CLEARED')}>Clear</button>
                  <button className="danger" onClick={() => decide(item, 'CONFIRMED_FRAUD')}>Confirm fraud</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
