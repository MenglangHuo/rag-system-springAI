<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import Sidebar from './Sidebar.vue'
import WelcomeScreen from './WelcomeScreen.vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'

const chat = useChatStore()
const auth = useAuthStore()
const isDark = ref(false)
const sidebarOpen = ref(false)
const scrollEl = ref<HTMLElement | null>(null)

// Initialize dark mode from localStorage
onMounted(() => {
  const saved = localStorage.getItem('theme-mode')
  if (saved === 'dark') {
    isDark.value = true
  }
  updateTheme()
  scrollToBottom()
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

function toggleDark() {
  isDark.value = !isDark.value
}

async function scrollToBottom() {
  await nextTick()
  scrollEl.value?.scrollTo({ top: scrollEl.value.scrollHeight, behavior: 'smooth' })
}

watch(() => chat.active?.messages.map(m => m.content).join('|'), scrollToBottom)
onMounted(scrollToBottom)

function send(text: string) { chat.sendMessage(text); sidebarOpen.value = false }
</script>
<template>
  <div class="h-full flex bg-slate-50 dark:bg-slate-950">
    <Sidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    <div v-if="sidebarOpen" class="lg:hidden fixed inset-0 bg-black/40 z-20" @click="sidebarOpen = false" />

    <main class="flex-1 flex flex-col min-w-0">
      <header class="h-14 px-4 border-b border-slate-200 dark:border-slate-800 flex items-center gap-2 bg-white/70 dark:bg-slate-900/70 backdrop-blur sticky top-0 z-10">
        <button class="lg:hidden btn-ghost !p-2" @click="sidebarOpen = true" aria-label="Open menu">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        </button>
        <span class="text-xl">⚖️</span>
        <h2 class="font-semibold truncate">{{ chat.active?.title || 'New conversation' }}</h2>
        <div class="ml-auto flex items-center gap-1">
          <span class="text-sm text-slate-600 dark:text-slate-400">{{ auth.currentUser }}</span>
          <button class="btn-ghost !p-2" @click="toggleDark()" :aria-label="isDark ? 'Light mode' : 'Dark mode'">
            <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
          </button>
          <!-- <button class="btn-ghost !p-2" @click="auth.logout()" title="Logout">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 3l4 4-4 4M19 9H9"/></svg>
          </button> -->
        </div>
      </header>

      <div ref="scrollEl" class="flex-1 overflow-y-auto scroll-smooth">
        <template v-if="!chat.active || !chat.active.messages.length">
          <WelcomeScreen @pick="send" />
        </template>
        <template v-else>
          <div class="max-w-3xl mx-auto w-full px-4 py-6 space-y-6">
            <ChatMessage v-for="m in chat.active.messages" :key="m.id" :message="m" />
          </div>
        </template>
      </div>

      <div class="border-t border-slate-200 dark:border-slate-800 bg-white/70 dark:bg-slate-900/70 backdrop-blur">
        <div class="max-w-3xl mx-auto w-full px-4 py-3">
          <div class="flex justify-center gap-2 mb-2 h-8">
            <button v-if="chat.isStreaming" class="btn-ghost border border-slate-300 dark:border-slate-700" @click="chat.stop()">
              <span class="w-2 h-2 bg-red-500 rounded-sm"></span> Stop generating
            </button>
            <button v-else-if="chat.active?.messages.length && !chat.active.messages[chat.active.messages.length - 1].content" class="btn-ghost border border-slate-300 dark:border-slate-700" @click="chat.regenerate()">
              ↻ Regenerate
            </button>
          </div>
          <ChatInput :disabled="chat.isStreaming" @send="send" />
          <p class="text-[11px] text-slate-400 text-center mt-2">Khmer Labour Law AI may produce inaccurate information. Verify with a licensed lawyer.</p>
        </div>
      </div>
    </main>
  </div>
</template>
