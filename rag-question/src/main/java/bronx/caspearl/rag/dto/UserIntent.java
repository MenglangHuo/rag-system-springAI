package bronx.caspearl.rag.dto;

/**
 * Classifies the user's intent for routing through the correct pipeline.
 *
 * <ul>
 *   <li>{@link #NEW_QUESTION} — standalone question → rewrite + RAG retrieval</li>
 *   <li>{@link #FOLLOW_UP} — references previous conversation → rewrite with history + RAG retrieval</li>
 *   <li>{@link #SUMMARIZE_CONVERSATION} — recaps the whole chat/questions so far → skip RAG</li>
 *   <li>{@link #TRANSFORM_PREVIOUS_ANSWER} — rewrites, summarizes, translates, or formats the last answer → skip RAG</li>
 *   <li>{@link #CONVERSATIONAL} — greetings, thanks, chitchat → respond directly, skip RAG</li>
 * </ul>
 */
public enum UserIntent {

    /** A standalone legal question that doesn't reference conversation history. */
    NEW_QUESTION,

    /** A message that references or builds on previous context and needs retrieval. */
    FOLLOW_UP,

    /** A request to summarize the whole conversation, previous questions, or topics so far. */
    SUMMARIZE_CONVERSATION,

    /** A request to summarize, shorten, rephrase, translate, or format the previous answer. */
    TRANSFORM_PREVIOUS_ANSWER,

    /** Greetings, acknowledgments, or chitchat with no legal substance. */
    CONVERSATIONAL;

    /**
     * Parses an LLM classification response into a UserIntent.
     * Defaults to NEW_QUESTION if the response is unrecognizable (safest fallback —
     * ensures the question goes through full RAG pipeline).
     */
    public static UserIntent fromLlmResponse(String response) {
        if (response == null || response.isBlank()) {
            return NEW_QUESTION;
        }
        String upper = response.trim().toUpperCase();
        if (upper.contains("SUMMARIZE_CONVERSATION")
                || upper.contains("CONVERSATION_SUMMARY")
                || upper.contains("SUMMARY_OF_CONVERSATION")) {
            return SUMMARIZE_CONVERSATION;
        }
        if (upper.contains("TRANSFORM_PREVIOUS_ANSWER") || upper.contains("TRANSFORM")) {
            return TRANSFORM_PREVIOUS_ANSWER;
        }
        if (upper.contains("FOLLOW_UP") || upper.contains("FOLLOWUP")) {
            return FOLLOW_UP;
        }
        if (upper.contains("CONVERSATIONAL")) {
            return CONVERSATIONAL;
        }
        if (upper.contains("NEW_QUESTION") || upper.contains("NEW")) {
            return NEW_QUESTION;
        }
        return NEW_QUESTION;
    }
}
