import { useState } from 'react'
import { CourseDialog } from './components/CourseDialog'
import { Icon } from './components/Icon'
import { courses, formatDate, initialCompleted } from './demoData'
import type { Course, LearningItem } from './demoData'
import './App.css'

type View = 'overview' | 'courses' | 'assignments' | 'calendar'
type TaskFilter = 'open' | 'submitted' | 'all'

const navigation: { id: View; label: string; icon: 'grid' | 'book' | 'assignment' | 'calendar' }[] = [
  { id: 'overview', label: 'Oversikt', icon: 'grid' },
  { id: 'courses', label: 'Mine emner', icon: 'book' },
  { id: 'assignments', label: 'Oppgaver', icon: 'assignment' },
  { id: 'calendar', label: 'Kalender', icon: 'calendar' },
]

const assignments = courses.flatMap((course) =>
  course.modules.flatMap((module) =>
    module.items.filter((item) => item.type === 'assignment').map((item) => ({ course, item })),
  ),
).sort((a, b) => (a.item.deadline ?? '').localeCompare(b.item.deadline ?? ''))

function CourseArtwork({ course }: { course: Course }) {
  return <div className={`course-artwork ${course.color}`} aria-hidden="true">
    <span className="artwork-label">{course.code}</span>
    {course.color === 'sage' && <div className="artwork-terminal"><span /><span /><span /><pre>{'{'}<br />&nbsp; learn: true,<br />&nbsp; build: 'together'<br />{'}'}</pre></div>}
    {course.color === 'lavender' && <div className="artwork-shapes"><i /><i /><i /><i /></div>}
    {course.color === 'peach' && <div className="artwork-nodes"><i /><i /><i /><i /><i /></div>}
    <span className="artwork-caption">{course.category}</span>
  </div>
}

function DemoApp({ onOpenCourses }: { onOpenCourses: () => void }) {
  const [view, setView] = useState<View>('overview')
  const [query, setQuery] = useState('')
  const [completed, setCompleted] = useState(initialCompleted)
  const [selected, setSelected] = useState<{ course: Course; itemId?: string } | null>(null)
  const [taskFilter, setTaskFilter] = useState<TaskFilter>('open')
  const [showNotifications, setShowNotifications] = useState(false)

  const openCourse = (course: Course, itemId?: string) => setSelected({ course, itemId })
  const markCompleted = (id: string) => setCompleted((current) => current.includes(id) ? current : [...current, id])
  const navigate = (next: View) => { setView(next); setQuery(''); setShowNotifications(false) }
  const itemsFor = (course: Course) => course.modules.flatMap((module) => module.items)
  const progressFor = (course: Course) => {
    const items = itemsFor(course)
    return Math.round(items.filter((item) => completed.includes(item.id)).length / items.length * 100)
  }
  const filteredCourses = courses.filter((course) => `${course.code} ${course.title}`.toLocaleLowerCase('nb').includes(query.toLocaleLowerCase('nb')))
  const pending = assignments.filter(({ item }) => !completed.includes(item.id))
  const visibleAssignments = assignments.filter(({ item }) => taskFilter === 'all' || (taskFilter === 'submitted' ? completed.includes(item.id) : !completed.includes(item.id)))

  const courseCards = <div className="course-grid">
    {filteredCourses.map((course) => <button className="course-card" key={course.id} aria-label={`Åpne ${course.code}: ${course.title}`} onClick={() => openCourse(course)}>
      <CourseArtwork course={course} />
      <div className="course-card-body"><span className="course-semester">HØST 2026 <span>STUDENT</span></span><h3>{course.title}</h3><p>{course.modules.length} moduler <span>·</span> {itemsFor(course).length} aktiviteter</p><div className="course-progress-label"><span>Din fremdrift</span><strong>{progressFor(course)} %</strong></div><div className="progress-track"><div style={{ width: `${progressFor(course)}%` }} /></div><div className="course-card-footer"><span className={`teacher-avatar ${course.color}`}>{course.teacherInitials}</span><span>{course.teacher}</span><Icon name="arrow" /></div></div>
    </button>)}
    {filteredCourses.length === 0 && <div className="empty-state"><Icon name="search" /><h3>Ingen emner funnet</h3><p>Prøv et emnenavn eller en emnekode.</p><button className="text-button" onClick={() => setQuery('')}>Tøm søket</button></div>}
  </div>

  const deadlineRow = ({ course, item }: { course: Course; item: LearningItem }) => <button className="deadline-row" key={item.id} onClick={() => openCourse(course, item.id)}><span className={`deadline-icon ${course.color}`}><Icon name="assignment" /></span><span className="deadline-copy"><span className="eyebrow">{course.code}</span><strong>{item.title}</strong><span>{formatDate(item.deadline!)} · kl. 23:59</span></span><Icon name="chevron" /></button>

  return <div className="app-shell">
    <button className="demo-return-button" onClick={onOpenCourses}>Til emneadministrasjonen <Icon name="arrow" /></button>
    <aside className="sidebar">
      <a className="brand" href="#oversikt" aria-label="Lær – gå til oversikten" onClick={(event) => { event.preventDefault(); navigate('overview') }}><span className="brand-mark"><Icon name="layers" /></span>lær<span className="brand-period">.</span></a>
      <div className="workspace-label">DITT LÆRINGSROM</div>
      <nav className="main-nav" aria-label="Hovedmeny">{navigation.map((entry) => <button key={entry.id} className={view === entry.id ? 'nav-item active' : 'nav-item'} aria-label={entry.label} aria-current={view === entry.id ? 'page' : undefined} onClick={() => navigate(entry.id)}><Icon name={entry.icon} /><span>{entry.label}</span>{entry.id === 'assignments' && pending.length > 0 && <span className="nav-count">{pending.length}</span>}</button>)}</nav>
      <div className="sidebar-courses"><div className="workspace-label">DINE EMNER <span>{courses.length}</span></div>{courses.map((course) => <button key={course.id} onClick={() => openCourse(course)}><span className={`course-dot ${course.color}`} /><span>{course.code}</span><Icon name="chevron" /></button>)}</div>
      <div className="sidebar-bottom"><div className="semester-note"><Icon name="sprout" /><strong>Små steg.<br />Stor fremgang.</strong><p>Et nytt semester.<br />Nye ting å oppdage.</p><span>HØST 2026</span></div><div className="sidebar-profile"><span className="user-avatar">V</span><div><strong>Viktor</strong><span>Student · HVL</span></div><span className="online-indicator" title="Demoprofil" /></div></div>
    </aside>

    <div className="workspace">
      <header className="topbar"><div className="breadcrumb">Mitt læringsrom <Icon name="chevron" /><strong>{navigation.find((entry) => entry.id === view)?.label}</strong></div><div className="topbar-actions"><label className="search-field"><Icon name="search" /><input type="search" placeholder="Søk etter emner …" aria-label="Søk etter emner" value={query} onChange={(event) => { setQuery(event.target.value); setView('courses') }} /></label><div className="notification-wrapper"><button className={`icon-button notification-button ${showNotifications ? 'selected' : ''}`} aria-label="Vis varsler" aria-expanded={showNotifications} onClick={() => setShowNotifications(!showNotifications)}><Icon name="bell" /><span className="notification-dot" /></button>{showNotifications && <div className="notification-popover"><strong>Siste nytt</strong><p><span>DAT250</span>Uke 42 er klar! Utforsk modulen «Data som varer».</p><button className="text-button" onClick={() => { openCourse(courses[0], 'dat250-orm'); setShowNotifications(false) }}>Åpne modulen <Icon name="arrow" /></button></div>}</div><span className="user-avatar topbar-avatar" title="Viktor · demoprofil">V</span></div></header>

      <main className="main-content" id="main-content">
        <div className="page-heading"><div><p className="eyebrow">{view === 'overview' ? 'VELKOMMEN TILBAKE, VIKTOR' : 'DITT LÆRINGSROM'}</p><h1>{({ overview: 'Klar for å lære noe nytt?', courses: 'Dine emner.', assignments: 'En ting om gangen.', calendar: 'Plass til å planlegge.' })[view]}</h1><p className="page-description">{({ overview: 'En liten oversikt. Litt mindre å holde styr på.', courses: 'Alt du trenger til semesteret, samlet på ett sted.', assignments: 'Her finner du innleveringer og fristene som kommer.', calendar: 'Frister i oktober, så du kan ligge et steg foran.' })[view]}</p></div><span className="demo-label"><span />Interaktiv demo</span></div>

        {view === 'overview' && <div className="overview-layout">
          <div className="overview-primary">
            <section className="continue-banner" aria-labelledby="continue-heading"><div className="continue-copy"><span className="banner-kicker"><span />DITT NESTE STEG</span><h2 id="continue-heading">Store ideer starter<br />med små steg.</h2><p>Fortsett med databaser og ORM i DAT250.<br />Du er allerede godt i gang.</p><button className="primary-button" onClick={() => openCourse(courses[0], itemsFor(courses[0]).find((item) => !completed.includes(item.id))?.id)}>Fortsett å lære <Icon name="arrow" /></button><span className="banner-footnote">DAT250 <span>·</span> Programvareteknologi</span></div><div className="learning-illustration" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><span className="illustration-spark spark-one">✳</span><span className="illustration-spark spark-two">+</span><div className="illustration-card back-card"><div /><div /><div /></div><div className="illustration-card front-card"><Icon name="code" /><span>learn. build. repeat.</span><div className="illustration-line" /><div className="illustration-line short" /></div><div className="illustration-check"><Icon name="check" /></div><span className="illustration-caption">litt klokere, hver dag.</span></div></section>
            <section className="courses-section" aria-labelledby="courses-heading"><div className="section-heading"><h2 id="courses-heading">Mine emner <span>{courses.length}</span></h2><button className="text-button" onClick={() => navigate('courses')}>Se alle emner <Icon name="arrow" /></button></div>{courseCards}</section>
            <section className="announcement" aria-labelledby="announcement-heading"><span className="announcement-icon"><Icon name="megaphone" /></span><div><div className="announcement-meta">SISTE FRA DAT250 <span>·</span> 14. OKTOBER</div><h3 id="announcement-heading">Fra modeller til kode</h3><p>Denne uken jobber vi med JPA og databaser. Uke 42-modulen er klar — ta en titt før neste forelesning.</p></div><button className="icon-button" aria-label="Åpne uke 42 i DAT250" onClick={() => openCourse(courses[0], 'dat250-orm')}><Icon name="arrow" /></button></section>
          </div>
          <aside className="overview-secondary" aria-label="Ukeoversikt og frister">
            <section className="week-panel"><div className="section-heading"><h2>Din uke</h2><span className="week-number">UKE 42</span></div><p>12. – 18. oktober</p><div className="week-days">{['M', 'T', 'O', 'T', 'F', 'L', 'S'].map((day, index) => <div className={index === 2 ? 'week-day today' : 'week-day'} key={index}><span>{day}</span><strong>{12 + index}</strong><i className={index === 4 ? 'has-deadline' : ''} /></div>)}</div><div className="week-note"><span className="tiny-dot" />{pending.some(({ item }) => item.deadline === '2026-10-16') ? 'En innlevering denne uken' : 'Denne ukens innlevering er levert'}</div><button className="text-button" onClick={() => navigate('calendar')}>Åpne kalenderen <Icon name="arrow" /></button></section>
            <section className="deadlines-panel"><div className="section-heading"><h2>Kommende frister</h2><Icon name="clock" /></div>{pending.length ? pending.slice(0, 3).map(deadlineRow) : <div className="all-done"><Icon name="check" /><p>Alt er levert. Godt jobbet!</p></div>}<button className="text-button" onClick={() => navigate('assignments')}>Alle oppgaver <Icon name="arrow" /></button></section>
            <section className="progress-note"><span className="progress-note-icon"><Icon name="sparkles" /></span><div><strong>Du er på vei!</strong><p>{completed.length} aktiviteter fullført.<br />Hver eneste en teller.</p></div></section>
          </aside>
        </div>}

        {view === 'courses' && <section className="full-courses"><div className="section-heading"><h2>Høst 2026 <span>{filteredCourses.length}</span></h2><span className="muted-label">Din rolle: student</span></div>{courseCards}</section>}

        {view === 'assignments' && <section className="assignments-section"><div className="task-toolbar"><div className="filter-tabs" aria-label="Filtrer oppgaver">{([{ id: 'open', label: 'Åpne' }, { id: 'submitted', label: 'Levert' }, { id: 'all', label: 'Alle' }] as const).map((filter) => <button key={filter.id} className={taskFilter === filter.id ? 'active' : ''} aria-pressed={taskFilter === filter.id} onClick={() => setTaskFilter(filter.id)}>{filter.label}{filter.id === 'open' && <span>{pending.length}</span>}</button>)}</div><span className="muted-label">Sortert etter frist</span></div><div className="assignment-list">{visibleAssignments.map(({ course, item }) => <button className="assignment-card" key={item.id} onClick={() => openCourse(course, item.id)}><span className={`deadline-icon ${course.color}`}><Icon name="assignment" /></span><div className="assignment-title"><span className="eyebrow">{course.code} · {course.title}</span><h3>{item.title}</h3><p>{item.summary}</p></div><div className="assignment-date"><span>FRIST</span><strong>{formatDate(item.deadline!)}</strong></div><span className={`status-pill ${completed.includes(item.id) ? 'done' : ''}`}>{completed.includes(item.id) ? 'Levert' : 'Åpen'}</span><Icon name="chevron" /></button>)}{visibleAssignments.length === 0 && <div className="empty-state"><Icon name={taskFilter === 'open' ? 'check' : 'assignment'} /><h3>{taskFilter === 'open' ? 'Du er helt i rute!' : 'Ingen innleveringer ennå'}</h3><p>{taskFilter === 'open' ? 'Alle demooppgavene er levert.' : 'Åpne en oppgave og prøv en demo-innlevering.'}</p></div>}</div></section>}

        {view === 'calendar' && <section className="calendar-section"><div className="section-heading"><h2>Oktober <span>2026</span></h2><span className="calendar-legend"><span className="tiny-dot" />Innleveringsfrist</span></div><div className="calendar-grid">{['Mandag', 'Tirsdag', 'Onsdag', 'Torsdag', 'Fredag', 'Lørdag', 'Søndag'].map((day) => <div className="calendar-weekday" key={day}>{day}</div>)}{Array.from({ length: 35 }, (_, index) => { const day = index - 2; const outside = day < 1 || day > 31; const dayAssignments = assignments.filter(({ item }) => Number(item.deadline?.slice(-2)) === day); return <div className={`calendar-day ${outside ? 'outside' : ''} ${day === 14 ? 'today' : ''}`} key={index}>{!outside && <><span className="calendar-date">{day}</span>{dayAssignments.map(({ course, item }) => <button className={`calendar-event ${course.color} ${completed.includes(item.id) ? 'submitted' : ''}`} key={item.id} onClick={() => openCourse(course, item.id)} aria-label={`${course.code}: ${item.title}, ${formatDate(item.deadline!)}`}><strong>{course.code}</strong><span>{item.title}</span>{completed.includes(item.id) && <Icon name="check" />}</button>)}</>}</div> })}</div><p className="calendar-footnote">Eksempeluke i demoen: 14. oktober 2026. Trykk på en frist for å åpne oppgaven.</p></section>}

        <footer className="page-footer"><span>Et sted for nysgjerrighet.</span><span>HVL · Høst 2026 <span> / </span> Demo med eksempeldata</span></footer>
      </main>
    </div>
    {selected && <CourseDialog key={`${selected.course.id}-${selected.itemId ?? 'start'}`} course={selected.course} initialItemId={selected.itemId} completed={completed} onComplete={markCompleted} onClose={() => setSelected(null)} />}
  </div>
}

export default DemoApp
