import ChatMessage from './ChatMessage.vue';
const __VLS_props = defineProps();
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {[typeof ChatMessage, ]} */ ;
// @ts-ignore
const __VLS_0 = __VLS_asFunctionalComponent(ChatMessage, new ChatMessage({
    message: (__VLS_ctx.message),
}));
const __VLS_1 = __VLS_0({
    message: (__VLS_ctx.message),
}, ...__VLS_functionalComponentArgsRest(__VLS_0));
var __VLS_3 = {};
var __VLS_2;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            ChatMessage: ChatMessage,
        };
    },
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
