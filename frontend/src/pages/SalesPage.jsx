import { useCallback, useEffect, useState } from 'react'
import { CloudAlert, Receipt, Store } from 'lucide-react'
import { api } from '../api'
import { useStores } from '../StoreContext'
import { Badge, Button, StateBlock } from '../components/ui'
import { RowErrors, Stat, UploadAlert, UploadCard, UploadHistory, num, period } from '../components/upload'

const won = (n) => n.toLocaleString('ko-KR') + '원'

const COLUMNS = [
  ['판매일시', 'sold_at'],
  ['영수증번호', 'bill_no'],
  ['메뉴명', 'menu'],
  ['수량', 'quantity'],
  ['금액', 'amount'],
]
const OPTIONAL = [
  ['할인', 'discount'],
  ['상태', 'status'],
]

function DeleteAll({ onDelete }) {
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!confirm) {
    return (
      <Button size="sm" variant="danger" onClick={() => setConfirm(true)}>
        매출 전체 삭제
      </Button>
    )
  }
  return (
    <span className="confirm-inline">
      <span>저장된 매출을 모두 지울까요?</span>
      <Button
        size="sm"
        variant="danger-solid"
        disabled={busy}
        onClick={async () => {
          setBusy(true)
          try {
            await onDelete()
          } finally {
            setBusy(false)
            setConfirm(false)
          }
        }}
      >
        모두 지우기
      </Button>
      <Button size="sm" variant="quiet" disabled={busy} onClick={() => setConfirm(false)}>
        취소
      </Button>
    </span>
  )
}

function SummaryCard({ summary, onDeleteAll }) {
  return (
    <section className="card">
      <div className="card-current-head">
        <h2 className="card-title">저장된 매출</h2>
        <Badge tone="complete">분석에 쓸 수 있어요</Badge>
      </div>
      <div className="stats">
        <Stat label="기간" value={period(summary.periodStart, summary.periodEnd)} />
        <Stat label="영수증" value={num(summary.billCount) + '건'} />
        <Stat label="판매 품목" value={num(summary.lineCount) + '줄'} />
        <Stat label="매출 합계" value={won(summary.normalAmount)} sub="반품 제외, 할인 반영" />
      </div>
      <div className="summary-foot">
        <p className="manage-note">메뉴는 지워지지 않아요. 판매 기록이 없어진 메뉴는 메뉴 화면에서 지울 수 있어요.</p>
        <DeleteAll onDelete={onDeleteAll} />
      </div>
    </section>
  )
}

function ResultCard({ result }) {
  return (
    <section className="card result-card" aria-live="polite">
      <div>
        <p className="eyebrow">방금 올린 파일 · {result.fileName}</p>
        <h2 className="result-title">
          {num(result.totalRows)}줄 중 <strong>{num(result.savedRows)}줄</strong>을 새로 저장했어요.
        </h2>
        <p className="card-desc">기간 {period(result.periodStart, result.periodEnd)}</p>
      </div>

      <div className="stats">
        <Stat label="새로 저장" value={num(result.savedRows) + '줄'} />
        <Stat label="이미 있던 줄" value={num(result.duplicateRows) + '줄'} sub="건너뜀" />
        <Stat label="저장 못 한 줄" value={num(result.errorRows) + '줄'} />
      </div>

      {(result.newMenus > 0 || result.newGroups > 0) && (
        <div className="menu-notice">
          <p>
            새 메뉴 <strong>{num(result.newMenus)}개</strong>를 등록하고 메뉴군 <strong>{num(result.newGroups)}개</strong>로
            묶었어요.
          </p>
          <Button variant="secondary" size="sm" to="/menus">
            메뉴 확인하기
          </Button>
        </div>
      )}

      <RowErrors errors={result.errors} errorRows={result.errorRows} />
    </section>
  )
}

export default function SalesPage() {
  const { selected } = useStores()
  const [summary, setSummary] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [uploadError, setUploadError] = useState(null)

  const load = useCallback(() => {
    if (!selected) return
    setLoadError(null)
    api
      .getSalesSummary(selected.id)
      .then(setSummary)
      .catch((e) => setLoadError(e.message))
  }, [selected?.id])

  useEffect(() => {
    setSummary(null)
    setResult(null)
    setNotice(null)
    setUploadError(null)
    load()
  }, [load])

  const [notice, setNotice] = useState(null)
  const afterDelete = (r) => {
    setResult(null)
    setNotice(`매출 ${num(r.deletedRows)}줄을 지웠어요.`)
    load()
  }
  const deleteUpload = async (jobId) => afterDelete(await api.deleteSalesUpload(selected.id, jobId))
  const deleteAll = async () => afterDelete(await api.deleteAllSales(selected.id))

  const upload = async (file) => {
    setBusy(true)
    setUploadError(null)
    setResult(null)
    setNotice(null)
    try {
      const r = await api.uploadSales(selected.id, file)
      setResult(r)
      load()
      return true
    } catch (e) {
      setUploadError(e.message)
      return false
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title-row">
          <h1>매출</h1>
        </div>
        <p className="page-desc">영수증 한 줄 한 줄이 리뷰와 비교할 기준이 돼요. 기간이 길수록 비교가 정확해져요.</p>
      </header>

      {!selected && (
        <div className="card card-state">
          <StateBlock
            icon={Store}
            title="먼저 매장을 등록해 주세요"
            text="매출은 매장마다 따로 저장해요."
            action={<Button to="/stores/new">매장 등록하기</Button>}
          />
        </div>
      )}

      {selected && loadError && (
        <div className="card card-state">
          <StateBlock icon={CloudAlert} tone="error" title="매출 정보를 불러오지 못했어요." text="잠시 후 다시 시도해 주세요." />
        </div>
      )}

      {selected && !loadError && (
        <>
          {summary && summary.lineCount > 0 && <SummaryCard summary={summary} onDeleteAll={deleteAll} />}
          {notice && (
            <p className="notice" role="status">
              {notice}
            </p>
          )}

          <UploadCard
            title="매출 CSV 올리기"
            desc="POS에서 내려받은 판매 내역을 올리면 매출이 저장되고, 판매된 메뉴는 메뉴 화면에 자동으로 등록돼요."
            columns={COLUMNS}
            optional={OPTIONAL}
            busy={busy}
            onUpload={upload}
          />

          {uploadError && <UploadAlert message={uploadError} icon={CloudAlert} />}

          {result && <ResultCard result={result} />}

          {summary && summary.lineCount === 0 && !result && (
            <div className="card card-state">
              <StateBlock
                icon={Receipt}
                title="아직 저장된 매출이 없어요"
                text="위에서 CSV를 올리면 기간·영수증 수·매출 합계가 여기에 정리돼요."
              />
            </div>
          )}

          {summary && <UploadHistory uploads={summary.uploads} onDelete={deleteUpload} />}
        </>
      )}
    </div>
  )
}
