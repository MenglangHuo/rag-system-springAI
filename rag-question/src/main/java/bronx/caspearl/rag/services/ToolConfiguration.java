package bronx.caspearl.rag.services;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ToolConfiguration {
    // This annotation tells the LLM exactly what this method does and when to use it
    @Tool(description = "Check the current processing status of a digital form using its reference ID (e.g., SF-12345).")
    public String checkFormStatus(@ToolParam(description = "The exact reference ID to look up") String referenceId) {

        System.out.println(">>> LLM triggered the checkFormStatus tool for ID: " + referenceId);

        // Mocking a database response
        if (referenceId.toUpperCase().startsWith("SF-")) {
            return "Status: APPROVED, Assigned To: HR Department, Last Updated: " + LocalDate.now();
        } else {
            return "Status: NOT_FOUND";
        }
    }
}
