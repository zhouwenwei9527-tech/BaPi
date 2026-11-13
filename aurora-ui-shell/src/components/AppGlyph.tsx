import { useId } from 'react'
import type { AppDef, AppId } from '../data/apps'

const PALETTE: Record<AppId, [string, string]> = {
  finder: ['#6cc6ff', '#1f63e8'],
  directory: ['#93c5fd', '#2563eb'],
  agent: ['#c4b5fd', '#5b4ae8'],
  approvals: ['#6ee7b7', '#0f9f6e'],
  audit: ['#ffc46b', '#ef5b2b'],
  tools: ['#8ce3ff', '#0e7490'],
  plugins: ['#ffb3d9', '#c0268f'],
  rbac: ['#d9f99d', '#4d7c0f'],
  access: ['#fcd34d', '#d97706'],
  guide: ['#99f6e4', '#0f766e'],
  glass: ['#dbe4f0', '#52627a']
}

/**
 * Bespoke macOS-style app icons. Each icon is a complete squircle with its own
 * layered gradient, gloss and glyph, so the dock/desktop do not need generic
 * line icons on top of a gradient tile.
 */
export function AppGlyph({ app, size = 56, className = '' }: {
  app: AppDef
  size?: number
  className?: string
}) {
  const raw = useId().replace(/[^a-zA-Z0-9_-]/g, '')
  const bg = `bg-${raw}`
  const gloss = `gloss-${raw}`
  const inner = `inner-${raw}`
  const [from, to] = PALETTE[app.id]

  return (
    <svg
      className={`app-glyph ${className}`}
      width={size}
      height={size}
      viewBox="0 0 64 64"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={bg} x1="10" y1="3" x2="54" y2="61" gradientUnits="userSpaceOnUse">
          <stop stopColor={from} />
          <stop offset="1" stopColor={to} />
        </linearGradient>
        <linearGradient id={gloss} x1="10" y1="4" x2="42" y2="36" gradientUnits="userSpaceOnUse">
          <stop stopColor="#ffffff" stopOpacity="0.46" />
          <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
        </linearGradient>
        <radialGradient id={inner} cx="0" cy="0" r="1"
          gradientTransform="translate(20 11) rotate(42) scale(52)">
          <stop stopColor="#ffffff" stopOpacity="0.3" />
          <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
        </radialGradient>
      </defs>

      <rect x="2" y="2" width="60" height="60" rx="15.5" fill={`url(#${bg})`} />
      <rect x="2.5" y="2.5" width="59" height="59" rx="15" fill="none"
        stroke="#ffffff" strokeOpacity="0.18" />
      <path d="M2 18C2 9.16 9.16 2 18 2h28c8.84 0 16 7.16 16 16v3H2v-3Z"
        fill={`url(#${gloss})`} />
      <rect x="9" y="9" width="46" height="46" rx="11" fill={`url(#${inner})`} opacity="0.7" />
      <path d="M2 46c0 8.84 7.16 16 16 16h28c8.84 0 16-7.16 16-16v-3H2v3Z"
        fill="#000000" opacity="0.08" />

      {artwork(app.id)}
    </svg>
  )
}

function artwork(id: AppId) {
  switch (id) {
    case 'finder':
      return (
        <g>
          <path d="M2 22C2 11.5 11.5 2 22 2h4v60h-4C11.5 62 2 52.5 2 42V22Z" fill="#ffffff" opacity="0.12" />
          <path d="M32 4v56" stroke="#ffffff" strokeOpacity="0.18" />
          <ellipse cx="23" cy="27" rx="2.2" ry="5.2" fill="#ffffff" />
          <ellipse cx="41" cy="27" rx="2.2" ry="5.2" fill="#ffffff" />
          <path d="M21 38.5c3.6 5.4 18.4 5.4 22 0" fill="none" stroke="#ffffff"
            strokeWidth="2.8" strokeLinecap="round" />
          <circle cx="49.5" cy="14" r="2" fill="#ffffff" opacity="0.7" />
        </g>
      )
    case 'agent':
      return (
        <g>
          <g opacity="0.58" stroke="#ffffff" strokeWidth="1.2" fill="none">
            <ellipse cx="32" cy="34" rx="22" ry="8" transform="rotate(-28 32 34)" />
            <ellipse cx="32" cy="34" rx="22" ry="8" transform="rotate(28 32 34)" />
          </g>
          <path d="M32 12.5c1.7 10.4 5.1 14.2 14.5 15.7-9.4 1.5-12.8 5.3-14.5 15.7-1.7-10.4-5.1-14.2-14.5-15.7 9.4-1.5 12.8-5.3 14.5-15.7Z"
            fill="#ffffff" />
          <circle cx="47" cy="18.5" r="2.4" fill="#ffffff" opacity="0.92" />
          <circle cx="17.5" cy="46" r="1.6" fill="#ffffff" opacity="0.72" />
        </g>
      )
    case 'approvals':
      return (
        <g>
          <circle cx="32" cy="32" r="17.5" fill="#ffffff" opacity="0.18" />
          <circle cx="32" cy="32" r="14.5" fill="#ffffff" />
          <path d="M24.5 32.5l5 5 10-11" fill="none" stroke="#0f9f6e"
            strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="49" cy="17.5" r="2" fill="#ffffff" opacity="0.72" />
        </g>
      )
    case 'audit':
      return (
        <g>
          <circle cx="32" cy="32" r="17" fill="#ffffff" opacity="0.16" />
          <circle cx="32" cy="32" r="14" fill="#ffffff" />
          <path d="M32 21.5V32l7 4.2" fill="none" stroke="#ef5b2b"
            strokeWidth="2.8" strokeLinecap="round" />
          <path d="M18.5 19.5a18 18 0 0 1 27 0" fill="none" stroke="#ffffff"
            strokeWidth="2.4" strokeLinecap="round" opacity="0.6" />
          <circle cx="32" cy="32" r="2.1" fill="#ef5b2b" />
        </g>
      )
    case 'tools':
      return (
        <g>
          <g stroke="#ffffff" strokeWidth="2.8" strokeLinecap="round" opacity="0.42">
            <path d="M32 12v7M32 45v7M12 32h7M45 32h7M18 18l5 5M41 41l5 5M46 18l-5 5M23 41l-5 5" />
          </g>
          <circle cx="32" cy="32" r="11.5" fill="none" stroke="#ffffff" strokeWidth="3" opacity="0.62" />
          <path d="M44 15.5a8.6 8.6 0 0 0-10.4 10.6L17.5 42.2a4.4 4.4 0 1 0 6.3 6.3L40 32.4A8.6 8.6 0 0 0 50.5 22l-5.2 2.2-3.5-3.5 2.2-5.2Z"
            fill="#ffffff" />
          <circle cx="32" cy="32" r="3.4" fill="#0e7490" opacity="0.6" />
        </g>
      )
    case 'plugins':
      return (
        <g>
          <path d="M16 24h8v-3a4 4 0 1 1 8 0v3h8v8h3a4 4 0 1 1 0 8h-3v8h-8v-3a4 4 0 1 0-8 0v3h-8v-8h-3a4 4 0 1 1 0-8h3v-8Z"
            fill="#ffffff" opacity="0.32" transform="translate(4 4)" />
          <path d="M14 22h10v-3a4 4 0 1 1 8 0v3h10v10h3a4 4 0 1 1 0 8h-3v10H32v-3a4 4 0 1 0-8 0v3H14V40h-3a4 4 0 1 1 0-8h3V22Z"
            fill="#ffffff" opacity="0.95" />
          <circle cx="32" cy="31" r="2.4" fill="#c0268f" opacity="0.55" />
        </g>
      )
    case 'rbac':
      return (
        <g>
          <path d="M32 10.5 50 17.5v13.2c0 10.6-7.3 18.2-18 22.3-10.7-4.1-18-11.7-18-22.3V17.5l18-7Z"
            fill="#ffffff" opacity="0.95" />
          <path d="M32 10.5 50 17.5v13.2c0 10.6-7.3 18.2-18 22.3" fill="none"
            stroke="#ffffff" strokeOpacity="0.55" />
          <path d="M32 24.5a5.2 5.2 0 0 0-3.1 9.4v5.6h6.2v-5.6a5.2 5.2 0 0 0-3.1-9.4Z"
            fill="#4d7c0f" />
          <circle cx="32" cy="29.5" r="1.7" fill="#ffffff" opacity="0.75" />
        </g>
      )
    case 'glass':
      return (
        <g>
          <path d="M32 11.5c8.3 10.4 14.5 16.6 14.5 24.5a14.5 14.5 0 0 1-29 0c0-7.9 6.2-14.1 14.5-24.5Z"
            fill="#ffffff" opacity="0.92" />
          <path d="M26.5 28.5c-3 5-2.7 10 .8 14" fill="none" stroke="#7dd3fc"
            strokeWidth="2.2" strokeLinecap="round" opacity="0.8" />
          <path d="M38 27.5c2.2 3 2.7 6.5 1.5 9.5" fill="none" stroke="#f9a8d4"
            strokeWidth="2" strokeLinecap="round" opacity="0.8" />
          <circle cx="26.5" cy="23.5" r="2.1" fill="#ffffff" />
        </g>
      )
    case 'directory':
      return (
        <g>
          <rect x="13" y="18" width="38" height="28" rx="6" fill="#ffffff" opacity="0.95" />
          <circle cx="23" cy="29" r="5.2" fill="#2563eb" opacity="0.85" />
          <path d="M16 40c1.2-4.6 4-6.8 7-6.8s5.8 2.2 7 6.8" fill="#2563eb" opacity="0.5" />
          <rect x="34" y="24" width="12" height="2.6" rx="1.3" fill="#2563eb" opacity="0.55" />
          <rect x="34" y="30" width="9" height="2.2" rx="1.1" fill="#2563eb" opacity="0.35" />
          <circle cx="47" cy="41" r="6.2" fill="#ffffff" />
          <path d="M47 37.8v6.4M43.8 41h6.4" stroke="#0f9f6e" strokeWidth="2.2" strokeLinecap="round" />
        </g>
      )
    case 'access':
      return (
        <g>
          <circle cx="32" cy="32" r="15" fill="#ffffff" opacity="0.16" />
          <path d="M25 32.5l4.5 4.5 10-10" fill="none" stroke="#ffffff" strokeWidth="3"
            strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="20" cy="22" r="3.1" fill="none" stroke="#ffffff" strokeWidth="2" />
          <path d="M23 22h14M33 22v8" fill="none" stroke="#ffffff" strokeWidth="2"
            strokeLinecap="round" opacity="0.8" />
          <path d="M46 43v8M42 47h8" stroke="#ffffff" strokeWidth="2.4"
            strokeLinecap="round" />
        </g>
      )
    case 'guide':
      return (
        <g>
          <path d="M19 24h10M35 24h10M24 34v10M40 34v10" fill="none" stroke="#ffffff"
            strokeWidth="2" strokeLinecap="round" opacity="0.55" />
          <circle cx="17" cy="24" r="6" fill="#ffffff" opacity="0.95" />
          <circle cx="47" cy="24" r="6" fill="#ffffff" opacity="0.7" />
          <circle cx="32" cy="45" r="6" fill="#ffffff" opacity="0.85" />
          <path d="M15 22.5l1.5 1.5 3-3" fill="none" stroke="#0f766e" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round" />
          <path d="M45 22.5l1.5 1.5 3-3" fill="none" stroke="#0f766e" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round" opacity="0.7" />
          <path d="M30 43.5l1.5 1.5 3-3" fill="none" stroke="#0f766e" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round" />
        </g>
      )
    default:
      return null
  }
}
