import { useCallback, useEffect, useState } from 'react'
import { CloudAlert, Ellipsis, Plus, Store, Utensils, X } from 'lucide-react'
import { api } from '../api'
import { useStores } from '../StoreContext'
import { Badge, Button, StateBlock } from '../components/ui'

const RULE_LABEL = {
  RULE: '자동 묶음',
  AS_IS: '메뉴명 그대로',
  MANUAL: '직접 옮김',
}
const CATEGORY = ['음료', '주류'] // 목록 맨 뒤로
const num = (n) => n.toLocaleString('ko-KR')
const shortDay = (d) => (d ? d.slice(2).replaceAll('-', '.') : '')

function sortGroups(list) {
  const rank = (g) => (CATEGORY.includes(g.name) ? 1 : 0)
  const sold = (g) => g.menus.reduce((n, m) => n + m.soldQuantity, 0)
  return [...list].sort((a, b) => rank(a) - rank(b) || sold(b) - sold(a) || a.name.localeCompare(b.name))
}

/** 리뷰에서 부르는 이름(별칭) 편집 */
function Aliases({ group, onSave }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const save = async (next) => {
    setBusy(true)
    setError(null)
    try {
      await onSave(group.id, next)
      return true
    } catch (e) {
      setError(e.message)
      return false
    } finally {
      setBusy(false)
    }
  }

  const add = async () => {
    const words = text
      .split(',')
      .map((w) => w.trim())
      .filter((w) => w && w !== group.name && !group.aliases.includes(w))
    if (words.length === 0) {
      setText('')
      return
    }
    if (await save([...group.aliases, ...words])) setText('')
  }

  return (
    <div className="aliases">
      <p className="aliases-label">
        리뷰에서 부르는 이름 <span className="aliases-hint">메뉴군 이름 ‘{group.name}’은 기본으로 찾아요</span>
      </p>
      <div className="alias-editor">
        {group.aliases.map((a) => (
          <span key={a} className="alias-chip">
            {a}
            <button
              type="button"
              className="alias-remove"
              aria-label={`${a} 지우기`}
              disabled={busy}
              onClick={() => save(group.aliases.filter((x) => x !== a))}
            >
              <X size={14} aria-hidden="true" />
            </button>
          </span>
        ))}
        <form
          className="alias-form"
          onSubmit={(e) => {
            e.preventDefault()
            add()
          }}
        >
          <input
            className="alias-input"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={group.aliases.length ? '이름 더하기' : '쉼표로 여러 개 (예: 줄임말, 다른 표기)'}
            aria-label={`${group.name} 별칭 추가`}
            maxLength={100}
            disabled={busy}
          />
          <button type="submit" className="alias-add" disabled={busy || !text.trim()} aria-label="별칭 추가">
            <Plus size={16} aria-hidden="true" />
          </button>
        </form>
      </div>
      {error && <p className="field-error">{error}</p>}
    </div>
  )
}

/** 메뉴 한 줄 + 관리(옮기기·단종·삭제) */
function MenuRow({ menu, groupNames, onUpdate, onDelete }) {
  const [open, setOpen] = useState(false)
  const [target, setTarget] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const run = async (fn) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setOpen(false)
      setConfirmDelete(false)
      setTarget('')
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const canDelete = menu.soldQuantity === 0
  const listId = `groups-${menu.id}`

  return (
    <li className={'menu-row-wrap' + (menu.discontinued ? ' is-off' : '')}>
      <div className="menu-row">
        <span className="menu-main">
          <span className="menu-pos">{menu.posName}</span>
          <span className="menu-meta">
            {menu.soldQuantity > 0 ? `${num(menu.soldQuantity)}개 판매 · 마지막 ${shortDay(menu.lastSoldOn)}` : '판매 기록 없음'}
            {' · '}
            <span className={'menu-rule rule-' + menu.parseRule}>{RULE_LABEL[menu.parseRule] ?? menu.parseRule}</span>
          </span>
        </span>
        <span className="menu-parts">
          {menu.discontinued && <Badge tone="off">단종</Badge>}
          {menu.variant && <Badge>{menu.variant}</Badge>}
          {menu.topping && <Badge>+ {menu.topping}</Badge>}
        </span>
        <button
          type="button"
          className={'icon-btn' + (open ? ' is-on' : '')}
          aria-label={`${menu.posName} 관리`}
          aria-expanded={open}
          onClick={() => {
            setOpen(!open)
            setConfirmDelete(false)
            setError(null)
          }}
        >
          <Ellipsis size={18} aria-hidden="true" />
        </button>
      </div>

      {open && (
        <div className="menu-manage">
          <form
            className="move-form"
            onSubmit={(e) => {
              e.preventDefault()
              if (target.trim()) run(() => onUpdate(menu.id, { groupName: target.trim() }))
            }}
          >
            <label className="move-label" htmlFor={`move-${menu.id}`}>
              다른 메뉴군으로 옮기기
            </label>
            <div className="move-controls">
              <input
                id={`move-${menu.id}`}
                className="input input-sm"
                list={listId}
                value={target}
                onChange={(e) => setTarget(e.target.value)}
                placeholder="메뉴군 이름 (없으면 새로 만들어요)"
                maxLength={50}
                disabled={busy}
              />
              <datalist id={listId}>
                {groupNames.map((n) => (
                  <option key={n} value={n} />
                ))}
              </datalist>
              <Button size="sm" variant="secondary" type="submit" disabled={busy || !target.trim()}>
                옮기기
              </Button>
            </div>
          </form>

          <div className="manage-actions">
            <Button
              size="sm"
              variant="quiet"
              disabled={busy}
              onClick={() => run(() => onUpdate(menu.id, { discontinued: !menu.discontinued }))}
            >
              {menu.discontinued ? '다시 판매 중으로' : '단종으로 표시'}
            </Button>
            {canDelete &&
              (confirmDelete ? (
                <span className="confirm-inline">
                  <span>정말 지울까요?</span>
                  <Button size="sm" variant="danger-solid" disabled={busy} onClick={() => run(() => onDelete(menu.id))}>
                    지우기
                  </Button>
                  <Button size="sm" variant="quiet" disabled={busy} onClick={() => setConfirmDelete(false)}>
                    취소
                  </Button>
                </span>
              ) : (
                <Button size="sm" variant="danger" disabled={busy} onClick={() => setConfirmDelete(true)}>
                  메뉴 지우기
                </Button>
              ))}
          </div>
          <p className="manage-note">
            {canDelete
              ? '판매 기록이 없는 메뉴라 지울 수 있어요.'
              : '판매 기록이 있는 메뉴는 지우지 않고 단종으로 표시해요. 지난 매출 분석에는 그대로 쓰여요.'}
          </p>
          {error && <p className="field-error">{error}</p>}
        </div>
      )}
    </li>
  )
}

function GroupCard({ group, groupNames, onSaveAliases, onUpdateMenu, onDeleteMenu }) {
  const sold = group.menus.reduce((n, m) => n + m.soldQuantity, 0)
  const off = group.menus.filter((m) => m.discontinued).length
  return (
    <li className="card group-card">
      <div className="group-head">
        <div className="group-title-row">
          <h3 className="group-name">{group.name}</h3>
          {CATEGORY.includes(group.name) && <Badge>자동 분류</Badge>}
        </div>
        <p className="group-count">
          POS 메뉴 {group.menus.length}개{off > 0 && ` (단종 ${off})`} · {num(sold)}개 판매
        </p>
      </div>

      <Aliases group={group} onSave={onSaveAliases} />

      <ul className="menu-list">
        {group.menus.map((m) => (
          <MenuRow
            key={m.id}
            menu={m}
            groupNames={groupNames.filter((n) => n !== group.name)}
            onUpdate={onUpdateMenu}
            onDelete={onDeleteMenu}
          />
        ))}
      </ul>
    </li>
  )
}

export default function MenusPage() {
  const { selected } = useStores()
  const [groups, setGroups] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    if (!selected) return Promise.resolve()
    return api
      .listMenuGroups(selected.id)
      .then((list) => {
        setGroups(sortGroups(list))
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [selected?.id])

  useEffect(() => {
    setGroups(null)
    setError(null)
    load()
  }, [load])

  // 수정 후에는 목록을 다시 받아 화면을 서버 상태와 맞춘다
  const saveAliases = async (groupId, aliases) => {
    await api.updateAliases(selected.id, groupId, aliases)
    await load()
  }
  const updateMenu = async (menuId, body) => {
    await api.updateMenu(selected.id, menuId, body)
    await load()
  }
  const deleteMenu = async (menuId) => {
    await api.deleteMenu(selected.id, menuId)
    await load()
  }

  const menuCount = groups ? groups.reduce((n, g) => n + g.menus.length, 0) : 0
  const groupNames = groups ? groups.map((g) => g.name).sort((a, b) => a.localeCompare(b)) : []

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>메뉴</h1>
        </div>
        <p className="page-desc">
          POS 메뉴명을 리뷰에서 부르는 메뉴군으로 묶어요. 리뷰는 메뉴군 이름과 아래에 적은 이름으로 찾아요.
        </p>
      </header>

      {!selected && (
        <div className="card card-state">
          <StateBlock
            icon={Store}
            title="먼저 매장을 등록해 주세요"
            text="메뉴는 매장마다 따로 관리해요."
            action={<Button to="/stores/new">매장 등록하기</Button>}
          />
        </div>
      )}

      {selected && error && !groups && (
        <div className="card card-state">
          <StateBlock icon={CloudAlert} tone="error" title="메뉴를 불러오지 못했어요." text="잠시 후 다시 시도해 주세요." />
        </div>
      )}

      {selected && groups && groups.length === 0 && (
        <div className="card card-state">
          <StateBlock
            icon={Utensils}
            title="아직 메뉴가 없어요"
            text="매출 CSV를 올리면 판매된 메뉴가 자동으로 등록되고, 비슷한 메뉴끼리 묶여요."
            action={<Button to="/sales">매출 올리러 가기</Button>}
          />
        </div>
      )}

      {selected && groups && groups.length > 0 && (
        <>
          <section className="menu-summary">
            <p>
              <strong>{selected.name}</strong>의 POS 메뉴 <strong>{menuCount}개</strong>를 메뉴군{' '}
              <strong>{groups.length}개</strong>로 묶었어요.
            </p>
            <p className="menu-summary-note">
              잘못 묶인 메뉴는 ⋯ 버튼으로 다른 메뉴군에 옮기세요. 같은 이름의 메뉴군으로 옮기면 합쳐져요.
            </p>
          </section>
          <ul className="group-list">
            {groups.map((g) => (
              <GroupCard
                key={g.id}
                group={g}
                groupNames={groupNames}
                onSaveAliases={saveAliases}
                onUpdateMenu={updateMenu}
                onDeleteMenu={deleteMenu}
              />
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
