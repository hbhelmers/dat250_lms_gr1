export type CourseRole = 'TEACHER' | 'STUDENT'
export type Institution = 'HVL' | 'UIB'

export type StoredCourse = {
  id: number
  title: string
  description: string | null
  semester: string
  role: CourseRole
  institution: Institution | null
  courseCode: string | null
  selfEnrollment: boolean
  officialUrl: string | null
}

export type CatalogCourse = Omit<StoredCourse, 'role' | 'selfEnrollment'> & { membershipRole: CourseRole | null }

export type CourseMember = { userId: number; name: string; email: string; role: CourseRole }
export type CourseActivity = {
  id: string; type: 'QUIZ_SUBMITTED' | 'ASSIGNMENT_SUBMITTED' | 'GRADE_PUBLISHED'; occurredAt: string
  moduleId: number; itemId: number; itemTitle: string; studentName: string | null; studentEmail: string | null
  version: number; late: boolean
}

export type LearningItem = {
  id: number; moduleId: number; type: 'PAGE' | 'QUIZ' | 'ASSIGNMENT'; title: string; body: string; position: number
  published: boolean; visibleFrom: string | null; visibleUntil: string | null
}
export type LearningModule = { id: number; title: string; position: number; items: LearningItem[] }
export type PageInput = Pick<LearningItem, 'title' | 'body' | 'published' | 'visibleFrom' | 'visibleUntil'>
export type QuizQuestionInput = { prompt: string; options: string[]; correctOption: number; hint: string }
export type QuizInput = PageInput & { maxAttempts: number; deadline: string | null; resultsVisible: boolean; questions: QuizQuestionInput[] }
export type QuizQuestion = { id: number; prompt: string; options: string[]; correctOption: number | null; hint: string | null }
export type QuizAttempt = {
  id: number; number: number; submittedAt: string; score: number | null; total: number; studentName: string | null; studentEmail: string | null
  answers: { questionId: number; selectedOption: number; correct: boolean; correctOption: number; hint: string }[]
}
export type Quiz = { content: LearningItem; maxAttempts: number; deadline: string | null; resultsVisible: boolean; questionsLocked: boolean; questions: QuizQuestion[]; attempts: QuizAttempt[] }

export type AssignmentGrade = 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'PASSED' | 'FAILED'
export type GradeInput = { grade: AssignmentGrade; feedback: string; published: boolean }
export type AssignmentInput = PageInput & { deadline: string | null; acceptLate: boolean }
export type AssignmentSubmission = {
  id: number; version: number; submittedAt: string; late: boolean; filename: string; fileSize: number
  studentId: number | null; studentName: string | null; studentEmail: string | null
  grade: AssignmentGrade | null; feedback: string | null; gradePublished: boolean; gradedAt: string | null
}
export type Assignment = { content: LearningItem; deadline: string | null; acceptLate: boolean; submissions: AssignmentSubmission[] }

export type GroupPerson = { userId: number; name: string; email: string | null }
export type CourseGroup = { id: number; name: string; capacity: number; open: boolean; members: GroupPerson[] }
export type GroupSeeker = { userId: number; name: string; email: string | null; note: string; since: string; invited: boolean }
export type GroupInvitation = { id: number; groupId: number; groupName: string; invitedBy: string }
export type GroupOverview = {
  teacher: boolean; myUserId: number; myGroupId: number | null; seeking: boolean; myNote: string | null
  groups: CourseGroup[]; seekers: GroupSeeker[]; invitations: GroupInvitation[]; ungroupedStudents: GroupPerson[]
}
export type GroupInput = { name: string; capacity: number; open: boolean }

export type CreateCourseInput = {
  title: string
  description: string
  semester: string
  institution: Institution
  courseCode: string
  selfEnrollment: boolean
}

export type Session = { id: number; name: string; email: string; preferredRole: CourseRole }

export type RegistrationInput = { name: string; email: string; password: string; preferredRole: CourseRole; includeExamples: boolean }
export type DemoAccess = { enabled: boolean; teacherEmail: string | null; studentEmail: string | null; password: string | null }

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...options,
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...options.headers },
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Får ikke kontakt med serveren. Prøv igjen om litt.')
  }

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const message = response.status === 401 ? 'Logg inn for å fortsette.'
      : response.status === 403 ? problem?.detail ?? 'Handlingen kunne ikke bekreftes. Last siden på nytt og prøv igjen.'
      : response.status === 503 ? problem?.detail ?? 'Tjenesten er utilgjengelig. Prøv igjen om litt.'
      : response.status >= 500 ? 'Serveren er utilgjengelig akkurat nå. Prøv igjen om litt.'
      : problem?.detail ?? 'Noe gikk galt. Prøv igjen.'
    throw new ApiError(response.status, message, problem?.fieldErrors ?? {})
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function csrfHeaders() {
  const csrf = await request<{ headerName: string; token: string }>('/api/csrf')
  return { [csrf.headerName]: csrf.token }
}

export function getSession(signal?: AbortSignal) {
  return request<Session>('/api/session', { signal })
}

export function getDemoAccess(signal?: AbortSignal) {
  return request<DemoAccess>('/api/demo-access', { signal })
}

export function getCourses(signal?: AbortSignal) {
  return request<StoredCourse[]>('/api/courses', { signal })
}

export function getCourseCatalog(institution: Institution, signal?: AbortSignal) {
  return request<CatalogCourse[]>(`/api/courses/catalog?institution=${institution}`, { signal })
}

export function enrollInCourse(id: number) {
  return contentWrite<StoredCourse>(`/api/courses/${id}/enroll`, 'POST')
}

export function getCourse(id: number, signal?: AbortSignal) {
  return request<StoredCourse>(`/api/courses/${id}`, { signal })
}

export function getCourseMembers(id: number, signal?: AbortSignal) {
  return request<CourseMember[]>(`/api/courses/${id}/members`, { signal })
}

export function getCourseActivity(courseId: number, signal?: AbortSignal) {
  return request<CourseActivity[]>(`/api/courses/${courseId}/activity`, { signal })
}

function groupsPath(courseId: number, rest = '') {
  return `/api/courses/${courseId}/groups${rest}`
}
export function getGroups(courseId: number, signal?: AbortSignal) {
  return request<GroupOverview>(groupsPath(courseId), { signal })
}
export function saveGroup(courseId: number, input: GroupInput, groupId?: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, groupId === undefined ? '' : `/${groupId}`), groupId === undefined ? 'POST' : 'PUT', input)
}
export function deleteGroup(courseId: number, groupId: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, `/${groupId}`), 'DELETE')
}
export function addGroupMember(courseId: number, groupId: number, userId: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, `/${groupId}/members`), 'POST', { userId })
}
export function removeGroupMember(courseId: number, groupId: number, userId: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, `/${groupId}/members/${userId}`), 'DELETE')
}
export function joinGroup(courseId: number, groupId: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, `/${groupId}/join`), 'POST')
}
export function setSeekingGroup(courseId: number, note: string | null) {
  return note === null ? contentWrite<GroupOverview>(groupsPath(courseId, '/seeking'), 'DELETE')
    : contentWrite<GroupOverview>(groupsPath(courseId, '/seeking'), 'PUT', { note })
}
export function inviteToGroup(courseId: number, groupId: number, userId: number) {
  return contentWrite<GroupOverview>(groupsPath(courseId, `/${groupId}/invitations`), 'POST', { userId })
}
export function answerGroupInvitation(courseId: number, invitationId: number, accept: boolean) {
  return accept ? contentWrite<GroupOverview>(groupsPath(courseId, `/invitations/${invitationId}/accept`), 'POST')
    : contentWrite<GroupOverview>(groupsPath(courseId, `/invitations/${invitationId}`), 'DELETE')
}

export function getModules(courseId: number, signal?: AbortSignal) {
  return request<LearningModule[]>(`/api/courses/${courseId}/modules`, { signal })
}

async function contentWrite<T>(path: string, method: 'POST' | 'PUT' | 'DELETE', data?: unknown) {
  return request<T>(path, {
    method,
    headers: { ...await csrfHeaders(), ...(data === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: data === undefined ? undefined : JSON.stringify(data),
  })
}

export function saveModule(courseId: number, title: string, moduleId?: number) {
  return contentWrite<LearningModule>(`/api/courses/${courseId}/modules${moduleId === undefined ? '' : `/${moduleId}`}`,
    moduleId === undefined ? 'POST' : 'PUT', { title })
}

export function deleteModule(courseId: number, moduleId: number) {
  return contentWrite<void>(`/api/courses/${courseId}/modules/${moduleId}`, 'DELETE')
}

export function setModuleOrder(courseId: number, ids: number[]) {
  return contentWrite<LearningModule[]>(`/api/courses/${courseId}/modules/order`, 'PUT', { ids })
}

export function saveLearningPage(courseId: number, moduleId: number, input: PageInput, pageId?: number) {
  return contentWrite<LearningItem>(`/api/courses/${courseId}/modules/${moduleId}/pages${pageId === undefined ? '' : `/${pageId}`}`,
    pageId === undefined ? 'POST' : 'PUT', input)
}

export function deleteLearningPage(courseId: number, moduleId: number, pageId: number) {
  return contentWrite<void>(`/api/courses/${courseId}/modules/${moduleId}/pages/${pageId}`, 'DELETE')
}

export function setPageOrder(courseId: number, moduleId: number, ids: number[]) {
  return contentWrite<LearningModule>(`/api/courses/${courseId}/modules/${moduleId}/items/order`, 'PUT', { ids })
}

function quizPath(courseId: number, moduleId: number, quizId?: number) {
  return `/api/courses/${courseId}/modules/${moduleId}/quizzes${quizId === undefined ? '' : `/${quizId}`}`
}
export function getQuiz(courseId: number, moduleId: number, quizId: number, signal?: AbortSignal) {
  return request<Quiz>(quizPath(courseId, moduleId, quizId), { signal })
}
export function saveQuiz(courseId: number, moduleId: number, input: QuizInput, quizId?: number) {
  return contentWrite<Quiz>(quizPath(courseId, moduleId, quizId), quizId === undefined ? 'POST' : 'PUT', input)
}
export function submitQuiz(courseId: number, moduleId: number, quizId: number, submissionId: string, answers: { questionId: number; option: number }[]) {
  return contentWrite<QuizAttempt>(`${quizPath(courseId, moduleId, quizId)}/attempts`, 'POST', { submissionId, answers })
}
export function getQuizResults(courseId: number, moduleId: number, quizId: number, signal?: AbortSignal) {
  return request<QuizAttempt[]>(`${quizPath(courseId, moduleId, quizId)}/attempts`, { signal })
}
export function deleteLearningItem(courseId: number, moduleId: number, itemId: number) {
  return contentWrite<void>(`/api/courses/${courseId}/modules/${moduleId}/items/${itemId}`, 'DELETE')
}

function assignmentPath(courseId: number, moduleId: number, assignmentId?: number) {
  return `/api/courses/${courseId}/modules/${moduleId}/assignments${assignmentId === undefined ? '' : `/${assignmentId}`}`
}
export function getAssignment(courseId: number, moduleId: number, assignmentId: number, signal?: AbortSignal) {
  return request<Assignment>(assignmentPath(courseId, moduleId, assignmentId), { signal })
}
export function saveAssignment(courseId: number, moduleId: number, input: AssignmentInput, assignmentId?: number) {
  return contentWrite<Assignment>(assignmentPath(courseId, moduleId, assignmentId), assignmentId === undefined ? 'POST' : 'PUT', input)
}
export async function submitAssignment(courseId: number, moduleId: number, assignmentId: number, submissionId: string, file: File) {
  const body = new FormData()
  body.set('submissionId', submissionId); body.set('file', file)
  return request<AssignmentSubmission>(`${assignmentPath(courseId, moduleId, assignmentId)}/submissions`, {
    method: 'POST', headers: await csrfHeaders(), body,
  })
}
export function gradeSubmission(courseId: number, moduleId: number, assignmentId: number, submissionId: number, input: GradeInput) {
  return contentWrite<AssignmentSubmission>(`${assignmentPath(courseId, moduleId, assignmentId)}/submissions/${submissionId}/grade`, 'PUT', input)
}
export function submissionDownloadUrl(courseId: number, moduleId: number, assignmentId: number, submissionId: number) {
  return `${assignmentPath(courseId, moduleId, assignmentId)}/submissions/${submissionId}/file`
}

export async function addStudent(id: number, email: string) {
  return request<CourseMember>(`/api/courses/${id}/students`, {
    method: 'POST',
    headers: { ...await csrfHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
}

export async function updateCourse(id: number, course: CreateCourseInput) {
  return request<StoredCourse>(`/api/courses/${id}`, {
    method: 'PUT',
    headers: { ...await csrfHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(course),
  })
}

export async function register(input: RegistrationInput) {
  return request<Session>('/api/register', {
    method: 'POST',
    headers: { ...await csrfHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export async function login(email: string, password: string) {
  await request<void>('/api/login', {
    method: 'POST',
    headers: { ...await csrfHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: email.trim(), password }),
  })
  return getSession()
}

export async function logout() {
  return request<void>('/api/logout', { method: 'POST', headers: await csrfHeaders() })
}

export async function createCourse(course: CreateCourseInput) {
  return request<StoredCourse>('/api/courses', {
    method: 'POST',
    headers: { ...await csrfHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(course),
  })
}
