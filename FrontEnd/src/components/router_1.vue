<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getHotBlogs, getShopTypes, toggleBlogLike } from "../api";
import { getToken } from "../utils/auth";
import { normalizeBlog, resolveImageUrl } from "../utils/utils";

const router = useRouter();
const types = ref([]);
const blogs = ref([]);
const current = ref(1);
const loading = ref(false);
const finished = ref(false);
const keyword = ref("");

async function loadTypes() {
  try {
    const { data } = await getShopTypes();
    types.value = data || [];
  } catch (error) {
    ElMessage.error(error.message || "获取店铺分类失败");
  }
}

async function loadBlogs() {
  if (loading.value || finished.value) {
    return;
  }
  loading.value = true;
  try {
    const { data } = await getHotBlogs(current.value);
    const page = (data || []).map(normalizeBlog);
    blogs.value.push(...page);
    finished.value = page.length < 10;
    current.value += 1;
  } catch (error) {
    finished.value = true;
    ElMessage.error(error.message || "获取热门笔记失败");
  } finally {
    loading.value = false;
  }
}

function openShopList(type) {
  router.push({
    path: "/shop-list",
    query: { typeId: type.id, name: type.name }
  });
}

function search() {
  const name = keyword.value.trim();
  if (!name) {
    ElMessage.info("请输入店铺名称");
    return;
  }
  router.push({ path: "/shop-list", query: { name } });
}

function openBlog(blog) {
  if (!getToken()) {
    router.push({ path: "/login", query: { redirect: `/blog/${blog.id}` } });
    return;
  }
  router.push(`/blog/${blog.id}`);
}

async function like(blog) {
  if (!getToken()) {
    router.push({ path: "/login", query: { redirect: "/home" } });
    return;
  }
  try {
    await toggleBlogLike(blog.id);
    blog.isLike = !blog.isLike;
    blog.liked = Math.max(0, Number(blog.liked || 0) + (blog.isLike ? 1 : -1));
  } catch (error) {
    ElMessage.error(error.message || "点赞失败");
  }
}

function handleScroll(event) {
  const element = event.currentTarget;
  if (element.scrollHeight - element.scrollTop - element.clientHeight < 160) {
    loadBlogs();
  }
}

onMounted(() => {
  loadTypes();
  loadBlogs();
});
</script>

<template>
  <main class="page home-page">
    <header class="hero">
      <div class="location"><el-icon><Location /></el-icon> 杭州</div>
      <div class="search-box">
        <el-icon><Search /></el-icon>
        <input
          v-model="keyword"
          placeholder="搜索店铺名称"
          @keyup.enter="search"
        />
        <button @click="search">搜索</button>
      </div>
      <button class="profile-entry" @click="router.push('/profile')">
        <el-icon><User /></el-icon>
      </button>
    </header>

    <section class="type-panel">
      <button
        v-for="type in types"
        :key="type.id"
        class="type-item"
        @click="openShopList(type)"
      >
        <span class="type-icon">
          <img :src="resolveImageUrl(type.icon)" :alt="type.name" />
        </span>
        <span>{{ type.name }}</span>
      </button>
    </section>

    <section class="feed-section">
      <div class="section-title">
        <div>
          <span>热门探店</span>
          <small>发现城市里的好店</small>
        </div>
        <el-icon><TrendCharts /></el-icon>
      </div>

      <div class="blog-grid" @scroll="handleScroll">
        <article
          v-for="blog in blogs"
          :key="blog.id"
          class="blog-card"
          @click="openBlog(blog)"
        >
          <div class="cover">
            <img :src="blog.img" :alt="blog.title" loading="lazy" />
          </div>
          <h2>{{ blog.title || "无标题笔记" }}</h2>
          <div class="blog-meta">
            <div class="author">
              <img :src="blog.iconUrl" alt="" />
              <span>{{ blog.name || "匿名用户" }}</span>
            </div>
            <button
              class="like-button"
              :class="{ liked: blog.isLike }"
              @click.stop="like(blog)"
            >
              <el-icon><Pointer /></el-icon>
              {{ blog.liked || 0 }}
            </button>
          </div>
        </article>

        <div v-if="loading" class="load-more">正在加载...</div>
        <button
          v-else-if="!finished"
          class="load-button"
          @click="loadBlogs"
        >
          加载更多
        </button>
        <div v-else-if="finished && !blogs.length" class="empty-state">
          暂无热门笔记
        </div>
        <div v-else-if="finished" class="load-more">已经到底啦</div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  padding-bottom: 12px;
}

.hero {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  background: linear-gradient(135deg, #ff6633, #ff9b55);
  color: #fff;
}

.location {
  display: flex;
  gap: 2px;
  align-items: center;
  font-size: 13px;
  white-space: nowrap;
}

.search-box {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  height: 38px;
  padding-left: 12px;
  overflow: hidden;
  border-radius: 20px;
  background: #fff;
  color: #999;
}

.search-box input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 8px;
  border: 0;
  outline: 0;
}

.search-box button {
  height: 100%;
  padding: 0 14px;
  border: 0;
  background: #ffe7dc;
  color: var(--brand);
  font-size: 13px;
}

.profile-entry {
  display: grid;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 60%);
  border-radius: 50%;
  background: rgb(255 255 255 / 15%);
  color: #fff;
  font-size: 19px;
}

.type-panel {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 15px 8px;
  padding: 18px 12px;
  background: #fff;
  box-shadow: 0 2px 10px rgb(0 0 0 / 4%);
}

.type-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  border: 0;
  background: transparent;
  color: #333;
  font-size: 12px;
}

.type-icon {
  display: grid;
  width: 45px;
  height: 45px;
  overflow: hidden;
  place-items: center;
  border-radius: 14px;
  background: #fff4ef;
}

.type-icon img {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.feed-section {
  padding: 18px 14px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title span {
  display: block;
  font-size: 19px;
  font-weight: 800;
}

.section-title small {
  color: #999;
}

.section-title .el-icon {
  color: var(--brand);
  font-size: 25px;
}

.blog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.blog-card {
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 3px 10px rgb(0 0 0 / 7%);
  cursor: pointer;
}

.cover {
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #eee;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.blog-card:hover .cover img {
  transform: scale(1.03);
}

.blog-card h2 {
  display: -webkit-box;
  min-height: 42px;
  margin: 0;
  padding: 10px 10px 4px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.blog-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px 10px;
}

.author {
  display: flex;
  gap: 5px;
  align-items: center;
  min-width: 0;
  color: #777;
  font-size: 11px;
}

.author img {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}

.author span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.like-button {
  display: flex;
  gap: 3px;
  align-items: center;
  border: 0;
  background: transparent;
  color: #999;
}

.like-button.liked {
  color: var(--brand);
}

.empty-state,
.load-more,
.load-button {
  grid-column: 1 / -1;
}

.load-button {
  justify-self: center;
  padding: 8px 28px;
  border: 1px solid var(--brand);
  border-radius: 20px;
  background: #fff;
  color: var(--brand);
}

@media (min-width: 760px) {
  .type-panel {
    grid-template-columns: repeat(10, 1fr);
  }

  .blog-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
