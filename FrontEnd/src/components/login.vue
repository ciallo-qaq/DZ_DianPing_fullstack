<script setup>
import { onBeforeUnmount, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { login as loginApi, sendLoginCode } from "../api";
import { setToken } from "../utils/auth";

const route = useRoute();
const router = useRouter();
const form = reactive({
  phone: "",
  code: "",
  password: ""
});
const agreed = ref(false);
const sending = ref(false);
const submitting = ref(false);
const countdown = ref(0);
let timer;

function validateForm() {
  if (!form.phone.trim()) {
    ElMessage.warning("请输入手机号");
    return false;
  }
  if (!form.code.trim()) {
    ElMessage.warning("请输入验证码");
    return false;
  }
  if (!agreed.value) {
    ElMessage.warning("请先阅读并同意用户协议");
    return false;
  }
  return true;
}

async function sendCode() {
  if (!form.phone.trim()) {
    ElMessage.warning("请输入手机号");
    return;
  }
  sending.value = true;
  try {
    await sendLoginCode(form.phone.trim());
    ElMessage.success("验证码已发送。开发环境请查看 Spring Boot 控制台日志");
    countdown.value = 60;
    window.clearInterval(timer);
    timer = window.setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        window.clearInterval(timer);
      }
    }, 1000);
  } catch (error) {
    ElMessage.error(error.message || "验证码发送失败");
  } finally {
    sending.value = false;
  }
}

async function submit() {
  if (!validateForm()) {
    return;
  }
  submitting.value = true;
  try {
    const { data } = await loginApi({
      phone: form.phone.trim(),
      code: form.code.trim(),
      password: ""
    });
    if (!data) {
      throw new Error("登录接口没有返回 Token");
    }
    setToken(data);
    ElMessage.success("登录成功");
    const redirect = typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
      ? route.query.redirect
      : "/home";
    router.replace(redirect);
  } catch (error) {
    ElMessage.error(error.message || "登录失败");
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.replace("/home");
  }
}

onBeforeUnmount(() => window.clearInterval(timer));
</script>

<template>
  <main class="page login-page">
    <header class="page-header">
      <button class="back-button" @click="goBack">
        <el-icon><ArrowLeftBold /></el-icon>
      </button>
      <h1>登录 DZ_DianPing_Front</h1>
      <span></span>
    </header>

    <section class="login-panel">
      <div class="brand-mark">
        <img src="/imgs/bd.png" alt="DZ_DianPing_Front" />
      </div>
      <h1>发现好店，分享生活</h1>
      <p>使用手机号验证码快速登录</p>

      <div class="phone-field">
        <el-input
          v-model="form.phone"
          maxlength="11"
          placeholder="请输入手机号"
          clearable
        />
        <button :disabled="sending || countdown > 0" @click="sendCode">
          {{ countdown > 0 ? `${countdown} 秒后重发` : sending ? "发送中" : "获取验证码" }}
        </button>
      </div>
      <el-input
        v-model="form.code"
        class="code-field"
        maxlength="10"
        placeholder="请输入验证码"
        @keyup.enter="submit"
      />
      <el-button
        type="primary"
        class="login-button"
        :loading="submitting"
        @click="submit"
      >
        登录
      </el-button>

      <label class="agreement">
        <input v-model="agreed" type="checkbox" />
        <span>
          我已阅读并同意
          <a href="javascript:void(0)">《DZ_DianPing_Front 用户服务协议》</a>
          和
          <a href="javascript:void(0)">《隐私政策》</a>
        </span>
      </label>

      <button class="password-link" @click="router.push('/password-login')">
        查看密码登录说明
      </button>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  background:
    radial-gradient(circle at 50% 10%, #ffe3d7, transparent 34%),
    linear-gradient(180deg, #fff8f4, #f7f7f7);
}

.login-panel {
  width: min(92%, 430px);
  margin: 50px auto 0;
  padding: 28px 25px;
  border-radius: 16px;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 15px 40px rgb(151 66 29 / 12%);
  text-align: center;
}

.brand-mark {
  display: grid;
  width: 68px;
  height: 68px;
  margin: 0 auto 16px;
  overflow: hidden;
  place-items: center;
  border-radius: 20px;
  background: #fff1eb;
}

.brand-mark img {
  width: 54px;
}

.login-panel h1 {
  margin: 0;
  font-size: 23px;
}

.login-panel > p {
  margin: 7px 0 25px;
  color: #888;
  font-size: 13px;
}

.phone-field {
  display: flex;
  gap: 9px;
}

.phone-field button {
  flex: 0 0 112px;
  border: 1px solid var(--brand);
  border-radius: var(--el-border-radius-base);
  background: #fff;
  color: var(--brand);
  font-size: 12px;
}

.phone-field button:disabled {
  border-color: #ccc;
  color: #999;
}

.code-field {
  margin-top: 13px;
}

.login-button {
  width: 100%;
  margin-top: 16px;
  height: 42px;
}

.agreement {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  margin: 18px 0 12px;
  color: #999;
  font-size: 11px;
  line-height: 1.55;
  text-align: left;
}

.agreement input {
  margin-top: 2px;
  accent-color: var(--brand);
}

.password-link {
  border: 0;
  background: transparent;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
