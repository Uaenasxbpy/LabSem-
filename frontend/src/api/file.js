import request from './request'

/**
 * GET /api/files
 * Returns a raw array of all stored files with report context.
 */
export function getFiles() {
  return request.get('/files')
}

/**
 * Get the preview URL for a file by id.
 */
export function getFilePreviewUrl(id) {
  return `/api/files/${id}/preview`
}

/**
 * Get the download URL for a file by id.
 */
export function getFileDownloadUrl(id) {
  return `/api/files/${id}/download`
}
