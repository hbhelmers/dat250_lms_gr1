import { useEffect, useRef, useState } from 'react'
import { addStudent, ApiError, getCourse, getCourseMembers } from '../api'
import type { CourseMember, StoredCourse } from '../api'
import { Icon } from './Icon'
import { CourseContent } from './CourseContent'

export function CourseDetails({ course, onClose, onEdit, onSessionExpired }: {
  course: StoredCourse
  onClose: () => void
  onEdit: (course: StoredCourse) => void
  onSessionExpired: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [details, setDetails] = useState(course)
  const [members, setMembers] = useState<CourseMember[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [adding, setAdding] = useState(false)
  const [section, setSection] = useState<'content' | 'about' | 'members'>('content')
  const [contentWorking, setContentWorking] = useState(false)
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const teacher = details.role === 'TEACHER'
  const blocked = adding || contentWorking

  useEffect(() => {
    if (!dialogRef.current?.open) dialogRef.current?.showModal()
    const controller = new AbortController()
    async function load() {
      try {
        const current = await getCourse(course.id, controller.signal)
        if (controller.signal.aborted) return
        setDetails(current)
        if (current.role === 'TEACHER') {
          const roster = await getCourseMembers(course.id, controller.signal)
          if (!controller.signal.aborted) setMembers(roster)
        }
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
        setLoadFailed(true)
        setError(failure instanceof Error ? failure.message : 'Kunne ikke laste emnet.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    void load()
    return () => controller.abort()
  }, [course.id, onSessionExpired])

  return (
    <dialog className="create-course-dialog course-details-dialog" ref={dialogRef}
      onClose={onClose} onCancel={(event) => { if (blocked) event.preventDefault() }}
      aria-labelledby="course-details-heading">
      <div className="dialog-header">
        <div><span className="eyebrow">{details.semester}</span><h2 id="course-details-heading">{details.title}</h2></div>
        <button className="icon-button" aria-label="Lukk emnet" disabled={blocked}
          onClick={() => dialogRef.current?.close()}><Icon name="close" /></button>
      </div>
      <div className="course-details-body">
        {loading ? <p className="details-loading" role="status">Henter emnet …</p> : <>
          {error && <p className="api-feedback error" role="alert">{error}</p>}
          {!loadFailed && <>
            <div className="course-access-heading">
              <nav className="course-sections" aria-label="Emnevisning">
                <button aria-pressed={section === 'content'} disabled={blocked} onClick={() => setSection('content')}>Innhold</button>
                <button aria-pressed={section === 'about'} disabled={blocked} onClick={() => setSection('about')}>Om emnet</button>
                {teacher && <button aria-pressed={section === 'members'} disabled={blocked} onClick={() => setSection('members')}>Deltakere</button>}
              </nav>
              <span className={`course-role ${teacher ? 'teacher' : 'student'}`}>{teacher ? 'Lærer' : 'Student'}</span>
            </div>
            {section === 'content' && <CourseContent courseId={details.id} teacher={teacher}
              onSessionExpired={onSessionExpired} onWorkingChange={setContentWorking} />}
            {section === 'about' && <>
              <p className="stored-description">{details.description || 'Ingen beskrivelse lagt til.'}</p>
              {teacher && <button className="secondary-button" onClick={() => onEdit(details)}>Rediger emne</button>}
              {!teacher && <p className="student-access-note">Du deltar som student i dette emnet.</p>}
            </>}
            {teacher && section === 'members' && <>
              <section className="course-members" aria-labelledby="course-members-heading">
                <h3 id="course-members-heading">Deltakere <span>{members.length}</span></h3>
                <ul className="member-list">
                  {members.map((member) => <li key={member.userId}>
                    <div><strong>{member.name}</strong><span>{member.email}</span></div>
                    <span className={`course-role ${member.role === 'TEACHER' ? 'teacher' : 'student'}`}>
                      {member.role === 'TEACHER' ? 'Lærer' : 'Student'}
                    </span>
                  </li>)}
                </ul>
                <form className="add-student-form" onSubmit={async (event) => {
                  event.preventDefault()
                  if (adding) return
                  setAdding(true)
                  setError('')
                  setNotice('')
                  setFieldErrors({})
                  try {
                    const member = await addStudent(details.id, email)
                    setMembers((current) => [...current, member])
                    setEmail('')
                    setNotice(`${member.name} er lagt til som student.`)
                  } catch (failure) {
                    if (failure instanceof ApiError) {
                      if (failure.status === 401) { onSessionExpired(); return }
                      setFieldErrors(failure.fieldErrors)
                    }
                    setError(failure instanceof Error ? failure.message : 'Kunne ikke legge til studenten.')
                  } finally { setAdding(false) }
                }}>
                  <div className="form-field">
                    <label htmlFor="student-email">Legg til student med e-postadresse</label>
                    <input id="student-email" name="email" type="email" value={email}
                      onChange={(event) => setEmail(event.target.value)} placeholder="student@eksempel.no"
                      required maxLength={254} disabled={adding} autoCapitalize="none" spellCheck={false}
                      aria-invalid={Boolean(fieldErrors.email)}
                      aria-describedby={fieldErrors.email ? 'student-email-error' : 'student-email-hint'} />
                    <p className="field-hint" id="student-email-hint">Studenten må ha opprettet en konto først.</p>
                    {fieldErrors.email && <p className="field-error" id="student-email-error">{fieldErrors.email}</p>}
                  </div>
                  <button className="primary-button" type="submit" disabled={adding}>
                    {adding ? 'Legger til …' : 'Legg til student'}<Icon name="arrow" />
                  </button>
                  {notice && <p className="api-feedback success" role="status"><Icon name="check" />{notice}</p>}
                </form>
              </section>
            </>}
          </>}
        </>}
      </div>
    </dialog>
  )
}
