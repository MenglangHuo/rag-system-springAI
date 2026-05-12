import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
const VALID_USERNAME = 'Bronx';
const VALID_PASSWORD = 'Bronx@123';
export const useAuthStore = defineStore('auth', () => {
    const isAuthenticated = ref(false);
    const currentUser = ref(null);
    const error = ref(null);
    // hydrate from localStorage
    try {
        const stored = localStorage.getItem('klc:auth');
        if (stored) {
            const auth = JSON.parse(stored);
            isAuthenticated.value = auth.isAuthenticated;
            currentUser.value = auth.currentUser;
        }
    }
    catch { }
    const isLoggedIn = computed(() => isAuthenticated.value);
    function login(username, password) {
        error.value = null;
        if (username === VALID_USERNAME && password === VALID_PASSWORD) {
            isAuthenticated.value = true;
            currentUser.value = username;
            localStorage.setItem('klc:auth', JSON.stringify({
                isAuthenticated: true,
                currentUser: username
            }));
            return true;
        }
        else {
            error.value = 'Invalid username or password';
            return false;
        }
    }
    function logout() {
        isAuthenticated.value = false;
        currentUser.value = null;
        error.value = null;
        localStorage.removeItem('klc:auth');
    }
    function clearError() {
        error.value = null;
    }
    return {
        isAuthenticated,
        currentUser,
        error,
        isLoggedIn,
        login,
        logout,
        clearError
    };
});
