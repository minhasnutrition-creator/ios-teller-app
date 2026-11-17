package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.HindiMappingDao
import com.minhas.teller.data.entities.HindiMapping

class HindiMappingRepository(private val dao: HindiMappingDao) {
    
    suspend fun getAllMappings(): List<HindiMapping> = dao.getAll()
    
    suspend fun findByHindiName(name: String): HindiMapping? = dao.findByHindiName(name.lowercase())
    
    suspend fun findByEnglishName(name: String): HindiMapping? = dao.findByEnglishName(name.lowercase())
    
    suspend fun search(query: String): List<HindiMapping> = dao.search(query.lowercase())
    
    suspend fun hasData(): Boolean = dao.count() > 0
    
    suspend fun initializeDatabase() {
        if (dao.count() == 0) {
            // Pre-populate with common Hindi items
            dao.insertAll(getPredefinedMappings())
        }
    }
    
    /**
     * Predefined Hindi-English mappings for common grocery items
     */
    private fun getPredefinedMappings(): List<HindiMapping> {
        return listOf(
            // Fruits
            HindiMapping(hindiName = "केला", hinglishName = "kela", englishName = "banana", category = "fruit", hsnCode = "0803", keywords = "kela,keela,kela,banana,fruit"),
            HindiMapping(hindiName = "सेब", hinglishName = "seb", englishName = "apple", category = "fruit", hsnCode = "0808", keywords = "seb,saeb,apple,fruit"),
            HindiMapping(hindiName = "आम", hinglishName = "aam", englishName = "mango", category = "fruit", hsnCode = "0804", keywords = "aam,aam,mango,fruit"),
            HindiMapping(hindiName = "संतरा", hinglishName = "santara", englishName = "orange", category = "fruit", hsnCode = "0805", keywords = "santara,orange,narangi,fruit"),
            HindiMapping(hindiName = "अंगूर", hinglishName = "angoor", englishName = "grapes", category = "fruit", hsnCode = "0806", keywords = "angoor,angur,grapes,fruit"),
            HindiMapping(hindiName = "तरबूज", hinglishName = "tarbooz", englishName = "watermelon", category = "fruit", hsnCode = "0807", keywords = "tarbooz,tarbuj,watermelon,fruit"),
            HindiMapping(hindiName = "पपीता", hinglishName = "papita", englishName = "papaya", category = "fruit", hsnCode = "0807", keywords = "papita,papeeta,papaya,fruit"),
            HindiMapping(hindiName = "गुआवा", hinglishName = "amrud", englishName = "guava", category = "fruit", hsnCode = "0804", keywords = "amrud,peru,guava,fruit"),
            HindiMapping(hindiName = "अनार", hinglishName = "anaar", englishName = "pomegranate", category = "fruit", hsnCode = "0810", keywords = "anaar,anar,pomegranate,fruit"),
            HindiMapping(hindiName = "नारियल", hinglishName = "nariyal", englishName = "coconut", category = "fruit", hsnCode = "0801", keywords = "nariyal,narial,coconut,fruit"),
            
            HindiMapping(hindiName = "आलू", hinglishName = "aaloo", englishName = "potato", category = "vegetable", hsnCode = "0701", keywords = "aaloo,aalloo,alloo,aloo,aalu,allu,aalo,allo,alu,batata,potato,vegetable"),
            HindiMapping(hindiName = "प्याज", hinglishName = "pyaaz", englishName = "onion", category = "vegetable", hsnCode = "0703", keywords = "pyaaz,pyaz,onion,vegetable"),
            HindiMapping(hindiName = "टमाटर", hinglishName = "tamatar", englishName = "tomato", category = "vegetable", hsnCode = "0702", keywords = "tamatar,tamatr,tomato,tomato,vegetable"),
            HindiMapping(hindiName = "भिंडी", hinglishName = "bhindi", englishName = "okra", category = "vegetable", hsnCode = "0709", keywords = "bhindi,bhindi,okra,lady finger,vegetable"),
            HindiMapping(hindiName = "बैंगन", hinglishName = "baingan", englishName = "eggplant", category = "vegetable", hsnCode = "0709", keywords = "baingan,bengan,brinjal,eggplant,vegetable"),
            HindiMapping(hindiName = "गोभी", hinglishName = "gobhi", englishName = "cauliflower", category = "vegetable", hsnCode = "0704", keywords = "gobhi,gobi,cauliflower,phool gobi,vegetable"),
            HindiMapping(hindiName = "गाजर", hinglishName = "gajar", englishName = "carrot", category = "vegetable", hsnCode = "0706", keywords = "gajar,gajjar,carrot,vegetable"),
            HindiMapping(hindiName = "मूली", hinglishName = "mooli", englishName = "radish", category = "vegetable", hsnCode = "0706", keywords = "mooli,mooli,radish,vegetable"),
            HindiMapping(hindiName = "पालक", hinglishName = "palak", englishName = "spinach", category = "vegetable", hsnCode = "0709", keywords = "palak,spinach,vegetable"),
            HindiMapping(hindiName = "शिमला मिर्च", hinglishName = "shimla mirch", englishName = "capsicum", category = "vegetable", hsnCode = "0709", keywords = "shimla mirch,capsicum,shimla,vegetable"),
            HindiMapping(hindiName = "हरी मिर्च", hinglishName = "hari mirch", englishName = "green chili", category = "vegetable", hsnCode = "0709", keywords = "hari mirch,mirchi,green chili,vegetable"),
            HindiMapping(hindiName = "लहसुन", hinglishName = "lahsun", englishName = "garlic", category = "vegetable", hsnCode = "0703", keywords = "lahsun,lasun,garlic,vegetable"),
            HindiMapping(hindiName = "अदरक", hinglishName = "adrak", englishName = "ginger", category = "vegetable", hsnCode = "0910", keywords = "adrak,adrack,ginger,vegetable,spice"),
            HindiMapping(hindiName = "करेला", hinglishName = "karela", englishName = "bitter gourd", category = "vegetable", hsnCode = "0709", keywords = "karela,karela,bitter gourd,vegetable"),
            HindiMapping(hindiName = "लौकी", hinglishName = "lauki", englishName = "bottle gourd", category = "vegetable", hsnCode = "0709", keywords = "lauki,loki,dudhi,bottle gourd,vegetable"),
            
            // Grains & Pulses
            HindiMapping(hindiName = "चावल", hinglishName = "chawal", englishName = "rice", category = "grocery", hsnCode = "1006", keywords = "chawal,chaval,rice,grain"),
            HindiMapping(hindiName = "गेहूं", hinglishName = "gehu", englishName = "wheat", category = "grocery", hsnCode = "1001", keywords = "gehu,gehun,gandum,wheat,grain"),
            HindiMapping(hindiName = "आटा", hinglishName = "aata", englishName = "wheat flour", category = "grocery", hsnCode = "1101", keywords = "aata,atta,wheat flour,grain"),
            HindiMapping(hindiName = "दाल", hinglishName = "daal", englishName = "lentils", category = "grocery", hsnCode = "0713", keywords = "daal,dal,lentils,pulses"),
            HindiMapping(hindiName = "अरहर की दाल", hinglishName = "arhar dal", englishName = "pigeon peas", category = "grocery", hsnCode = "0713", keywords = "arhar,arhar dal,toor dal,pigeon peas,pulses"),
            HindiMapping(hindiName = "मूंग की दाल", hinglishName = "moong dal", englishName = "mung beans", category = "grocery", hsnCode = "0713", keywords = "moong,mung,mung dal,moong dal,pulses"),
            HindiMapping(hindiName = "मसूर की दाल", hinglishName = "masoor dal", englishName = "red lentils", category = "grocery", hsnCode = "0713", keywords = "masoor,masoor dal,red lentils,pulses"),
            HindiMapping(hindiName = "उड़द की दाल", hinglishName = "urad dal", englishName = "black gram", category = "grocery", hsnCode = "0713", keywords = "urad,urad dal,black gram,pulses"),
            HindiMapping(hindiName = "चना", hinglishName = "chana", englishName = "chickpeas", category = "grocery", hsnCode = "0713", keywords = "chana,chole,chickpeas,pulses"),
            HindiMapping(hindiName = "राजमा", hinglishName = "rajma", englishName = "kidney beans", category = "grocery", hsnCode = "0713", keywords = "rajma,rajma,kidney beans,pulses"),
            HindiMapping(hindiName = "बेसन", hinglishName = "besan", englishName = "gram flour", category = "grocery", hsnCode = "1106", keywords = "besan,besan,gram flour,pulses"),
            HindiMapping(hindiName = "सूजी", hinglishName = "sooji", englishName = "semolina", category = "grocery", hsnCode = "1103", keywords = "sooji,sooji,rava,semolina"),
            HindiMapping(hindiName = "मक्का", hinglishName = "makka", englishName = "maize", category = "grocery", hsnCode = "1005", keywords = "makka,makka,corn,maize,grain"),
            HindiMapping(hindiName = "बाजरा", hinglishName = "bajra", englishName = "pearl millet", category = "grocery", hsnCode = "1008", keywords = "bajra,bajra,pearl millet,grain"),
            HindiMapping(hindiName = "ज्वार", hinglishName = "jwar", englishName = "sorghum", category = "grocery", hsnCode = "1007", keywords = "jwar,jowar,sorghum,grain"),
            
            // Dairy
            HindiMapping(hindiName = "दूध", hinglishName = "doodh", englishName = "milk", category = "dairy", hsnCode = "0401", keywords = "doodh,dudh,milk,dairy"),
            HindiMapping(hindiName = "दही", hinglishName = "dahi", englishName = "curd", category = "dairy", hsnCode = "0403", keywords = "dahi,curd,yogurt,dairy"),
            HindiMapping(hindiName = "घी", hinglishName = "ghee", englishName = "clarified butter", category = "dairy", hsnCode = "0405", keywords = "ghee,ghii,clarified butter,dairy"),
            HindiMapping(hindiName = "मक्खन", hinglishName = "makhan", englishName = "butter", category = "dairy", hsnCode = "0405", keywords = "makhan,makkhan,butter,dairy"),
            HindiMapping(hindiName = "पनीर", hinglishName = "paneer", englishName = "cottage cheese", category = "dairy", hsnCode = "0406", keywords = "paneer,paneer,cottage cheese,dairy"),
            HindiMapping(hindiName = "छाछ", hinglishName = "chhachh", englishName = "buttermilk", category = "dairy", hsnCode = "0403", keywords = "chhachh,chhach,buttermilk,dairy"),
            
            // Spices
            HindiMapping(hindiName = "हल्दी", hinglishName = "haldi", englishName = "turmeric", category = "spice", hsnCode = "0910", keywords = "haldi,haldi,turmeric,spice"),
            HindiMapping(hindiName = "लाल मिर्च", hinglishName = "lal mirch", englishName = "red chili powder", category = "spice", hsnCode = "0904", keywords = "lal mirch,lal mirchi,red chili,chilli powder,spice"),
            HindiMapping(hindiName = "धनिया", hinglishName = "dhaniya", englishName = "coriander", category = "spice", hsnCode = "0909", keywords = "dhaniya,dhania,coriander,spice"),
            HindiMapping(hindiName = "जीरा", hinglishName = "jeera", englishName = "cumin", category = "spice", hsnCode = "0909", keywords = "jeera,zeera,cumin,spice"),
            HindiMapping(hindiName = "अजवाइन", hinglishName = "ajwain", englishName = "carom seeds", category = "spice", hsnCode = "0910", keywords = "ajwain,ajvain,carom seeds,spice"),
            HindiMapping(hindiName = "हींग", hinglishName = "hing", englishName = "asafoetida", category = "spice", hsnCode = "1301", keywords = "hing,heeng,asafoetida,spice"),
            HindiMapping(hindiName = "लौंग", hinglishName = "laung", englishName = "cloves", category = "spice", hsnCode = "0907", keywords = "laung,long,cloves,spice"),
            HindiMapping(hindiName = "इलायची", hinglishName = "elaichi", englishName = "cardamom", category = "spice", hsnCode = "0908", keywords = "elaichi,elachi,cardamom,spice"),
            HindiMapping(hindiName = "दालचीनी", hinglishName = "dalchini", englishName = "cinnamon", category = "spice", hsnCode = "0906", keywords = "dalchini,dalchini,cinnamon,spice"),
            HindiMapping(hindiName = "काली मिर्च", hinglishName = "kali mirch", englishName = "black pepper", category = "spice", hsnCode = "0904", keywords = "kali mirch,kali mirchi,black pepper,spice"),
            HindiMapping(hindiName = "तेज पत्ता", hinglishName = "tej patta", englishName = "bay leaf", category = "spice", hsnCode = "0910", keywords = "tej patta,bay leaf,spice"),
            
            // Sugar & Salt
            HindiMapping(hindiName = "चीनी", hinglishName = "cheeni", englishName = "sugar", category = "grocery", hsnCode = "1701", keywords = "cheeni,chini,sugar,grocery"),
            HindiMapping(hindiName = "नमक", hinglishName = "namak", englishName = "salt", category = "grocery", hsnCode = "2501", keywords = "namak,nammak,salt,grocery"),
            
            // Tea & Coffee
            HindiMapping(hindiName = "चाय", hinglishName = "chai", englishName = "tea", category = "beverage", hsnCode = "0902", keywords = "chai,chay,tea,beverage"),
            HindiMapping(hindiName = "चाय पत्ती", hinglishName = "chai patti", englishName = "tea leaves", category = "beverage", hsnCode = "0902", keywords = "chai patti,chai patti,tea leaves"),
            HindiMapping(hindiName = "कॉफी", hinglishName = "coffee", englishName = "coffee", category = "beverage", hsnCode = "0901", keywords = "coffee,cofi,coffee"),
            
            // Oils
            HindiMapping(hindiName = "तेल", hinglishName = "tel", englishName = "oil", category = "grocery", hsnCode = "1508", keywords = "tel,tail,oil,edible oil"),
            HindiMapping(hindiName = "सरसों का तेल", hinglishName = "sarson ka tel", englishName = "mustard oil", category = "grocery", hsnCode = "1508", keywords = "sarson ka tel,mustard oil"),
            HindiMapping(hindiName = "रिफाइंड", hinglishName = "refined", englishName = "refined oil", category = "grocery", hsnCode = "1508", keywords = "refined,refind,refined oil"),
            HindiMapping(hindiName = "नारियल तेल", hinglishName = "nariyal tel", englishName = "coconut oil", category = "grocery", hsnCode = "1513", keywords = "nariyal tel,coconut oil"),
            
            // Dry Fruits
            HindiMapping(hindiName = "बादाम", hinglishName = "badam", englishName = "almond", category = "dry fruit", hsnCode = "0802", keywords = "badam,badam,almond,dry fruit"),
            HindiMapping(hindiName = "काजू", hinglishName = "kaju", englishName = "cashew", category = "dry fruit", hsnCode = "0801", keywords = "kaju,kaju,cashew,dry fruit"),
            HindiMapping(hindiName = "पिस्ता", hinglishName = "pista", englishName = "pistachio", category = "dry fruit", hsnCode = "0802", keywords = "pista,pista,pistachio,dry fruit"),
            HindiMapping(hindiName = "अखरोट", hinglishName = "akhrot", englishName = "walnut", category = "dry fruit", hsnCode = "0802", keywords = "akhrot,akhroat,walnut,dry fruit"),
            HindiMapping(hindiName = "किशमिश", hinglishName = "kishmish", englishName = "raisins", category = "dry fruit", hsnCode = "0806", keywords = "kishmish,kismis,raisins,dry fruit"),
            HindiMapping(hindiName = "मुनक्का", hinglishName = "munnaka", englishName = "black raisins", category = "dry fruit", hsnCode = "0806", keywords = "munnaka,munakka,black raisins"),
            HindiMapping(hindiName = "खजूर", hinglishName = "khajoor", englishName = "dates", category = "dry fruit", hsnCode = "0804", keywords = "khajoor,khajur,dates,dry fruit"),
            HindiMapping(hindiName = "अंजीर", hinglishName = "anjeer", englishName = "fig", category = "dry fruit", hsnCode = "0804", keywords = "anjeer,anjeer,fig,dry fruit"),
            
            // Flours
            HindiMapping(hindiName = "मैदा", hinglishName = "maida", englishName = "all purpose flour", category = "grocery", hsnCode = "1101", keywords = "maida,all purpose flour,refined flour"),
            HindiMapping(hindiName = "पोहा", hinglishName = "poha", englishName = "flattened rice", category = "grocery", hsnCode = "1904", keywords = "poha,powa,flattened rice"),
            HindiMapping(hindiName = "सेवइयां", hinglishName = "seviyan", englishName = "vermicelli", category = "grocery", hsnCode = "1902", keywords = "seviyan,sevian,vermicelli"),
            
            // Snacks
            HindiMapping(hindiName = "बिस्कुट", hinglishName = "biscuit", englishName = "biscuits", category = "snacks", hsnCode = "1905", keywords = "biscuit,biskut,cookies"),
            HindiMapping(hindiName = "चिप्स", hinglishName = "chips", englishName = "chips", category = "snacks", hsnCode = "2005", keywords = "chips,crisps"),
            HindiMapping(hindiName = "नमकीन", hinglishName = "namkeen", englishName = "savory snacks", category = "snacks", hsnCode = "2005", keywords = "namkeen,savory,snacks"),
            
            // Misc
            HindiMapping(hindiName = "साबुन", hinglishName = "sabun", englishName = "soap", category = "personal care", hsnCode = "3401", keywords = "sabun,soap"),
            HindiMapping(hindiName = "शैम्पू", hinglishName = "shampoo", englishName = "shampoo", category = "personal care", hsnCode = "3305", keywords = "shampoo,shampu"),
            HindiMapping(hindiName = "टूथपेस्ट", hinglishName = "toothpaste", englishName = "toothpaste", category = "personal care", hsnCode = "3306", keywords = "toothpaste,datun"),
            HindiMapping(hindiName = "अंडा", hinglishName = "anda", englishName = "egg", category = "food", hsnCode = "0407", keywords = "anda,egg,eggs"),
            HindiMapping(hindiName = "चिकन", hinglishName = "chicken", englishName = "chicken", category = "meat", hsnCode = "0207", keywords = "chicken,murg,murga"),
            HindiMapping(hindiName = "मटन", hinglishName = "mutton", englishName = "mutton", category = "meat", hsnCode = "0204", keywords = "mutton,goat meat"),
            HindiMapping(hindiName = "मछली", hinglishName = "machli", englishName = "fish", category = "meat", hsnCode = "0303", keywords = "machli,macchli,fish"),
            HindiMapping(hindiName = "हलवा", hinglishName = "halwa", englishName = "halwa", category = "sweets", hsnCode = "1704", keywords = "halwa,halva,sweet"),
            HindiMapping(hindiName = "बर्फी", hinglishName = "barfi", englishName = "barfi", category = "sweets", hsnCode = "1704", keywords = "barfi,burfi,barfee,sweet"),
            HindiMapping(hindiName = "रसगुल्ला", hinglishName = "rasgulla", englishName = "rasgulla", category = "sweets", hsnCode = "1704", keywords = "rasgulla,rosogolla,sweet"),
            HindiMapping(hindiName = "गुलाब जामुन", hinglishName = "gulab jamun", englishName = "gulab jamun", category = "sweets", hsnCode = "1704", keywords = "gulab jamun,gulabjamun,sweet"),
            HindiMapping(hindiName = "लड्डू", hinglishName = "laddu", englishName = "laddu", category = "sweets", hsnCode = "1704", keywords = "laddu,ladoo,laddoo,sweet"),
            HindiMapping(hindiName = "जलेबी", hinglishName = "jalebi", englishName = "jalebi", category = "sweets", hsnCode = "1704", keywords = "jalebi,jalebee,sweet"),
            
            // Common Brands/Types
            HindiMapping(hindiName = "ताजा", hinglishName = "taza", englishName = "fresh", category = "general", hsnCode = null, keywords = "taza,tazaa,fresh"),
            HindiMapping(hindiName = "पुराना", hinglishName = "purana", englishName = "old", category = "general", hsnCode = null, keywords = "purana,purana,old"),
            HindiMapping(hindiName = "छोटा", hinglishName = "chota", englishName = "small", category = "general", hsnCode = null, keywords = "chota,chhota,small"),
            HindiMapping(hindiName = "बड़ा", hinglishName = "bada", englishName = "big", category = "general", hsnCode = null, keywords = "bada,bada,big,large"),
            HindiMapping(hindiName = "मीडियम", hinglishName = "medium", englishName = "medium", category = "general", hsnCode = null, keywords = "medium,midiam"),
        )
    }
    
    /**
     * Transliterate Hindi/Hinglish to English
     */
    suspend fun translateToEnglish(input: String): String? {
        val normalized = input.lowercase().trim()

        // Exact match first
        val exactMatch = dao.findByHindiName(normalized)
        if (exactMatch != null) return exactMatch.englishName

        // Search in keywords. The DB query (`keywords LIKE '%query%'`) is only a fast
        // pre-filter and matches raw substrings — "ande" (eggs) is a literal substring of
        // "coriander", and "dudh" (milk) is a literal substring of "dudhi" (bottle gourd's
        // keyword), so either word would otherwise hijack an unrelated item. Keep only
        // candidates where the query is actually one of the comma-separated keyword tokens.
        val keywordMatches = dao.search(normalized)
            .filter { mapping ->
                mapping.keywords.split(',').any { it.trim().equals(normalized, ignoreCase = true) }
            }
        if (keywordMatches.isNotEmpty()) {
            // Return best match (could implement fuzzy matching here)
            return keywordMatches.first().englishName
        }

        return null
    }
}
