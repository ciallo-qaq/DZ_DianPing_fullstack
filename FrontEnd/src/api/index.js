import request from "../utils/request";

// 用户
export const sendLoginCode = (phone) =>
  request.post("/user/code", null, { params: { phone } });
export const login = (form) => request.post("/user/login", form);
export const logout = () => request.post("/user/logout");
export const getCurrentUser = () => request.get("/user/me");
export const getUserInfo = (id) => request.get(`/user/info/${id}`);
export const getUser = (id) => request.get(`/user/${id}`);
export const signIn = () => request.post("/user/sign");
export const getSignCount = () => request.get("/user/sign/count");

// 店铺与店铺分类
export const getShop = (id) => request.get(`/shop/${id}`);
export const createShop = (shop) => request.post("/shop", shop);
export const updateShop = (shop) => request.put("/shop", shop);
export const getShopsByType = (params) =>
  request.get("/shop/of/type", { params });
export const getShopsByName = (params) =>
  request.get("/shop/of/name", { params });
export const getShopTypes = () => request.get("/shop-type/list");

// 实时搜索
export const getSearchSuggestions = (keyword, requestId, signal) =>
  request.get("/search/suggestions", {
    params: { keyword, requestId },
    signal
  });
export const getSearchResults = (params, signal) =>
  request.get("/search/results", { params, signal });

// 探店笔记
export const createBlog = (blog) => request.post("/blog", blog);
export const toggleBlogLike = (id) => request.put(`/blog/like/${id}`);
export const getMyBlogs = (current = 1) =>
  request.get("/blog/of/me", { params: { current } });
export const getHotBlogs = (current = 1) =>
  request.get("/blog/hot", { params: { current } });
export const getBlog = (id) => request.get(`/blog/${id}`);
export const getBlogLikes = (id) => request.get(`/blog/likes/${id}`);
export const getUserBlogs = (id, current = 1) =>
  request.get("/blog/of/user", { params: { id, current } });
export const getFollowBlogs = (lastId, offset = 0) =>
  request.get("/blog/of/follow", { params: { lastId, offset } });

// 关注
export const followUser = (id, isFollow) =>
  request.put(`/follow/${id}/${isFollow}`);
export const isFollowing = (id) => request.get(`/follow/or/not/${id}`);
export const getCommonFollows = (id) => request.get(`/follow/common/${id}`);

// 优惠券与秒杀订单
export const createVoucher = (voucher) => request.post("/voucher", voucher);
export const createSeckillVoucher = (voucher) =>
  request.post("/voucher/seckill", voucher);
export const getShopVouchers = (shopId) =>
  request.get(`/voucher/list/${shopId}`);
export const seckillVoucher = (id) =>
  request.post(`/voucher-order/seckill/${id}`);

// 文件
export const uploadBlogImage = (file) => {
  const formData = new FormData();
  formData.append("file", file);
  return request.post("/upload/blog", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
};
export const deleteBlogImage = (name) =>
  request.get("/upload/blog/delete", { params: { name } });
