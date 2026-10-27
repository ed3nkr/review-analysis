import { useEffect, useRef, useState } from 'react'
import { ArrowDown, ArrowRight, ArrowUpRight, MapPin, Search } from 'lucide-react'
import { api } from '../api'
import { useRouter } from '../router'
import { useStores } from '../StoreContext'
import { Badge, Button, DetailRow, SectionHeading, StateBlock, StepsBar } from '../components/ui'

const EMPTY = { name: '', category: '', address: '', phone: '', slotCutoff: '16:00' }

const toForm = (s) => ({
  name: s.name ?? '',
  category: s.category ?? '',
  address: s.address ?? '',
  phone: s.phone ?? '',
  slotCutoff: s.slotCutoff ? s.slotCutoff.slice(0, 5) : '16:00',
})

const nullIfBlank = (v) => (v.trim() === '' ? null : v.trim())

/* ---------- 01 가게 찾기 ---------- */
function SearchCard({ query, setQuery, onSearch, searching, error, results, onPick }) {
  return (
    <section className="card">
      <SectionHeading num="01" title="카카오맵에서 가게 찾기" />

      <form className="search" onSubmit={onSearch} role="search">
        <label className="field-label" htmlFor="place-query">
          가게 이름이나 동네 이름
        </label>
        <div className="search-controls">
          <div className="search-box">
            <Search size={20} aria-hidden="true" />
            <input
              id="place-query"
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="예: 백소정 당산"
              autoComplete="off"
            />
          </div>
          <Button type="submit" disabled={searching}>
            {searching ? '찾는 중' : '찾기'}
          </Button>
        </div>
        {error && <p className="field-error">{error}</p>}
      </form>

      {results && results.length === 0 && (
        <StateBlock
          icon={Search}
          title="검색 결과가 없어요."
          text="동네 이름을 붙이거나 줄여서 다시 찾아 보세요."
        />
      )}

      {results && results.length > 0 && (
        <div className="results">
          <p className="results-heading">검색 결과 {results.length}개</p>
          <ul className="results-list">
            {results.map((r) => (
              <li key={r.kakaoId} className="result">
                <div className="result-info">
                  <p className="result-identity">
                    <span className="result-name">{r.name}</span>
                    {r.category && <Badge>{r.category}</Badge>}
                  </p>
                  <p className="result-contact">
                    <span>{r.roadAddress || r.address}</span>
                    {r.phone && <span>{r.phone}</span>}
                  </p>
                </div>
                <Button variant="secondary" size="sm" onClick={() => onPick(r)}>
                  이 가게 확인
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

/* ---------- 02 안내 띠 (아직 고르지 않았을 때) ---------- */
function ConfirmationBridge() {
  return (
    <section className="bridge" aria-label="다음 단계 안내">
      <span className="bridge-num">02</span>
      <div className="bridge-text">
        <p className="bridge-title">이곳이 맞나요?</p>
        <p className="bridge-desc">검색 결과에서 가게를 고르면, 주소와 전화번호를 한 번 더 확인해요.</p>
      </div>
      <div className="bridge-next">
        <p className="bridge-next-title">네, 맞아요 → 03</p>
        <p className="bridge-next-desc">아래 정보가 채워져요</p>
      </div>
    </section>
  )
}

/* ---------- 02 가게 확인 ---------- */
function ConfirmationCard({ place, onConfirm, onBack }) {
  const ref = useRef(null)
  useEffect(() => {
    ref.current?.focus()
  }, [])
  return (
    <section className="confirm" ref={ref} tabIndex={-1} aria-labelledby="confirm-q">
      <div className="confirm-question">
        <p className="eyebrow">02 · 가게 확인</p>
        <p className="confirm-q" id="confirm-q">
          이곳이
          <br />
          맞나요?
        </p>
        <p className="confirm-hint">선택한 가게의 주소와 전화번호를 확인해 주세요.</p>
      </div>

      <div className="confirm-store">
        <div>
          <p className="confirm-source">카카오맵에서 고른 가게</p>
          <h2 className="confirm-name">{place.name}</h2>
        </div>
        <hr className="divider" />
        <dl className="details details-loose">
          <DetailRow label="도로명">{place.roadAddress || '없음'}</DetailRow>
          <DetailRow label="지번">{place.address || '없음'}</DetailRow>
          <DetailRow label="전화번호">{place.phone || '없음'}</DetailRow>
          <DetailRow label="업종">{place.category || '없음'}</DetailRow>
        </dl>
        {place.placeUrl && (
          <a className="map-link" href={place.placeUrl} target="_blank" rel="noreferrer">
            <MapPin size={17} aria-hidden="true" />
            <span>카카오맵에서 위치 보기</span>
            <ArrowUpRight size={15} aria-hidden="true" />
          </a>
        )}
        <div className="actions">
          <Button icon={ArrowRight} onClick={onConfirm}>
            네, 맞아요
          </Button>
          <Button variant="secondary" onClick={onBack}>
            다른 곳 고르기
          </Button>
        </div>
      </div>
    </section>
  )
}

function NextStepPreview({ place }) {
  return (
    <div className="next-step">
      <p className="flow-connector">
        <ArrowDown size={18} aria-hidden="true" />
        <span>‘네, 맞아요’ 다음 단계</span>
      </p>
      <section className="card">
        <SectionHeading num="03" title="매장 정보 확인" />
        <p className="card-desc">‘네, 맞아요’를 누르면 단계 3 입력 폼에 정보가 채워져요. 저장하기 전에 내용을 확인하고 고칠 수 있어요.</p>
        <dl className="details prefill">
          <DetailRow label="매장 이름">{place.name}</DetailRow>
          <DetailRow label="주소">{place.roadAddress || place.address || '없음'}</DetailRow>
        </dl>
      </section>
    </div>
  )
}

/* ---------- 03 매장 정보 확인 ---------- */
function StoreForm({ form, setForm, filled, onSave, saving, error, isEdit, onCancel, formRef }) {
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))
  return (
    <form className="card" onSubmit={onSave} ref={formRef}>
      <SectionHeading num="03" title="매장 정보 확인">
        {filled && <Badge tone="complete">가게 확인 후 자동 입력</Badge>}
      </SectionHeading>

      <div className="form-grid">
        <label className="field">
          <span className="field-label">매장 이름</span>
          <input value={form.name} onChange={set('name')} required maxLength={100} />
        </label>
        <label className="field">
          <span className="field-label">업종</span>
          <input value={form.category} onChange={set('category')} maxLength={50} />
        </label>
        <label className="field field-full">
          <span className="field-label">주소</span>
          <input value={form.address} onChange={set('address')} maxLength={255} />
        </label>
        <label className="field">
          <span className="field-label">전화번호</span>
          <input value={form.phone} onChange={set('phone')} maxLength={20} inputMode="tel" />
        </label>
        <label className="field">
          <span className="field-label">점심/저녁을 나누는 시각</span>
          <input type="time" value={form.slotCutoff} onChange={set('slotCutoff')} required />
          <span className="field-hint">이 시각 전 결제는 점심, 이후는 저녁으로 셉니다.</span>
        </label>
      </div>

      {error && <p className="form-error">{error}</p>}

      <hr className="divider" />
      <div className="actions">
        <Button type="submit" disabled={saving}>
          {saving ? '저장 중' : isEdit ? '변경 내용 저장' : '매장 등록'}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  )
}

/* ---------- 매장 삭제 (수정 화면에서만) ---------- */
function DeleteZone({ storeName, onDelete }) {
  const [asking, setAsking] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState(null)

  async function run() {
    setDeleting(true)
    setError(null)
    try {
      await onDelete()
    } catch (err) {
      setError(err.message)
      setDeleting(false)
    }
  }

  return (
    <section className="card danger-zone">
      <SectionHeading title="매장 삭제" />
      {!asking ? (
        <div className="danger-row">
          <p className="card-desc">매장을 지우면 이 매장의 메뉴, 매출, 리뷰, 분석 결과도 함께 지워지고 되돌릴 수 없어요.</p>
          <Button variant="danger" onClick={() => setAsking(true)}>
            매장 삭제
          </Button>
        </div>
      ) : (
        <div className="danger-confirm" role="alert">
          <p className="danger-q">‘{storeName}’을(를) 정말 삭제할까요?</p>
          <div className="actions">
            <Button variant="danger-solid" onClick={run} disabled={deleting}>
              {deleting ? '삭제 중' : '네, 삭제할게요'}
            </Button>
            <Button variant="secondary" onClick={() => setAsking(false)} disabled={deleting}>
              취소
            </Button>
          </div>
        </div>
      )}
      {error && <p className="form-error">{error}</p>}
    </section>
  )
}

export default function StoreFormPage({ storeId }) {
  const isEdit = storeId != null
  const { navigate } = useRouter()
  const { reload, setSelectedId } = useStores()

  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null) // null = 아직 검색 전
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState(null)
  const [picked, setPicked] = useState(null)

  const [form, setForm] = useState(EMPTY)
  const [filled, setFilled] = useState(false)
  const [loadError, setLoadError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const formRef = useRef(null)

  useEffect(() => {
    if (!isEdit) return
    api
      .getStore(storeId)
      .then((s) => setForm(toForm(s)))
      .catch((e) => setLoadError(e.message))
  }, [isEdit, storeId])

  const activeStep = picked ? 2 : filled || isEdit ? 3 : 1

  async function onSearch(e) {
    e.preventDefault()
    const q = query.trim()
    if (q.length < 2) {
      setSearchError('가게 이름을 2글자 이상 입력해 주세요.')
      return
    }
    setSearching(true)
    setSearchError(null)
    try {
      setResults(await api.searchPlaces(q))
    } catch (err) {
      setResults(null)
      setSearchError(err.message)
    } finally {
      setSearching(false)
    }
  }

  function confirmPlace() {
    setForm((f) => ({
      ...f,
      name: picked.name ?? '',
      category: picked.category ?? '',
      address: picked.roadAddress || picked.address || '',
      phone: picked.phone ?? '',
    }))
    setFilled(true)
    setPicked(null)
    setResults(null)
    requestAnimationFrame(() => {
      formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      formRef.current?.querySelector('input')?.focus({ preventScroll: true })
    })
  }

  async function onSave(e) {
    e.preventDefault()
    if (form.name.trim() === '') {
      setSaveError('매장 이름을 입력해 주세요.')
      return
    }
    setSaving(true)
    setSaveError(null)
    try {
      const saved = isEdit
        ? await api.updateStore(storeId, {
            name: form.name.trim(),
            category: form.category.trim(),
            address: form.address.trim(),
            phone: form.phone.trim(),
            slotCutoff: form.slotCutoff,
          })
        : await api.createStore({
            name: form.name.trim(),
            category: nullIfBlank(form.category),
            address: nullIfBlank(form.address),
            phone: nullIfBlank(form.phone),
            slotCutoff: form.slotCutoff,
          })
      await reload()
      setSelectedId(saved.id)
      navigate('/stores')
    } catch (err) {
      setSaveError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>{isEdit ? '매장 정보 수정' : '매장 등록'}</h1>
        </div>
        <p className="page-desc">
          카카오맵에서 가게를 찾아 확인하면 아래 정보가 채워져요. 저장하기 전에 내용을 확인하고 고칠 수 있어요.
        </p>
      </header>

      <StepsBar active={activeStep} />

      {loadError && <p className="form-error">{loadError}</p>}

      {picked ? (
        <>
          <ConfirmationCard place={picked} onConfirm={confirmPlace} onBack={() => setPicked(null)} />
          <NextStepPreview place={picked} />
        </>
      ) : (
        <>
          <SearchCard
            query={query}
            setQuery={setQuery}
            onSearch={onSearch}
            searching={searching}
            error={searchError}
            results={results}
            onPick={setPicked}
          />
          {!filled && !isEdit && <ConfirmationBridge />}
          <StoreForm
            form={form}
            setForm={setForm}
            filled={filled}
            onSave={onSave}
            saving={saving}
            error={saveError}
            isEdit={isEdit}
            onCancel={() => navigate('/stores')}
            formRef={formRef}
          />
          {isEdit && (
            <DeleteZone
              storeName={form.name}
              onDelete={async () => {
                await api.deleteStore(storeId)
                await reload()
                navigate('/stores')
              }}
            />
          )}
        </>
      )}
    </div>
  )
}
