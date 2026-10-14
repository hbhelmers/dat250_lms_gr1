import { useEffect, useRef, useState } from 'react'
import { ApiError, getCourses, getSession, logout } from '../api'
import type { Session, StoredCourse } from '../api'
import { CreateCourseDialog } from './CreateCourseDialog'
import { Icon } from './Icon'
import { AuthPanel } from './AuthPanel'

function CourseDetails({ course, onClose }: { course: StoredCourse; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    if (!dialogRef.current?.open) dialogRef.current?.showModal()
  }, [])

  return (
    <dialog className="create-course-dialog course-details-dialog" ref={dialogRef}
      onClose={onClose} aria-labelledby="course-details-heading">
      <div className="dialog-header">
        <div><span className="eyebrow">{course.semester}</span><h2 id="course-details-heading">{course.title}</h2></div>
        <button className="icon-button" aria-label="Lukk emnet" onClick={() => dialogRef.current?.close()}><Icon name="close" /></button>
      </div>
      <div className="course-details-body">
        <span className="content-type"><Icon name="book" />Om emnet</span>
        <p className="stored-description">{course.description || 'Ingen beskrivelse lagt til.'}</p>
        <div className="course-scope-note"><Icon name="sprout" /><p>Emnet er klart. Moduler og læringsinnhold kommer i neste versjon.</p></div>
      </div>
    </dialog>
  )
}

export function CourseWorkspace({ onOpenDemo }: { onOpenDemo: () => void }) {
  const [session, setSession] = useState<Session | null>(null)
  const [courses, setCourses] = useState<StoredCourse[]>([])
  const [initialLoading, setInitialLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [query, setQuery] = useState('')
  const [semesterFilter, setSemesterFilter] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<StoredCourse | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    async function restoreSession() {
      try {
        const current = await getSession(controller.signal)
        if (controller.signal.aborted) return
        setSession(current)
        const stored = await getCourses(controller.signal)
        if (!controller.signal.aborted) setCourses(stored)
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) {
          setSession(null)
          setCourses([])
        } else {
          setError(failure instanceof Error ? failure.message : 'Kunne ikke laste emnene.')
        }
      } finally {
        if (!controller.signal.aborted) setInitialLoading(false)
      }
    }
    void restoreSession()
    return () => controller.abort()
  }, [])

  const sessionExpired = () => {
    setSession(null)
    setCourses([])
    setCreating(false)
    setSelected(null)
    setNotice('')
    setError('Økten er utløpt. Logg inn igjen for å fortsette.')
  }

  const refreshCourses = async () => {
    setRefreshing(true)
    setError('')
    try {
      setCourses(await getCourses())
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) sessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Kunne ikke oppdatere emnene.')
    } finally {
      setRefreshing(false)
    }
  }

  const semesters = [...new Set(courses.map((course) => course.semester))]
  const filtered = courses.filter((course) =>
    (!semesterFilter || course.semester === semesterFilter)
    && `${course.title} ${course.description ?? ''} ${course.semester}`
      .toLocaleLowerCase('nb').includes(query.toLocaleLowerCase('nb')))

  return (
    <div className="app-shell live-workspace">
      <aside className="sidebar">
        <a className="brand" href="#emner" aria-label="Lær – gå til emneoversikten"><span className="brand-mark"><Icon name="layers" /></span>lær<span className="brand-period">.</span></a>
        <div className="workspace-label">DITT LÆRINGSROM</div>
        <nav className="main-nav" aria-label="Hovedmeny">
          <a href="#emner" className="nav-item active" aria-label="Emner" aria-current="page"><Icon name="book" /><span>Emner</span>{session && <span className="nav-count">{courses.length}</span>}</a>
          <button className="nav-item" aria-label="Utforsk demoen" onClick={onOpenDemo}><Icon name="grid" /><span>Utforsk demoen</span></button>
        </nav>
        <div className="sidebar-bottom">
          <div className="semester-note"><Icon name="sprout" /><strong>Små steg.<br />Stor fremgang.</strong><p>Et godt emne starter<br />med en god idé.</p><span>ET STED FOR LÆRING</span></div>
          <div className="sidebar-profile"><span className="user-avatar">{session?.name.charAt(0).toLocaleUpperCase('nb') || 'L'}</span><div><strong title={session?.email}>{session?.name || 'Velkommen'}</strong><span>{session ? 'Innlogget' : 'Ditt læringsrom venter'}</span></div>{session && <span className="online-indicator" />}</div>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">Mitt læringsrom <Icon name="chevron" /><strong>Emner</strong></div>
          <div className="topbar-actions">
            {session && <label className="search-field"><Icon name="search" /><input type="search" placeholder="Søk etter emner …" aria-label="Søk etter emner" value={query} onChange={(event) => setQuery(event.target.value)} /></label>}
            {session && <button className="text-button logout-button" disabled={loggingOut} onClick={async () => {
              setLoggingOut(true)
              setError('')
              try {
                await logout()
                setSession(null)
                setCourses([])
                setNotice('')
                setCreating(false)
                setSelected(null)
                setQuery('')
                setSemesterFilter('')
              } catch (failure) {
                if (failure instanceof ApiError && failure.status === 401) sessionExpired()
                else setError(failure instanceof Error ? failure.message : 'Kunne ikke logge ut.')
              } finally { setLoggingOut(false) }
            }}>{loggingOut ? 'Logger ut …' : 'Logg ut'}</button>}
          </div>
        </header>

        <main className="main-content" id="emner">
          <div className="page-heading">
            <div><p className="eyebrow">{session ? 'PLASS TIL NYE IDEER' : 'VELKOMMEN TIL LÆR'}</p><h1>{session ? 'Dine emner starter her.' : 'Et godt sted å lære.'}</h1><p className="page-description">{session ? 'Opprett et emne, samle ideene og bygg videre.' : 'Logg inn for å opprette og finne emner.'}</p></div>
            {session && !initialLoading && <button className="primary-button" disabled={refreshing} onClick={() => { setNotice(''); setCreating(true) }}>Nytt emne <span aria-hidden="true">+</span></button>}
          </div>

          {initialLoading ? <div className="workspace-loading" role="status"><span className="loading-dot" />Henter læringsrommet ditt …</div> : !session ? (
            <AuthPanel error={error} onClearError={() => setError('')} onOpenDemo={onOpenDemo}
              onLoggedIn={async (current) => {
                setSession(current)
                setQuery('')
                setSemesterFilter('')
                await refreshCourses()
              }} />
          ) : (
            <>
              {error && <p className="api-feedback error" role="alert">{error}</p>}
              {notice && <p className="api-feedback success" role="status"><Icon name="check" />{notice}</p>}
              <div className="course-list-toolbar">
                <h2>Alle emner <span>{courses.length}</span></h2>
                <div className="course-list-controls">
                  <label className="semester-filter"><span className="visually-hidden">Filtrer etter semester</span><select value={semesterFilter} onChange={(event) => setSemesterFilter(event.target.value)}><option value="">Alle semestre</option>{semesters.map((semester) => <option key={semester}>{semester}</option>)}</select></label>
                  <button className="secondary-button" onClick={() => void refreshCourses()} disabled={refreshing}>{refreshing ? 'Henter …' : 'Oppdater'}</button>
                </div>
              </div>
              <div className="course-grid stored-course-grid">
                {filtered.map((course) => {
                  const color = (['sage', 'lavender', 'peach'] as const)[(course.id - 1) % 3]
                  return <button key={course.id} className="course-card stored-course-card" aria-label={`Åpne emnet ${course.title}`} onClick={() => setSelected(course)}>
                    <div className={`course-artwork ${color}`} aria-hidden="true"><span className="artwork-label">{course.semester}</span><span className="stored-course-glyph"><Icon name="book" /></span><span className="artwork-caption">PLASS TIL NYE IDEER</span></div>
                    <div className="course-card-body"><span className="course-semester">EMNE</span><h3>{course.title}</h3><p className="stored-card-description">{course.description || 'Ingen beskrivelse lagt til.'}</p><div className="course-card-footer"><span>Se emnet</span><Icon name="arrow" /></div></div>
                  </button>
                })}
                {courses.length === 0 && !error && <div className="empty-state first-course-state"><Icon name="sprout" /><h3>Ditt første emne venter.</h3><p>Begynn med en tittel og en idé om hva studentene skal lære.</p><button className="primary-button" disabled={refreshing} onClick={() => setCreating(true)}>Opprett ditt første emne <Icon name="arrow" /></button></div>}
                {courses.length > 0 && filtered.length === 0 && <div className="empty-state"><Icon name="search" /><h3>Ingen emner funnet</h3><p>Prøv et annet søk eller velg et annet semester.</p><button className="text-button" onClick={() => { setQuery(''); setSemesterFilter('') }}>Tøm filtrene</button></div>}
              </div>
            </>
          )}
          <footer className="page-footer"><span>Et sted for nysgjerrighet.</span><span>Lær · Emneadministrasjon</span></footer>
        </main>
      </div>

      {creating && session && <CreateCourseDialog onClose={() => setCreating(false)} onSessionExpired={sessionExpired} onSaved={(course) => {
        setCourses((current) => [course, ...current])
        setQuery('')
        setSemesterFilter('')
        setError('')
        setNotice(`«${course.title}» er opprettet.`)
        setCreating(false)
      }} />}
      {selected && session && <CourseDetails course={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
