package com.society.app.util

/**
 * On-device, offline parser for table text, WhatsApp messages, and CSVs.
 * Parses lines into ParsedCollectionRow without needing any AI or network connection.
 */
object TextTableParser {

    val SAMPLE_TABLE_TEXT = """
        B-504, Rupesh Jha, 2100, Online
        G-302, Kamlesh Agrawal, 1500, Cash
        C-504, Santosh Mishra, 2100, Online
        B-201, Amit Sharma, 2100, Online
        A-102, Rajesh Verma, 1800, Cash
        D-403, Priya Nair, 2100, Online
        E-301, Vikram Patel, 2500, Online
        F-204, Sunita Rao, 1500, Cash
        A-501, Suresh Gupta, 2100, Online
        C-103, Deepak Joshi, 2100, Online
        G-104, Manoj Tiwari, 1500, Cash
        B-302, Neha Kulkarni, 2100, Online
    """.trimIndent()

    /**
     * Parses raw pasted text (from WhatsApp, CSV, Excel, or ledger) into collection rows.
     */
    fun parse(rawText: String): List<ParsedCollectionRow> {
        val lines = rawText.lines()
        val result = mutableListOf<ParsedCollectionRow>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Skip common header lines
            if (isHeaderLine(trimmed)) continue

            val row = parseLine(trimmed)
            if (row != null) {
                result.add(row)
            }
        }

        return result
    }

    private fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        val hasHeaderKeywords = (lower.contains("block") || lower.contains("flat") || lower.contains("unit")) &&
                (lower.contains("name") || lower.contains("owner") || lower.contains("resident") || lower.contains("amount"))
        // If it also has numbers like 504 and 2100, it's probably data, not just a header
        val hasDigits = line.any { it.isDigit() }
        return hasHeaderKeywords && !hasDigits
    }

    private fun parseLine(line: String): ParsedCollectionRow? {
        // Delimiters could be commas, tabs, pipes, or semicolons
        val delimiter = when {
            line.contains(",") -> ","
            line.contains("\t") -> "\t"
            line.contains("|") -> "|"
            line.contains(";") -> ";"
            else -> null
        }

        if (delimiter != null) {
            val tokens = line.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }
            if (tokens.size >= 2) {
                return parseTokens(tokens)
            }
        }

        // Space-separated or unstructured text fallback
        return parseUnstructuredLine(line)
    }

    private fun parseTokens(tokens: List<String>): ParsedCollectionRow? {
        var flatStr = ""
        var blockStr = ""
        var nameStr = ""
        var amountVal = 0.0
        var paymentMode = "Online"

        // Search tokens for Flat, Amount, Payment Mode, and Name
        val remainingTokens = mutableListOf<String>()

        for (token in tokens) {
            val lower = token.lowercase()

            // 1. Check if token is payment mode
            if (lower in listOf("cash", "cheque", "check")) {
                paymentMode = "Cash"
                continue
            } else if (lower in listOf("online", "upi", "gpay", "neft", "rtgs", "bank", "phonepe", "paytm")) {
                paymentMode = "Online"
                continue
            }

            // 2. Check if token is Amount (e.g. 2100, 2,100, 2100.00, ₹2100, Rs. 2100)
            val cleanAmt = token.replace("₹", "").replace("Rs", "", ignoreCase = true)
                .replace(".", "").replace(",", "").trim()
            val parsedAmt = token.replace("₹", "").replace("Rs", "", ignoreCase = true)
                .replace(",", "").trim().toDoubleOrNull()

            if (parsedAmt != null && parsedAmt > 0 && amountVal == 0.0 && cleanAmt.all { it.isDigit() }) {
                amountVal = parsedAmt
                continue
            }

            // 3. Check if token is Flat (e.g. B-504, B504, 504, A 101, C-203)
            val flatMatch = Regex("^([A-Za-z])?[-/\\s]?([0-9]{3,4})$").find(token)
            if (flatMatch != null && flatStr.isBlank()) {
                val blockPart = flatMatch.groupValues[1].uppercase()
                val numPart = flatMatch.groupValues[2]
                blockStr = blockPart
                flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
                continue
            }

            remainingTokens.add(token)
        }

        // If Flat was not isolated, check remaining tokens
        if (flatStr.isBlank()) {
            for (i in remainingTokens.indices) {
                val t = remainingTokens[i]
                val match = Regex("([A-Za-z])?[-/\\s]?([0-9]{3,4})").find(t)
                if (match != null) {
                    val blockPart = match.groupValues[1].uppercase()
                    val numPart = match.groupValues[2]
                    blockStr = blockPart
                    flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
                    remainingTokens.removeAt(i)
                    break
                }
            }
        }

        // If Amount was not isolated, search in remaining tokens
        if (amountVal == 0.0) {
            for (i in remainingTokens.indices) {
                val t = remainingTokens[i]
                val match = Regex("([0-9]+(?:\\.[0-9]{1,2})?)").find(t.replace(",", ""))
                val num = match?.groupValues?.get(1)?.toDoubleOrNull()
                if (num != null && num > 0) {
                    amountVal = num
                    remainingTokens.removeAt(i)
                    break
                }
            }
        }

        // Remaining tokens compose the owner/resident name
        nameStr = remainingTokens.joinToString(" ")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "") // remove numbering like "1."
            .trim()

        if (nameStr.isBlank()) {
            nameStr = "Resident"
        }

        if (blockStr.isBlank()) {
            blockStr = if (flatStr.contains("-")) flatStr.substringBefore("-").trim() else "A"
        }

        if (flatStr.isNotBlank() || amountVal > 0.0) {
            return ParsedCollectionRow(
                block = blockStr.ifBlank { "A" },
                flatNo = flatStr.ifBlank { "101" },
                ownerName = nameStr,
                amount = amountVal,
                paymentMode = paymentMode
            )
        }

        return null
    }

    private fun parseUnstructuredLine(line: String): ParsedCollectionRow? {
        val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return parseTokens(tokens)
    }
}
