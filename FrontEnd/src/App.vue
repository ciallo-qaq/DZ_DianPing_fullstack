<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const showNav = computed(() => !route.meta.hideNav);

const go = (path) => router.push(path);
</script>

<template>
  <div class="app-frame" :class="{ 'no-nav': !showNav }">
    <RouterView />

    <nav v-if="showNav" class="bottom-nav">
      <button :class="{ active: route.path === '/home' }" @click="go('/home')">
        <el-icon><House /></el-icon>
        <span>首页</span>
      </button>
      <button :class="{ active: route.path === '/map' }" @click="go('/map')">
        <el-icon><Location /></el-icon>
        <span>地图</span>
      </button>
      <button class="publish-btn" @click="go('/blog-edit')">
        <el-icon><Plus /></el-icon>
      </button>
      <button :class="{ active: route.path === '/follow' }" @click="go('/follow')">
        <el-icon><ChatLineRound /></el-icon>
        <span>关注</span>
      </button>
      <button :class="{ active: route.path === '/profile' }" @click="go('/profile')">
        <el-icon><User /></el-icon>
        <span>我的</span>
      </button>
    </nav>
  </div>
</template>
