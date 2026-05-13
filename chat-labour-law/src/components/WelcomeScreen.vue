<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ (e: 'pick', text: string): void }>()

const faqGroups = [
  {
    icon: '📝',
    title: 'Employment Contracts',
    questions: [
      'What is a Fixed Duration Contract (FDC)?',
      'What is the difference between FDC and UDC in Cambodia?',
      'How many times can an FDC be renewed?',
      'What is the maximum probation period in Cambodia?',
      'What happens if there is no written contract?'
    ]
  },
  {
    icon: '⏱️',
    title: 'Working Hours & Overtime',
    questions: [
      'What are the legal working hours in Cambodia?',
      'What is considered overtime work?',
      'What is the overtime pay rate in Cambodia?',
      'How many overtime hours are legally allowed?',
      'What are the rules for night shift work?'
    ]
  },
  {
    icon: '💰',
    title: 'Salary & Wage',
    questions: [
      'What is the minimum wage in Cambodia?',
      'How often must salaries be paid?',
      'What deductions are legally allowed from salary?',
      'Are employers required to provide payslips?',
      'Are bonuses required by Cambodian labour law?'
    ]
  },
  {
    icon: '🌴',
    title: 'Leave & Holidays',
    questions: [
      'How many annual leave days are employees entitled to?',
      'What public holidays are recognized in Cambodia?',
      'What are the rules for sick leave?',
      'How many days of maternity leave are allowed?',
      'What leave is available for family emergencies?'
    ]
  }
]

// Default open the first group
const openGroupIndex = ref<number | null>(0)

function toggleGroup(index: number) {
  openGroupIndex.value = openGroupIndex.value === index ? null : index
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 py-8 text-center animate-fade-in w-full">
    <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-50 dark:bg-slate-800 text-brand-700 dark:text-brand-300 text-xs font-semibold mb-4">
      <span class="w-1.5 h-1.5 rounded-full bg-brand-500"></span>
      Cambodia Labour Law • Powered by AI
    </div>
    <h1 class="font-display text-3xl sm:text-4xl font-extrabold tracking-tight bg-gradient-to-br from-slate-900 to-brand-700 dark:from-white dark:to-brand-300 bg-clip-text text-transparent mb-3">
      Your trusted legal assistant
    </h1>
    <p class="text-slate-600 dark:text-slate-400 max-w-xl mx-auto mb-8 text-sm">
      Select a frequently asked question from the categories below to get started.
    </p>

    <!-- FAQ Accordion Container -->
    <div class="text-left space-y-3 max-w-3xl mx-auto">
      <div v-for="(group, index) in faqGroups" :key="group.title" 
           class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden shadow-sm transition-all duration-200">
        
        <!-- Accordion Header -->
        <button @click="toggleGroup(index)"
                class="w-full flex items-center justify-between p-4 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors">
          <div class="flex items-center gap-3">
            <span class="text-xl">{{ group.icon }}</span>
            <span class="font-semibold text-slate-900 dark:text-white">{{ group.title }}</span>
          </div>
          <svg class="w-5 h-5 text-slate-400 transition-transform duration-200"
               :class="{ 'rotate-180': openGroupIndex === index }"
               fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
          </svg>
        </button>

        <!-- Accordion Content -->
        <div v-show="openGroupIndex === index" 
             class="px-4 pb-4 bg-slate-50/50 dark:bg-slate-800/20 border-t border-slate-100 dark:border-slate-800">
          <div class="grid sm:grid-cols-1 md:grid-cols-2 gap-2 mt-4">
            <button v-for="(question, qIndex) in group.questions" :key="question"
                    @click="$emit('pick', question)"
                    class="text-left p-3 text-sm text-slate-700 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:border-brand-400 hover:text-brand-600 dark:hover:text-brand-400 hover:shadow-sm transition-all flex items-start gap-2">
              <span class="font-medium text-slate-400 dark:text-slate-500 mt-0.5">{{ qIndex + 1 }}.</span>
              <span class="flex-1">{{ question }}</span>
            </button>
          </div>
        </div>
        
      </div>
    </div>
    
    <p class="mt-8 text-xs text-slate-400">Informational only — not a substitute for licensed legal advice.</p>
  </div>
</template>

