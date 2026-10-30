import { createApp } from 'vue';
import './style.css';
import 'element-plus/dist/index.css';
import App from './App.vue';
import * as element from '@element-plus/icons-vue';
import elementplus from 'element-plus';
import router from './router';

const app = createApp(App);
for (const [key, component] of Object.entries(element)) {
  app.component(key, component);
}
app.use(elementplus);
app.use(router).mount('#app');


