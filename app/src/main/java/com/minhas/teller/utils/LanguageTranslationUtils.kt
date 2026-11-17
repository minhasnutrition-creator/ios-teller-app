package com.minhas.teller.utils

import java.util.Locale

object LanguageTranslationUtils {

    private val COLLAPSE_REPEAT_REGEX = Regex("([a-z])\\1+")
    private val WS_SPLIT_REGEX = Regex("\\s+")
    private val TEL_WORD_REGEX = Regex("\\btel\\b")

    private val translationMap: Map<String, String> = mapOf(
        // Electrical, Electronics, Auto Spares, Supplements & Hinglish Kirana store vocabulary
        "taar" to "electric wire",
        "bijli taar" to "electric wire",
        "bijli ka taar" to "electric wire",
        "pankha" to "ceiling fan",
        "farata" to "pedestal fan",
        "bijli board" to "extension board",
        "batry" to "battery",
        "battry" to "battery",
        "blub" to "led bulb",
        "balb" to "led bulb",
        "invrtr" to "inverter",
        "stablizr" to "voltage stabilizer",
        "geysr" to "geyser",
        "kalach" to "clutch plate",
        "chakka" to "tyre",
        "shoker" to "shock absorber",
        "spak plug" to "spark plug",
        "brak pad" to "brake pad",
        "kamani" to "leaf spring",
        "sheesha" to "side mirror",
        "chashma" to "sunglasses",
        "jhadu" to "broom",
        "jharu" to "broom",
        "poncha" to "floor mop",
        "pocha" to "floor mop",
        "kapoor" to "camphor",
        "kapur" to "camphor",
        "dhoopbatti" to "incense stick",
        "loban" to "incense stick",
        "machis" to "matchbox",
        "mombatti" to "candle",
        "tala" to "padlock",
        "hathoda" to "hammer",
        "pechkas" to "screwdriver",
        "plass" to "plier",
        "paana" to "spanner",
        "nal" to "water tap",
        "regmar" to "sandpaper",
        "bartan" to "steel utensils",
        "kainchi" to "scissors",
        "silai machine" to "sewing machine",
        "taraju" to "weighing scale",
        "protin" to "whey protein",
        "whey protien" to "whey protein",
        "creatin" to "creatine",
        "shilajeet" to "shilajit",
        "ashwagnda" to "ashwagandha",
        "biskut" to "biscuit",
        "choklet" to "chocolate",
        "daliya" to "broken wheat",
        "sarson tel" to "mustard oil",
        "nariyal tel" to "coconut oil",
        "moongfali tel" to "groundnut oil",
        "saboon" to "soap",
        "manjan" to "toothpaste",
        "dawai" to "medicine",
        // Fruits & Groceries (Common Transliterated Hindi/Plurals)
        "aam" to "Mango", "aamo" to "Mango", "seb" to "Apple", "sebo" to "Apple", 
        "dahi" to "Yogurt", "doodh" to "Milk", "dudh" to "Milk", "paneer" to "Cottage Cheese", 
        "chawal" to "Rice", "atta" to "Flour", "dal" to "Lentils", "cheeni" to "Sugar", 
        "chini" to "Sugar", "shakkar" to "Sugar", "gud" to "Jaggery", "namak" to "Salt",
        "tel" to "Oil", "masala" to "Spices", "masale" to "Spices", "sabzi" to "Vegetable", 
        "sabji" to "Vegetable", "sabziyan" to "Vegetables", "sabjiyan" to "Vegetables",
        "phool" to "Flower", "phal" to "Fruit", "adrak" to "Ginger", "lasan" to "Garlic",
        "lahsun" to "Garlic", "haldi" to "Turmeric", "jeera" to "Cumin", "laung" to "Cloves",
        "badam" to "Almond", "kaju" to "Cashew", "kishmish" to "Raisins", "pista" to "Pistachio", 
        "akhrot" to "Walnut", "balty" to "Bucket", "chabi" to "Key", "tala" to "Lock",
        "kursi" to "Chair", "kursiyan" to "Chair", "kursiya" to "Chair", "chair" to "Chair", "chairs" to "Chair",
        "mez" to "Table", "mej" to "Table", "table" to "Table", "tables" to "Table", "tebal" to "Table", "teble" to "Table", "desk" to "Table", "desks" to "Table",
        "kalam" to "Pen", "pen" to "Pen", "pens" to "Pen", "kagaz" to "Paper",
        "kenchi" to "Scissors", "pech" to "Screw", "rang" to "Paint",
        "tamatar" to "Tomato", "tamataro" to "Tomato", "tamator" to "Tomato", "tamatr" to "Tomato", "tamater" to "Tomato",
        "aloo" to "Potato", "aalu" to "Potato", "aaloo" to "Potato", "aalloo" to "Potato", "alloo" to "Potato", "allu" to "Potato", "aalo" to "Potato", "allo" to "Potato", "alu" to "Potato", "alo" to "Potato", "batata" to "Potato", "batate" to "Potato", "aaluo" to "Potato",
        "pyaj" to "Onion", "pyaaz" to "Onion", "pyaz" to "Onion", "pyaaj" to "Onion", "kanda" to "Onion", "kande" to "Onion", "pyajo" to "Onion", "pyaazo" to "Onion",
        "anda" to "Egg", "ande" to "Egg", "andey" to "Egg", "murgi" to "Chicken", "machli" to "Fish", 
        "mirch" to "Chili", "mirchi" to "Chili", "hari mirch" to "Green Chilli", "lal mirch" to "Red Chilli",
        "dhaniya" to "Coriander", "dhaniya patti" to "Coriander", "pudina" to "Mint", "saunf" to "Fennel",
        "kaddu" to "Pumpkin", "bhindi" to "Ladyfinger", "bhindiyan" to "Ladyfinger", "baigan" to "Brinjal", 
        "matar" to "Peas", "gajar" to "Carrot", "gajaron" to "Carrot", "mooli" to "Radish", 
        "gobhi" to "Cauliflower", "palak" to "Spinach", "kela" to "Banana", "kele" to "Banana", 
        "santra" to "Orange", "kheera" to "Cucumber", "kheere" to "Cucumber",
        "angoor" to "Grapes", "anar" to "Pomegranate", "papita" to "Papaya",
        "tarbooj" to "Watermelon", "kharbooja" to "Muskmelon", "nashpati" to "Pear",
        "amrood" to "Guava", "ananas" to "Pineapple", "chikoo" to "Sapodilla",
        "nimbu" to "Lemon", "nariyal" to "Coconut", "maida" to "Refined Flour",
        "besan" to "Gram Flour", "rajma" to "Kidney Beans", "chana" to "Chickpeas",
        "gehun" to "Wheat", "makka" to "Corn", "bajra" to "Millet",
        "makhan" to "Butter", "dahi" to "Curd", "ghee" to "Ghee",
        "sabun" to "Soap", "shampoo" to "Shampoo", "toothpaste" to "Toothpaste",
        "kapda" to "Cloth", "kapde" to "Cloth", "juta" to "Shoes", "jute" to "Shoes", "juto" to "Shoes", "joote" to "Shoes", "joota" to "Shoes",
        "gaadi" to "Vehicle", "gadi" to "Vehicle", "bike" to "Motorcycle",
        "motorcycle" to "Motorcycle", "car" to "Car", "kar" to "Car", "truck" to "Truck",
        "mobile" to "Mobile", "phone" to "Phone", "iphone" to "Iphone", "laptop" to "Laptop",
        "computer" to "Computer", "tv" to "Television", "fridge" to "Refrigerator",
        "ac" to "Air Conditioner", "fan" to "Fan", "fans" to "Fan", "pankha" to "Fan", "pankhe" to "Fan", "pankho" to "Fan", "bulb" to "Bulb", "coffee" to "Coffee", "coffe" to "Coffee",
        "cement" to "Cement", "bricks" to "Bricks", "sand" to "Sand",
        "iron" to "Iron", "sariya" to "Iron Rods", "paint" to "Paint",
        // ---- Metals, jewellery & more Hindi/Hinglish words (added) ----
        "sona" to "Gold", "sone" to "Gold", "sonaa" to "Gold", "gold" to "Gold",
        "सोना" to "Gold", "सोने" to "Gold", "chandi" to "Silver", "chaandi" to "Silver",
        "chandee" to "Silver", "silver" to "Silver", "चांदी" to "Silver", "चाँदी" to "Silver",
        "tamba" to "Copper", "taamba" to "Copper", "tambaa" to "Copper", "tambe" to "Copper",
        "tamda" to "Copper", "copper" to "Copper", "तांबा" to "Copper", "तांबे" to "Copper",
        "pital" to "Brass", "peetal" to "Brass", "pittal" to "Brass", "brass" to "Brass",
        "पीतल" to "Brass", "loha" to "Iron", "lohe" to "Iron", "लोहा" to "Iron",
        "lohaa" to "Iron", "steel" to "Steel", "stainless steel" to "Steel", "platinum" to "Platinum",
        "plateenam" to "Platinum", "heera" to "Diamond", "heere" to "Diamond", "hira" to "Diamond",
        "diamond" to "Diamond", "हीरा" to "Diamond", "moti" to "Pearl", "motee" to "Pearl",
        "pearl" to "Pearl", "मोती" to "Pearl", "gehna" to "Jewellery", "gahna" to "Jewellery",
        "gehne" to "Jewellery", "gahne" to "Jewellery", "zewar" to "Jewellery", "zevar" to "Jewellery",
        "jewar" to "Jewellery", "jewellery" to "Jewellery", "jewelry" to "Jewellery", "jewelery" to "Jewellery",
        "jewelary" to "Jewellery", "zewarat" to "Jewellery", "jewarat" to "Jewellery", "गहना" to "Jewellery",
        "गहने" to "Jewellery", "जेवर" to "Jewellery", "anguthi" to "Ring", "angoothi" to "Ring",
        "anghuthi" to "Ring", "ring" to "Ring", "अंगूठी" to "Ring", "sone ka biscuit" to "Gold Biscuit",
        "gold biscuit" to "Gold Biscuit", "sone ka sikka" to "Gold Coin", "chandi ka sikka" to "Silver Coin", "sikka" to "Coin",
        "sikke" to "Coin", "lakdi" to "Wood", "lakadi" to "Wood", "kanch" to "Glass",
        "kaanch" to "Glass", "sheesha" to "Glass", "pathar" to "Stone", "reta" to "Sand",
        "kapas" to "Cotton", "resham" to "Silk", "oon" to "Wool",
        "plastic" to "Plastic", "chaipatti" to "Tea Leaves", "mungfali" to "Groundnut",
        "moongfali" to "Groundnut", "mungphali" to "Groundnut", "til" to "Sesame", "jaiphal" to "Nutmeg",
        "tejpatta" to "Bay Leaf", "tej patta" to "Bay Leaf", "kesar" to "Saffron", "imli" to "Tamarind",
        "papad" to "Papad", "achar" to "Pickle", "achaar" to "Pickle", "agarbatti" to "Incense Sticks",
        "agarbati" to "Incense Sticks", "mombatti" to "Candle", "mombati" to "Candle", "davai" to "Medicine",
        "sarson ka tel" to "Mustard Oil", "sarso ka tel" to "Mustard Oil", "mungfali ka tel" to "Groundnut Oil", "nariyal tel" to "Coconut Oil",
        "nariyal ka tel" to "Coconut Oil", "sadi" to "Sari", "chhuri" to "Knife", "churi" to "Knife",
        // Grains & Essentials
        "chini" to "Sugar", "shakkar" to "Sugar", "gud" to "Jaggery",
        "dhan" to "Paddy", "sarson" to "Mustard", "soyabean" to "Soybean",
        "mung" to "Moong Dal", "arhar" to "Toor Dal", "masoor" to "Red Lentils",
        "urad" to "Black Gram", "kabuli chana" to "White Chickpeas",
        "suji" to "Semolina", "rava" to "Semolina", "poha" to "Beaten Rice",
        "murmura" to "Puffed Rice", "makhan" to "Butter", "malai" to "Cream",
        "lassi" to "Buttermilk", "chhach" to "Buttermilk",
        // Spices
        "kali mirch" to "Black Pepper", "lal mirch" to "Red Chilli",
        "dalchini" to "Cinnamon", "elaichi" to "Cardamom", "hing" to "Asafoetida",
        "methi" to "Fenugreek", "ajwain" to "Carom Seeds", "rai" to "Mustard Seeds",
        "saunth" to "Dry Ginger", "kasuri methi" to "Dry Fenugreek Leaves",
        // Vegetables
        "parwal" to "Pointed Gourd", "lauki" to "Bottle Gourd", "tinda" to "Apple Gourd",
        "tori" to "Ridge Gourd", "karela" to "Bitter Gourd", "shimla mirch" to "Capsicum",
        "pattagobhi" to "Cabbage", "phoolgobhi" to "Cauliflower", "baingan" to "Eggplant",
        "arbi" to "Colocasia", "kathal" to "Jackfruit", "shalgam" to "Turnip",
        "chukandar" to "Beetroot", "shakarkand" to "Sweet Potato",
        // Dairy & Bakery
        "khoa" to "Condensed Milk", "mava" to "Condensed Milk", "pav" to "Bread",
        "double roti" to "Bread", "biscuit" to "Biscuits", "namkeen" to "Savory Snacks",
        // Household
        "bartan" to "Utensils", "thali" to "Plate", "katori" to "Bowl",
        "chamach" to "Spoon", "glass" to "Glass", "chaku" to "Knife",
        "balti" to "Bucket", "magga" to "Mug", "rassa" to "Rope",
        "jhadu" to "Broom", "poncha" to "Mop", "parda" to "Curtain",
        "chadar" to "Bedsheet", "takiya" to "Pillow", "gadda" to "Mattress",
        // Clothing
        "kurta" to "Shirt", "pajama" to "Trousers", "saree" to "Sari",
        "dhoti" to "Dhoti", "topi" to "Cap", "moja" to "Socks",
        "banian" to "Undershirt", "chappal" to "Slippers",
        // Construction & Hardware
        "eent" to "Bricks", "rodi" to "Gravel", "badarpur" to "Coarse Sand",
        "choona" to "Lime", "kil" to "Nail", "hathoda" to "Hammer",
        "taar" to "Wire", "rassi" to "Rope", "pipe" to "Pipe",
        "nal" to "Tap", "shisha" to "Glass", "patthar" to "Stone",
        // Business & Market Terms
        "bhada" to "Rent/Freight", "freight" to "Rent/Freight", "kiraya" to "Rent", 
        "pagar" to "Salary", "vetan" to "Salary", "bima" to "Insurance", 
        "udhar" to "Outstanding", "bakaya" to "Outstanding", "jama" to "Deposit", 
        "chai" to "Tea", "tea" to "Tea", "chai patti" to "Tea Leaves", 
        "biscuits" to "Biscuits", "biscuit" to "Biscuits", "biskut" to "Biscuits",
        "nariyal paani" to "Coconut Water", "nimbu paani" to "Lemonade",
        "pudina" to "Mint",

        // ── Additional common items: stationery, snacks, household, clothing ──
        "kitab" to "Book", "kitaab" to "Book", "kitaben" to "Book", "kitabe" to "Book", "pustak" to "Book", "pustake" to "Book", "pustaken" to "Book",
        "kapi" to "Notebook", "copy" to "Notebook", "kapiyan" to "Notebook", "kopiyan" to "Notebook", "copies" to "Notebook", "notebook" to "Notebook", "notebooks" to "Notebook",
        "book" to "Book", "books" to "Book",
        "pencil" to "Pencil", "pencils" to "Pencil", "rubber" to "Eraser", "ruber" to "Eraser", "rabar" to "Eraser", "eraser" to "Eraser", "erasers" to "Eraser", "scale" to "Ruler",
        "shirt" to "Shirt", "shirts" to "Shirt", "tshirt" to "T-Shirt", "jeans" to "Jeans", "socks" to "Socks", "moje" to "Socks",
        "cylinder" to "Cylinder", "cylinders" to "Cylinder",
        "candy" to "Candy", "toffee" to "Candy", "chocolate" to "Chocolate", "choclet" to "Chocolate",
        "chips" to "Chips", "juice" to "Juice", "cold drink" to "Soft Drink", "sharbat" to "Syrup",
        "toothbrush" to "Toothbrush", "datun" to "Toothbrush", "detergent" to "Detergent", "surf" to "Detergent",
        "bucket" to "Bucket", "bottle" to "Bottle", "btl" to "Bottle", "dibba" to "Container", "dabba" to "Container",
        "bag" to "Bag", "bags" to "Bags", "chashma" to "Spectacles", "goggles" to "Sunglasses",
        "tiles" to "Tiles", "tile" to "Tiles",

        // ── English countable plurals (voice: "mangoes"/"apples" → singular stock item) ──
        "mango" to "Mango", "mangoes" to "Mango", "mangos" to "Mango",
        "apple" to "Apple", "apples" to "Apple",
        "orange" to "Orange", "oranges" to "Orange",
        "banana" to "Banana", "bananas" to "Banana",
        "tomato" to "Tomato", "tomatoes" to "Tomato",
        "potato" to "Potato", "potatoes" to "Potato",
        "onion" to "Onion", "onions" to "Onion",

        // ── Sanitary / hardware items with common ASR phonetic spellings ──
        "wash basin" to "Wash Basin", "wash baisen" to "Wash Basin", "baisen" to "Wash Basin",
        "bash baisen" to "Wash Basin", "bashbaisen" to "Wash Basin", "basin" to "Wash Basin", "basins" to "Wash Basin",
        "sink" to "Sink", "sinks" to "Sink",
        "slab" to "Slab", "slabs" to "Slab",

        // ── Indian Retail & Grocery & Hardware Items ──
        "dawa" to "Medicine", "dawai" to "Medicine", "dawain" to "Medicine", "medicine" to "Medicine", "medicines" to "Medicine",
        "cooler" to "Air Cooler", "coolers" to "Air Cooler", "air cooler" to "Air Cooler",
        "wire" to "Wire", "wires" to "Wire", "finolex wire" to "Finolex Wire", "cable" to "Cable", "cables" to "Cable", "switch" to "Switch", "switches" to "Switch", "socket" to "Socket", "sockets" to "Socket",
        "led bulb" to "LED Bulb", "tube light" to "Tube Light",
        "notebook" to "Notebook", "notebooks" to "Notebook", "register" to "Register", "registers" to "Register",
        "towel" to "Towel", "towels" to "Towel", "tauliya" to "Towel",
        "kurti" to "Kurti", "kurtis" to "Kurti", "suit" to "Suit", "suits" to "Suit", "dupatta" to "Dupatta",
        "putty" to "Wall Putty", "primer" to "Primer",
        "ret" to "Sand", "reti" to "Sand",
        "basmati chawal" to "Basmati Rice", "basmati rice" to "Basmati Rice",
        "sarson tel" to "Mustard Oil", "refined tel" to "Refined Oil", "mustard oil" to "Mustard Oil", "refined oil" to "Refined Oil",
        "chai patti" to "Tea Leaves", "tea leaves" to "Tea Leaves",
        "parle g" to "Biscuits", "parle-g" to "Biscuits"
    )

    private val categoryMap: Map<String, String> = mapOf(
        "electric wire" to "Electricals",
        "ceiling fan" to "Electricals",
        "pedestal fan" to "Electricals",
        "extension board" to "Electricals",
        "led bulb" to "Electricals",
        "inverter" to "Electricals",
        "voltage stabilizer" to "Electricals",
        "geyser" to "Electricals",
        "clutch plate" to "Automobile Spare Parts",
        "tyre" to "Automobile Spare Parts",
        "shock absorber" to "Automobile Spare Parts",
        "spark plug" to "Automobile Spare Parts",
        "brake pad" to "Automobile Spare Parts",
        "leaf spring" to "Automobile Spare Parts",
        "side mirror" to "Automobile Spare Parts",
        "whey protein" to "Health Supplements",
        "creatine" to "Health Supplements",
        "shilajit" to "Health Supplements",
        "ashwagandha" to "Health Supplements",
        "mass gainer" to "Health Supplements",
        "broom" to "Groceries & Home Care",
        "floor mop" to "Groceries & Home Care",
        "camphor" to "Puja Samagri",
        "incense stick" to "Puja Samagri",
        "matchbox" to "Groceries & Staples",
        "candle" to "Groceries & Home Care",
        "padlock" to "Hardware",
        "hammer" to "Hardware",
        "screwdriver" to "Hardware",
        "plier" to "Hardware",
        "spanner" to "Hardware",
        "water tap" to "Plumbing & Hardware",
        "sandpaper" to "Hardware",
        "steel utensils" to "Kitchenware",
        "medicine" to "Medicines & Pharma",
        "Mango" to "Fruits", "Apple" to "Fruits", "Banana" to "Fruits",
        "Orange" to "Fruits", "Grapes" to "Fruits", "Pomegranate" to "Fruits",
        "Papaya" to "Fruits", "Watermelon" to "Fruits", "Muskmelon" to "Fruits",
        "Fruit" to "Fruits", "Coconut" to "Fruits", "Guava" to "Fruits",
        "Pineapple" to "Fruits", "Sapodilla" to "Fruits", "Lemon" to "Fruits",
        "Pear" to "Fruits",
        "Potato" to "Vegetables", "Tomato" to "Vegetables", "Onion" to "Vegetables",
        "Brinjal" to "Vegetables", "Ladyfinger" to "Vegetables",
        "Cauliflower" to "Vegetables", "Peas" to "Vegetables", "Carrot" to "Vegetables",
        "Radish" to "Vegetables", "Spinach" to "Vegetables", "Pumpkin" to "Vegetables",
        "Ginger" to "Vegetables", "Garlic" to "Vegetables", "Coriander" to "Vegetables",
        "Chili" to "Vegetables", "Vegetable" to "Vegetables",
        "Pointed Gourd" to "Vegetables", "Bottle Gourd" to "Vegetables",
        "Apple Gourd" to "Vegetables", "Ridge Gourd" to "Vegetables",
        "Bitter Gourd" to "Vegetables", "Capsicum" to "Vegetables",
        "Cabbage" to "Vegetables", "Eggplant" to "Vegetables",
        "Colocasia" to "Vegetables", "Jackfruit" to "Vegetables",
        "Turnip" to "Vegetables", "Beetroot" to "Vegetables",
        "Sweet Potato" to "Vegetables",
        "Rice" to "Grains & Pulses", "Wheat" to "Grains & Pulses",
        "Flour" to "Grains & Pulses", "Refined Flour" to "Grains & Pulses",
        "Gram Flour" to "Grains & Pulses", "Lentils" to "Grains & Pulses",
        "Kidney Beans" to "Grains & Pulses", "Chickpeas" to "Grains & Pulses",
        "Millet" to "Grains & Pulses", "Corn" to "Grains & Pulses",
        "Paddy" to "Grains & Pulses", "Mustard" to "Grains & Pulses",
        "Soybean" to "Grains & Pulses", "Moong Dal" to "Grains & Pulses",
        "Toor Dal" to "Grains & Pulses", "Red Lentils" to "Grains & Pulses",
        "Black Gram" to "Grains & Pulses", "White Chickpeas" to "Grains & Pulses",
        "Semolina" to "Grains & Pulses", "Beaten Rice" to "Grains & Pulses",
        "Puffed Rice" to "Grains & Pulses",
        "Salt" to "Spices & Pantry", "Sugar" to "Spices & Pantry",
        "Turmeric" to "Spices & Pantry", "Cumin" to "Spices & Pantry",
        "Cloves" to "Spices & Pantry", "Fennel" to "Spices & Pantry",
        "Spices" to "Spices & Pantry", "Oil" to "Spices & Pantry",
        "Jaggery" to "Spices & Pantry", "Black Pepper" to "Spices & Pantry",
        "Red Chili" to "Spices & Pantry", "Red Chilli" to "Spices & Pantry", "Cinnamon" to "Spices & Pantry",
        "Cardamom" to "Spices & Pantry", "Asafoetida" to "Spices & Pantry",
        "Fenugreek" to "Spices & Pantry", "Carom Seeds" to "Spices & Pantry",
        "Mustard Seeds" to "Spices & Pantry", "Dry Ginger" to "Spices & Pantry",
        "Dry Fenugreek Leaves" to "Spices & Pantry",
        "Milk" to "Dairy", "Curd" to "Dairy", "Yogurt" to "Dairy",
        "Cottage Cheese" to "Dairy", "Butter" to "Dairy", "Ghee" to "Dairy",
        "Egg" to "Dairy", "Condensed Milk" to "Dairy", "Buttermilk" to "Dairy",
        "Cream" to "Dairy",
        "Chicken" to "Meat & Fish", "Fish" to "Meat & Fish",
        "Almond" to "Dry Fruits", "Pistachio" to "Dry Fruits",
        "Walnut" to "Dry Fruits", "Raisins" to "Dry Fruits",
        "Soap" to "Personal Care", "Shampoo" to "Personal Care",
        "Toothpaste" to "Personal Care", "Hair Oil" to "Personal Care",
        "Motorcycle" to "Two Wheelers", "Bike" to "Two Wheelers", "Scooter" to "Two Wheelers", "Car" to "Cars & Vehicles", "Vehicle" to "Vehicles",
        "Truck" to "Vehicles",
        "Mobile" to "Electronics & Appliances", "Phone" to "Electronics & Appliances",
        "Laptop" to "Electronics & Appliances", "Computer" to "Electronics & Appliances",
        "Television" to "Electronics & Appliances", "Refrigerator" to "Electronics & Appliances",
        "Air Conditioner" to "Electronics & Appliances", "Fan" to "Electronics & Appliances",
        "Bulb" to "Electronics & Appliances",
        "Cement" to "Construction", "Bricks" to "Construction",
        "Sand" to "Construction", "Iron" to "Construction", "Paint" to "Construction",
        "Slab" to "Construction",
        "Gold" to "Precious Metals", "Silver" to "Precious Metals", "Platinum" to "Precious Metals", "Diamond" to "Precious Metals",
        "Pearl" to "Precious Metals", "Jewellery" to "Jewellery", "Ring" to "Jewellery", "Gold Biscuit" to "Precious Metals",
        "Gold Coin" to "Precious Metals", "Silver Coin" to "Precious Metals", "Copper" to "Metals", "Brass" to "Metals", "Steel" to "Metals",
        "Iron Rods" to "Construction", "Gravel" to "Construction",
        "Coarse Sand" to "Construction", "Lime" to "Construction",
        "Nail" to "Hardware", "Hammer" to "Hardware", "Wire" to "Hardware",
        "Rope" to "Hardware", "Pipe" to "Hardware", "Tap" to "Hardware",
        "Glass" to "Hardware", "Stone" to "Hardware",
        "Wash Basin" to "Hardware", "Sink" to "Hardware",
        "Bread" to "Bakery", "Biscuits" to "Bakery", "Savory Snacks" to "Bakery",
        "Utensils" to "Household", "Plate" to "Household", "Bowl" to "Household",
        "Spoon" to "Household", "Knife" to "Household", "Glass" to "Household",
        "Bucket" to "Household", "Mug" to "Household", "Broom" to "Household",
        "Mop" to "Household", "Curtain" to "Household", "Bedsheet" to "Household",
        "Pillow" to "Household", "Mattress" to "Household",
        "Shirt" to "Clothing", "Trousers" to "Clothing", "Sari" to "Clothing",
        "Dhoti" to "Clothing", "Cap" to "Clothing", "Socks" to "Clothing",
        "Undershirt" to "Clothing", "Slippers" to "Clothing",
        "Medicine" to "Healthcare",
        "Air Cooler" to "Electronics",
        "LED Bulb" to "Electronics", "Tube Light" to "Electronics",
        "Wire" to "Hardware", "Finolex Wire" to "Hardware", "Cable" to "Hardware", "Switch" to "Hardware", "Socket" to "Hardware",
        "Notebook" to "Stationery", "Register" to "Stationery", "Book" to "Stationery", "Pencil" to "Stationery", "Pen" to "Stationery", "Eraser" to "Stationery", "Ruler" to "Stationery",
        "Table" to "Furniture", "Chair" to "Furniture", "Furniture" to "Furniture", "Desk" to "Furniture",
        "Towel" to "Household",
        "Kurti" to "Clothing", "Suit" to "Clothing", "Dupatta" to "Clothing",
        "Wall Putty" to "Construction", "Primer" to "Construction",
        "Basmati Rice" to "Grains & Pulses",
        "Mustard Oil" to "Cooking Oils", "Refined Oil" to "Cooking Oils",
        "Tea Leaves" to "Spices & Pantry"
    )

    fun translateItem(input: String): String {
        val lowerInput = input.lowercase(Locale.getDefault()).trim()
        if (lowerInput in setOf("for", "to", "from", "and", "of", "in", "the", "a", "an", "goods", "item", "items", "maal", "amount", "rs", "rupees", "rupaye")) {
            return "Goods"
        }
        val exact = translationMap[lowerInput]
        if (exact != null) return exact
        
        // Normalize repeated letters (e.g. "aalloo" -> "alo", "soooap" -> "soap", "milkk" -> "milk")
        val collapsed = lowerInput.replace(COLLAPSE_REPEAT_REGEX, "$1")
        val collapsedMatch = translationMap[collapsed]
        if (collapsedMatch != null) return collapsedMatch

        // Normalize duplicates of vowels & consonants
        val normalized = lowerInput
            .replace("aa", "a")
            .replace("oo", "u") // "aaloo" -> "alu", "doodh" -> "dudh"
            .replace("ee", "i")
            .replace("ll", "l")
            .replace("tt", "t")
            .replace("dd", "d")
        val normMatch = translationMap[normalized]
        if (normMatch != null) return normMatch

        val normalizedO = lowerInput
            .replace("aa", "a")
            .replace("oo", "o") // "aaloo" -> "alo"
            .replace("ee", "i")
            .replace("ll", "l")
            .replace("tt", "t")
            .replace("dd", "d")
        val normMatchO = translationMap[normalizedO]
        if (normMatchO != null) return normMatchO

        // Stemming plurals
        val stems = listOf(
            if (lowerInput.endsWith("o")) lowerInput.substring(0, lowerInput.length - 1) else null,
            if (lowerInput.endsWith("e")) lowerInput.substring(0, lowerInput.length - 1) + "a" else null, // kele -> kela
            if (lowerInput.endsWith("e")) lowerInput.substring(0, lowerInput.length - 1) else null,       // kele -> kel
            if (lowerInput.endsWith("en")) lowerInput.substring(0, lowerInput.length - 2) else null,     // kitaben -> kitab
            if (lowerInput.endsWith("ain")) lowerInput.substring(0, lowerInput.length - 3) else null,
            if (lowerInput.endsWith("yan")) lowerInput.substring(0, lowerInput.length - 3) + "i" else null, // bhindiyan -> bhindi
            if (lowerInput.endsWith("on")) lowerInput.substring(0, lowerInput.length - 2) else null         // tamataron -> tamatar
        ).filterNotNull()

        for (stem in stems) {
            val stemMatch = translationMap[stem]
            if (stemMatch != null) return stemMatch
            
            val normStem = stem.replace("aa", "a").replace("oo", "u").replace("ee", "i").replace("ll", "l")
            val normStemMatch = translationMap[normStem]
            if (normStemMatch != null) return normStemMatch
        }

        // Large offline dictionary of Hindi/Hinglish words (assets/hindi_items.tsv).
        HindiItemDictionary.translate(input)?.let { return it }

        val singular = singularize(lowerInput)
        if (singular != lowerInput) {
            val singMatch = translationMap[singular]
            if (singMatch != null) return singMatch
            return singular.split(WS_SPLIT_REGEX).joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }


        if (input.isEmpty()) return input
        return input.split(WS_SPLIT_REGEX).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    /**
     * Lowercase, singular comparison key for matching item names that differ only by
     * plural/case ("Apples" vs "apple"). Used when saving a voice voucher so "apples"
     * reuses the existing "Apple" stock item instead of creating a duplicate.
     */
    fun itemKey(name: String): String =
        singularize(name.lowercase(Locale.ROOT).trim()).trim()

    /** Best-effort English plural → singular, for building comparison keys (not display names). */
    private fun singularize(word: String): String {
        val w = word.trim()
        val lastSpace = w.lastIndexOf(' ')
        val lastWord = if (lastSpace >= 0) w.substring(lastSpace + 1) else w
        if (lastWord.length <= 3) return w
        return when {
            lastWord.endsWith("ies") -> w.dropLast(3) + "y"                              // berries -> berry
            (lastWord.endsWith("oes") && lastWord.length >= 6 && lastWord != "canoes" && lastWord != "tiptoes") ||
            lastWord.endsWith("ses") || lastWord.endsWith("xes") ||
            lastWord.endsWith("zes") || lastWord.endsWith("ches") || lastWord.endsWith("shes") -> w.dropLast(2) // mangoes -> mango
            lastWord.endsWith("s") && !lastWord.endsWith("ss") && !lastWord.endsWith("us") && !lastWord.endsWith("is") -> w.dropLast(1) // apples -> apple
            else -> w
        }
    }

    private val UNIT_PREFIX_REGEX = Regex(
        "^(?:killo|kilo|kilos|kila|kile|kg|kgs|kilogram|kilograms|gms|gram|grams|gm|litres|liters|litre|liter|ltr|ltrs|leeter|litar|lit|darjan|dozens|dozen|doz|pieces|piece|pcs|pc|peice|peices|nag|nos|number|numbers|packets|packet|pkt|pkts|pack|packs|meters|meter|metre|metres|mtr|mtrs|boriya|boriyan|bori|bags|bag|peti|box|boxes|bottles|bottle|btl|btls|foot|feet|ft)\\s+(.+)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Canonicalises an incoming inventory item name for storage: translates/singularises it,
     * strips a stray leading quantity ("20 milk" → ("Milk", 20.0)),
     * strips a stray leading unit ("20 liter milk" → ("Milk", 20.0, "Liter")),
     * and preserves an explicitly-passed unit ("Pcs", "Kg", "Liter", …) verbatim.
     * Returns (canonicalName, quantity, canonicalUnit), where canonicalUnit is `null` when no
     * unit was specified — the caller then decides the default (item's saved unit, else
     * ProductCategory.defaultUnitForItem).
     */
    fun canonicalItemDetails(name: String, quantity: Double, existingUnit: String? = null): Triple<String, Double, String?> {
        var n = translateItem(name).trim()
        var q = quantity
        var u = existingUnit?.takeIf { it.isNotBlank() && it != "null" }

        // 1. Strip leading numeric quantity: "20 liter milk" -> q=20, n="liter milk", or "20 milk" -> q=20, n="milk"
        val m = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)$").find(n)
        if (m != null && m.groupValues[2].trim().length >= 2) {
            val parsedQty = m.groupValues[1].toDoubleOrNull()
            if (parsedQty != null && (q == 1.0 || q == 0.0)) {
                q = parsedQty
            }
            n = translateItem(m.groupValues[2].trim())
        }

        // 2. Strip leading unit prefix if present: "liter milk" -> u="Liter", n="Milk"
        val uMatch = UNIT_PREFIX_REGEX.find(n)
        if (uMatch != null) {
            if (u.isNullOrBlank() || u.equals("Pcs", true) || u.equals("Units", true)) {
                val rawUnitToken = n.substring(0, n.length - uMatch.groupValues[1].length).trim()
                u = ProductCategory.resolveUnit(rawUnitToken)
            }
            n = translateItem(uMatch.groupValues[1].trim())
        }

        // 3. No unit yet → leave it null. The caller resolves the default: an existing item's
        //    saved unit first, then ProductCategory.defaultUnitForItem for brand-new items.
        //    (An explicitly-spoken "Pcs"/"Units" must NOT be re-derived — that's how "50 pieces
        //    banana" was silently turned into "Dozen".)

        return Triple(n, q, u)
    }

    /**
     * Canonicalises an incoming inventory item name for storage: translates/singularises it and
     * strips a stray leading quantity that ASR/the cloud LLM merged into the name
     * (e.g. "5 wash basin" → ("Wash Basin", 5.0)). Returns (name, quantity).
     */
    fun canonicalItemName(name: String, quantity: Double): Pair<String, Double> {
        val details = canonicalItemDetails(name, quantity, null)
        return details.first to details.second
    }

    /** Every word the built-in item dictionary knows, on either side of the translation. */
    private val knownWords: Set<String> by lazy {
        val words = HashSet<String>(translationMap.size * 3)
        for ((hinglish, english) in translationMap) {
            words.add(hinglish)
            val e = english.lowercase(Locale.ROOT)
            words.add(e)
            // Multi-word names also contribute their parts ("mustard oil" → "mustard", "oil").
            e.split(Regex("[\\s/]+")).forEach { if (it.length > 2) words.add(it) }
        }
        words
    }

    /**
     * True when [word] is a word the item dictionary recognises.
     *
     * [SpeechRepair] uses this to tell recogniser glue from a genuine name: "doodh" and "chawal"
     * are real words and must survive untouched, while "panchram" is not and is a candidate for
     * splitting back into "panch ram".
     */
    fun isKnownWord(word: String): Boolean {
        val w = word.lowercase(Locale.ROOT).trim()
        if (w.length < 3) return true // particles and two-letter words are never treated as glue
        if (w in knownWords) return true
        val singular = singularize(w)
        if (singular != w && singular in knownWords) return true
        // The large offline dictionary (assets/hindi_items.tsv) is only loaded on-device.
        return HindiItemDictionary.translate(w) != null
    }

    /** Consonant spine of a word: ASR gets vowels wrong far more often than consonants. */
    private fun consonantSkeleton(word: String): String {
        val stripped = word.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
        return stripped.filter { it !in "aeiouh" }.replace(Regex("([a-z])\\1+"), "$1")
    }

    /**
     * Picks the existing item name that a misheard spoken name most likely refers to
     * ("ram" → "Rum"), so a voice voucher reuses the real stock item instead of creating a
     * one-off duplicate of it.
     *
     * Callers must only pass names the dictionary does *not* recognise (see [isKnownWord]) —
     * otherwise a genuinely new item ("Pen") could be absorbed into a similar existing one
     * ("Pan"). Returns null when nothing is close enough.
     */
    fun closestKnownItem(spoken: String, candidates: List<String>): String? {
        val target = itemKey(spoken).lowercase(Locale.ROOT)
        if (target.length < 3) return null
        val targetSkeleton = consonantSkeleton(target)
        if (targetSkeleton.length < 2) return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates) {
            val key = itemKey(candidate).lowercase(Locale.ROOT)
            if (key.length < 3) continue
            val skeleton = consonantSkeleton(key)
            if (skeleton.firstOrNull() != targetSkeleton.firstOrNull()) continue
            val distance = editDistance(target, key)
            val score = when {
                skeleton == targetSkeleton -> 0                    // "ram" ~ "rum", "chini" ~ "cheeni"
                distance == 1 && target.length >= 4 -> 1
                distance == 2 && target.length >= 7 -> 2
                else -> continue
            }
            if (score < bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    fun suggestCategory(itemName: String): String {
        val translated = translateItem(itemName)
        categoryMap[translated]?.let { return it }
        val l = translated.lowercase(Locale.ROOT)
        return when {
            l.contains("supplement") || l.contains("gainer") || l.contains("whey") ||
            l.contains("protein") || l.contains("creatine") || l.contains("bcaa") ||
            l.contains("eaa") || l.contains("amino") || l.contains("pre workout") ||
            l.contains("preworkout") || l.contains("glutamine") || l.contains("carnitine") ||
            l.contains("citrulline") || l.contains("multivitamin") || l.contains("fish oil") ||
            l.contains("omega 3") || l.contains("mutant") || l.contains("muscleblaze") ||
            l.contains("dymatize") || l.contains("muscletech") || l.contains("myprotein") ||
            l.contains("labrada") || l.contains("isopure") || l.contains("avvatar") ||
            l.contains("asitis") || l.contains("nutrabay") || l.contains("wellcore") ||
            l.contains("bigmuscles") || l.contains("cellucor") || l.contains("c4") ||
            l.contains("fat burner") || l.contains("mass tech") || l.contains("serious mass") ||
            l.contains("nitrotech") || l.contains("nitro tech") || l.contains("endura mass") ||
            l.contains("kevin levrone") -> "Health Supplements"
            l.contains("engine oil") || l.contains("motor oil") || l.contains("lubricant") || l.contains("gear oil") -> "Vehicle Parts & Accessories"
            l.contains("cooking oil") || l.contains("mustard oil") || l.contains("sunflower oil") || l.contains("edible oil") -> "Cooking Oils"
            l.contains("biscuit") || l.contains("parle") || l.contains("cookie") || l.contains("rusk") -> "Biscuits & Bakery"
            l.contains("motorcycle") || l.contains("bike") || l.contains("scooter") || l.contains("scooty") -> "Two Wheelers"
            l.contains("chocolate") || l.contains("candy") || l.contains("cadbury dairy milk") || l.contains("dairymilk") || l.contains("cadbury") || l.contains("kitkat") -> "Confectionery & Chocolates"
            l.contains("thums up") || l.contains("thumbs up") || l.contains("coca cola") || l.contains("coke") || l.contains("pepsi") || l.contains("sprite") || l.contains("fanta") || l.contains("cold drink") || l.contains("soft drink") || l.contains("juice") || l.contains("beverage") -> "Beverages"
            l.contains("noodle") || l.contains("pasta") || l.contains("maggi") -> "Noodles"
            l.contains("chip") || l.contains("namkeen") -> "Snacks & Ready To Eat"
            l.contains("cbc") || l.contains("blood test") || l.contains("lft") || l.contains("kft") || l.contains("rft") || l.contains("hba1c") || l.contains("lipid profile") || l.contains("thyroid") || l.contains("xray") || l.contains("x ray") || l.contains("ultrasound") || l.contains("ct scan") || l.contains("mri") || l.contains("ecg") || l.contains("urine test") || l.contains("dengue") || l.contains("widal") -> "Diagnostic Services"
            l.contains("vacutainer") || l.contains("test tube") || l.contains("microscope") || l.contains("analyzer") || l.contains("centrifuge") || l.contains("reagent") || l.contains("micro tips") || l.contains("pipette") -> "Laboratory Supplies"
            l.contains("paint") || l.contains("putty") || l.contains("primer") || l.contains("enamel") || l.contains("thinner") || l.contains("asian paints") || l.contains("berger") || l.contains("nerolac") || l.contains("dulux") || l.contains("distemper") -> "Paints & Coatings"
            l.contains("biryani") || l.contains("dosa") || l.contains("idli") || l.contains("chowmein") || l.contains("paneer butter masala") || l.contains("dal makhani") || l.contains("tandoori") || l.contains("naan") || l.contains("pizza") || l.contains("burger") || l.contains("sandwich") -> "Restaurant & Food Service"
            l.contains("watch") || l.contains("smartwatch") || l.contains("g-shock") || l.contains("fastrack") || l.contains("titan") -> "Watches & Wearables"
            l.contains("sunglass") || l.contains("spectacles") || l.contains("ray ban") || l.contains("ray-ban") || l.contains("eyewear") -> "Eyewear & Optical"
            l.contains("headphone") || l.contains("earphone") || l.contains("airpod") || l.contains("earbud") || l.contains("airdopes") || l.contains("rockerz") -> "Audio & Accessories"
            l.contains("soap") || l.contains("shampoo") || l.contains("hair oil") || (l.contains("oil") && !l.contains("engine") && !l.contains("motor") && !l.contains("lubricant") && !l.contains("mustard") && !l.contains("cooking")) || l.contains("paste") -> "Personal Care"
            l.contains("tv") || l.contains("television") || l.contains("mobile") || l.contains("phone") || l.contains("laptop") || l.contains("computer") -> "Electronics & Appliances"
            l.contains("milk") || l.contains("ghee") || l.contains("butter") || l.contains("paneer") || l.contains("curd") -> "Dairy Products"
            l.contains("cement") -> "Construction Materials"
            l.contains("cloth") || l.contains("fabric") || l.contains("textile") -> "Textiles"
            l.contains("shirt") || l.contains("pant") || l.contains("jean") -> "Clothing"
            else -> "General Inventory"
        }
    }

    fun getGstRate(itemName: String, amount: Double = 0.0): Double {
        val translated = translateItem(itemName).lowercase(Locale.ROOT)
        return when {
            translated.contains("motorcycle") || translated.contains("bike") ->
                if (amount > 200000.0) 0.40 else 0.18
            translated.contains("cbc") || translated.contains("blood test") ||
            translated.contains("lft") || translated.contains("kft") ||
            translated.contains("rft") || translated.contains("hba1c") ||
            translated.contains("xray") || translated.contains("x ray") ||
            translated.contains("ultrasound") || translated.contains("mri") ||
            translated.contains("ct scan") || translated.contains("ecg") ||
            translated.contains("widal") || translated.contains("dengue") ||
            translated.contains("urine test") -> 0.0
            translated.contains("milk") || translated.contains("fruit") ||
            translated.contains("apple") || translated.contains("mango") ||
            translated.contains("banana") || translated.contains("orange") ||
            translated.contains("grape") || translated.contains("pomegranate") ||
            translated.contains("guava") || translated.contains("papaya") ||
            translated.contains("watermelon") ||
            translated.contains("vegetable") || translated.contains("egg") ||
            translated.contains("rice") || translated.contains("wheat") ||
            translated.contains("flour") || translated.contains("lentil") ||
            translated.contains("potato") || translated.contains("tomato") ||
            translated.contains("onion") || translated.contains("chicken") ||
            translated.contains("fish") -> 0.0
            translated.contains("biryani") || translated.contains("dosa") ||
            translated.contains("idli") || translated.contains("chowmein") ||
            translated.contains("paneer butter masala") || translated.contains("dal makhani") ||
            translated.contains("tandoori") || translated.contains("naan") ||
            translated.contains("kulcha") || translated.contains("thali") ||
            translated.contains("sugar") || isFivePercentOil(translated) ||
            translated.contains("tea") || translated.contains("coffee") ||
            translated.contains("spice") || translated.contains("cumin") ||
            translated.contains("turmeric") -> 0.05
            translated.contains("butter") || translated.contains("cheese") ||
            translated.contains("ghee") || translated.contains("dry fruit") ||
            translated.contains("almond") || translated.contains("cashew") ||
            translated.contains("pistachio") -> 0.12
            translated.contains("soap") || translated.contains("toothpaste") ||
            translated.contains("hair oil") || translated.contains("shampoo") ||
            translated.contains("paint") || translated.contains("cement") -> 0.18
            translated.contains("ac") || translated.contains("fridge") ||
            translated.contains("refrigerator") || translated.contains("washing machine") ||
            translated.contains("air conditioner") -> 0.28
            else -> 0.18
        }
    }

    /**
     * True if this is an oil that sits in the 5% slab under GST 2.0 — i.e. edible cooking oil
     * (mustard, sunflower, coconut…) or engine/lubricating oil (also 5%). Personal-care oils
     * (hair, essential, aroma, massage, body…) are 18% and are deliberately excluded, so
     * "engine oil" is not mistaken for cooking oil and "hair oil" is not under-rated at 5%.
     */
    fun isFivePercentOil(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        if (!n.contains("oil") && !TEL_WORD_REGEX.containsMatchIn(n)) return false
        val notFivePercent = listOf("hair", "essential", "aroma", "massage", "body", "baby", "beard", "skin", "face")
        return notFivePercent.none { n.contains(it) }
    }
}
