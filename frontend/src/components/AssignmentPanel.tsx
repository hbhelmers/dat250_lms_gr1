import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, getAssignment, gradeSubmission, saveAssignment, submissionDownloadUrl, submitAssignment } from '../api'
import type { Assignment, AssignmentGrade, AssignmentSubmission, GradeInput, LearningItem } from '../api'
import { LessonBody } from './LessonEditors'
import { AssignmentEditor } from './AssignmentEditor'
import './assignments.css'

const grades: AssignmentGrade[] = ['A', 'B', 'C', 'D', 'E', 'F', 'PASSED', 'FAILED']
function gradeLabel(grade: AssignmentGrade) { return grade === 'PASSED' ? 'Godkjent' : grade === 'FAILED' ? 'Ikke godkjent' : grade }
function dateTime(value: string) { return new Date(value).toLocaleString('nb-NO', { dateStyle: 'medium', timeStyle: 'short' }) }

function AssessmentEditor({ submission, onSave, onCancel }: {
  submission: AssignmentSubmission; onSave: (input: GradeInput) => Promise<void>; onCancel: () => void
}) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  return <form className="lesson-editor assessment-editor" onSubmit={async (event) => {
    event.preventDefault()
    if (saving) return
    const fields = new FormData(event.currentTarget)
    setSaving(true); setError('')
    try { await onSave({ grade: String(fields.get('grade')) as AssignmentGrade, feedback: String(fields.get('feedback') ?? ''), published: fields.has('published') }) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Kunne ikke lagre vurderingen.') }
    finally { setSaving(false) }
  }}>
    <h4>Vurder {submission.studentName} · versjon {submission.version}</h4>
    <div className="form-field"><label htmlFor="assessment-grade">Karakter</label>
      <select id="assessment-grade" name="grade" defaultValue={submission.grade ?? ''} required disabled={saving}>
        <option value="" disabled>Velg karakter</option>{grades.map((grade) => <option key={grade} value={grade}>{gradeLabel(grade)}</option>)}
      </select></div>
    <div className="form-field"><label htmlFor="assessment-feedback">Tilbakemelding</label>
      <textarea id="assessment-feedback" name="feedback" rows={5} maxLength={10_000} defaultValue={submission.feedback ?? ''} disabled={saving} /></div>
    <label className="publish-option"><input type="checkbox" name="published" defaultChecked={submission.gradePublished} disabled={saving} />Publiser vurderingen til studenten</label>
    <p className="field-hint">Uten avhuking lagres vurderingen som utkast. Studenten får se karakter og tilbakemelding når den publiseres.</p>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    <div className="lesson-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onCancel}>Avbryt vurdering</button>
      <button className="primary-button" disabled={saving}>{saving ? 'Lagrer …' : 'Lagre vurdering'}</button></div>
  </form>
}

export function AssignmentPanel({ courseId, moduleId, assignmentId, teacher, onSessionExpired, onWorkingChange, onChanged }: {
  courseId: number; moduleId: number; assignmentId: number; teacher: boolean; onSessionExpired: () => void
  onWorkingChange: (working: boolean) => void; onChanged: (content: LearningItem) => void
}) {
  const [assignment, setAssignment] = useState<Assignment | null>(null)
  const [editing, setEditing] = useState(false)
  const [assessing, setAssessing] = useState<AssignmentSubmission | null>(null)
  const [busy, setBusy] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [now, setNow] = useState(() => Date.now())
  const submissionId = useRef<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const blocked = editing || assessing !== null || busy || file !== null
  useEffect(() => { onWorkingChange(blocked); return () => onWorkingChange(false) }, [blocked, onWorkingChange])
  const reportError = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
    setError(failure instanceof Error ? failure.message : 'Kunne ikke hente oppgaven.')
  }, [onSessionExpired])
  const load = useCallback((signal?: AbortSignal) => getAssignment(courseId, moduleId, assignmentId, signal), [courseId, moduleId, assignmentId])
  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then((data) => { if (!controller.signal.aborted) setAssignment(data) })
      .catch((failure: unknown) => { if (!controller.signal.aborted) reportError(failure) })
    return () => controller.abort()
  }, [load, reportError])
  useEffect(() => {
    if (!assignment?.deadline) return
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [assignment?.deadline])

  if (editing && assignment) return <AssignmentEditor assignment={assignment} onSessionExpired={onSessionExpired} onCancel={() => setEditing(false)}
    onSave={async (input) => {
      const saved = await saveAssignment(courseId, moduleId, input, assignmentId)
      setAssignment(saved); onChanged(saved.content); setEditing(false); setError(''); setNotice('Oppgaven er lagret.')
    }} />
  const late = assignment?.deadline ? new Date(assignment.deadline).getTime() <= now : false
  const closed = late && !assignment?.acceptLate
  const latestByStudent = new Map<number | null, number>()
  for (const submission of assignment?.submissions ?? []) latestByStudent.set(submission.studentId,
    Math.max(latestByStudent.get(submission.studentId) ?? 0, submission.version))

  return <section className="assignment-panel" aria-label="Innleveringsoppgave">
    <div className="quiz-panel-toolbar"><span className="eyebrow">INNLEVERINGSOPPGAVE</span><div className="lesson-actions">
      <button className="secondary-button" disabled={blocked} onClick={async () => {
        setBusy(true); setError(''); setNotice('')
        try { setAssignment(await load()) } catch (failure) { reportError(failure) } finally { setBusy(false) }
      }}>Oppdater oppgave</button>
      {teacher && assignment && <button className="secondary-button" disabled={blocked} onClick={() => { setEditing(true); setNotice('') }}>Rediger oppgave</button>}
    </div></div>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    {notice && <p className="api-feedback success" role="status">{notice}</p>}
    {!assignment ? !error && <p role="status">Henter oppgave …</p> : <>
      <h3 className="quiz-heading">{assignment.content.title}</h3>
      <div className="quiz-facts"><span>{assignment.groupWork ? `Gruppeinnlevering${assignment.groupName ? ` · ${assignment.groupName}` : ''}` : 'Individuell innlevering'}</span><span>PDF / DOCX / ZIP · maks 10 MB</span>
        <span>{assignment.deadline ? `Frist: ${dateTime(assignment.deadline)}` : 'Ingen innleveringsfrist'}</span></div>
      <LessonBody body={assignment.content.body} />
      {assignment.starterRepoUrl && <div className="assessment-feedback"><strong>Startkode</strong>
        <p><code>git clone {assignment.starterRepoUrl}</code></p></div>}
      {!teacher && assignment.groupWork && !assignment.groupName && <p className="api-feedback">Dette er en gruppeoppgave. Bli med i en gruppe under fanen «Grupper» før du leverer.</p>}
      {assignment.acceptLate && <p className="field-hint">Levering etter fristen er tillatt og merkes som sen.</p>}
      {!teacher && <section className="assignment-upload" aria-labelledby="upload-heading"><h4 id="upload-heading">{assignment.groupWork ? 'Gruppens innlevering' : 'Din innlevering'}</h4>
        {closed ? <><p className="api-feedback">Innleveringsfristen er passert. Du kan fortsatt lese og laste ned egne innleveringer.</p>
          {file && <button type="button" className="text-button" disabled={busy} onClick={() => { setFile(null); submissionId.current = null }}>Fjern valgt fil</button>}</>
          : assignment.submissions.length > 0 && !uploadOpen ? <><p className="field-hint">Oppgaven er levert. Du kan levere en ny versjon frem til fristen.</p>
            <button className="secondary-button" onClick={() => setUploadOpen(true)}>Lever ny versjon</button></> : <form onSubmit={async (event) => {
          event.preventDefault()
          if (busy || !file) return
          if (file.size > 10 * 1024 * 1024) { setError('Filen kan være maksimalt 10 MB.'); return }
          submissionId.current ??= crypto.randomUUID()
          setBusy(true); setError(''); setNotice('')
          try {
            const saved = await submitAssignment(courseId, moduleId, assignmentId, submissionId.current, file)
            setAssignment({ ...assignment, submissions: [saved, ...assignment.submissions.filter((entry) => entry.id !== saved.id)] })
            setFile(null); setUploadOpen(false); submissionId.current = null
            if (fileInput.current) fileInput.current.value = ''
            setNotice(`Versjon ${saved.version} er levert${saved.late ? ' etter fristen' : ''}.`)
          } catch (failure) { reportError(failure) } finally { setBusy(false) }
        }}>
          <div className="form-field"><label htmlFor="submission-file">Velg PDF, Word-dokument eller ZIP</label>
            <input ref={fileInput} id="submission-file" type="file" accept=".pdf,.docx,.zip" required disabled={busy} onChange={(event) => {
              setFile(event.target.files?.[0] ?? null); submissionId.current = null; setError(''); setNotice('')
            }} /></div>
          <p className="field-hint">{assignment.submissions.length ? 'En ny levering lagres som en ny versjon. Tidligere versjoner og vurderinger beholdes.' : 'Vurderingen vises her når læreren publiserer den.'}</p>
          <div className="lesson-actions"><button className="primary-button" disabled={busy || !file}>{busy ? 'Leverer …' : assignment.submissions.length ? 'Lever ny versjon' : 'Lever oppgave'}</button>
            {file && <button type="button" className="text-button" disabled={busy} onClick={() => {
              setFile(null); submissionId.current = null; if (fileInput.current) fileInput.current.value = ''
            }}>Fjern valgt fil</button>}
            {assignment.submissions.length > 0 && <button type="button" className="text-button" disabled={busy} onClick={() => {
              setFile(null); setUploadOpen(false); submissionId.current = null
            }}>Avbryt ny levering</button>}</div>
        </form>}
      </section>}
      <section className="assignment-history" aria-label={teacher ? 'Studentinnleveringer' : 'Dine innleveringer'}>
        <h4>{teacher ? 'Studentinnleveringer' : 'Dine innleveringer'} <span className="submission-count">{assignment.submissions.length}</span></h4>
        {assignment.submissions.length === 0 && <p className="field-hint">{teacher ? 'Ingen studenter har levert ennå.' : 'Du har ikke levert denne oppgaven ennå.'}</p>}
        {assignment.submissions.map((submission) => <article className="submission-card" key={submission.id}>
          <div className="submission-heading"><div><strong>{teacher ? submission.studentName : `Versjon ${submission.version}`}</strong>
            {teacher && <small>{submission.groupName ? `${submission.groupName} · ` : ''}{submission.studentEmail} · versjon {submission.version}{latestByStudent.get(submission.studentId) === submission.version ? ' · nyeste' : ' · tidligere versjon'}</small>}
            <small>{dateTime(submission.submittedAt)} · {(submission.fileSize / 1024).toLocaleString('nb-NO', { maximumFractionDigits: 1 })} kB</small></div>
            <span className={`assessment-badge ${submission.gradePublished ? 'published' : ''}`}>{submission.gradePublished ? `Karakter: ${gradeLabel(submission.grade!)}` : teacher && submission.grade ? `Utkast: ${gradeLabel(submission.grade)}` : 'Venter på vurdering'}</span></div>
          {submission.late && <p className="late-submission">Levert etter fristen</p>}
          <a className="submission-download" href={submissionDownloadUrl(courseId, moduleId, assignmentId, submission.id)} download>{submission.filename} ↓</a>
          {submission.feedback && <div className="assessment-feedback"><strong>Tilbakemelding{teacher && !submission.gradePublished ? ' (utkast)' : ''}</strong><p>{submission.feedback}</p></div>}
          {submission.gradedAt && <p className="field-hint">Vurdert {dateTime(submission.gradedAt)}</p>}
          {teacher && (assessing?.id === submission.id ? <AssessmentEditor submission={submission} onCancel={() => setAssessing(null)} onSave={async (input) => {
            try {
              const saved = await gradeSubmission(courseId, moduleId, assignmentId, submission.id, input)
              setAssignment({ ...assignment, submissions: assignment.submissions.map((entry) => entry.id === saved.id ? saved : entry) })
              setAssessing(null); setError(''); setNotice(input.published ? 'Vurderingen er publisert til studenten.' : 'Vurderingen er lagret som utkast.')
            } catch (failure) {
              if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
              throw failure
            }
          }} /> : <button className="secondary-button" disabled={blocked} onClick={() => { setAssessing(submission); setNotice('') }}>{submission.grade ? 'Endre vurdering' : 'Vurder innlevering'}</button>)}
        </article>)}
      </section>
    </>}
  </section>
}
