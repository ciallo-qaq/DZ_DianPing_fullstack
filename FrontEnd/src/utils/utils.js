const DEFAULT_IMAGE = "/imgs/icons/default-icon.png";

export function resolveImageUrl(path) {
  if (!path) {
    return DEFAULT_IMAGE;
  }

  const value = String(path).trim();
  if (/^(https?:|data:|blob:)/i.test(value)) {
    return value;
  }
  if (value.startsWith("/src/components/imgs")) {
    return value.replace("/src/components/imgs", "/imgs");
  }
  if (value.startsWith("/imgs/")) {
    return value;
  }
  if (value.startsWith("/blogs/") || value.startsWith("/avatars/")) {
    return `/imgs${value}`;
  }
  if (value.startsWith("blogs/") || value.startsWith("avatars/")) {
    return `/imgs/${value}`;
  }
  if (value.startsWith("/")) {
    return value;
  }
  return `/imgs/${value}`;
}

export function parseImages(images) {
  if (!images) {
    return [];
  }
  if (Array.isArray(images)) {
    return images.filter(Boolean).map(resolveImageUrl);
  }
  return String(images)
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map(resolveImageUrl);
}

export function normalizeScore(score) {
  const value = Number(score) || 0;
  return Math.min(value > 5 ? value / 10 : value, 5);
}

export function normalizeBlog(blog = {}) {
  const images = parseImages(blog.images);
  return {
    ...blog,
    images,
    img: images[0] || DEFAULT_IMAGE,
    iconUrl: resolveImageUrl(blog.icon)
  };
}

export function normalizeShop(shop = {}) {
  const images = parseImages(shop.images);
  return {
    ...shop,
    images,
    img: images[0] || DEFAULT_IMAGE,
    scoreValue: normalizeScore(shop.score)
  };
}

export function formatDate(value, withTime = false) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (number) => String(number).padStart(2, "0");
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return withTime ? `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}` : day;
}

export function formatDistance(distance) {
  const value = Number(distance);
  if (!Number.isFinite(value)) {
    return "";
  }
  return value < 1000
    ? `${value.toFixed(1)}m`
    : `${(value / 1000).toFixed(1)}km`;
}

export function formatPrice(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  const number = Number(value);
  return Number.isFinite(number) ? number.toLocaleString("zh-CN") : String(value);
}

export default {
  resolveImageUrl,
  parseImages,
  normalizeBlog,
  normalizeShop,
  normalizeScore,
  formatDate,
  formatDistance,
  formatPrice
};
