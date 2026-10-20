export type CourseRole = 'TEACHER' | 'STUDENT'

export type StoredCourse = {
  id: number
  title: string
  description: string | null
  semester: string
  role: CourseRole
}

export type CourseMember = { userId: number; name: string; email: string; role: CourseRole }

export type LearningPage = {
  id: number; moduleId: number; type: 'PAGE'; title: string; body: string; position: number
  published: boolean; visibleFrom: string | null; visibleUntil: string | null
}
export type LearningModule = { id: number; title: string; position: number; items: LearningPage[] }
export type PageInput = Pick<LearningPage, 'title' | 'body' | 'published' | 'visibleFrom' | 'visibleUntil'>

export type CreateCourseInput = {
  title: string
  description: string
  semester: string
}

export type Session = { id: number; name: string; email: string }

export type RegistrationInput = { name: string; email: string; password: string }

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

export function getCourses(signal?: AbortSignal) {
  return request<StoredCourse[]>('/api/courses', { signal })
}

export function getCourse(id: number, signal?: AbortSignal) {
  return request<StoredCourse>(`/api/courses/${id}`, { signal })
}

export function getCourseMembers(id: number, signal?: AbortSignal) {
  return request<CourseMember[]>(`/api/courses/${id}/members`, { signal })
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
  return contentWrite<LearningPage>(`/api/courses/${courseId}/modules/${moduleId}/pages${pageId === undefined ? '' : `/${pageId}`}`,
    pageId === undefined ? 'POST' : 'PUT', input)
}

export function deleteLearningPage(courseId: number, moduleId: number, pageId: number) {
  return contentWrite<void>(`/api/courses/${courseId}/modules/${moduleId}/pages/${pageId}`, 'DELETE')
}

export function setPageOrder(courseId: number, moduleId: number, ids: number[]) {
  return contentWrite<LearningModule>(`/api/courses/${courseId}/modules/${moduleId}/pages/order`, 'PUT', { ids })
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
