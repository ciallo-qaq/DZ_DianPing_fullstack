<script setup>
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <main class="page map-page">
    <header class="page-header">
      <span></span>
      <h1>附近地图</h1>
      <span></span>
    </header>

    <section class="map-placeholder">
      <div class="radar">
        <el-icon><Location /></el-icon>
      </div>
      <h2>地图能力尚未配置</h2>
      <p>
        当前接口文档没有地图、坐标逆解析或店铺点位接口。你仍可以进入店铺列表，
        授权浏览器定位后按 5 公里范围查询附近店铺。
      </p>
      <el-button type="primary" @click="router.push('/home')">返回首页选择分类</el-button>
    </section>
  </main>
</template>

<style scoped>
.map-page {
  background: #f5f5f5;
}

.map-placeholder {
  width: min(90%, 480px);
  margin: 70px auto 0;
  padding: 30px;
  border-radius: 16px;
  background: #fff;
  text-align: center;
  box-shadow: 0 8px 28px rgb(0 0 0 / 7%);
}

.radar {
  display: grid;
  width: 100px;
  height: 100px;
  margin: 0 auto 18px;
  place-items: center;
  border: 14px solid #fff0e9;
  border-radius: 50%;
  background: var(--brand);
  box-shadow: 0 0 0 12px #fff7f3;
  color: #fff;
  font-size: 37px;
}

.map-placeholder h2 {
  margin: 0 0 9px;
}

.map-placeholder p {
  margin: 0 0 22px;
  color: #888;
  font-size: 13px;
  line-height: 1.75;
}
</style>
