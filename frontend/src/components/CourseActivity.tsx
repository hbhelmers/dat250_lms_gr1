import { useCallback, useEffect, useState } from 'react'
import { ApiError, getCourseActivity } from '../api'
import type { CourseActivity as Activity } from '../api'
import { Icon } from './Icon'
import './activity.css'

const labels = { QUIZ_SUBMITTED: 'Quiz levert', ASSIGNMENT_SUBMITTED: 'Oppgave levert', GRADE_PUBLISHED: 'Vurdering publisert' }

export function CourseActivity({ courseId, teacher, onSessionExpired, onOpen }: {
  courseId: number; teacher: boolean; onSessionExpired: () => void; onOpen: (moduleId: number, itemId: number) => void
}) {
  const [activity, setActivity] = useState<Activity[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const reportError = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
    setError(failure instanceof Error ? failure.message : 'Kunne ikke hente aktiviteten.')
  }, [onSessionExpired])
  useEffect(() => {
    const controller = new AbortController()
    let fetching = false
    const poll = () => {
      if (fetching) return
      fetching = true
      getCourseActivity(courseId, controller.signal).then((data) => {
        if (!controller.signal.aborted) { setActivity(data); setError('') }
      }).catch((failure: unknown) => { if (!controller.signal.aborted) reportError(failure) })
        .finally(() => { fetching = false; if (!controller.signal.aborted) setLoading(false) })
    }
    poll()
    const interval = setInterval(poll, 15_000)
    return () => { controller.abort(); clearInterval(interval) }
  }, [courseId, reportError])

  return <section className="course-activity" aria-labelledby="activity-heading">
    <div className="activity-toolbar"><div><span className="eyebrow">DET SOM SKJER I EMNET</span>
      <h3 id="activity-heading">{teacher ? 'Emneaktivitet' : 'Din aktivitet'}</h3></div>
      <button className="secondary-button" disabled={loading || refreshing} onClick={async () => {
        setRefreshing(true)
        try { setActivity(await getCourseActivity(courseId)); setError('') } catch (failure) { reportError(failure) }
        finally { setRefreshing(false) }
      }}>{refreshing ? 'Oppdaterer …' : 'Oppdater aktivitet'}</button></div>
    <p className="activity-description">{teacher ? 'Følg med på innleveringer, quizforsøk og publiserte vurderinger i emnet.'
      : 'Dine innleveringer, quizforsøk og publiserte vurderinger samlet på ett sted.'}</p>
    <p className="field-hint">Viser de siste 50 hendelsene. Nye hendelser dukker vanligvis opp etter noen sekunder. Listen oppdateres automatisk.</p>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    {loading ? <p className="details-loading" role="status">Henter aktivitet …</p>
      : activity.length === 0 && !error ? <div className="activity-empty"><Icon name="clock" /><h4>Ingen aktivitet ennå</h4>
        <p>{teacher ? 'Her vises det når studentene leverer og vurderinger publiseres.' : 'Lever en oppgave eller ta en quiz for å komme i gang.'}</p></div>
      : <ol className="activity-list">{activity.map((event) => <li key={event.id}>
        <div className={`activity-icon ${event.type === 'GRADE_PUBLISHED' ? 'assessment' : ''}`}><Icon name={event.type === 'QUIZ_SUBMITTED' ? 'quiz' : event.type === 'GRADE_PUBLISHED' ? 'check' : 'assignment'} /></div>
        <div className="activity-entry"><div className="activity-entry-heading"><strong>{labels[event.type]}</strong>
          <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString('nb-NO', { dateStyle: 'medium', timeStyle: 'short' })}</time></div>
          <button className="activity-content-link" onClick={() => onOpen(event.moduleId, event.itemId)}>{event.itemTitle}<Icon name="arrow" /></button>
          <div className="activity-meta">{teacher && <span>{event.studentName}<small>{event.studentEmail}</small></span>}
            <span>{event.type === 'QUIZ_SUBMITTED' ? 'Forsøk' : 'Versjon'} {event.version}</span>
            {event.type === 'ASSIGNMENT_SUBMITTED' && event.late && <span className="activity-late">Levert etter fristen</span>}
          </div>
        </div>
      </li>)}</ol>}
  </section>
}
