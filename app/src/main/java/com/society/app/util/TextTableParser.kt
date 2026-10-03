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

        // Pattern 1: BLOCK/WING followed by letter (e.g. "BLOCK: B", "BLOCK - B", "BLOCK B", "BLOCK 'C'", "WING: A", "WING C")
        val regexBlockFirst = Regex("""(?:MAINTENANCE|COLLECTION)?[:\s]*(?:BLOCK|WING)\s*[:\-_/]?\s*['"‘“]?([A-Za-z0-9])['"’”]?\b""", RegexOption.IGNORE_CASE)
        val matchBlockFirst = regexBlockFirst.find(headerSection)
        if (matchBlockFirst != null) {
            val b = matchBlockFirst.groupValues[1].uppercase()
            if (b.isNotBlank()) return b
        }

        // Pattern 2: Quoted or unquoted letter followed by BLOCK/WING (e.g. "'B' BLOCK", "'G' BLOCK", "B BLOCK", "C BLOCK", "A WING")
        val regexLetterFirst = Regex("""(?:MAINTENANCE|COLLECTION)?[:\s]*['"‘“]?([A-Za-z0-9])['"’”]?\s*[-/_]?\s*(?:BLOCK|WING)\b""", RegexOption.IGNORE_CASE)
        val matchLetterFirst = regexLetterFirst.find(headerSection)
        if (matchLetterFirst != null) {
            val b = matchLetterFirst.groupValues[1].uppercase()
            if (b.isNotBlank() && b !in listOf("NO", "OF", "THE")) return b
        }

        // Pattern 3: Standalone quoted letter in the header line (e.g. "'B'", "'C'", "'G'")
        val regexQuoted = Regex("""['"‘“]([A-Za-z0-9])['"’”]""", RegexOption.IGNORE_CASE)
        val matchQuoted = regexQuoted.find(headerSection)
        if (matchQuoted != null) {
            val b = matchQuoted.groupValues[1].uppercase()
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
        val lower = line.lowercase().trim()
        val hasDigits = line.any { it.isDigit() }

        // If line has no digits at all, it cannot be a collection row (rows need flat & amount)
        if (!hasDigits) return true

        // Check if it's explicitly the top society title or document header line
        if ((lower.contains("maintenance:") || lower.contains("collection:")) &&
            (lower.contains("block") || lower.contains("wing") || lower.contains("register"))
        ) {
            return true
        }
        if (lower.contains("society reg") || lower.contains("pin code") || lower.contains("ahmedabad - 38")) {
            return true
        }

        // Check if it's a summary/total line at the bottom
        if (lower.startsWith("total") || lower.contains("grand total") ||
            lower.contains("total (rupees") || lower.contains("total rupees") ||
            lower.contains("total amount")
        ) {
            return true
        }

        // Table column header line (e.g. "Sr No Flat No Name Amount Signature")
        val hasColKeywords = (lower.contains("block") || lower.contains("flat") || lower.contains("unit")) &&
                (lower.contains("name") || lower.contains("owner") || lower.contains("resident")) &&
                (lower.contains("amount") || lower.contains("mode") || lower.contains("sign"))
        if (hasColKeywords && !lower.contains("1200") && !lower.contains("1500") && !lower.contains("2000") && !lower.contains("2100")) {
            val numCount = Regex("""\b\d+\b""").findAll(line).count()
            if (numCount <= 1) return true
        }

        return false
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

    fun extractAmountFromToken(token: String): Double? {
        val clean = token.replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("/-", "")
            .replace("/=", "")
            .replace("/", "")
            .replace("=", "")
            .replace("|", "")
            .replace(",", "")
            .trim()

        val match = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""").find(clean) ?: return null
        val numStr = match.groupValues[1]
        var amt = numStr.toDoubleOrNull() ?: return null

        // If OCR recognized 1200 as 12.00 or 1500 as 15.00
        if (amt in 10.0..99.0 && clean.contains(".")) {
            amt *= 100.0
        }
        return if (amt > 0.0) amt else null
    }

    fun extractFlatFromToken(token: String): Pair<String, String>? {
        val clean = token.trim().trim('.', ',', '|', ':', ';', '#', '(', ')')
        val match = Regex("""^([A-Za-z])?[-/\s]?([0-9]{1,4})$""").find(clean) ?: return null
        val blockPart = match.groupValues[1].uppercase()
        val numPart = match.groupValues[2]
        val flatStr = if (blockPart.isNotBlank()) "$blockPart-$numPart" else numPart
        return Pair(blockPart, flatStr)
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

            // Amount check (e.g. 1200, 1500, ₹2100, 1200/-, Rs. 1500/-)
            val parsedAmt = extractAmountFromToken(token)
            if (parsedAmt != null && parsedAmt >= 100.0 && amountVal == 0.0) {
                // If it's a 3-4 digit number, make sure we don't accidentally treat flat number as amount
                // Flat numbers are usually smaller or checked separately, but if amountVal is not set, set it
                amountVal = parsedAmt
                continue
            }

            // Flat check (e.g. B-504, G-302, 101, 102, 504)
            val flatPair = extractFlatFromToken(token)
            if (flatPair != null && flatStr.isBlank()) {
                val blockPart = flatPair.first
                val flatPart = flatPair.second
                if (blockPart.isNotBlank()) blockStr = blockPart
                flatStr = flatPart
                continue
            }

            // Standalone Block column (e.g. "A", "B", "G", "Block G", "Wing A")
            val blockOnlyMatch = Regex("""^(?:Block|Wing)?\s*([A-Za-z])$""", RegexOption.IGNORE_CASE).find(token)
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
                val flatPair = extractFlatFromToken(t)
                if (flatPair != null && flatPair.second.any { it.isDigit() }) {
                    val blockPart = flatPair.first
                    val flatPart = flatPair.second
                    if (blockPart.isNotBlank()) blockStr = blockPart
                    flatStr = flatPart
                    remainingTokens.removeAt(i)
                    break
                }
            }
        }

        // If Amount was not found, check remaining tokens for an amount
        if (amountVal == 0.0) {
            for (i in remainingTokens.indices) {
                val t = remainingTokens[i]
                val parsedAmt = extractAmountFromToken(t)
                if (parsedAmt != null && parsedAmt > 0) {
                    amountVal = parsedAmt
                    remainingTokens.removeAt(i)
                    break
                }
            }
        }

        // Clean out stray keywords, boilerplate tokens, serial numbers, and block letters from name
        val boilerplateRegex = Regex("""(?i)\b(?:sr\s*no|ser\s*no|date\s*of\s*deposit|period\s*from|bal\s*due|late\s*fee|rupees\s*only|signature|signed|sign|date|deposit|period)\b""")
        remainingTokens.removeAll { t ->
            val cleanT = t.trim().trim('.', ',', '|', ':', ';', '-')
            cleanT.matches(Regex("^[0-9]{1,2}$")) ||
            cleanT.equals(blockStr, ignoreCase = true) ||
            (defaultBlock != null && cleanT.equals(defaultBlock, ignoreCase = true)) ||
            cleanT.matches(Regex("""^(?:Block|Wing)?\s*([A-Za-z])$""", RegexOption.IGNORE_CASE)) ||
            cleanT.equals("Block", ignoreCase = true) ||
            cleanT.equals("Flat", ignoreCase = true) ||
            cleanT.equals("Wing", ignoreCase = true) ||
            cleanT.equals("Sign", ignoreCase = true) ||
            cleanT.equals("Signature", ignoreCase = true) ||
            cleanT.equals("Signed", ignoreCase = true) ||
            cleanT.equals("-", ignoreCase = true) ||
            cleanT.equals("--", ignoreCase = true) ||
            boilerplateRegex.matches(cleanT)
        }

        // Remaining tokens compose the owner/resident name
        nameStr = remainingTokens.joinToString(" ")
            .replace(boilerplateRegex, "")
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
