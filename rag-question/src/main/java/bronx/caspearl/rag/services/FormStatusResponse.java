package bronx.caspearl.rag.services;

// What your Java method will return back to the LLM
public record FormStatusResponse(String status, String assignedTo, String lastUpdated) {}
