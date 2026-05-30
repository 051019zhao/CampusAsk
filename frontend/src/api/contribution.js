import request from '@/utils/request'

export function submitContribution(data) {
  return request({
    url: '/api/contribute/submit', // 加上 /api，和后端对齐
    method: 'post',
    data
  })
}
