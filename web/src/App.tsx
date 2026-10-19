import { useState } from 'react';
import { tokenClaims } from './api';
import { AccountsPage } from './AccountsPage';
import { LoginPage } from './LoginPage';
import { ReviewPage } from './ReviewPage';

const TOKEN_KEY = 'vaultpay.token';

// sessionStorage: the login survives a page refresh but not closing the tab. Any script on the page
// could read it (XSS), which an HttpOnly cookie would prevent; acceptable for a simulator, noted in the README.
function loadToken(): string | null {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (token && (tokenClaims(token).exp ?? 0) * 1000 > Date.now()) return token;
  } catch {
    // storage blocked or token unreadable: start logged out
  }
  return null;
}

export function App() {
  const [token, setToken] = useState<string | null>(loadToken);
  const [tab, setTab] = useState<'accounts' | 'review'>('accounts');

  const signIn = (t: string) => {
    try { sessionStorage.setItem(TOKEN_KEY, t); } catch { /* in-memory only */ }
    setToken(t);
  };
  const signOut = () => {
    try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* nothing stored */ }
    setToken(null);
    setTab('accounts');
  };

  if (!token) {
    return (
      <Shell>
        <LoginPage onSignedIn={signIn} />
      </Shell>
    );
  }

  const claims = tokenClaims(token);
  const isReviewer = (claims.scope ?? '').split(' ').includes('fraud:review');

  return (
    <Shell>
      <nav className="nav">
        <button className={tab === 'accounts' ? 'tab active' : 'tab'} onClick={() => setTab('accounts')}>
          Accounts
        </button>
        {isReviewer && (
          <button className={tab === 'review' ? 'tab active' : 'tab'} onClick={() => setTab('review')}>
            Fraud review
          </button>
        )}
        <span className="spacer" />
        <span className="muted">{claims.email}</span>
        <button className="link" onClick={signOut}>Sign out</button>
      </nav>
      {tab === 'accounts' ? <AccountsPage token={token} onExpired={signOut} />
                          : <ReviewPage token={token} onExpired={signOut} />}
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="shell">
      <header className="header">
        <h1>VaultPay</h1>
        <span className="badge">Simulator: no real money</span>
      </header>
      <main>{children}</main>
    </div>
  );
}
