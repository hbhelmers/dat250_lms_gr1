import { useEffect, useRef, useState } from 'react'
import { ApiError, enrollInCourse, getCourse, getCourseCatalog } from '../api'
import type { CatalogCourse, Institution, StoredCourse } from '../api'
import { Icon } from './Icon'
import './catalog.css'

const institutions = [
  { id: 'HVL' as const, short: 'HVL', name: 'Høgskulen på Vestlandet', description: 'DAT-emner og ingeniørfag', style: 'hvl' },
  { id: 'UIB' as const, short: 'UiB', name: 'Universitetet i Bergen', description: 'INF-emner i informatikk', style: 'uib' },
]

export function CourseCatalog({ onClose, onSessionExpired, onEnrolled, onOpen }: {
  onClose: () => void; onSessionExpired: () => void; onEnrolled: (course: StoredCourse) => void; onOpen: (course: StoredCourse) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [institution, setInstitution] = useState<Institution | null>(null)
  const [courses, setCourses] = useState<CatalogCourse[]>([])
  const [loading, setLoading] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const selected = institutions.find((entry) => entry.id === institution)
  const filtered = courses.filter((course) => `${course.courseCode ?? ''} ${course.title} ${course.description ?? ''}`
    .toLocaleLowerCase('nb').includes(query.toLocaleLowerCase('nb')))

  useEffect(() => { if (!dialog.current?.open) dialog.current?.showModal() }, [])
  useEffect(() => {
    if (!institution) return
    const controller = new AbortController()
    getCourseCatalog(institution, controller.signal).then((data) => {
      if (!controller.signal.aborted) setCourses(data)
    }).catch((failure: unknown) => {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
      setError(failure instanceof Error ? failure.message : 'Kunne ikke hente emnekatalogen.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [institution, onSessionExpired])

  async function choose(course: CatalogCourse) {
    if (busyId !== null) return
    setBusyId(course.id); setError(''); setNotice('')
    try {
      if (course.membershipRole) { onOpen(await getCourse(course.id)); return }
      const saved = await enrollInCourse(course.id)
      setCourses((current) => current.map((entry) => entry.id === saved.id ? { ...entry, membershipRole: saved.role } : entry))
      onEnrolled(saved); setNotice(`Du er påmeldt «${saved.title}». Emnet ligger nå under Mine emner.`)
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
      setError(failure instanceof Error ? failure.message : 'Kunne ikke melde deg på emnet.')
    } finally { setBusyId(null) }
  }

  return <dialog className="create-course-dialog course-catalog-dialog" ref={dialog} aria-labelledby="catalog-heading"
    onClose={onClose} onCancel={(event) => { if (busyId !== null) event.preventDefault() }}>
    <div className="dialog-header"><div><span className="eyebrow">FINN DITT NESTE EMNE</span>
      <h2 id="catalog-heading">{selected ? `Emner ved ${selected.short}` : 'Velg institusjon.'}</h2></div>
      <button className="icon-button" aria-label="Lukk emnekatalogen" disabled={busyId !== null} onClick={() => dialog.current?.close()}><Icon name="close" /></button></div>
    <div className="catalog-body">
      <div className="catalog-steps" aria-label="Steg i påmeldingen"><span className={!institution ? 'active' : ''}>01 · Velg institusjon</span><span aria-hidden="true">→</span><span className={institution ? 'active' : ''}>02 · Meld deg på emner</span></div>
      {!institution ? <>
        <p className="catalog-intro">Hvor vil du finne fag? Velg studiested, utforsk emnene og meld deg på fagene du vil følge.</p>
        <div className="institution-grid">{institutions.map((entry) => <button className={`institution-card ${entry.style}`} key={entry.id} aria-label={`Velg ${entry.short}`}
          onClick={() => { setLoading(true); setCourses([]); setQuery(''); setError(''); setNotice(''); setInstitution(entry.id) }}>
          <span className="institution-monogram">{entry.short}</span><strong>{entry.name}</strong><span>{entry.description}</span>
          <span className="institution-action">Se emner <Icon name="arrow" /></span>
        </button>)}</div>
        <p className="catalog-demo-note">Eksempelemner basert på emnekoder fra HVL og UiB. Påmeldingen gjelder dette læringsrommet.</p>
      </> : <>
        <div className="catalog-toolbar"><button className="text-button" disabled={busyId !== null} onClick={() => {
          setInstitution(null); setCourses([]); setQuery(''); setError(''); setNotice(''); setLoading(false)
        }}>← Bytt institusjon</button>
          <label className="catalog-search"><Icon name="search" /><input type="search" placeholder="Søk emnekode eller tittel …" aria-label="Søk i emnekatalogen"
            value={query} onChange={(event) => setQuery(event.target.value)} /></label></div>
        {error && <p className="api-feedback error" role="alert">{error}</p>}
        {notice && <p className="api-feedback success" role="status"><Icon name="check" />{notice}</p>}
        {loading ? <p className="details-loading" role="status">Henter emner …</p> : <>
          <div className="catalog-result-count"><span>{filtered.length} emner</span><span>{selected?.name}</span></div>
          <div className="catalog-course-grid">{filtered.map((course) => <article className="catalog-course" key={course.id} aria-labelledby={`catalog-course-${course.id}`}>
            <div className="catalog-course-meta"><span className={`institution-badge ${selected?.style}`}>{selected?.short}{course.courseCode ? ` · ${course.courseCode}` : ''}</span>
              <span>{course.semester}</span></div>
            <h3 id={`catalog-course-${course.id}`}>{course.title}</h3><p>{course.description || 'Et læringsrom med undervisningsinnhold og oppgaver.'}</p>
            <div className="catalog-course-footer"><div>{course.membershipRole && <span className="catalog-enrolled"><Icon name="check" />{course.membershipRole === 'TEACHER' ? 'Du underviser her' : 'Påmeldt'}</span>}
              {course.officialUrl && <a href={course.officialUrl} target="_blank" rel="noopener noreferrer">Offisiell studieplan ↗</a>}</div>
              <button className={course.membershipRole ? 'secondary-button' : 'primary-button'} disabled={busyId !== null} aria-label={`${course.membershipRole ? 'Åpne' : 'Meld deg på'} ${course.courseCode || course.title}`}
                onClick={() => void choose(course)}>{busyId === course.id ? 'Henter …' : course.membershipRole ? 'Åpne emne' : 'Meld meg på'}<Icon name="arrow" /></button>
            </div>
          </article>)}</div>
          {filtered.length === 0 && !error && <div className="catalog-empty"><Icon name="search" /><h3>Ingen emner funnet</h3><p>{query ? 'Prøv en annen emnekode eller tittel.' : 'Ingen emner er åpne for påmelding her ennå.'}</p></div>}
        </>}
      </>}
    </div>
  </dialog>
}
