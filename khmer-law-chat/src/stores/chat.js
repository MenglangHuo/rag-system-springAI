import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { sseStream } from '@/composables/useStreaming';
const uid = () => Math.random().toString(36).slice(2, 10);
export const useChatStore = defineStore('chat', () => {
    const conversations = ref([]);
    const activeId = ref(null);
    const isStreaming = ref(false);
    const search = ref('');
    let abortCtrl = null;
    // hydrate
    try {
        const raw = localStorage.getItem('klc:conversations');
        if (raw)
            conversations.value = JSON.parse(raw);
        activeId.value = localStorage.getItem('klc:active') || null;
    }
    catch { }
    function persist() {
        localStorage.setItem('klc:conversations', JSON.stringify(conversations.value));
        if (activeId.value)
            localStorage.setItem('klc:active', activeId.value);
    }
    const active = computed(() => conversations.value.find(c => c.id === activeId.value) || null);
    const filtered = computed(() => {
        const q = search.value.trim().toLowerCase();
        if (!q)
            return conversations.value;
        return conversations.value.filter(c => c.title.toLowerCase().includes(q) ||
            c.messages.some(m => m.content.toLowerCase().includes(q)));
    });
    function newChat() {
        const c = { id: uid(), title: 'New conversation', messages: [], createdAt: Date.now(), updatedAt: Date.now() };
        conversations.value.unshift(c);
        activeId.value = c.id;
        persist();
    }
    function selectChat(id) { activeId.value = id; persist(); }
    function deleteChat(id) {
        conversations.value = conversations.value.filter(c => c.id !== id);
        if (activeId.value === id)
            activeId.value = conversations.value[0]?.id ?? null;
        persist();
    }
    function ensureActive() {
        if (!active.value)
            newChat();
    }
    async function sendMessage(text) {
        if (!text.trim() || isStreaming.value)
            return;
        ensureActive();
        const conv = active.value;
        const userMsg = { id: uid(), role: 'user', content: text.trim(), createdAt: Date.now() };
        conv.messages.push(userMsg);
        if (conv.messages.length === 1)
            conv.title = text.trim().slice(0, 48);
        const assistant = { id: uid(), role: 'assistant', content: '', createdAt: Date.now(), streaming: true };
        conv.messages.push(assistant);
        const proxiedAssistant = conv.messages[conv.messages.length - 1];
        conv.updatedAt = Date.now();
        persist();
        isStreaming.value = true;
        abortCtrl = new AbortController();
        const payload = {
            question: text,
            sessionId: conv.id
        };
        await sseStream('/api/rag/ask/stream', payload, {
            signal: abortCtrl.signal,
            onDelta: chunk => { proxiedAssistant.content += chunk; },
            onSessionId: id => {
                // We could update the conversation ID if the backend gives a different one,
                // but since we passed conv.id, the backend should just use ours.
                // If it did change it, we could handle it here.
            },
            onDone: () => { proxiedAssistant.streaming = false; isStreaming.value = false; persist(); },
            onError: () => { proxiedAssistant.streaming = false; isStreaming.value = false; },
        });
    }
    function stop() { abortCtrl?.abort(); isStreaming.value = false; if (active.value) {
        const last = active.value.messages.at(-1);
        if (last)
            last.streaming = false;
    } }
    async function regenerate() {
        if (!active.value || isStreaming.value)
            return;
        const msgs = active.value.messages;
        // remove last assistant
        while (msgs.length && msgs.at(-1)?.role === 'assistant')
            msgs.pop();
        const lastUser = [...msgs].reverse().find(m => m.role === 'user');
        if (!lastUser)
            return;
        msgs.pop(); // we'll re-add via sendMessage path
        await sendMessage(lastUser.content);
    }
    return { conversations, activeId, active, filtered, search, isStreaming,
        newChat, selectChat, deleteChat, sendMessage, stop, regenerate };
});
