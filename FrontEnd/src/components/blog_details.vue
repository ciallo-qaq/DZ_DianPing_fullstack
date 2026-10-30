<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  followUser,
  getBlog,
  getBlogLikes,
  getCurrentUser,
  getShop,
  isFollowing,
  toggleBlogLike
} from "../api";
import {
  formatDate,
  normalizeBlog,
  normalizeShop,
  resolveImageUrl
} from "../utils/utils";

const route = useRoute();
const router = useRouter();
const blog = ref({ images: [] });
const shop = ref({ images: [] });
const currentUser = ref(null);
const likedUsers = ref([]);
const followed = ref(false);
const loading = ref(true);
const liking = ref(false);
const following = ref(false);

async function loadBlog() {
  try {
    const { data } = await getBlog(route.params.id);
    if (!data) {
      throw new Error("博客不存在");
    }
    blog.value = normalizeBlog(data);
    loadShop();
    loadLikes();
    loadCurrentUser();
  } catch (error) {
    ElMessage.error(error.message || "获取笔记详情失败");
  }
}

async function loadShop() {
  if (!blog.value.shopId) {
    return;
  }
  try {
    const { data } = await getShop(blog.value.shopId);
    shop.value = data ? normalizeShop(data) : { images: [] };
  } catch {
    shop.value = { images: [] };
  }
}

async function loadLikes() {
  try {
    const { data } = await getBlogLikes(route.params.id);
    likedUsers.value = data || [];
  } catch {
    likedUsers.value = [];
  }
}

async function loadCurrentUser() {
  try {
    const { data } = await getCurrentUser();
    currentUser.value = data || null;
    if (currentUser.value && String(currentUser.value.id) !== String(blog.value.userId)) {
      const result = await isFollowing(blog.value.userId);
      followed.value = Boolean(result.data);
    }
  } catch {
    currentUser.value = null;
  }
}

async function toggleFollow() {
  following.value = true;
  try {
    await followUser(blog.value.userId, !followed.value);
    followed.value = !followed.value;
    ElMessage.success(followed.value ? "已关注" : "已取消关注");
  } catch (error) {
    ElMessage.error(error.message || "关注操作失败");
  } finally {
    following.value = false;
  }
}

async function toggleLike() {
  liking.value = true;
  try {
    await toggleBlogLike(blog.value.id);
    const { data } = await getBlog(blog.value.id);
    blog.value = normalizeBlog(data);
    await loadLikes();
  } catch (error) {
    ElMessage.error(error.message || "点赞失败");
  } finally {
    liking.value = false;
  }
}

onMounted(async () => {
  await loadBlog();
  loading.value = false;
});
</script>

<template>
  <main class="page blog-detail-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>探店笔记</h1>
      <button class="back-button share-button" @click="router.push('/home')">
        <el-icon><HomeFilled /></el-icon>
      </button>
    </header>

    <div v-if="loading" class="loading-state">正在加载笔记...</div>
    <template v-else>
      <section v-if="blog.images.length" class="image-panel">
        <el-carousel
          height="390px"
          :loop="false"
          indicator-position="outside"
          arrow="always"
        >
          <el-carousel-item v-for="image in blog.images" :key="image">
            <img :src="image" :alt="blog.title" />
          </el-carousel-item>
        </el-carousel>
      </section>

      <section class="blog-content">
        <h1>{{ blog.title || "无标题笔记" }}</h1>
        <div class="author-row">
          <button
            class="author"
            @click="router.push(`/users/${blog.userId}`)"
          >
            <img :src="resolveImageUrl(blog.icon)" alt="" />
            <span>
              <strong>{{ blog.name || "匿名用户" }}</strong>
              <small>{{ formatDate(blog.createTime, true) }}</small>
            </span>
          </button>
          <button
            v-if="!currentUser || String(currentUser.id) !== String(blog.userId)"
            class="follow-button"
            :disabled="following"
            @click="toggleFollow"
          >
            {{ followed ? "取消关注" : "+ 关注" }}
          </button>
        </div>
        <p class="text-content">{{ blog.content || "作者没有填写正文。" }}</p>
      </section>

      <button
        v-if="shop.name"
        class="shop-link"
        @click="router.push(`/shop/${shop.id}`)"
      >
        <img :src="shop.img" :alt="shop.name" />
        <span>
          <strong>{{ shop.name }}</strong>
          <small>{{ shop.area || shop.address || "查看店铺详情" }}</small>
        </span>
        <el-rate :model-value="shop.scoreValue" disabled />
        <el-icon><ArrowRight /></el-icon>
      </button>

      <section class="like-panel">
        <button
          class="like-button"
          :class="{ liked: blog.isLike }"
          :disabled="liking"
          @click="toggleLike"
        >
          <el-icon><Pointer /></el-icon>
          <span>{{ blog.isLike ? "已赞" : "点赞" }}</span>
          <strong>{{ blog.liked || 0 }}</strong>
        </button>
        <div class="liked-users">
          <img
            v-for="user in likedUsers"
            :key="user.id"
            :src="resolveImageUrl(user.icon)"
            :alt="user.nickName"
          />
          <span v-if="likedUsers.length">等用户赞过</span>
          <span v-else>还没有人点赞</span>
        </div>
        <div class="comment-count">
          <el-icon><ChatDotSquare /></el-icon>
          {{ blog.comments || 0 }} 评论
        </div>
      </section>

      <section class="comments-placeholder">
        <h2>评论</h2>
        <p>
          接口文档中 `/blog-comments` 当前没有可用映射，因此评论内容无法从后端读取或提交。
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.blog-detail-page {
  background: #fff;
}

.share-button {
  justify-content: flex-end;
  font-size: 21px;
}

.image-panel {
  background: #222;
}

.image-panel :deep(.el-carousel__item img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-panel :deep(.el-carousel__indicators) {
  bottom: -4px;
}

.blog-content {
  padding: 22px 20px 14px;
}

.blog-content > h1 {
  margin: 0 0 18px;
  font-size: 24px;
  line-height: 1.35;
}

.author-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.author {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.author img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}

.author span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.author strong {
  color: #333;
  font-size: 14px;
}

.author small {
  color: #aaa;
}

.follow-button {
  padding: 6px 16px;
  border: 1px solid var(--brand);
  border-radius: 16px;
  background: #fff;
  color: var(--brand);
  font-size: 12px;
}

.text-content {
  margin: 0;
  color: #3f3f3f;
  font-size: 15px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.shop-link {
  display: flex;
  gap: 11px;
  align-items: center;
  width: calc(100% - 40px);
  margin: 10px 20px 22px;
  padding: 11px;
  border: 1px solid #eee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgb(0 0 0 / 7%);
  text-align: left;
}

.shop-link img {
  width: 58px;
  height: 58px;
  border-radius: 7px;
  object-fit: cover;
}

.shop-link > span {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.shop-link strong,
.shop-link small {
  overflow: hidden;
  color: #333;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-link small {
  color: #999;
  font-size: 11px;
}

.shop-link :deep(.el-rate__icon) {
  font-size: 14px;
}

.like-panel {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 15px 20px;
  background: #fafafa;
  border-block: 1px solid #eee;
}

.like-button {
  display: flex;
  gap: 5px;
  align-items: center;
  border: 0;
  background: transparent;
  color: #777;
}

.like-button.liked {
  color: var(--brand);
}

.liked-users {
  display: flex;
  flex: 1;
  align-items: center;
  color: #999;
  font-size: 11px;
}

.liked-users img {
  width: 23px;
  height: 23px;
  margin-left: -5px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
}

.liked-users span {
  margin-left: 8px;
}

.comment-count {
  display: flex;
  gap: 4px;
  align-items: center;
  color: #777;
  font-size: 12px;
}

.comments-placeholder {
  padding: 20px;
}

.comments-placeholder h2 {
  margin: 0 0 8px;
  font-size: 17px;
}

.comments-placeholder p {
  margin: 0;
  color: #999;
  font-size: 12px;
  line-height: 1.7;
}

@media (min-width: 720px) {
  .image-panel :deep(.el-carousel) {
    max-width: 760px;
    margin: 0 auto;
  }
}
</style>
