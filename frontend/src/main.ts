import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import "@fontsource/noto-sans-sc/400.css";
import "@fontsource/noto-sans-sc/700.css";
import App from "./App.vue";
import router from "./router";
import { i18n } from "./i18n";
import { useAuthStore } from "./stores/auth";
import "./styles/main.css";

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(i18n);
app.use(ElementPlus, { locale: zhCn });

window.addEventListener("mop:unauthorized", () => {
  useAuthStore().clear();
  if (router.currentRoute.value.name !== "login") {
    void router.push({ name: "login" });
  }
});

app.mount("#app");
