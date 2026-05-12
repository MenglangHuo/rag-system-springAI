<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import ChatLayout from '@/components/ChatLayout.vue'
import LoginPage from '@/components/LoginPage.vue'

const auth = useAuthStore()
</script>
<template>
  <LoginPage v-if="!auth.isLoggedIn" />
  <ChatLayout v-else />
</template>
