<template>
  <div class="interaction-container">
    <header class="nav-header">
      <div class="nav-logo" @click="goBack">⬅️ 返回用户中心</div>
      <h3 class="page-title">我的茶话会动态（点赞 / 评论）</h3>
    </header>

    <main class="main-body">
      <div class="tab-header">
        <div class="tab-item" :class="{ active: currentTab === 'like' }" @click="currentTab = 'like'">
          ❤️ 我点赞的帖子 ({{ likeTotal }})
        </div>
        <div class="tab-item" :class="{ active: currentTab === 'comment' }" @click="currentTab = 'comment'">
          💬 我发表的评论 ({{ commentTotal }})
        </div>
      </div>

      <div class="content-card">
        <div v-if="currentTab === 'like'">
          <div v-if="likeList.length > 0" class="list-wrapper">
            <div v-for="like in likeList" :key="like.id" class="data-row">
              <div class="row-main">
                <span class="heart-icon">❤️</span>
                <span class="post-link" @click="goToPost(like.postId)">您点赞了帖子：【{{ like.postTitle || '查看详情' }}】</span>
              </div>
              <span class="row-time">{{ formatTime(like.createdAt) }}</span>
            </div>
          </div>
          <div v-else class="empty-box">暂无点赞帖子记录 💨</div>
        </div>

        <div v-if="currentTab === 'comment'">
          <div v-if="commentList.length > 0" class="list-wrapper">
            <div v-for="cmt in commentList" :key="cmt.id" class="data-row comment-row">
              <div class="comment-top">
                <span class="comment-text">💬 我评论道：{{ cmt.content }}</span>
                <span class="row-time">{{ formatTime(cmt.createdAt) }}</span>
              </div>
              <div class="comment-source" @click="goToPost(cmt.postId)">
                原帖：{{ cmt.postTitle || '点击查看帖子原文' }}
              </div>
            </div>
          </div>
          <div v-else class="empty-box">暂无评论发表记录 💨</div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const currentTab = ref('like') // 默认看点赞

const likeList = ref([])
const likeTotal = ref(0)
const commentList = ref([])
const commentTotal = ref(0)

onMounted(() => {
  fetchMyLikes()
  fetchMyComments()
})

// 对应去拉取用户的点赞记录
const fetchMyLikes = async () => {
  try {
    const res = await request.get('/auth/my-likes') // 后端对应写个简单拉点赞的联调接口
    if (res && res.code === 200) {
      likeList.value = res.data || []
      likeTotal.value = likeList.value.length
    }
  } catch (error) { console.error(error) }
}

// 对应去拉取用户的评论记录
const fetchMyComments = async () => {
  try {
    const res = await request.get('/auth/my-comments') // 后端对应写个获取评论记录的接口
    if (res && res.code === 200) {
      commentList.value = res.data || []
      commentTotal.value = commentList.value.length
    }
  } catch (error) { console.error(error) }
}

const formatTime = (timeStr) => timeStr ? timeStr.replace('T', ' ').substring(0, 16) : ''
const goBack = () => { router.push('/user-center') }
const goToPost = (id) => { router.push(`/forum/post/${id}`) } // 联动跳去茶话会帖子页面
</script>

<style scoped>
.interaction-container { background-color: #f5f7f6; min-height: 100vh; }
.nav-header { background-color: white; height: 60px; padding: 0 30px; display: flex; align-items: center; gap: 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.nav-logo { color: #11b980; font-weight: bold; cursor: pointer; }
.page-title { margin: 0; font-size: 16px; color: #333; }
.main-body { max-width: 800px; margin: 30px auto; padding: 0 20px; }

.tab-header { display: flex; gap: 10px; margin-bottom: 15px; }
.tab-item { background-color: #eef1f0; padding: 12px 24px; border-radius: 8px 8px 0 0; cursor: pointer; font-size: 14px; color: #555; font-weight: 500; transition: all 0.2s; }
.tab-item.active { background-color: white; color: #11b980; font-weight: bold; border-top: 3px solid #11b980; }

.content-card { background: white; border-radius: 0 0 12px 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.03); padding: 30px; min-height: 400px; }
.list-wrapper { display: flex; flex-direction: column; gap: 15px; }
.data-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px dashed #eee; }
.row-main { display: flex; align-items: center; gap: 10px; }
.post-link { font-size: 14px; color: #333; cursor: pointer; }
.post-link:hover { color: #11b980; text-decoration: underline; }
.row-time { font-size: 12px; color: #999; }

.comment-row { flex-direction: column; align-items: flex-start; gap: 6px; }
.comment-top { display: flex; justify-content: space-between; width: 100%; }
.comment-text { font-size: 14px; color: #222; font-weight: 500; }
.comment-source { font-size: 12px; color: #0088cc; background-color: #e6f7ff; padding: 4px 10px; border-radius: 4px; cursor: pointer; margin-top: 4px; }

.empty-box { text-align: center; color: #aaa; font-size: 14px; padding-top: 100px; }
</style>