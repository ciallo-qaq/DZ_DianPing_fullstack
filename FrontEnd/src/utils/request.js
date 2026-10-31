import axios from "axios";
import { ElMessage } from "element-plus";
import JSONbig from "json-bigint";
import { clearAuth, getToken, redirectToLogin } from "./auth";

const JSONParser = JSONbig({ storeAsString: true });

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 15000,
  transformResponse: [
    (data) => {
      if (!data || typeof data !== "string") {
        return data;
      }
      try {
        // Long 类型 ID 超过 Number.MAX_SAFE_INTEGER 时保留为字符串。
        return JSONParser.parse(data);
      } catch {
        return data;
      }
    }
  ]
});

service.interceptors.request.use(
  (config) => {
    // 每次请求都重新读取 Token，避免登录后必须刷新页面。
    const token = getToken();
    if (token) {
      config.headers.authorization = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

service.interceptors.response.use(
  (response) => {
    const result = response.data;
    if (result?.success === false) {
      const error = new Error(result.errorMsg || "请求失败");
      error.code = "BUSINESS_ERROR";
      error.response = response;
      return Promise.reject(error);
    }
    return result;
  },
  (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    const status = error.response?.status;
    const retryAfter = error.response?.headers?.["retry-after"];
    const serverMessage = error.response?.data?.errorMsg;

    if (status === 401) {
      clearAuth();
      redirectToLogin();
      ElMessage.warning("登录状态已失效，请重新登录");
    }

    const message =
      serverMessage ||
      (status === 429
        ? `操作过于频繁${retryAfter ? `，请 ${retryAfter} 秒后重试` : "，请稍后重试"}`
        : status
          ? `请求失败（HTTP ${status}）`
          : "无法连接后端服务，请确认 Spring Boot 已在 8080 端口启动");

    const normalizedError = new Error(message);
    normalizedError.status = status;
    normalizedError.retryAfter = retryAfter;
    normalizedError.cause = error;
    return Promise.reject(normalizedError);
  }
);

export default service;
