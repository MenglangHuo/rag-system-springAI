<!-- Convenience wrapper around ChatMessage for clarity in larger apps -->
<script setup lang="ts">
import type { Message } from '@/types/chat'
import ChatMessage from './ChatMessage.vue'
defineProps<{ message: Message }>()
</script>
<template><ChatMessage :message="message" /></template>
