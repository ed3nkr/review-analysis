import { useCallback, useEffect, useState } from 'react'
import { CloudAlert, Download, MessageSquare, Store } from 'lucide-react'
import { api } from '../api'
import { useStores } from '../StoreContext'
import { Badge, Button, StateBlock } from '../components/ui'
import { RowErrors, Stat, UploadHistory, day, num, period } from '../components/upload'

const sourceLabel = () => '네이버 플레이스'
const when = (iso) => {
  if (!iso) return null
  const d = new Date(iso)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function SummaryCard({ summary }) {
  const keywordOnly = summary.reviewCount - summary.withContentCount
  return (
    <section className="card">
      <div className="card-current-head">
        <h2 className="card-title">저장된 리뷰</h2>
        <Badge>분석 전</Badge>
      </div>
      <div className="stats">
        <Stat label="기간 (방문일 기준)" value={period(summary.periodStart, summary.periodEnd)} />
        <Stat
          label="리뷰"
          value={num(summary.reviewCount) + '건'}
          sub={`본문 있음 ${num(summary.withContentCount)} · 키워드만 ${num(keywordOnly)}`}
        />
        <Stat
          label="평균 별점"
          value={summary.avgRating != null ? summary.avgRating.toFixed(2) : '없음'}
          sub={summary.avgRating != null ? '별점이 있는 리뷰만' : '별점이 없는 리뷰예요'}
        />
      </div>
    </section>
  )
}

const WAIT_SECONDS = 3

function CollectCard({ store, lastCollectedAt, salesPeriod, phase, countdown, onCollect, onCancel }) {
  const noSales = !salesPeriod
  return (
    <section className="card">
      <div className="collect">
        <span className="collect-symbol" aria-hidden="true">
          <MessageSquare size={24} />
        </span>
        <div className="collect-text">
          <h2 className="card-title">네이버 플레이스 리뷰 불러오기</h2>
          <p className="card-desc">
            {noSales
              ? '리뷰는 매출 기간에 맞춰 불러와요. 먼저 매출 CSV를 올려 주세요.'
              : `${store.name}의 방문자 리뷰 중 매출 기간(${period(salesPeriod[0], salesPeriod[1])})의 리뷰를 모아요. 이미 저장된 리뷰는 건너뛰어요.`}
          </p>
          <p className="collect-last">
            {lastCollectedAt ? `마지막으로 불러온 시각 ${when(lastCollectedAt)}` : '아직 불러온 적이 없어요'}
          </p>
        </div>
        {noSales ? (
          <Button variant="secondary" to="/sales">
            매출 올리러 가기
          </Button>
        ) : (
          <Button icon={Download} disabled={phase !== 'idle'} onClick={onCollect}>
            {phase === 'loading' ? '불러오는 중…' : phase === 'waiting' ? `${countdown}초 뒤 시작` : '리뷰 불러오기'}
          </Button>
        )}
      </div>
      {phase === 'waiting' && (
        <div className="collect-notice" role="status">
          <p>
            업로드하신 매출 데이터의 기간(<strong>{period(salesPeriod[0], salesPeriod[1])}</strong>)과 같은 기간의 리뷰를
            불러올게요.
          </p>
          <Button size="sm" variant="quiet" onClick={onCancel}>
            취소
          </Button>
        </div>
      )}
      {phase === 'loading' && (
        <p className="collect-progress" role="status">
          네이버 플레이스에서 {period(salesPeriod[0], salesPeriod[1])} 리뷰를 읽고 있어요. 1~2분 걸릴 수 있어요.
        </p>
      )}
    </section>
  )
}

function ResultCard({ result }) {
  return (
    <section className="card result-card" aria-live="polite">
      <div>
        <p className="eyebrow">방금 불러온 리뷰 · {sourceLabel(result.source)}</p>
        <h2 className="result-title">
          {result.totalRows > 0 ? (
            <>
              {num(result.totalRows)}건 중 <strong>{num(result.savedRows)}건</strong>을 새로 저장했어요.
            </>
          ) : (
            '새 리뷰가 없어요.'
          )}
        </h2>
        {result.periodStart && <p className="card-desc">기간 {period(result.periodStart, result.periodEnd)}</p>}
      </div>
      {result.totalRows > 0 && (
        <div className="stats">
          <Stat label="새로 저장" value={num(result.savedRows) + '건'} />
          <Stat label="이미 있던 리뷰" value={num(result.duplicateRows) + '건'} sub="건너뜀" />
          <Stat label="읽지 못한 리뷰" value={num(result.errorRows) + '건'} />
        </div>
      )}
      {result.outOfPeriodRows > 0 && (
        <p className="card-desc">매출 기간 밖의 리뷰 {num(result.outOfPeriodRows)}건은 저장하지 않았어요.</p>
      )}
      <RowErrors errors={result.errors} errorRows={result.errorRows} />
    </section>
  )
}

function Stars({ rating }) {
  if (rating == null) return null
  return (
    <span className="stars" aria-label={`별점 ${rating}점`}>
      {'★'.repeat(rating)}
      <span className="stars-off">{'★'.repeat(5 - rating)}</span>
    </span>
  )
}

function RecentReviews({ reviews }) {
  if (!reviews || reviews.length === 0) return null
  return (
    <section className="other-stores">
      <h2 className="section-title">최근 리뷰</h2>
      <ul className="card review-list">
        {reviews.map((r) => (
          <li key={r.id} className="review-item">
            <div className="review-meta">
              <Stars rating={r.rating} />
              <span>방문 {day(r.visitedAt || r.writtenAt)}</span>
              {r.visitSlotTag && <span>· {r.visitSlotTag}</span>}
            </div>
            {r.content ? (
              <p className="review-text">{r.content}</p>
            ) : (
              <p className="review-text is-empty">본문 없이 키워드만 골랐어요</p>
            )}
            {r.keywords.length > 0 && (
              <ul className="review-keywords">
                {r.keywords.map((k) => (
                  <li key={k}>{k}</li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

export default function ReviewsPage() {
  const { selected } = useStores()
  const [summary, setSummary] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [salesPeriod, setSalesPeriod] = useState(undefined) // undefined = 불러오는 중, null = 매출 없음
  const [phase, setPhase] = useState('idle') // idle → waiting(3초) → loading
  const [countdown, setCountdown] = useState(WAIT_SECONDS)
  const [result, setResult] = useState(null)
  const [collectError, setCollectError] = useState(null)

  const load = useCallback(() => {
    if (!selected) return
    setLoadError(null)
    api
      .getReviewSummary(selected.id)
      .then(setSummary)
      .catch((e) => setLoadError(e.message))
    api
      .getSalesSummary(selected.id)
      .then((s) => setSalesPeriod(s.lineCount > 0 ? [s.periodStart, s.periodEnd] : null))
      .catch(() => setSalesPeriod(null))
  }, [selected?.id])

  useEffect(() => {
    setSummary(null)
    setSalesPeriod(undefined)
    setResult(null)
    setCollectError(null)
    setPhase('idle')
    load()
  }, [load])

  // 안내 문구를 보여 주고 3초 센 뒤 불러오기
  useEffect(() => {
    if (phase !== 'waiting') return
    if (countdown <= 0) {
      setPhase('loading')
      api
        .collectReviews(selected.id)
        .then((r) => {
          setResult(r)
          load()
        })
        .catch((e) => setCollectError(e.message))
        .finally(() => setPhase('idle'))
      return
    }
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => clearTimeout(t)
  }, [phase, countdown])

  const startCollect = () => {
    setCollectError(null)
    setResult(null)
    setCountdown(WAIT_SECONDS)
    setPhase('waiting')
  }

  const history = summary?.collections?.map((c) => ({ ...c, fileName: sourceLabel(c.source) }))

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>리뷰</h1>
        </div>
        <p className="page-desc">네이버 플레이스 방문자 리뷰를 모아 매출과 비교해요.</p>
      </header>

      {!selected && (
        <div className="card card-state">
          <StateBlock
            icon={Store}
            title="먼저 매장을 등록해 주세요"
            text="리뷰는 매장마다 따로 모아요."
            action={<Button to="/stores/new">매장 등록하기</Button>}
          />
        </div>
      )}

      {selected && loadError && (
        <div className="card card-state">
          <StateBlock icon={CloudAlert} tone="error" title="리뷰 정보를 불러오지 못했어요." text="잠시 후 다시 시도해 주세요." />
        </div>
      )}

      {selected && !loadError && (
        <>
          {summary && summary.reviewCount > 0 && <SummaryCard summary={summary} />}

          {salesPeriod !== undefined && (
            <CollectCard
              store={selected}
              lastCollectedAt={summary?.lastCollectedAt}
              salesPeriod={salesPeriod}
              phase={phase}
              countdown={countdown}
              onCollect={startCollect}
              onCancel={() => setPhase('idle')}
            />
          )}

          {collectError && (
            <div className="alert alert-danger" role="alert">
              <CloudAlert size={20} aria-hidden="true" />
              <div>
                <p className="alert-title">리뷰를 불러오지 못했어요</p>
                <p className="alert-text">{collectError}</p>
              </div>
            </div>
          )}

          {result && <ResultCard result={result} />}

          {summary && summary.reviewCount === 0 && !result && (
            <div className="card card-state">
              <StateBlock
                icon={MessageSquare}
                title="아직 모은 리뷰가 없어요"
                text="위의 '리뷰 불러오기'를 누르면 리뷰 수·기간·평균 별점과 최근 리뷰가 여기에 보여요."
              />
            </div>
          )}

          {summary && <RecentReviews reviews={summary.recent} />}
          {history && (
            <UploadHistory uploads={history} unit="건" title="불러온 기록" whenLabel="불러온 시각" fileLabel="출처" />
          )}
        </>
      )}
    </div>
  )
}
