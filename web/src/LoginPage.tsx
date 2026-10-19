import { FormEvent, useState } from 'react';
import { api, describeError } from './api';

export function LoginPage({ onSignedIn }: { onSignedIn: (token: string) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'register') {
        await api.register(email, password, fullName);
      }
      const { accessToken } = await api.login(email, password);
      onSignedIn(accessToken);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card narrow">
      <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
      <form onSubmit={submit} className="form">
        <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>
          Password{mode === 'register' && <span className="muted"> (at least 12 characters)</span>}
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                 minLength={mode === 'register' ? 12 : undefined} required />
        </label>
        {mode === 'register' && (
          <label>Full name<input value={fullName} onChange={e => setFullName(e.target.value)} required /></label>
        )}
        {error && <p className="error">{error}</p>}
        <button className="primary" disabled={busy}>{mode === 'login' ? 'Sign in' : 'Register'}</button>
      </form>
      <button className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}>
        {mode === 'login' ? 'New here? Create an account' : 'Already registered? Sign in'}
      </button>
    </section>
  );
}
