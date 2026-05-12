# Khmer Law AI — Cambodia Labour Law Chat

Production-quality Vue 3 + Vite + TypeScript chat UI for a Cambodia Labour Law assistant, fully integrated with the `rag-question` streaming backend.

## Stack
- **Vue 3** (Composition API, `<script setup>`)
- **TypeScript** (strict)
- **Vite 5** (with API Proxying)
- **TailwindCSS 3**
- **Pinia** (state management)
- **VueUse** (`useDark`)
- **marked + highlight.js** (markdown + code highlighting)

## Features
- **Real-time Streaming**: Connected to the Spring Boot RAG backend using Server-Sent Events (SSE).
- **Intelligent Parser**: Custom SSE parser in `useStreaming.ts` that handles raw string chunks and session ID interception (`[SESSION:uuid]`).
- **Grouped FAQ**: Categorized law suggestions on the welcome screen with numbered lists for better UX.
- **Dark Mode**: Fully themed for both light and dark environments.
- **Reactive UI**: Built with Pinia and Vue 3 reactivity for smooth, flicker-free streaming.

## Run
Ensure your backend (`rag-question-service`) is running on `http://localhost:8090` (standard).

```bash
cd khmer-law-chat
npm install
npm run dev
```

## Folder Structure
```
src/
  components/
    ChatLayout.vue        # Main container with header, message list, and controls
    WelcomeScreen.vue     # Grouped FAQ categories and suggestion system
    ChatMessage.vue       # Reactive chat bubbles with Markdown support
    ChatInput.vue         # Auto-resizing textarea with keyboard support
    Sidebar.vue           # Collapsible history and profile panel
    MarkdownRenderer.vue  # marked.js integration for legal text formatting
  composables/
    useStreaming.ts       # SSE fetch-based stream reader and string decoder
  stores/
    chat.ts               # Pinia store managing messages, streaming states, and persistence
  types/
    chat.ts               # TypeScript interfaces for Conversations and Messages
```

## Backend Integration
The project is configured via `vite.config.ts` to proxy `/api` requests to the backend service.

- **Streaming Endpoint**: `/api/rag/ask/stream`
- **Request Format**: `POST` with `{"question": "...", "sessionId": "..."}`
- **Response Format**: `text/event-stream` returning raw string tokens.

## UI Logic
- **Regenerate Button**: Intelligent visibility—only appears if the previous generation failed or resulted in an empty response.
- **Streaming State**: Managed via `isStreaming` and `proxiedAssistant` references to ensure real-time UI updates during long responses.

## Theming
Edit `tailwind.config.js` (`brand` colors) and `src/style.css` for design tokens. Dark mode is automatically managed via the header toggle.
