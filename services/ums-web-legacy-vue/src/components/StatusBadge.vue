<script setup>
import { computed } from 'vue'
import { STATUS_LABELS } from '../api'

const props = defineProps({
  status: { type: String, required: true }
})

const CLS = {
  Pending: 'badge-amber', NeedsReview: 'badge-purple',
  Approved: 'badge-green', Rejected: 'badge-red',
  Success: 'badge-green', Failed: 'badge-red', NotRun: 'badge-gray'
}

const label = computed(() => STATUS_LABELS[props.status] || props.status)
const cls = computed(() => CLS[props.status] || 'badge-gray')
</script>

<template>
  <span class="badge" :class="cls">{{ label }}</span>
</template>
