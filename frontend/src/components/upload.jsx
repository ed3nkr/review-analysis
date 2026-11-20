// 매출·리뷰 CSV 업로드 화면이 같이 쓰는 조각들
import { useRef, useState } from 'react'
import { FileText, Upload } from 'lucide-react'
import { Button } from './ui'

export const num = (n) => (n ?? 0).toLocaleString('ko-KR')
export const day = (d) => (d ? d.replaceAll('-', '.') : '')
export const period = (a, b) => {
  if (!a) return '없음'
  // 같은 해면 끝 날짜의 연도는 생략: 2026.01.01 – 06.30
  const end = a.slice(0, 4) === b.slice(0, 4) ? day(b).slice(5) : day(b)
  return `${day(a)} – ${end}`
}
const fileSize = (b) => (b >= 1024 * 1024 ? (b / 1024 / 1024).toFixed(1) + 'MB' : Math.max(1, Math.round(b / 1024)) + 'KB')
const when = (iso) => {
  const d = new Date(iso)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function Stat({ label, value, sub }) {
  return (
    <div className="stat">
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      {sub && <p className="stat-sub">{sub}</p>}
    </div>
  )
}

/** columns / optional: [['판매일시', 'sold_at'], ...] */
export function UploadCard({ title, desc, columns, optional = [], columnsTitle = '첫 줄에 이 열이 있어야 해요', busy, onUpload }) {
  const [file, setFile] = useState(null)
  const [drag, setDrag] = useState(false)
  const input = useRef(null)

  const pick = (f) => {
    if (f) setFile(f)
  }

  return (
    <section className="card">
      <div>
        <h2 className="card-title">{title}</h2>
        <p className="card-desc">{desc}</p>
      </div>

      <label
        className={'dropzone' + (drag ? ' is-drag' : '') + (file ? ' has-file' : '')}
        onDragOver={(e) => {
          e.preventDefault()
          setDrag(true)
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDrag(false)
          pick(e.dataTransfer.files?.[0])
        }}
      >
        <input
          ref={input}
          type="file"
          accept=".csv,text/csv"
          className="visually-hidden"
          onChange={(e) => pick(e.target.files?.[0])}
          disabled={busy}
        />
        <span className="dropzone-symbol" aria-hidden="true">
          {file ? <FileText size={24} /> : <Upload size={24} />}
        </span>
        {file ? (
          <span className="dropzone-text">
            <span className="dropzone-title">{file.name}</span>
            <span className="dropzone-sub">{fileSize(file.size)} · 다른 파일을 고르려면 여기를 누르세요</span>
          </span>
        ) : (
          <span className="dropzone-text">
            <span className="dropzone-title">CSV 파일을 끌어다 놓거나 눌러서 고르세요</span>
            <span className="dropzone-sub">20MB까지 올릴 수 있어요</span>
          </span>
        )}
      </label>

      <div className="upload-foot">
        <div className="columns-hint">
          <p className="columns-title">{columnsTitle}</p>
          <ul className="columns-list">
            {columns.map(([ko, en]) => (
              <li key={en} className="column-chip">
                {ko} <span className="column-en">{en}</span>
              </li>
            ))}
            {optional.map(([ko, en]) => (
              <li key={en} className="column-chip is-optional">
                {ko} <span className="column-en">{en} · 있으면 사용</span>
              </li>
            ))}
          </ul>
          <p className="columns-note">같은 파일을 다시 올려도 이미 저장된 줄은 건너뛰어요.</p>
        </div>
        <Button
          icon={Upload}
          disabled={!file || busy}
          onClick={() => {
            onUpload(file).then((ok) => {
              if (ok) {
                setFile(null)
                if (input.current) input.current.value = ''
              }
            })
          }}
        >
          {busy ? '올리는 중…' : '올리기'}
        </Button>
      </div>
    </section>
  )
}

export function RowErrors({ errors, errorRows }) {
  if (errors.length === 0) return null
  const others = errorRows - errors.length
  return (
    <div className="row-errors">
      <p className="row-errors-title">저장하지 못한 줄</p>
      <ul>
        {errors.map((e) => (
          <li key={e.line}>
            <span className="row-errors-line">{e.line}번째 줄</span>
            {e.reason}
          </li>
        ))}
      </ul>
      {others > 0 && <p className="row-errors-more">그 밖에 {num(others)}줄이 더 있어요.</p>}
    </div>
  )
}

function DeleteCell({ onDelete }) {
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!confirm) {
    return (
      <span className="history-actions">
        <Button size="sm" variant="quiet" onClick={() => setConfirm(true)}>
          삭제
        </Button>
      </span>
    )
  }
  return (
    <span className="history-actions">
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
        지우기
      </Button>
      <Button size="sm" variant="quiet" disabled={busy} onClick={() => setConfirm(false)}>
        취소
      </Button>
    </span>
  )
}

export function UploadHistory({ uploads, unit = '줄', title = '올린 기록', whenLabel = '올린 시각', fileLabel = '파일', onDelete }) {
  if (!uploads || uploads.length === 0) return null
  const row = 'history-row' + (onDelete ? ' has-actions' : '')
  return (
    <section className="other-stores">
      <h2 className="section-title">{title}</h2>
      <div className="card table-card">
        <div className={row + ' history-head'} aria-hidden="true">
          <span>{whenLabel}</span>
          <span>{fileLabel}</span>
          <span>기간</span>
          <span className="num-col">새로 저장</span>
          <span className="num-col">오류</span>
          {onDelete && <span />}
        </div>
        <ul>
          {uploads.map((u) => (
            <li key={u.jobId} className={row}>
              <span className="history-when">{when(u.requestedAt)}</span>
              <span className="history-file">{u.fileName || '—'}</span>
              <span>{period(u.periodStart, u.periodEnd)}</span>
              <span className="num-col">
                {num(u.savedRows)}
                {unit}
              </span>
              <span className={'num-col' + (u.errorRows > 0 ? ' is-warn' : '')}>{num(u.errorRows)}줄</span>
              {onDelete && <DeleteCell onDelete={() => onDelete(u.jobId)} />}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

/** 업로드 실패 안내 */
export function UploadAlert({ message, icon: Icon }) {
  return (
    <div className="alert alert-danger" role="alert">
      <Icon size={20} aria-hidden="true" />
      <div>
        <p className="alert-title">파일을 저장하지 못했어요</p>
        <p className="alert-text">{message}</p>
      </div>
    </div>
  )
}
