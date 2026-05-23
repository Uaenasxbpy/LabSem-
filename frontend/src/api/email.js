import request from './request'

/**
 * POST /api/emails/send
 * Send a custom email. JSON body: { member_ids, file_ids, subject, body }
 */
export function sendEmail(data) {
  return request.post('/emails/send', data)
}
