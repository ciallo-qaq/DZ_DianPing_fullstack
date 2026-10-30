<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  followUser,
  getCommonFollows,
  getCurrentUser,
  getUser,
  getUserBlogs,
  getUserInfo,
  isFollowing
} from "../api";
import {
  formatDate,
  normalizeBlog,
  resolveImageUrl
} from "../utils/utils";

const route = useRoute();
const router = useRouter();
const user = ref(null);
const info = ref({});
const blogs = ref([]);
const current = ref(1);
const loading = ref(true);
const loadingBlogs = ref(false);
const finished = ref(false);
const followed = ref(false);
const following = ref(false);
const currentUser = ref(null);
const commonFollows = ref([]);
const activeTab = ref("blogs");

const isSelf = computed(
  () => currentUser.value && String(currentUser.value.id) === String(route.params.id)
);

async function loadPage() {
  loading.value = true;
  blogs.value = [];
  info.value = {};
  commonFollows.value = [];
  followed.value = false;
  try {
    const [userResult, currentResult] = await Promise.all([
      getUser(route.params.id),
      getCurrentUser().catch(() => ({ data: null }))
    ]);
    user.value = userResult.data || null;
    currentUser.value = currentResult.data || null;
    if (!user.value) {
      ElMessage.warning("用户不存在");
      return;
    }
    await Promise.all([
      loadInfo(),
      loadBlogs(true),
      loadRelation(),
      loadCommonFollows()
    ]);
  } catch (error) {
    ElMessage.error(error.message || "获取用户资料失败");
  } finally {
    loading.value = false;
  }
}

async function loadInfo() {
  try {
    const { data } = await getUserInfo(route.params.id);
    info.value = data || {};
  } catch {
    info.value = {};
  }
}

async function loadBlogs(reset = false) {
  if (loadingBlogs.value || (!reset && finished.value)) {
    return;
  }
  if (reset) {
    current.value = 1;
    blogs.value = [];
    finished.value = false;
  }
  loadingBlogs.value = true;
  try {
    const { data } = await getUserBlogs(route.params.id, current.value);
    const page = (data || []).map(normalizeBlog);
    blogs.value.push(...page);
    finished.value = page.length < 10;
    current.value += 1;
  } catch (error) {
    finished.value = true;
    ElMessage.error(error.message || "获取用户笔记失败");
  } finally {
    loadingBlogs.value = false;
  }
}

async function loadRelation() {
  if (isSelf.value) {
    return;
  }
  try {
    const { data } = await isFollowing(route.params.id);
    followed.value = Boolean(data);
  } catch {
    followed.value = false;
  }
}

async function loadCommonFollows() {
  if (isSelf.value) {
    return;
  }
  try {
    const { data } = await getCommonFollows(route.params.id);
    commonFollows.value = data || [];
  } catch {
    commonFollows.value = [];
  }
}

async function toggleFollow() {
  following.value = true;
  try {
    await followUser(route.params.id, !followed.value);
    followed.value = !followed.value;
    if (info.value.fans !== undefined) {
      info.value.fans = Math.max(0, Number(info.value.fans) + (followed.value ? 1 : -1));
    }
    ElMessage.success(followed.value ? "已关注" : "已取消关注");
  } catch (error) {
    ElMessage.error(error.message || "关注操作失败");
  } finally {
    following.value = false;
  }
}

watch(
  () => route.params.id,
  () => loadPage(),
  { immediate: true }
);
</script>

<template>
  <main class="page user-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>用户主页</h1>
      <button class="back-button share-button" @click="router.push('/home')">
        <el-icon><House /></el-icon>
      </button>
    </header>

    <div v-if="loading" class="loading-state">正在加载用户资料...</div>
    <div v-else-if="!user" class="empty-state">用户不存在</div>
    <template v-else>
      <section class="user-hero">
        <img :src="resolveImageUrl(user.icon)" :alt="user.nickName" />
        <div class="user-main">
          <h1>{{ user.nickName || "DZ_DianPing_Front 用户" }}</h1>
          <p>{{ info.city || "未知城市" }} · {{ info.introduce || "这个人很神秘，什么都没有留下" }}</p>
          <div class="counts">
            <span><strong>{{ info.fans || 0 }}</strong> 粉丝</span>
            <span><strong>{{ info.followee || 0 }}</strong> 关注</span>
            <span><strong>{{ info.credits || 0 }}</strong> 积分</span>
          </div>
        </div>
        <button
          v-if="!isSelf"
          class="follow-button"
          :disabled="following"
          @click="toggleFollow"
        >
          {{ followed ? "已关注" : "+ 关注" }}
        </button>
      </section>

      <section class="user-content">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="TA 的笔记" name="blogs">
            <div class="user-blogs">
              <article
                v-for="blog in blogs"
                :key="blog.id"
                @click="router.push(`/blog/${blog.id}`)"
              >
                <img :src="blog.img" :alt="blog.title" loading="lazy" />
                <div>
                  <h2>{{ blog.title || "无标题笔记" }}</h2>
                  <p>{{ formatDate(blog.createTime) }}</p>
                  <span>
                    <el-icon><Pointer /></el-icon>{{ blog.liked || 0 }}
                    <el-icon><ChatDotSquare /></el-icon>{{ blog.comments || 0 }}
                  </span>
                </div>
              </article>
              <div v-if="loadingBlogs" class="loading-state">正在加载...</div>
              <div v-else-if="!blogs.length" class="empty-state">TA 还没有发布笔记</div>
              <button v-else-if="!finished" class="load-button" @click="loadBlogs(false)">
                加载更多
              </button>
              <div v-else class="load-more">已经到底啦</div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="共同关注" name="common">
            <div v-if="commonFollows.length" class="common-list">
              <button
                v-for="item in commonFollows"
                :key="item.id"
                @click="router.push(`/users/${item.id}`)"
              >
                <img :src="resolveImageUrl(item.icon)" alt="" />
                <span>{{ item.nickName }}</span>
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <div v-else class="empty-state">暂时没有共同关注</div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </template>
  </main>
</template>

<style scoped>
.user-page {
  background: #f5f5f5;
}

.share-button {
  justify-content: flex-end;
  font-size: 20px;
}

.user-hero {
  display: flex;
  gap: 13px;
  align-items: center;
  padding: 22px 18px;
  background: linear-gradient(140deg, #fff, #fff0e9);
}

.user-hero > img {
  flex: 0 0 72px;
  width: 72px;
  height: 72px;
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 3px 12px rgb(0 0 0 / 10%);
  object-fit: cover;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.user-main h1 {
  margin: 0 0 5px;
  overflow: hidden;
  font-size: 21px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-main > p {
  display: -webkit-box;
  margin: 0 0 9px;
  overflow: hidden;
  color: #888;
  font-size: 12px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.counts {
  display: flex;
  gap: 16px;
  color: #888;
  font-size: 11px;
}

.counts strong {
  color: #333;
}

.follow-button {
  flex: 0 0 auto;
  padding: 7px 13px;
  border: 0;
  border-radius: 16px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
}

.user-content {
  margin: 12px;
  padding: 0 13px 12px;
  border-radius: 10px;
  background: #fff;
}

.user-blogs {
  display: grid;
  gap: 10px;
}

.user-blogs article {
  display: flex;
  gap: 11px;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}

.user-blogs article img {
  flex: 0 0 94px;
  width: 94px;
  height: 94px;
  border-radius: 8px;
  object-fit: cover;
}

.user-blogs article > div {
  flex: 1;
  min-width: 0;
}

.user-blogs h2 {
  margin: 2px 0 10px;
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-blogs p {
  margin: 0 0 10px;
  color: #aaa;
  font-size: 11px;
}

.user-blogs span {
  display: flex;
  gap: 4px;
  align-items: center;
  color: #888;
  font-size: 11px;
}

.user-blogs .el-icon:nth-of-type(2) {
  margin-left: 10px;
}

.load-button {
  justify-self: center;
  margin-top: 8px;
  padding: 7px 22px;
  border: 1px solid var(--brand);
  border-radius: 16px;
  background: #fff;
  color: var(--brand);
}

.common-list {
  display: grid;
  gap: 4px;
  padding-top: 8px;
}

.common-list button {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid #eee;
  background: transparent;
  text-align: left;
}

.common-list img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}

.common-list span {
  flex: 1;
}
</style>
