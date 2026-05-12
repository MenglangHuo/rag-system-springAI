<script setup lang="ts">
import { ref } from 'vue'
import type { Message } from '@/types/chat'
import MarkdownRenderer from './MarkdownRenderer.vue'

const props = defineProps<{ message: Message }>()
const copied = ref(false)
async function copy() {
  await navigator.clipboard.writeText(props.message.content)
  copied.value = true
  setTimeout(() => copied.value = false, 1500)
}
const time = new Date(props.message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
const isUser = props.message.role === 'user'
</script>
<template>
  <div class="group flex gap-3 animate-fade-in" :class="isUser ? 'flex-row-reverse' : ''">
    <div class="shrink-0 w-9 h-9 rounded-full grid place-items-center text-xs font-bold shadow-soft"
         :class="isUser ? 'bg-slate-200 dark:bg-slate-700 text-slate-800 dark:text-slate-100' : 'bg-gradient-to-br from-brand-600 to-brand-800 text-white'">
      <span v-if="isUser">You</span>
      <span v-else>⚖️</span>
    </div>
    <div class="max-w-[85%] sm:max-w-[75%]" :class="isUser ? 'text-right' : ''">
      <div class="rounded-2xl px-4 py-3 shadow-soft border"
           :class="isUser
             ? 'bg-brand-600 text-white border-brand-700 rounded-tr-sm'
             : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 rounded-tl-sm'">
        <template v-if="isUser">
          <p class="whitespace-pre-wrap leading-relaxed">{{ message.content }}</p>
        </template>
        <template v-else>
          <MarkdownRenderer :source="message.content || ''" />
          <span v-if="message.streaming && !message.content" class="inline-flex gap-1">
            <span class="w-2 h-2 bg-slate-400 rounded-full animate-pulse-dot"></span>
            <span class="w-2 h-2 bg-slate-400 rounded-full animate-pulse-dot" style="animation-delay:.15s"></span>
            <span class="w-2 h-2 bg-slate-400 rounded-full animate-pulse-dot" style="animation-delay:.3s"></span>
          </span>
        </template>
      </div>
      <div class="mt-1 flex items-center gap-2 text-xs text-slate-500 opacity-0 group-hover:opacity-100 transition"
           :class="isUser ? 'justify-end' : ''">
        <span>{{ time }}</span>
        <button v-if="!isUser && message.content" @click="copy" class="hover:text-brand-600">
          {{ copied ? 'Copied!' : 'Copy' }}
        </button>
      </div>
    </div>
  </div>
</template>
