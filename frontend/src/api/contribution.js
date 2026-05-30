import request from '@/utils/request'

export function submitContribution(data) {
  return request({
    url: '/api/contribute/submit',
    method: 'post',
    data
  })
}

export function getSensitiveWords() {
  return request({
    url: '/api/contribute/sensitive-words',
    method: 'get'
  })
}
