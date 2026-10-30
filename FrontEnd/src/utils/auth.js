const TOKEN_KEY = "token";

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || "";
}

export function setToken(token) {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  }
}

export function clearAuth() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem("userInfo");
}

export function redirectToLogin() {
  const currentPath = window.location.hash.replace(/^#/, "") || "/";
  if (currentPath.startsWith("/login")) {
    return;
  }
  window.location.hash = `#/login?redirect=${encodeURIComponent(currentPath)}`;
}
