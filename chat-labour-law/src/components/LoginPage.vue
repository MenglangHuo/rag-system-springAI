<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isDark = ref(false)
const username = ref('Bronx')
const password = ref('Bronx@123')
const isLoading = ref(false)
const showPassword = ref(false)

// Initialize dark mode from localStorage
onMounted(() => {
  const saved = localStorage.getItem('theme-mode')
  if (saved === 'dark') {
    isDark.value = true
  }
  updateTheme()
})

// Watch for theme changes
watch(isDark, () => {
  updateTheme()
})

function updateTheme() {
  const htmlElement = document.documentElement
  if (isDark.value) {
    htmlElement.classList.add('dark')
    localStorage.setItem('theme-mode', 'dark')
  } else {
    htmlElement.classList.remove('dark')
    localStorage.setItem('theme-mode', 'light')
  }
}

async function handleLogin() {
  if (!username.value.trim() || !password.value.trim()) {
    auth.error = 'Please enter both username and password'
    return
  }

  isLoading.value = true
  // Simulate async operation
  setTimeout(() => {
    const success = auth.login(username.value, password.value)
    isLoading.value = false
    if (!success) {
      password.value = ''
    }
  }, 500)
}

function handleKeyPress(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    handleLogin()
  }
}
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-slate-950 flex items-center justify-center p-4">

    <!-- Login Card -->
    <div class="relative w-full max-w-md">
      <div class="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl overflow-hidden border border-gray-100 dark:border-slate-800">
        <!-- Header -->
        <div class="bg-gradient-to-br from-brand-50 via-sky-50 to-brand-50 dark:from-slate-800 dark:via-slate-700 dark:to-slate-800 px-6 pt-6 border-b border-gray-200 dark:border-slate-700 relative">
          <div class="absolute top-3 right-3">
            <button 
              @click="isDark = !isDark"
              class="p-2 rounded-lg hover:bg-gray-200 dark:hover:bg-slate-600 transition-colors"
              :title="isDark ? 'Light mode' : 'Dark mode'"
            >
              <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
              <svg v-else class="w-5 h-5 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              </svg>
            </button>
          </div>
          <div class="flex items-center justify-center gap-2 mb-2">
            <div class="flex items-center justify-center w-10 h-10 bg-gradient-to-br rounded-lg text-6xl py-5">
              ⚖️
            </div>
          </div>
          <h1 class="text-2xl font-bold text-gray-700 dark:text-white text-center mb-0.5 pt-3">Cambodia Labour Law</h1>
          <p class="text-center text-gray-600 dark:text-slate-300 text-xs font-medium pb-5">Intelligent Legal Assistant</p>
        </div>

        <!-- Form -->
        <div class="px-6 py-5">
          <!-- Error Message -->
          <div v-if="auth.error" class="mb-4 p-3 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg text-red-700 dark:text-red-300 text-xs">
            {{ auth.error }}
          </div>

          <!-- Username Field -->
          <div class="mb-4">
            <label class="block text-gray-700 dark:text-gray-500 font-semibold mb-1.5 text-sm">Username</label>
            <div class="relative">
              <input
                v-model="username"
                type="text"
                placeholder="Enter your username"
                @keypress="handleKeyPress"
                :disabled="isLoading"
                class="w-full text-gray-600 dark:text-gray-100 bg-white dark:bg-slate-800 px-3 py-2 border-2 border-gray-300 dark:border-slate-600 rounded-lg focus:outline-none focus:border-brand-500 dark:focus:border-brand-400 transition-colors disabled:bg-gray-100 dark:disabled:bg-slate-700 disabled:cursor-not-allowed text-sm"
              />
              <svg class="absolute right-3 top-2 w-4 h-4 text-gray-700 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
              </svg>
            </div>
          </div>

          <!-- Password Field -->
          <div class="mb-4">
            <label class="block text-gray-700 dark:text-gray-200 font-semibold mb-1.5 text-sm">Password</label>
            <div class="relative">
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="Enter your password"
                @keypress="handleKeyPress"
                :disabled="isLoading"
                class="w-full px-3 py-2 bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600 rounded-lg focus:outline-none focus:border-brand-500 dark:focus:border-brand-400 transition-colors disabled:bg-gray-100 dark:disabled:bg-slate-700 disabled:cursor-not-allowed text-sm text-gray-600 dark:text-gray-100"
              />
              <button
                type="button"
                @click="showPassword = !showPassword"
                :disabled="isLoading"
                class="absolute right-3 top-2 text-gray-700 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 disabled:cursor-not-allowed"
              >
                <svg v-if="!showPassword" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-4.803m5.596-3.856a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                </svg>
              </button>
            </div>
          </div>

          <!-- Login Button -->
          <button
            @click="handleLogin"
            :disabled="isLoading"
            class="w-full py-2 px-4 bg-gradient-to-r from-brand-600 to-brand-700 hover:from-brand-700 hover:to-brand-800 dark:from-brand-500 dark:to-brand-600 dark:hover:from-brand-600 dark:hover:to-brand-700 text-white font-semibold rounded-lg hover:shadow-lg transform hover:scale-105 transition-all disabled:opacity-70 disabled:cursor-not-allowed disabled:transform-none flex items-center justify-center gap-2 text-sm"
          >
            <svg
              v-if="isLoading"
              class="w-4 h-4 animate-spin"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
            </svg>
            <svg
              v-else
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1"/>
            </svg>
            {{ isLoading ? 'Logging in...' : 'Sign In' }}
          </button>

          <!-- Demo Credentials Info -->
          <!-- <div class="mt-4 p-3 bg-brand-50 dark:bg-slate-800 border border-brand-200 dark:border-slate-700 rounded-lg text-xs text-gray-600 dark:text-gray-300">
            <p class="font-semibold text-gray-700 dark:text-gray-100">Demo: bronx / bronx123</p>
          </div> -->
        </div>

        <!-- Footer -->
        <div class="px-6 py-3 bg-gray-50 dark:bg-slate-800 border-t border-gray-200 dark:border-slate-700 text-center text-xs text-gray-600 dark:text-gray-400">
          <p>Powered by Bronx Technology</p>
        </div>
      </div>
    </div>
  </div>
</template>
