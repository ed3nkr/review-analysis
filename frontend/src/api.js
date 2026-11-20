// 백엔드 API 호출 모음. 오류는 Spring의 ProblemDetail(detail, errors)을 읽어 메시지로 만든다.

async function request(method, url, body) {
  const isForm = body instanceof FormData // 파일 업로드는 브라우저가 Content-Type을 직접 붙인다
  const res = await fetch(url, {
    method,
    headers: body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : undefined,
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  })
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    let message = (data && (data.detail || data.error)) || `요청 실패 (${res.status})`
    if (data && Array.isArray(data.errors) && data.errors.length) {
      message += ' — ' + data.errors.join(', ')
    }
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return data
}

function safeJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  // 매장
  listStores: () => request('GET', '/api/stores'),
  getStore: (id) => request('GET', `/api/stores/${id}`),
  createStore: (body) => request('POST', '/api/stores', body),
  updateStore: (id, body) => request('PATCH', `/api/stores/${id}`, body),
  deleteStore: (id) => request('DELETE', `/api/stores/${id}`),

  // 메뉴
  listMenuGroups: (storeId) => request('GET', `/api/stores/${storeId}/menu-groups`),
  updateAliases: (storeId, groupId, aliases) =>
    request('PUT', `/api/stores/${storeId}/menu-groups/${groupId}/aliases`, { aliases }),
  updateMenu: (storeId, menuId, body) => request('PATCH', `/api/stores/${storeId}/menus/${menuId}`, body), // { groupName } 또는 { discontinued }
  deleteMenu: (storeId, menuId) => request('DELETE', `/api/stores/${storeId}/menus/${menuId}`),

  // 매출
  getSalesSummary: (storeId) => request('GET', `/api/stores/${storeId}/sales/summary`),
  deleteSalesUpload: (storeId, jobId) => request('DELETE', `/api/stores/${storeId}/sales/uploads/${jobId}`),
  deleteAllSales: (storeId) => request('DELETE', `/api/stores/${storeId}/sales`),
  uploadSales: (storeId, file) => {
    const form = new FormData()
    form.append('file', file)
    return request('POST', `/api/stores/${storeId}/sales/upload`, form)
  },

  // 리뷰
  getReviewSummary: (storeId) => request('GET', `/api/stores/${storeId}/reviews/summary`),
  collectReviews: (storeId) => request('POST', `/api/stores/${storeId}/reviews/collect`),

  // 분석·리포트
  runAnalysis: (storeId) => request('POST', `/api/stores/${storeId}/analysis`),
  getLatestAnalysis: (storeId) => request('GET', `/api/stores/${storeId}/analysis/latest`),
  evaluateInsight: (storeId, insightId, body) =>
    request('PUT', `/api/stores/${storeId}/insights/${insightId}/evaluation`, body), // { evalNew, evalCorrect, evalAction, evalComment }

  // 장소 검색 (카카오)
  searchPlaces: (query) => request('GET', `/api/places/search?query=${encodeURIComponent(query)}`),
}
