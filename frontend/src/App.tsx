import { useState } from 'react'
import DemoApp from './DemoApp'
import { CourseWorkspace } from './components/CourseWorkspace'
import './App.css'
import './courses.css'
import './learning.css'

function App() {
  const [showDemo, setShowDemo] = useState(false)

  return showDemo
    ? <DemoApp onOpenCourses={() => setShowDemo(false)} />
    : <CourseWorkspace onOpenDemo={() => setShowDemo(true)} />
}

export default App
