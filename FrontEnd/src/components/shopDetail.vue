<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getShop, getShopVouchers, seckillVoucher } from "../api";
import { getToken } from "../utils/auth";
import { formatDate, formatPrice, normalizeShop } from "../utils/utils";

const route = useRoute();
const router = useRouter();
const shop = ref({ images: [], scoreValue: 0 });
const vouchers = ref([]);
const loading = ref(true);
const submittingId = ref(null);
const now = ref(Date.now());
let timer;

const displayImages = computed(() => (shop.value.images.length ? shop.value.images : [shop.value.img]));

function isSeckill(voucher) {
  return Number(voucher.type) === 1;
}

function isNotStarted(voucher) {
  return voucher.beginTime && new Date(voucher.beginTime).getTime() > now.value;
}

function isEnded(voucher) {
  return voucher.endTime && new Date(voucher.endTime).getTime() <= now.value;
}

function isSoldOut(voucher) {
  return voucher.stock !== undefined && voucher.stock !== null && Number(voucher.stock) <= 0;
}

function countdown(voucher) {
  const target = isNotStarted(voucher) ? voucher.beginTime : voucher.endTime;
  if (!target) {
    return "";
  }
  const seconds = Math.max(0, Math.floor((new Date(target).getTime() - now.value) / 1000));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remain = seconds % 60;
  const clock = [hours, minutes, remain].map((item) => String(item).padStart(2, "0")).join(":");
  return `${isNotStarted(voucher) ? "距开始" : "距结束"} ${days ? `${days}天 ` : ""}${clock}`;
}

function discount(voucher) {
  const pay = Number(voucher.payValue);
  const actual = Number(voucher.actualValue);
  if (!pay || !actual) {
    return "";
  }
  return `${(pay / actual * 10).toFixed(1)} 折`;
}

async function loadShop() {
  try {
    const { data } = await getShop(route.params.id);
    if (!data) {
      throw new Error("店铺不存在");
    }
    shop.value = normalizeShop(data);
  } catch (error) {
    ElMessage.error(error.message || "获取店铺信息失败");
  }
}

async function loadVouchers() {
  try {
    const { data } = await getShopVouchers(route.params.id);
    vouchers.value = data || [];
  } catch (error) {
    ElMessage.error(error.message || "获取优惠券失败");
  }
}

async function grabVoucher(voucher) {
  if (!getToken()) {
    router.push({
      path: "/login",
      query: { redirect: route.fullPath }
    });
    return;
  }
  if (isNotStarted(voucher) || isEnded(voucher) || isSoldOut(voucher)) {
    return;
  }

  submittingId.value = voucher.id;
  try {
    const { data } = await seckillVoucher(voucher.id);
    ElMessage.success(`抢购成功，订单号：${data}`);
    voucher.stock = Math.max(0, Number(voucher.stock || 0) - 1);
  } catch (error) {
    ElMessage.error(error.message || "抢购失败");
  } finally {
    submittingId.value = null;
  }
}

onMounted(async () => {
  timer = window.setInterval(() => {
    now.value = Date.now();
  }, 1000);
  await Promise.all([loadShop(), loadVouchers()]);
  loading.value = false;
});

onBeforeUnmount(() => window.clearInterval(timer));
</script>

<template>
  <main class="page detail-page">
    <header class="page-header floating-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>店铺详情</h1>
      <button class="back-button share-button" @click="router.push('/home')">
        <el-icon><HomeFilled /></el-icon>
      </button>
    </header>

    <div v-if="loading" class="loading-state">正在加载店铺...</div>
    <template v-else>
      <section class="shop-hero">
        <div class="image-scroll">
          <img
            v-for="image in displayImages"
            :key="image"
            :src="image"
            :alt="shop.name"
          />
        </div>
        <div class="shop-summary content-card">
          <h1>{{ shop.name }}</h1>
          <div class="rating">
            <el-rate :model-value="shop.scoreValue" disabled show-score text-color="#ff6633" />
            <span>{{ shop.comments || 0 }} 条评价</span>
          </div>
          <div class="shop-tags">
            <span>{{ shop.area || "本地生活" }}</span>
            <span>￥{{ shop.avgPrice ?? "-" }}/人</span>
            <span>已售 {{ shop.sold || 0 }}</span>
          </div>
          <p><el-icon><Location /></el-icon> {{ shop.address || "暂无地址" }}</p>
          <p><el-icon><Clock /></el-icon> {{ shop.openHours || "营业时间暂未提供" }}</p>
        </div>
      </section>

      <section class="section-block">
        <div class="section-heading">
          <h2>优惠券</h2>
          <span>{{ vouchers.length }} 张可用</span>
        </div>

        <div v-if="vouchers.length" class="voucher-list">
          <article
            v-for="voucher in vouchers"
            :key="voucher.id"
            class="voucher-card"
            :class="{ disabled: isEnded(voucher) || isSoldOut(voucher) }"
          >
            <div class="voucher-price">
              <strong>￥{{ formatPrice(voucher.payValue) }}</strong>
              <del v-if="voucher.actualValue">￥{{ formatPrice(voucher.actualValue) }}</del>
              <span v-if="discount(voucher)">{{ discount(voucher) }}</span>
            </div>
            <div class="voucher-info">
              <h3>{{ voucher.title }}</h3>
              <p>{{ voucher.subTitle || "店铺优惠券" }}</p>
              <small v-if="isSeckill(voucher)">
                {{ countdown(voucher) }}<template v-if="voucher.stock !== undefined"> · 剩 {{ voucher.stock }} 张</template>
              </small>
              <small v-else>使用时间：{{ formatDate(voucher.beginTime || voucher.createTime) || "以门店为准" }}</small>
            </div>
            <button
              v-if="isSeckill(voucher)"
              class="grab-button"
              :disabled="isNotStarted(voucher) || isEnded(voucher) || isSoldOut(voucher) || submittingId === voucher.id"
              @click="grabVoucher(voucher)"
            >
              {{ submittingId === voucher.id ? "提交中" : isSoldOut(voucher) ? "已抢光" : isNotStarted(voucher) ? "未开始" : isEnded(voucher) ? "已结束" : "立即抢" }}
            </button>
            <span v-else class="shop-voucher-label">店铺券</span>
          </article>
        </div>
        <div v-else class="empty-state">该店铺暂无可用优惠券</div>
      </section>

      <section class="section-block">
        <div class="section-heading">
          <h2>店铺信息</h2>
        </div>
        <div class="detail-grid">
          <div><span>店铺分类 ID</span><strong>{{ shop.typeId ?? "-" }}</strong></div>
          <div><span>评分</span><strong>{{ shop.scoreValue.toFixed(1) }}</strong></div>
          <div><span>评论数</span><strong>{{ shop.comments || 0 }}</strong></div>
          <div><span>销量</span><strong>{{ shop.sold || 0 }}</strong></div>
        </div>
        <p class="api-note">
          当前后端未提供店铺评论接口，因此页面不再展示模拟评论。
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.detail-page {
  background: #f5f5f5;
}

.floating-header {
  border-bottom-color: transparent;
}

.share-button {
  justify-content: flex-end;
  font-size: 21px;
}

.shop-hero {
  margin-top: -56px;
}

.image-scroll {
  display: flex;
  gap: 4px;
  height: 300px;
  padding-top: 56px;
  overflow-x: auto;
  background: #222;
  scroll-snap-type: x mandatory;
}

.image-scroll img {
  min-width: 86%;
  height: 100%;
  object-fit: cover;
  scroll-snap-align: center;
}

.shop-summary {
  position: relative;
  z-index: 2;
  margin: -18px 14px 0;
  padding: 17px;
  box-shadow: 0 3px 12px rgb(0 0 0 / 8%);
}

.shop-summary h1 {
  margin: 0 0 8px;
  font-size: 22px;
}

.rating {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #888;
  font-size: 12px;
}

.shop-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.shop-tags span {
  padding: 3px 8px;
  border-radius: 4px;
  background: #fff1ec;
  color: #9c4a2b;
  font-size: 11px;
}

.shop-summary p {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 8px 0 0;
  color: #777;
  font-size: 13px;
}

.section-block {
  margin: 12px 14px;
  padding: 16px;
  border-radius: 10px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-heading h2 {
  margin: 0;
  font-size: 17px;
}

.section-heading span {
  color: #999;
  font-size: 12px;
}

.voucher-list {
  display: grid;
  gap: 10px;
}

.voucher-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 13px;
  border: 1px solid #ffd6c7;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff9f6, #fff);
}

.voucher-card.disabled {
  filter: grayscale(0.5);
  opacity: 0.68;
}

.voucher-price {
  display: flex;
  flex: 0 0 84px;
  flex-direction: column;
  color: var(--brand);
}

.voucher-price strong {
  font-size: 20px;
}

.voucher-price del,
.voucher-price span {
  color: #999;
  font-size: 11px;
}

.voucher-price span {
  margin-top: 3px;
  color: #d55325;
}

.voucher-info {
  flex: 1;
  min-width: 0;
}

.voucher-info h3 {
  margin: 0 0 4px;
  font-size: 14px;
}

.voucher-info p,
.voucher-info small {
  display: block;
  margin: 0;
  overflow: hidden;
  color: #888;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voucher-info small {
  margin-top: 5px;
  color: #d55325;
}

.grab-button {
  flex: 0 0 auto;
  padding: 7px 12px;
  border: 0;
  border-radius: 16px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
}

.grab-button:disabled {
  background: #bbb;
}

.shop-voucher-label {
  padding: 5px 8px;
  border-radius: 12px;
  background: #fff0e9;
  color: var(--brand);
  font-size: 11px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.detail-grid div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  border-radius: 8px;
  background: #f8f8f8;
}

.detail-grid span {
  color: #999;
  font-size: 11px;
}

.detail-grid strong {
  font-size: 15px;
}

.api-note {
  margin: 14px 0 0;
  color: #aaa;
  font-size: 11px;
}
</style>
