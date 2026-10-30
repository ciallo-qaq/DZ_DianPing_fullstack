<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getCurrentUser, getUserInfo } from "../api";
import { formatDate, resolveImageUrl } from "../utils/utils";

const router = useRouter();
const user = ref({});
const info = ref({});
const loading = ref(true);

onMounted(async () => {
  try {
    const { data } = await getCurrentUser();
    user.value = data || {};
    if (user.value.id) {
      const result = await getUserInfo(user.value.id);
      info.value = result.data || {};
    }
  } catch (error) {
    ElMessage.error(error.message || "读取资料失败");
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="page profile-form-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>账号资料</h1>
      <span></span>
    </header>

    <div v-if="loading" class="loading-state">正在读取资料...</div>
    <template v-else>
      <section class="avatar-card">
        <img :src="resolveImageUrl(user.icon)" :alt="user.nickName" />
        <div>
          <h2>{{ user.nickName || "DZ_DianPing_Front 用户" }}</h2>
          <p>用户 ID：{{ user.id || "-" }}</p>
        </div>
      </section>

      <section class="info-list">
        <div><span>昵称</span><strong>{{ user.nickName || "-" }}</strong></div>
        <div><span>城市</span><strong>{{ info.city || "-" }}</strong></div>
        <div><span>个人介绍</span><strong>{{ info.introduce || "-" }}</strong></div>
        <div><span>生日</span><strong>{{ formatDate(info.birthday) || "-" }}</strong></div>
        <div><span>性别标记</span><strong>{{ info.gender ? "是" : "否 / 未设置" }}</strong></div>
        <div><span>积分</span><strong>{{ info.credits || 0 }}</strong></div>
        <div><span>等级标记</span><strong>{{ info.level ? "已开通" : "普通用户" }}</strong></div>
      </section>

      <section class="notice">
        <el-icon><InfoFilled /></el-icon>
        <p>
          接口文档未提供更新用户资料或头像的接口，因此当前页面只展示后端已有数据，不提供无效的保存按钮。
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.profile-form-page {
  padding-bottom: 20px;
  background: #f4f4f4;
}

.avatar-card {
  display: flex;
  gap: 15px;
  align-items: center;
  margin: 14px;
  padding: 20px;
  border-radius: 12px;
  background: #fff;
}

.avatar-card img {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-card h2 {
  margin: 0 0 5px;
  font-size: 19px;
}

.avatar-card p {
  margin: 0;
  color: #999;
  font-size: 12px;
}

.info-list {
  margin: 14px;
  padding: 0 16px;
  border-radius: 12px;
  background: #fff;
}

.info-list div {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 15px 0;
  border-bottom: 1px solid #eee;
}

.info-list div:last-child {
  border-bottom: 0;
}

.info-list span {
  color: #777;
}

.info-list strong {
  max-width: 68%;
  color: #333;
  font-weight: 500;
  text-align: right;
  overflow-wrap: anywhere;
}

.notice {
  display: flex;
  gap: 8px;
  margin: 14px;
  padding: 12px;
  border-radius: 9px;
  background: #fff8ed;
  color: #b56b12;
}

.notice p {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
}
</style>
