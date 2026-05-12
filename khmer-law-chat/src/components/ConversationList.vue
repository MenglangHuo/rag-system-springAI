<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
const chat = useChatStore()
</script>
<template>
  <div class="space-y-1">
    <button v-for="c in chat.filtered" :key="c.id" @click="chat.selectChat(c.id)"
      class="group w-full text-left rounded-lg px-3 py-2 text-sm transition flex items-center justify-between"
      :class="chat.activeId === c.id ? 'bg-brand-50 dark:bg-slate-800 text-brand-700 dark:text-brand-300' : 'hover:bg-slate-100 dark:hover:bg-slate-800/60 text-slate-700 dark:text-slate-300'">
      <span class="truncate">{{ c.title || 'New conversation' }}</span>
      <span class="opacity-0 group-hover:opacity-100 transition text-slate-400 hover:text-red-500"
            @click.stop="chat.deleteChat(c.id)" role="button" aria-label="Delete">×</span>
    </button>
    <p v-if="!chat.filtered.length" class="text-xs text-slate-400 px-3 py-6 text-center">No conversations yet</p>
  </div>
</template>
