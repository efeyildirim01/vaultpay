import { FormEvent, useCallback, useEffect, useState } from 'react';
import { AccountView, ApiError, api, describeError } from './api';
import { HistoryPanel } from './HistoryPanel';
import { useIdempotencyKey } from './idempotency';
import { formatPence, parsePounds } from './money';

type Props = { token: string; onExpired: () => void };

export function AccountsPage({ token, onExpired }: Props) {
  const [accounts, setAccounts] = useState<AccountView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setAccounts(await api.accounts(token));
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onExpired();
      else setError(describeError(e));
    }
  }, [token, onExpired]);

  useEffect(() => { void reload(); }, [reload]);

  return (
    <div className="grid">
      <section className="card wide">
        <h2>Your accounts</h2>
        {error && <p className="error">{error}</p>}
        {accounts.length === 0 ? <p className="muted">No accounts yet. Open one to get started.</p> : (
          <table>
            <thead><tr><th>Name</th><th>Account id</th><th>Status</th><th className="num">Balance</th><th /></tr></thead>
            <tbody>
              {accounts.map(a => (
                <tr key={a.id}>
                  <td>{a.name}</td>
                  <td><code className="id" title="Share this id to receive transfers">{a.id}</code></td>
                  <td><span className={`status ${a.status.toLowerCase()}`}>{a.status}</span></td>
                  <td className="num">{formatPence(a.balanceMinor)}</td>
                  <td>
                    <button className="link" onClick={() => setHistoryId(historyId === a.id ? null : a.id)}>
                      {historyId === a.id ? 'Hide history' : 'History'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <OpenAccountForm token={token} onDone={reload} />
      </section>
      {historyId && (
        <HistoryPanel token={token} account={accounts.find(a => a.id === historyId)} ownAccounts={accounts}
                      refreshKey={accounts}
                      onExpired={onExpired} />
      )}
      <DepositForm token={token} accounts={accounts} onDone={reload} />
      <TransferForm token={token} accounts={accounts} onDone={reload} />
    </div>
  );
}

function OpenAccountForm({ token, onDone }: { token: string; onDone: () => void }) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await api.openAccount(token, name);
      setName('');
      setError(null);
      onDone();
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <form onSubmit={submit} className="inline">
      <input placeholder="New account name, e.g. Savings" value={name} onChange={e => setName(e.target.value)} required />
      <button>Open GBP account</button>
      {error && <span className="error">{error}</span>}
    </form>
  );
}

type FormProps = { token: string; accounts: AccountView[]; onDone: () => void };

function DepositForm({ token, accounts, onDone }: FormProps) {
  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [round, setRound] = useState(0);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const key = useIdempotencyKey(round, accountId, amount);
  const target = accountId || accounts[0]?.id || '';

  async function submit(e: FormEvent) {
    e.preventDefault();
    const pence = parsePounds(amount);
    if (pence === null) return setMessage({ ok: false, text: 'Enter an amount like 25 or 25.50' });
    try {
      await api.deposit(token, key, target, pence);
      setMessage({ ok: true, text: `Deposited ${formatPence(pence)}` });
      setAmount('');
      setRound(r => r + 1);
      onDone();
    } catch (err) {
      setMessage({ ok: false, text: describeError(err) });
    }
  }

  return (
    <section className="card">
      <h2>Simulated deposit</h2>
      <p className="muted">Money arrives from an internal "external funding" account. Nothing real moves.</p>
      <form onSubmit={submit} className="form">
        <label>Into<AccountSelect accounts={accounts} value={target} onChange={setAccountId} /></label>
        <label>Amount (£)<input inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} required /></label>
        {message && <p className={message.ok ? 'ok' : 'error'}>{message.text}</p>}
        <button className="primary" disabled={!target}>Deposit</button>
      </form>
    </section>
  );
}

function TransferForm({ token, accounts, onDone }: FormProps) {
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');
  const [amount, setAmount] = useState('');
  const [reference, setReference] = useState('');
  const [round, setRound] = useState(0);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const source = fromId || accounts[0]?.id || '';
  const key = useIdempotencyKey(round, source, toId, amount, reference);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const pence = parsePounds(amount);
    if (pence === null) return setMessage({ ok: false, text: 'Enter an amount like 25 or 25.50' });
    try {
      const result = await api.transfer(token, key, source, toId.trim(), pence, reference);
      setMessage({ ok: true, text: result.replayed ? 'Already sent earlier: nothing was sent twice.'
                                                   : `Sent ${formatPence(pence)}` });
      setAmount('');
      setReference('');
      setRound(r => r + 1);
      onDone();
    } catch (err) {
      setMessage({ ok: false, text: describeError(err) });
    }
  }

  return (
    <section className="card">
      <h2>Transfer</h2>
      <form onSubmit={submit} className="form">
        <label>From<AccountSelect accounts={accounts} value={source} onChange={setFromId} /></label>
        <label>
          To account id
          <input list="own-accounts" value={toId} onChange={e => setToId(e.target.value)}
                 placeholder="Paste an account id" required />
          <datalist id="own-accounts">
            {accounts.filter(a => a.id !== source).map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </datalist>
        </label>
        <label>Amount (£)<input inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} required /></label>
        <label>Reference<input value={reference} maxLength={140} onChange={e => setReference(e.target.value)} /></label>
        {message && <p className={message.ok ? 'ok' : 'error'}>{message.text}</p>}
        <button className="primary" disabled={!source}>Send</button>
      </form>
    </section>
  );
}

function AccountSelect({ accounts, value, onChange }:
                       { accounts: AccountView[]; value: string; onChange: (id: string) => void }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}>
      {accounts.map(a => (
        <option key={a.id} value={a.id}>{a.name} ({formatPence(a.balanceMinor)})</option>
      ))}
    </select>
  );
}
