<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
import ConversationList from './ConversationList.vue'
import { useAuthStore } from '@/stores/auth';
defineProps<{ open: boolean }>()
defineEmits<{ (e: 'close'): void }>()
const chat = useChatStore()
const auth=useAuthStore()
</script>
<template>
  <aside
    class="fixed lg:static inset-y-0 left-0 z-30 w-72 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 flex flex-col transition-transform duration-300"
    :class="open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'">
    <div class="p-4 flex items-center gap-2">
      <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-600 to-brand-800 grid place-items-center text-white font-bold shadow-glow">⚖</div>
      <div class="flex-1">
        <p class="font-display font-bold leading-tight">Khmer Labour Law AI</p>
        <p class="text-[11px] text-slate-500">Cambodia Labour Law</p>
      </div>
      <button class="lg:hidden btn-ghost !p-2" @click="$emit('close')" aria-label="Close sidebar">✕</button>
    </div>

    <div class="px-3">
      <button class="btn-primary w-full" @click="chat.newChat()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
        New chat
      </button>
    </div>

    <div class="px-3 mt-4">
      <div class="relative">
        <svg class="absolute left-2.5 top-2.5 text-slate-400" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input v-model="chat.search" placeholder="Search conversations"
          class="w-full pl-8 pr-3 py-2 text-sm rounded-lg bg-slate-100 dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-500/40" />
      </div>
    </div>

    <div class="flex-1 overflow-y-auto px-2 mt-3">
      <p class="px-3 text-[11px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Recent</p>
      <ConversationList />
    </div>

    <div class="p-3 border-t border-slate-200 dark:border-slate-800 flex items-center gap-3">
      <div class="w-9 h-9 rounded-full bg-gradient-to-br from-blue-600 to-blue-400 grid place-items-center text-white text-sm font-bold">BT</div>
      <div class="flex-1 min-w-0">
        <p class="text-sm font-semibold truncate">Bronx Technology</p>
        <!-- <p class="text-xs text-slate-500 truncate">Free plan</p> -->
      </div>
      <!-- <button class="btn-ghost !p-2" title="Settings" aria-label="Settings">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>
      </button> -->
    
        <button class="btn-ghost !p-2" @click="auth.logout()" title="Logout">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 3l4 4-4 4M19 9H9"/></svg></button>
    </div>
  </aside>
</template>
