package bronx.caspearl.rag.services;

// What the LLM needs to provide to call the tool
public record FormStatusRequest(String referenceId) {}
