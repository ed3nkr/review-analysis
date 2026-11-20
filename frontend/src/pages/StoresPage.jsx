import { useEffect, useState } from 'react'
import { ArrowRight, CloudAlert, Plus, Store } from 'lucide-react'
import { api } from '../api'
import { Badge, Button, DetailRow, StateBlock } from '../components/ui'
import { useStores } from '../StoreContext'

const cutoff = (t) => (t ? t.slice(0, 5) : '16:00')

function CurrentStoreCard({ store }) {
  const infoDone = Boolean(store.address)
  return (
    <section className="card card-current" aria-label="지금 보는 매장">
      <div className="card-current-head">
        <p className="eyebrow">지금 보는 매장</p>
        {infoDone ? <Badge tone="complete">매장 정보 완료</Badge> : <Badge>주소 입력 필요</Badge>}
      </div>

      <div className="identity">
        <div>
          <h2 className="identity-name">{store.name}</h2>
          {store.category && <p className="identity-sub">{store.category}</p>}
        </div>
        <Button variant="secondary" to={`/stores/${store.id}`}>
          매장 정보 수정
        </Button>
      </div>

      <hr className="divider" />

      <div className="store-details">
        <dl className="details">
          <DetailRow label="주소">{store.address || '아직 없어요'}</DetailRow>
          <DetailRow label="전화번호">{store.phone || '없음'}</DetailRow>
        </dl>
        <div className="meal-boundary">
          <p className="meal-label">점심과 저녁을 나누는 시각</p>
          <p className="meal-value">{cutoff(store.slotCutoff)}</p>
        </div>
      </div>
    </section>
  )
}

// 아직 없는 기능은 '준비 중'으로 그대로 보여 준다
function prepSteps(store, hasSales, hasReviews) {
  return [
    { label: '매장 정보', done: Boolean(store.address) },
    { label: '매출 올리기', done: hasSales },
    { label: '메뉴 정리', done: false },
    { label: '리뷰 모으기', done: hasReviews },
    { label: '리포트', done: false },
  ]
}

function PreparationCard({ store }) {
  const [hasSales, setHasSales] = useState(false)
  const [hasReviews, setHasReviews] = useState(false)
  useEffect(() => {
    setHasSales(false)
    setHasReviews(false)
    api
      .getSalesSummary(store.id)
      .then((s) => setHasSales(s.lineCount > 0))
      .catch(() => setHasSales(false))
    api
      .getReviewSummary(store.id)
      .then((s) => setHasReviews(s.reviewCount > 0))
      .catch(() => setHasReviews(false))
  }, [store.id])
  const steps = prepSteps(store, hasSales, hasReviews)
  return (
    <section className="card card-prep">
      <h2 className="card-title">분석 준비 순서</h2>
      <ol className="prep">
        {steps.map((s, i) => (
          <li key={s.label} className={'prep-step' + (s.done ? ' is-done' : '')}>
            <div className="prep-body">
              <p className="prep-heading">
                <span className="prep-num">{String(i + 1).padStart(2, '0')}</span>
                <span className="prep-label">{s.label}</span>
              </p>
              {s.done ? <Badge tone="complete">완료</Badge> : <Badge>준비 중</Badge>}
            </div>
            {i < steps.length - 1 && <ArrowRight size={16} className="prep-arrow" aria-hidden="true" />}
          </li>
        ))}
      </ol>
    </section>
  )
}

function OtherStores({ stores, onSelect }) {
  return (
    <section className="other-stores">
      <h2 className="section-title">다른 매장 목록</h2>
      <div className="card table-card">
        <div className="table-head" aria-hidden="true">
          <span className="col-name">가게 이름</span>
          <span className="col-addr">주소</span>
          <span className="col-phone">전화번호</span>
          <span className="col-actions">매장 선택 및 수정</span>
        </div>
        <ul>
          {stores.map((s) => (
            <li key={s.id} className="table-row">
              <span className="col-name row-name">{s.name}</span>
              <span className="col-addr row-sub">{s.address || '주소 미입력'}</span>
              <span className="col-phone row-sub-sm">{s.phone || '전화 없음'}</span>
              <span className="col-actions row-actions">
                <Button variant="secondary" size="sm" onClick={() => onSelect(s.id)}>
                  이 매장 보기
                </Button>
                <Button variant="quiet" size="sm" to={`/stores/${s.id}`}>
                  수정
                </Button>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

export default function StoresPage() {
  const { stores, selected, selectedId, setSelectedId, error, reload } = useStores()
  const others = stores.filter((s) => s.id !== selectedId)

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>매장</h1>
          <Button icon={Plus} to="/stores/new">
            매장 등록
          </Button>
        </div>
        <p className="page-desc">분석할 매장을 고르고, 리뷰와 매출을 연결할 준비가 어디까지 됐는지 확인해요.</p>
      </header>

      {error && (
        <div className="card card-state">
          <StateBlock
            icon={CloudAlert}
            tone="error"
            title="매장 목록을 불러오지 못했어요."
            text="잠시 후 다시 시도해 주세요."
            action={
              <Button variant="secondary" onClick={reload}>
                다시 불러오기
              </Button>
            }
          />
        </div>
      )}

      {!error && stores.length === 0 && (
        <div className="card card-state">
          <StateBlock
            icon={Store}
            title="등록된 매장이 없어요"
            text="카카오맵에서 가게를 찾아 등록하면 매출과 리뷰를 연결할 수 있어요."
            action={<Button to="/stores/new">첫 매장 등록하기</Button>}
          />
        </div>
      )}

      {selected && (
        <>
          <CurrentStoreCard store={selected} />
          <PreparationCard store={selected} />
        </>
      )}

      {others.length > 0 && <OtherStores stores={others} onSelect={setSelectedId} />}
    </div>
  )
}
