import request from './request'

/**
 * GET /api/logs
 * Optional params: ip, keyword, start_date, end_date, limit.
 * Returns a raw array of access log entries.
 */
export function getLogs(params) {
  return request.get('/logs', { params })
}
