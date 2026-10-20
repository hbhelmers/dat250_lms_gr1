import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, getQuiz, getQuizResults, saveQuiz, submitQuiz } from '../api'
import type { LearningItem, Quiz, QuizAttempt } from '../api'
import { LessonBody } from './LessonEditors'
import { QuizEditor } from './QuizEditor'

function dateTime(value: string) { return new Date(value).toLocaleString('nb-NO', { dateStyle: 'medium', timeStyle: 'short' }) }

export function QuizPanel({ courseId, moduleId, quizId, teacher, onSessionExpired, onWorkingChange, onChanged }: {
  courseId: number; moduleId: number; quizId: number; teacher: boolean; onSessionExpired: () => void
  onWorkingChange: (working: boolean) => void; onChanged: (content: LearningItem) => void
}) {
  const [quiz, setQuiz] = useState<Quiz | null>(null)
  const [results, setResults] = useState<QuizAttempt[]>([])
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [answers, setAnswers] = useState<Record<number, number>>({})
  const [completed, setCompleted] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const submissionId = useRef<string | null>(null)
  useEffect(() => { onWorkingChange(editing || busy); return () => onWorkingChange(false) }, [editing, busy, onWorkingChange])
  const reportError = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
    setError(failure instanceof Error ? failure.message : 'Kunne ikke hente quizen.')
  }, [onSessionExpired])

  const load = useCallback((signal?: AbortSignal) => {
    return Promise.all([getQuiz(courseId, moduleId, quizId, signal),
      teacher ? getQuizResults(courseId, moduleId, quizId, signal) : Promise.resolve([])])
  }, [courseId, moduleId, quizId, teacher])
  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(([data, attempts]) => {
      if (!controller.signal.aborted) { setQuiz(data); setResults(attempts) }
    }).catch((failure: unknown) => { if (!controller.signal.aborted) reportError(failure) })
    return () => controller.abort()
  }, [load, reportError])
  useEffect(() => {
    if (!quiz?.deadline) return
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [quiz?.deadline])

  async function reload() {
    setBusy(true); setError('')
    try {
      const [data, attempts] = await load()
      setQuiz(data); setResults(attempts); setAnswers({}); setCompleted(false); submissionId.current = null
    }
    catch (failure) { reportError(failure) }
    finally { setBusy(false) }
  }

  if (editing && quiz) return <QuizEditor quiz={quiz} onCancel={() => setEditing(false)} onSessionExpired={onSessionExpired}
    onSave={async (input) => {
      const saved = await saveQuiz(courseId, moduleId, input, quizId)
      setQuiz(saved); onChanged(saved.content); setEditing(false); setError(''); setNotice('Quizen er lagret.')
    }} />

  const latest = quiz?.attempts[0]
  const remaining = quiz ? Math.max(0, quiz.maxAttempts - quiz.attempts.length) : 0
  const closed = quiz?.deadline ? new Date(quiz.deadline).getTime() <= now : false
  return <section className="quiz-panel" aria-label="Quiz">
    <div className="quiz-panel-toolbar">
      <span className="eyebrow">QUIZ · FLERVALG</span>
      <div className="lesson-actions"><button className="secondary-button" disabled={busy} onClick={() => void reload()}>Oppdater quiz</button>
        {teacher && quiz && <button className="secondary-button" disabled={busy} onClick={() => { setNotice(''); setEditing(true) }}>Rediger quiz</button>}</div>
    </div>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    {notice && <p className="api-feedback success" role="status">{notice}</p>}
    {!quiz ? !error && <p className="details-loading" role="status">Henter quiz …</p> : <>
      <h3 className="quiz-heading">{quiz.content.title}</h3>
      <div className="quiz-facts"><span>{quiz.questions.length} spørsmål</span><span>{teacher ? `${quiz.maxAttempts} forsøk per student` : `${remaining} av ${quiz.maxAttempts} forsøk igjen`}</span>
        <span>{quiz.deadline ? `Frist: ${dateTime(quiz.deadline)}` : 'Ingen innleveringsfrist'}</span></div>
      {quiz.content.body && <LessonBody body={quiz.content.body} />}
      {teacher ? <>
        <p className="field-hint">{quiz.resultsVisible ? 'Studentene får se resultater og fasit etter innlevering.' : 'Resultatene er skjult for studentene.'}</p>
        <div className="quiz-teacher-preview">{quiz.questions.map((question, index) => <section key={question.id}>
          <h4>{index + 1}. {question.prompt}</h4>
          <ul>{question.options.map((option, position) => <li key={position} className={position === question.correctOption ? 'correct-answer' : ''}>
            {option}{position === question.correctOption && <strong>Riktig svar</strong>}</li>)}</ul>
          {question.hint && <p className="field-hint">Hint ved feil: {question.hint}</p>}
        </section>)}</div>
        <section className="quiz-results" aria-labelledby={`results-${quizId}`}><h4 id={`results-${quizId}`}>Innleverte forsøk <span>{results.length}</span></h4>
          {results.length === 0 ? <p className="field-hint">Ingen studenter har levert ennå.</p> : <div className="quiz-results-scroll"><table>
            <thead><tr><th>Student</th><th>Forsøk</th><th>Poeng</th><th>Levert</th></tr></thead>
            <tbody>{results.map((attempt) => <tr key={attempt.id}><td>{attempt.studentName}<small>{attempt.studentEmail}</small></td>
              <td>{attempt.number}</td><td>{attempt.score} / {attempt.total}</td><td>{dateTime(attempt.submittedAt)}</td></tr>)}</tbody>
          </table></div>}
        </section>
      </> : <>
        {!quiz.resultsVisible && <p className="api-feedback">Læreren publiserer resultatene senere.</p>}
        {closed && <p className="api-feedback">Innleveringsfristen er passert.</p>}
        {!closed && remaining === 0 && <p className="api-feedback">Du har brukt alle forsøkene dine.</p>}
        {completed ? <div className="quiz-submitted" role="status"><strong>Forsøk {latest?.number} er levert.</strong>
          <p>{latest?.score == null ? 'Resultatet vises når læreren publiserer det.' : `Du fikk ${latest.score} av ${latest.total} poeng.`}</p>
          {!closed && remaining > 0 && <button className="secondary-button" disabled={busy} onClick={() => { setCompleted(false); setAnswers({}); submissionId.current = null }}>Nytt forsøk</button>}
        </div> : !closed && remaining > 0 && <form className="quiz-answer-form" onSubmit={async (event) => {
          event.preventDefault()
          if (busy) return
          setBusy(true); setError(''); setNotice('')
          submissionId.current ??= crypto.randomUUID()
          try {
            const attempt = await submitQuiz(courseId, moduleId, quizId, submissionId.current,
              quiz.questions.map((question) => ({ questionId: question.id, option: answers[question.id] })))
            setQuiz({ ...quiz, attempts: [attempt, ...quiz.attempts.filter((entry) => entry.id !== attempt.id)] })
            setCompleted(true)
          } catch (failure) { reportError(failure) }
          finally { setBusy(false) }
        }}>
          {quiz.questions.map((question, index) => <fieldset key={question.id} disabled={busy}><legend>{index + 1}. {question.prompt}</legend>
            {question.options.map((option, position) => <label className={`quiz-answer-option ${answers[question.id] === position ? 'selected' : ''}`} key={position}>
              <input type="radio" name={`answer-${question.id}`} value={position} required checked={answers[question.id] === position}
                onChange={() => setAnswers((current) => ({ ...current, [question.id]: position }))} /><span>{option}</span></label>)}
          </fieldset>)}
          <button className="primary-button" disabled={busy}>{busy ? 'Leverer …' : 'Lever svar'}</button>
        </form>}
        {quiz.attempts.length > 0 && <section className="quiz-history" aria-label="Dine quizforsøk"><h4>Dine forsøk</h4>
          {quiz.attempts.map((attempt, index) => <details key={attempt.id} open={completed && index === 0}>
            <summary>Forsøk {attempt.number} · {attempt.score == null ? 'Venter på resultat' : `${attempt.score} / ${attempt.total} poeng`}<span>{dateTime(attempt.submittedAt)}</span></summary>
            {attempt.answers.length === 0 ? <p>Resultatet er ikke publisert ennå.</p> : attempt.answers.map((answer) => {
              const question = quiz.questions.find((entry) => entry.id === answer.questionId)
              return <div className={`quiz-answer-feedback ${answer.correct ? 'correct' : ''}`} key={answer.questionId}>
                <strong>{answer.correct ? 'Riktig' : 'Feil'}: {question?.prompt}</strong><p>Ditt svar: {question?.options[answer.selectedOption]}</p>
                {!answer.correct && <p>Riktig svar: {question?.options[answer.correctOption]}</p>}
                {answer.hint && <p>Hint: {answer.hint}</p>}
              </div>
            })}
          </details>)}
        </section>}
      </>}
    </>}
  </section>
}
