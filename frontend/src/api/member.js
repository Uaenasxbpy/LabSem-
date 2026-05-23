import request from './request'

/**
 * GET /api/members
 * Returns { members: [...] }
 */
export function getMembers() {
  return request.get('/members')
}

/**
 * POST /api/members
 * Create a member. Params sent as form-urlencoded.
 */
export function createMember(data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.post('/members', params)
}

/**
 * PUT /api/members/{id}
 * Update a member. Params sent as form-urlencoded.
 */
export function updateMember(id, data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.put(`/members/${id}`, params)
}

/**
 * DELETE /api/members/{id}
 */
export function deleteMember(id) {
  return request.delete(`/members/${id}`)
}
