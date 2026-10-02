package com.society.app.util

/**
 * On-device, offline parser for table text, WhatsApp messages, and CSVs.
 * Parses lines into ParsedCollectionRow without needing any AI or network connection.
 */
object TextTableParser {

    /**
     * Inspects the document header to detect if the entire sheet belongs to a single specific block.
     * Examples:
     * - "MAINTENANCE: 'G' BLOCK - 20" -> "G"
     * - "MAINTENANCE : 'G' BLOCK" -> "G"
     * - "'G' BLOCK" -> "G"
     * If the document already contains multiple distinct blocks (e.g. B-504, G-302, A-501), returns null.
     */
    fun detectHeaderBlock(fullText: String): String? {
        // 1. If text already has multiple distinct block prefixes (e.g. B-504, G-302, A-501),
        // then this is clearly a multi-block document! Do NOT enforce any single header block.
        val distinctPrefixes = Regex("""\b([A-Za-z])[-/][0-9]{3,4}\b""").findAll(fullText)
            .map { it.groupValues[1].uppercase() }
            .distinct()
            .toList()
        if (distinctPrefixes.size > 1) {
            return null
        }

        val headerSection = fullText.lines().take(10).joinToString("\n")

        // Pattern 1: 'G' BLOCK, "G" BLOCK, or MAINTENANCE: 'G' BLOCK, MAINTENANCE: 'G' BLOCK - 20
        val regex1 = Regex("""(?:MAINTENANCE|COLLECTION)?[:\s]*['"‘“]([A-Za-z0-9])['"’”]\s*[-/_]?\s*(?:BLOCK|WING)""", RegexOption.IGNORE_CASE)
        val match1 = regex1.find(headerSection)
        if (match1 != null) {
            val b = match1.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        // Pattern 2: Standalone quoted letter followed by BLOCK anywhere in header
        val regex2 = Regex("""['"‘“]([A-Za-z0-9])['"’”]\s*(?:BLOCK|WING)""", RegexOption.IGNORE_CASE)
        val match2 = regex2.find(headerSection)
        if (match2 != null) {
            val b = match2.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        // Pattern 3: Explicit MAINTENANCE / COLLECTION title with BLOCK / WING
        val regex3 = Regex("""(?:MAINTENANCE|COLLECTION)\s*[:\-_]?\s*(?:BLOCK|WING)?\s*[:\-_]?\s*['"‘“]?([A-Za-z0-9])['"’”]?\s*(?:BLOCK|WING)""", RegexOption.IGNORE_CASE)
        val match3 = regex3.find(headerSection)
        if (match3 != null) {
            val b = match3.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        return null
    }

    /**
     * Parses raw pasted text or OCR text into collection rows.
     * - For multi-block tables: extracts the explicit block for each row individually (e.g. A-101, B-202).
     * - For single-block sheets: if a header block (e.g. "G" from "MAINTENANCE: 'G' BLOCK") is found,
     *   flats without a block prefix (e.g. "101", "102", "302", "503") are assigned that block and formatted as "G-101", "G-102", etc.
     */
    fun parse(rawText: String, overrideHeaderBlock: String? = null): List<ParsedCollectionRow> {
        val detectedHeaderBlock = overrideHeaderBlock?.trim()?.uppercase()?.ifBlank { null }
            ?: detectHeaderBlock(rawText)

        val lines = rawText.lines()
        val result = mutableListOf<ParsedCollectionRow>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Skip title/header/footer rows
            if (isHeaderOrTitleLine(trimmed)) continue

            val row = parseLine(trimmed, detectedHeaderBlock)
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
            lower.contains("signature") || lower.contains("sign")
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

        // 1. First pass: detect payment mode anywhere in tokens (e.g. "cash", "CASH", "1200 cash", "1500 ONE LINE")
        for (token in tokens) {
            val lower = token.lowercase()
            if (lower.contains("cash") || lower.contains("cheque") || lower.contains("check")) {
                paymentMode = "Cash"
                break
            } else if (lower.contains("online") || lower.contains("one line") || lower.contains("on line") ||
                lower.contains("upi") || lower.contains("gpay") || lower.contains("neft") || lower.contains("rtgs") ||
                lower.contains("bank") || lower.contains("phonepe") || lower.contains("paytm")
            ) {
                paymentMode = "Online"
            }
        }

        // 2. Pre-clean tokens: strip payment mode keywords from tokens so that "1200 cash" -> "1200", "1500 ONE LINE" -> "1500"
        val modeWordRegex = Regex("""(?i)\b(?:cash|cheque|check|one\s*line|on\s*line|online|upi|gpay|neft|rtgs|bank|phonepe|paytm)\b""")
        val remainingTokens = mutableListOf<String>()

        for (rawToken in tokens) {
            val token = rawToken.replace(modeWordRegex, "").trim()
            if (token.isBlank()) continue

            // Skip standalone 1-2 digit serial numbers (e.g. 1, 2, ..., 20)
            if (token.matches(Regex("^[0-9]{1,2}$"))) {
                continue
            }

            // Skip date tokens (e.g. 01/10/26, 27/9/26, 28/9/26)
            if (token.matches(Regex("^[0-9]{1,2}[-/][0-9]{1,2}(?:[-/][0-9]{2,4})?$"))) {
                continue
            }

            // Skip dash-only tokens (e.g. "-", "--")
            if (token == "-" || token == "--") {
                continue
            }

            // Amount check (e.g. 2100, 1500, ₹2100, 12.00, 1200 in OCR notation)
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

            // Flat check (e.g. B-504, G-302, 101, 102, 504)
            val flatMatch = Regex("^([A-Za-z])?[-/\\s]?([0-9]{3,4})$").find(token)
            if (flatMatch != null && flatStr.isBlank()) {
                val blockPart = flatMatch.groupValues[1].uppercase()
                val numPart = flatMatch.groupValues[2]
                if (blockPart.isNotBlank()) blockStr = blockPart
                flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
                continue
            }

            // Standalone Block column (e.g. "A", "B", "G", "Block G", "Wing A")
            val blockOnlyMatch = Regex("^(?:Block|Wing)?\\s*([A-Za-z])$", RegexOption.IGNORE_CASE).find(token)
            if (blockOnlyMatch != null && blockOnlyMatch.groupValues[1].length == 1) {
                if (blockStr.isBlank()) {
                    blockStr = blockOnlyMatch.groupValues[1].uppercase()
                }
                continue // Consume block token without putting it in name
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

        // If Amount was not found, check remaining tokens for a number
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

        // Determine effective block:
        // 1. Explicit row block takes top precedence (for multi-block sheets).
        // 2. Otherwise, if document header specifies a block (e.g. "G" from "MAINTENANCE: 'G' BLOCK"), use it.
        // 3. Otherwise, if flatStr already has a block prefix (e.g. "B-504"), use it.
        // 4. Default to "General".
        val effectiveBlock = when {
            blockStr.isNotBlank() && blockStr != "General" -> blockStr
            !defaultBlock.isNullOrBlank() -> defaultBlock.uppercase()
            flatStr.contains("-") -> flatStr.substringBefore("-").trim().uppercase()
            else -> "General"
        }

        // Standardize flat number with the block (e.g. G-101, G-102, G-302, B-504)
        val cleanFlat = when {
            effectiveBlock != "General" && flatStr.startsWith("$effectiveBlock-", ignoreCase = true) -> flatStr
            effectiveBlock != "General" && flatStr.startsWith(effectiveBlock, ignoreCase = true) && flatStr.length > effectiveBlock.length -> {
                "$effectiveBlock-${flatStr.removePrefix(effectiveBlock).removePrefix("-")}"
            }
            effectiveBlock != "General" && !flatStr.contains("-") && flatStr.all { it.isDigit() } -> {
                "$effectiveBlock-$flatStr"
            }
            else -> flatStr
        }

        // A valid collection row MUST have a flat number with digits AND a positive amount!
        val hasDigitsInFlat = cleanFlat.any { it.isDigit() }
        if (hasDigitsInFlat && amountVal > 0.0) {
            return ParsedCollectionRow(
                block = effectiveBlock,
                flatNo = cleanFlat,
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
