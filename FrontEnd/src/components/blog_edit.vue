<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  createBlog,
  deleteBlogImage,
  getShopsByName,
  uploadBlogImage
} from "../api";
import { parseImages } from "../utils/utils";

const router = useRouter();
const form = reactive({
  title: "",
  content: ""
});
const shopName = ref("");
const shops = ref([]);
const selectedShop = ref(null);
const showShopDialog = ref(false);
const inputRef = ref();
const imagePaths = ref([]);
const uploading = ref(false);
const submitting = ref(false);

async function queryShops() {
  try {
    const { data } = await getShopsByName({
      name: shopName.value.trim(),
      current: 1
    });
    shops.value = data || [];
  } catch (error) {
    ElMessage.error(error.message || "查询店铺失败");
  }
}

async function handleFileChange(event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) {
    return;
  }
  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning("图片不能超过 10MB");
    return;
  }

  uploading.value = true;
  try {
    const { data } = await uploadBlogImage(file);
    imagePaths.value.push(data);
  } catch (error) {
    ElMessage.error(error.message || "图片上传失败");
  } finally {
    uploading.value = false;
  }
}

async function removeImage(index) {
  const path = imagePaths.value[index];
  try {
    await deleteBlogImage(path.replace(/^\/+/, ""));
  } catch (error) {
    // 服务端删除失败时仍允许从本次编辑中移除，避免卡住发布流程。
    console.warn(error);
  }
  imagePaths.value.splice(index, 1);
}

function selectShop(shop) {
  selectedShop.value = shop;
  showShopDialog.value = false;
}

async function submitBlog() {
  if (!form.title.trim()) {
    ElMessage.warning("请填写笔记标题");
    return;
  }
  if (!form.content.trim()) {
    ElMessage.warning("请填写笔记内容");
    return;
  }
  if (!imagePaths.value.length) {
    ElMessage.warning("请至少上传一张图片");
    return;
  }
  if (!selectedShop.value?.id) {
    ElMessage.warning("请选择关联店铺");
    return;
  }

  submitting.value = true;
  try {
    const { data } = await createBlog({
      title: form.title.trim(),
      content: form.content.trim(),
      images: imagePaths.value.join(","),
      shopId: selectedShop.value.id
    });
    ElMessage.success("笔记发布成功");
    router.replace(data ? `/blog/${data}` : "/profile");
  } catch (error) {
    ElMessage.error(error.message || "发布失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(queryShops);
</script>

<template>
  <main class="page editor-page">
    <header class="page-header">
      <button class="text-button" @click="router.back()">取消</button>
      <h1>发布探店笔记</h1>
      <button class="publish-button" :disabled="submitting" @click="submitBlog">
        {{ submitting ? "发布中" : "发布" }}
      </button>
    </header>

    <section class="editor-body">
      <div class="upload-panel">
        <input
          ref="inputRef"
          class="hidden-input"
          type="file"
          accept="image/*"
          @change="handleFileChange"
        />
        <button class="add-image" :disabled="uploading" @click="inputRef.click()">
          <el-icon><Plus /></el-icon>
          <span>{{ uploading ? "上传中" : "添加图片" }}</span>
        </button>
        <div v-for="(path, index) in imagePaths" :key="path" class="preview-image">
          <img :src="parseImages(path)[0]" alt="笔记图片" />
          <button @click="removeImage(index)"><el-icon><Close /></el-icon></button>
        </div>
      </div>

      <input
        v-model="form.title"
        class="title-input"
        maxlength="50"
        placeholder="填写标题更容易上首页哦~"
      />
      <textarea
        v-model="form.content"
        class="content-input"
        maxlength="2000"
        placeholder="最近打卡了什么地方，有什么新奇体验呢？"
      ></textarea>

      <button class="shop-selector" @click="showShopDialog = true">
        <span>关联商户</span>
        <strong>{{ selectedShop?.name || "选择店铺" }}</strong>
        <el-icon><ArrowRight /></el-icon>
      </button>
      <p class="form-tip">图片、标题、内容和关联店铺为当前前端的发布校验条件。</p>
    </section>

    <div v-if="showShopDialog" class="dialog-mask" @click.self="showShopDialog = false">
      <section class="shop-dialog">
        <header>
          <h2>选择关联店铺</h2>
          <button @click="showShopDialog = false"><el-icon><Close /></el-icon></button>
        </header>
        <div class="shop-search">
          <el-input
            v-model="shopName"
            clearable
            placeholder="输入店铺名称前缀"
            @keyup.enter="queryShops"
          />
          <el-button type="primary" @click="queryShops">搜索</el-button>
        </div>
        <div class="shop-options">
          <button v-for="shop in shops" :key="shop.id" @click="selectShop(shop)">
            <strong>{{ shop.name }}</strong>
            <span>{{ shop.area || shop.address || "暂无区域信息" }}</span>
          </button>
          <div v-if="!shops.length" class="empty-state">没有找到匹配店铺</div>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.editor-page {
  background: #fff;
}

.publish-button {
  padding: 6px 13px;
  border: 0;
  border-radius: 15px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
}

.publish-button:disabled {
  opacity: 0.6;
}

.editor-body {
  padding: 18px;
}

.hidden-input {
  display: none;
}

.upload-panel {
  display: flex;
  gap: 9px;
  padding-bottom: 18px;
  overflow-x: auto;
  border-bottom: 1px solid #eee;
}

.add-image,
.preview-image {
  position: relative;
  flex: 0 0 112px;
  width: 112px;
  height: 132px;
  border-radius: 8px;
}

.add-image {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  border: 1px dashed #b8b8b8;
  background: #fafafa;
  color: #888;
  font-size: 12px;
}

.add-image .el-icon {
  font-size: 27px;
}

.preview-image img {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  object-fit: cover;
}

.preview-image button {
  position: absolute;
  top: 4px;
  right: 4px;
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: rgb(0 0 0 / 60%);
  color: #fff;
}

.title-input,
.content-input {
  width: 100%;
  border: 0;
  outline: 0;
}

.title-input {
  padding: 18px 0;
  border-bottom: 1px solid #eee;
  font-size: 17px;
  font-weight: 600;
}

.content-input {
  min-height: 280px;
  padding: 16px 0;
  resize: vertical;
  line-height: 1.75;
}

.shop-selector {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 14px 0;
  border: 0;
  border-block: 1px solid #eee;
  background: transparent;
}

.shop-selector span {
  color: #333;
  font-weight: 600;
}

.shop-selector strong {
  flex: 1;
  overflow: hidden;
  color: #888;
  font-size: 13px;
  font-weight: 400;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-tip {
  color: #aaa;
  font-size: 11px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgb(0 0 0 / 42%);
}

.shop-dialog {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  width: min(100%, var(--page-width));
  max-height: 72vh;
  margin: 0 auto;
  padding: 16px;
  border-radius: 16px 16px 0 0;
  background: #fff;
}

.shop-dialog header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shop-dialog h2 {
  margin: 0;
  font-size: 18px;
}

.shop-dialog header button {
  border: 0;
  background: transparent;
  font-size: 20px;
}

.shop-search {
  display: flex;
  gap: 8px;
  margin: 14px 0;
}

.shop-options {
  max-height: 48vh;
  overflow-y: auto;
}

.shop-options > button {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 13px 2px;
  border: 0;
  border-bottom: 1px solid #eee;
  background: transparent;
  text-align: left;
}

.shop-options span {
  color: #999;
  font-size: 12px;
}
</style>
