package bronx.caspearl.rag.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * LLM-callable tools for computing legal calculations under Cambodian Labour Law.
 * The LLM autonomously decides when to call these based on user questions like
 * "How much severance would I get after 5 years?" or "When does my probation end?"
 */
@Slf4j
@Service
public class LegalCalculatorTools {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Calculates severance pay (indemnity for dismissal) under Cambodian Labour Law.
     *
     * UDC (Article 89): 15 days' wages per year for ≤ 6 months to 1 year,
     * then proportionally for each additional year.
     * In practice: 15 days per year of service.
     *
     * FDC (Article 73): 5% of total wages paid during the contract period.
     */
    @Tool(description = "Calculate severance pay (dismissal indemnity) for an employee " +
                         "under Cambodian Labour Law. Uses Article 73 for FDC contracts " +
                         "and Article 89 for UDC contracts.")
    public String calculateSeverance(
            @ToolParam(description = "Contract type: 'FDC' (Fixed Duration Contract) or 'UDC' (Undetermined Duration Contract)")
            String contractType,
            @ToolParam(description = "Total number of complete years of service")
            int yearsOfService,
            @ToolParam(description = "Monthly base salary in USD")
            double monthlySalary) {

        log.info(">>> Tool: calculateSeverance({}, {} years, ${}/month)", contractType, yearsOfService, monthlySalary);

        if (yearsOfService < 0) return "Error: Years of service cannot be negative.";
        if (monthlySalary <= 0) return "Error: Monthly salary must be positive.";

        double severance;
        String legalBasis;

        if ("UDC".equalsIgnoreCase(contractType)) {
            // Article 89: 15 days' wages per year of service
            double dailyWage = monthlySalary / 30.0;
            severance = yearsOfService * 15 * dailyWage;
            legalBasis = "Article 89 (UDC dismissal indemnity: 15 days' wages per year of service)";
        } else if ("FDC".equalsIgnoreCase(contractType)) {
            // Article 73: 5% of total wages paid during the contract
            double totalWages = yearsOfService * 12 * monthlySalary;
            severance = totalWages * 0.05;
            legalBasis = "Article 73 (FDC end-of-contract indemnity: 5% of total wages paid)";
        } else {
            return "Error: Contract type must be 'FDC' or 'UDC'. Received: " + contractType;
        }

        return String.format(
                "Severance Calculation Result:\n" +
                "- Contract Type: %s\n" +
                "- Years of Service: %d\n" +
                "- Monthly Salary: $%.2f USD\n" +
                "- Estimated Severance: $%.2f USD\n" +
                "- Legal Basis: %s\n" +
                "Note: This is an estimate. Actual amounts may vary based on specific circumstances, " +
                "additional allowances, and any applicable collective bargaining agreements.",
                contractType.toUpperCase(), yearsOfService, monthlySalary, severance, legalBasis);
    }

    /**
     * Calculates the required notice period for contract termination
     * under Articles 73-75 of the Cambodia Labour Law.
     *
     * UDC notice periods (Article 75):
     * - Less than 6 months: 7 days
     * - 6 months to 2 years: 15 days
     * - 2 to 5 years: 1 month
     * - 5 to 10 years: 2 months
     * - More than 10 years: 3 months
     */
    @Tool(description = "Calculate the legal notice period required for contract termination " +
                         "under Cambodian Labour Law Articles 73-75, based on contract type and seniority.")
    public String calculateNoticePeriod(
            @ToolParam(description = "Contract type: 'FDC' or 'UDC'")
            String contractType,
            @ToolParam(description = "Total number of complete years of service")
            int yearsOfService,
            @ToolParam(description = "Planned termination date in YYYY-MM-DD format (optional, use 'none' if unknown)")
            String terminationDate) {

        log.info(">>> Tool: calculateNoticePeriod({}, {} years, {})", contractType, yearsOfService, terminationDate);

        if ("FDC".equalsIgnoreCase(contractType)) {
            return "FDC Notice Period:\n" +
                   "- Fixed Duration Contracts (FDC) expire automatically at the end date.\n" +
                   "- No notice period is legally required for FDC expiration (Article 73).\n" +
                   "- Early termination by either party requires mutual agreement or just cause.\n" +
                   "- Unilateral early termination may result in damages equal to remaining wages.";
        }

        String noticePeriod;
        if (yearsOfService < 1) {
            noticePeriod = "7 days";
        } else if (yearsOfService < 2) {
            noticePeriod = "15 days";
        } else if (yearsOfService < 5) {
            noticePeriod = "1 month";
        } else if (yearsOfService < 10) {
            noticePeriod = "2 months";
        } else {
            noticePeriod = "3 months";
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format(
                "UDC Notice Period Calculation:\n" +
                "- Years of Service: %d\n" +
                "- Required Notice Period: %s\n" +
                "- Legal Basis: Article 75\n",
                yearsOfService, noticePeriod));

        // Calculate the latest date to give notice
        if (terminationDate != null && !"none".equalsIgnoreCase(terminationDate)) {
            try {
                LocalDate termDate = LocalDate.parse(terminationDate, DATE_FORMAT);
                LocalDate noticeDeadline = switch (noticePeriod) {
                    case "7 days" -> termDate.minusDays(7);
                    case "15 days" -> termDate.minusDays(15);
                    case "1 month" -> termDate.minusMonths(1);
                    case "2 months" -> termDate.minusMonths(2);
                    case "3 months" -> termDate.minusMonths(3);
                    default -> termDate;
                };
                result.append(String.format("- Desired Termination Date: %s\n", termDate));
                result.append(String.format("- Notice Must Be Given By: %s\n", noticeDeadline));
            } catch (DateTimeParseException e) {
                result.append("- Could not parse termination date. Use YYYY-MM-DD format.\n");
            }
        }

        return result.toString();
    }

    /**
     * Calculates the probation end date under Article 68 of Cambodia Labour Law.
     *
     * Probation periods:
     * - Regular employees: 3 months maximum
     * - Specialized/skilled workers: 2 months maximum
     * - Unskilled workers: 1 month maximum
     */
    @Tool(description = "Calculate the probation period end date under Cambodian Labour Law Article 68, " +
                         "based on the employee's start date and worker category.")
    public String calculateProbationEndDate(
            @ToolParam(description = "Employment start date in YYYY-MM-DD format")
            String startDate,
            @ToolParam(description = "Worker category: 'regular', 'specialized', or 'unskilled'")
            String workerCategory) {

        log.info(">>> Tool: calculateProbationEndDate({}, {})", startDate, workerCategory);

        int probationMonths;
        String categoryLabel;
        switch (workerCategory.toLowerCase()) {
            case "unskilled" -> {
                probationMonths = 1;
                categoryLabel = "Unskilled worker";
            }
            case "specialized", "skilled" -> {
                probationMonths = 2;
                categoryLabel = "Specialized/skilled worker";
            }
            default -> {
                probationMonths = 3;
                categoryLabel = "Regular employee";
            }
        }

        try {
            LocalDate start = LocalDate.parse(startDate, DATE_FORMAT);
            LocalDate probationEnd = start.plusMonths(probationMonths);

            return String.format(
                    "Probation Period Calculation:\n" +
                    "- Worker Category: %s\n" +
                    "- Start Date: %s\n" +
                    "- Maximum Probation Period: %d month(s)\n" +
                    "- Probation End Date: %s\n" +
                    "- Legal Basis: Article 68\n" +
                    "Note: During probation, either party may terminate the contract " +
                    "without notice or indemnity (Article 82).",
                    categoryLabel, start, probationMonths, probationEnd);
        } catch (DateTimeParseException e) {
            return "Error: Could not parse start date '" + startDate + "'. Please use YYYY-MM-DD format.";
        }
    }
}
