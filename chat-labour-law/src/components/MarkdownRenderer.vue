<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const props = defineProps<{ source: string }>()

marked.setOptions({
  gfm: true,
  breaks: true,
})
// @ts-expect-error - marked highlight option
marked.use({ renderer: { code(code: string, lang: string) {
  const language = hljs.getLanguage(lang) ? lang : 'plaintext'
  const html = hljs.highlight(code, { language }).value
  return `<pre><code class="hljs language-${language}">${html}</code></pre>`
}}})

const html = computed(() => marked.parse(props.source || '') as string)
</script>
<template>
  <div class="prose-chat max-w-none" v-html="html" />
</template>
