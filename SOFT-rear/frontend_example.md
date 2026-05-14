# 前端项目实现方案

## 项目结构

```
WEB-vue/
├── src/
│   ├── components/
│   │   ├── LoginForm.vue       # 登录组件
│   │   ├── ImageUpload.vue     # 图片上传组件
│   │   └── ModelChat.vue       # 大模型聊天组件
│   ├── views/
│   │   ├── LoginView.vue       # 登录页面
│   │   ├── HomeView.vue        # 首页
│   │   └── ModelView.vue       # 大模型页面
│   ├── api/
│   │   ├── auth.js             # 认证相关API
│   │   ├── image.js            # 图片相关API
│   │   └── model.js            # 模型相关API
│   ├── utils/
│   │   └── axios.js            # Axios配置
│   ├── App.vue
│   └── main.js
├── vite.config.js
└── package.json
```

## 1. 安装依赖

在 `E:\WEB-vue` 目录下执行：

```bash
npm install axios
```

## 2. 配置 Vite 代理

修改 `vite.config.js`：

```javascript
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
```

## 3. Axios 配置

创建 `src/utils/axios.js`：

```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    if (response.data.code === 200) {
      return response.data
    } else {
      return Promise.reject(new Error(response.data.message || '请求失败'))
    }
  },
  error => {
    return Promise.reject(error)
  }
)

export default api
```

## 4. API 模块

### 认证 API (`src/api/auth.js`)

```javascript
import api from '../utils/axios'

export const login = (data) => {
  return api.post('/user/login', data)
}

export const register = (data) => {
  return api.post('/user', data)
}
```

### 图片 API (`src/api/image.js`)

```javascript
import api from '../utils/axios'

export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/image/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const processImage = (data) => {
  return api.post('/image/process', data)
}

export const processImageWithPython = (data) => {
  return api.post('/external/python/process-image', data)
}
```

### 模型 API (`src/api/model.js`)

```javascript
import api from '../utils/axios'

export const chatWithModel = (data) => {
  return api.post('/model/chat', data)
}

export const generateContent = (data) => {
  return api.post('/model/generate', data)
}

export const chatWithDify = (data) => {
  return api.post('/external/dify/chat', data)
}
```

## 5. 组件实现

### 登录组件 (`src/components/LoginForm.vue`)

```vue
<template>
  <div class="login-form">
    <h2>用户登录</h2>
    <form @submit.prevent="handleLogin">
      <div class="form-item">
        <label>用户名</label>
        <input v-model="form.userName" type="text" required>
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="form.passWord" type="password" required>
      </div>
      <button type="submit" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>
      <div v-if="error" class="error">{{ error }}</div>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '../api/auth'

const form = ref({
  userName: '',
  passWord: ''
})
const loading = ref(false)
const error = ref('')

const handleLogin = async () => {
  try {
    loading.value = true
    error.value = ''
    const response = await login(form.value)
    localStorage.setItem('user', JSON.stringify(response.data))
    // 跳转到首页
    window.location.href = '/'
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-form {
  max-width: 400px;
  margin: 0 auto;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.form-item {
  margin-bottom: 15px;
}

label {
  display: block;
  margin-bottom: 5px;
}

input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

button {
  width: 100%;
  padding: 10px;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error {
  color: red;
  margin-top: 10px;
}
</style>
```

### 图片上传组件 (`src/components/ImageUpload.vue`)

```vue
<template>
  <div class="image-upload">
    <h3>图片上传</h3>
    <input type="file" accept="image/*" @change="handleFileChange">
    <div v-if="imageUrl" class="preview">
      <img :src="imageUrl" alt="预览" style="max-width: 200px;">
    </div>
    <div v-if="loading" class="loading">上传中...</div>
    <div v-if="error" class="error">{{ error }}</div>
    
    <div v-if="imageUrl" class="process-options">
      <h4>图像处理</h4>
      <select v-model="processType">
        <option value="resize">调整大小</option>
        <option value="crop">裁剪</option>
        <option value="filter">滤镜</option>
      </select>
      <button @click="handleProcess">处理图片</button>
      <button @click="handleProcessWithPython">Python处理</button>
    </div>
    
    <div v-if="processResult" class="result">
      <h4>处理结果</h4>
      <p>{{ processResult }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { uploadImage, processImage, processImageWithPython } from '../api/image'

const imageUrl = ref('')
const loading = ref(false)
const error = ref('')
const processType = ref('resize')
const processResult = ref('')

const handleFileChange = async (event) => {
  const file = event.target.files[0]
  if (file) {
    try {
      loading.value = true
      error.value = ''
      const response = await uploadImage(file)
      imageUrl.value = response.data
    } catch (err) {
      error.value = err.message || '上传失败'
    } finally {
      loading.value = false
    }
  }
}

const handleProcess = async () => {
  if (!imageUrl.value) return
  
  try {
    loading.value = true
    error.value = ''
    const response = await processImage({
      imagePath: imageUrl.value,
      processType: processType.value
    })
    processResult.value = response.data
  } catch (err) {
    error.value = err.message || '处理失败'
  } finally {
    loading.value = false
  }
}

const handleProcessWithPython = async () => {
  if (!imageUrl.value) return
  
  try {
    loading.value = true
    error.value = ''
    const response = await processImageWithPython({
      imagePath: imageUrl.value,
      processType: processType.value
    })
    processResult.value = response.data.result
  } catch (err) {
    error.value = err.message || 'Python处理失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.image-upload {
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  margin: 20px 0;
}

.preview {
  margin: 10px 0;
}

.process-options {
  margin-top: 20px;
}

button {
  margin: 5px;
  padding: 5px 10px;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.loading {
  color: blue;
  margin: 10px 0;
}

.error {
  color: red;
  margin: 10px 0;
}

.result {
  margin-top: 20px;
  padding: 10px;
  background: #f0f0f0;
  border-radius: 4px;
}
</style>
```

### 大模型聊天组件 (`src/components/ModelChat.vue`)

```vue
<template>
  <div class="model-chat">
    <h3>大模型聊天</h3>
    <div class="chat-container">
      <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
        <div class="role">{{ message.role === 'user' ? '我' : '模型' }}:</div>
        <div class="content">{{ message.content }}</div>
      </div>
    </div>
    <div class="input-area">
      <input v-model="inputMessage" type="text" placeholder="输入消息..." @keyup.enter="sendMessage">
      <button @click="sendMessage" :disabled="loading">发送</button>
      <button @click="sendToDify" :disabled="loading">Dify</button>
    </div>
    <div v-if="loading" class="loading">处理中...</div>
    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { chatWithModel, chatWithDify } from '../api/model'

const inputMessage = ref('')
const messages = ref([
  { role: 'model', content: '你好，我是大模型，有什么可以帮助你的？' }
])
const loading = ref(false)
const error = ref('')

const sendMessage = async () => {
  if (!inputMessage.value) return
  
  const userMessage = inputMessage.value
  messages.value.push({ role: 'user', content: userMessage })
  inputMessage.value = ''
  
  try {
    loading.value = true
    error.value = ''
    const response = await chatWithModel({
      message: userMessage
    })
    messages.value.push({ role: 'model', content: response.data.response })
  } catch (err) {
    error.value = err.message || '发送失败'
  } finally {
    loading.value = false
  }
}

const sendToDify = async () => {
  if (!inputMessage.value) return
  
  const userMessage = inputMessage.value
  messages.value.push({ role: 'user', content: userMessage })
  inputMessage.value = ''
  
  try {
    loading.value = true
    error.value = ''
    const response = await chatWithDify({
      message: userMessage,
      userId: '123'
    })
    messages.value.push({ role: 'model', content: response.data.response })
  } catch (err) {
    error.value = err.message || 'Dify发送失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.model-chat {
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  margin: 20px 0;
}

.chat-container {
  height: 300px;
  overflow-y: auto;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 4px;
}

.message {
  margin: 10px 0;
  padding: 8px 12px;
  border-radius: 8px;
}

.message.user {
  background: #e3f2fd;
  align-self: flex-end;
}

.message.model {
  background: #f5f5f5;
  align-self: flex-start;
}

.role {
  font-weight: bold;
  margin-bottom: 4px;
}

.input-area {
  display: flex;
  gap: 10px;
}

input {
  flex: 1;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

button {
  padding: 0 15px;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.loading {
  color: blue;
  margin-top: 10px;
}

.error {
  color: red;
  margin-top: 10px;
}
</style>
```

## 6. 页面实现

### 登录页面 (`src/views/LoginView.vue`)

```vue
<template>
  <div class="login-view">
    <LoginForm />
  </div>
</template>

<script setup>
import LoginForm from '../components/LoginForm.vue'
</script>

<style scoped>
.login-view {
  max-width: 500px;
  margin: 100px auto;
  padding: 20px;
}
</style>
```

### 首页 (`src/views/HomeView.vue`)

```vue
<template>
  <div class="home-view">
    <h1>欢迎使用系统</h1>
    <div class="user-info" v-if="user">
      <p>当前用户: {{ user.userName }}</p>
      <button @click="logout">退出登录</button>
    </div>
    <div class="feature-grid">
      <div class="feature-card">
        <h3>图片处理</h3>
        <ImageUpload />
      </div>
      <div class="feature-card">
        <h3>大模型</h3>
        <ModelChat />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ImageUpload from '../components/ImageUpload.vue'
import ModelChat from '../components/ModelChat.vue'

const user = ref(null)

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    user.value = JSON.parse(userStr)
  }
})

const logout = () => {
  localStorage.removeItem('user')
  window.location.href = '/login'
}
</script>

<style scoped>
.home-view {
  padding: 20px;
}

.user-info {
  margin-bottom: 20px;
  padding: 10px;
  background: #f0f0f0;
  border-radius: 4px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.feature-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 20px;
}

button {
  padding: 5px 10px;
  background: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
```

## 7. 主应用配置

### App.vue

```vue
<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: Arial, sans-serif;
  line-height: 1.6;
  color: #333;
}

#app {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
</style>
```

### main.js

```javascript
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
```

## 8. 路由配置

创建 `src/router/index.js`：

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    const user = localStorage.getItem('user')
    if (user) {
      next()
    } else {
      next('/login')
    }
  } else {
    next()
  }
})

export default router
```

## 9. 安装路由

在 `E:\WEB-vue` 目录下执行：

```bash
npm install vue-router
```

## 10. 启动项目

### 后端启动

在 `E:\BD\MAVEN\SOFT-rear` 目录下执行：

```bash
./mvnw spring-boot:run
```

### 前端启动

在 `E:\WEB-vue` 目录下执行：

```bash
npm run dev
```

## 11. 测试说明

1. **登录功能**：使用任意用户名和密码登录（后端目前没有密码加密，直接比较明文）
2. **图片上传**：选择图片文件上传，然后选择处理类型进行处理
3. **大模型聊天**：在输入框中输入消息，点击发送按钮与大模型对话
4. **外部服务**：点击 Dify 按钮使用 Dify 服务，点击 Python 处理按钮使用 Python 图像处理服务

## 12. 注意事项

1. 确保后端服务在 `http://localhost:8080` 运行
2. 确保 Python 服务在 `http://localhost:5000` 运行
3. 确保 Dify 服务在 `http://localhost:8000` 运行
4. 前端服务默认在 `http://localhost:3000` 运行

## 13. 后续优化

1. 添加密码加密功能
2. 添加 JWT 认证
3. 完善错误处理
4. 添加更多图像处理功能
5. 集成更多大模型服务
6. 添加用户权限管理
7. 优化前端 UI/UX
