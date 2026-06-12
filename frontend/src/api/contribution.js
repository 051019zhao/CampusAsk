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

/** 实时敏感内容检测（DeepSeek） */
export function checkSensitiveText(text) {
  return request({
    url: '/api/contribute/check-sensitive',
    method: 'post',
    data: { text }
  })
}

export function getRejectedContributions() {
  return request({
    url: '/api/contribute/rejected',
    method: 'get'
  })
}

export function getMyContributions() {
  return request({
    url: '/api/contribute/my-contributions',
    method: 'get'
  })
}
