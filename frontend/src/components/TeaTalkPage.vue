<template>
  <div class="teatalk-page">
    <!-- ===== 左侧边栏 ===== -->
    <aside class="side-panel">
      <div class="brand">
        <div class="brand-badge"></div>
        <div>
          <div class="brand-title">CampusAsk</div>
          <div class="brand-sub">茶话会</div>
        </div>
      </div>
      <nav class="side-nav">
        <button class="nav-item active">
          <span class="nav-icon">💬</span> 茶话会
        </button>
        <button class="nav-item" @click="goHome">
          <span class="nav-icon">🏠</span> 校园问答
        </button>
        <button class="nav-item" @click="goUserCenter">
          <span class="nav-icon">👤</span> 个人中心
        </button>
      </nav>
      <div class="side-group">
        <div class="side-title">热门标签</div>
        <div class="side-list">
          <div
            v-for="tag in hotTags"
            :key="tag"
            class="side-tag"
            @click="searchTag(tag)"
          >
            # {{ tag }}
          </div>
        </div>
      </div>
      <button class="post-btn" @click="showCreatePost = true">📝 发帖</button>
    </aside>

    <!-- ===== 中间内容区 ===== -->
    <main class="content-panel">
      <!-- 帖子列表 -->
      <div v-if="!showDetail && !showCreatePost" class="post-feed">
        <div class="feed-header">
          <h2 class="feed-title">✨ 茶话会</h2>
          <span class="feed-count">共 {{ totalPosts }} 条讨论</span>
        </div>

        <!-- 骨架屏 / 加载状态 -->
        <div v-if="loading" class="post-list">
          <div v-for="n in 3" :key="n" class="skeleton-card">
            <div class="skeleton-line w-40"></div>
            <div class="skeleton-line w-80"></div>
            <div class="skeleton-line w-60"></div>
          </div>
        </div>

        <div v-else-if="postList.length === 0" class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-text">还没有帖子，快来发布第一条吧！</div>
        </div>

        <button
          v-else-if="postError"
          class="ui-state ui-state-error retry-btn"
          type="button"
          @click="loadPosts"
        >
          {{ postError }}（点击重试）
        </button>

        <transition-group v-else name="list" tag="div" class="post-list">
          <article
            v-for="post in postList"
            :key="post.id"
            class="post-card"
            @click="openPost(post.id)"
          >
            <div class="post-header">
              <div class="post-avatar">{{ post.nickname ? post.nickname.charAt(0) : '?' }}</div>
              <div class="post-meta">
                <span class="post-author">{{ post.nickname || '匿名用户' }}</span>
                <span class="post-time">{{ formatTime(post.createdAt) }}</span>
              </div>
            </div>
            <h3 class="post-title">{{ post.title }}</h3>
            <p class="post-excerpt">{{ truncate(post.content, 120) }}</p>
            <div class="post-footer">
              <span class="post-stat">👁️ {{ post.viewCount }}</span>
              <span class="post-stat">👍 {{ post.likeCount }}</span>
              <span class="post-stat">💬 {{ post.commentCount || 0 }}</span>
            </div>
          </article>
        </transition-group>

        <!-- 加载更多 -->
        <div v-if="hasMore" class="load-more">
          <button class="load-more-btn" @click="loadMore">加载更多</button>
        </div>
      </div>

      <!-- ===== 帖子详情 + 评论 ===== -->
      <div v-if="showDetail && currentPost" class="post-detail">
        <button class="back-btn" @click="closePost">← 返回列表</button>

        <article class="detail-card">
          <div class="detail-header">
            <div class="post-avatar large">{{ currentPost.nickname ? currentPost.nickname.charAt(0) : '?' }}</div>
            <div class="post-meta">
              <span class="post-author">{{ currentPost.nickname || '匿名用户' }}</span>
              <span class="post-time">{{ formatTime(currentPost.createdAt) }}</span>
            </div>
            <button
              v-if="currentPost.userId === currentUserId"
              class="delete-btn"
              @click="handleDelete(currentPost.id)"
            >🗑️</button>
          </div>
          <h2 class="detail-title">{{ currentPost.title }}</h2>
          <p class="detail-content">{{ currentPost.content }}</p>
          <div class="detail-stats">
            <span>👁️ {{ currentPost.viewCount }}</span>
            <button
              class="like-btn"
              :class="{ liked: currentPost.liked }"
              @click="handleLike(currentPost.id)"
            >
              {{ currentPost.liked ? '❤️' : '🤍' }} {{ currentPost.likeCount }}
            </button>
            <span>💬 {{ totalCommentCount }}</span>
          </div>
        </article>

        <!-- 评论列表（支持回复） -->
        <div class="comments-section">
          <h3 class="comments-title">全部评论 ({{ totalCommentCount }})</h3>

          <div v-if="comments.length === 0" class="empty-comments">
            暂无评论，来说两句吧～
          </div>

          <div v-for="comment in comments" :key="comment.id" class="comment-thread">
            <!-- 顶级评论 -->
            <div class="comment-item">
              <div class="comment-avatar">{{ comment.nickname ? comment.nickname.charAt(0) : '?' }}</div>
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-author">{{ comment.nickname || '匿名用户' }}</span>
                  <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-actions">
                  <button class="reply-toggle-btn" @click="startReply(comment)">
                    💬 回复
                  </button>
                </div>
                <!-- 内联回复框 -->
                <div v-if="replyTarget && replyTarget.commentId === comment.id" class="inline-reply-box">
                  <textarea
                    v-model="replyText"
                    :placeholder="'回复 @' + replyTarget.nickname + '...'"
                    rows="2"
                    @keyup.enter.exact.prevent="submitReply"
                  ></textarea>
                  <div class="inline-reply-actions">
                    <button class="cancel-reply-btn" @click="cancelReply">取消</button>
                    <button class="send-reply-btn" @click="submitReply">发送</button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 子回复列表（嵌套显示） -->
            <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
              <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                <div class="comment-avatar small">{{ reply.nickname ? reply.nickname.charAt(0) : '?' }}</div>
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="comment-author">{{ reply.nickname || '匿名用户' }}</span>
                    <span v-if="reply.replyToNickname" class="reply-to">
                      <span class="reply-arrow">▶</span> @{{ reply.replyToNickname }}
                    </span>
                    <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
                  </div>
                  <p class="comment-text">{{ reply.content }}</p>
                  <div class="comment-actions">
                    <button class="reply-toggle-btn" @click="startReply(reply)">
                      💬 回复
                    </button>
                  </div>
                  <!-- 内联回复框（对子回复的回复） -->
                  <div v-if="replyTarget && replyTarget.commentId === reply.id" class="inline-reply-box">
                    <textarea
                      v-model="replyText"
                      :placeholder="'回复 @' + replyTarget.nickname + '...'"
                      rows="2"
                      @keyup.enter.exact.prevent="submitReply"
                    ></textarea>
                    <div class="inline-reply-actions">
                      <button class="cancel-reply-btn" @click="cancelReply">取消</button>
                      <button class="send-reply-btn" @click="submitReply">发送</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 顶级评论输入框 -->
          <div class="comment-composer">
            <textarea
              v-model="commentText"
              placeholder="写下你的评论..."
              rows="2"
              @keyup.enter.exact.prevent="submitComment"
            ></textarea>
            <button class="send-comment-btn" @click="submitComment">发送</button>
          </div>
        </div>
      </div>

      <!-- ===== 创建帖子弹窗 ===== -->
      <div v-if="showCreatePost" class="post-detail">
        <button class="back-btn" @click="showCreatePost = false">← 取消</button>

        <div class="create-post-card">
          <h2 class="create-title">📝 发布新帖子</h2>
          <div class="form-group">
            <label>标题</label>
            <input
              v-model="newPost.title"
              placeholder="给你的帖子起个标题..."
              class="form-input"
              maxlength="100"
            />
          </div>
          <div class="form-group">
            <label>内容</label>
            <textarea
              v-model="newPost.content"
              placeholder="分享你的想法、问题或见闻..."
              rows="8"
              class="form-textarea"
            ></textarea>
          </div>
          <div class="create-actions">
            <span class="char-count">{{ newPost.content.length }} 字</span>
            <button class="publish-btn" :disabled="submitting" @click="publishPost">
              {{ submitting ? '发布中...' : '✨ 发布' }}
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- ===== 右侧热门面板 ===== -->
    <aside class="hot-panel">
      <div class="hot-title">🔥 热门帖子</div>
      <div class="hot-list">
        <div v-if="hotLoading" class="hot-state ui-state ui-state-loading">加载中...</div>
        <button
          v-else-if="hotError"
          class="hot-state ui-state ui-state-error"
          type="button"
          @click="loadHotPosts"
        >
          {{ hotError }}（点击重试）
        </button>
        <div v-else-if="hotPosts.length === 0" class="hot-state ui-state ui-state-empty">暂无数据</div>
        <button
          v-else
          v-for="(post, index) in hotPosts"
          :key="post.id"
          class="hot-item"
          @click="openPost(post.id)"
        >
          <span class="hot-rank">{{ index + 1 }}</span>
          <span class="hot-text">{{ post.title }}</span>
        </button>
      </div>
    </aside>
  </div>
</template>

<script>
import {
  getForumPosts,
  getForumPostDetail,
  createForumPost,
  addForumComment,
  likeForumPost,
  deleteForumPost
} from '@/api/forum'
import { toastError, toastInfo, toastSuccess } from '@/utils/toast'

export default {
  name: 'TeaTalkPage',
  data() {
    return {
      // 帖子列表
      postList: [],
      totalPosts: 0,
      currentPage: 1,
      pageSize: 10,
      hasMore: false,
      loading: false,
      postError: '',

      // 详情
      showDetail: false,
      currentPost: null,
      comments: [],
      commentText: '',

      // 回复
      replyTarget: null,   // { commentId, nickname, userId }
      replyText: '',

      // 创建帖子
      showCreatePost: false,
      newPost: { title: '', content: '' },
      submitting: false,

      // 右侧热门
      hotPosts: [],
      hotLoading: false,
      hotError: '',

      hotTags: ['校园生活', '学习交流', '情感树洞', '二手交易', '社团活动', '求助']
    }
  },
  computed: {
    currentUserId() {
      return localStorage.getItem('username') || null
    },
    totalCommentCount() {
      let count = 0
      for (const c of this.comments) {
        count++
        if (c.replies) count += c.replies.length
      }
      return count
    }
  },
  mounted() {
    this.loadPosts()
    this.loadHotPosts()
  },
  methods: {
    async loadPosts() {
      this.loading = true
      this.postError = ''
      try {
        const res = await getForumPosts(this.currentPage, this.pageSize)
        if (res.code === 200) {
          this.postList = res.data.list || []
          this.totalPosts = res.data.total || 0
          this.hasMore = this.currentPage < (res.data.totalPages || 1)
        } else {
          this.postError = '帖子加载失败'
        }
      } catch (e) {
        console.error('加载帖子失败:', e)
        this.postError = '帖子加载失败'
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      this.currentPage++
      try {
        const res = await getForumPosts(this.currentPage, this.pageSize)
        if (res.code === 200) {
          this.postList = [...this.postList, ...(res.data.list || [])]
          this.hasMore = this.currentPage < (res.data.totalPages || 1)
        }
      } catch (e) {
        this.currentPage--
        console.error('加载更多失败:', e)
      }
    },
    async openPost(id) {
      try {
        const res = await getForumPostDetail(id)
        if (res.code === 200) {
          this.currentPost = res.data.post
          this.comments = res.data.comments || []
          this.showDetail = true
          this.showCreatePost = false
          // 刷新列表中的浏览量
          const found = this.postList.find(p => p.id === id)
          if (found) found.viewCount = (found.viewCount || 0) + 1
        }
      } catch (e) {
        console.error('加载帖子详情失败:', e)
      }
    },
    closePost() {
      this.showDetail = false
      this.currentPost = null
      this.comments = []
      this.commentText = ''
      this.replyTarget = null
      this.replyText = ''
    },
    async submitComment() {
      const text = this.commentText.trim()
      if (!text || !this.currentPost) return
      try {
        const res = await addForumComment(this.currentPost.id, text)
        if (res.code === 200) {
          await this.refreshComments()
          this.commentText = ''
        }
      } catch (e) {
        console.error('提交评论失败:', e)
      }
    },
    /** 点击某条评论的"回复"按钮 */
    startReply(comment) {
      this.replyTarget = {
        commentId: comment.id,
        nickname: comment.nickname || '匿名用户',
        userId: comment.userId
      }
      this.replyText = ''
    },
    /** 取消回复 */
    cancelReply() {
      this.replyTarget = null
      this.replyText = ''
    },
    /** 提交子回复 */
    async submitReply() {
      const text = this.replyText.trim()
      if (!text || !this.currentPost || !this.replyTarget) return
      try {
        // 确定父评论ID：如果当前回复目标本身就是顶级评论，parentId就是它；否则找它的parentId
        const targetComment = this.findCommentById(this.replyTarget.commentId)
        const parentId = targetComment && targetComment.parentId
          ? targetComment.parentId
          : this.replyTarget.commentId

        const res = await addForumComment(
          this.currentPost.id,
          text,
          parentId,
          this.replyTarget.userId
        )
        if (res.code === 200) {
          await this.refreshComments()
          this.cancelReply()
        }
      } catch (e) {
        console.error('提交回复失败:', e)
      }
    },
    /** 在评论树中查找指定ID的评论 */
    findCommentById(id) {
      for (const c of this.comments) {
        if (c.id === id) return c
        if (c.replies) {
          const found = c.replies.find(r => r.id === id)
          if (found) return found
        }
      }
      return null
    },
    /** 重新加载评论 */
    async refreshComments() {
      if (!this.currentPost) return
      try {
        const detail = await getForumPostDetail(this.currentPost.id)
        if (detail.code === 200) {
          this.comments = detail.data.comments || []
          // 更新列表中的评论数
          const found = this.postList.find(p => p.id === this.currentPost.id)
          if (found) {
            found.commentCount = this.totalCommentCount
          }
          if (this.currentPost) {
            this.currentPost.commentCount = this.totalCommentCount
          }
        }
      } catch (e) {
        console.error('刷新评论失败:', e)
      }
    },
    async handleLike(postId) {
      try {
        const res = await likeForumPost(postId)
        if (res.code === 200) {
          const isLiked = res.data.liked
          // 更新当前帖子的点赞状态
          if (this.currentPost && this.currentPost.id === postId) {
            this.currentPost.liked = isLiked
            this.currentPost.likeCount += isLiked ? 1 : -1
          }
          const found = this.postList.find(p => p.id === postId)
          if (found) {
            found.liked = isLiked
            found.likeCount += isLiked ? 1 : -1
          }
        }
      } catch (e) {
        console.error('点赞失败:', e)
      }
    },
    async handleDelete(postId) {
      if (!confirm('确定要删除这个帖子吗？')) return
      try {
        const res = await deleteForumPost(postId)
        if (res.code === 200) {
          this.postList = this.postList.filter(p => p.id !== postId)
          this.totalPosts--
          this.closePost()
        }
      } catch (e) {
        console.error('删除失败:', e)
      }
    },
    async publishPost() {
      if (!this.newPost.title.trim() || !this.newPost.content.trim()) {
        toastError('标题和内容不能为空')
        return
      }
      this.submitting = true
      try {
        const res = await createForumPost({
          title: this.newPost.title.trim(),
          content: this.newPost.content.trim()
        })
        if (res.code === 200) {
          toastSuccess('发布成功')
          this.newPost = { title: '', content: '' }
          this.showCreatePost = false
          this.currentPage = 1
          await this.loadPosts()
        }
      } catch (e) {
        console.error('发布失败:', e)
        toastError('发布失败，请检查登录状态')
      } finally {
        this.submitting = false
      }
    },
    async loadHotPosts() {
      this.hotLoading = true
      this.hotError = ''
      try {
        const res = await getForumPosts(1, 5)
        if (res.code === 200) {
          const list = res.data.list || []
          this.hotPosts = [...list].sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0)).slice(0, 5)
        } else {
          this.hotPosts = []
          this.hotError = '热门加载失败'
        }
      } catch (e) {
        console.error('加载热门失败:', e)
        this.hotPosts = []
        this.hotError = '热门加载失败'
      } finally {
        this.hotLoading = false
      }
    },
    searchTag(tag) {
      // 后续可扩展为按标签搜索
      toastInfo('搜索标签：' + tag + '（功能开发中）')
    },
    goHome() {
      this.$router.push({ name: 'QaPage' })
    },
    goUserCenter() {
      this.$router.push({ name: 'UserCenter' })
    },
    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${month}-${day}`
    },
    truncate(text, len) {
      if (!text) return ''
      return text.length > len ? text.slice(0, len) + '...' : text
    }
  }
}
</script>

<style scoped>
/* ===== 全局布局 ===== */
.teatalk-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 0;
  box-sizing: border-box;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
  color: #1c2b35;
  overflow-y: auto;
}

.teatalk-page {
  max-width: none;
  min-height: 100vh;
  margin: 0;
  display: grid;
  grid-template-columns: 200px 1fr 200px;
  gap: 14px;
  background: #f8fbfd;
  padding: 12px;
}

/* ===== 左侧边栏 ===== */
.side-panel {
  background: #3c6476;
  border-radius: 16px;
  padding: 12px 12px;
  color: #e8f5fb;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #b5d7e6;
}

.brand-title {
  font-size: 18px;
  font-weight: 600;
}

.brand-sub {
  font-size: 12px;
  opacity: 0.8;
}

.side-nav {
  display: grid;
  gap: 10px;
}

.nav-item {
  background: rgba(255, 255, 255, 0.12);
  border: none;
  border-radius: 10px;
  padding: 9px 12px;
  color: #eef7fb;
  text-align: left;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.22);
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.28);
  font-weight: 600;
}

.nav-icon {
  font-size: 16px;
}

.side-group {
  display: grid;
  gap: 10px;
}

.side-title {
  font-size: 12px;
  opacity: 0.85;
}

.side-list {
  display: grid;
  gap: 6px;
}

.side-tag {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
}

.side-tag:hover {
  background: rgba(255, 255, 255, 0.25);
}

.post-btn {
  border: none;
  background: #e8a87c;
  color: #fff;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  margin-top: auto;
  width: 100%;
}

.post-btn:hover {
  background: #d9956a;
}

/* ===== 中间内容区 ===== */
.content-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

/* ===== 帖子列表 ===== */
.feed-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 0 4px;
}

.feed-title {
  font-size: 20px;
  font-weight: 700;
  color: #2c3e50;
  margin: 0;
}

.feed-count {
  font-size: 13px;
  color: #7a8b97;
}

.post-list {
  display: grid;
  gap: 12px;
}

.post-card {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.post-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.post-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.post-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #b5d7e6;
  color: #3c6476;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}

.post-avatar.large {
  width: 44px;
  height: 44px;
  font-size: 20px;
}

.post-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.post-author {
  font-size: 14px;
  font-weight: 600;
  color: #2c3e50;
}

.post-time {
  font-size: 12px;
  color: #8a9ba8;
}

.post-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
  color: #1c2b35;
  line-height: 1.4;
}

.post-excerpt {
  font-size: 13px;
  color: #4f6573;
  line-height: 1.6;
  margin: 0 0 12px 0;
}

.post-footer {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #7a8b97;
}

.post-stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ===== 骨架屏 ===== */
.skeleton-card {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  display: grid;
  gap: 10px;
}

.skeleton-line {
  height: 14px;
  border-radius: 8px;
  background: linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-line.w-40 { width: 40%; }
.skeleton-line.w-60 { width: 60%; }
.skeleton-line.w-80 { width: 80%; }

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

/* ===== 空状态 ===== */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #8a9ba8;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
}

/* ===== 加载更多 ===== */
.load-more {
  text-align: center;
  padding: 12px;
}

.load-more-btn {
  border: 1px solid #d0d9e0;
  border-radius: 20px;
  padding: 8px 24px;
  background: #fff;
  color: #3c6476;
  font-size: 13px;
  cursor: pointer;
}

.load-more-btn:hover {
  background: #f0f4f8;
}

/* ===== 帖子详情 ===== */
.post-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.back-btn {
  align-self: flex-start;
  border: none;
  background: #fff;
  border-radius: 10px;
  padding: 8px 16px;
  font-size: 13px;
  color: #3c6476;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.back-btn:hover {
  background: #f0f4f8;
}

.detail-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.delete-btn {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
}

.delete-btn:hover {
  background: #fee2e2;
}

.detail-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 12px 0;
  color: #1c2b35;
  line-height: 1.4;
}

.detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: #2c3e50;
  margin: 0 0 16px 0;
  white-space: pre-wrap;
}

.detail-stats {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #7a8b97;
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid #f0f2f5;
}

.like-btn {
  border: none;
  background: transparent;
  font-size: 14px;
  color: #7a8b97;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
}

.like-btn:hover {
  background: #fdf2f2;
}

.like-btn.liked {
  color: #e74c3c;
}

/* ===== 评论 ===== */
.comments-section {
  background: #fff;
  border-radius: 14px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.comments-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 14px 0;
  color: #2c3e50;
}

.empty-comments {
  text-align: center;
  padding: 30px 0;
  color: #8a9ba8;
  font-size: 13px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 6px 0;
}

.comment-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #d4e0e6;
  color: #3c6476;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
  color: #2c3e50;
}

.comment-time {
  font-size: 11px;
  color: #8a9ba8;
}

.comment-text {
  font-size: 13px;
  color: #3c5461;
  line-height: 1.6;
  margin: 0;
}

.comment-composer {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  align-items: flex-end;
}

.comment-composer textarea {
  flex: 1;
  border: 1px solid #d0d9e0;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: #f8fafc;
  min-height: 40px;
}

.comment-composer textarea:focus {
  border-color: #3c6476;
  background: #fff;
}

.send-comment-btn {
  border: none;
  background: #3c6476;
  color: #fff;
  border-radius: 12px;
  padding: 10px 18px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.send-comment-btn:hover {
  background: #2b5569;
}

/* ===== 评论回复 ===== */
.comment-thread {
  border-bottom: 1px solid #f0f2f5;
  padding: 12px 0;
}

.comment-thread:last-child {
  border-bottom: none;
}

.comment-actions {
  margin-top: 6px;
}

.reply-toggle-btn {
  border: none;
  background: transparent;
  color: #8a9ba8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.reply-toggle-btn:hover {
  color: #3c6476;
  background: #f0f4f8;
}

/* 内联回复框 */
.inline-reply-box {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.inline-reply-box textarea {
  width: 100%;
  border: 1px solid #d0d9e0;
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: #f8fafc;
  box-sizing: border-box;
  min-height: 36px;
}

.inline-reply-box textarea:focus {
  border-color: #3c6476;
  background: #fff;
}

.inline-reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.cancel-reply-btn {
  border: 1px solid #d0d9e0;
  background: #fff;
  color: #7a8b97;
  border-radius: 8px;
  padding: 5px 14px;
  font-size: 12px;
  cursor: pointer;
}

.cancel-reply-btn:hover {
  background: #f0f4f8;
}

.send-reply-btn {
  border: none;
  background: #3c6476;
  color: #fff;
  border-radius: 8px;
  padding: 5px 14px;
  font-size: 12px;
  cursor: pointer;
}

.send-reply-btn:hover {
  background: #2b5569;
}

/* 子回复列表 */
.replies-list {
  margin-top: 4px;
  margin-left: 44px;
  padding-left: 14px;
  border-left: 2px solid #e8ecf1;
  background: #fafbfc;
  border-radius: 0 10px 10px 0;
  padding-top: 6px;
  padding-bottom: 2px;
}

.reply-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
}

.reply-item:last-child {
  padding-bottom: 6px;
}

.comment-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 12px;
}

.reply-to {
  font-size: 12px;
  color: #3c6476;
  background: #eef4f8;
  border-radius: 4px;
  padding: 1px 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.reply-arrow {
  font-size: 10px;
}

/* ===== 创建帖子 ===== */
.create-post-card {
  background: #fff;
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.create-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 20px 0;
  color: #2c3e50;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #3c5461;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  border: 1px solid #d0d9e0;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #3c6476;
}

.form-textarea {
  width: 100%;
  border: 1px solid #d0d9e0;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  line-height: 1.6;
}

.form-textarea:focus {
  border-color: #3c6476;
}

.create-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.char-count {
  font-size: 12px;
  color: #8a9ba8;
}

.publish-btn {
  border: none;
  background: #e8a87c;
  color: #fff;
  border-radius: 12px;
  padding: 10px 28px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.publish-btn:hover {
  background: #d9956a;
}

.publish-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ===== 右侧热门 ===== */
.hot-panel {
  background: #3c6476;
  border-radius: 16px;
  padding: 16px 14px;
  color: #e8f5fb;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hot-title {
  font-size: 16px;
  font-weight: 600;
}

.hot-list {
  display: grid;
  gap: 8px;
}

.hot-item {
  display: flex;
  gap: 8px;
  align-items: center;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 7px 9px;
  border: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
  width: 100%;
}

.hot-item:hover {
  background: rgba(255, 255, 255, 0.2);
}

.hot-state {
  padding: 10px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  text-align: center;
}

.retry-btn {
  border: none;
  cursor: pointer;
}

.hot-rank {
  font-weight: 700;
  font-size: 14px;
  min-width: 20px;
}

.hot-text {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 过渡动画 ===== */
.list-enter-active {
  transition: all 0.3s ease;
}

.list-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

@media (max-width: 1100px) {
  .teatalk-page {
    grid-template-columns: 200px 1fr;
  }

  .hot-panel {
    display: none;
  }
}

@media (max-width: 900px) {
  .teatalk-page {
    grid-template-columns: 1fr;
    padding: 10px;
  }

  .side-panel {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: space-between;
  }

  .side-group {
    flex: 1 1 100%;
  }

  .comment-composer {
    flex-direction: column;
    align-items: stretch;
  }

  .send-comment-btn {
    width: 100%;
  }
}
</style>
