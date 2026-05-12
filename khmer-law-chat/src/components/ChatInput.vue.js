import { ref, nextTick } from 'vue';
const props = defineProps();
const emit = defineEmits();
const text = ref('');
const ta = ref(null);
async function autoresize() {
    await nextTick();
    if (!ta.value)
        return;
    ta.value.style.height = 'auto';
    ta.value.style.height = Math.min(ta.value.scrollHeight, 220) + 'px';
}
function onKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        submit();
    }
}
function submit() {
    if (!text.value.trim() || props.disabled)
        return;
    emit('send', text.value);
    text.value = '';
    autoresize();
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "card p-2 flex items-end gap-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.textarea)({
    ...{ onInput: (__VLS_ctx.autoresize) },
    ...{ onKeydown: (__VLS_ctx.onKey) },
    ref: "ta",
    value: (__VLS_ctx.text),
    rows: "1",
    placeholder: "Ask about Cambodian labour law…",
    ...{ class: "flex-1 resize-none bg-transparent px-2 py-2 text-[15px] focus:outline-none placeholder:text-slate-400 max-h-[220px]" },
});
/** @type {typeof __VLS_ctx.ta} */ ;
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.submit) },
    ...{ class: "btn-primary !rounded-xl" },
    disabled: (__VLS_ctx.disabled || !__VLS_ctx.text.trim()),
    'aria-label': "Send",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.svg, __VLS_intrinsicElements.svg)({
    width: "18",
    height: "18",
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    'stroke-width': "2",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.path)({
    d: "m3 11 18-8-8 18-2-8-8-2z",
});
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-end']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['resize-none']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-transparent']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[15px]']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['placeholder:text-slate-400']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-[220px]']} */ ;
/** @type {__VLS_StyleScopedClasses['btn-primary']} */ ;
/** @type {__VLS_StyleScopedClasses['!rounded-xl']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            text: text,
            ta: ta,
            autoresize: autoresize,
            onKey: onKey,
            submit: submit,
        };
    },
    __typeEmits: {},
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeEmits: {},
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
