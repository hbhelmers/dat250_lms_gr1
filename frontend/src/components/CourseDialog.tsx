import { useEffect, useRef, useState } from 'react'
import { formatDate } from '../demoData'
import type { Course, LearningItem } from '../demoData'
import { Icon } from './Icon'

const itemLabels = { page: 'Lesestoff', quiz: 'Quiz', assignment: 'Innlevering', code: 'Kodeeksempel' }

function ItemContent({ item, done, onComplete }: { item: LearningItem; done: boolean; onComplete: (id: string) => void }) {
  const [answer, setAnswer] = useState<number | null>(null)
  const [result, setResult] = useState<'correct' | 'incorrect' | null>(null)
  const question = item.question

  return <article className="item-content">
    <span className="content-type"><Icon name={item.type === 'assignment' ? 'assignment' : item.type === 'code' ? 'code' : item.type === 'quiz' ? 'quiz' : 'page'} />{itemLabels[item.type]}{done && <span className="content-completed"><Icon name="check" />Fullført</span>}</span>
    <h2>{item.title}</h2><p className="item-summary">{item.summary}</p>
    {item.deadline && <div className="item-deadline"><Icon name="clock" />Frist {formatDate(item.deadline)} 2026, kl. 23:59</div>}
    <div className="reading-content">{item.body.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}</div>
    {item.type === 'quiz' && question && <form className="quiz-form" onSubmit={(event) => { event.preventDefault(); if (answer === null) return; const correct = answer === question.correct; setResult(correct ? 'correct' : 'incorrect'); if (correct) onComplete(item.id) }}>
      <fieldset><legend>{question.text}</legend>{question.options.map((option, index) => <label key={option} className={`quiz-option ${answer === index ? 'selected' : ''}`}><input type="radio" name={item.id} value={index} checked={answer === index} onChange={() => { setAnswer(index); setResult(null) }} /><span>{option}</span></label>)}</fieldset>
      <div aria-live="polite">{result && <div className={`quiz-feedback ${result}`}><strong>{result === 'correct' ? 'Helt riktig!' : 'Ikke helt. Prøv en gang til.'}</strong><p>{result === 'correct' ? question.explanation : 'Les spørsmålet en gang til og prøv et annet alternativ.'}</p></div>}</div>
      <button className="primary-button" type="submit" disabled={answer === null}>Sjekk svaret <Icon name="arrow" /></button>
    </form>}
    {item.type === 'code' && <pre className="code-example"><code>{'@RestController\n@RequestMapping("/api/courses")\nclass CourseController {\n\n    @GetMapping\n    List<Course> getCourses() {\n        return courseService.findAll();\n    }\n}'}</code></pre>}
    {item.type === 'assignment' && <div className={`submission-demo ${done ? 'is-submitted' : ''}`}><span className="submission-symbol"><Icon name={done ? 'check' : 'assignment'} /></span><h3>{done ? 'Innleveringen er registrert i demoen' : 'Prøv en innlevering'}</h3><p>{done ? 'Du kan finne oppgaven under «Levert». Demoen nullstilles når du laster siden på nytt.' : 'Simuler en innlevering og se statusen oppdateres. Ingen filer blir sendt eller lagret.'}</p><button className="primary-button" disabled={done} onClick={() => onComplete(item.id)}>{done ? 'Levert i demoen' : 'Lever demooppgave'}<Icon name={done ? 'check' : 'arrow'} /></button></div>}
    {(item.type === 'page' || item.type === 'code') && <button className={`primary-button ${done ? 'completed-button' : ''}`} disabled={done} onClick={() => onComplete(item.id)}>{done ? 'Markert som lest' : 'Marker som lest'}<Icon name="check" /></button>}
  </article>
}

export function CourseDialog({ course, initialItemId, completed, onComplete, onClose }: { course: Course; initialItemId?: string; completed: string[]; onComplete: (id: string) => void; onClose: () => void }) {
  const items = course.modules.flatMap((module) => module.items)
  const [itemId, setItemId] = useState(initialItemId ?? items.find((item) => !completed.includes(item.id))?.id ?? items[0].id)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const contentRef = useRef<HTMLDivElement>(null)
  const item = items.find((entry) => entry.id === itemId)!
  const completedCount = items.filter((entry) => completed.includes(entry.id)).length
  const nextItem = items[items.findIndex((entry) => entry.id === itemId) + 1]

  useEffect(() => { if (!dialogRef.current?.open) dialogRef.current?.showModal() }, [])
  const selectItem = (id: string) => {
    setItemId(id)
    contentRef.current?.scrollTo({ top: 0 })
    contentRef.current?.parentElement?.scrollTo({ top: 0 })
  }

  return <dialog className="course-dialog" ref={dialogRef} onClose={onClose} aria-labelledby="dialog-title">
    <div className="dialog-header"><div><span className="eyebrow">{course.code} · HØST 2026</span><h2 id="dialog-title">{course.title}</h2></div><button className="icon-button" aria-label="Lukk emnet" onClick={() => dialogRef.current?.close()}><Icon name="close" /></button></div>
    <div className="dialog-body"><aside className="module-sidebar"><div className="module-progress"><div><strong>Din fremdrift</strong><span>{completedCount} av {items.length}</span></div><div className="progress-track"><div style={{ width: `${completedCount / items.length * 100}%` }} /></div></div>
      <nav aria-label="Moduler og innhold">{course.modules.map((module, index) => <div className="module-group" key={module.title}><h3><span>{String(index + 1).padStart(2, '0')}</span>{module.title}</h3>{module.items.map((entry) => <button key={entry.id} className={`module-item ${itemId === entry.id ? 'active' : ''} ${completed.includes(entry.id) ? 'done' : ''}`} aria-current={itemId === entry.id ? 'step' : undefined} onClick={() => selectItem(entry.id)}><Icon name={completed.includes(entry.id) ? 'check' : entry.type === 'assignment' ? 'assignment' : entry.type === 'quiz' ? 'quiz' : entry.type === 'code' ? 'code' : 'page'} /><span>{entry.title}</span></button>)}</div>)}</nav><p className="module-demo-note">Eksempelinnhold · Din rolle: student</p>
    </aside><div className="dialog-content" ref={contentRef}><ItemContent key={item.id} item={item} done={completed.includes(item.id)} onComplete={onComplete} /><div className="lesson-bottom-nav"><span>{items.findIndex((entry) => entry.id === itemId) + 1} av {items.length} aktiviteter</span>{nextItem && <button className="text-button" onClick={() => selectItem(nextItem.id)}>Neste aktivitet <Icon name="arrow" /></button>}</div></div></div>
  </dialog>
}
