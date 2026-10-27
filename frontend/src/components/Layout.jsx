import { ChevronDown, FileText, MessageSquare, Receipt, Store, Utensils } from 'lucide-react'
import { Link, useRouter } from '../router'
import { useStores } from '../StoreContext'

const NAV = [
  { to: '/stores', label: '매장', icon: Store, ready: true },
  { to: '/menus', label: '메뉴', icon: Utensils, ready: false },
  { to: '/sales', label: '매출', icon: Receipt, ready: false },
  { to: '/reviews', label: '리뷰', icon: MessageSquare, ready: false },
  { to: '/report', label: '리포트', icon: FileText, ready: false },
]

export default function Layout({ children }) {
  const { path } = useRouter()
  const { stores, selectedId, setSelectedId } = useStores()
  const isActive = (to) => path === to || path.startsWith(to + '/') || (to === '/stores' && path === '/')

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/stores" className="brand">
          <span className="brand-symbol" aria-hidden="true">
            <Store size={22} />
          </span>
          <span className="brand-name">리뷰와 매출</span>
        </Link>
        <span className="account">개발용 계정으로 접속 중</span>
      </header>

      <div className="workspace">
        <aside className="sidebar">
          <label className="store-select">
            <span className="store-select-label">지금 보는 매장</span>
            <span className="store-select-box">
              <select
                value={selectedId ?? ''}
                onChange={(e) => setSelectedId(Number(e.target.value))}
                disabled={stores.length === 0}
              >
                {stores.length === 0 && <option value="">등록된 매장 없음</option>}
                {stores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
              <ChevronDown size={16} aria-hidden="true" />
            </span>
          </label>

          <nav className="nav" aria-label="주요 메뉴">
            {NAV.map(({ to, label, icon: Icon, ready }) => {
              const active = isActive(to)
              return (
                <Link
                  key={to}
                  to={to}
                  className={'nav-item' + (active ? ' is-active' : '')}
                  aria-current={active ? 'page' : undefined}
                >
                  <Icon size={19} className="nav-icon" aria-hidden="true" />
                  <span className="nav-label">{label}</span>
                  {!ready && <span className="nav-soon">준비 중</span>}
                </Link>
              )
            })}
          </nav>
        </aside>

        <main className="main">{children}</main>
      </div>
    </div>
  )
}
