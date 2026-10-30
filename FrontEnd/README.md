# DZ_DianPing_Front Vue 3 前端

DZ_DianPing_Front 使用 JavaScript、Vue 3、Vue Router、Element Plus 和 Vite，接口调用已按仓库根目录
`接口文档.md` 对齐。

## 本地运行

环境要求：

- Node.js 18 或更高版本
- npm
- Spring Boot 后端运行在 `http://localhost:8080`

在 `DZ_DianPing_Front` 目录执行：

```powershell
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

开发环境的请求链路：

```text
浏览器 /api/user/login
  -> Vite 开发服务器
  -> 去掉 /api
  -> Spring Boot http://localhost:8080/user/login
```

Vite 代理配置位于 `vite.config.js`。如果后端不在 `localhost:8080`，修改其中的
`target`。

## 生产构建

```powershell
npm run build
```

构建产物位于 `F:\nginx-1.30.6\html\DZ_DianPing_Front`，每个前端项目使用 Nginx
`html` 目录下的独立子目录。

## Nginx

本项目使用本机单实例 Nginx：

- 主配置：`F:\nginx-1.30.6\conf\nginx.conf`
- 项目配置：`F:\nginx-1.30.6\conf\conf.d\DZ_DianPing_Front.conf`
- 前端根目录：`F:\nginx-1.30.6\html\DZ_DianPing_Front`

以后新增项目时，只需在 `conf.d` 中增加新的 `.conf` 文件，无需再次修改主配置。

## 鉴权说明

- 登录成功后，前端把接口返回的原始 Token 保存到 `sessionStorage`。
- 每次请求都把 Token 原样写入 `authorization` 请求头，不添加 `Bearer` 前缀。
- 收到 HTTP 401 后，前端清理 Token 并跳转登录页。
- 后端 `/user/logout` 当前固定返回“功能未完成”，因此前端退出按钮只完成本地登出。

## 接口覆盖

`src/api/index.js` 覆盖接口文档中的全部 31 个有效接口。页面已接入用户、店铺、
博客、关注、优惠券、秒杀订单和文件上传相关接口。用户资料编辑、评论和地图能力因后端
没有对应接口，页面会明确提示而不发送不存在的请求。
