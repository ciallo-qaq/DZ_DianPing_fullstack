<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getShopsByName, getShopsByType, getShopTypes } from "../api";
import { formatDistance, normalizeShop } from "../utils/utils";

const route = useRoute();
const router = useRouter();
const types = ref([]);
const shops = ref([]);
const current = ref(1);
const loading = ref(false);
const finished = ref(false);
const sortBy = ref("default");
const coordinates = ref(null);
let requestVersion = 0;

const title = computed(
  () => route.query.name || types.value.find((item) => String(item.id) === String(route.query.typeId))?.name || "店铺列表"
);

const sortedShops = computed(() => {
  const result = [...shops.value];
  if (sortBy.value === "comments") {
    result.sort((a, b) => Number(b.comments || 0) - Number(a.comments || 0));
  } else if (sortBy.value === "score") {
    result.sort((a, b) => b.scoreValue - a.scoreValue);
  }
  return result;
});

async function loadTypes() {
  try {
    const { data } = await getShopTypes();
    types.value = data || [];
  } catch (error) {
    ElMessage.error(error.message || "获取店铺分类失败");
  }
}

function buildParams() {
  const params = { current: current.value };
  const typeId = Number(route.query.typeId);
  const name = String(route.query.name || "").trim();

  if (typeId) {
    params.typeId = typeId;
  } else if (name) {
    params.name = name;
  } else {
    return null;
  }

  if (coordinates.value && !name && typeId) {
    params.x = coordinates.value.longitude;
    params.y = coordinates.value.latitude;
  }
  return params;
}

async function loadShops(reset = false) {
  if (!reset && (loading.value || finished.value)) {
    return;
  }
  if (reset) {
    requestVersion += 1;
    current.value = 1;
    shops.value = [];
    finished.value = false;
  }

  const version = requestVersion;
  const params = buildParams();
  if (!params) {
    finished.value = true;
    shops.value = [];
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const request = route.query.typeId ? getShopsByType(params) : getShopsByName(params);
    const { data } = await request;
    if (version !== requestVersion) {
      return;
    }
    const page = (data || []).map(normalizeShop);
    shops.value.push(...page);
    const pageSize = route.query.typeId ? 5 : 10;
    finished.value = page.length < pageSize;
    current.value += 1;
  } catch (error) {
    if (version !== requestVersion) {
      return;
    }
    finished.value = true;
    ElMessage.error(error.message || "查询店铺失败");
  } finally {
    if (version === requestVersion) {
      loading.value = false;
    }
  }
}

function switchType(type) {
  router.push({
    path: "/shop-list",
    query: { typeId: type.id, name: type.name }
  });
}

function locateNearby() {
  if (!navigator.geolocation) {
    ElMessage.warning("当前浏览器不支持定位");
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (position) => {
      coordinates.value = position.coords;
      ElMessage.success("已按当前位置查询 5 公里内店铺");
      loadShops(true);
    },
    () => ElMessage.warning("定位失败，将继续使用数据库默认顺序"),
    { timeout: 8000 }
  );
}

function openShop(shop) {
  router.push(`/shop/${shop.id}`);
}

watch(
  () => [route.query.typeId, route.query.name],
  () => loadShops(true)
);

onMounted(async () => {
  await loadTypes();
  loadShops(true);
});
</script>

<template>
  <main class="page shop-list-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>{{ title }}</h1>
      <button
        v-if="!route.query.name"
        class="back-button locate-button"
        title="附近店铺"
        @click="locateNearby"
      >
        <el-icon><Location /></el-icon>
      </button>
      <span v-else></span>
    </header>

    <section class="filter-bar">
      <el-dropdown trigger="click" @command="switchType">
        <button class="type-filter">
          {{ title }}
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="type in types" :key="type.id" :command="type">
              {{ type.name }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <div class="sort-tabs">
        <button :class="{ active: sortBy === 'default' }" @click="sortBy = 'default'">默认</button>
        <button :class="{ active: sortBy === 'comments' }" @click="sortBy = 'comments'">人气</button>
        <button :class="{ active: sortBy === 'score' }" @click="sortBy = 'score'">评分</button>
      </div>
    </section>

    <section class="shop-grid">
      <article v-for="shop in sortedShops" :key="shop.id" class="shop-card" @click="openShop(shop)">
        <div class="shop-cover">
          <img :src="shop.img" :alt="shop.name" loading="lazy" />
        </div>
        <div class="shop-content">
          <h2>{{ shop.name }}</h2>
          <div class="rating-row">
            <el-rate :model-value="shop.scoreValue" disabled />
            <span>{{ shop.comments || 0 }} 条评价</span>
          </div>
          <div class="meta-row">
            <span>{{ shop.area || "未知商圈" }}</span>
            <span v-if="shop.distance !== undefined">{{ formatDistance(shop.distance) }}</span>
          </div>
          <div class="price-row">
            <strong>￥{{ shop.avgPrice ?? "-" }}</strong>/人
          </div>
          <div class="address">
            <el-icon><MapLocation /></el-icon>
            {{ shop.address || "暂无地址" }}
          </div>
        </div>
      </article>

      <div v-if="loading" class="loading-state">正在加载店铺...</div>
      <div v-else-if="!sortedShops.length" class="empty-state">该条件下暂无店铺</div>
      <button v-else-if="!finished" class="load-again" @click="loadShops(false)">
        加载更多
      </button>
      <div v-else class="load-more">已经到底啦</div>
    </section>
  </main>
</template>

<style scoped>
.shop-list-page {
  padding-bottom: 16px;
}

.locate-button {
  justify-content: flex-end;
  font-size: 21px;
}

.filter-bar {
  position: sticky;
  top: 56px;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.type-filter {
  display: flex;
  gap: 5px;
  align-items: center;
  border: 0;
  background: transparent;
  color: #333;
  font-weight: 600;
}

.sort-tabs {
  display: flex;
  gap: 18px;
}

.sort-tabs button {
  padding: 4px 0;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #777;
  font-size: 13px;
}

.sort-tabs button.active {
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 600;
}

.shop-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 12px;
}

.shop-card {
  display: flex;
  min-height: 154px;
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
  cursor: pointer;
}

.shop-cover {
  flex: 0 0 144px;
  background: #eee;
}

.shop-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
}

.shop-content h2 {
  margin: 0 0 5px;
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rating-row,
.meta-row,
.address {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rating-row {
  font-size: 11px;
  color: #999;
}

.rating-row :deep(.el-rate) {
  height: 18px;
}

.rating-row :deep(.el-rate__icon) {
  font-size: 16px;
}

.meta-row {
  justify-content: space-between;
  margin-top: 6px;
  color: #777;
  font-size: 12px;
}

.price-row {
  margin-top: 5px;
  color: #777;
  font-size: 11px;
}

.price-row strong {
  color: var(--brand);
  font-size: 16px;
}

.address {
  min-width: 0;
  margin-top: auto;
  overflow: hidden;
  color: #999;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.load-again {
  justify-self: center;
  padding: 8px 28px;
  border: 1px solid var(--brand);
  border-radius: 20px;
  background: #fff;
  color: var(--brand);
}

.loading-state,
.empty-state,
.load-more,
.load-again {
  grid-column: 1 / -1;
}

@media (min-width: 720px) {
  .shop-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
