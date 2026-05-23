import request from './request'

/**
 * GET /api/paper-pool
 * Optional params: status, keyword.
 * Returns { papers: [...] }
 */
export function getPaperPool(params) {
  return request.get('/paper-pool', { params })
}

/**
 * POST /api/paper-pool
 * Create a paper pool entry. Params sent as form-urlencoded.
 */
export function createPaperPool(data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.post('/paper-pool', params)
}

/**
 * PUT /api/paper-pool/{id}
 * Update a paper pool entry. Params sent as form-urlencoded.
 */
export function updatePaperPool(id, data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.put(`/paper-pool/${id}`, params)
}

/**
 * DELETE /api/paper-pool/{id}
 */
export function deletePaperPool(id) {
  return request.delete(`/paper-pool/${id}`)
}

/**
 * PUT /api/paper-pool/{id}/claim
 * Claim a paper for a student.
 */
export function claimPaper(id, claimedBy) {
  const params = new URLSearchParams()
  params.append('claimed_by', claimedBy)
  return request.put(`/paper-pool/${id}/claim`, params)
}

/**
 * PUT /api/paper-pool/{id}/unclaim
 * Unclaim a paper.
 */
export function unclaimPaper(id) {
  return request.put(`/paper-pool/${id}/unclaim`)
}
