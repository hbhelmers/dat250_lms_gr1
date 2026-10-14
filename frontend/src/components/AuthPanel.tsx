import { useState } from 'react'
import { ApiError, login, register } from '../api'
import type { Session } from '../api'
import { Icon } from './Icon'

export function AuthPanel({ onLoggedIn, onOpenDemo, error: externalError, onClearError }: {
  onLoggedIn: (session: Session) => Promise<void>
  onOpenDemo: () => void
  error: string
  onClearError: () => void
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const registering = mode === 'register'

  function switchMode(next: typeof mode) {
    setMode(next)
    setPassword('')
    setConfirmation('')
    setError('')
    setNotice('')
    setFieldErrors({})
    onClearError()
  }

  return (
    <section className="login-panel" aria-labelledby="login-heading">
      <div className="auth-mode-switch" aria-label="Velg innlogging eller registrering">
        <button type="button" aria-pressed={!registering} disabled={busy}
          onClick={() => switchMode('login')}>Logg inn</button>
        <button type="button" aria-pressed={registering} disabled={busy}
          onClick={() => switchMode('register')}>Opprett konto</button>
      </div>
      <span className="login-symbol"><Icon name="book" /></span>
      <h2 id="login-heading">{registering ? 'Ditt læringsrom venter.' : 'Velkommen tilbake.'}</h2>
      <p>{registering ? 'Opprett en konto og kom i gang.' : 'Logg inn med e-postadressen din.'}</p>
      {notice && <p className="api-feedback success" role="status"><Icon name="check" />{notice}</p>}
      <form onSubmit={async (event) => {
        event.preventDefault()
        if (busy) return
        setError('')
        setNotice('')
        setFieldErrors({})
        onClearError()
        if (registering && password !== confirmation) {
          setFieldErrors({ confirmation: 'Passordene må være like.' })
          return
        }
        setBusy(true)
        try {
          if (registering) {
            const user = await register({ name, email, password })
            setEmail(user.email)
            setName('')
            setPassword('')
            setConfirmation('')
            setMode('login')
            setNotice('Kontoen din er opprettet. Logg inn for å fortsette.')
          } else {
            const current = await login(email, password)
            setPassword('')
            await onLoggedIn(current)
          }
        } catch (failure) {
          if (failure instanceof ApiError) setFieldErrors(failure.fieldErrors)
          setError(failure instanceof ApiError && Object.keys(failure.fieldErrors).length > 0
            ? 'Kontroller feltene og prøv igjen.'
            : failure instanceof ApiError && failure.status === 401
              ? 'Feil e-post eller passord. Prøv igjen.'
              : failure instanceof Error ? failure.message : 'Kunne ikke fullføre. Prøv igjen.')
        } finally {
          setBusy(false)
        }
      }}>
        {registering && <div className="form-field">
          <label htmlFor="auth-name">Navn</label>
          <input id="auth-name" name="name" autoComplete="name" value={name}
            onChange={(event) => setName(event.target.value)} required maxLength={120} disabled={busy}
            aria-invalid={Boolean(fieldErrors.name)} aria-describedby={fieldErrors.name ? 'auth-name-error' : undefined} />
          {fieldErrors.name && <p className="field-error" id="auth-name-error">{fieldErrors.name}</p>}
        </div>}
        <div className="form-field">
          <label htmlFor="auth-email">E-postadresse</label>
          <input id="auth-email" name="email" type="email" autoComplete="username" value={email}
            onChange={(event) => setEmail(event.target.value)} required maxLength={254} disabled={busy}
            autoCapitalize="none" spellCheck={false} aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'auth-email-error' : undefined} />
          {fieldErrors.email && <p className="field-error" id="auth-email-error">{fieldErrors.email}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="auth-password">Passord</label>
          <input id="auth-password" name="password" type="password" value={password}
            autoComplete={registering ? 'new-password' : 'current-password'}
            onChange={(event) => setPassword(event.target.value)} required disabled={busy}
            minLength={registering ? 15 : undefined} maxLength={registering ? 128 : undefined}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'auth-password-error' : registering ? 'auth-password-hint' : undefined} />
          {registering && <p className="field-hint" id="auth-password-hint">Bruk 15–128 tegn, gjerne en lang setning.</p>}
          {fieldErrors.password && <p className="field-error" id="auth-password-error">{fieldErrors.password}</p>}
        </div>
        {registering && <div className="form-field">
          <label htmlFor="auth-confirmation">Gjenta passord</label>
          <input id="auth-confirmation" name="confirmation" type="password" autoComplete="new-password"
            value={confirmation} onChange={(event) => setConfirmation(event.target.value)}
            required maxLength={128} disabled={busy} aria-invalid={Boolean(fieldErrors.confirmation)}
            aria-describedby={fieldErrors.confirmation ? 'auth-confirmation-error' : undefined} />
          {fieldErrors.confirmation && <p className="field-error" id="auth-confirmation-error">{fieldErrors.confirmation}</p>}
        </div>}
        {(error || externalError) && <p className="api-feedback error" role="alert">{error || externalError}</p>}
        <button className="primary-button" type="submit" disabled={busy}>
          {busy ? registering ? 'Oppretter konto …' : 'Logger inn …' : registering ? 'Opprett konto' : 'Logg inn'}
          <Icon name="arrow" />
        </button>
      </form>
      <div className="login-demo-link"><span>Bare nysgjerrig?</span>
        <button className="text-button" disabled={busy} onClick={onOpenDemo}>Utforsk frontend-demoen <Icon name="arrow" /></button>
      </div>
    </section>
  )
}
