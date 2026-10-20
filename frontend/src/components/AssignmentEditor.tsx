import { useState } from 'react'
import { ApiError } from '../api'
import type { Assignment, AssignmentInput } from '../api'
import { localDateTime } from '../dateTime'

export function AssignmentEditor({ assignment, onSave, onCancel, onSessionExpired }: {
  assignment?: Assignment; onSave: (input: AssignmentInput) => Promise<void>; onCancel: () => void; onSessionExpired: () => void
}) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  return <form className="lesson-editor" onSubmit={async (event) => {
    event.preventDefault()
    if (saving) return
    const fields = new FormData(event.currentTarget)
    const value = (name: string) => String(fields.get(name) ?? '')
    const date = (name: string) => value(name) ? new Date(value(name)).toISOString() : null
    setSaving(true); setError('')
    try {
      await onSave({ title: value('title'), body: value('body'), published: fields.has('published'),
        visibleFrom: date('visibleFrom'), visibleUntil: date('visibleUntil'), deadline: date('deadline'), acceptLate: fields.has('acceptLate') })
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
      setError(failure instanceof ApiError && Object.keys(failure.fieldErrors).length
        ? Object.values(failure.fieldErrors).join(' ') : failure instanceof Error ? failure.message : 'Kunne ikke lagre oppgaven.')
    } finally { setSaving(false) }
  }}>
    <h3>{assignment ? 'Rediger oppgave' : 'Ny innleveringsoppgave'}</h3>
    <div className="form-field"><label htmlFor="assignment-title">Oppgavetittel</label>
      <input id="assignment-title" name="title" defaultValue={assignment?.content.title ?? ''} required maxLength={255} disabled={saving} autoFocus /></div>
    <div className="form-field"><label htmlFor="assignment-body">Oppgavebeskrivelse</label>
      <textarea id="assignment-body" name="body" defaultValue={assignment?.content.body ?? ''} required maxLength={100_000} rows={8} disabled={saving} />
      <span className="field-hint">Du kan bruke Markdown. Studentene leverer én PDF eller DOCX på opptil 10 MB per versjon.</span></div>
    <div className="form-field"><label htmlFor="assignment-deadline">Innleveringsfrist (lokal tid)</label>
      <input id="assignment-deadline" name="deadline" type="datetime-local" defaultValue={localDateTime(assignment?.deadline)} disabled={saving} /></div>
    <label className="publish-option"><input type="checkbox" name="acceptLate" defaultChecked={assignment?.acceptLate ?? false} disabled={saving} />Tillat levering etter fristen</label>
    <p className="field-hint">Sene innleveringer merkes tydelig. Tidligere versjoner beholdes ved ny levering.</p>
    <label className="publish-option"><input type="checkbox" name="published" defaultChecked={assignment?.content.published ?? false} disabled={saving} />Publisert</label>
    <div className="visibility-fields">
      <div className="form-field"><label htmlFor="assignment-from">Synlig fra (lokal tid)</label>
        <input id="assignment-from" name="visibleFrom" type="datetime-local" defaultValue={localDateTime(assignment?.content.visibleFrom)} disabled={saving} /></div>
      <div className="form-field"><label htmlFor="assignment-until">Synlig til (lokal tid)</label>
        <input id="assignment-until" name="visibleUntil" type="datetime-local" defaultValue={localDateTime(assignment?.content.visibleUntil)} disabled={saving} /></div>
    </div>
    <p className="field-hint">Utkast er bare synlige for læreren. Tomme tidsfelt gir ingen tidsbegrensning.</p>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    <div className="lesson-actions"><button className="secondary-button" type="button" disabled={saving} onClick={onCancel}>Avbryt</button>
      <button className="primary-button" disabled={saving}>{saving ? 'Lagrer …' : 'Lagre oppgave'}</button></div>
  </form>
}
