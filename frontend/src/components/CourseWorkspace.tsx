import { useCallback, useEffect, useState } from 'react'
import { ApiError, getCourses, getSession, logout } from '../api'
import type { Session, StoredCourse } from '../api'
import { CreateCourseDialog } from './CreateCourseDialog'
import { Icon } from './Icon'
import { AuthPanel } from './AuthPanel'
import { CourseDetails } from './CourseDetails'
import { CourseCatalog } from './CourseCatalog'

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
  const [editing, setEditing] = useState<StoredCourse | null>(null)
  const [catalogOpen, setCatalogOpen] = useState(false)
  const canCreate = session?.preferredRole === 'TEACHER'

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

  const sessionExpired = useCallback(() => {
    setSession(null)
    setCourses([])
    setCreating(false)
    setSelected(null)
    setEditing(null)
    setCatalogOpen(false)
    setNotice('')
    setError('Økten er utløpt. Logg inn igjen for å fortsette.')
  }, [])

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
          <div className="sidebar-profile"><span className="user-avatar">{session?.name.charAt(0).toLocaleUpperCase('nb') || 'L'}</span><div><strong title={session?.email}>{session?.name || 'Velkommen'}</strong><span>{session ? session.preferredRole === 'TEACHER' ? 'Lærerprofil' : 'Studentprofil' : 'Ditt læringsrom venter'}</span></div>{session && <span className="online-indicator" />}</div>
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
                setEditing(null)
                setCatalogOpen(false)
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
            <div><p className="eyebrow">{session ? 'PLASS TIL NYE IDEER' : 'VELKOMMEN TIL LÆR'}</p><h1>{session ? 'Ditt læringsrom.' : 'Et godt sted å lære.'}</h1><p className="page-description">{session ? canCreate ? 'Emnene du underviser i og deltar på, samlet på ett sted.' : 'Finn emner ved HVL eller UiB, meld deg på og kom i gang.' : 'Logg inn for å finne og delta i emner.'}</p></div>
            {session && !initialLoading && <div className="workspace-course-actions">
              <button className={canCreate ? 'secondary-button' : 'primary-button'} disabled={refreshing} onClick={() => setCatalogOpen(true)}>Finn emner <Icon name="search" /></button>
              {canCreate && <button className="primary-button" disabled={refreshing} onClick={() => { setNotice(''); setCreating(true) }}>Nytt emne <span aria-hidden="true">+</span></button>}
            </div>}
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
                <h2>Mine emner <span>{courses.length}</span></h2>
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
                    <div className="course-card-body"><span className="course-semester">{course.institution === 'UIB' ? 'UiB' : course.institution ?? 'EMNE'}{course.courseCode ? ` · ${course.courseCode}` : ''}</span><h3>{course.title}</h3><p className="stored-card-description">{course.description || 'Ingen beskrivelse lagt til.'}</p><div className="course-card-footer"><span className={`course-role ${course.role === 'TEACHER' ? 'teacher' : 'student'}`}>{course.role === 'TEACHER' ? 'Lærer' : 'Student'}</span><span>Se emnet <Icon name="arrow" /></span></div></div>
                  </button>
                })}
                {courses.length === 0 && !error && <div className="empty-state first-course-state"><Icon name="sprout" /><h3>{canCreate ? 'Ingen emner ennå.' : 'Finn ditt første emne.'}</h3><p>{canCreate ? 'Opprett et emne du vil undervise i, eller utforsk emnekatalogen.' : 'Velg HVL eller UiB og meld deg på fagene du vil følge.'}</p><button className="primary-button" disabled={refreshing} onClick={() => canCreate ? setCreating(true) : setCatalogOpen(true)}>{canCreate ? 'Opprett ditt første emne' : 'Finn emner'} <Icon name="arrow" /></button></div>}
                {courses.length > 0 && filtered.length === 0 && <div className="empty-state"><Icon name="search" /><h3>Ingen emner funnet</h3><p>Prøv et annet søk eller velg et annet semester.</p><button className="text-button" onClick={() => { setQuery(''); setSemesterFilter('') }}>Tøm filtrene</button></div>}
              </div>
            </>
          )}
          <footer className="page-footer"><span>Et sted for nysgjerrighet.</span><span>Lær · Emneadministrasjon</span></footer>
        </main>
      </div>

      {creating && canCreate && <CreateCourseDialog onClose={() => setCreating(false)} onSessionExpired={sessionExpired} onSaved={(course) => {
        setCourses((current) => [course, ...current])
        setQuery('')
        setSemesterFilter('')
        setError('')
        setNotice(`«${course.title}» er opprettet.`)
        setCreating(false)
      }} />}
      {editing && session && <CreateCourseDialog course={editing} onClose={() => setEditing(null)}
        onSessionExpired={sessionExpired} onSaved={(course) => {
          setCourses((current) => current.map((item) => item.id === course.id ? course : item))
          setEditing(null)
          setNotice(`«${course.title}» er oppdatert.`)
          setSelected(course)
        }} />}
      {catalogOpen && session && <CourseCatalog onClose={() => setCatalogOpen(false)} onSessionExpired={sessionExpired} onEnrolled={(course) => {
        setCourses((current) => [course, ...current.filter((entry) => entry.id !== course.id)])
        setQuery(''); setSemesterFilter(''); setError(''); setNotice(`Du er påmeldt «${course.title}».`)
      }} onOpen={(course) => { setCatalogOpen(false); setSelected(course) }} />}
      {selected && session && <CourseDetails key={selected.id} course={selected} onClose={() => setSelected(null)}
        onSessionExpired={sessionExpired} onEdit={(course) => { setSelected(null); setEditing(course) }} />}
    </div>
  )
}
