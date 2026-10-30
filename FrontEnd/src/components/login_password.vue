<script setup>
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <main class="page password-page">
    <header class="page-header">
      <button class="back-button" @click="router.back()">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>密码登录说明</h1>
      <span></span>
    </header>

    <section class="notice-card">
      <el-icon><InfoFilled /></el-icon>
      <h2>当前后端不支持密码登录</h2>
      <p>
        接口文档明确说明：`POST /user/login` 只校验手机号和验证码，请求体中的
        `password` 字段不会被读取。为保证登录可用，请返回验证码登录。
      </p>
      <el-button type="primary" @click="router.replace('/login')">
        返回验证码登录
      </el-button>
    </section>
  </main>
</template>

<style scoped>
.password-page {
  background: #f6f6f6;
}

.notice-card {
  width: min(90%, 460px);
  margin: 70px auto 0;
  padding: 28px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 25px rgb(0 0 0 / 7%);
  text-align: center;
}

.notice-card > .el-icon {
  color: #e6a23c;
  font-size: 42px;
}

.notice-card h2 {
  margin: 14px 0 8px;
  font-size: 20px;
}

.notice-card p {
  margin: 0 0 22px;
  color: #777;
  font-size: 13px;
  line-height: 1.8;
}
</style>
