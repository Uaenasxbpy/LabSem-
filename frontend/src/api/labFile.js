import request from './request'

/**
 * GET /api/lab-files
 * Optional params: keyword, tag.
 * Returns { files: [...] }
 */
export function getLabFiles(params) {
  return request.get('/lab-files', { params })
}

/**
 * GET /api/lab-files/tags
 * Returns a list of unique tag strings.
 */
export function getLabFileTags() {
  return request.get('/lab-files/tags')
}

/**
 * POST /api/lab-files
 * Upload a lab file (FormData with file).
 */
export function createLabFile(formData) {
  return request.post('/lab-files', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * PUT /api/lab-files/{id}
 * Update lab file metadata. Params sent as form-urlencoded.
 */
export function updateLabFile(id, data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, value)
    }
  }
  return request.put(`/lab-files/${id}`, params)
}

/**
 * DELETE /api/lab-files/{id}
 */
export function deleteLabFile(id) {
  return request.delete(`/lab-files/${id}`)
}
