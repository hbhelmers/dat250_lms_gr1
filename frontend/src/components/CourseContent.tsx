import { useCallback, useEffect, useState } from 'react'
import { ApiError, deleteLearningPage, deleteModule, getModules, saveLearningPage, saveModule, setModuleOrder, setPageOrder } from '../api'
import type { LearningModule, LearningPage } from '../api'
import { Icon } from './Icon'
import { LessonBody, ModuleEditor, PageEditor } from './LessonEditors'

type Selection = { moduleId: number | null; pageId: number | null }
type Editor = { kind: 'module'; module?: LearningModule } | { kind: 'page'; moduleId: number; page?: LearningPage }

function pageStatus(page: LearningPage) {
  if (!page.published) return 'Utkast'
  if (page.visibleFrom && new Date(page.visibleFrom).getTime() > Date.now()) return 'Planlagt'
  if (page.visibleUntil && new Date(page.visibleUntil).getTime() <= Date.now()) return 'Utløpt'
  return 'Publisert'
}

function movedIds(items: { id: number }[], id: number, direction: number) {
  const ids = items.map((item) => item.id)
  const index = ids.indexOf(id)
  const target = index + direction
  if (index >= 0 && target >= 0 && target < ids.length) [ids[index], ids[target]] = [ids[target], ids[index]]
  return ids
}

export function CourseContent({ courseId, teacher, onSessionExpired, onWorkingChange }: {
  courseId: number
  teacher: boolean
  onSessionExpired: () => void
  onWorkingChange: (working: boolean) => void
}) {
  const [modules, setModules] = useState<LearningModule[]>([])
  const [selection, setSelection] = useState<Selection>({ moduleId: null, pageId: null })
  const [editor, setEditor] = useState<Editor | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const blocked = busy || editor !== null

  useEffect(() => {
    onWorkingChange(blocked)
    return () => onWorkingChange(false)
  }, [blocked, onWorkingChange])

  const applyModules = useCallback((data: LearningModule[], preferred?: Selection) => {
    setModules(data)
    setSelection((previous) => {
      const desired = preferred ?? previous
      const module = data.find((entry) => entry.id === desired.moduleId) ?? data[0]
      const page = module?.items.find((entry) => entry.id === desired.pageId)
      return { moduleId: module?.id ?? null, pageId: page?.id ?? (teacher ? null : module?.items[0]?.id ?? null) }
    })
  }, [teacher])

  const reportError = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
    setError(failure instanceof Error ? failure.message : 'Kunne ikke hente innholdet.')
  }, [onSessionExpired])

  useEffect(() => {
    const controller = new AbortController()
    getModules(courseId, controller.signal).then((data) => {
      if (!controller.signal.aborted) applyModules(data)
    }).catch((failure: unknown) => { if (!controller.signal.aborted) reportError(failure) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [courseId, applyModules, reportError])

  async function refresh(preferred?: Selection) { applyModules(await getModules(courseId), preferred) }

  async function mutate(action: () => Promise<unknown>, message: string, preferred?: Selection) {
    if (blocked) return
    setBusy(true); setError(''); setNotice('')
    try { await action(); await refresh(preferred); setNotice(message) }
    catch (failure) { reportError(failure) }
    finally { setBusy(false) }
  }

  const module = modules.find((entry) => entry.id === selection.moduleId)
  const page = module?.items.find((entry) => entry.id === selection.pageId)

  if (loading) return <p className="details-loading" role="status">Henter moduler …</p>

  return <section className="course-content" aria-label="Undervisningsinnhold">
    <div className="content-toolbar"><h3>Moduler og sider</h3><div>
      <button className="secondary-button" disabled={blocked} onClick={() => void mutate(async () => {}, 'Innholdet er oppdatert.')}>Oppdater innhold</button>
      {teacher && <button className="primary-button" disabled={blocked} onClick={() => { setNotice(''); setEditor({ kind: 'module' }) }}>Ny modul <span aria-hidden="true">+</span></button>}
    </div></div>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    {notice && <p className="api-feedback success" role="status">{notice}</p>}
    <div className="lesson-layout">
      <nav className="lesson-sidebar" aria-label="Moduler og sider">
        {modules.map((entry, index) => <div className="lesson-module" key={entry.id}>
          <div className="lesson-module-heading"><button className={module?.id === entry.id ? 'selected' : ''} disabled={blocked}
            onClick={() => { setSelection({ moduleId: entry.id, pageId: teacher ? null : entry.items[0]?.id ?? null }); setNotice('') }}>
            <span>{String(index + 1).padStart(2, '0')}</span>{entry.title}</button>
            {teacher && <div className="order-controls">
              <button disabled={blocked || index === 0} aria-label={`Flytt modulen ${entry.title} opp`}
                onClick={() => void mutate(() => setModuleOrder(courseId, movedIds(modules, entry.id, -1)), 'Modulrekkefølgen er lagret.')}>↑</button>
              <button disabled={blocked || index === modules.length - 1} aria-label={`Flytt modulen ${entry.title} ned`}
                onClick={() => void mutate(() => setModuleOrder(courseId, movedIds(modules, entry.id, 1)), 'Modulrekkefølgen er lagret.')}>↓</button>
            </div>}
          </div>
          {entry.items.map((item) => <button key={item.id} className={`lesson-link ${page?.id === item.id ? 'selected' : ''}`}
            disabled={blocked} aria-current={page?.id === item.id ? 'page' : undefined}
            onClick={() => { setSelection({ moduleId: entry.id, pageId: item.id }); setNotice('') }}>
            <Icon name="page" /><span>{item.title}{teacher && <small>{pageStatus(item)}</small>}</span>
          </button>)}
          {teacher && entry.items.length === 0 && <p className="module-empty">Ingen sider ennå</p>}
        </div>)}
        {modules.length === 0 && <p className="module-empty">{teacher ? 'Begynn med en modul.' : 'Ingen publiserte sider ennå.'}</p>}
      </nav>

      <div className="lesson-main">
        {editor?.kind === 'module' ? <ModuleEditor key={`module-${editor.module?.id ?? 'new'}`} module={editor.module}
          onCancel={() => setEditor(null)} onSessionExpired={onSessionExpired} onSave={async (title) => {
            const saved = await saveModule(courseId, title, editor.module?.id)
            applyModules(editor.module ? modules.map((entry) => entry.id === saved.id ? saved : entry) : [...modules, saved],
              { moduleId: saved.id, pageId: null })
            setEditor(null); setError(''); setNotice('Modulen er lagret.')
            try { await refresh({ moduleId: saved.id, pageId: null }) } catch (failure) { reportError(failure) }
          }} /> : editor?.kind === 'page' ? <PageEditor key={`page-${editor.page?.id ?? 'new'}`} page={editor.page}
          onCancel={() => setEditor(null)} onSessionExpired={onSessionExpired} onSave={async (input) => {
            const saved = await saveLearningPage(courseId, editor.moduleId, input, editor.page?.id)
            applyModules(modules.map((entry) => entry.id !== editor.moduleId ? entry : { ...entry,
              items: editor.page ? entry.items.map((item) => item.id === saved.id ? saved : item) : [...entry.items, saved] }),
              { moduleId: editor.moduleId, pageId: saved.id })
            setEditor(null); setError(''); setNotice(input.published ? 'Siden er lagret. Synligheten følger valgt tidsrom.' : 'Siden er lagret som utkast.')
            try { await refresh({ moduleId: editor.moduleId, pageId: saved.id }) } catch (failure) { reportError(failure) }
          }} /> : page && module ? <article className="lesson-reader">
            <span className="eyebrow">{module.title}</span>
            <div className="lesson-title"><h3>{page.title}</h3>{teacher && <span className="page-status">{pageStatus(page)}</span>}</div>
            {teacher && <div className="lesson-actions page-management">
              <button className="secondary-button" disabled={busy} onClick={() => setEditor({ kind: 'page', moduleId: module.id, page })}>Rediger side</button>
              <button className="secondary-button" disabled={busy} onClick={() => void mutate(() => saveLearningPage(courseId, module.id, { ...page, published: !page.published }, page.id), 'Publiseringsvalget er lagret.')}>{page.published ? 'Gjør til utkast' : 'Publiser side'}</button>
              <button className="text-button danger-button" disabled={busy} onClick={() => {
                if (window.confirm(`Slette siden «${page.title}»?`)) void mutate(() => deleteLearningPage(courseId, module.id, page.id), 'Siden er slettet.', { moduleId: module.id, pageId: null })
              }}>Slett side</button>
              <div className="order-controls">
                <button disabled={busy || module.items[0]?.id === page.id} aria-label="Flytt siden opp" onClick={() => void mutate(() => setPageOrder(courseId, module.id, movedIds(module.items, page.id, -1)), 'Siderekkefølgen er lagret.')}>↑</button>
                <button disabled={busy || module.items.at(-1)?.id === page.id} aria-label="Flytt siden ned" onClick={() => void mutate(() => setPageOrder(courseId, module.id, movedIds(module.items, page.id, 1)), 'Siderekkefølgen er lagret.')}>↓</button>
              </div>
            </div>}
            <LessonBody body={page.body} />
            {!teacher && module.items.findIndex((item) => item.id === page.id) < module.items.length - 1 && <button className="secondary-button next-lesson"
              onClick={() => setSelection({ moduleId: module.id, pageId: module.items[module.items.findIndex((item) => item.id === page.id) + 1].id })}>Neste side <Icon name="arrow" /></button>}
          </article> : module ? <div className="module-overview"><span className="eyebrow">MODUL</span><h3>{module.title}</h3>
            <p>{module.items.length} {module.items.length === 1 ? 'side' : 'sider'} i denne modulen.</p>
            {teacher && <div className="lesson-actions">
              <button className="primary-button" disabled={busy} onClick={() => setEditor({ kind: 'page', moduleId: module.id })}>Ny side <span aria-hidden="true">+</span></button>
              <button className="secondary-button" disabled={busy} onClick={() => setEditor({ kind: 'module', module })}>Endre modultittel</button>
              <button className="text-button danger-button" disabled={busy} onClick={() => {
                if (window.confirm(`Slette modulen «${module.title}» og alle sidene i den?`)) void mutate(() => deleteModule(courseId, module.id), 'Modulen er slettet.')
              }}>Slett modul</button>
            </div>}
          </div> : <div className="content-empty"><Icon name="sprout" /><h3>{teacher ? 'Gi emnet innhold.' : 'Innholdet kommer snart.'}</h3>
            <p>{teacher ? 'Lag en modul, skriv en side og publiser når den er klar.' : 'Publiserte sider vises her når læreren gjør dem tilgjengelige.'}</p>
          </div>}
      </div>
    </div>
  </section>
}
