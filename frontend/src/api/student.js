import request from './request'

/**
 * GET /api/students
 * Returns { students: [{id, name}, ...] }
 */
export function getStudents() {
  return request.get('/students')
}
