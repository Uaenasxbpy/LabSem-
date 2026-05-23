import request from './request'

/**
 * GET /api/schedules
 * Optional params: month (yyyy-MM), status.
 * Returns { schedules: [...] }
 */
export function getSchedules(params) {
  return request.get('/schedules', { params })
}

/**
 * POST /api/schedules
 * Create a schedule. Params sent as form-urlencoded.
 */
export function createSchedule(data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.post('/schedules', params)
}

/**
 * PUT /api/schedules/{id}
 * Update a schedule. Params sent as form-urlencoded.
 */
export function updateSchedule(id, data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.put(`/schedules/${id}`, params)
}

/**
 * DELETE /api/schedules/{id}
 */
export function deleteSchedule(id) {
  return request.delete(`/schedules/${id}`)
}

/**
 * PUT /api/schedules/{id}/status
 * Update schedule status only.
 */
export function updateScheduleStatus(id, status) {
  const params = new URLSearchParams()
  params.append('status', status)
  return request.put(`/schedules/${id}/status`, params)
}
