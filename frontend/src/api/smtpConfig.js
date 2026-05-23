import request from './request'

/**
 * GET /api/smtp-config
 * Returns the SMTP configuration (password masked).
 */
export function getSmtpConfig() {
  return request.get('/smtp-config')
}

/**
 * PUT /api/smtp-config
 * Update SMTP configuration. Params sent as form-urlencoded.
 */
export function updateSmtpConfig(data) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (value != null) {
      params.append(key, String(value))
    }
  }
  return request.put('/smtp-config', params)
}
