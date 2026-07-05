<template>
  <div class="teatalk-page">
    <!-- ===== 左侧边栏 ===== -->
    <aside class="side-panel">
      <div class="brand">
        <img src="/teachat.png" class="brand-badge" />
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
      <button class="post-btn" @click="showCreatePost = true; showDetail = false">📝 发帖</button>
    </aside>

    <!-- ===== 中间内容区 ===== -->
    <main class="content-panel">
      <!-- 帖子列表 -->
      <div v-if="!showDetail && !showCreatePost" class="post-feed">
        <div class="feed-header">
          <h2 class="feed-title">✨ 茶话会</h2>
          <span v-if="activeTag" class="tag-filter-badge">#{{ activeTag }}<button class="tag-clear-btn" @click="clearTagFilter">✕</button></span>
          <span class="feed-count">共 {{ totalPosts }} 条</span>
          <div class="search-box">
            <input v-model="searchKeyword" placeholder="搜索帖子..." @keyup.enter="searchPosts" />
            <button v-if="searchKeyword" class="search-clear" @click="exitSearch()">✕</button>
          </div>
        </div>

        <!-- 搜索状态提示 -->
        <div v-if="isSearching" class="search-hint">
          🔍 搜索 "{{ searchKeyword }}" 找到 {{ postList.length }} 条结果
          <button class="search-exit-btn" @click="exitSearch">✕ 退出搜索</button>
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
              <div class="post-avatar" @click.stop="showProfile(post)">
                <img v-if="post.avatarUrl" :src="post.avatarUrl" class="avatar-img" />
                <span v-else class="avatar-text">{{ post.nickname ? post.nickname.charAt(0) : '?' }}</span>
              </div>
              <div class="post-meta">
                <span class="post-author">{{ post.nickname || '匿名用户' }}</span>
                <span class="post-time">{{ formatTime(post.createdAt) }}</span>
              </div>
            </div>
            <h3 class="post-title">{{ post.title }}</h3>
            <p class="post-excerpt">{{ truncate(post.content, 120) }}</p>
            <img v-if="post.images" :src="post.images" class="post-thumb" />
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
            <div class="post-avatar large" @click="showProfile(currentPost)">
              <img v-if="currentPost.avatarUrl" :src="currentPost.avatarUrl" class="avatar-img" />
              <span v-else class="avatar-text">{{ currentPost.nickname ? currentPost.nickname.charAt(0) : '?' }}</span>
            </div>
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
          <img v-if="currentPost.images" :src="currentPost.images" class="detail-image" @click="openImage(currentPost.images)" />
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
            <button class="favorite-btn" :class="{faved:currentPost.favorited}" @click="handleFavorite(currentPost.id)">{{ currentPost.favorited ? '⭐' : '☆' }} 收藏</button>
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
              <div class="comment-avatar" @click="showProfile(comment)">
                <img v-if="comment.avatarUrl" :src="comment.avatarUrl" class="avatar-img" />
                <span v-else class="avatar-text">{{ comment.nickname ? comment.nickname.charAt(0) : '?' }}</span>
              </div>
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-author">{{ comment.nickname || '匿名用户' }}</span>
                  <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-actions">
                  <button class="reply-toggle-btn" @click="startReply(comment)">💬 回复</button>
                  <button class="reply-toggle-btn" @click="handleCommentLike(comment)">{{ comment.liked ? '❤️' : '🤍' }} {{ comment.like_count||0 }}</button>
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
                <div class="comment-avatar small" @click="showProfile(reply)">
                  <img v-if="reply.avatarUrl" :src="reply.avatarUrl" class="avatar-img" />
                  <span v-else class="avatar-text">{{ reply.nickname ? reply.nickname.charAt(0) : '?' }}</span>
                </div>
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
            <div class="comment-actions-row">
              <span v-if="commentSensitiveHint" class="sensitive-hint">{{ commentSensitiveHint }}</span>
              <button class="send-comment-btn" :disabled="!!commentSensitiveHint" @click="submitComment">发送</button>
            </div>
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
            <label>标签（逗号分隔，如：校园生活,求助）</label>
            <input
              v-model="newPostTags"
              placeholder="校园生活, 学习交流, 求助..."
              class="form-input"
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
          <div class="form-group">
            <label>图片（可选）</label>
            <input type="file" accept="image/*" @change="uploadPostImage" ref="postImageInput" style="display:none" />
            <button type="button" class="btn-upload-img" @click="$refs.postImageInput.click()">📷 选择图片</button>
            <span v-if="uploadingImage" style="font-size:12px;color:#909399">上传中...</span>
            <img v-if="newPostImages" :src="newPostImages" class="preview-img" />
          </div>
          <div class="create-actions">
            <span class="char-count">{{ newPost.content.length }} 字</span>
            <span v-if="sensitiveHint" class="sensitive-hint">{{ sensitiveHint }}</span>
            <button class="publish-btn" :disabled="submitting || !!sensitiveHint" @click="publishPost">
              {{ submitting ? '发布中...' : '✨ 发布' }}
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- V4: 用户主页弹窗 -->
    <div v-if="profileVisible" class="modal-overlay" @click.self="profileVisible=false">
      <div class="profile-modal">
        <button class="profile-close" @click="profileVisible=false">✕</button>
        <div class="profile-avatar-wrap"><img v-if="profileUser.avatarUrl" :src="profileUser.avatarUrl" /><span v-else class="profile-avatar-text">{{ (profileUser.nickname||profileUser.username||'?').charAt(0) }}</span></div>
        <div class="profile-name">{{ profileUser.nickname || '未设置昵称' }}</div>
        <div class="profile-username">@{{ profileUser.username }}</div>
        <div class="profile-motto">「{{ profileUser.motto || '这个人很懒，什么都没写~' }}」</div>
        <div class="profile-posts">
          <div class="profile-posts-title">📝 最近发帖 {{ profileUser.posts ? '('+profileUser.posts.length+')' : '' }}</div>
          <div v-if="profileUser.posts && profileUser.posts.length">
            <div v-for="p in profileUser.posts.slice(0,5)" :key="p.id" class="profile-post-item" @click="profileVisible=false;openPost(p.id)">{{ p.title }}</div>
          </div>
          <div v-else class="profile-no-posts">暂无发帖</div>
        </div>
      </div>
    </div>

    <!-- ===== 右侧热门面板 ===== -->
    <aside class="hot-panel">
      <!-- 用户信息区 -->
      <div class="user-block">
        <div v-if="isLoggedIn" class="user-info-wrap">
          <div class="user-info" @click="$router.push('/user-center')">
            <div class="user-avatar">
              <img v-if="userInfo.avatarUrl" :src="userInfo.avatarUrl" alt="avatar" class="avatar-img" />
              <span v-else class="avatar-initial">{{ avatarInitial }}</span>
            </div>
            <div class="user-meta">
              <span class="user-name">{{ userInfo.nickname || userInfo.username || '用户' }}</span>
              <span class="user-role-tag">{{ isAdmin ? '👑 管理员' : '🎓 学生' }}</span>
            </div>
          </div>
          <button class="logout-btn" type="button" @click="logout">退出登录</button>
        </div>
        <!-- V4: 通知铃铛 -->
        <div v-if="isLoggedIn" class="notif-bell" @click="showNotifs=!showNotifs" title="通知">
          🔔<span v-if="unreadCount>0" class="notif-badge">{{ unreadCount }}</span>
        </div>
        <button v-else class="login-btn" @click="$router.push('/login')">登录</button>

        <!-- V4: 通知下拉 -->
        <div v-if="showNotifs" class="notif-dropdown">
          <div v-if="notifications.length===0" class="notif-empty">暂无通知</div>
          <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{unread:!n.is_read}" @click="readNotif(n)">{{ n.content }}<span class="notif-time">{{ formatNotifTime(n.created_at) }}</span></div>
        </div>
      </div>

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
  deleteForumPost,
  getHotForumPosts,
  getPostsByTag,
  getHotForumTags
} from '@/api/forum'
import request, { clearAuthState } from '@/utils/request'
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

      hotTags: [],            // V4: 从后端动态加载
      activeTag: '',         // V4: 当前筛选的标签
      newPostTags: '',       // V4: 发帖时的标签输入

      // 用户信息
      userInfo: {
        nickname: localStorage.getItem('nickname') || '',
        avatarUrl: localStorage.getItem('avatarUrl') || '',
        username: localStorage.getItem('userId') || localStorage.getItem('username') || '',
        role: localStorage.getItem('role') || ''
      },
      profileVisible: false, // V4: 用户主页弹窗
      profileUser: {},       // V4: 主页用户数据
      searchKeyword: '',     // V4: 搜索关键词
      isSearching: false,    // V4: 是否处于搜索模式
      allPosts: [],          // V4: 全部帖子（搜索前备份）
      sensitiveWords: [],    // V4: 敏感词列表
      newPostImages: '',     // V4: 帖子图片URL
      uploadingImage: false, // V4: 图片上传中
      // V4: 通知
      showNotifs: false,
      notifications: [],
      unreadCount: 0,
    }
  },
  computed: {
    currentUserId() {
      return localStorage.getItem('username') || null
    },
    isLoggedIn() {
      return !!localStorage.getItem('token')
    },
    isAdmin() {
      return String(this.userInfo.role) === '1'
    },
    avatarInitial() {
      const name = this.userInfo.nickname || this.userInfo.username || 'U'
      return name.slice(0, 1).toUpperCase()
    },
    totalCommentCount() {
      let count = 0
      for (const c of this.comments) {
        count++
        if (c.replies) count += c.replies.length
      }
      return count
    },
    // V4: 发帖实时敏感词检测
    sensitiveHint() {
      if (!this.sensitiveWords.length) return ''
      const text = (this.newPost.title + ' ' + this.newPost.content + ' ' + this.newPostTags).toLowerCase()
      const matched = this.sensitiveWords.filter(w => w && text.includes(w.toLowerCase()))
      return matched.length ? '⚠ 包含敏感词：' + matched.join('、') : ''
    },
    // V4: 评论实时敏感词检测
    commentSensitiveHint() {
      if (!this.sensitiveWords.length || !this.commentText.trim()) return ''
      const matched = this.sensitiveWords.filter(w => w && this.commentText.toLowerCase().includes(w.toLowerCase()))
      return matched.length ? '⚠ 包含敏感词：' + matched.join('、') : ''
    }
  },
  mounted() {
    this.loadPosts()
    this.loadHotPosts()
    this.loadHotTagList()
    this.loadSensitiveWords()
    this.loadNotifications()
  },
  methods: {
    searchPosts() {
      const kw = this.searchKeyword.trim().toLowerCase()
      if (!kw) { this.exitSearch(); return }
      this.isSearching = true
      // 在全部帖子中过滤：匹配标题、内容、标签
      this.postList = this.allPosts.filter(p => {
        const title = (p.title || '').toLowerCase()
        const content = (p.content || '').toLowerCase()
        const tags = (p.tags || '').toLowerCase()
        return title.includes(kw) || content.includes(kw) || tags.includes(kw)
      })
    },
    exitSearch() {
      this.searchKeyword = ''
      this.isSearching = false
      this.postList = [...this.allPosts]
    },
    async uploadPostImage(e) {
      const file = e.target.files[0]; if (!file) return
      this.uploadingImage = true
      try {
        const fd = new FormData(); fd.append('file', file)
        const token = localStorage.getItem('token'); const headers = token ? { token } : {}
        const r = await fetch('/api/forum/upload-image', { method: 'POST', headers, body: fd })
        const d = await r.json()
        if (d.code === 200) this.newPostImages = d.data
        else alert(d.message || '上传失败')
      } catch (ex) { alert('上传失败') }
      finally { this.uploadingImage = false; e.target.value = '' }
    },
    async loadPosts() {
      this.loading = true
      this.postError = ''
      try {
        let res
        if (this.activeTag) {
          res = await getPostsByTag(this.activeTag, this.currentPage, this.pageSize)
        } else {
          res = await getForumPosts(this.currentPage, this.pageSize)
        }
        if (res.code === 200) {
          this.allPosts = res.data.list || []
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
          this.allPosts = [...this.allPosts, ...(res.data.list || [])]
          this.postList = [...this.postList, ...(res.data.list || [])]
          this.hasMore = this.currentPage < (res.data.totalPages || 1)
        }
      } catch (e) {
        this.currentPage--
        console.error('加载更多失败:', e)
      }
    },
    async openPost(id) {
      // V4: 保存浏览记录到 localStorage（同时存标题）
      const post = this.postList.find(p => p.id === id)
      this.saveBrowseHistory(id, post ? post.title : '')
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
    saveBrowseHistory(postId, title) {
      const key = 'browseHistory_' + (this.currentUserId || 'guest')
      let list = JSON.parse(localStorage.getItem(key) || '[]')
      // 兼容旧数据（纯 ID 数组）
      if (list.length > 0 && typeof list[0] !== 'object') {
        list = list.map(id => ({ id, title: '' }))
      }
      list = list.filter(item => (item.id || item) !== postId)
      list.unshift({ id: postId, title: title || '', time: new Date().toISOString() })
      if (list.length > 20) list = list.slice(0, 20)
      localStorage.setItem(key, JSON.stringify(list))
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
        } else {
          toastError(res.message || '评论失败')
        }
      } catch (e) {
        console.error('提交评论失败:', e)
        toastError('评论失败')
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
        } else {
          toastError(res.message || '回复失败')
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
          content: this.newPost.content.trim(),
          tags: this.newPostTags.trim(),
          images: this.newPostImages
        })
        if (res.code === 200) {
          toastSuccess('发布成功')
          this.newPost = { title: '', content: '' }
          this.newPostTags = ''; this.newPostImages = ''
          this.showCreatePost = false
          this.currentPage = 1
          await this.loadPosts()
        } else {
          toastError(res.message || '发布失败')
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
        const res = await getHotForumPosts(10)
        if (res.code === 200) {
          this.hotPosts = res.data || []
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
    async loadHotTagList() {
      try {
        const res = await getHotForumTags(10)
        if (res.code === 200) {
          this.hotTags = (res.data || []).map(t => t.name).filter(Boolean)
        }
      } catch (e) { /* 失败就用默认 */ }
    },
    async loadSensitiveWords() {
      try {
        const res = await request.get('/api/forum/sensitive-words')
        if (res.code === 200) { this.sensitiveWords = res.data || [] }
      } catch (e) { }
    },
    async loadNotifications() {
      try { const r = await fetch('/api/notifications', { headers: { token: localStorage.getItem('token') || '' } }); const d = await r.json(); if (d.code === 200) this.notifications = d.data || [] } catch (e) { }
      try { const r = await fetch('/api/notifications/unread-count', { headers: { token: localStorage.getItem('token') || '' } }); const d = await r.json(); if (d.code === 200) this.unreadCount = d.data?.count || 0 } catch (e) { }
    },
    async readNotif(n) { if (!n.is_read) { try { await fetch(`/api/notifications/${n.id}/read`, { method: 'PUT', headers: { token: localStorage.getItem('token') || '' } }); n.is_read = 1; this.unreadCount = Math.max(0, this.unreadCount - 1) } catch (e) { } } },
    formatNotifTime(t) { if (!t) return ''; const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` },
    searchTag(tag) {
      this.activeTag = tag
      this.currentPage = 1
      this.loadPosts()
    },
    clearTagFilter() {
      this.activeTag = ''
      this.currentPage = 1
      this.loadPosts()
    },
    async handleFavorite(postId) {
      try {
        const r = await fetch(`/api/forum/posts/${postId}/favorite`, { method: 'POST', headers: { token: localStorage.getItem('token') || '' } })
        const d = await r.json()
        if (d.code === 200 && this.currentPost) this.currentPost.favorited = d.data.favorited
        if (d.data.favorited) toastSuccess('已收藏'); else toastInfo('已取消收藏')
      } catch (e) { }
    },
    async handleCommentLike(comment) {
      const commentId = comment.id
      const wasLiked = comment.liked
      comment.liked = !wasLiked
      comment.like_count = (comment.like_count||0) + (wasLiked ? -1 : 1)
      try {
        await fetch(`/api/forum/comments/${commentId}/like`, { method: 'POST', headers: { token: localStorage.getItem('token') || '' } })
      } catch (e) { comment.liked = wasLiked; comment.like_count = (comment.like_count||0) + (wasLiked ? 1 : -1) }
    },
    openImage(url) { window.open(url, '_blank') },
    goHome() {
      this.$router.push({ name: 'QaPage' })
    },
    goUserCenter() {
      this.$router.push({ name: 'UserCenter' })
    },
    logout() {
      clearAuthState()
      this.userInfo.nickname = ''
      this.userInfo.avatarUrl = ''
      this.userInfo.username = ''
      this.userInfo.role = ''
      this.$router.replace('/login')
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
    async showProfile(user) {
      if (!user || !user.userId) return
      // 先用本地数据立即显示
      this.profileUser = { nickname: user.nickname || '', username: user.userId, avatarUrl: user.avatarUrl || '', motto: '加载中...', posts: null }
      this.profileVisible = true
      // 异步加载完整数据
      try {
        const r = await fetch(`/api/profile/${user.userId}`)
        const d = await r.json()
        if (d && d.code === 200 && d.data) {
          const dd = d.data
          this.profileUser = {
            nickname: dd.nickname || user.nickname || '',
            username: dd.username || user.userId,
            avatarUrl: dd.avatarUrl || user.avatarUrl || '',
            motto: dd.motto || '',
            posts: dd.posts || []
          }
        }
      } catch (e) { console.error('Profile load failed:', e) }
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
  height: 100vh;
  background: #f5f7fa;
  padding: 0;
  box-sizing: border-box;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
  color: #1c2b35;
  overflow: hidden;
  display: grid;
  grid-template-columns: 200px 1fr 200px;
  gap: 14px;
  background: #f8fbfd;
  padding: 12px;
  align-items: start;
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
  position: sticky;
  top: 12px;
  height: calc(100vh - 24px);
  overflow-y: auto;
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
  object-fit: cover;
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
  height: calc(100vh - 24px);
  overflow-y: auto;
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

/* V4: 标签筛选 */
.tag-filter-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #e8f0f5;
  color: #2E75B6;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}
.tag-clear-btn {
  border: none;
  background: transparent;
  color: #6b8290;
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
  line-height: 1;
}
.tag-clear-btn:hover { color: #e74c3c; }

/* V4: 搜索提示与退出 */
.search-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  margin-bottom: 4px;
  background: #eef4fb;
  border-radius: 10px;
  font-size: 13px;
  color: #3c6476;
}
.search-exit-btn {
  margin-left: auto;
  border: 1px solid #c4d7e6;
  background: #fff;
  color: #5a7a8f;
  border-radius: 8px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}
.search-exit-btn:hover {
  background: #f56c6c;
  color: #fff;
  border-color: #f56c6c;
}

/* V4: 搜索输入框 */
.search-box {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}
.search-box input {
  border: 1px solid #d7e2ea;
  border-radius: 10px;
  padding: 5px 12px;
  font-size: 13px;
  width: 180px;
  outline: none;
}
.search-box input:focus {
  border-color: #3c6476;
}
.search-clear {
  border: none;
  background: transparent;
  color: #909399;
  cursor: pointer;
  font-size: 14px;
  padding: 0 4px;
}
.search-clear:hover { color: #e74c3c; }

.post-list {
  display: grid;
  gap: 12px;
}

.post-card {
  background: #fff;
  border-radius: 16px;
  padding: 18px;
  box-shadow: 0 2px 12px rgba(60,80,90,.05);
  cursor: pointer;
  transition: all .25s cubic-bezier(.4,0,.2,1);
  border: 1px solid #f0f3f5;
}
.post-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(43,85,105,.12);
  border-color: #dce5ea;
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
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.post-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.post-avatar .avatar-text {
  color: #3c6476;
  font-weight: 700;
  font-size: 16px;
}

.post-avatar.large {
  width: 44px;
  height: 44px;
}

.post-avatar.large .avatar-text {
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
.post-thumb { max-width: 100%; max-height: 200px; border-radius: 8px; margin-bottom: 8px; }

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
.detail-image { max-width: 100%; max-height: 400px; border-radius: 12px; cursor: pointer; margin-bottom: 16px; }
.favorite-btn { border: none; background: transparent; font-size: 13px; cursor: pointer; color: #909399; padding: 4px 8px; border-radius: 6px; }
.favorite-btn:hover { background: #fef3e2; color: #e6a23c; }
.favorite-btn.faved { color: #e6a23c; }

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
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.comment-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.comment-avatar .avatar-text {
  color: #3c6476;
  font-weight: 600;
  font-size: 14px;
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

.send-comment-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.comment-actions-row {
  display: flex;
  align-items: center;
  gap: 8px;
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

.sensitive-hint {
  font-size: 12px;
  color: #e74c3c;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 4px 10px;
  flex: 1;
  text-align: center;
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

.btn-upload-img {
  border: 1px solid #d7e2ea;
  background: #fff;
  color: #606266;
  border-radius: 12px;
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
}

.btn-upload-img:hover {
  border-color: #3c6476;
  color: #3c6476;
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
  position: sticky;
  top: 12px;
  height: calc(100vh - 24px);
  overflow-y: auto;
}

/* ===== 用户信息块 ===== */
.user-block {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 10px;
  position: relative;
}
.user-info-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #48b6b5, #5494b9);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-initial {
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.user-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}
.user-role-tag {
  font-size: 11px;
  opacity: 0.75;
}
.logout-btn {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.1);
  color: #e8f5fb;
  font-size: 12px;
  cursor: pointer;
}
.logout-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.login-btn {
  display: block;
  width: 100%;
  border: none;
  border-radius: 8px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.18);
  color: #e8f5fb;
  font-size: 13px;
  cursor: pointer;
  text-align: center;
}
.login-btn:hover {
  background: rgba(255, 255, 255, 0.28);
}

/* V4: 通知 */
.notif-bell { position: relative; cursor: pointer; font-size: 18px; padding: 4px; }
.notif-badge { position: absolute; top: -4px; right: -4px; background: #f56c6c; color: #fff; font-size: 10px; border-radius: 10px; padding: 1px 5px; min-width: 16px; text-align: center; }
.notif-dropdown { position: absolute; top: 100%; right: 0; background: #fff; border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,.15); z-index: 100; width: 280px; max-height: 360px; overflow-y: auto; margin-top: 8px; }
.notif-empty { text-align: center; padding: 20px; color: #C0C4CC; font-size: 13px; }
.notif-item { padding: 10px 14px; border-bottom: 1px solid #f5f7fa; font-size: 12px; color: #606266; cursor: pointer; }
.notif-item.unread { background: #e6f7ff; font-weight: 600; }
.notif-item:hover { background: #f5f7fa; }
.notif-time { display: block; font-size: 10px; color: #C0C4CC; margin-top: 3px; }

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

/* V4: 用户主页弹窗 */
.modal-overlay { position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,.5); display:flex; align-items:center; justify-content:center; z-index:1000; }
.profile-modal { background:#fff; border-radius:16px; padding:28px 20px 20px; text-align:center; position:relative; min-width:280px; max-width:340px; box-shadow:0 12px 40px rgba(0,0,0,.2); max-height:80vh; overflow-y:auto; }
.profile-close { position:absolute; top:8px; right:12px; border:none; background:transparent; font-size:18px; cursor:pointer; color:#909399; }
.profile-avatar-wrap { width:72px; height:72px; border-radius:50%; margin:0 auto 10px; background:#b5d7e6; display:flex; align-items:center; justify-content:center; overflow:hidden; }
.profile-avatar-wrap img { width:100%; height:100%; object-fit:cover; }
.profile-avatar-text { font-size:30px; font-weight:700; color:#3c6476; }
.profile-name { font-size:18px; font-weight:700; color:#303133; margin-bottom:2px; }
.profile-username { font-size:13px; color:#909399; }
.profile-motto { margin-top:8px; font-size:13px; color:#606266; }
.profile-posts { margin-top:16px; text-align:left; border-top:1px solid #f0f2f5; padding-top:12px; }
.profile-posts-title { font-size:13px; font-weight:600; color:#606266; margin-bottom:8px; }
.profile-post-item { font-size:12px; color:#2E75B6; padding:6px 0; cursor:pointer; border-bottom:1px solid #f5f7fa; }
.profile-post-item:hover { color:#1890ff; }
.profile-no-posts { font-size:12px; color:#C0C4CC; text-align:center; padding:8px 0; }

@media (max-width: 1100px) {
  .teatalk-page {
    grid-template-columns: 200px 1fr;
    height: 100vh;
    overflow: hidden;
  }
  .hot-panel { display: none; }
}

@media (max-width: 900px) {
  .teatalk-page {
    grid-template-columns: 1fr;
    height: auto;
    overflow-y: auto;
    padding: 10px;
  }
  .side-panel { height: auto; position: static; flex-direction: row; flex-wrap: wrap; justify-content: space-between; }
  .side-group { flex: 1 1 100%; }
  .content-panel { height: auto; overflow-y: visible; }
  .comment-composer { flex-direction: column; align-items: stretch; }
  .send-comment-btn { width: 100%; }
}
</style>
