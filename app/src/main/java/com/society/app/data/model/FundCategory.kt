package com.society.app.data.model

data class FundCategory(
    val id: String,
    val displayName: String,
    val description: String,
    val isMonthly: Boolean = false,
    val iconType: String = "apartment"
) {
    companion object {
        const val CATEGORY_MAINTENANCE = "Monthly Maintenance"
        const val CATEGORY_NAVRATRI = "Navratri Collection"
        const val CATEGORY_GANPATI = "Ganpati Festival"

        val DEFAULT_CATEGORIES = listOf(
            FundCategory(
                id = CATEGORY_MAINTENANCE,
                displayName = "Monthly Maintenance",
                description = "Regular monthly flat maintenance & society operations",
                isMonthly = true,
                iconType = "apartment"
            ),
            FundCategory(
                id = CATEGORY_NAVRATRI,
                displayName = "Navratri Collection",
                description = "Garba, pooja, sound system & festival celebration",
                isMonthly = false,
                iconType = "navratri"
            )
        )
    }
}
