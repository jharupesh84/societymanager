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
     * Inspects the document header (first 12 lines) to detect if the entire sheet belongs to a specific block.
     * Examples:
     * - "MAINTENANCE: 'G' BLOCK - 20" -> "G"
     * - "MAINTENANCE : 'G' BLOCK" -> "G"
     * - "'G' BLOCK" -> "G"
     * - "BLOCK: B" or "BLOCK - B" -> "B"
     * - "BLOCK G" or "WING A" -> "G" or "A"
     */
    fun detectHeaderBlock(fullText: String): String? {
        val headerSection = fullText.lines().take(12).joinToString("\n")

        // Pattern 1: 'G' BLOCK, "G" BLOCK, or MAINTENANCE: 'G' BLOCK
        val regex1 = Regex("""(?:MAINTENANCE|COLLECTION)?[:\s]*['"‘“]?([A-Za-z0-9])['"’”]?\s*[-/_]?\s*(?:BLOCK|WING)""", RegexOption.IGNORE_CASE)
        val match1 = regex1.find(headerSection)
        if (match1 != null) {
            val b = match1.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        // Pattern 2: BLOCK: G, BLOCK - G, BLOCK 'G', BLOCK "G", BLOCK G, WING: A
        val regex2 = Regex("""(?:BLOCK|WING)\s*[:\-_]?\s*['"‘“]?([A-Za-z0-9])['"’”]?\b""", RegexOption.IGNORE_CASE)
        val match2 = regex2.find(headerSection)
        if (match2 != null) {
            val b = match2.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        // Pattern 3: Standalone quoted letter followed by BLOCK anywhere in header
        val regex3 = Regex("""['"‘“]([A-Za-z0-9])['"’”]\s*(?:BLOCK|WING)""", RegexOption.IGNORE_CASE)
        val match3 = regex3.find(headerSection)
        if (match3 != null) {
            val b = match3.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        return null
    }

    /**
     * Parses raw pasted text or OCR text into collection rows.
     * If forcedBlock or a header block (e.g. 'G') is found, flats without a block prefix
     * like "101", "102", "302" are assigned that block and formatted as "G-101", "G-102", etc.
     */
    fun parse(rawText: String, forcedBlock: String? = null): List<ParsedCollectionRow> {
        val detectedBlock = forcedBlock?.ifBlank { null } ?: detectHeaderBlock(rawText)
        val lines = rawText.lines()
        val result = mutableListOf<ParsedCollectionRow>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Skip title/header/footer rows
            if (isHeaderOrTitleLine(trimmed)) continue

            val row = parseLine(trimmed, detectedBlock)
            if (row != null) {
                result.add(row)
            }
        }

        return result
    }

    private fun isHeaderOrTitleLine(line: String): Boolean {
        val lower = line.lowercase()
        // Skip document title, society name, address, or table column header lines
        if (lower.contains("maintenance:") || lower.contains("collection:") ||
            lower.contains("enclave") || lower.contains("society") ||
            lower.contains("ahmedabad") || lower.contains("gujarat") ||
            lower.contains("kuber nagar") || lower.contains("bangla area") ||
            lower.contains("saijpur") || lower.contains("ser no") ||
            lower.contains("sr no") || lower.contains("date of deposit") ||
            lower.contains("period from") || lower.contains("bal due") ||
            lower.contains("late fee") || lower.contains("rupees only") ||
            lower.contains("total (rupees") || lower.contains("navratri ke") ||
            lower.contains("cash/ on line") || lower.contains("cash/on line")
        ) {
            return true
        }

        val hasHeaderKeywords = (lower.contains("block") || lower.contains("flat") || lower.contains("unit")) &&
                (lower.contains("name") || lower.contains("owner") || lower.contains("resident") || lower.contains("amount"))
        val hasDigits = line.any { it.isDigit() }
        return hasHeaderKeywords && !hasDigits
    }

    private fun parseLine(line: String, defaultBlock: String?): ParsedCollectionRow? {
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
                return parseTokens(tokens, defaultBlock)
            }
        }

        return parseUnstructuredLine(line, defaultBlock)
    }

    private fun parseTokens(tokens: List<String>, defaultBlock: String?): ParsedCollectionRow? {
        var flatStr = ""
        var blockStr = ""
        var nameStr = ""
        var amountVal = 0.0
        var paymentMode = "Online"

        val remainingTokens = mutableListOf<String>()

        for (token in tokens) {
            val lower = token.lowercase()

            // 1. Payment mode check
            if (lower in listOf("cash", "cheque", "check")) {
                paymentMode = "Cash"
                continue
            } else if (lower in listOf("online", "upi", "gpay", "neft", "rtgs", "bank", "phonepe", "paytm", "one line", "on line")) {
                paymentMode = "Online"
                continue
            }

            // 2. Amount check (e.g. 1200, 1500, ₹2100, 12.00 in OCR notation)
            val cleanAmt = token.replace("₹", "").replace("Rs", "", ignoreCase = true)
                .replace(".", "").replace(",", "").trim()
            val parsedAmt = token.replace("₹", "").replace("Rs", "", ignoreCase = true)
                .replace(",", "").trim().toDoubleOrNull()

            // Handle OCR recognizing 1200 as 12.00 or 1500 as 15.00
            val normalizedAmt = if (parsedAmt != null && parsedAmt in 10.0..99.0 && token.contains(".")) {
                parsedAmt * 100.0 // e.g. 12.00 -> 1200.0
            } else {
                parsedAmt
            }

            if (normalizedAmt != null && normalizedAmt > 0 && amountVal == 0.0 && cleanAmt.all { it.isDigit() }) {
                amountVal = normalizedAmt
                continue
            }

            // 3. Flat check (e.g. B-504, G-302, 101, 102, 504)
            val flatMatch = Regex("^([A-Za-z])?[-/\\s]?([0-9]{3,4})$").find(token)
            if (flatMatch != null && flatStr.isBlank()) {
                val blockPart = flatMatch.groupValues[1].uppercase()
                val numPart = flatMatch.groupValues[2]
                if (blockPart.isNotBlank()) blockStr = blockPart
                flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
                continue
            }

            // 4. Standalone Block column (e.g. "A", "B", "G", "Block G", "Wing A")
            val blockOnlyMatch = Regex("^(?:Block|Wing)?\\s*([A-Za-z])$", RegexOption.IGNORE_CASE).find(token)
            if (blockOnlyMatch != null) {
                if (blockStr.isBlank()) {
                    blockStr = blockOnlyMatch.groupValues[1].uppercase()
                }
                continue // Consume block token without putting it in name
            }

            // 5. Skip date tokens (e.g. 01/10/26, 27/9/26, 28/9/26)
            if (token.matches(Regex("^[0-9]{1,2}[-/][0-9]{1,2}(?:[-/][0-9]{2,4})?$"))) {
                continue
            }

            // 6. Skip standalone serial numbers (e.g. 1, 2, 3, ..., 20)
            if (token.matches(Regex("^[0-9]{1,2}$")) && flatStr.isNotBlank()) {
                continue
            }

            remainingTokens.add(token)
        }

        // If Flat was not found yet, check remaining tokens for a 3-4 digit number
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

        // If Amount was not found, check remaining tokens
        if (amountVal == 0.0) {
            for (i in remainingTokens.indices) {
                val t = remainingTokens[i]
                val match = Regex("([0-9]+(?:\\.[0-9]{1,2})?)").find(t.replace(",", ""))
                var num = match?.groupValues?.get(1)?.toDoubleOrNull()
                if (num != null && num in 10.0..99.0 && t.contains(".")) {
                    num *= 100.0
                }
                if (num != null && num > 0) {
                    amountVal = num
                    remainingTokens.removeAt(i)
                    break
                }
            }
        }

        // Clean out stray serial numbers (e.g. "1", "2") and stray block letters ("A", "G") from name
        remainingTokens.removeAll {
            it.matches(Regex("^[0-9]{1,2}$")) ||
            it.equals(blockStr, ignoreCase = true) ||
            (defaultBlock != null && it.equals(defaultBlock, ignoreCase = true)) ||
            it.matches(Regex("^(?:Block|Wing)?\\s*([A-Za-z])$", RegexOption.IGNORE_CASE)) ||
            it.equals("Block", ignoreCase = true) ||
            it.equals("Flat", ignoreCase = true) ||
            it.equals("Wing", ignoreCase = true) ||
            it.equals("-", ignoreCase = true) ||
            it.equals("--", ignoreCase = true)
        }

        // Remaining tokens compose the owner/resident name
        nameStr = remainingTokens.joinToString(" ")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "")
            .trim()

        if (nameStr.isBlank()) {
            nameStr = "Resident"
        }

        // Determine effective block: row's explicit block > document header / forced block > default "A"
        val effectiveBlock = when {
            blockStr.isNotBlank() && blockStr != "General" -> blockStr
            !defaultBlock.isNullOrBlank() -> defaultBlock.uppercase()
            flatStr.contains("-") -> flatStr.substringBefore("-").trim().uppercase()
            else -> "A"
        }

        // Standardize flat number with the block (e.g. G-101, G-102, G-302)
        val cleanFlat = when {
            flatStr.startsWith("$effectiveBlock-", ignoreCase = true) -> flatStr
            flatStr.startsWith(effectiveBlock, ignoreCase = true) && flatStr.length > effectiveBlock.length -> {
                "$effectiveBlock-${flatStr.removePrefix(effectiveBlock).removePrefix("-")}"
            }
            effectiveBlock.isNotBlank() && effectiveBlock != "General" && !flatStr.contains("-") -> {
                "$effectiveBlock-$flatStr"
            }
            else -> flatStr
        }

        if (cleanFlat.isNotBlank() || amountVal > 0.0) {
            return ParsedCollectionRow(
                block = effectiveBlock,
                flatNo = cleanFlat.ifBlank { "$effectiveBlock-101" },
                ownerName = nameStr,
                amount = amountVal,
                paymentMode = paymentMode
            )
        }

        return null
    }

    private fun parseUnstructuredLine(line: String, defaultBlock: String?): ParsedCollectionRow? {
        val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return parseTokens(tokens, defaultBlock)
    }
}
