import { useEffect, useRef, useState } from 'react'
import { ApiError, createCourse, updateCourse } from '../api'
import type { Institution, StoredCourse } from '../api'
import { Icon } from './Icon'

export function CreateCourseDialog({ course, onClose, onSaved, onSessionExpired }: {
  course?: StoredCourse
  onClose: () => void
  onSaved: (course: StoredCourse) => void
  onSessionExpired: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [title, setTitle] = useState(course?.title ?? '')
  const [description, setDescription] = useState(course?.description ?? '')
  const [institution, setInstitution] = useState<Institution>(course?.institution ?? 'HVL')
  const [courseCode, setCourseCode] = useState(course?.courseCode ?? '')
  const [selfEnrollment, setSelfEnrollment] = useState(course?.selfEnrollment ?? true)
  const [semester, setSemester] = useState(() => {
    if (course) return course.semester
    const today = new Date()
    return `${today.getMonth() >= 6 ? 'Høst' : 'Vår'} ${today.getFullYear()}`
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!dialogRef.current?.open) dialogRef.current?.showModal()
  }, [])

  return (
    <dialog ref={dialogRef} className="create-course-dialog" aria-labelledby="create-course-heading"
      onClose={onClose} onCancel={(event) => { if (saving) event.preventDefault() }}>
      <div className="dialog-header">
        <div><span className="eyebrow">PLASS TIL NYE IDEER</span><h2 id="create-course-heading">{course ? 'Rediger emne' : 'Opprett et emne'}</h2></div>
        <button type="button" className="icon-button" aria-label="Lukk skjemaet" disabled={saving}
          onClick={() => dialogRef.current?.close()}><Icon name="close" /></button>
      </div>
      <form className="course-form" onSubmit={async (event) => {
        event.preventDefault()
        if (saving) return
        setSaving(true)
        setError('')
        setFieldErrors({})
        try {
          const input = { title, description, semester, institution, courseCode, selfEnrollment }
          onSaved(course ? await updateCourse(course.id, input) : await createCourse(input))
        } catch (failure) {
          if (failure instanceof ApiError) {
            if (failure.status === 401) { onSessionExpired(); return }
            setFieldErrors(failure.fieldErrors)
          }
          setError(failure instanceof Error ? failure.message : 'Kunne ikke lagre emnet.')
        } finally {
          setSaving(false)
        }
      }}>
        <p className="form-intro">{course ? 'Oppdater emnets tittel, semester og beskrivelse.' : 'Gi emnet en tittel, velg semester og skriv hva det handler om. Du blir lærer i emnet.'}</p>
        <div className="course-catalog-fields">
          <div className="form-field"><label htmlFor="course-institution">Institusjon</label>
            <select id="course-institution" value={institution} onChange={(event) => setInstitution(event.target.value as Institution)} disabled={saving} required>
              <option value="HVL">HVL – Høgskulen på Vestlandet</option><option value="UIB">UiB – Universitetet i Bergen</option>
            </select></div>
          <div className="form-field"><label htmlFor="course-code">Emnekode (valgfritt)</label>
            <input id="course-code" placeholder="F.eks. DAT250 eller INF100" value={courseCode} maxLength={20} disabled={saving}
              onChange={(event) => setCourseCode(event.target.value.toUpperCase())} aria-invalid={Boolean(fieldErrors.courseCode)} />
            {fieldErrors.courseCode && <p className="field-error">{fieldErrors.courseCode}</p>}</div>
        </div>
        <div className="form-field">
          <label htmlFor="course-title">Tittel</label>
          <input id="course-title" name="title" placeholder="F.eks. DAT250 – Programvareteknologi"
            value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={255}
            disabled={saving} autoFocus aria-invalid={Boolean(fieldErrors.title)}
            aria-describedby={fieldErrors.title ? 'course-title-error' : undefined} />
          {fieldErrors.title && <p className="field-error" id="course-title-error">{fieldErrors.title}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="course-semester">Semester</label>
          <input id="course-semester" name="semester" placeholder="F.eks. Høst 2026"
            value={semester} onChange={(event) => setSemester(event.target.value)} required maxLength={50}
            disabled={saving} aria-invalid={Boolean(fieldErrors.semester)}
            aria-describedby={fieldErrors.semester ? 'course-semester-error' : undefined} />
          {fieldErrors.semester && <p className="field-error" id="course-semester-error">{fieldErrors.semester}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="course-description">Beskrivelse</label>
          <textarea id="course-description" name="description" rows={5}
            placeholder="Hva skal studentene lære i dette emnet?" value={description}
            onChange={(event) => setDescription(event.target.value)} required maxLength={10000}
            disabled={saving} aria-invalid={Boolean(fieldErrors.description)}
            aria-describedby={fieldErrors.description ? 'course-description-error' : undefined} />
          {fieldErrors.description && <p className="field-error" id="course-description-error">{fieldErrors.description}</p>}
        </div>
        <label className="publish-option"><input type="checkbox" checked={selfEnrollment} disabled={saving} onChange={(event) => setSelfEnrollment(event.target.checked)} />Åpent for påmelding</label>
        <p className="field-hint">Åpne emner vises under valgt institusjon i emnekatalogen. Studentene kan melde seg på selv.</p>
        {error && <p className="api-feedback error" role="alert">{error}</p>}
        <div className="form-actions">
          <button type="button" className="secondary-button" disabled={saving}
            onClick={() => dialogRef.current?.close()}>Avbryt</button>
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? 'Lagrer …' : course ? 'Lagre endringer' : 'Opprett emne'}<Icon name="arrow" />
          </button>
        </div>
      </form>
    </dialog>
  )
}
