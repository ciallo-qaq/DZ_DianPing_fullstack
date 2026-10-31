<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  getHotBlogs,
  getSearchSuggestions,
  getShopTypes,
  toggleBlogLike
} from "../api";
import { getToken } from "../utils/auth";
import { normalizeBlog, resolveImageUrl } from "../utils/utils";

const SEARCH_DEBOUNCE_MS = 300;
const MAX_SEARCH_LENGTH = 10;
const TYPE_LABELS = {
  SHOP: "店铺",
  VOUCHER: "优惠",
  BLOG: "笔记"
};

const router = useRouter();
const types = ref([]);
const blogs = ref([]);
const current = ref(1);
const loading = ref(false);
const finished = ref(false);
const keyword = ref("");
const suggestions = ref([]);
const suggestionsLoading = ref(false);
const suggestionsVisible = ref(false);
const searchFocused = ref(false);
const highlightedIndex = ref(-1);
const keywordTooLong = computed(
  () => Array.from(keyword.value.trim()).length > MAX_SEARCH_LENGTH
);

let debounceTimer = null;
let requestVersion = 0;
let suggestionController = null;

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
  const searchKeyword = keyword.value.trim();
  if (!searchKeyword) {
    ElMessage.info("请输入搜索关键词");
    return;
  }
  if (Array.from(searchKeyword).length > MAX_SEARCH_LENGTH) {
    return;
  }
  closeSuggestions();
  router.push({
    path: "/search-results",
    query: { keyword: searchKeyword, type: "ALL" }
  });
}

function closeSuggestions() {
  suggestionsVisible.value = false;
  highlightedIndex.value = -1;
}

function clearPendingSuggestionRequest() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  if (suggestionController) {
    suggestionController.abort();
    suggestionController = null;
  }
}

async function loadSuggestions(searchKeyword, version) {
  suggestionController = new AbortController();
  suggestionsLoading.value = true;
  suggestionsVisible.value = searchFocused.value;
  try {
    const { data } = await getSearchSuggestions(
      searchKeyword,
      version,
      suggestionController.signal
    );
    if (
      version !== requestVersion ||
      keyword.value.trim() !== searchKeyword ||
      data?.keyword !== searchKeyword
    ) {
      return;
    }
    suggestions.value = data?.suggestions || [];
    highlightedIndex.value = -1;
    suggestionsVisible.value = searchFocused.value;
  } catch (error) {
    if (version !== requestVersion || error?.code === "ERR_CANCELED") {
      return;
    }
    suggestions.value = [];
    suggestionsVisible.value = searchFocused.value;
  } finally {
    if (version === requestVersion) {
      suggestionsLoading.value = false;
    }
  }
}

function handleSuggestionFocus() {
  searchFocused.value = true;
  if (suggestions.value.length) {
    suggestionsVisible.value = true;
  }
}

function handleSuggestionBlur() {
  window.setTimeout(() => {
    searchFocused.value = false;
    suggestionsVisible.value = false;
  }, 100);
}

function moveSuggestion(step) {
  if (!suggestionsVisible.value || !suggestions.value.length) {
    return;
  }
  const length = suggestions.value.length;
  highlightedIndex.value =
    (highlightedIndex.value + step + length) % length;
}

function handleSearchEnter() {
  if (
    suggestionsVisible.value &&
    highlightedIndex.value >= 0 &&
    suggestions.value[highlightedIndex.value]
  ) {
    selectSuggestion(suggestions.value[highlightedIndex.value]);
    return;
  }
  search();
}

function selectSuggestion(suggestion) {
  const searchKeyword = suggestion.suggestionText || suggestion.title;
  if (!searchKeyword) {
    return;
  }
  requestVersion += 1;
  clearPendingSuggestionRequest();
  keyword.value = searchKeyword;
  suggestions.value = [];
  closeSuggestions();
  router.push({
    path: "/search-results",
    query: {
      keyword: searchKeyword,
      type: suggestion.type || "ALL"
    }
  });
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

watch(keyword, (value) => {
  clearPendingSuggestionRequest();
  requestVersion += 1;
  suggestions.value = [];
  suggestionsLoading.value = false;
  closeSuggestions();

  const searchKeyword = value.trim();
  if (
    !searchKeyword ||
    Array.from(searchKeyword).length > MAX_SEARCH_LENGTH
  ) {
    return;
  }

  const version = requestVersion;
  debounceTimer = window.setTimeout(() => {
    debounceTimer = null;
    loadSuggestions(searchKeyword, version);
  }, SEARCH_DEBOUNCE_MS);
});

onMounted(() => {
  loadTypes();
  loadBlogs();
});

onBeforeUnmount(() => {
  clearPendingSuggestionRequest();
  requestVersion += 1;
});
</script>

<template>
  <main class="page home-page">
    <header class="hero">
      <div class="location"><el-icon><Location /></el-icon> 杭州</div>
      <div class="search-shell">
        <div class="search-box">
          <el-icon><Search /></el-icon>
          <input
            v-model="keyword"
            placeholder="搜索店铺、笔记和优惠"
            @focus="handleSuggestionFocus"
            @blur="handleSuggestionBlur"
            @keydown.down.prevent="moveSuggestion(1)"
            @keydown.up.prevent="moveSuggestion(-1)"
            @keydown.enter.prevent="handleSearchEnter"
            @keydown.esc.prevent="closeSuggestions"
          />
          <button @click="search">搜索</button>
        </div>
        <div
          v-if="searchFocused && suggestionsVisible && !keywordTooLong"
          class="suggestion-panel"
        >
          <button
            v-for="(suggestion, index) in suggestions"
            :key="`${suggestion.type}-${suggestion.sourceId}`"
            class="suggestion-item"
            :class="{ active: highlightedIndex === index }"
            @mousedown.prevent="selectSuggestion(suggestion)"
            @mouseenter="highlightedIndex = index"
          >
            <span class="suggestion-copy">
              <strong>{{ suggestion.suggestionText }}</strong>
              <span v-if="suggestion.title !== suggestion.suggestionText">
                {{ suggestion.title }}
              </span>
              <small>{{ suggestion.subtitle }}</small>
            </span>
            <em>{{ TYPE_LABELS[suggestion.type] || suggestion.type }}</em>
          </button>
          <div v-if="suggestionsLoading" class="suggestion-tip">
            正在搜索...
          </div>
          <div
            v-else-if="!suggestions.length"
            class="suggestion-tip"
          >
            暂无匹配建议
          </div>
        </div>
        <div v-if="keywordTooLong" class="search-length-tip">
          关键词最多 10 个字
        </div>
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

.search-shell {
  position: relative;
  flex: 1;
  min-width: 0;
}

.search-box {
  display: flex;
  align-items: center;
  width: 100%;
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

.suggestion-panel {
  position: absolute;
  top: calc(100% + 7px);
  right: 0;
  left: 0;
  z-index: 30;
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 28px rgb(0 0 0 / 16%);
}

.suggestion-item {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-bottom: 1px solid #f1f1f1;
  background: #fff;
  color: #333;
  text-align: left;
}

.suggestion-item:last-of-type {
  border-bottom: 0;
}

.suggestion-item.active {
  background: #fff4ef;
}

.suggestion-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.suggestion-copy strong,
.suggestion-copy span,
.suggestion-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggestion-copy strong {
  color: #222;
  font-size: 14px;
}

.suggestion-copy span,
.suggestion-copy small {
  color: #888;
  font-size: 11px;
}

.suggestion-item em {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 10px;
  background: #fff0ea;
  color: var(--brand);
  font-size: 10px;
  font-style: normal;
}

.suggestion-tip {
  padding: 12px;
  color: #999;
  font-size: 12px;
  text-align: center;
}

.search-length-tip {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgb(0 0 0 / 75%);
  color: #fff;
  font-size: 11px;
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
