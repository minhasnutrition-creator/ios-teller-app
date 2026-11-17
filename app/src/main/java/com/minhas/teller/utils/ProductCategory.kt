package com.minhas.teller.utils

object ProductCategory {
    data class Category(val name: String, val displayName: String, val gstRate: Double)
    data class PredefinedUnit(val symbol: String, val fullName: String)

    val ALL_CATEGORIES: List<Category> = listOf(
        Category("FRESH_FOOD", "Fresh Food (Fruits, Vegetables)", 0.0),
        Category("GRAINS", "Grains & Cereals (Rice, Wheat, Dal)", 0.0),
        Category("MILK_PRODUCTS", "Milk & Dairy (Fresh)", 0.0),
        Category("BOOKS", "Books & Educational Material", 0.0),
        Category("HANDLOOM", "Handloom & Khadi Products", 0.0),
        Category("PACKAGED_FOOD", "Packaged Food Items", 0.05),
        Category("SUGAR_TEA_COFFEE", "Sugar, Tea, Coffee & Spices", 0.05),
        Category("FABRIC", "Fabric & Textiles", 0.05),
        Category("FOOTWEAR_BUDGET", "Footwear (Under ₹500)", 0.05),
        Category("TRANSPORT", "Transport & Logistics", 0.05),
        Category("FERTILIZER", "Fertilizers & Organic Manure", 0.05),
        Category("MEDICINES", "Medicines & Pharma", 0.05),
        Category("COAL", "Coal & Lignite", 0.05),
        Category("PROCESSED_FOOD", "Processed / Frozen Food", 0.12),
        Category("MOBILE_PHONE", "Mobile Phones", 0.12),
        Category("SEWING_MACHINE", "Sewing Machines", 0.12),
        Category("UMBRELLA", "Umbrellas & Accessories", 0.12),
        Category("NUTS_DRY_FRUITS", "Dry Fruits & Nuts", 0.12),
        Category("BUTTER_GHEE_CHEESE", "Butter, Ghee & Cheese", 0.12),
        Category("FOOTWEAR_MID", "Footwear (₹500-₹1000)", 0.12),
        Category("ELECTRONICS", "Electronics & Appliances", 0.18),
        Category("COMPUTERS_LAPTOPS", "Computers & Laptops", 0.18),
        Category("STEEL_IRON", "Steel, Iron & Metal Products", 0.18),
        Category("CEMENT", "Cement & Building Materials", 0.18),
        Category("FURNITURE", "Furniture & Fittings", 0.18),
        Category("SERVICES_IT", "IT & Software Services", 0.18),
        Category("SERVICES_RESTAURANT", "Restaurant & Catering", 0.18),
        Category("SERVICES_PROFESSIONAL", "Professional Services (Consulting)", 0.18),
        Category("FOOTWEAR_PREMIUM", "Footwear (Above ₹1000)", 0.18),
        Category("COSMETICS", "Cosmetics & Beauty Products", 0.18),
        Category("VEHICLES", "Vehicles & Automobiles", 0.28),
        Category("TOBACCO", "Tobacco & Cigarettes", 0.28),
        Category("AERATED_DRINKS", "Aerated Drinks & Soda", 0.28),
        Category("LUXURY_GOODS", "Luxury Goods & Jewellery", 0.28),
        Category("OTHERS", "Others / General", 0.18)
    )

    val ALL_UNITS: List<PredefinedUnit> = listOf(
        // Count / General
        PredefinedUnit("Pcs",      "Pieces"),
        PredefinedUnit("Nos",      "Numbers"),
        PredefinedUnit("Unit",     "Unit"),
        PredefinedUnit("Dozen",    "Dozen (12)"),
        PredefinedUnit("Gross",    "Gross (144)"),
        PredefinedUnit("Pair",     "Pair"),
        PredefinedUnit("Set",      "Set"),
        PredefinedUnit("Pack",     "Pack"),
        PredefinedUnit("Strip",    "Strip"),
        // Weight
        PredefinedUnit("Kg",       "Kilogram"),
        PredefinedUnit("Gram",     "Gram"),
        PredefinedUnit("Mg",       "Milligram"),
        PredefinedUnit("Quintal",  "Quintal (100 Kg)"),
        PredefinedUnit("Ton",      "Metric Tonne (1000 Kg)"),
        PredefinedUnit("Lb",       "Pound"),
        PredefinedUnit("Oz",       "Ounce"),
        // Volume / Liquid
        PredefinedUnit("Liter",    "Liter"),
        PredefinedUnit("mL",       "Milliliter"),
        PredefinedUnit("KL",       "Kiloliter"),
        PredefinedUnit("Gallon",   "Gallon"),
        PredefinedUnit("Cu.M",     "Cubic Meter"),
        PredefinedUnit("Cu.Ft",    "Cubic Feet"),
        // Length
        PredefinedUnit("Meter",    "Meter"),
        PredefinedUnit("cm",       "Centimeter"),
        PredefinedUnit("mm",       "Millimeter"),
        PredefinedUnit("Km",       "Kilometer"),
        PredefinedUnit("Feet",     "Feet"),
        PredefinedUnit("Inch",     "Inch"),
        PredefinedUnit("Yard",     "Yard"),
        // Area
        PredefinedUnit("Sq.Ft",   "Square Feet"),
        PredefinedUnit("Sq.M",    "Square Meter"),
        PredefinedUnit("Sq.Yd",   "Square Yard"),
        PredefinedUnit("Sq.Inch", "Square Inch"),
        PredefinedUnit("Acre",    "Acre"),
        PredefinedUnit("Hectare", "Hectare"),
        // Packaging
        PredefinedUnit("Box",      "Box"),
        PredefinedUnit("Carton",   "Carton"),
        PredefinedUnit("Bag",      "Bag"),
        PredefinedUnit("Bundle",   "Bundle"),
        PredefinedUnit("Roll",     "Roll"),
        PredefinedUnit("Spool",    "Spool"),
        PredefinedUnit("Drum",     "Drum"),
        PredefinedUnit("Can",      "Can / Tin"),
        PredefinedUnit("Bottle",   "Bottle"),
        PredefinedUnit("Sachet",   "Sachet"),
        PredefinedUnit("Pouch",    "Pouch"),
        PredefinedUnit("Pallet",   "Pallet"),
        PredefinedUnit("Crate",    "Crate"),
        // Services / Time
        PredefinedUnit("Hr",       "Hour"),
        PredefinedUnit("Day",      "Day"),
        PredefinedUnit("Month",    "Month"),
        PredefinedUnit("Job",      "Job / Work Order"),
        PredefinedUnit("Trip",     "Trip"),
        // Others
        PredefinedUnit("Others",   "Others (Enter Manually)")
    )

    /** Alias map so the finance AI can resolve spoken unit names → symbol */
    val UNIT_ALIASES: Map<String, String> = mapOf(
        "piece" to "Pcs", "pieces" to "Pcs", "pcs" to "Pcs", "pc" to "Pcs", "peice" to "Pcs", "peices" to "Pcs", "nag" to "Pcs",
        "number" to "Nos", "numbers" to "Nos", "nos" to "Nos",
        "unit" to "Units", "units" to "Units",
        "kilogram" to "Kg", "kilograms" to "Kg", "kg" to "Kg", "kgs" to "Kg",
        "kilo" to "Kg", "killos" to "Kg", "killo" to "Kg", "kilos" to "Kg", "kila" to "Kg", "kile" to "Kg", "kilogm" to "Kg",
        "gram" to "Gram", "grams" to "Gram", "g" to "Gram", "gm" to "Gram", "gms" to "Gram",
        "tonne" to "Ton", "ton" to "Ton", "tonnes" to "Ton", "tons" to "Ton", "mt" to "Ton",
        "quintal" to "Quintal", "quintals" to "Quintal", "qtl" to "Quintal",
        "liter" to "Liter", "litre" to "Liter", "liters" to "Liter", "litres" to "Liter", "l" to "Liter", "ltr" to "Liter",
        "ltrs" to "Liter", "leeter" to "Liter", "litar" to "Liter", "lit" to "Liter",
        "milliliter" to "mL", "millilitre" to "mL", "ml" to "mL",
        "kiloliter" to "KL", "kl" to "KL",
        "gallon" to "Gallon", "gallons" to "Gallon",
        "meter" to "Meter", "meters" to "Meter", "metre" to "Meter", "metres" to "Meter", "m" to "Meter", "mtr" to "Meter", "mtrs" to "Meter",
        "centimeter" to "cm", "centimetre" to "cm", "cms" to "cm",
        "kilometer" to "Km", "kilometres" to "Km", "km" to "Km",
        "feet" to "Feet", "foot" to "Feet", "ft" to "Feet",
        "inch" to "Inch", "inches" to "Inch", "in" to "Inch",
        "yard" to "Yard", "yards" to "Yard", "yd" to "Yard",
        "pound" to "Lb", "pounds" to "Lb", "lb" to "Lb", "lbs" to "Lb",
        "ounce" to "Oz", "ounces" to "Oz", "oz" to "Oz",
        "box" to "Box", "boxes" to "Box", "peti" to "Box",
        "carton" to "Carton", "cartons" to "Carton",
        "bag" to "Bag", "bags" to "Bag", "bori" to "Bag", "boriya" to "Bag", "boriyan" to "Bag",
        "packet" to "Pack", "packets" to "Pack", "pkt" to "Pack", "pkts" to "Pack", "pack" to "Pack", "packs" to "Pack",
        "bundle" to "Bundle", "bundles" to "Bundle",
        "roll" to "Roll", "rolls" to "Roll",
        "dozen" to "Dozen", "dozens" to "Dozen", "doz" to "Dozen", "darjan" to "Dozen", "dzn" to "Dozen",
        "pair" to "Pair", "pairs" to "Pair", "jodi" to "Pair", "jodee" to "Pair", "जोड़ी" to "Pair",
        "set" to "Set", "sets" to "Set",
        "bottle" to "Bottle", "bottles" to "Bottle", "btl" to "Bottle", "btls" to "Bottle",
        "hour" to "Hr", "hours" to "Hr", "hrs" to "Hr",
        "day" to "Day", "days" to "Day",
        "month" to "Month", "months" to "Month"
    )

    /** Resolve a spoken/typed unit name to its canonical symbol */
    fun resolveUnit(input: String): String {
        val lower = input.trim().lowercase()
        return UNIT_ALIASES[lower] ?: ALL_UNITS.firstOrNull {
            it.symbol.equals(input.trim(), ignoreCase = true)
        }?.symbol ?: input.trim()
    }

    /** Intuitive default unit for common goods when no unit is explicitly spoken or entered */
    fun defaultUnitForItem(itemName: String): String {
        val l = itemName.lowercase()
        return when {
            l in setOf("gold", "silver", "platinum", "sona", "chandi", "gold biscuit", "gold bar", "silver bar") -> "Gram"
            l.contains("milk") || l.contains("doodh") || l.contains("dudh") ||
            l.contains("oil") || l.contains("tel") || l.contains("petrol") || l.contains("diesel") ||
            l.contains("juice") || l.contains("water") || l.contains("paani") || l.contains("pani") -> "Liter"
            l.contains("sugar") || l.contains("cheeni") || l.contains("rice") || l.contains("chawal") ||
            l.contains("wheat") || l.contains("atta") || l.contains("aata") || l.contains("flour") || l.contains("dal") ||
            l.contains("potato") || l.contains("aaloo") || l.contains("aalu") || l.contains("onion") ||
            l.contains("pyaaz") || l.contains("pyaz") || l.contains("tomato") || l.contains("tamatar") ||
            l.contains("ghee") || l.contains("salt") || l.contains("namak") -> "Kg"
            l.contains("cloth") || l.contains("kapda") || l.contains("wire") || l.contains("pipe") || l.contains("cable") -> "Meter"
            l.contains("banana") || l.contains("kela") || l.contains("egg") || l.contains("anda") || l.contains("ande") -> "Dozen"
            else -> "Pcs"
        }
    }

    fun getGstRate(categoryName: String?): Double {
        if (categoryName.isNullOrBlank()) return 0.18
        return ALL_CATEGORIES.firstOrNull { it.name == categoryName }?.gstRate ?: 0.18
    }

    fun getDisplayName(categoryName: String?): String {
        if (categoryName.isNullOrBlank()) return "Not Set"
        return ALL_CATEGORIES.firstOrNull { it.name == categoryName }?.displayName ?: categoryName
    }

    fun getGstLabel(categoryName: String?): String {
        val rate = getGstRate(categoryName)
        return "${(rate * 100).toInt()}% GST"
    }
}
