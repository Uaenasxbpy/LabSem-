import request from './request'

/**
 * POST /api/reports
 * Create a report with papers (FormData with file uploads).
 */
export function createReport(formData) {
  return request.post('/reports', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * GET /api/reports
 * Search reports with optional filters: student_name, keyword, start_date, end_date.
 */
export function searchReports(params) {
  return request.get('/reports', { params })
}

/**
 * DELETE /api/reports/{id}
 */
export function deleteReport(id) {
  return request.delete(`/reports/${id}`)
}

/**
 * POST /api/reports/{id}/notify
 * Send email notification for a report.
 */
export function notifyReport(id) {
  return request.post(`/reports/${id}/notify`)
}
