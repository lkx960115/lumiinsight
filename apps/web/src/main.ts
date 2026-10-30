import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'
import { applyTheme, readStoredTheme } from './stores/theme'
import './assets/theme.css'
import './assets/main.css'

// 挂载前先定主题，避免白底闪一下再变深色
applyTheme(readStoredTheme())

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permission)
app.mount('#app')
