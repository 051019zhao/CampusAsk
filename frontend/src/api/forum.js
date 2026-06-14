import request from '@/utils/request'

/** 获取帖子列表（分页） */
export function getForumPosts(page = 1, size = 10) {
  return request({
    url: '/api/forum/posts',
    method: 'get',
    params: { page, size }
  })
}

/** 获取帖子详情（含评论） */
export function getForumPostDetail(id) {
  return request({
    url: `/api/forum/posts/${id}`,
    method: 'get'
  })
}

/** 创建帖子 */
export function createForumPost(data) {
  return request({
    url: '/api/forum/posts',
    method: 'post',
    data
  })
}

/** 添加评论或回复 */
export function addForumComment(postId, content, parentId = null, replyToUserId = null) {
  const data = { content }
  if (parentId) data.parentId = parentId
  if (replyToUserId) data.replyToUserId = replyToUserId
  return request({
    url: `/api/forum/posts/${postId}/comments`,
    method: 'post',
    data
  })
}

/** 点赞/取消点赞（后端自动切换，防止重复点赞） */
export function likeForumPost(postId) {
  return request({
    url: `/api/forum/posts/${postId}/like`,
    method: 'post'
  })
}

/** 删除帖子 */
export function deleteForumPost(postId) {
  return request({
    url: `/api/forum/posts/${postId}`,
    method: 'delete'
  })
}
