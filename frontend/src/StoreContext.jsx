import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api } from './api'

// 현재 선택된 매장. 메뉴·매출·리뷰·리포트 화면이 모두 이 매장을 기준으로 동작한다.
const StoreContext = createContext(null)
const KEY = 'selectedStoreId'

function readSaved() {
  try {
    const v = localStorage.getItem(KEY)
    return v ? Number(v) : null
  } catch {
    return null
  }
}

export function StoreProvider({ children }) {
  const [stores, setStores] = useState([])
  const [selectedId, setSelectedId] = useState(readSaved)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    try {
      const list = await api.listStores()
      setStores(list)
      setError(null)
      // 저장된 선택이 없거나 사라졌으면 첫 매장 선택
      setSelectedId((cur) => (list.some((s) => s.id === cur) ? cur : list[0]?.id ?? null))
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  useEffect(() => {
    try {
      if (selectedId != null) localStorage.setItem(KEY, String(selectedId))
    } catch {
      /* 저장 실패는 무시 */
    }
  }, [selectedId])

  const selected = stores.find((s) => s.id === selectedId) ?? null

  return (
    <StoreContext.Provider value={{ stores, selected, selectedId, setSelectedId, reload, error }}>
      {children}
    </StoreContext.Provider>
  )
}

export function useStores() {
  return useContext(StoreContext)
}
