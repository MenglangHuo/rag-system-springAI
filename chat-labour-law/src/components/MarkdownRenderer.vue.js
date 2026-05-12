import { computed } from 'vue';
import { marked } from 'marked';
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css';
const props = defineProps();
marked.setOptions({
    gfm: true,
    breaks: true,
});
// @ts-expect-error - marked highlight option
marked.use({ renderer: { code(code, lang) {
            const language = hljs.getLanguage(lang) ? lang : 'plaintext';
            const html = hljs.highlight(code, { language }).value;
            return `<pre><code class="hljs language-${language}">${html}</code></pre>`;
        } } });
const html = computed(() => marked.parse(props.source || ''));
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div)({
    ...{ class: "prose-chat max-w-none" },
});
__VLS_asFunctionalDirective(__VLS_directives.vHtml)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.html) }, null, null);
/** @type {__VLS_StyleScopedClasses['prose-chat']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-none']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            html: html,
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
