<script setup lang="ts">
import { ref, nextTick } from 'vue'
const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ (e: 'send', text: string): void }>()
const text = ref('')
const ta = ref<HTMLTextAreaElement | null>(null)

async function autoresize() {
  await nextTick()
  if (!ta.value) return
  ta.value.style.height = 'auto'
  ta.value.style.height = Math.min(ta.value.scrollHeight, 220) + 'px'
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault(); submit()
  }
}
function submit() {
  if (!text.value.trim() || props.disabled) return
  emit('send', text.value)
  text.value = ''
  autoresize()
}
</script>
<template>
  <div class="card p-2 flex items-end gap-2">
    <!-- <button class="btn-ghost !p-2" title="Attach file (UI only)" aria-label="Attach file">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.5 12.5 21a5 5 0 0 1-7-7L14 5.5a3.5 3.5 0 0 1 5 5L10.5 19"/></svg>
    </button> -->
    <textarea ref="ta" v-model="text" @input="autoresize" @keydown="onKey"
      rows="1" placeholder="Ask about Cambodian labour law…"
      class="flex-1 resize-none bg-transparent px-2 py-2 text-[15px] focus:outline-none placeholder:text-slate-400 max-h-[220px]" />
    <!-- <button class="btn-ghost !p-2" title="Voice input (UI only)" aria-label="Voice input">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>
    </button> -->
    <button class="btn-primary !rounded-xl" :disabled="disabled || !text.trim()" @click="submit" aria-label="Send">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m3 11 18-8-8 18-2-8-8-2z"/></svg>
      Send
    </button>
  </div>
</template>
