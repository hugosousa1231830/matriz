import type { CSSProperties } from 'react'
import { Link } from 'react-router'
import { ROLE_LABELS, type Project } from '../api/projects'
import { ProjectStatusBadge } from './Badges'
import { TaskSummary } from './TaskSummary'
import { usePressNavigate } from './usePressNavigate'

/** Decorative stripe colours; the same project always gets the same one. */
const ACCENTS = ['#5b5bd6', '#3b82f6', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#06b6d4', '#ef4444']

function accentColour(key: string): string {
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.codePointAt(0)!) >>> 0
  return ACCENTS[hash % ACCENTS.length]
}

interface Props {
  project: Project
  /** Heading level of the project name, to fit the page outline. */
  headingLevel?: 'h2' | 'h3'
  /** Shows the Active/Archived pill and the description excerpt. */
  detailed?: boolean
}

/** A project in a card grid (inside a `<ul>`): the whole card links to the project. */
export function ProjectCard({ project, headingLevel: Heading = 'h3', detailed = false }: Props) {
  const to = `/projects/${project.id}`
  const { pressed, onClick } = usePressNavigate(to)
  const style = { '--project-accent': accentColour(project.id) } as CSSProperties
  return (
    <li>
      <article className={`card card-hover project-card${pressed ? ' is-pressed' : ''}`} style={style}>
        <div className="project-card-head">
          <Heading className="project-card-title">
            <Link className="stretched-link" to={to} onClick={onClick}>
              {project.name}
            </Link>
          </Heading>
          {detailed && <ProjectStatusBadge archived={project.status === 'ARCHIVED'} />}
        </div>
        {detailed && project.description && <p className="project-card-description">{project.description}</p>}
        <p className="muted project-card-meta">
          {project.myRole ? `Your role: ${ROLE_LABELS[project.myRole]}` : 'Administrator access'} ·{' '}
          {project.memberCount} {project.memberCount === 1 ? 'member' : 'members'}
        </p>
        <TaskSummary
          counts={project.taskCounts}
          percent={project.progressPercent}
          label={`Tasks done in ${project.name}`}
          compact
        />
      </article>
    </li>
  )
}
