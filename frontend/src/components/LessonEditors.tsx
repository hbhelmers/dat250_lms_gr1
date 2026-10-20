import { useState } from 'react'
import Markdown from 'react-markdown'
import { ApiError } from '../api'
import type { LearningModule, LearningItem, PageInput } from '../api'
import { localDateTime } from '../dateTime'

export function LessonBody({ body }: { body: string }) {
  return <div className="lesson-markdown"><Markdown skipHtml>{body}</Markdown></div>
}

export function ModuleEditor({ module, onSave, onCancel, onSessionExpired }: {
  module?: LearningModule
  onSave: (title: string) => Promise<void>
  onCancel: () => void
  onSessionExpired: () => void
}) {
  const [title, setTitle] = useState(module?.title ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  return <form className="lesson-editor" onSubmit={async (event) => {
    event.preventDefault()
    if (saving) return
    setSaving(true); setError('')
    try { await onSave(title) }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
      setError(failure instanceof ApiError && failure.fieldErrors.title ? failure.fieldErrors.title
        : failure instanceof Error ? failure.message : 'Kunne ikke lagre modulen.')
    } finally { setSaving(false) }
  }}>
    <h3>{module ? 'Endre modultittel' : 'Ny modul'}</h3>
    <div className="form-field"><label htmlFor="module-title">Modultittel</label>
      <input id="module-title" value={title} onChange={(event) => setTitle(event.target.value)}
        required maxLength={200} autoFocus disabled={saving} placeholder="F.eks. Uke 1 – Introduksjon" />
    </div>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    <div className="lesson-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onCancel}>Avbryt</button>
      <button className="primary-button" disabled={saving}>{saving ? 'Lagrer …' : 'Lagre modul'}</button></div>
  </form>
}

export function PageEditor({ page, onSave, onCancel, onSessionExpired }: {
  page?: LearningItem
  onSave: (input: PageInput) => Promise<void>
  onCancel: () => void
  onSessionExpired: () => void
}) {
  const [title, setTitle] = useState(page?.title ?? '')
  const [body, setBody] = useState(page?.body ?? '')
  const [published, setPublished] = useState(page?.published ?? false)
  const [preview, setPreview] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  return <form className="lesson-editor" onSubmit={async (event) => {
    event.preventDefault()
    if (saving) return
    setSaving(true); setError(''); setFieldErrors({})
    try {
      const fields = new FormData(event.currentTarget)
      const visibleFrom = String(fields.get('visibleFrom') ?? '')
      const visibleUntil = String(fields.get('visibleUntil') ?? '')
      await onSave({ title, body, published,
        visibleFrom: visibleFrom ? new Date(visibleFrom).toISOString() : null,
        visibleUntil: visibleUntil ? new Date(visibleUntil).toISOString() : null })
    } catch (failure) {
      if (failure instanceof ApiError) {
        if (failure.status === 401) { onSessionExpired(); return }
        setFieldErrors(failure.fieldErrors)
      }
      setError(failure instanceof Error ? failure.message : 'Kunne ikke lagre siden.')
    } finally { setSaving(false) }
  }}>
    <h3>{page ? 'Rediger side' : 'Ny undervisningsside'}</h3>
    <div className="form-field"><label htmlFor="page-title">Sidetittel</label>
      <input id="page-title" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={255}
        disabled={saving} autoFocus aria-invalid={Boolean(fieldErrors.title)} aria-describedby={fieldErrors.title ? 'page-title-error' : undefined} />
      {fieldErrors.title && <p className="field-error" id="page-title-error">{fieldErrors.title}</p>}
    </div>
    <div className="form-field"><label htmlFor="page-body">Sideinnhold</label>
      <textarea id="page-body" value={body} onChange={(event) => setBody(event.target.value)} required maxLength={100_000}
        disabled={saving} rows={12} aria-invalid={Boolean(fieldErrors.body)} aria-describedby={fieldErrors.body ? 'page-body-error' : 'page-body-hint'} />
      <p className="field-hint" id="page-body-hint">Bruk Markdown: ## overskrift, **fet tekst**, - punktliste og ![bildetekst](bildeadresse).</p>
      {fieldErrors.body && <p className="field-error" id="page-body-error">{fieldErrors.body}</p>}
    </div>
    <button type="button" className="text-button" aria-expanded={preview} onClick={() => setPreview(!preview)}>
      {preview ? 'Skjul forhåndsvisning' : 'Forhåndsvis siden'}
    </button>
    {preview && <div className="lesson-preview"><LessonBody body={body} /></div>}
    <label className="publish-option"><input type="checkbox" checked={published} onChange={(event) => setPublished(event.target.checked)} disabled={saving} />Publisert</label>
    <p className="field-hint">Utkast er bare synlige for lærere. Publiserte sider følger tidsrommet nedenfor.</p>
    <div className="visibility-fields">
      <div className="form-field"><label htmlFor="visible-from">Synlig fra (lokal tid)</label>
        <input id="visible-from" name="visibleFrom" type="datetime-local" defaultValue={localDateTime(page?.visibleFrom)} disabled={saving} />
      </div>
      <div className="form-field"><label htmlFor="visible-until">Synlig til (lokal tid)</label>
        <input id="visible-until" name="visibleUntil" type="datetime-local" defaultValue={localDateTime(page?.visibleUntil)} disabled={saving} />
      </div>
    </div>
    <p className="field-hint">Tomme tidsfelt gir ingen tidsbegrensning.</p>
    {fieldErrors.visibilityWindowValid && <p className="field-error">{fieldErrors.visibilityWindowValid}</p>}
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    <div className="lesson-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onCancel}>Avbryt</button>
      <button className="primary-button" disabled={saving}>{saving ? 'Lagrer …' : 'Lagre side'}</button></div>
  </form>
}
