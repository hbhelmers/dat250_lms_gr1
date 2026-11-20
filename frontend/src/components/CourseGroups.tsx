import { useCallback, useEffect, useId, useState } from 'react'
import {
  addGroupMember, answerGroupInvitation, ApiError, deleteGroup, getGroups, inviteToGroup, joinGroup,
  removeGroupMember, saveGroup, setSeekingGroup,
} from '../api'
import type { CourseGroup, GroupInput, GroupOverview, GroupSeeker } from '../api'
import { Icon } from './Icon'
import './groups.css'

export function CourseGroups({ courseId, onSessionExpired }: { courseId: number; onSessionExpired: () => void }) {
  const [overview, setOverview] = useState<GroupOverview | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const report = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return }
    setError(failure instanceof Error ? failure.message : 'Noe gikk galt med gruppene.')
  }, [onSessionExpired])

  useEffect(() => {
    const controller = new AbortController()
    getGroups(courseId, controller.signal).then(setOverview)
      .catch((failure: unknown) => { if (!controller.signal.aborted) report(failure) })
    return () => controller.abort()
  }, [courseId, report])

  const run = async (action: () => Promise<GroupOverview>, success: string) => {
    if (busy) return false
    setBusy(true); setError(''); setNotice('')
    try { setOverview(await action()); setNotice(success); return true } catch (failure) { report(failure); return false } finally { setBusy(false) }
  }

  if (!overview) return <section className="course-groups">{error
    ? <p className="api-feedback error" role="alert">{error}</p> : <p className="details-loading" role="status">Henter grupper …</p>}</section>

  return <section className="course-groups" aria-labelledby="groups-heading">
    <span className="eyebrow">{overview.teacher ? 'GRUPPER I EMNET' : 'FINN GRUPPE'}</span>
    <h3 id="groups-heading">Grupper</h3>
    <p className="groups-description">{overview.teacher
      ? 'Opprett grupper og fordel studentene. Åpne grupper kan studentene melde seg på selv, og studenter i en gruppe kan invitere de som ser etter gruppe.'
      : 'Bli med i en åpen gruppe, eller si fra at du ser etter gruppe. Da kan studenter i grupper med ledig plass invitere deg.'}</p>
    {error && <p className="api-feedback error" role="alert">{error}</p>}
    {notice && <p className="api-feedback success" role="status"><Icon name="check" />{notice}</p>}
    {overview.teacher
      ? <TeacherGroups courseId={courseId} overview={overview} busy={busy} run={run} />
      : <StudentGroups courseId={courseId} overview={overview} busy={busy} run={run} />}
  </section>
}

type Run = (action: () => Promise<GroupOverview>, success: string) => Promise<boolean>
type ViewProps = { courseId: number; overview: GroupOverview; busy: boolean; run: Run }

function TeacherGroups({ courseId, overview, busy, run }: ViewProps) {
  const [editing, setEditing] = useState<number | null>(null)
  return <>
    <div className="group-card group-create">
      <h4>Ny gruppe</h4>
      <GroupForm busy={busy} submitLabel="Opprett gruppe"
        onSubmit={(input) => run(() => saveGroup(courseId, input), `«${input.name.trim()}» er opprettet.`)} />
    </div>
    <p className="groups-summary">{overview.groups.length} grupper · {overview.ungroupedStudents.length} studenter uten gruppe</p>
    <ul className="group-list">{overview.groups.map((group) => <li key={group.id} className="group-card">
      {editing === group.id
        ? <GroupForm busy={busy} initial={group} submitLabel="Lagre" onCancel={() => setEditing(null)}
          onSubmit={async (input) => { if (await run(() => saveGroup(courseId, input, group.id), 'Gruppen er oppdatert.')) setEditing(null); return true }} />
        : <GroupHeading group={group} />}
      <MemberList group={group} onRemove={(userId, name) => run(() => removeGroupMember(courseId, group.id, userId), `${name} er fjernet fra gruppen.`)} busy={busy} />
      <div className="group-actions">
        {group.members.length < group.capacity && overview.ungroupedStudents.length > 0 &&
          <AddStudent students={overview.ungroupedStudents} busy={busy}
            onAdd={(userId) => run(() => addGroupMember(courseId, group.id, userId), 'Studenten er lagt til.')} />}
        {editing !== group.id && <button className="text-button" disabled={busy} onClick={() => setEditing(group.id)}>Rediger</button>}
        <button className="text-button group-danger" disabled={busy} onClick={() => {
          if (window.confirm(`Slette «${group.name}»? Medlemmene blir stående uten gruppe.`)) void run(() => deleteGroup(courseId, group.id), 'Gruppen er slettet.')
        }}>Slett</button>
      </div>
    </li>)}</ul>
    {overview.seekers.length > 0 && <SeekerList title="Ser etter gruppe" seekers={overview.seekers} />}
  </>
}

function StudentGroups({ courseId, overview, busy, run }: ViewProps) {
  const myGroup = overview.groups.find((group) => group.id === overview.myGroupId)
  const otherGroups = overview.groups.filter((group) => group.id !== overview.myGroupId)
  const canInvite = myGroup !== undefined && myGroup.members.length < myGroup.capacity
  return <>
    {myGroup ? <div className="group-card my-group">
      <span className="eyebrow">DIN GRUPPE</span>
      <GroupHeading group={myGroup} />
      <MemberList group={myGroup} busy={busy} />
      <div className="group-actions"><button className="text-button group-danger" disabled={busy} onClick={() => {
        if (window.confirm(`Melde deg ut av «${myGroup.name}»?`))
          void run(() => removeGroupMember(courseId, myGroup.id, overview.myUserId), 'Du har meldt deg ut av gruppen.')
      }}>Meld meg ut</button></div>
    </div> : <>
      {overview.invitations.map((invitation) => <div key={invitation.id} className="group-card group-invitation">
        <p><strong>{invitation.invitedBy}</strong> har invitert deg til <strong>{invitation.groupName}</strong>.</p>
        <div className="group-actions">
          <button className="primary-button" disabled={busy} onClick={() => void run(() => answerGroupInvitation(courseId, invitation.id, true), `Du er nå med i «${invitation.groupName}».`)}>Bli med<Icon name="check" /></button>
          <button className="text-button" disabled={busy} onClick={() => void run(() => answerGroupInvitation(courseId, invitation.id, false), 'Invitasjonen er avslått.')}>Avslå</button>
        </div>
      </div>)}
      <SeekingCard key={String(overview.myNote)} overview={overview} busy={busy}
        onSave={(note) => run(() => setSeekingGroup(courseId, note), note === null ? 'Du står ikke lenger på listen.' : 'Andre i emnet kan nå se at du ser etter gruppe.')} />
    </>}

    <SeekerList title={canInvite ? 'Ser etter gruppe – inviter noen' : 'Andre som ser etter gruppe'} seekers={overview.seekers}
      onInvite={canInvite ? (seeker) => run(() => inviteToGroup(courseId, myGroup.id, seeker.userId), `${seeker.name} er invitert.`) : undefined} busy={busy} />

    <h4 className="groups-subheading">{myGroup ? 'Andre grupper' : 'Grupper i emnet'}</h4>
    {otherGroups.length === 0 ? <p className="field-hint">Læreren har ikke opprettet {myGroup ? 'flere ' : ''}grupper ennå.</p>
      : <ul className="group-list">{otherGroups.map((group) => {
        const full = group.members.length >= group.capacity
        return <li key={group.id} className="group-card">
          <GroupHeading group={group} />
          <MemberList group={group} busy={busy} />
          {!myGroup && <div className="group-actions">{group.open && !full
            ? <button className="secondary-button" disabled={busy} onClick={() => void run(() => joinGroup(courseId, group.id), `Du er nå med i «${group.name}».`)}>Bli med</button>
            : <span className="field-hint">{full ? 'Gruppen er full.' : 'Lukket gruppe – du kan bli med hvis et medlem inviterer deg.'}</span>}</div>}
        </li>
      })}</ul>}
  </>
}

function SeekingCard({ overview, busy, onSave }: { overview: GroupOverview; busy: boolean; onSave: (note: string | null) => Promise<boolean> }) {
  const [note, setNote] = useState(overview.myNote ?? '')
  return <div className="group-card seeking-card">
    <label className="seeking-toggle"><input type="checkbox" checked={overview.seeking} disabled={busy}
      onChange={(event) => void onSave(event.target.checked ? note : null)} />Jeg ser etter gruppe</label>
    <div className="form-field">
      <label htmlFor="seeking-note">Kort notat til de andre (valgfritt)</label>
      <textarea id="seeking-note" rows={2} maxLength={280} value={note} disabled={busy} onChange={(event) => setNote(event.target.value)}
        placeholder="For eksempel: Vil helst jobbe med frontend, kan møtes på campus tirsdager." />
      <p className="field-hint">{note.length}/280 tegn. Andre studenter ser navnet ditt og notatet, ikke e-postadressen.</p>
    </div>
    {overview.seeking && note.trim() !== (overview.myNote ?? '') &&
      <button className="secondary-button" disabled={busy} onClick={() => void onSave(note)}>Oppdater notatet</button>}
  </div>
}

function SeekerList({ title, seekers, onInvite, busy }: { title: string; seekers: GroupSeeker[]; onInvite?: (seeker: GroupSeeker) => void; busy?: boolean }) {
  if (seekers.length === 0) return null
  return <>
    <h4 className="groups-subheading">{title}</h4>
    <ul className="seeker-list">{seekers.map((seeker) => <li key={seeker.userId}>
      <div><strong>{seeker.name}</strong>{seeker.email && <small>{seeker.email}</small>}
        {seeker.note ? <p>{seeker.note}</p> : <p className="field-hint">Ingen notat.</p>}</div>
      {onInvite && (seeker.invited ? <span className="group-badge">Invitert</span>
        : <button className="secondary-button" disabled={busy} onClick={() => onInvite(seeker)}>Inviter</button>)}
    </li>)}</ul>
  </>
}

function GroupHeading({ group }: { group: CourseGroup }) {
  return <div className="group-heading"><h4>{group.name}</h4>
    <span className="group-count">{group.members.length} / {group.capacity}</span>
    <span className={`group-badge ${group.open ? 'open' : ''}`}>{group.open ? 'Åpen' : 'Lukket'}</span></div>
}

function MemberList({ group, onRemove, busy }: { group: CourseGroup; onRemove?: (userId: number, name: string) => void; busy: boolean }) {
  if (group.members.length === 0) return <p className="field-hint">Ingen medlemmer ennå.</p>
  return <ul className="member-chips">{group.members.map((member) => <li key={member.userId} title={member.email ?? undefined}>
    {member.name}
    {onRemove && <button className="icon-button" aria-label={`Fjern ${member.name} fra gruppen`} disabled={busy}
      onClick={() => onRemove(member.userId, member.name)}><Icon name="close" /></button>}
  </li>)}</ul>
}

function AddStudent({ students, busy, onAdd }: { students: GroupOverview['ungroupedStudents']; busy: boolean; onAdd: (userId: number) => void }) {
  const [userId, setUserId] = useState('')
  const id = useId()
  return <form className="add-member" onSubmit={(event) => { event.preventDefault(); if (userId) onAdd(Number(userId)) }}>
    <label className="visually-hidden" htmlFor={id}>Legg til student</label>
    <select id={id} value={userId} disabled={busy} onChange={(event) => setUserId(event.target.value)}>
      <option value="">Legg til student …</option>
      {students.map((student) => <option key={student.userId} value={student.userId}>{student.name} ({student.email})</option>)}
    </select>
    <button className="secondary-button" type="submit" disabled={busy || !userId}>Legg til</button>
  </form>
}

function GroupForm({ initial, busy, submitLabel, onSubmit, onCancel }: {
  initial?: GroupInput; busy: boolean; submitLabel: string; onSubmit: (input: GroupInput) => Promise<boolean>; onCancel?: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [capacity, setCapacity] = useState(initial?.capacity ?? 4)
  const [open, setOpen] = useState(initial?.open ?? true)
  return <form className="group-form" onSubmit={async (event) => {
    event.preventDefault()
    if (await onSubmit({ name, capacity, open }) && !initial) { setName(''); setCapacity(4); setOpen(true) }
  }}>
    <div className="form-field"><label>Navn<input value={name} maxLength={100} required disabled={busy} onChange={(event) => setName(event.target.value)} placeholder="Gruppe 1" /></label></div>
    <div className="form-field group-capacity"><label>Plasser<input type="number" min={1} max={50} required value={capacity} disabled={busy} onChange={(event) => setCapacity(Number(event.target.value))} /></label></div>
    <label className="seeking-toggle"><input type="checkbox" checked={open} disabled={busy} onChange={(event) => setOpen(event.target.checked)} />Åpen for påmelding</label>
    <div className="group-actions">
      <button className="primary-button" type="submit" disabled={busy || !name.trim()}>{submitLabel}</button>
      {onCancel && <button className="text-button" type="button" disabled={busy} onClick={onCancel}>Avbryt</button>}
    </div>
  </form>
}
