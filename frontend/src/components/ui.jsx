import { ChevronRight } from 'lucide-react'
import { Link } from '../router'

/** 버튼: variant = primary | secondary | quiet, size = md(48) | sm(40) */
export function Button({ variant = 'primary', size = 'md', icon: Icon, to, children, className = '', ...rest }) {
  const cls = `btn btn-${variant} btn-${size} ${className}`.trim()
  const inner = (
    <>
      {Icon && <Icon size={18} aria-hidden="true" />}
      <span>{children}</span>
    </>
  )
  if (to) {
    return (
      <Link to={to} className={cls} {...rest}>
        {inner}
      </Link>
    )
  }
  return (
    <button type="button" className={cls} {...rest}>
      {inner}
    </button>
  )
}

/** 배지: tone = complete | pending */
export function Badge({ tone = 'pending', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

/** 카드 안 제목: "01  카카오맵에서 가게 찾기" */
export function SectionHeading({ num, title, children }) {
  return (
    <div className="section-heading">
      <h2>
        {num && <span className="section-num">{num}</span>}
        {title}
      </h2>
      {children}
    </div>
  )
}

/** 매장 등록 3단계 진행 표시 */
const STEPS = ['가게 찾기', '가게 확인', '정보 확인']
export function StepsBar({ active }) {
  return (
    <ol className="steps" aria-label="등록 단계">
      {STEPS.map((label, i) => {
        const n = i + 1
        const on = n === active
        return (
          <li key={label} className={'step' + (on ? ' is-active' : '')} aria-current={on ? 'step' : undefined}>
            <span className="step-num">{String(n).padStart(2, '0')}</span>
            <span className="step-label">{label}</span>
            {n < STEPS.length && <ChevronRight size={16} className="step-chevron" aria-hidden="true" />}
          </li>
        )
      })}
    </ol>
  )
}

/** 빈 상태·오류·준비 중 안내 */
export function StateBlock({ icon: Icon, tone = 'default', kicker, title, text, action }) {
  return (
    <div className={`state state-${tone}`}>
      <span className="state-symbol" aria-hidden="true">
        <Icon size={26} />
      </span>
      <div className="state-text">
        {kicker && <p className="state-kicker">{kicker}</p>}
        <p className="state-title">{title}</p>
        {text && <p className="state-desc">{text}</p>}
      </div>
      {action}
    </div>
  )
}

/** 라벨-값 한 줄 (주소, 전화번호 등) */
export function DetailRow({ label, children }) {
  return (
    <div className="detail">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}
