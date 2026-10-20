import { useEffect, useState } from 'react'
import { ApiError, getDemoAccess, login, register } from '../api'
import type { CourseRole, DemoAccess, Session } from '../api'
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
  const [preferredRole, setPreferredRole] = useState<CourseRole>('STUDENT')
  const [includeExamples, setIncludeExamples] = useState(true)
  const [demoAccess, setDemoAccess] = useState<DemoAccess | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const registering = mode === 'register'

  useEffect(() => {
    const controller = new AbortController()
    getDemoAccess(controller.signal).then((data) => {
      if (!controller.signal.aborted) setDemoAccess(data)
    }).catch(() => { /* Test accounts are optional; normal login still works. */ })
    return () => controller.abort()
  }, [])

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
        if (registering && (Array.from(password).length < 8 || !/[\p{P}\p{S}]/u.test(password))) {
          setFieldErrors({ password: 'Bruk minst 8 tegn og minst ett spesialtegn, for eksempel !, ? eller #.' })
          return
        }
        setBusy(true)
        try {
          if (registering) {
            const user = await register({ name, email, password, preferredRole, includeExamples: Boolean(demoAccess?.enabled) && includeExamples })
            setEmail(user.email)
            setName('')
            setPassword('')
            setConfirmation('')
            setMode('login')
            setNotice(includeExamples && demoAccess?.enabled
              ? 'Kontoen din og eksempelfagene er klare. Logg inn for å fortsette.'
              : 'Kontoen din er opprettet. Logg inn for å fortsette.')
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
        {registering && <fieldset className="registration-role" disabled={busy}>
          <legend>Jeg vil starte som</legend>
          <div className="registration-role-options">
            <label className={preferredRole === 'STUDENT' ? 'selected' : ''}><input type="radio" name="preferredRole" value="STUDENT"
              checked={preferredRole === 'STUDENT'} onChange={() => setPreferredRole('STUDENT')} /><span>Student</span></label>
            <label className={preferredRole === 'TEACHER' ? 'selected' : ''}><input type="radio" name="preferredRole" value="TEACHER"
              checked={preferredRole === 'TEACHER'} onChange={() => setPreferredRole('TEACHER')} /><span>Lærer</span></label>
          </div>
          <p className="field-hint">Du kan ha ulike roller i ulike emner.</p>
        </fieldset>}
        {registering && demoAccess?.enabled && <div className="registration-examples">
          <label><input type="checkbox" checked={includeExamples} onChange={(event) => setIncludeExamples(event.target.checked)} disabled={busy} />
            <span>Legg til eksempelfag</span></label>
          <p>DAT250 og DAT100 med moduler, sider og quizer. Fjern haken for å starte med et tomt læringsrom.</p>
          {includeExamples && <p>{preferredRole === 'TEACHER' ? 'Du får egne kopier som du kan redigere.' : 'Du blir student i eksempellærerens fag.'}</p>}
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
            minLength={registering ? 8 : undefined}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'auth-password-error' : registering ? 'auth-password-hint' : undefined} />
          {registering && <p className="field-hint" id="auth-password-hint">Minst 8 tegn og ett spesialtegn, for eksempel !, ? eller #.</p>}
          {fieldErrors.password && <p className="field-error" id="auth-password-error">{fieldErrors.password}</p>}
        </div>
        {registering && <div className="form-field">
          <label htmlFor="auth-confirmation">Gjenta passord</label>
          <input id="auth-confirmation" name="confirmation" type="password" autoComplete="new-password"
            value={confirmation} onChange={(event) => setConfirmation(event.target.value)}
            required disabled={busy} aria-invalid={Boolean(fieldErrors.confirmation)}
            aria-describedby={fieldErrors.confirmation ? 'auth-confirmation-error' : undefined} />
          {fieldErrors.confirmation && <p className="field-error" id="auth-confirmation-error">{fieldErrors.confirmation}</p>}
        </div>}
        {(error || externalError) && <p className="api-feedback error" role="alert">{error || externalError}</p>}
        <button className="primary-button" type="submit" disabled={busy}>
          {busy ? registering ? 'Oppretter konto …' : 'Logger inn …' : registering ? 'Opprett konto' : 'Logg inn'}
          <Icon name="arrow" />
        </button>
      </form>
      {!registering && demoAccess?.enabled && <details className="demo-account-panel">
        <summary>Prøv ferdige testkontoer</summary>
        <p>Passord for begge: <code>{demoAccess.password}</code></p>
        {([{ role: 'Lærer', email: demoAccess.teacherEmail }, { role: 'Student', email: demoAccess.studentEmail }]).map((account) => <button
          key={account.role} className="demo-account-choice" disabled={busy} onClick={() => {
            setEmail(account.email ?? ''); setPassword(demoAccess.password ?? ''); setError(''); setFieldErrors({}); onClearError()
            setNotice('Testkontoen er fylt ut. Trykk Logg inn.')
          }}><span><strong>{account.role}</strong><small>{account.email}</small></span><Icon name="arrow" /></button>)}
      </details>}
      <div className="login-demo-link"><span>Bare nysgjerrig?</span>
        <button className="text-button" disabled={busy} onClick={onOpenDemo}>Utforsk frontend-demoen <Icon name="arrow" /></button>
      </div>
    </section>
  )
}
