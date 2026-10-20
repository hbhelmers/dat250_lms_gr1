import { useState } from 'react'
import { ApiError } from '../api'
import type { Quiz, QuizInput, QuizQuestionInput } from '../api'
import { localDateTime } from '../dateTime'

function newQuestion(): QuizQuestionInput { return { prompt: '', options: ['', ''], correctOption: 0, hint: '' } }

export function QuizEditor({ quiz, onSave, onCancel, onSessionExpired }: {
  quiz?: Quiz; onSave: (input: QuizInput) => Promise<void>; onCancel: () => void; onSessionExpired: () => void
}) {
  const [questions, setQuestions] = useState<QuizQuestionInput[]>(() => quiz?.questions.map((question) => ({
    prompt: question.prompt, options: [...question.options], correctOption: question.correctOption ?? 0, hint: question.hint ?? '',
  })) ?? [newQuestion()])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const locked = quiz?.questionsLocked ?? false
  function updateQuestion(index: number, update: Partial<QuizQuestionInput>) {
    setQuestions((current) => current.map((question, position) => position === index ? { ...question, ...update } : question))
  }

  return <form className="lesson-editor quiz-editor" onSubmit={async (event) => {
    event.preventDefault()
    if (saving) return
    const fields = new FormData(event.currentTarget)
    const value = (name: string) => String(fields.get(name) ?? '')
    const date = (name: string) => value(name) ? new Date(value(name)).toISOString() : null
    setSaving(true); setError(''); setFieldErrors({})
    try {
      await onSave({ title: value('title'), body: value('body'), published: fields.has('published'),
        visibleFrom: date('visibleFrom'), visibleUntil: date('visibleUntil'), deadline: date('deadline'),
        maxAttempts: Number(value('maxAttempts')), resultsVisible: fields.has('resultsVisible'), questions })
    } catch (failure) {
      if (failure instanceof ApiError) {
        if (failure.status === 401) { onSessionExpired(); return }
        setFieldErrors(failure.fieldErrors)
      }
      setError(failure instanceof Error ? failure.message : 'Kunne ikke lagre quizen.')
    } finally { setSaving(false) }
  }}>
    <h3>{quiz ? 'Rediger quiz' : 'Ny quiz'}</h3>
    <div className="form-field"><label htmlFor="quiz-title">Quiztittel</label>
      <input id="quiz-title" name="title" defaultValue={quiz?.content.title ?? ''} required maxLength={255} disabled={saving} autoFocus /></div>
    <div className="form-field"><label htmlFor="quiz-body">Introduksjon (valgfritt)</label>
      <textarea id="quiz-body" name="body" defaultValue={quiz?.content.body ?? ''} maxLength={100_000} rows={3} disabled={saving} /></div>
    <p className="field-hint">Hvert spørsmål har ett riktig svar. Merk fasiten med radioknappen.</p>
    {locked && <p className="api-feedback">Spørsmål og fasit er låst etter første innlevering. Du kan fortsatt endre innstillinger og publisering.</p>}
    {questions.map((question, index) => <fieldset key={index} className="quiz-question-editor" disabled={saving || locked}>
      <legend>Spørsmål {index + 1}</legend>
      <div className="form-field"><label htmlFor={`question-${index}`}>Spørsmålstekst {index + 1}</label>
        <textarea id={`question-${index}`} value={question.prompt} onChange={(event) => updateQuestion(index, { prompt: event.target.value })} required maxLength={2000} rows={2} /></div>
      {question.options.map((option, optionIndex) => <div className="quiz-option-editor" key={optionIndex}>
        <input type="radio" name={`correct-${index}`} aria-label={`Riktig svar på spørsmål ${index + 1}: alternativ ${optionIndex + 1}`}
          checked={question.correctOption === optionIndex} onChange={() => updateQuestion(index, { correctOption: optionIndex })} />
        <input aria-label={`Spørsmål ${index + 1}, alternativ ${optionIndex + 1}`} value={option} required maxLength={1000}
          onChange={(event) => updateQuestion(index, { options: question.options.map((text, position) => position === optionIndex ? event.target.value : text) })} />
        <button type="button" className="text-button danger-button" disabled={question.options.length <= 2} aria-label={`Fjern alternativ ${optionIndex + 1} fra spørsmål ${index + 1}`}
          onClick={() => updateQuestion(index, { options: question.options.filter((_, position) => position !== optionIndex),
            correctOption: question.correctOption === optionIndex ? 0 : question.correctOption > optionIndex ? question.correctOption - 1 : question.correctOption })}>×</button>
      </div>)}
      <button type="button" className="text-button" disabled={question.options.length >= 8}
        onClick={() => updateQuestion(index, { options: [...question.options, ''] })}>Legg til alternativ i spørsmål {index + 1}</button>
      <div className="form-field"><label htmlFor={`hint-${index}`}>Hint ved feil svar på spørsmål {index + 1} (valgfritt)</label>
        <input id={`hint-${index}`} value={question.hint} onChange={(event) => updateQuestion(index, { hint: event.target.value })} maxLength={2000} /></div>
      <div className="lesson-actions">
        <button type="button" className="secondary-button" disabled={index === 0} onClick={() => setQuestions((current) => {
          const result = [...current]; [result[index - 1], result[index]] = [result[index], result[index - 1]]; return result
        })}>Flytt spørsmål {index + 1} opp</button>
        <button type="button" className="text-button danger-button" disabled={questions.length === 1}
          onClick={() => setQuestions((current) => current.filter((_, position) => position !== index))}>Fjern spørsmål {index + 1}</button>
      </div>
    </fieldset>)}
    <button type="button" className="secondary-button" disabled={saving || locked || questions.length >= 30}
      onClick={() => setQuestions((current) => [...current, newQuestion()])}>Legg til spørsmål</button>
    <div className="visibility-fields">
      <div className="form-field"><label htmlFor="quiz-attempts">Maks antall forsøk</label>
        <input id="quiz-attempts" name="maxAttempts" type="number" min={1} max={20} required defaultValue={quiz?.maxAttempts ?? 3} disabled={saving} /></div>
      <div className="form-field"><label htmlFor="quiz-deadline">Innleveringsfrist (lokal tid)</label>
        <input id="quiz-deadline" name="deadline" type="datetime-local" defaultValue={localDateTime(quiz?.deadline)} disabled={saving} /></div>
    </div>
    <label className="publish-option"><input type="checkbox" name="resultsVisible" defaultChecked={quiz?.resultsVisible ?? true} disabled={saving} />Vis resultater til studentene</label>
    <p className="field-hint">Når dette er valgt, vises poeng, fasit og eventuelle hint etter innlevering.</p>
    <label className="publish-option"><input type="checkbox" name="published" defaultChecked={quiz?.content.published ?? false} disabled={saving} />Publisert</label>
    <div className="visibility-fields">
      <div className="form-field"><label htmlFor="quiz-from">Synlig fra (lokal tid)</label>
        <input id="quiz-from" name="visibleFrom" type="datetime-local" defaultValue={localDateTime(quiz?.content.visibleFrom)} disabled={saving} /></div>
      <div className="form-field"><label htmlFor="quiz-until">Synlig til (lokal tid)</label>
        <input id="quiz-until" name="visibleUntil" type="datetime-local" defaultValue={localDateTime(quiz?.content.visibleUntil)} disabled={saving} /></div>
    </div>
    <p className="field-hint">Tomme tidsfelt gir ingen tidsbegrensning. Utkast er bare synlige for lærere.</p>
    {error && <div className="api-feedback error" role="alert"><div><p>{error}</p>{Object.entries(fieldErrors).map(([field, message]) => {
      const question = /questions\[(\d+)\]/.exec(field)
      return <p key={field}>{question ? `Spørsmål ${Number(question[1]) + 1}: ` : ''}{message}</p>
    })}</div></div>}
    <div className="lesson-actions"><button className="secondary-button" type="button" disabled={saving} onClick={onCancel}>Avbryt</button>
      <button className="primary-button" disabled={saving}>{saving ? 'Lagrer …' : 'Lagre quiz'}</button></div>
  </form>
}
