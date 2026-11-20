import { useCallback, useEffect, useState } from 'react'
import { ChartColumn, CloudAlert, FileText, Play, Store } from 'lucide-react'
import { api } from '../api'
import { useStores } from '../StoreContext'
import { Badge, Button, StateBlock } from '../components/ui'
import { Stat, num, period } from '../components/upload'

/* ---------- 표시용 이름 ---------- */
const QUESTION = {
  Q1: '판매 vs 리뷰 언급',
  Q2: '칭찬·불만으로 본 메뉴',
  Q3: '기간 앞뒤 변화',
  Q4: '시간대 쏠림',
  Q5: '시간대별 리뷰·불만',
  Q6: '월별 시간대 쏠림',
  Q7: '리뷰와 손님 수',
  Q8: '월별 리뷰 작성률',
  Q9: '매출이 크게 변한 주',
  R1: '반복되는 불만',
}
const THEMES = [
  { key: 'menu', title: '메뉴', codes: ['Q1', 'Q2', 'Q3'] },
  { key: 'slot', title: '시간대', codes: ['Q4', 'Q5', 'Q6'] },
  { key: 'period', title: '기간', codes: ['Q7', 'Q8', 'Q9'] },
  { key: 'repeat', title: '반복 불만', codes: ['R1'] },
]
const EXCLUDE = {
  MEANINGLESS: '의미 없는 글',
  AD: '광고',
  EVENT: '이벤트 참여',
  OTHER_STORE: '다른 가게 이야기',
  DUPLICATE: '중복',
  LOW_RELIABILITY: '모델이 노이즈로 판단',
}
const SLOT = { LUNCH: '점심', DINNER: '저녁' }

const pct = (v, d = 0) => (v == null ? '—' : (v * 100).toFixed(d) + '%')
const pp = (v) => (v == null ? '—' : (v > 0 ? '+' : '') + v.toFixed(1) + '%p')
const fixed = (v, d = 1) => (v == null ? '—' : Number(v).toFixed(d))
const when = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
const monthLabel = (id) => (id && /^\d{4}-\d{2}$/.test(id) ? Number(id.slice(5)) + '월' : id)
const parse = (s) => {
  try {
    return typeof s === 'string' ? JSON.parse(s) : s || {}
  } catch {
    return {}
  }
}

/* ---------- 근거 수치 → 막대 + 사실 ---------- */
// tone: sales(매출) | review(리뷰) | plain(같은 종류끼리 비교)
function evidenceOf(ins) {
  const e = parse(ins.evidence)
  const bars = []
  const facts = []
  switch (ins.questionCode) {
    case 'Q1':
      bars.push({ label: '판매 비중', value: e.sales_share, text: pct(e.sales_share), tone: 'sales' })
      bars.push({ label: '리뷰 언급 비중', value: e.mention_share, text: pct(e.mention_share), tone: 'review' })
      facts.push(['판매', num(Math.round(e.units ?? 0)) + '개'], ['리뷰 언급', `${num(e.mentions)}건 / 전체 ${num(e.n_mentions)}건`])
      break
    case 'Q2':
      if (e.position) facts.push(['위치', e.position])
      facts.push(
        ['판매', num(Math.round(e.units ?? 0)) + '개'],
        ['칭찬', `${num(e.praise)}건 · 1,000개당 ${fixed(e.praise_rate)}건`],
        ['불만', `${num(e.blame)}건 · 1,000개당 ${fixed(e.blame_rate)}건`],
        ['메뉴 중앙값', `판매 ${num(Math.round(e.median_units ?? 0))}개 · 칭찬 ${fixed(e.median_praise_rate)}건`],
      )
      break
    case 'Q3':
      if (e.periods) facts.push(['비교 기간', `${e.periods[0]} → ${e.periods[1]}`])
      facts.push(
        ['판매 비중 변화', pp(e.sales_share_change_pp)],
        ['리뷰 언급 비중 변화', pp(e.mention_share_change_pp)],
        ['리뷰 언급', `${num(e.mentions_early)}건 → ${num(e.mentions_late)}건`],
      )
      break
    case 'Q4':
    case 'Q6':
      bars.push({ label: '매출 영수증 중 저녁', value: e.sales_dinner_share, text: pct(e.sales_dinner_share), tone: 'sales' })
      bars.push({ label: '리뷰 중 저녁', value: e.review_dinner_share, text: pct(e.review_dinner_share), tone: 'review' })
      facts.push(['시간대를 알 수 있는 리뷰', num(e.n) + '건'])
      if (e.usual_gap_pp != null) facts.push(['평소 차이', fixed(e.usual_gap_pp) + '%p'])
      break
    case 'Q5':
      if (ins.targetId === 'NEG_RATE') {
        for (const s of ['LUNCH', 'DINNER']) {
          const v = e.negative_rate?.[s]
          bars.push({ label: `${SLOT[s]} 불만 비율`, value: v, text: pct(v), tone: 'plain' })
        }
      } else {
        const r = e.review_rate_per_1000 || {}
        const max = Math.max(r.LUNCH ?? 0, r.DINNER ?? 0) || 1
        for (const s of ['LUNCH', 'DINNER']) {
          bars.push({ label: `${SLOT[s]} 1,000명당 리뷰`, value: (r[s] ?? 0) / max, text: fixed(r[s]) + '건', tone: 'plain' })
        }
      }
      facts.push(
        ['영수증', `점심 ${num(e.bills?.LUNCH)} · 저녁 ${num(e.bills?.DINNER)}`],
        ['리뷰', `점심 ${num(e.reviews?.LUNCH)} · 저녁 ${num(e.reviews?.DINNER)}`],
      )
      break
    case 'Q7':
      facts.push(['비교한 주', num(e.weeks) + '주'], ['주별 상관계수', fixed(e.weekly_corr, 2)])
      break
    case 'Q8': {
      const max = Math.max(e.rate_per_1000 ?? 0, e.overall_rate_per_1000 ?? 0) || 1
      bars.push({ label: `${monthLabel(ins.targetId)} 1,000명당 리뷰`, value: (e.rate_per_1000 ?? 0) / max, text: fixed(e.rate_per_1000) + '건', tone: 'plain' })
      bars.push({ label: '전체 기간 평균', value: (e.overall_rate_per_1000 ?? 0) / max, text: fixed(e.overall_rate_per_1000) + '건', tone: 'plain' })
      facts.push(['영수증', num(e.bills) + '장'], ['리뷰', num(e.reviews) + '건'])
      break
    }
    case 'Q9':
      facts.push(['매출 변화', (e.sales_change_pct > 0 ? '+' : '') + fixed(e.sales_change_pct, 0) + '%'])
      facts.push(['그 주 리뷰', `${num(e.reviews_after)}건 중 불만 ${num(e.negative_after)}건`])
      facts.push(['전 주 리뷰', `${num(e.reviews_before)}건 중 불만 ${num(e.negative_before)}건`])
      facts.push(['평소 불만 비율', pct(e.store_negative_rate)])
      break
    case 'R1': {
      const max = Math.max(e.count ?? 0, e.expected ?? 0) || 1
      bars.push({ label: '몰린 기간의 불만', value: (e.count ?? 0) / max, text: num(e.count) + '건', tone: 'plain' })
      bars.push({ label: '평소라면', value: (e.expected ?? 0) / max, text: fixed(e.expected) + '건', tone: 'plain' })
      if (e.start) facts.push(['기간', period(e.start, e.end)])
      facts.push(['전체 기간 불만', num(e.total) + '건'])
      break
    }
    default:
  }
  return { bars, facts, alpha: e.alpha }
}

function Bars({ bars }) {
  if (bars.length === 0) return null
  return (
    <ul className="ev-bars">
      {bars.map((b) => (
        <li key={b.label} className="ev-bar" title={`${b.label} ${b.text}`}>
          <span className="ev-bar-label">{b.label}</span>
          <span className="ev-bar-track">
            <span className={'ev-bar-fill tone-' + b.tone} style={{ width: `${Math.max(1, Math.min(1, b.value ?? 0) * 100)}%` }} />
          </span>
          <span className="ev-bar-value">{b.text}</span>
        </li>
      ))}
    </ul>
  )
}

/* ---------- 점주 평가 ---------- */
const EVAL_QUESTIONS = [
  ['evalNew', '몰랐던 사실인가요?'],
  ['evalCorrect', '실제와 맞나요?'],
  ['evalAction', '행동으로 옮길 건가요?'],
]

function Evaluation({ insight, onSave }) {
  const [state, setState] = useState({
    evalNew: insight.evalNew,
    evalCorrect: insight.evalCorrect,
    evalAction: insight.evalAction,
    evalComment: insight.evalComment ?? '',
  })
  const [status, setStatus] = useState(null) // saving | saved | 오류 메시지
  const [commentOpen, setCommentOpen] = useState(Boolean(insight.evalComment))

  const save = async (next) => {
    setState(next)
    setStatus('saving')
    try {
      await onSave(insight.id, { ...next, evalComment: next.evalComment.trim() || null })
      setStatus('saved')
    } catch (e) {
      setStatus(e.message)
    }
  }

  return (
    <div className="eval">
      <p className="eval-title">이 결과, 어떠세요?</p>
      <div className="eval-questions">
        {EVAL_QUESTIONS.map(([key, label]) => (
          <div key={key} className="eval-q" role="group" aria-label={label}>
            <span className="eval-q-label">{label}</span>
            <span className="seg">
              {[
                [true, '예'],
                [false, '아니요'],
              ].map(([v, text]) => (
                <button
                  key={text}
                  type="button"
                  className={'seg-btn' + (state[key] === v ? ' is-on' : '')}
                  aria-pressed={state[key] === v}
                  onClick={() => save({ ...state, [key]: state[key] === v ? null : v })}
                >
                  {text}
                </button>
              ))}
            </span>
          </div>
        ))}
      </div>
      {commentOpen ? (
        <form
          className="eval-comment"
          onSubmit={(e) => {
            e.preventDefault()
            save(state)
          }}
        >
          <input
            className="input input-sm"
            value={state.evalComment}
            onChange={(e) => setState({ ...state, evalComment: e.target.value })}
            placeholder="한 줄 의견 (예: 점심 웨이팅은 알고 있었어요)"
            maxLength={500}
            aria-label="한 줄 의견"
          />
          <Button size="sm" variant="secondary" type="submit" disabled={status === 'saving'}>
            의견 저장
          </Button>
        </form>
      ) : (
        <button type="button" className="link-btn" onClick={() => setCommentOpen(true)}>
          한 줄 의견 남기기
        </button>
      )}
      {status && (
        <p className={'eval-status' + (status !== 'saving' && status !== 'saved' ? ' is-error' : '')} role="status">
          {status === 'saving' ? '저장하는 중…' : status === 'saved' ? '평가를 저장했어요.' : status}
        </p>
      )}
    </div>
  )
}

function InsightCard({ insight, onEvaluate }) {
  const { bars, facts, alpha } = evidenceOf(insight)
  return (
    <li className="card insight-card">
      <div className="insight-head">
        <span className="insight-code">{insight.questionCode}</span>
        <span className="insight-q">{QUESTION[insight.questionCode]}</span>
        {insight.targetType === 'MONTH' && <Badge>{monthLabel(insight.targetId)}</Badge>}
      </div>
      <p className="insight-sentence">{insight.sentence}</p>
      <Bars bars={bars} />
      {facts.length > 0 && (
        <dl className="ev-facts">
          {facts.map(([k, v]) => (
            <div key={k} className="ev-fact">
              <dt>{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      )}
      {insight.pValue != null && (
        <p className="ev-p">
          p = {insight.pValue < 0.0001 ? insight.pValue.toExponential(1) : insight.pValue.toFixed(4)}
          {alpha != null && ` · 판정 기준 ${alpha} (본페로니 보정)`}
        </p>
      )}
      <Evaluation insight={insight} onSave={onEvaluate} />
    </li>
  )
}

/* ---------- 메뉴별 판매 비중 vs 리뷰 언급 비중 (Q1 전체) ---------- */
function MenuShareChart({ insights }) {
  const [table, setTable] = useState(false)
  const rows = insights
    .filter((i) => i.questionCode === 'Q1')
    .map((i) => ({ ...parse(i.evidence), name: i.targetId, flagged: i.result === 'FLAGGED' }))
    .filter((r) => r.sales_share != null)
    .sort((a, b) => b.sales_share - a.sales_share)
  if (rows.length < 2) return null
  const max = Math.max(...rows.flatMap((r) => [r.sales_share, r.mention_share ?? 0]))

  return (
    <section className="card">
      <div className="chart-head">
        <div>
          <h2 className="card-title">메뉴별 판매 비중과 리뷰 언급 비중</h2>
          <p className="card-desc">두 막대의 길이가 크게 다르면 리뷰가 판매를 제대로 비추지 못하는 메뉴예요.</p>
        </div>
        <button type="button" className="link-btn" onClick={() => setTable(!table)}>
          {table ? '그래프로 보기' : '표로 보기'}
        </button>
      </div>
      <ul className="legend" aria-label="범례">
        <li>
          <span className="legend-swatch tone-sales" />
          판매 비중
        </li>
        <li>
          <span className="legend-swatch tone-review" />
          리뷰 언급 비중
        </li>
      </ul>
      {table ? (
        <table className="share-table">
          <thead>
            <tr>
              <th>메뉴군</th>
              <th className="num-col">판매 비중</th>
              <th className="num-col">리뷰 언급 비중</th>
              <th className="num-col">차이</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.name}>
                <td>
                  {r.name} {r.flagged && <Badge tone="complete">눈에 띔</Badge>}
                </td>
                <td className="num-col">{pct(r.sales_share, 1)}</td>
                <td className="num-col">{pct(r.mention_share, 1)}</td>
                <td className="num-col">{pp(r.diff_pp)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <ul className="share-chart">
          {rows.map((r) => (
            <li key={r.name} className={'share-row' + (r.flagged ? ' is-flagged' : '')}>
              <span className="share-name">
                {r.name}
                {r.flagged && <span className="share-flag">눈에 띔</span>}
              </span>
              <span className="share-bars">
                {[
                  ['sales', '판매', r.sales_share],
                  ['review', '리뷰 언급', r.mention_share ?? 0],
                ].map(([tone, label, v]) => (
                  <span key={tone} className="share-line" title={`${r.name} ${label} ${pct(v, 1)}`}>
                    <span className="ev-bar-track is-bare">
                      <span className={'ev-bar-fill tone-' + tone} style={{ width: `${Math.max(0.5, (v / max) * 100)}%` }} />
                    </span>
                    <span className="share-value">{pct(v)}</span>
                  </span>
                ))}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/* ---------- 리뷰 분석 요약 ---------- */
function ReviewStatsCard({ stats }) {
  if (!stats) return null
  const model = stats.modelVersion?.includes('klue') ? 'KLUE-BERT 모델 + 규칙' : '규칙만 사용'
  const types = Object.entries(stats.negTypes || {})
  const maxType = Math.max(1, ...types.map(([, n]) => n))
  const reasons = Object.entries(stats.excludeReasons || {})
  return (
    <section className="card">
      <div className="card-current-head">
        <h2 className="card-title">리뷰 분석</h2>
        <Badge tone="complete">{model}</Badge>
      </div>
      <div className="stats report-stats">
        <Stat label="분석에 쓴 리뷰" value={num(stats.used) + '건'} sub={`전체 ${num(stats.total)}건`} />
        <Stat label="걸러 낸 리뷰" value={num(stats.excluded) + '건'} sub="광고·중복·의미 없는 글 등" />
        <Stat label="긍정" value={num(stats.positive) + '건'} />
        <Stat label="불만 있음" value={num(stats.negative) + '건'} sub={stats.used ? pct(stats.negative / stats.used) : ''} />
      </div>
      {reasons.length > 0 && (
        <div>
          <p className="sub-title">걸러 낸 이유</p>
          <ul className="reason-chips">
            {reasons.map(([k, n]) => (
              <li key={k}>
                {EXCLUDE[k] ?? k} <strong>{num(n)}</strong>
              </li>
            ))}
          </ul>
        </div>
      )}
      {types.length > 0 && (
        <div>
          <p className="sub-title">불만 유형 (한 리뷰에 여러 유형이 있을 수 있어요)</p>
          <ul className="ev-bars">
            {types.map(([t, n]) => (
              <li key={t} className="ev-bar" title={`${t} ${n}건`}>
                <span className="ev-bar-label">{t}</span>
                <span className="ev-bar-track">
                  <span className="ev-bar-fill tone-plain" style={{ width: `${(n / maxType) * 100}%` }} />
                </span>
                <span className="ev-bar-value">{num(n)}건</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

/* ---------- 분석 실행 ---------- */
function AnalyzeCard({ report, running, elapsed, onRun }) {
  const done = report?.jobId != null
  return (
    <section className="card">
      <div className="collect">
        <span className="collect-symbol" aria-hidden="true">
          <ChartColumn size={24} />
        </span>
        <div className="collect-text">
          <h2 className="card-title">리뷰·매출 연계 분석</h2>
          <p className="card-desc">
            리뷰를 걸러 내고 불만 유형을 분류한 뒤, 메뉴·시간대·기간별로 매출과 비교해 눈에 띄는 점을 찾아요.
          </p>
          <p className="collect-last">
            {done
              ? `마지막 분석 ${when(report.analyzedAt)} · 기간 ${period(report.periodStart, report.periodEnd)}`
              : '아직 분석한 적이 없어요'}
          </p>
        </div>
        <Button icon={Play} disabled={running} onClick={onRun}>
          {running ? '분석하는 중…' : done ? '다시 분석하기' : '분석하기'}
        </Button>
      </div>
      {running && (
        <p className="collect-progress" role="status">
          리뷰를 분류하고 매출과 비교하고 있어요. 1~3분 걸릴 수 있어요. ({elapsed}초)
        </p>
      )}
    </section>
  )
}

function Others({ title, items }) {
  if (items.length === 0) return null
  return (
    <details className="card others">
      <summary>
        <span className="card-title">{title}</span>
        <span className="others-count">{items.length}개</span>
      </summary>
      <ul className="others-list">
        {items.map((i) => (
          <li key={i.id}>
            <span className="insight-code">{i.questionCode}</span>
            <span>{i.sentence}</span>
          </li>
        ))}
      </ul>
    </details>
  )
}

export default function ReportPage() {
  const { selected } = useStores()
  const [report, setReport] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [running, setRunning] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [runError, setRunError] = useState(null)

  const load = useCallback(() => {
    if (!selected) return
    setLoadError(null)
    api
      .getLatestAnalysis(selected.id)
      .then(setReport)
      .catch((e) => setLoadError(e.message))
  }, [selected?.id])

  useEffect(() => {
    setReport(null)
    setRunError(null)
    load()
  }, [load])

  useEffect(() => {
    if (!running) return
    setElapsed(0)
    const t = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(t)
  }, [running])

  const run = async () => {
    setRunning(true)
    setRunError(null)
    try {
      setReport(await api.runAnalysis(selected.id))
    } catch (e) {
      setRunError(e.message)
    } finally {
      setRunning(false)
    }
  }

  const evaluate = async (insightId, body) => {
    await api.evaluateInsight(selected.id, insightId, body)
    setReport((r) => ({ ...r, insights: r.insights.map((i) => (i.id === insightId ? { ...i, ...body } : i)) }))
  }

  const insights = report?.insights ?? []
  const flagged = insights.filter((i) => i.result === 'FLAGGED')
  const normal = insights.filter((i) => i.result === 'NORMAL')
  const insufficient = insights.filter((i) => i.result === 'INSUFFICIENT')

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>리포트</h1>
        </div>
        <p className="page-desc">리뷰와 매출이 서로 다른 이야기를 하는 지점을 모았어요. 결과마다 맞는지 평가해 주세요.</p>
      </header>

      {!selected && (
        <div className="card card-state">
          <StateBlock
            icon={Store}
            title="먼저 매장을 등록해 주세요"
            text="리포트는 매장마다 따로 만들어요."
            action={<Button to="/stores/new">매장 등록하기</Button>}
          />
        </div>
      )}

      {selected && loadError && (
        <div className="card card-state">
          <StateBlock icon={CloudAlert} tone="error" title="리포트를 불러오지 못했어요." text="잠시 후 다시 시도해 주세요." />
        </div>
      )}

      {selected && !loadError && report && (
        <>
          <AnalyzeCard report={report} running={running} elapsed={elapsed} onRun={run} />

          {runError && (
            <div className="alert alert-danger" role="alert">
              <CloudAlert size={20} aria-hidden="true" />
              <div>
                <p className="alert-title">분석하지 못했어요</p>
                <p className="alert-text">{runError}</p>
              </div>
            </div>
          )}

          {report.jobId == null && !running && (
            <div className="card card-state">
              <StateBlock
                icon={FileText}
                title="아직 리포트가 없어요"
                text="매출 CSV를 올리고 리뷰를 불러온 다음 '분석하기'를 누르면, 눈에 띈 점과 근거 수치가 여기에 정리돼요."
                action={
                  <span className="state-actions">
                    <Button variant="secondary" to="/sales">
                      매출
                    </Button>
                    <Button variant="secondary" to="/reviews">
                      리뷰
                    </Button>
                  </span>
                }
              />
            </div>
          )}

          {report.jobId != null && (
            <>
              <section className="report-lead">
                <p>
                  질문 {num(insights.length)}개를 확인해 <strong>눈에 띈 것 {num(flagged.length)}개</strong>를 찾았어요.
                </p>
                <p className="report-lead-sub">
                  특이사항 없음 {num(normal.length)}개 · 데이터가 부족해 판단하지 않은 것 {num(insufficient.length)}개
                </p>
              </section>

              <ReviewStatsCard stats={report.reviews} />

              {THEMES.map((theme) => {
                const list = flagged.filter((i) => theme.codes.includes(i.questionCode))
                const chart = theme.key === 'menu' ? <MenuShareChart insights={insights} /> : null
                if (list.length === 0 && !chart) return null
                return (
                  <section key={theme.key} className="theme">
                    <h2 className="section-title">
                      {theme.title} <span className="theme-count">눈에 띈 것 {list.length}개</span>
                    </h2>
                    {chart}
                    {list.length > 0 && (
                      <ul className="insight-list">
                        {list.map((i) => (
                          <InsightCard key={i.id} insight={i} onEvaluate={evaluate} />
                        ))}
                      </ul>
                    )}
                  </section>
                )
              })}

              <Others title="특이사항 없음" items={normal} />
              <Others title="데이터가 부족해 판단하지 않은 것" items={insufficient} />
            </>
          )}
        </>
      )}
    </div>
  )
}
