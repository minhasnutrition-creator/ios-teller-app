package com.minhas.teller.utils

import com.minhas.teller.data.entities.HsnEntry

/**
 * Exhaustive offline HSN/GST retail catalogs organized by Indian retail store vertical:
 * 1. Health, Fitness, Clinical & Ayurvedic Supplements
 * 2. Kirana / Kriyana & General Provision Stores
 * 3. Electrical Stores (Wires, Switchgear, Lighting, Fans, Power Backup, Conduits, Tools)
 * 4. Electronics, IT, Networking, CCTV, Office & Mobile Stores
 * 5. Automobile & Two-Wheeler Spare Parts Stores (2W/4W/Commercial/Lubricants)
 * 6. Plumbing, Hardware, Kitchenware, Stationery & General Retail
 *
 * Merged into [HsnCommonItems.entries] so every offline matcher, FinanceHelper parser,
 * VoucherExcelImporter, and Stock Item lookup shares the same single source of truth.
 */
internal object HsnStoreCatalogs {

    fun allStoreEntries(): List<HsnEntry> =
        supplementEntries() +
            kiranaEntries() +
            electricalEntries() +
            electronicsEntries() +
            automobileSparePartsEntries() +
            generalRetailEntries()

    // ═════════════════════════════════════════════════════════════════════════
    // 1. SUPPLEMENTS (Sports, Clinical Nutrition, Vitamins, Ayurvedic, Hydration)
    // ═════════════════════════════════════════════════════════════════════════
    private fun supplementEntries(): List<HsnEntry> = listOf(
        // NOTE: Do not include the word "snack" in 2106 supplement keywords so
        // HsnPresetData.correctMisPricedHeading keeps them at 18% GST.
        HsnEntry(
            hsnCode = "2106",
            description = "Clinical & adult nutrition powder (Ensure, Protinex, Resource)",
            keywords = "ensure, ensure diabetes care, ensure plus, protinex, protinex mama, protinex diabetes, pediasure, resource high protein, fresubin, kabipro, pentasure, nepro, threptin, medical nutrition, clinical nutrition, nutritional drink powder, diabetic protein powder, senior nutrition",
            gstRate = 18.0,
            category = "Clinical & Health Supplements"
        ),
        HsnEntry(
            hsnCode = "1901",
            description = "Health malt drink & infant cereal (Horlicks, Bournvita, Cerelac)",
            keywords = "horlicks, women's horlicks, junior horlicks, mother's horlicks, bournvita, boost, complan, milo, ovaltine, cerelac, lactogen, nan pro, dexolac, similac, enfamil, infant formula, baby food cereal, malt drink",
            gstRate = 5.0,
            category = "Health Drinks & Baby Food"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Electrolyte, ORS & sports hydration powder",
            keywords = "electrolyte powder, hydration mix, fast&up reload, reload effervescent, enervate, isostar, liquid iv, wellcore electrolytes, muscleblaze iso zero hydration, keto electrolytes, magnesium electrolyte, sports hydration",
            gstRate = 18.0,
            category = "Health Supplements"
        ),
        HsnEntry(
            hsnCode = "3004",
            description = "ORS oral rehydration salts (Electral, Enerzal ORS, WHO ORS)",
            keywords = "electral, ors powder, who ors, rehydration salt, oral rehydration solution, electral sachet, orsl, pedialyte",
            gstRate = 5.0,
            category = "Medicines & Pharmaceuticals"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Protein bars, energy bars & high-protein cookies",
            keywords = "protein bar, ritebite, max protein, yoga bar protein, yogabar, quest bar, grenade carb killa, muscleblaze protein bar, phab protein bar, clean protein bar, whey crisp bar, energy bar, protein cookie",
            gstRate = 18.0,
            category = "Health Supplements"
        ),
        HsnEntry(
            hsnCode = "2008",
            description = "Peanut butter & nut butters (Pintola, Alpino, MyFitness, Sundrop)",
            keywords = "peanut butter, pintola, alpino, myfitness, sundrop peanut butter, disano peanut butter, high protein peanut butter, chocolate peanut butter, almond butter, crunchy peanut butter, creamy peanut butter, natural peanut butter",
            gstRate = 5.0,
            category = "Health Foods"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Specialized sports amino, pump & endurance supplements",
            keywords = "l-citrulline, citrulline malate, beta alanine, arginine, aakg, agmatine sulfate, taurine powder, acetyl l-carnitine, alcar, cla, zma supplement, melatonin gummies, sleep aid supplement, caffeine anhydrous, l-theanine, glycerol monostearate, hydromax, electrolyte capsules",
            gstRate = 18.0,
            category = "Health Supplements"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Vitamins, minerals, heart, joint & gut nutraceuticals",
            keywords = "vitamin b12, methylcobalamin, vitamin k2 mk7, calcium citrate malate, magnesium glycinate, zinc picolinate, iron folic acid, coq10, ubiquinol, glucosamine chondroitin, msm joint support, curcumin capsules, probiotics capsules, prebiotic fiber, digestive enzymes, betaine hcl, milk thistle, silymarin, berberine, myo inositol, folic acid, luteolin, hyaluronic acid, wellbeing nutrition, swisse, setu, carbamide forte, truebasics, himalayan organics, tata 1mg supplements, healthkart hk vitals, hk vitals, wow life science, oziva, cosmix, plix, kapiva",
            gstRate = 18.0,
            category = "Health Supplements"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Herbal nutraceutical extracts (Ashwagandha, Shilajit, Apple Cider Vinegar)",
            keywords = "ashwagandha, ashwagandha ksm66, shilajit, pure himalayan shilajit, shilajit resin, kapiva shilajit, upakarma shilajit, rasayanam, safed musli, gokshura, gokhru, kaunch beej, brahmi, shatavari, moringa powder, spirulina tablets, wheatgrass powder, matcha green tea extract, apple cider vinegar, acv effervescent, bragg acv, test booster, testosterone booster, herbal immunity",
            gstRate = 18.0,
            category = "Health Supplements"
        ),
        HsnEntry(
            hsnCode = "3004",
            description = "Ayurvedic classical medicines (Chyawanprash, Triphala, Isabgol, Liv52)",
            keywords = "chyawanprash, dabur chyawanprash, baidyanath, zandu kesari jivan, triphala churna, isabgol, sat isabgol, psyllium husk, giloy juice, amla juice, aloe vera juice, karela jamun juice, liv 52, liv52, septilin, confido, speman, tentex forte, pilex, ashokarishta, dashmoolarishta, kayam churna, hajmola, pudin hara, zandu balm, amrutanjan",
            gstRate = 5.0,
            category = "Ayurvedic & OTC"
        ),
        HsnEntry(
            hsnCode = "3924",
            description = "Gym shaker bottle, gallon jug & pill organizer box",
            keywords = "shaker bottle, protein shaker, gym shaker, spider shaker, cyclone shaker, steel gym shaker, gallon water bottle, pill box, supplement container",
            gstRate = 18.0,
            category = "Fitness Accessories"
        )
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 2. KIRANA / KRIYANA & GENERAL PROVISION STORE
    // ═════════════════════════════════════════════════════════════════════════
    private fun kiranaEntries(): List<HsnEntry> = listOf(
        // Millets, Flours & Dry Grains
        HsnEntry(
            hsnCode = "1008",
            description = "Millets (Ragi, Jowar, Bajra, Kangni, Kutki, Kodo, Sabudana/Buckwheat)",
            keywords = "millet, millets, ragi, nachni, finger millet, jowar, sorghum, bajra, pearl millet, foxtail millet, kangni, little millet, kutki, kodo millet, barnyard millet, sanwa, kuttu, buckwheat, singhara flour",
            gstRate = 0.0,
            category = "Grains & Flours"
        ),
        HsnEntry(
            hsnCode = "1101",
            description = "Atta, Maida, Sooji, Besan & Milling Flours",
            keywords = "wheat flour, chakki atta, sharbati atta, multigrain atta, aashirvaad atta, fortune atta, pillsbury atta, patanjali atta, rajdhani besan, fortune besan, gram flour, besan, maida, refined flour, sooji, suji, rava, semolina, bombay rava, idli rava, rice flour, chawal ka atta, makki atta, corn flour, kuttu atta, ragi flour",
            gstRate = 0.0,
            category = "Grains & Flours"
        ),
        HsnEntry(
            hsnCode = "1904",
            description = "Poha, Murmura, Daliya, Oats, Cornflakes & Muesli",
            keywords = "poha, flattened rice, chivda, murmura, puffed rice, lai, kheel, daliya, broken wheat, rolled oats, steel cut oats, quaker oats, saffola oats, bagrrys oats, muesli, granola, kelloggs corn flakes, chocos, sabudana, sago, makhanas, fox nuts",
            gstRate = 5.0,
            category = "Breakfast & Cereals"
        ),
        HsnEntry(
            hsnCode = "1902",
            description = "Vermicelli, Seviyan, Macaroni & Pasta",
            keywords = "vermicelli, seviyan, bambino vermicelli, bambino, mtr vermicelli, roasted vermicelli, macaroni, penne pasta, fusilli pasta, spaghetti, disano pasta, weikfield pasta",
            gstRate = 5.0,
            category = "Noodles & Pasta"
        ),
        // Whole & Blended Spices (complete)
        HsnEntry(
            hsnCode = "0908",
            description = "Cardamom, Nutmeg & Mace (Elaichi, Jaiphal, Javitri)",
            keywords = "elaichi, choti elaichi, green cardamom, badi elaichi, black cardamom, jaiphal, nutmeg, javitri, mace",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        HsnEntry(
            hsnCode = "0906",
            description = "Cinnamon & Cassia (Dalchini)",
            keywords = "dalchini, cinnamon, cinnamon stick, cassia bark, tez patta, bay leaf, tej patta",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        HsnEntry(
            hsnCode = "0907",
            description = "Cloves (Laung)",
            keywords = "laung, cloves, whole clove, lavang",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        HsnEntry(
            hsnCode = "0904",
            description = "Black Pepper & Red Chilli (Kali Mirch, Lal Mirch)",
            keywords = "kali mirch, black pepper, gol mirch, safed mirch, white pepper, lal mirch, kashmiri mirch, degi mirch, red chilli powder, whole red chilli, sabut mirch, chilli flakes",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        HsnEntry(
            hsnCode = "0909",
            description = "Cumin, Coriander, Fennel, Ajwain & Kalonji (Jeera, Dhania, Saunf)",
            keywords = "jeera, cumin seeds, shahi jeera, dhania, coriander seeds, dhania powder, saunf, fennel seeds, ajwain, carom seeds, methi dana, fenugreek seeds, rai, mustard seeds, sarson, kalonji, nigella seeds, chakri phool, star anise, poppy seeds, khus khus, posta",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        HsnEntry(
            hsnCode = "0910",
            description = "Turmeric, Hing, Blended Masalas & Seasoning (MDH, Everest, Catch)",
            keywords = "haldi, turmeric powder, hing, asafoetida, everest hing, lg hing, garam masala, kitchen king, chana masala, chole masala, pav bhaji masala, sambar powder, rasam powder, biryani masala, meat masala, chicken masala, fish curry masala, chaat masala, jaljeera, pani puri masala, sabji masala, kasuri methi, amchoor, dry mango powder, mdh, everest, catch masala, badshah masala, suhana, ramdev masala, goldiee, tata sampann masala, oregano, mixed herbs, black salt, kala namak, sendha namak",
            gstRate = 5.0,
            category = "Spices & Masalas"
        ),
        // Condiments, Pickles, Sauces, Honey, Jams, Papad, Soy chunks
        HsnEntry(
            hsnCode = "2103",
            description = "Tomato Ketchup, Sauces, Mayonnaise, Soy & Chilli Sauce",
            keywords = "tomato ketchup, ketchup, tomato sauce, kissan ketchup, maggi hot and sweet, heinz ketchup, soy sauce, red chilli sauce, green chilli sauce, schezwan chutney, chings, ching's secret, mayonnaise, veg mayo, veeba, funfoods, mustard sauce, pizza pasta sauce, momos chutney",
            gstRate = 5.0,
            category = "Sauces & Condiments"
        ),
        HsnEntry(
            hsnCode = "2001",
            description = "Pickles / Achar & Vinegar (Mother's Recipe, Priya, Pachranga)",
            keywords = "achar, pickle, mango pickle, aam ka achar, lemon pickle, nimbu achar, mixed pickle, chilli pickle, mirchi achar, garlic pickle, mother's recipe, mothers recipe, nilons, priya pickle, pachranga, tops pickle, white vinegar, sirka, apple vinegar",
            gstRate = 5.0,
            category = "Pickles & Preserves"
        ),
        HsnEntry(
            hsnCode = "2007",
            description = "Fruit Jam, Marmalade & Murabba (Kissan, Tops)",
            keywords = "jam, mixed fruit jam, strawberry jam, mango jam, kissan jam, tops jam, orange marmalade, amla murabba, gulkand",
            gstRate = 5.0,
            category = "Pickles & Preserves"
        ),
        HsnEntry(
            hsnCode = "0409",
            description = "Natural Honey (Dabur, Patanjali, Saffola, Apis)",
            keywords = "honey, shahad, dabur honey, patanjali honey, saffola honey, apis honey, hitkari honey, organic wild honey, sundarban honey",
            gstRate = 5.0,
            category = "Groceries & Staples"
        ),
        HsnEntry(
            hsnCode = "1905",
            description = "Papad, Khakhra & Fryums (Lijjat Papad)",
            keywords = "papad, lijjat papad, moong papad, urad papad, punjabi masala papad, rice papad, sabudana papad, khakhra, fryums, kachri",
            gstRate = 0.0,
            category = "Groceries & Staples"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Soya chunks / Soya vadi / Nutrela",
            keywords = "soya chunks, soya vadi, soya badi, nutrela, fortune soya chunks, mini soya chunks, soya granules",
            gstRate = 5.0,
            category = "Groceries & Staples"
        ),
        HsnEntry(
            hsnCode = "1207",
            description = "Oilseeds, Sesame, Flax, Chia, Pumpkin & Watermelon Seeds",
            keywords = "til, sesame seeds, safed til, kala til, alsi, flax seeds, chia seeds, pumpkin seeds, sunflower seeds, melon seeds, magaz, charmagaz, kharbuj beej, dry coconut, nariyal gola, kopra",
            gstRate = 5.0,
            category = "Dry Fruits & Seeds"
        ),
        HsnEntry(
            hsnCode = "1901",
            description = "Custard Powder, Cornflour, Baking Powder, Cake Mix & Gulab Jamun Mix",
            keywords = "custard powder, weikfield custard, baking powder, baking soda, meetha soda, cooking soda, instant dry yeast, gelatin, agar agar, gulab jamun mix, gits gulab jamun, mtr gulab jamun, idli mix, dosa mix, dhokla mix, khaman mix, cocoa powder, drinking chocolate, vanilla essence",
            gstRate = 5.0,
            category = "Baking & Instant Mixes"
        ),
        // Feminine Hygiene, Baby Diapers, Disposables, Cleaning & Houseware
        HsnEntry(
            hsnCode = "9619",
            description = "Sanitary napkins / Sanitary pads (0% GST exempt in India)",
            keywords = "sanitary pad, sanitary pads, sanitary napkin, whisper, whisper ultra, stayfree, stayfree secure, sofy, nua, carmesi, panty liner, tampon, menstrual pad",
            gstRate = 0.0,
            category = "Hygiene & Personal Care"
        ),
        HsnEntry(
            hsnCode = "9619",
            description = "Baby diapers, pant diapers & adult diapers (Pampers, MamyPoko, Huggies)",
            keywords = "baby diaper, diapers, pant diaper, pampers, mamypoko pants, mamy poko, huggies, teddyy diaper, adult diaper, friends adult diaper, wet wipes, baby wipes",
            gstRate = 5.0,
            category = "Hygiene & Baby Care"
        ),
        HsnEntry(
            hsnCode = "9603",
            description = "Broom sticks / Phool jhadu / Coconut broom (0% GST)",
            keywords = "jhadu, broom, phool jhadu, grass broom, coconut broom, seekh jhadu, nariyal jhadu, gala broom, monkey broom",
            gstRate = 0.0,
            category = "Home Care & Cleaning"
        ),
        HsnEntry(
            hsnCode = "9603",
            description = "Floor mop, wiper, toilet brush, scrub pad & cleaning brushes",
            keywords = "floor mop, spin mop, gala mop, scotch brite mop, floor wiper, bathroom wiper, toilet brush, cloth brush, scrub pad, scotch brite, green scrubber, steel wool, juna, dish scrubber",
            gstRate = 18.0,
            category = "Home Care & Cleaning"
        ),
        HsnEntry(
            hsnCode = "3808",
            description = "Phenyl, Toilet Cleaner, Mosquito Coil, Liquid Vaporizer & Disinfectants",
            keywords = "phenyl, black phenyl, white phenyl, gainda phenyl, lizol, harpic, toilet cleaner, domex, colin, glass cleaner, dettol disinfectant, savlon, mortein, good knight, goodknight, all out, hit spray, kala hit, laal hit, mosquito coil, mosquito liquid, odonil, air freshener, room freshener, naphthalene balls, kapoor goli, rat kill, mortein rat kill",
            gstRate = 18.0,
            category = "Home Care & Cleaning"
        ),
        HsnEntry(
            hsnCode = "7607",
            description = "Aluminium foil & food wrapping foil (Freshwrapp)",
            keywords = "aluminium foil, aluminum foil, freshwrapp, hindalco foil, food foil, silver foil, roti foil",
            gstRate = 18.0,
            category = "Disposables & Packaging"
        ),
        HsnEntry(
            hsnCode = "4818",
            description = "Tissue paper, paper napkins, toilet roll & kitchen roll",
            keywords = "tissue paper, paper napkin, facial tissue, toilet roll, toilet paper, kitchen towel, origami tissue, butter paper, parchment paper",
            gstRate = 18.0,
            category = "Disposables & Packaging"
        ),
        HsnEntry(
            hsnCode = "4823",
            description = "Disposable paper plates, dona, paper cups & cling wrap",
            keywords = "paper cup, disposable cup, paper plate, dona, pattal, thermocol plate, disposable spoon, garbage bag, trash bag, dustbin bag, cling wrap, food wrap",
            gstRate = 18.0,
            category = "Disposables & Packaging"
        ),
        HsnEntry(
            hsnCode = "3605",
            description = "Safety matches / Matchbox (Machis, Aim, Homelite, Ship)",
            keywords = "matchbox, match box, safety matches, machis, aim matchbox, homelite, ship matchbox",
            gstRate = 5.0,
            category = "Kirana Utilities"
        ),
        HsnEntry(
            hsnCode = "9613",
            description = "Gas lighter & pocket cigarette lighter",
            keywords = "gas lighter, kitchen lighter, cigarette lighter, pocket lighter, bic lighter, clipper lighter",
            gstRate = 18.0,
            category = "Kirana Utilities"
        ),
        // Extended Personal Care & Grooming
        HsnEntry(
            hsnCode = "3304",
            description = "Face wash, moisturizer, cold cream, sunscreen, talcum powder & lip balm",
            keywords = "face wash, himalaya neem face wash, garnier face wash, ponds face wash, clean and clear, cetaphil, moisturizer, nivea cream, ponds cold cream, boroplus, boroline, vaseline petroleum jelly, lotion, body lotion, sunscreen, lakme sun expert, talcum powder, ponds powder, dermicool, navratna powder, nycil, lip balm, nivea lip balm, kajal, sindoor, bindi",
            gstRate = 18.0,
            category = "Personal Care"
        ),
        HsnEntry(
            hsnCode = "3305",
            description = "Hair oil, hair dye, mehndi, hair serum & conditioner (Indulekha, Livon, Garnier)",
            keywords = "parchute hair oil, coconut hair oil, almond drops, bajaj almond drops, navratna oil, kesh king, indulekha, amla hair oil, dabur amla, sarson amla, hair conditioner, hair serum, livon serum, hair color, hair dye, garnier black naturals, godrej expert, godrej nupur mehndi, heena, loreal hair color",
            gstRate = 18.0,
            category = "Personal Care"
        ),
        HsnEntry(
            hsnCode = "3307",
            description = "Deodorant, perfume, aftershave, shaving foam & room spray",
            keywords = "deodorant, deo, body spray, fogg, axe, wild stone, engage, denver, bella vita, perfume, ittar, shaving cream, shaving foam, gillette foam, old spice, aftershave lotion, park avenue deo",
            gstRate = 18.0,
            category = "Personal Care"
        ),
        HsnEntry(
            hsnCode = "8212",
            description = "Shaving razors & razor blades (Gillette Mach3, Guard, 7 O'Clock)",
            keywords = "razor, shaving razor, razor blade, gillette guard, gillette mach3, mach 3, gillette fusion, 7 o clock blade, wilkinson sword, supermax blade, shaving cartridge",
            gstRate = 18.0,
            category = "Personal Care"
        ),
        // Extended Puja Samagri & Pet Food
        HsnEntry(
            hsnCode = "3307",
            description = "Pooja agarbatti, dhoopbatti, camphor (kapoor), loban & hawan samagri",
            keywords = "agarbatti, incense stick, mangaldeep, cycle agarbatti, zed black, moksh, dhoop, dhoop batti, dhoop cone, loban, sambrani cup, hawan samagri, kapoor, camphor, bhimseni kapoor, pooja oil, til oil puja",
            gstRate = 5.0,
            category = "Puja Samagri"
        ),
        HsnEntry(
            hsnCode = "1404",
            description = "Pooja roli, kumkum, chandan, kalava mauli, cotton batti & ganga jal",
            keywords = "roli, kumkum, chandan tika, ashtagandha, mauli, kalava, raksha sutra, janeu, cotton batti, rui batti, gol batti, ganga jal, diya, mitti diya, supari pooja, laung elaichi puja, nariyal pooja",
            gstRate = 0.0,
            category = "Puja Samagri"
        ),
        HsnEntry(
            hsnCode = "2309",
            description = "Pet food for dogs and cats (Pedigree, Whiskas, Drools)",
            keywords = "pet food, dog food, cat food, pedigree, drools, royal canin, whiskas, me-o, purepet, chappi, puppy food, dog treats",
            gstRate = 18.0,
            category = "Pet Care"
        ),
        HsnEntry(
            hsnCode = "2106",
            description = "Pan masala, supari, mouth freshener & mukhwas (Pass Pass, Rajnigandha)",
            keywords = "pan masala, paan masala, rajnigandha, vimal, kamla pasand, shikhar, dilbagh, supari, sweet supari, pass pass, mukhwas, silver coated elaichi, tulsi pan masala",
            gstRate = 40.0,
            category = "Tobacco & Pan Masala"
        ),
        HsnEntry(
            hsnCode = "2402",
            description = "Cigarettes, bidi & tobacco products (Gold Flake, Classic, Wills)",
            keywords = "cigarette, cigarettes, gold flake, classic regular, classic milds, marlboro, wills navy cut, four square, total cigarette, bidi, beedi, zarda, khaini, gutkha",
            gstRate = 40.0,
            category = "Tobacco & Pan Masala"
        )
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 3. ELECTRICAL STORE (Wires, Switchgear, Lighting, Fans, UPS, Conduits)
    // ═════════════════════════════════════════════════════════════════════════
    private fun electricalEntries(): List<HsnEntry> = listOf(
        // Wires & Cables (8544)
        HsnEntry(
            hsnCode = "8544",
            description = "House wire FR / FRLS / ZHFR PVC insulated copper wire coil",
            keywords = "house wire, electric wire, copper wire, pvc wire, fr wire, frls wire, zhfr wire, 0.75 sq mm, 1 sq mm, 1.5 sq mm, 2.5 sq mm, 4 sq mm, 6 sq mm, 10 sq mm, wire coil, 90m wire, 180m wire, finolex, finolex wire, polycab, polycab wire, havells wire, rr kabel, rr kabel wire, kei wire, anchor wire, v-guard wire, plaza wire, bijli taar, taar, multi strand wire",
            gstRate = 18.0,
            category = "Electrical Wires & Cables"
        ),
        HsnEntry(
            hsnCode = "8544",
            description = "Submersible flat cable, armoured power cable & flexible multicore cable",
            keywords = "submersible cable, 3 core flat cable, armoured cable, xlpe cable, service wire, aluminium cable, aluminium service wire, 2 core flexible wire, 3 core cable, 4 core cable, multicore cable, welding cable, solar dc cable, polycab cable, finolex cable, kei cable, rr kabel",
            gstRate = 18.0,
            category = "Electrical Wires & Cables"
        ),
        // Modular Switches, Sockets, Plates & Holders (8536)
        HsnEntry(
            hsnCode = "8536",
            description = "Modular switch, 1-way / 2-way / 6A / 16A power switch & bell push",
            keywords = "modular switch, electric switch, 6a switch, 16a switch, 20a switch, 25a ac switch, 32a dp switch, 2 way switch, bell push, indicator switch, fan regulator, step fan regulator, dimmer switch, anchor roma, anchor penta, legrand myrius, legrand arteor, schneider opale, schneider livia, crabtree athena, goldmedal curve, gm fourfive, cona, greatwhite",
            gstRate = 18.0,
            category = "Switches & Accessories"
        ),
        HsnEntry(
            hsnCode = "8536",
            description = "Electrical socket, combined box, modular plate & plug top",
            keywords = "5 pin socket, 6a socket, 16a power socket, 25a socket, multi socket, usb socket, modular plate, 1 module plate, 2m plate, 3m plate, 4m plate, 6m plate, 8m plate, 12m plate, gang box, combined box, ssr box, 2 pin plug top, 3 pin plug top, 16a plug top, multi plug adapter, travel adapter, ceiling rose, batten bulb holder, angle holder, pendant holder, pin holder, calling bell, door bell, ding dong bell, wireless doorbell",
            gstRate = 18.0,
            category = "Switches & Accessories"
        ),
        HsnEntry(
            hsnCode = "8537",
            description = "Extension board, spike guard, surge protector & power strip",
            keywords = "extension board, extension cord, spike guard, surge protector, power strip, 4 socket extension, heavy duty extension box, belkin surge protector, gm extension, anchor extension, wipro extension, goldmedal spike guard",
            gstRate = 18.0,
            category = "Switches & Accessories"
        ),
        // Switchgear & Distribution (8536 / 8537 / 8532)
        HsnEntry(
            hsnCode = "8536",
            description = "MCB, RCCB, ELCB, RCBO, Isolator & Changeover Switch",
            keywords = "mcb, miniature circuit breaker, sp mcb, dp mcb, tp mcb, fp mcb, tpn mcb, 6a mcb, 10a mcb, 16a mcb, 32a mcb, 40a mcb, 63a mcb, rccb, elcb, rcbo, mccb, dp isolator, main switch, changeover switch, manual changeover, automatic changeover, kitkat fuse, hrc fuse, havells mcb, l&t mcb, schneider acti9, legrand dx3, siemens mcb, abb mcb, c&s mcb",
            gstRate = 18.0,
            category = "Switchgear & Protection"
        ),
        HsnEntry(
            hsnCode = "8537",
            description = "Distribution board (DB box), MCB enclosure & motor starter panel",
            keywords = "distribution board, db box, mcb box, spn db, tpn db, 4 way db, 8 way db, 12 way db, double door db, single door db, metal clad socket box, ac metal box, motor starter, dol starter, star delta starter, submersible starter panel, water level controller, digital meter panel, contactor, overload relay, l&t starter, bentec starter, havells db",
            gstRate = 18.0,
            category = "Switchgear & Protection"
        ),
        HsnEntry(
            hsnCode = "8532",
            description = "Ceiling fan capacitor & submersible motor condenser (Tibcon, Keltron)",
            keywords = "fan capacitor, condenser, 2.5 mfd capacitor, 3.15 mfd capacitor, 4 mfd capacitor, motor start capacitor, motor run capacitor, 36 mfd capacitor, 50 mfd capacitor, 72 mfd capacitor, tibcon, epcos, keltron, havells capacitor",
            gstRate = 18.0,
            category = "Switchgear & Protection"
        ),
        // LED Lighting & Fixtures (8539 / 9405)
        HsnEntry(
            hsnCode = "8539",
            description = "LED bulb, Inverter rechargeable bulb & zero-watt night lamp",
            keywords = "led bulb, 9w led bulb, 12w led bulb, 15w led bulb, 18w led bulb, 20w led bulb, 30w led bulb, 40w led bulb, 50w high wattage bulb, inverter bulb, rechargeable led bulb, emergency bulb, zero watt bulb, night bulb, candle bulb, filament bulb, smart wifi led bulb, philips led, syska led, wipro garnet, surya led, crompton led, orient led, halonix bulb, eveready led, havells bulb, bajaj led",
            gstRate = 18.0,
            category = "LED Lighting"
        ),
        HsnEntry(
            hsnCode = "9405",
            description = "LED tube light, T5/T8 batten, LED panel light, downlight & COB spotlight",
            keywords = "led batten, led tube light, 20w tube light, t5 batten, t8 tube, led panel light, ceiling panel light, slim panel light, surface panel light, led downlight, 6w downlight, 12w panel, 15w panel, 18w panel, cob light, cob spotlight, track light, magnetic track light, led choke, led driver, tube starter, philips batten, wipro panel, syska panel, havells lighting, jaquar lighting",
            gstRate = 18.0,
            category = "LED Lighting"
        ),
        HsnEntry(
            hsnCode = "9405",
            description = "LED flood light, street light, strip light, rope light & decorative lamp",
            keywords = "led flood light, 50w flood light, 100w flood light, 150w flood light, 200w flood light, led street light, high bay light, led strip light, rgb strip light, profile light, neon rope light, smps 12v driver, gate light, pillar light, wall sconce, chandelier, jhoomar, hanging pendant light, diwali jhalar, fairy light, rechargeable torch, emergency lantern",
            gstRate = 18.0,
            category = "LED Lighting"
        ),
        // Fans & Ventilation (8414)
        HsnEntry(
            hsnCode = "8414",
            description = "Ceiling fan, BLDC energy-saving fan, table, pedestal & exhaust fan",
            keywords = "ceiling fan, bldc fan, bldc ceiling fan, energy saving fan, remote ceiling fan, high speed fan, 1200mm fan, pedestal fan, stand fan, farata fan, table fan, wall fan, exhaust fan, ventilation fan, kitchen chimney fan, bathroom exhaust, atomberg, atomberg renesa, gorilla fan, crompton fan, havells fan, orient fan, usha fan, bajaj fan, luminous fan, kuhl fan, pankha",
            gstRate = 18.0,
            category = "Fans & Cooling"
        ),
        HsnEntry(
            hsnCode = "8479",
            description = "Air cooler, desert cooler & cooler motor / pump (Symphony, Kenstar, Bajaj)",
            keywords = "air cooler, desert cooler, room cooler, tower cooler, personal cooler, symphony cooler, kenstar cooler, bajaj cooler, crompton cooler, orient cooler, cooler water pump, cooler motor, cooler honeycomb pad",
            gstRate = 18.0,
            category = "Fans & Cooling"
        ),
        // Inverters, Stabilizers & Batteries (8504 / 8507)
        HsnEntry(
            hsnCode = "8504",
            description = "Home inverter, Sine wave UPS, Online UPS & Voltage Stabilizer",
            keywords = "inverter, home inverter, pure sine wave inverter, ups inverter, luminous inverter, luminous zelio, luminous eco watt, microtek inverter, v-guard inverter, livguard inverter, exide inverter, solar inverter, mppt solar charge controller, voltage stabilizer, ac stabilizer, 4kva stabilizer, 5kva stabilizer, mainline stabilizer, fridge stabilizer, tv stabilizer, servo stabilizer, v-guard stabilizer, microtek stabilizer, bluebird stabilizer, transformer, step down transformer",
            gstRate = 18.0,
            category = "Power Backup & Solar"
        ),
        HsnEntry(
            hsnCode = "8507",
            description = "Tubular inverter battery, SMF UPS battery & dry cell batteries",
            keywords = "inverter battery, tubular battery, tall tubular battery, 150ah battery, 180ah battery, 200ah battery, 220ah battery, smf battery, vrla battery, 12v 7ah ups battery, lithium inverter battery, solar battery, luminous battery, exide invamaster, exide invatubular, amaron current, livguard battery, okaya battery, duracell aa, aaa battery, 9v battery, eveready battery, nippo cell, cr2032 button cell",
            gstRate = 18.0,
            category = "Batteries & Power"
        ),
        // Electrical Heating & Small Appliances (8516)
        HsnEntry(
            hsnCode = "8516",
            description = "Geyser, Immersion water heater rod, Room heater & Electric iron",
            keywords = "geyser, electric water heater, storage geyser, instant geyser, 15 ltr geyser, 25 ltr geyser, racold geyser, ao smith geyser, venus geyser, bajaj geyser, havells geyser, crompton geyser, immersion rod, water heating rod, geyser heating element, thermostat, room heater, halogen heater, oil filled heater, fan blower heater, dry iron, steam iron, press, philips iron, bajaj iron",
            gstRate = 18.0,
            category = "Home Appliances"
        ),
        HsnEntry(
            hsnCode = "8509",
            description = "Mixer grinder, Juicer, Hand blender & Food processor",
            keywords = "mixer grinder, mixie, juicer mixer grinder, sujata mixer, preethi zodiac, philips mixer, bajaj mixer, butterfly mixer, hand blender, chopper, wet grinder",
            gstRate = 18.0,
            category = "Kitchen Appliances"
        ),
        // Conduits, Enclosures, Tape, Earthing & Electrical Tools
        HsnEntry(
            hsnCode = "3917",
            description = "PVC electrical conduit pipe, flexible corrugated pipe & casing-capping",
            keywords = "pvc conduit pipe, electrical pvc pipe, 20mm pvc pipe, 25mm conduit pipe, 32mm conduit, flexible conduit pipe, corrugated pipe, pvc casing capping, casing pati, pvc bend, inspection elbow, pvc tee, coupler socket, junction box, circular box, deep junction box, fan box, concealed switch box, modular metal box",
            gstRate = 18.0,
            category = "Conduits & Wiring Materials"
        ),
        HsnEntry(
            hsnCode = "3919",
            description = "PVC insulation tape & cable ties (Steelgrip, Anchor)",
            keywords = "pvc insulation tape, electric tape, steelgrip, steel grip tape, anchor insulation tape, black electrical tape, teflon tape, nylon cable tie, zip tie",
            gstRate = 18.0,
            category = "Conduits & Wiring Materials"
        ),
        HsnEntry(
            hsnCode = "8535",
            description = "Copper earthing rod, earthing plate, chemical earthing & lightning arrester",
            keywords = "earthing rod, copper bonded rod, copper earthing plate, gi earthing plate, chemical earthing compound, backfill compound, lightning arrester, surge protection device, spd, earth pit cover, copper lug, cable gland",
            gstRate = 18.0,
            category = "Earthing & Heavy Electrical"
        ),
        HsnEntry(
            hsnCode = "9030",
            description = "Digital multimeter, clamp meter, line tester & kWh energy meter",
            keywords = "digital multimeter, clamp meter, ac dc multimeter, continuity tester, neon line tester, screwdriver tester, submeter, kwh energy meter, digital voltmeter, ampere meter, fluke multimeter, meco meter, mastech",
            gstRate = 18.0,
            category = "Electrical Testing & Tools"
        ),
        HsnEntry(
            hsnCode = "8515",
            description = "Soldering iron, solder wire, flux paste & desoldering pump",
            keywords = "soldering iron, 25w soldering iron, 60w soldering iron, soldering station, solder wire, solder lead, flux paste, rosin flux, desoldering pump, soldron, hot air rework station, hot glue gun",
            gstRate = 18.0,
            category = "Electrical Testing & Tools"
        ),
        HsnEntry(
            hsnCode = "8413",
            description = "Submersible borewell pump, monoblock water pump & booster pump",
            keywords = "submersible pump, borewell pump, openwell pump, monoblock water pump, centrifugal water pump, pressure booster pump, tullu pump, kirloskar pump, crompton water pump, texmo pump, cri pump, ksb pump, v-guard pump",
            gstRate = 5.0,
            category = "Pumps & Motors"
        )
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 4. ELECTRONICS, IT, COMPUTER HARDWARE, NETWORKING, CCTV & OFFICE
    // ═════════════════════════════════════════════════════════════════════════
    private fun electronicsEntries(): List<HsnEntry> = listOf(
        HsnEntry(
            hsnCode = "8471",
            description = "Laptop, Desktop PC, All-in-One PC, Tablet & Mini PC",
            keywords = "laptop, gaming laptop, business laptop, macbook air, macbook pro, desktop computer, cpu cabinet pc, all in one pc, mini pc, tablet, ipad, samsung galaxy tab, lenovo tab, xiaomi pad, hp laptop, dell laptop, lenovo thinkpad, ideapad, asus vivobook, rog, acer aspire, nitro, msi laptop",
            gstRate = 18.0,
            category = "Computers & Tablets"
        ),
        HsnEntry(
            hsnCode = "8471",
            description = "SSD, Hard Disk (HDD), Pen Drive, MicroSD Card & RAM memory",
            keywords = "ssd, nvme ssd, m.2 ssd, sata ssd, internal ssd, portable ssd, external hard disk, external hdd, 1tb hdd, 2tb hard drive, pen drive, flash drive, otg pendrive, memory card, microsd card, sd card, ram, ddr4 ram, ddr5 ram, laptop ram, desktop ram, sandisk, wd green, wd blue, wd black, seagate, crucial, kingston, samsung evo, corsair vengeance, xpg",
            gstRate = 18.0,
            category = "Computer Storage & Memory"
        ),
        HsnEntry(
            hsnCode = "8473",
            description = "Motherboard, Processor (CPU), Graphics Card (GPU), SMPS & Cabinet",
            keywords = "motherboard, intel core i5, intel core i7, amd ryzen, processor cpu, graphics card, gpu, nvidia rtx, geforce, radeon, smps, pc power supply, 550w psu, cpu cooler, liquid cooler, thermal paste, pc cabinet, computer cabinet, gigabyte, msi motherboard, asrock, asus motherboard, ant esports, cooler master",
            gstRate = 18.0,
            category = "Computer Components"
        ),
        HsnEntry(
            hsnCode = "8471",
            description = "Computer Keyboard, Mouse, Webcam, Card Reader & Barcode Scanner",
            keywords = "keyboard, mechanical keyboard, wireless keyboard, computer mouse, wireless mouse, gaming mouse, keyboard mouse combo, mousepad, webcam, hd webcam, card reader, fingerprint scanner, biometric mantra, barcode scanner, bluetooth dongle, usb wifi adapter, logitech, hp mouse, dell keyboard, zebronics, portronics, cosmic byte, kreo, razer",
            gstRate = 18.0,
            category = "Computer Peripherals"
        ),
        HsnEntry(
            hsnCode = "8528",
            description = "Computer Monitor, Gaming Monitor, Smart LED TV & Projector",
            keywords = "computer monitor, led monitor, ips monitor, 24 inch monitor, 27 inch monitor, gaming monitor, curved monitor, lg ultragear, benq monitor, dell monitor, samsung monitor, smart tv, android tv, google tv, qled tv, oled tv, 32 inch tv, 43 inch tv, 55 inch tv, sony bravia, lg tv, mi tv, tcl tv, hisense tv, projector, smart projector, epson projector",
            gstRate = 18.0,
            category = "Monitors, TVs & Displays"
        ),
        HsnEntry(
            hsnCode = "8443",
            description = "Printer, Thermal Billing Printer, Scanner, Lamination & Toner Cartridge",
            keywords = "printer, inktank printer, inkjet printer, laser printer, all in one printer, thermal printer, pos receipt printer, 3 inch billing printer, barcode label printer, flatbed scanner, document scanner, epson ecotank, l3250, hp smart tank, hp laserjet, canon pixma, g2010, brother printer, tvs printer, toner cartridge, 12a toner, 88a cartridge, drum unit",
            gstRate = 18.0,
            category = "Printers & Office Automation"
        ),
        HsnEntry(
            hsnCode = "3215",
            description = "Printer ink bottle & printer ink cartridge",
            keywords = "printer ink, ink bottle, epson 003 ink, canon gi790 ink, hp gt53 ink, hp gt52 ink, ink cartridge, black cyan magenta yellow ink, refill toner powder",
            gstRate = 18.0,
            category = "Printers & Office Automation"
        ),
        HsnEntry(
            hsnCode = "8470",
            description = "Calculator, Cash Register & Note Counting Machine",
            keywords = "calculator, scientific calculator, desktop calculator, casio fx991, casio mj12d, citizen calculator, orpat calculator, note counting machine, fake note detector, billing pos machine",
            gstRate = 18.0,
            category = "Office Electronics"
        ),
        // Networking & Security (8517, 8525, 8521, 8543)
        HsnEntry(
            hsnCode = "8517",
            description = "Wi-Fi Router, Mesh System, Fiber ONT, Network Switch & 4G/5G Dongle",
            keywords = "wifi router, wireless router, dual band router, wifi 6 router, mesh wifi, deco, network switch, gigabit switch, poe switch, rj45 switch, fiber ont router, broadband modem, wifi range extender, repeater, 4g dongle, 5g hotspot, jiofi, tp-link, tplink, d-link, dlink, tenda, netgear, mercusys, linksys, cisco",
            gstRate = 18.0,
            category = "Networking & Telecom"
        ),
        HsnEntry(
            hsnCode = "8525",
            description = "CCTV Camera (Dome, Bullet, PTZ, IP Wi-Fi) & Video Door Phone",
            keywords = "cctv camera, dome camera, bullet camera, ptz camera, ip camera, wifi cctv camera, hd 2mp camera, 5mp cctv, night vision camera, colorvu camera, video door phone, vdp, intercom, cp plus, hikvision, dahua, imou, ezviz, qubo, tapo camera",
            gstRate = 18.0,
            category = "Security & Surveillance"
        ),
        HsnEntry(
            hsnCode = "8521",
            description = "CCTV DVR, NVR, Media Player, Fire TV Stick & Set-Top Box",
            keywords = "dvr, nvr, 4ch dvr, 8ch dvr, 16ch dvr, hikvision dvr, cp plus dvr, android tv box, fire tv stick, chromecast, mi tv stick, tata play set top box, airtel dth, hdmi media player",
            gstRate = 18.0,
            category = "Security & Surveillance"
        ),
        HsnEntry(
            hsnCode = "8543",
            description = "Biometric Attendance Machine, GPS Tracker, Mosquito Bat & Smart Plug",
            keywords = "biometric machine, attendance machine, fingerprint time attendance, rfid card reader, door access control, essl, realtime biometric, gps tracker, car gps, smart plug, wifi smart socket, mosquito racket, electric mosquito bat, voltage spike filter",
            gstRate = 18.0,
            category = "Security & Electronics"
        ),
        // Data Cables, Adapters, Chargers, Audio & Mobile Accessories
        HsnEntry(
            hsnCode = "8544",
            description = "HDMI, VGA, DisplayPort, LAN Cat6 Ethernet, USB-C & AUX Cables",
            keywords = "hdmi cable, 4k hdmi, vga cable, displayport cable, lan cable, cat6 ethernet cable, cat5e cable, patch cord, 3+1 cctv cable, rg6 coaxial cable, type c cable, usb c cable, lightning cable, iphone cable, micro usb cable, braided data cable, aux cable, otg cable, rj45 connector",
            gstRate = 18.0,
            category = "Cables & Connectors"
        ),
        HsnEntry(
            hsnCode = "8504",
            description = "GaN Fast Charger, Laptop Adapter, Car Charger & Computer UPS",
            keywords = "gan charger, fast charger, 20w usb c charger, 33w charger, 65w charger, 100w charger, samsung 25w adapter, apple 20w adapter, car mobile charger, laptop adapter, 65w laptop charger, cctv power supply, 12v 2a adapter, 12v 5a smps, computer ups, apc ups 600va, numeric ups, microtek ups, wireless charger, magsafe charger, usb hub, type c dock",
            gstRate = 18.0,
            category = "Chargers & Power"
        ),
        HsnEntry(
            hsnCode = "8507",
            description = "Power bank & portable battery pack (Mi, Ambrane, Anker, Realme)",
            keywords = "power bank, powerbank, 10000mah power bank, 20000mah power bank, magsafe power bank, fast charging power bank, mi power bank, xiaomi power bank, ambrane, anker powercore, urbn, portronics, duracell power bank",
            gstRate = 18.0,
            category = "Mobile Accessories"
        ),
        HsnEntry(
            hsnCode = "7007",
            description = "Tempered glass mobile screen guard & camera lens protector",
            keywords = "tempered glass, screen guard, screen protector, matte glass, privacy tempered glass, uv curved glass, gorilla glass protector, camera lens protector, mobile glass, spigen glass",
            gstRate = 18.0,
            category = "Mobile Accessories"
        ),
        HsnEntry(
            hsnCode = "3926",
            description = "Mobile back cover, phone case, smartwatch strap & laptop stand",
            keywords = "mobile cover, phone back cover, silicone cover, magsafe case, armor cover, spigen case, mobile stand, desk phone holder, laptop stand, cooling pad, laptop sleeve, smartwatch strap, apple watch band",
            gstRate = 18.0,
            category = "Mobile Accessories"
        ),
        HsnEntry(
            hsnCode = "8518",
            description = "Bluetooth speaker, Soundbar, Home theatre, Amplifier & Microphone",
            keywords = "bluetooth speaker, portable speaker, party speaker, soundbar, home theatre, subwoofer, jbl flip, jbl charge, jbl partybox, marshall speaker, boat stone, sony ult, amplifier, condenser mic, collar mic, lavalier mic, wireless mic, boya mic, rode mic, fifine",
            gstRate = 18.0,
            category = "Audio Systems"
        ),
        HsnEntry(
            hsnCode = "9006",
            description = "Digital camera, DSLR, Action camera, Tripod, Ring light & Gimbal",
            keywords = "dslr camera, mirrorless camera, action camera, gopro, dji osmo, insta360, tripod stand, mobile tripod, ring light, gimbal stabilizer, camera lens, memory card reader, digitek, canon eos, nikon, sony alpha",
            gstRate = 18.0,
            category = "Cameras & Studio Gear"
        ),
        HsnEntry(
            hsnCode = "9504",
            description = "Gaming console, PlayStation, Xbox, Nintendo & Gamepad controller",
            keywords = "playstation, ps5, ps4, xbox series, nintendo switch, gaming controller, wireless gamepad, dualsense, steering wheel controller, cosmic byte controller, ant esports gamepad",
            gstRate = 18.0,
            category = "Gaming Consoles & Gear"
        )
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 5. AUTOMOBILE & TWO-WHEELER SPARE PARTS STORE (2W / 4W / Commercial)
    // ═════════════════════════════════════════════════════════════════════════
    private fun automobileSparePartsEntries(): List<HsnEntry> = listOf(
        // Engine & Fuel System (8409, 8413, 8483)
        HsnEntry(
            hsnCode = "8409",
            description = "Engine piston, piston rings, cylinder block kit, carburetor, valves & gaskets",
            keywords = "piston kit, piston ring, cylinder block kit, bore kit, engine valve, inlet valve, exhaust valve, valve seal, valve guide, rocker arm, tappet screw, gasket kit, full engine packing, head gasket, cylinder head gasket, carburetor, carburettor, throttle body, fuel injector, nozzle holder, connecting rod, kick shaft, goetze, mahle, usha piston, samkrg, mikuni, keihin, banco gasket",
            gstRate = 18.0,
            category = "Auto Engine Parts"
        ),
        HsnEntry(
            hsnCode = "8483",
            description = "Camshaft, crankshaft, timing chain kit, timing belt, gears & flywheel",
            keywords = "camshaft, crankshaft, timing chain, timing chain kit, timing belt, fan belt, v belt, serpentine belt, tensioner bearing, alternator belt, ac compressor belt, crank gear, oil pump gear, flywheel, ring gear, clutch bell, universal joint cross, u joint, propeller shaft, axle shaft, differential gear, bosch belt, gates belt, fenner belt, bando belt, contitech",
            gstRate = 18.0,
            category = "Auto Transmission & Engine"
        ),
        HsnEntry(
            hsnCode = "8413",
            description = "Automotive fuel pump, oil pump, water pump & power steering pump",
            keywords = "fuel pump, petrol fuel pump, fi fuel pump, high pressure diesel pump, engine oil pump, car water pump, coolant pump, power steering pump, washer pump, bosch fuel pump, denso pump, valeo pump",
            gstRate = 18.0,
            category = "Auto Engine Parts"
        ),
        // Filters (8421)
        HsnEntry(
            hsnCode = "8421",
            description = "Air filter, Oil filter, Fuel filter, Diesel filter & AC Cabin filter",
            keywords = "air filter, foam air filter, paper air filter, oil filter, lube filter, spin on filter, fuel filter, petrol filter, diesel filter, water separator filter, cabin filter, ac cabin filter, hydraulic filter, bosch filter, purolator, elofic, mahle filter, mann filter, fleetguard, donaldson, zip filter",
            gstRate = 18.0,
            category = "Auto Filters"
        ),
        // Brakes, Clutch & Chassis Parts (8708 Car/Truck, 8714 Two-Wheeler)
        HsnEntry(
            hsnCode = "8708",
            description = "Car & truck brake pad, brake shoe, disc rotor, caliper & master cylinder",
            keywords = "brake pad, car brake pad, disc brake pad, rear brake shoe, brake disc rotor, ventilated rotor, brake caliper, master cylinder, wheel cylinder, tandem master cylinder, brake booster, brake hose pipe, abs sensor, wheel speed sensor, handbrake cable, brake chamber, slack adjuster, brembo, bosch brake pad, tvs girling, brakes india, allied nippon, kbx",
            gstRate = 18.0,
            category = "Car & Commercial Spare Parts"
        ),
        HsnEntry(
            hsnCode = "8708",
            description = "Car clutch plate, pressure plate, clutch kit, flywheel & transmission parts",
            keywords = "car clutch plate, pressure plate, clutch cover assembly, clutch kit, clutch release bearing, csc concentric slave cylinder, clutch master cylinder, gearbox mounting, gear lever cable, cv joint, drive shaft, front axle, half shaft, hub assembly, wheel hub, differential carrier, valeo clutch, luk clutch, exedy clutch, sachs",
            gstRate = 18.0,
            category = "Car & Commercial Spare Parts"
        ),
        HsnEntry(
            hsnCode = "8708",
            description = "Car suspension, MacPherson strut, shock absorber, steering rack & bumper",
            keywords = "car shock absorber, macpherson strut, front strut, suspension arm, lower arm, control arm, ball joint, tie rod end, rack end, steering rack, stabilizer link, balance rod bush, steering column, radiator core, intercooler, car bumper, front bumper, rear bumper, fender liner, door handle, window regulator, monroe, gabriel, rane trw, talbros",
            gstRate = 18.0,
            category = "Car & Commercial Spare Parts"
        ),
        HsnEntry(
            hsnCode = "8714",
            description = "Two-wheeler clutch plate, chain sprocket kit, CVT belt, cables & brakes",
            keywords = "bike clutch plate, scooty clutch shoe, clutch center, clutch hub, clutch assembly, chain sprocket kit, chain set, roller drive chain, front sprocket, rear sprocket, cvt belt, activa drive belt, variator roller weight, clutch cable, accelerator cable, brake cable, speedometer cable, choke cable, bike brake pad, bike brake shoe, disc plate, brake lever, clutch lever, rolon chain, diamond chain, ask brake shoe, endurance, fcc, varroc",
            gstRate = 18.0,
            category = "Two-Wheeler Spare Parts"
        ),
        HsnEntry(
            hsnCode = "8714",
            description = "Two-wheeler shock absorber, front fork, cone set, silencer & body parts",
            keywords = "bike shock absorber, shocker, rear shocker, front shocker, splendor shocker, activa shocker, pulsar shocker, monoshock, front fork tube, fork T, cone set, steering ball racer kit, swingarm bush, silencer muffler, exhaust bend pipe, handlebar, hand grip, footrest, side stand, main stand, center stand, crash guard, leg guard, mudguard, visor, headlamp fairing, side panel, seat cover, speedometer meter, fuel tank cap, mudflap",
            gstRate = 18.0,
            category = "Two-Wheeler Spare Parts"
        ),
        // Bearings, Springs, Oil Seals, Glass, Mirrors & Locks
        HsnEntry(
            hsnCode = "8482",
            description = "Ball bearings, Wheel hub bearings, Taper roller & Clutch bearings",
            keywords = "ball bearing, 6201 bearing, 6202 bearing, 6203 bearing, 6301 bearing, 6302 bearing, 6304 bearing, 6004 bearing, hub bearing, wheel bearing, taper roller bearing, needle roller bearing, crankshaft bearing, skf bearing, fag bearing, nbc bearing, timken, koyo, ntn",
            gstRate = 18.0,
            category = "Bearings & Industrial"
        ),
        HsnEntry(
            hsnCode = "7320",
            description = "Automotive suspension coil spring, leaf spring & clutch spring",
            keywords = "coil spring, suspension spring, leaf spring, kamani patta, helper spring, main leaf, valve spring, clutch spring, stand spring, brake return spring, jamna auto",
            gstRate = 18.0,
            category = "Car & Commercial Spare Parts"
        ),
        HsnEntry(
            hsnCode = "4016",
            description = "Rubber oil seals, front fork seal, engine mounts, O-rings & dust boots",
            keywords = "oil seal, fork oil seal, crank seal, engine mounting, bush kit, shock bush, exhaust packing rubber, gear lever rubber, kick rubber, footrest rubber, carburetor manifold boot, o ring kit, axle boot, steering rack boot, wiper rubber refill, mudflap rubber",
            gstRate = 18.0,
            category = "Auto Rubber Parts"
        ),
        HsnEntry(
            hsnCode = "7009",
            description = "Vehicle rear-view side mirror & cabin IRVM mirror",
            keywords = "side mirror, bike mirror, rear view mirror, right mirror, left mirror, irvm mirror, orvm mirror, fender mirror, blind spot mirror, lumax mirror, uno minda mirror, fiem mirror",
            gstRate = 18.0,
            category = "Auto Mirrors & Glass"
        ),
        HsnEntry(
            hsnCode = "7007",
            description = "Car windshield laminated glass, door window & rear glass",
            keywords = "windshield glass, car front glass, laminated glass, toughened door glass, quarter glass, ais glass, saint gobain auto glass",
            gstRate = 18.0,
            category = "Auto Mirrors & Glass"
        ),
        HsnEntry(
            hsnCode = "8301",
            description = "Vehicle ignition lock set, tank cap lock, seat lock & steering lock",
            keywords = "ignition switch lock, lock set, bike lock kit, steering lock, fuel tank lock, handle lock, dickey lock, door latch lock, minda lock set, uno minda lock",
            gstRate = 18.0,
            category = "Auto Locks & Hardware"
        ),
        // Auto Ignition, Electrical, Lighting, Batteries & Instruments
        HsnEntry(
            hsnCode = "8511",
            description = "Spark plug, glow plug, ignition coil, CDI unit, RR unit, starter & alternator",
            keywords = "spark plug, iridium spark plug, spark plug car, spark plug bike, glow plug, heater plug, ignition coil, ht coil, cdi unit, rr unit, regulator rectifier, stator coil, magneto plate, pickup coil, starter motor, self motor, self bendix, solenoid switch, starter relay, alternator assembly, alternator rectifier, bosch spark plug, ngk spark plug, champion, denso, varroc cdi, lucas tvs",
            gstRate = 18.0,
            category = "Auto Electricals & Ignition"
        ),
        HsnEntry(
            hsnCode = "8512",
            description = "Headlight, tail light, indicator, halogen/LED bulb, horn & wiper blade",
            keywords = "headlight assembly, head lamp, tail light, back light, turn indicator, blinker, fog lamp, drl light, h4 headlight bulb, hs1 bulb, h7 bulb, car led headlight, bike horn, car horn, dual tone horn, windtone horn, flasher relay, wiper blade, frameless wiper, wiper motor, uno minda, lumax, fiem, hella, roots horn, osram night breaker, philips xtreme",
            gstRate = 18.0,
            category = "Auto Electricals & Lighting"
        ),
        HsnEntry(
            hsnCode = "8507",
            description = "Bike battery, Scooter 4Ah/5Ah battery, Car battery & Truck battery",
            keywords = "bike battery, two wheeler battery, 12v 4ah battery, 5ah battery, 7ah battery, 9ah battery, tz4 battery, tz5 battery, car battery, din55 battery, 35ah battery, 65ah battery, truck battery, 150ah tractor battery, amaron pro rider, amaron flo, exide xplore, exide mileage, sf sonic",
            gstRate = 18.0,
            category = "Auto Batteries"
        ),
        HsnEntry(
            hsnCode = "8544",
            description = "Automobile wiring harness, battery terminal wire & spark plug cable",
            keywords = "wiring harness, main wiring kit, bike wiring harness, battery cable, positive negative terminal cable, spark plug cap, high tension wire, automotive cable",
            gstRate = 18.0,
            category = "Auto Electricals & Ignition"
        ),
        HsnEntry(
            hsnCode = "8536",
            description = "Auto handlebar switch, brake light switch, relay & blade fuse box",
            keywords = "handlebar switch, combination switch, self start switch, kill switch, dimmer switch, front brake switch, rear brake switch, neutral switch, head lamp relay, horn relay, blade fuse, mini blade fuse, glass fuse, fuse box",
            gstRate = 18.0,
            category = "Auto Electricals & Ignition"
        ),
        HsnEntry(
            hsnCode = "9029",
            description = "Bike / car speedometer cluster, odometer, fuel gauge & worm gear",
            keywords = "speedometer assembly, digital meter, instrument cluster, tachometer, odometer worm gear, speedometer pinion, fuel level sensor, tpms sensor",
            gstRate = 18.0,
            category = "Auto Meters & Sensors"
        ),
        // Tyres, Tubes & Wheels
        HsnEntry(
            hsnCode = "4011",
            description = "Tubeless & tube-type bike, scooter, car, truck & tractor tyres",
            keywords = "tubeless tyre, tube type tyre, scooter tyre, 90/90-12 tyre, bike rear tyre, 100/90-17, car tyre, suv tyre, tractor tyre, truck tyre, mrf nylogrip, mrf zapper, ceat milaze, ceat gripp, apollo actigrip, apollo alnac, jk tyre, tvs eurogrip, bridgestone, michelin",
            gstRate = 18.0,
            category = "Tyres & Wheels"
        ),
        HsnEntry(
            hsnCode = "4013",
            description = "Butyl rubber inner tube for motorcycle, scooter & car tyres",
            keywords = "tyre tube, butyl tube, inner tube, bike tube, scooter tube, 3.00-18 tube, 2.75-18 tube, mrf tube, ceat tube, valve pin, tubeless valve nozzle",
            gstRate = 18.0,
            category = "Tyres & Wheels"
        ),
        // Lubricants, Coolant, Brake Fluid & Auto Care
        HsnEntry(
            hsnCode = "2710",
            description = "Engine oil (10W30, 10W40, 20W40, 5W30), gear oil, fork oil & grease",
            keywords = "10w30 oil, 10w40 engine oil, 20w40 oil, 20w50 oil, 15w40 diesel oil, 5w30 synthetic oil, 5w40 engine oil, gear oil, 80w90 gear oil, 85w140, fork oil, 2t oil, atf transmission fluid, hydraulic oil, lithium grease, wheel bearing grease, ap3 grease, castrol activ, castrol power1, castrol edge, motul 300v, motul 7100, motul 5100, motul scooter, shell advance, shell helix, mobil 1, servo 4t, mak 4t, gulf pride, valvoline, veedol, hp racer",
            gstRate = 18.0,
            category = "Lubricants & Fluids"
        ),
        HsnEntry(
            hsnCode = "3819",
            description = "Hydraulic brake fluid (DOT 3, DOT 4, DOT 5.1) & clutch oil",
            keywords = "brake fluid, brake oil, dot 3 brake oil, dot 4 brake fluid, dot 5.1 fluid, tvs girling brake fluid, bosch dot 4, castrol react",
            gstRate = 18.0,
            category = "Lubricants & Fluids"
        ),
        HsnEntry(
            hsnCode = "3820",
            description = "Radiator coolant & antifreeze liquid (Castrol Radicool, Wurth, Golden Cruiser)",
            keywords = "coolant, radiator coolant, green coolant, red coolant, antifreeze fluid, radicool, golden cruiser coolant, wurth coolant, motul motocool",
            gstRate = 18.0,
            category = "Lubricants & Fluids"
        ),
        HsnEntry(
            hsnCode = "3405",
            description = "Chain lube spray, chain cleaner, carb cleaner & dashboard polish",
            keywords = "chain lube, chain spray, chain cleaner spray, motul c2, motul c1, rollon chain spray, carburetor cleaner, throttle body cleaner, wd40, rust remover spray, dashboard polish, tyre polish, 3m car wax, formula 1 polish",
            gstRate = 18.0,
            category = "Auto Detailing & Sprays"
        ),
        HsnEntry(
            hsnCode = "2853",
            description = "Distilled water / Battery water & Deionized water",
            keywords = "battery water, distilled water, deionized water, inverter battery water, dm water",
            gstRate = 18.0,
            category = "Batteries & Fluids"
        ),
        HsnEntry(
            hsnCode = "8708",
            description = "Tractor spare parts & agricultural tractor accessories",
            keywords = "tractor parts, tractor spare parts, tractor clutch plate, tractor hydraulic pump, tractor tie rod, tractor radiator, mahindra tractor part, swaraj tractor part, sonalika part, john deere part, massey ferguson part",
            gstRate = 5.0,
            category = "Tractor Spare Parts"
        )
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 6. GENERAL HARDWARE, PLUMBING, SANITARYWARE, STATIONERY & KITCHENWARE
    // ═════════════════════════════════════════════════════════════════════════
    private fun generalRetailEntries(): List<HsnEntry> = listOf(
        HsnEntry(
            hsnCode = "3917",
            description = "Plumbing PVC, UPVC & CPVC pipes and pipe fittings (Astral, Ashirvad, Finolex)",
            keywords = "upvc pipe, cpvc pipe, swr pipe, plumbing pipe, astral pipe, ashirvad pipe, supreme pipe, finolex pvc pipe, pvc elbow, cpvc tee, ball valve, reducer bush, tank nipple, solvent cement, pipe adhesive",
            gstRate = 18.0,
            category = "Plumbing & Hardware"
        ),
        HsnEntry(
            hsnCode = "8481",
            description = "Water taps, angle cock, mixer faucet, health faucet & brass valves",
            keywords = "water tap, pillar cock, bib cock, angle valve, wall mixer, bathroom shower, health faucet, jet spray, jaquar tap, hindware faucet, cera tap, parryware, brass gate valve, non return valve",
            gstRate = 18.0,
            category = "Plumbing & Sanitaryware"
        ),
        HsnEntry(
            hsnCode = "8205",
            description = "Hand tools: Spanners, Wrenches, Pliers, Screwdrivers, Hammer & Drill Bits",
            keywords = "spanner set, ring spanner, box spanner, socket wrench, ratchet handle, allen key set, combination plier, plier insulated, insulated plier, stanley plier, nose plier, wire cutter, screwdriver set, claw hammer, hacksaw frame, measuring tape, spirit level, taparia tools, stanley tools, bosch drill bit, masonry bit",
            gstRate = 18.0,
            category = "Hand Tools & Hardware"
        ),
        HsnEntry(
            hsnCode = "8467",
            description = "Power tools: Angle grinder, Drill machine, Marble cutter & Blower",
            keywords = "drill machine, hammer drill, angle grinder, marble cutter, electric blower, jigsaw, sander, bosch power tool, dewalt, makita, black decker, stanley grinder, cutting wheel",
            gstRate = 18.0,
            category = "Power Tools & Hardware"
        ),
        HsnEntry(
            hsnCode = "7318",
            description = "Nuts, bolts, washers, wood screws, gypsum screws & wall fasteners",
            keywords = "nut bolt, hex bolt, allen bolt, washer, spring washer, self tapping screw, gypsum screw, wooden screw, rawl plug, anchor fastener, wall gitti",
            gstRate = 18.0,
            category = "Hardware & Fasteners"
        ),
        HsnEntry(
            hsnCode = "7323",
            description = "Steel utensils, pressure cooker, tawa, kadhai & thermos flask",
            keywords = "pressure cooker, hawkins cooker, prestige cooker, non stick tawa, kadhai, steel tiffin, lunch box, milton flask, cello bottle, steel utensils, bartan",
            gstRate = 5.0,
            category = "Kitchenware"
        ),
        HsnEntry(
            hsnCode = "9608",
            description = "Ball pens, gel pens, markers & whiteboard markers (Reynolds, Cello, Parker)",
            keywords = "ball pen, gel pen, fountain pen, permanent marker, whiteboard marker, highlighter, sketch pen, cello pinpoint, reynolds, hauser xo, pentonic, pilot v5, parker pen, trimax, camlin marker",
            gstRate = 18.0,
            category = "Stationery & Office"
        ),
        HsnEntry(
            hsnCode = "9609",
            description = "Lead pencils, sharpeners, crayons & geometry box",
            keywords = "pencil, nataraj pencil, apsara pencil, mechanical pencil, color pencil, wax crayons, doms crayons, camel oil pastel, sharpener, geometry box, compass box, scale ruler",
            gstRate = 0.0,
            category = "Stationery & Office"
        )
    )
}
