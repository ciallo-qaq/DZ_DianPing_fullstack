<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getFollowBlogs, toggleBlogLike } from "../api";
import { formatDate, normalizeBlog } from "../utils/utils";

const router = useRouter();
const blogs = ref([]);
const loading = ref(false);
const finished = ref(false);
const offset = ref(0);
const lastId = ref(Date.now());

async function loadFeed() {
  if (loading.value || finished.value) {
    return;
  }
  loading.value = true;
  try {
    const result = await getFollowBlogs(lastId.value, offset.value);
    const page = result.data;
    const list = (page?.list || []).map(normalizeBlog);
    const existingIds = new Set(blogs.value.map((item) => item.id));
    blogs.value.push(...list.filter((item) => !existingIds.has(item.id)));

    if (!page || !list.length) {
      finished.value = true;
    } else {
      lastId.value = page.minTime ?? lastId.value;
      offset.value = page.offset ?? 0;
      // 返回数量较少通常表示已经读到末尾；仍保留再次点击加载的能力。
      finished.value = false;
    }
  } catch (error) {
    finished.value = true;
    ElMessage.error(error.message || "获取关注流失败");
  } finally {
    loading.value = false;
  }
}

async function like(blog) {
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
  if (element.scrollHeight - element.scrollTop - element.clientHeight < 180) {
    loadFeed();
  }
}

onMounted(loadFeed);
</script>

<template>
  <main class="page follow-page">
    <header class="feed-header">
      <div>
        <h1>关注动态</h1>
        <p>看看你关注的人最近发现了什么</p>
      </div>
      <button @click="router.push('/home')">
        <el-icon><House /></el-icon>
      </button>
    </header>

    <section class="feed-list" @scroll="handleScroll">
      <article v-for="blog in blogs" :key="blog.id" class="feed-card">
        <button class="author" @click="router.push(`/users/${blog.userId}`)">
          <img :src="blog.iconUrl" alt="" />
          <span>
            <strong>{{ blog.name || "匿名用户" }}</strong>
            <small>{{ formatDate(blog.createTime, true) }}</small>
          </span>
        </button>
        <button class="post" @click="router.push(`/blog/${blog.id}`)">
          <img :src="blog.img" :alt="blog.title" loading="lazy" />
          <h2>{{ blog.title || "无标题笔记" }}</h2>
          <p>{{ blog.content || "查看笔记详情" }}</p>
        </button>
        <footer>
          <button :class="{ liked: blog.isLike }" @click="like(blog)">
            <el-icon><Pointer /></el-icon>
            {{ blog.liked || 0 }}
          </button>
          <span><el-icon><ChatDotSquare /></el-icon>{{ blog.comments || 0 }}</span>
        </footer>
      </article>

      <div v-if="loading" class="loading-state">正在加载关注动态...</div>
      <div v-else-if="!blogs.length" class="empty-state">
        暂无关注动态，先去看看其他用户的笔记吧
      </div>
      <button v-else-if="!finished" class="load-button" @click="loadFeed">加载更多</button>
      <div v-else class="load-more">没有更多动态了</div>
    </section>
  </main>
</template>

<style scoped>
.follow-page {
  padding-bottom: 14px;
}

.feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 18px;
  background: linear-gradient(135deg, #fff, #fff0e9);
  border-bottom: 1px solid #ffe2d5;
}

.feed-header h1 {
  margin: 0;
  font-size: 23px;
}

.feed-header p {
  margin: 4px 0 0;
  color: #999;
  font-size: 12px;
}

.feed-header button {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #fff;
  color: var(--brand);
  font-size: 20px;
  box-shadow: 0 3px 9px rgb(0 0 0 / 8%);
}

.feed-list {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.feed-card {
  padding: 14px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 10px rgb(0 0 0 / 6%);
}

.author {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.author img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
}

.author span {
  display: flex;
  flex-direction: column;
}

.author small {
  color: #aaa;
}

.post {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.post img {
  width: 100%;
  max-height: 390px;
  margin-top: 10px;
  border-radius: 9px;
  object-fit: cover;
}

.post h2 {
  margin: 10px 0 5px;
  font-size: 17px;
}

.post p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #777;
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.feed-card footer {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 12px;
  color: #888;
}

.feed-card footer button,
.feed-card footer span {
  display: flex;
  gap: 4px;
  align-items: center;
  border: 0;
  background: transparent;
  color: inherit;
}

.feed-card footer .liked {
  color: var(--brand);
}

.load-button {
  justify-self: center;
  padding: 8px 26px;
  border: 1px solid var(--brand);
  border-radius: 18px;
  background: #fff;
  color: var(--brand);
}
</style>
