import { createRouter, createWebHashHistory } from "vue-router";
import { getToken } from "../utils/auth";

const routes = [
  { path: "/", redirect: "/home" },
  { path: "/home", name: "home", component: () => import("../components/router_1.vue") },
  { path: "/shop-list", name: "shop-list", component: () => import("../components/shopList.vue") },
  { path: "/shop/:id", name: "shop-detail", component: () => import("../components/shopDetail.vue") },
  {
    path: "/blog/:id",
    name: "blog-detail",
    component: () => import("../components/blog_details.vue"),
    meta: { requiresAuth: true, hideNav: true }
  },
  {
    path: "/blog-edit",
    name: "blog-edit",
    component: () => import("../components/blog_edit.vue"),
    meta: { requiresAuth: true, hideNav: true }
  },
  {
    path: "/follow",
    name: "follow",
    component: () => import("../components/follow.vue"),
    meta: { requiresAuth: true }
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("../components/user_info.vue"),
    meta: { requiresAuth: true }
  },
  {
    path: "/profile/edit",
    name: "profile-edit",
    component: () => import("../components/user_info_edit.vue"),
    meta: { requiresAuth: true, hideNav: true }
  },
  {
    path: "/users/:id",
    name: "user-detail",
    component: () => import("../components/other_user_info.vue"),
    meta: { requiresAuth: true, hideNav: true }
  },
  { path: "/map", name: "map", component: () => import("../components/router_2.vue") },
  { path: "/login", name: "login", component: () => import("../components/login.vue"), meta: { hideNav: true } },
  {
    path: "/password-login",
    name: "password-login",
    component: () => import("../components/login_password.vue"),
    meta: { hideNav: true }
  },

  // 兼容旧地址
  { path: "/1", redirect: "/home" },
  { path: "/2", redirect: "/map" },
  { path: "/3", redirect: "/follow" },
  { path: "/shopList", redirect: (to) => ({ path: "/shop-list", query: to.query }) },
  { path: "/shop_details", redirect: (to) => ({ path: `/shop/${to.query.id}`, query: to.query }) },
  { path: "/blog_details", redirect: (to) => `/blog/${to.query.id}` },
  { path: "/blog_edit", redirect: "/blog-edit" },
  { path: "/user_info", redirect: "/profile" },
  { path: "/user_info_edit", redirect: "/profile/edit" },
  { path: "/other_user_info", redirect: (to) => `/users/${to.query.id}` },
  { path: "/:pathMatch(.*)*", redirect: "/home" }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return {
      path: "/login",
      query: { redirect: to.fullPath }
    };
  }
  return true;
});

router.afterEach((to) => {
  document.title = to.meta.title || "DZ_DianPing_Front";
});

export default router;
