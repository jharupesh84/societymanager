package com.society.app.util

/**
 * On-device, offline parser for table text, WhatsApp messages, and CSVs.
 * Parses lines into ParsedCollectionRow without needing any AI or network connection.
 */
object TextTableParser {

    val SAMPLE_TABLE_TEXT = """
        B, B-504, Rupesh Jha, 2100, Online
        G, G-302, Kamlesh Agrawal, 1500, Online
        C, C-504, Nitish Jha, 1500, Online
        G, G-503, R K Dubey, 1500, Online
        A, A-501, Sanotsh Mishra, 2100, Cash
        B, B-402, Rajesh Hirwani, 1501, Online
        F, F-101, Rakhi Pandey, 1200, Online
        B, B-502, Jitendra Ji, 1200, Online
        B, B-401, Parul Biswas, 1501, Online
        E, E-502, Rakesh Kumar Pal, 1200, Online
        G, G-102, Rahul Chavada, 1200, Cash
        G, G-303, Pratap Bhai, 1200, Cash
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
                if (blockPart.isNotBlank()) blockStr = blockPart
                flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
                continue
            }

            // 4. Check if token is standalone Block column (e.g. "A", "B", "C", "Block A", "Wing G")
            val blockOnlyMatch = Regex("^(?:Block|Wing)?\\s*([A-Za-z])$", RegexOption.IGNORE_CASE).find(token)
            if (blockOnlyMatch != null) {
                if (blockStr.isBlank()) {
                    blockStr = blockOnlyMatch.groupValues[1].uppercase()
                }
                continue // Consume block token without adding to name
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
                    if (blockPart.isNotBlank()) blockStr = blockPart
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

        // Clean out any leftover single-letter block or header artifacts from name
        remainingTokens.removeAll {
            it.equals(blockStr, ignoreCase = true) ||
            it.matches(Regex("^(?:Block|Wing)?\\s*([A-Za-z])$", RegexOption.IGNORE_CASE)) ||
            it.equals("Block", ignoreCase = true) ||
            it.equals("Flat", ignoreCase = true) ||
            it.equals("Wing", ignoreCase = true)
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

        // Standardize flat string so it does not repeat block
        val cleanFlat = when {
            flatStr.startsWith("$blockStr-", ignoreCase = true) -> flatStr
            flatStr.startsWith(blockStr, ignoreCase = true) && flatStr.length > blockStr.length -> {
                "$blockStr-${flatStr.removePrefix(blockStr).removePrefix("-")}"
            }
            blockStr.isNotBlank() && blockStr != "General" && !flatStr.contains("-") -> {
                "$blockStr-$flatStr"
            }
            else -> flatStr
        }

        if (cleanFlat.isNotBlank() || amountVal > 0.0) {
            return ParsedCollectionRow(
                block = blockStr.ifBlank { "A" },
                flatNo = cleanFlat.ifBlank { "101" },
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
