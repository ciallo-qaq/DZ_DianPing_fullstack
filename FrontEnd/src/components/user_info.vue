<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  getCurrentUser,
  getMyBlogs,
  getSignCount,
  getUserInfo,
  signIn
} from "../api";
import { clearAuth } from "../utils/auth";
import { formatDate, normalizeBlog, resolveImageUrl } from "../utils/utils";

const router = useRouter();
const user = ref({});
const info = ref({});
const blogs = ref([]);
const current = ref(1);
const loadingBlogs = ref(false);
const finished = ref(false);
const signCount = ref(0);
const signing = ref(false);
const activeTab = ref("blogs");

async function loadUser() {
  try {
    const { data } = await getCurrentUser();
    user.value = data || {};
    if (user.value.id) {
      await Promise.all([loadUserInfo(), loadBlogs(true), loadSignCount()]);
    }
  } catch (error) {
    ElMessage.error(error.message || "获取用户信息失败");
  }
}

async function loadUserInfo() {
  try {
    const { data } = await getUserInfo(user.value.id);
    info.value = data || {};
    sessionStorage.setItem("userInfo", JSON.stringify(info.value));
  } catch (error) {
    console.warn(error);
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
    const { data } = await getMyBlogs(current.value);
    const page = (data || []).map(normalizeBlog);
    blogs.value.push(...page);
    finished.value = page.length < 10;
    current.value += 1;
  } catch (error) {
    finished.value = true;
    ElMessage.error(error.message || "获取我的笔记失败");
  } finally {
    loadingBlogs.value = false;
  }
}

async function loadSignCount() {
  try {
    const { data } = await getSignCount();
    signCount.value = Number(data) || 0;
  } catch {
    signCount.value = 0;
  }
}

async function handleSign() {
  signing.value = true;
  try {
    await signIn();
    ElMessage.success("签到成功");
    await loadSignCount();
  } catch (error) {
    ElMessage.error(error.message || "签到失败");
  } finally {
    signing.value = false;
  }
}

function logout() {
  // 后端退出接口当前固定返回“功能未完成”，不能等待它成功后再清理本地状态。
  clearAuth();
  ElMessage.success("已退出本地登录");
  router.replace("/login");
}

onMounted(loadUser);
</script>

<template>
  <main class="page profile-page">
    <section class="profile-hero">
      <div class="profile-tools">
        <button @click="router.push('/home')"><el-icon><House /></el-icon></button>
        <button @click="logout">退出登录</button>
      </div>
      <div class="identity">
        <img :src="resolveImageUrl(user.icon)" :alt="user.nickName" />
        <div>
          <h1>{{ user.nickName || "DZ_DianPing_Front 用户" }}</h1>
          <p>{{ info.city || "杭州" }} · {{ info.introduce || "还没有填写个人简介" }}</p>
        </div>
        <button class="edit-button" @click="router.push('/profile/edit')">资料</button>
      </div>
      <div class="stats">
        <button @click="activeTab = 'blogs'">
          <strong>{{ blogs.length }}</strong>
          <span>笔记</span>
        </button>
        <div>
          <strong>{{ info.fans || 0 }}</strong>
          <span>粉丝</span>
        </div>
        <div>
          <strong>{{ info.followee || 0 }}</strong>
          <span>关注</span>
        </div>
        <div>
          <strong>{{ info.credits || 0 }}</strong>
          <span>积分</span>
        </div>
      </div>
    </section>

    <section class="sign-card">
      <div>
        <strong>本月连续签到 {{ signCount }} 天</strong>
        <span>每天签到可以保持活跃记录</span>
      </div>
      <button :disabled="signing" @click="handleSign">
        {{ signing ? "签到中" : "今日签到" }}
      </button>
    </section>

    <section class="profile-content">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的笔记" name="blogs">
          <div class="my-blogs">
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
            <div v-else-if="!blogs.length" class="empty-state">
              你还没有发布笔记
              <el-button type="primary" @click="router.push('/blog-edit')">去发布</el-button>
            </div>
            <button v-else-if="!finished" class="load-button" @click="loadBlogs(false)">
              加载更多
            </button>
            <div v-else class="load-more">已经到底啦</div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="账号信息" name="account">
          <div class="account-grid">
            <div><span>用户 ID</span><strong>{{ user.id || "-" }}</strong></div>
            <div><span>昵称</span><strong>{{ user.nickName || "-" }}</strong></div>
            <div><span>城市</span><strong>{{ info.city || "-" }}</strong></div>
            <div><span>生日</span><strong>{{ formatDate(info.birthday) || "-" }}</strong></div>
            <div><span>积分</span><strong>{{ info.credits || 0 }}</strong></div>
            <div><span>会员标记</span><strong>{{ info.level ? "已开通" : "普通用户" }}</strong></div>
          </div>
          <p class="account-note">
            当前后端没有用户资料修改接口，因此“资料”页仅用于查看账号数据。
          </p>
        </el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>

<style scoped>
.profile-page {
  padding-bottom: 16px;
}

.profile-hero {
  padding: 18px 18px 22px;
  background:
    radial-gradient(circle at 85% 0%, rgb(255 255 255 / 45%), transparent 38%),
    linear-gradient(135deg, #ff6633, #ff9b55);
  color: #fff;
}

.profile-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-tools button {
  border: 0;
  background: transparent;
  color: #fff;
  font-size: 13px;
}

.profile-tools button:first-child {
  font-size: 21px;
}

.identity {
  display: flex;
  gap: 13px;
  align-items: center;
  margin-top: 18px;
}

.identity img {
  width: 72px;
  height: 72px;
  border: 3px solid rgb(255 255 255 / 80%);
  border-radius: 50%;
  object-fit: cover;
}

.identity > div {
  flex: 1;
  min-width: 0;
}

.identity h1 {
  margin: 0 0 5px;
  overflow: hidden;
  font-size: 21px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity p {
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  opacity: 0.82;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-button {
  padding: 6px 12px;
  border: 1px solid rgb(255 255 255 / 65%);
  border-radius: 15px;
  background: rgb(255 255 255 / 15%);
  color: #fff;
  font-size: 12px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 22px;
  text-align: center;
}

.stats button,
.stats div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border: 0;
  background: transparent;
  color: #fff;
}

.stats strong {
  font-size: 19px;
}

.stats span {
  font-size: 11px;
  opacity: 0.8;
}

.sign-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -10px 14px 12px;
  padding: 14px 15px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 16px rgb(0 0 0 / 9%);
}

.sign-card div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sign-card strong {
  font-size: 14px;
}

.sign-card span {
  color: #999;
  font-size: 11px;
}

.sign-card button,
.load-button {
  padding: 7px 15px;
  border: 0;
  border-radius: 16px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
}

.profile-content {
  margin: 0 12px;
  padding: 0 13px 12px;
  border-radius: 10px;
  background: #fff;
}

.my-blogs {
  display: grid;
  gap: 10px;
}

.my-blogs article {
  display: flex;
  gap: 11px;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}

.my-blogs article > img {
  flex: 0 0 92px;
  width: 92px;
  height: 92px;
  border-radius: 8px;
  object-fit: cover;
}

.my-blogs article > div {
  flex: 1;
  min-width: 0;
}

.my-blogs h2 {
  display: -webkit-box;
  margin: 2px 0 8px;
  overflow: hidden;
  font-size: 15px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.my-blogs p {
  margin: 0 0 9px;
  color: #aaa;
  font-size: 11px;
}

.my-blogs span {
  display: flex;
  gap: 4px;
  align-items: center;
  color: #888;
  font-size: 11px;
}

.my-blogs span .el-icon:nth-of-type(2) {
  margin-left: 10px;
}

.load-button {
  justify-self: center;
  margin: 8px auto;
  border: 1px solid var(--brand);
  background: #fff;
  color: var(--brand);
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding-top: 8px;
}

.account-grid div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px;
  border-radius: 8px;
  background: #f7f7f7;
}

.account-grid span {
  color: #999;
  font-size: 11px;
}

.account-note {
  color: #aaa;
  font-size: 11px;
  line-height: 1.7;
}
</style>
