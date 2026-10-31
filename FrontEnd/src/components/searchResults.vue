<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getSearchResults } from "../api";
import { resolveImageUrl } from "../utils/utils";

const TYPE_LABELS = {
  ALL: "全部",
  SHOP: "店铺",
  VOUCHER: "优惠",
  BLOG: "笔记"
};
const TABS = ["ALL", "SHOP", "VOUCHER", "BLOG"];

const route = useRoute();
const router = useRouter();
const results = ref([]);
const loading = ref(false);
let requestVersion = 0;
let activeController = null;

const keyword = computed(() => String(route.query.keyword || "").trim());
const activeType = computed(() => {
  const type = String(route.query.type || "ALL").toUpperCase();
  return TABS.includes(type) ? type : "ALL";
});

async function loadResults() {
  const version = ++requestVersion;
  if (activeController) {
    activeController.abort();
  }
  if (!keyword.value) {
    results.value = [];
    loading.value = false;
    return;
  }

  activeController = new AbortController();
  loading.value = true;

  try {
    const { data } = await getSearchResults(
      {
        keyword: keyword.value,
        type: activeType.value
      },
      activeController.signal
    );
    if (version !== requestVersion) {
      return;
    }
    results.value = data || [];
  } catch (error) {
    if (version !== requestVersion || error?.code === "ERR_CANCELED") {
      return;
    }
    results.value = [];
    ElMessage.error(error.message || "搜索失败");
  } finally {
    if (version === requestVersion) {
      loading.value = false;
    }
  }
}

function switchType(type) {
  router.push({
    path: "/search-results",
    query: { keyword: keyword.value, type }
  });
}

function openResult(result) {
  if (result.type === "BLOG") {
    router.push(`/blog/${result.sourceId}`);
    return;
  }
  if (result.shopId) {
    router.push(`/shop/${result.shopId}`);
  }
}

watch(
  () => [route.query.keyword, route.query.type],
  loadResults,
  { immediate: true }
);

onBeforeUnmount(() => {
  requestVersion += 1;
  if (activeController) {
    activeController.abort();
  }
});
</script>

<template>
  <main class="page search-results-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>{{ keyword || "搜索结果" }}</h1>
      <span></span>
    </header>

    <section class="result-toolbar">
      <span>搜索“{{ keyword }}”</span>
      <strong>{{ results.length }} 条结果</strong>
    </section>

    <nav class="result-tabs">
      <button
        v-for="type in TABS"
        :key="type"
        :class="{ active: activeType === type }"
        @click="switchType(type)"
      >
        {{ TYPE_LABELS[type] }}
      </button>
    </nav>

    <section class="search-result-list">
      <article
        v-for="result in results"
        :key="`${result.type}-${result.sourceId}`"
        class="search-result-card"
        @click="openResult(result)"
      >
        <div class="result-image">
          <img :src="resolveImageUrl(result.image)" :alt="result.title" />
        </div>
        <div class="result-content">
          <div class="result-heading">
            <h2>{{ result.title }}</h2>
            <span>{{ TYPE_LABELS[result.type] || result.type }}</span>
          </div>
          <small>{{ result.subtitle }}</small>
          <p>{{ result.description || "暂无更多介绍" }}</p>
        </div>
      </article>

      <div v-if="loading" class="loading-state">正在搜索...</div>
      <div v-else-if="!results.length" class="empty-state">
        没有找到与“{{ keyword }}”相关的内容
      </div>
    </section>
  </main>
</template>

<style scoped>
.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  color: #777;
  font-size: 12px;
}

.result-toolbar strong {
  color: var(--brand);
  font-size: 12px;
}

.result-tabs {
  position: sticky;
  top: 56px;
  z-index: 15;
  display: flex;
  gap: 24px;
  padding: 0 16px;
  overflow-x: auto;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.result-tabs button {
  flex: 0 0 auto;
  padding: 12px 0 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #777;
  font-size: 13px;
}

.result-tabs button.active {
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 700;
}

.search-result-list {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.search-result-card {
  display: flex;
  min-height: 116px;
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
  cursor: pointer;
}

.result-image {
  flex: 0 0 116px;
  background: #eee;
}

.result-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.result-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
}

.result-heading {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.result-heading h2 {
  margin: 0;
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-heading span {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 10px;
  background: #fff0ea;
  color: var(--brand);
  font-size: 10px;
}

.result-content small {
  margin-top: 5px;
  overflow: hidden;
  color: #888;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-content p {
  display: -webkit-box;
  margin: auto 0 0;
  overflow: hidden;
  color: #666;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

@media (min-width: 720px) {
  .search-result-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
