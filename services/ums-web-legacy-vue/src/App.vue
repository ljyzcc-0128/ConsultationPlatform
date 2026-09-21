<script setup>
import { useToast } from './composables/useToast'

const { toasts } = useToast()
</script>

<template>
  <router-view />
  <div class="toast-area">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">{{ t.message }}</div>
  </div>
</template>
