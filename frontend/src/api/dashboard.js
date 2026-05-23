import request from './request'

/**
 * GET /api/dashboard/stats
 * Returns overall dashboard statistics.
 */
export function getStats() {
  return request.get('/dashboard/stats')
}

/**
 * GET /api/dashboard/by-student
 * Returns per-student report and paper counts.
 */
export function getByStudent() {
  return request.get('/dashboard/by-student')
}

/**
 * GET /api/dashboard/monthly
 * Returns monthly report and paper counts.
 */
export function getMonthly() {
  return request.get('/dashboard/monthly')
}
