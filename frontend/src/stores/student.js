import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getStudents } from '../api/student'

export const useStudentStore = defineStore('student', () => {
  const students = ref([])
  const loading = ref(false)

  async function fetchStudents() {
    loading.value = true
    try {
      const data = await getStudents()
      students.value = data.students || []
    } catch (e) {
      console.error('Failed to fetch students:', e)
    } finally {
      loading.value = false
    }
  }

  return { students, loading, fetchStudents }
})
