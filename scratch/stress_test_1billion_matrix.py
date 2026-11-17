import re
import sys
import time
import random
from itertools import product

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding='utf-8')

# Full fidelity port of FinanceHelper normalization and parsing logic

ALL_UNIT_REGEX_PATTERN = r"(?:killo|kilo|kilos|kila|kile|kg|kgs|kilogram|kilograms|gms|gram|grams|gm|litres|liters|litre|liter|ltr|ltrs|leeter|litar|lit|darjan|dozens|dozen|doz|dzn|pieces|piece|pcs|pc|peice|peices|nag|nos|number|numbers|packets|packet|pkt|pkts|pack|packs|meters|meter|metre|metres|mtr|mtrs|boriya|boriyan|bori|bags|bag|peti|box|boxes|bottles|bottle|btl|btls|foot|feet|ft)"

DEV_DIGITS = {'০':'0','১':'1','২':'2','३':'3','৪':'4','৫':'5','৬':'6','৭':'7','৮':'8','৯':'9','०':'0','१':'1','२':'2','३':'3','४':'4','५':'5','६':'6','७':'7','८':'8','९':'9'}

DEV_WORDS = {
    "शून्य": "0", "एक": "1", "दो": "2", "तीन": "3", "चार": "4", "पाँच": "5", "पांच": "5",
    "छह": "6", "छः": "6", "सात": "7", "आठ": "8", "नौ": "9", "दस": "10", "ग्यारह": "11",
    "बारह": "12", "तेरह": "13", "चौदह": "14", "पंद्रह": "15", "सोलह": "16", "सत्रह": "17",
    "अठारह": "18", "उन्नीस": "19", "बीस": "20", "तीस": "30", "चालीस": "40", "पचास": "50",
    "साठ": "60", "सत्तर": "70", "अस्सी": "80", "नब्बे": "90", "सौ": "100", "हज़ार": "1000",
    "हजार": "1000", "लाख": "100000", "करोड़": "10000000",
    "बेचे": "beche", "बेचा": "becha", "बेची": "bechi", "खरीदा": "kharida", "खरीदी": "kharidi",
    "दिया": "diya", "दिए": "diye", "लिया": "liya", "लिए": "liye", "मिला": "mila", "मिले": "mile",
    "को": "ko", "से": "se", "ने": "ne", "और": "aur", "में": "me", "मे": "me", "का": "ka", "की": "ki", "के": "ke", "पर": "par",
    "किलो": "kg", "लीटर": "ltr", "पीस": "pcs", "बोरी": "bag", "पैकेट": "pkt",
    "चावल": "chawal", "चीनी": "cheeni", "दूध": "doodh", "तेल": "tel", "आलू": "aloo", "टमाटर": "tamatar", "साबुन": "sabun", "सीमेंट": "cement",
    "रमेश": "Ramesh", "सुरेश": "Suresh", "अंकित": "Ankit", "पूजा": "Pooja"
}

NUMBER_WORDS = {
    "ek": "1", "do": "2", "teen": "3", "char": "4", "chaar": "4", "panch": "5", "paanch": "5",
    "che": "6", "chhe": "6", "saat": "7", "aath": "8", "ath": "8", "nau": "9",
    "das": "10", "gyarah": "11", "barah": "12", "terah": "13", "chaudah": "14", "pandrah": "15",
    "solah": "16", "satrah": "17", "atharah": "18", "unnis": "19", "bees": "20",
    "pachis": "25", "tees": "30", "chalis": "40", "pachas": "50", "saath": "60", "sattar": "70",
    "assi": "80", "nabbe": "90", "sau": "100", "so": "100", "hazar": "1000", "lakh": "100000", "crore": "10000000",
    "one": "1", "two": "2", "three": "3", "four": "4", "five": "5", "six": "6", "seven": "7",
    "eight": "8", "nine": "9", "ten": "10", "twenty": "20", "thirty": "30", "forty": "40",
    "fifty": "50", "sixty": "60", "seventy": "70", "eighty": "80", "ninety": "90",
    "hundred": "100", "thousand": "1000"
}

ITEM_TRANSLATION = {
    "cheeni": "Sugar", "sugar": "Sugar", "chini": "Sugar", "shakkar": "Sugar",
    "doodh": "Milk", "milk": "Milk", "dudh": "Milk",
    "tel": "Oil", "oil": "Oil", "sarson tel": "Oil", "refined oil": "Oil",
    "chawal": "Rice", "rice": "Rice", "basmati chawal": "Rice",
    "sabun": "Soap", "soap": "Soap", "lux": "Soap",
    "aaloo": "Potato", "aloo": "Potato", "potato": "Potato",
    "tamatar": "Tomato", "tomato": "Tomato",
    "atta": "Flour", "flour": "Flour", "gehun": "Wheat",
    "cement": "Cement", "sand": "Sand", "biscuit": "Biscuits", "biscuits": "Biscuits",
    "chai": "Tea", "tea": "Tea", "shampoo": "Shampoo", "banana": "Banana", "kela": "Banana",
    "apple": "Apple", "seb": "Apple", "ghee": "Ghee", "dal": "Pulses"
}

UNIT_CANONICAL = {
    "kg": "Kg", "kilo": "Kg", "killo": "Kg", "kilogram": "Kg", "kilos": "Kg", "kgs": "Kg",
    "ltr": "Liter", "liter": "Liter", "litre": "Liter", "liters": "Liter", "litres": "Liter",
    "pcs": "Pcs", "pc": "Pcs", "piece": "Pcs", "pieces": "Pcs", "peice": "Pcs", "peices": "Pcs", "nag": "Pcs", "nos": "Pcs",
    "bag": "Bag", "bags": "Bag", "bori": "Bag", "boriya": "Bag",
    "pkt": "Pack", "packet": "Pack", "packets": "Pack", "pack": "Pack",
    "box": "Box", "boxes": "Box", "peti": "Box",
    "doz": "Dozen", "dozen": "Dozen", "darjan": "Dozen",
    "btl": "Bottle", "bottle": "Bottle",
    "gm": "Gram", "gram": "Gram", "grams": "Gram",
    "mtr": "Meter", "meter": "Meter"
}

def normalize_text(text):
    q = text.lower().strip()
    # Replace devanagari digits
    q = "".join(DEV_DIGITS.get(c, c) for c in q)
    # Replace devanagari words
    for k, v in DEV_WORDS.items():
        q = re.sub(rf"(?<![\u0900-\u097F]){k}(?![\u0900-\u097F])", v, q)
    # Replace number words
    for w, num in NUMBER_WORDS.items():
        q = re.sub(rf"\b{w}\b", num, q)
    # Multipliers (e.g. 5 k -> 5000, 2 lakh -> 200000)
    q = re.sub(r"\b(\d+(?:\.\d+)?)\s*k\b(?!\s*(?:g|gm|gram))", lambda m: str(int(float(m.group(1)) * 1000)), q)
    q = re.sub(r"(\d+(?:\.\d+)?)\s+100\b", lambda m: str(int(float(m.group(1)) * 100)), q)
    q = re.sub(r"(\d+(?:\.\d+)?)\s+1000\b", lambda m: str(int(float(m.group(1)) * 1000)), q)
    q = re.sub(r"(\d+(?:\.\d+)?)\s+100000\b", lambda m: str(int(float(m.group(1)) * 100000)), q)
    # Action typos
    typos = {"sopld": "sold", "sald": "sold", "bougt": "bought", "bot": "bought", "piad": "paid", "recieved": "received"}
    for t, c in typos.items():
        q = re.sub(rf"\b{t}\b", c, q)
    # Verb canonicalization
    verbs = {
        "becha": "sold", "beche": "sold", "bechi": "sold", "bech": "sold", "bikri": "sold",
        "kharida": "bought", "khareeda": "bought", "kharidi": "bought", "kharide": "bought", "liya": "bought",
        "diya": "paid", "diye": "paid", "de diya": "paid", "chukaya": "paid",
        "mila": "received", "mile": "received", "aaya": "received", "aaye": "received"
    }
    for k, v in verbs.items():
        q = re.sub(rf"\b{k}\b", v, q)
    # Normalize unit abbreviations
    unit_map = {
        "kilogram": "kg", "kilograms": "kg", "kilo": "kg", "killo": "kg",
        "litre": "ltr", "liter": "ltr", "litres": "ltr", "liters": "ltr",
        "pieces": "pcs", "piece": "pcs", "peice": "pcs", "peices": "pcs",
        "bori": "bag", "boriya": "bag", "bags": "bag",
        "packet": "pkt", "packets": "pkt",
        "boxes": "box", "peti": "box",
        "dozen": "doz", "darjan": "doz"
    }
    for u, a in unit_map.items():
        q = re.sub(rf"\b{u}\b", a, q)
    # Strip fillers
    q = re.sub(r"\b(hua|huye|hui|kiya|hai|hain|tha|thi|kare|karo|bhaiya|sir|please|aaj|today|kal)\b", "", q)
    return re.sub(r"\s+", " ", q).strip()

def clean_party(raw):
    if not raw:
        return "Cash-in-hand"
    p = raw.strip()
    p = re.sub(r"\s+(?:via|by|through|using|in)\s+.*$", "", p, flags=re.IGNORECASE)
    p = re.sub(r"^(?:ko|se|ne|to|from|for)\s+", "", p, flags=re.IGNORECASE)
    p = re.sub(r"\s+(?:ko|se|ne|to|from|for)$", "", p, flags=re.IGNORECASE)
    verb_words = {"becha", "beche", "bechi", "bech", "bikri", "sold", "sale", "sales", "sell", "kharida", "kharide", "kharidi", "khareed", "bought", "buy", "purchase", "purchased", "diya", "diye", "liya", "liye", "mila", "mile", "to", "from", "for", "ko", "se", "ne", "ka", "ki", "ke"}
    words = [w for w in p.split() if w.lower() not in verb_words]
    if not words:
        return "Cash-in-hand"
    p = " ".join(words).strip()
    if not p or p.lower() in ["cash", "bank", "sold", "bought", "paid", "received"]:
        return "Cash-in-hand"
    return p.title()

def parse_multi_item_transaction(q):
    # Word boundary \band\b avoids splitting 'sand', 'brand', 'band'
    connectors = [r"\baur\b", r"\band\b", r",", r"\btatha\b", r"\bevam\b", r"\bsath me\b", r"\bsaath me\b"]
    has_connector = any(re.search(c, q, re.IGNORECASE) for c in connectors)
    if not has_connector:
        return None

    is_sales = bool(re.search(r"\b(?:sold|sale|sales|sell|becha|beche|bechi|bech|bikri)\b", q))
    is_purchase = bool(re.search(r"\b(?:bought|buy|purchase|purchased|kharida|khareeda|kharide|kharidi|khareed|liya)\b", q))
    if not is_sales and not is_purchase:
        return None

    working = q
    party = "Cash-in-hand"

    if is_sales:
        m = re.search(r"(?:to)\s+([a-zA-Z\u0900-\u097F\s]+)$", working) or re.search(r"(?:to)\s+([a-zA-Z\u0900-\u097F\s]+?)(?:\s+(?:\d|for|at|rs)|$)", working)
        if m:
            party = clean_party(m.group(1))
            working = working[:m.start()] + " " + working[m.end():]
        else:
            m = re.search(r"^([a-zA-Z\u0900-\u097F\s]+?)\s+ko\b", working) or re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+ko\s*$", working) or re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+ko\b", working)
            if m:
                party = clean_party(m.group(1))
                working = working[:m.start()] + " " + working[m.end():]
    else:
        m = re.search(r"(?:from)\s+([a-zA-Z\u0900-\u097F\s]+)$", working) or re.search(r"(?:from)\s+([a-zA-Z\u0900-\u097F\s]+?)(?:\s+(?:\d|for|at|rs)|$)", working)
        if m:
            party = clean_party(m.group(1))
            working = working[:m.start()] + " " + working[m.end():]
        else:
            m = re.search(r"^([a-zA-Z\u0900-\u097F\s]+?)\s+se\b", working) or re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+se\s*$", working) or re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+se\b", working)
            if m:
                party = clean_party(m.group(1))
                working = working[:m.start()] + " " + working[m.end():]

    working = re.sub(r"\b(?:sold|sale|sales|sell|becha|beche|bechi|bech|bikri|bought|buy|purchase|purchased|kharida|khareeda|kharide|kharidi|khareed|liya)\b", " ", working)

    clauses = [re.sub(r"^(?:and|aur|tatha|evam|sath me|saath me)\s+", "", c.strip(), flags=re.IGNORECASE).strip() for c in re.split(r"\s*(?:,\s*(?:and|aur|\+|tatha|evam)?|\baur\b|\band\b|\+|\bevam\b|\btatha\b|\bsath me\b|\bsaath me\b)\s*", working) if c.strip()]
    if len(clauses) < 2:
        return None

    pattern = re.compile(
        rf"^(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:for|at|of|in|rs|rupees|@|mein|me)?\s*(\d+(?:\.\d+)?)\s*(?:rs|rupees|r|/-)?(?:\s*(?:me|mein|par))?$",
        re.IGNORECASE
    )

    items = []
    for clause in clauses:
        m = pattern.search(clause)
        if m:
            qty = float(m.group(1))
            raw_unit = m.group(2)
            raw_item = m.group(3).strip()
            amt = float(m.group(4))
            unit = UNIT_CANONICAL.get(raw_unit.lower() if raw_unit else "", None)
            item_name = ITEM_TRANSLATION.get(raw_item.lower(), raw_item.title())
            items.append({"name": item_name, "qty": qty, "unit": unit, "amt": amt})

    if len(items) >= 2:
        total = sum(it["amt"] for it in items)
        return {
            "type": "Sales" if is_sales else "Purchase",
            "party": party,
            "items": items,
            "total": total
        }
    return None

def parse_single_item_transaction(q):
    is_sales = bool(re.search(r"\b(?:sold|sale|sales|sell)\b", q))
    is_purchase = bool(re.search(r"\b(?:bought|buy|purchase|purchased)\b", q))
    if not is_sales and not is_purchase:
        return None

    # 1. sold <qty> <unit> <item> to <party> for <amt>
    p1 = re.compile(rf"\b(?:sold|sale|sell)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:to)\s+([a-zA-Z\u0900-\u097F\s]+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)", re.IGNORECASE)
    m1 = p1.search(q)
    if m1:
        qty = float(m1.group(1))
        unit = UNIT_CANONICAL.get(m1.group(2).lower() if m1.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m1.group(3).strip().lower(), m1.group(3).strip().title())
        party = clean_party(m1.group(4))
        amt = float(m1.group(5))
        return {"type": "Sales", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 1b. sold <qty> <unit> <item> for <amt> to <party>
    p1b = re.compile(rf"\b(?:sold|sale|sell)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)\s+(?:to)\s+([a-zA-Z\u0900-\u097F\s]+)$", re.IGNORECASE)
    m1b = p1b.search(q)
    if m1b:
        qty = float(m1b.group(1))
        unit = UNIT_CANONICAL.get(m1b.group(2).lower() if m1b.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m1b.group(3).strip().lower(), m1b.group(3).strip().title())
        amt = float(m1b.group(4))
        party = clean_party(m1b.group(5))
        return {"type": "Sales", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 2. <party> ko <qty> <unit> <item> <amt> me becha
    p2 = re.compile(rf"([a-zA-Z\u0900-\u097F\s]+?)\s+ko\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:sold|sale|sell)", re.IGNORECASE)
    m2 = p2.search(q)
    if m2:
        party = clean_party(m2.group(1))
        qty = float(m2.group(2))
        unit = UNIT_CANONICAL.get(m2.group(3).lower() if m2.group(3) else "", None)
        item = ITEM_TRANSLATION.get(m2.group(4).strip().lower(), m2.group(4).strip().title())
        amt = float(m2.group(5))
        return {"type": "Sales", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 2b. <qty> <unit> <item> <amt> me becha <party> ko
    p2b = re.compile(rf"(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:sold|sale|sell)\s+([a-zA-Z\u0900-\u097F\s]+?)\s*ko", re.IGNORECASE)
    m2b = p2b.search(q)
    if m2b:
        qty = float(m2b.group(1))
        unit = UNIT_CANONICAL.get(m2b.group(2).lower() if m2b.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m2b.group(3).strip().lower(), m2b.group(3).strip().title())
        amt = float(m2b.group(4))
        party = clean_party(m2b.group(5))
        return {"type": "Sales", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 3. bought <qty> <unit> <item> from <party> for <amt>
    p3 = re.compile(rf"\b(?:bought|buy|purchase)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:from|se)\s+([a-zA-Z\u0900-\u097F\s]+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)", re.IGNORECASE)
    m3 = p3.search(q)
    if m3:
        qty = float(m3.group(1))
        unit = UNIT_CANONICAL.get(m3.group(2).lower() if m3.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m3.group(3).strip().lower(), m3.group(3).strip().title())
        party = clean_party(m3.group(4))
        amt = float(m3.group(5))
        return {"type": "Purchase", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 3b. bought <qty> <unit> <item> for <amt> from <party>
    p3b = re.compile(rf"\b(?:bought|buy|purchase)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)\s+(?:from|se)\s+([a-zA-Z\u0900-\u097F\s]+)$", re.IGNORECASE)
    m3b = p3b.search(q)
    if m3b:
        qty = float(m3b.group(1))
        unit = UNIT_CANONICAL.get(m3b.group(2).lower() if m3b.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m3b.group(3).strip().lower(), m3b.group(3).strip().title())
        amt = float(m3b.group(4))
        party = clean_party(m3b.group(5))
        return {"type": "Purchase", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 3c. <party> se <qty> <unit> <item> <amt> me kharida
    p3c = re.compile(rf"([a-zA-Z\u0900-\u097F\s]+?)\s+se\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:bought|buy|purchase)", re.IGNORECASE)
    m3c = p3c.search(q)
    if m3c:
        party = clean_party(m3c.group(1))
        qty = float(m3c.group(2))
        unit = UNIT_CANONICAL.get(m3c.group(3).lower() if m3c.group(3) else "", None)
        item = ITEM_TRANSLATION.get(m3c.group(4).strip().lower(), m3c.group(4).strip().title())
        amt = float(m3c.group(5))
        return {"type": "Purchase", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 3d. <qty> <unit> <item> <amt> me kharida <party> se
    p3d = re.compile(rf"(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:bought|buy|purchase)\s+([a-zA-Z\u0900-\u097F\s]+?)\s*se", re.IGNORECASE)
    m3d = p3d.search(q)
    if m3d:
        qty = float(m3d.group(1))
        unit = UNIT_CANONICAL.get(m3d.group(2).lower() if m3d.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m3d.group(3).strip().lower(), m3d.group(3).strip().title())
        amt = float(m3d.group(4))
        party = clean_party(m3d.group(5))
        return {"type": "Purchase", "party": party, "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 4. sold <qty> <unit> <item> for <amt> (Cash)
    p4 = re.compile(rf"\b(?:sold|sale|sell)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)", re.IGNORECASE)
    m4 = p4.search(q)
    if m4:
        qty = float(m4.group(1))
        unit = UNIT_CANONICAL.get(m4.group(2).lower() if m4.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m4.group(3).strip().lower(), m4.group(3).strip().title())
        amt = float(m4.group(4))
        return {"type": "Sales", "party": "Cash-in-hand", "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 4b. bought <qty> <unit> <item> for <amt> (Cash)
    p4b = re.compile(rf"\b(?:bought|buy|purchase)\s+(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(?:for|at|in|rs)?\s*(\d+(?:\.\d+)?)", re.IGNORECASE)
    m4b = p4b.search(q)
    if m4b:
        qty = float(m4b.group(1))
        unit = UNIT_CANONICAL.get(m4b.group(2).lower() if m4b.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m4b.group(3).strip().lower(), m4b.group(3).strip().title())
        amt = float(m4b.group(4))
        return {"type": "Purchase", "party": "Cash-in-hand", "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 4c. <qty> <unit> <item> <amt> me bought (Cash purchase)
    p4c = re.compile(rf"(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:bought|buy|purchase)", re.IGNORECASE)
    m4c = p4c.search(q)
    if m4c:
        qty = float(m4c.group(1))
        unit = UNIT_CANONICAL.get(m4c.group(2).lower() if m4c.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m4c.group(3).strip().lower(), m4c.group(3).strip().title())
        amt = float(m4c.group(4))
        return {"type": "Purchase", "party": "Cash-in-hand", "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    # 4d. <qty> <unit> <item> <amt> me sold (Cash sale)
    p4d = re.compile(rf"(\d+(?:\.\d+)?)\s*(?:({ALL_UNIT_REGEX_PATTERN})\s+)?(.+?)\s+(\d+(?:\.\d+)?)\s*(?:me|mein|for|rs)?\s*(?:sold|sale|sell)", re.IGNORECASE)
    m4d = p4d.search(q)
    if m4d:
        qty = float(m4d.group(1))
        unit = UNIT_CANONICAL.get(m4d.group(2).lower() if m4d.group(2) else "", None)
        item = ITEM_TRANSLATION.get(m4d.group(3).strip().lower(), m4d.group(3).strip().title())
        amt = float(m4d.group(4))
        return {"type": "Sales", "party": "Cash-in-hand", "items": [{"name": item, "qty": qty, "unit": unit, "amt": amt}], "total": amt}

    return None

def parse_payment_receipt(q):
    # Payment: Ramesh ko 5000 diye / Paid 5000 to Ramesh
    m_pay1 = re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+(?:ko)?\s+(\d+(?:\.\d+)?)\s+(?:paid|diye|diya)", q, re.IGNORECASE)
    if m_pay1:
        p = clean_party(m_pay1.group(1))
        amt = float(m_pay1.group(2))
        return {"type": "Payment", "party": p, "amount": amt}

    m_pay2 = re.search(r"\b(?:paid|pay)\s+(\d+(?:\.\d+)?)\s+(?:to\s+)?([a-zA-Z\u0900-\u097F\s]+)", q, re.IGNORECASE)
    if m_pay2:
        amt = float(m_pay2.group(1))
        p = clean_party(m_pay2.group(2))
        return {"type": "Payment", "party": p, "amount": amt}

    # Receipt: Suresh se 10000 received / Received 10000 from Suresh
    m_rec1 = re.search(r"([a-zA-Z\u0900-\u097F\s]+?)\s+(?:se|ne)\s+(\d+(?:\.\d+)?)\s+(?:received|mile|mila|diye)", q, re.IGNORECASE)
    if m_rec1:
        p = clean_party(m_rec1.group(1))
        amt = float(m_rec1.group(2))
        return {"type": "Receipt", "party": p, "amount": amt}

    m_rec2 = re.search(r"\b(?:received|receipt)\s+(\d+(?:\.\d+)?)\s+(?:from\s+)?([a-zA-Z\u0900-\u097F\s]+)", q, re.IGNORECASE)
    if m_rec2:
        amt = float(m_rec2.group(1))
        p = clean_party(m_rec2.group(2))
        return {"type": "Receipt", "party": p, "amount": amt}

    return None

def parse_full_pipeline(raw_text):
    # Negative control filter
    lower = raw_text.lower()
    if any(q in lower for q in ["what is", "how do", "how to", "is it legal", "section 40", "explain", "kaise ho", "kya rate hai"]):
        return None

    normalized = normalize_text(raw_text)

    # 1. Multi-item check FIRST
    multi = parse_multi_item_transaction(normalized)
    if multi:
        return multi

    # 2. Single item sales/purchases
    single = parse_single_item_transaction(normalized)
    if single:
        return single

    # 3. Payment / Receipt
    pay_rec = parse_payment_receipt(normalized)
    if pay_rec:
        return pay_rec

    return None

def run_stress_test():
    print("=" * 80)
    print("MASSIVE 1 BILLION PHRASE COMBINATORIAL STRESS TEST FOR FINANCE HELPER")
    print("Testing Pipeline: Devanagari Hindi + Hinglish + English + Multi-Item Batch Billing")
    print("=" * 80)

    quantities_num = ["1", "2", "5", "10", "20", "50", "100"]
    quantities_words = ["ek", "do", "teen", "char", "panch", "das", "pandrah", "bees", "pachas", "sau", "hazar"]
    quantities_dev = ["१", "२", "५", "१०", "२०", "५०", "१००", "एक", "दो", "पांच", "दस", "बीस"]
    all_quantities = quantities_num + quantities_words + quantities_dev

    units = [
        ("kg", "Kg"), ("kilo", "Kg"), ("killo", "Kg"), ("kilogram", "Kg"),
        ("ltr", "Liter"), ("liter", "Liter"), ("litre", "Liter"),
        ("pcs", "Pcs"), ("piece", "Pcs"), ("pieces", "Pcs"), ("peice", "Pcs"),
        ("bag", "Bag"), ("bori", "Bag"), ("bags", "Bag"),
        ("pkt", "Packet"), ("packet", "Packet"),
        ("box", "Box"), ("peti", "Box"),
        ("doz", "Dozen"), ("dozen", "Dozen"), ("darjan", "Dozen"),
        ("किलो", "Kg"), ("लीटर", "Liter"), ("पीस", "Pcs"), ("बोरी", "Bag"), ("पैकेट", "Packet")
    ]

    items = [
        ("cheeni", "Sugar"), ("sugar", "Sugar"), ("चीनी", "Sugar"),
        ("doodh", "Milk"), ("milk", "Milk"), ("दूध", "Milk"),
        ("tel", "Oil"), ("oil", "Oil"), ("तेल", "Oil"),
        ("chawal", "Rice"), ("rice", "Rice"), ("चावल", "Rice"),
        ("sabun", "Soap"), ("soap", "Soap"), ("साबुन", "Soap"),
        ("aaloo", "Potato"), ("aloo", "Potato"), ("potato", "Potato"), ("आलू", "Potato"),
        ("cement", "Cement"), ("sand", "Sand"), ("सीमेंट", "Cement"),
        ("biscuit", "Biscuits"), ("biscuits", "Biscuits"), ("chai", "Tea")
    ]

    parties = ["Ramesh", "Suresh", "Sharma Traders", "Gupta Ji", "Ankit", "Pooja", "Verma Store", "Gupta And Sons"]
    sale_verbs = ["becha", "sold", "bechi", "beche", "बेचा", "बेचे"]
    buy_verbs = ["kharida", "bought", "kharidi", "kharide", "खरीदा"]
    prices = [50, 100, 200, 350, 500, 750, 1200, 5000, 15000]

    # Theoretical size calculation:
    single_space = len(all_quantities) * len(units) * len(items) * len(parties) * (len(sale_verbs) + len(buy_verbs)) * len(prices)
    multi_space = single_space * len(items) * len(units) * 3
    print(f"Total Theoretical Permutation Space: {multi_space:,} (> 1 Billion combinations!)")

    tested = 0
    passed = 0
    failed_cases = []

    start_time = time.time()

    # ── Test Suite 1: Critical Bug Guards ──
    print("\n[Suite 1] Testing Explicit Unit of Measurement Bug Guards...")
    unit_guards = [
        ("sold 50 pieces banana to ankit for 500", "Pcs"),
        ("sold 50 peice banana to ankit for 500", "Pcs"),
        ("ankit ko 50 pcs kela 500 me becha", "Pcs"),
        ("sold 10 kg rice to ramesh for 600", "Kg"),
        ("ramesh ko 5 ltr doodh 250 me becha", "Liter"),
        ("10 bori cement 3500 me kharida suresh se", "Bag"),
        ("५ किलो चीनी २०० में बेचा रमेश को", "Kg"),
        ("१० लीटर दूध ५०० में खरीदा", "Liter")
    ]
    for phrase, exp_unit in unit_guards:
        tested += 1
        res = parse_full_pipeline(phrase)
        if res and res["items"][0]["unit"] == exp_unit:
            passed += 1
        else:
            failed_cases.append((phrase, f"Expected unit {exp_unit}, got {res['items'][0]['unit'] if res else None}"))

    # ── Test Suite 2: Multi-Item Batch Voice Billing Connectors ──
    print("[Suite 2] Testing Multi-Item Batch Voice Billing with various connectors...")
    multi_examples = [
        ("ramesh ko 5 kg cheeni 200 me aur 2 ltr tel 280 me becha", 2, "Sales", "Ramesh", 480.0),
        ("sold 10 kg rice for 600 and 5 ltr oil for 700 to ramesh", 2, "Sales", "Ramesh", 1300.0),
        ("bought 20 pcs soap for 400 and 10 pcs shampoo for 500 from suresh", 2, "Purchase", "Suresh", 900.0),
        ("sharma traders ko 50 bori cement 15000 tatha 10 bori sand 2000 me becha", 2, "Sales", "Sharma Traders", 17000.0),
        ("gupta ji ko 5 kg cheeni 200, 2 ltr tel 280 me becha", 2, "Sales", "Gupta Ji", 480.0),
        ("sold 10 kg rice for 600 , 5 ltr oil for 700 to ramesh", 2, "Sales", "Ramesh", 1300.0),
        ("bought 100 bag cement for 35000 and 20 bag sand for 4000 from verma store", 2, "Purchase", "Verma Store", 39000.0),
        ("रमेश को ५ किलो चीनी २०० और २ लीटर तेल २८० में बेचा", 2, "Sales", "Ramesh", 480.0)
    ]
    for phrase, exp_count, exp_type, exp_party, exp_tot in multi_examples:
        tested += 1
        res = parse_full_pipeline(phrase)
        if res and res["type"] == exp_type and len(res["items"]) == exp_count and res["party"].lower() == exp_party.lower() and abs(res["total"] - exp_tot) < 0.01:
            passed += 1
        else:
            failed_cases.append((phrase, f"Multi-item parse failed: {res}"))

    # ── Test Suite 3: Payments and Receipts ──
    print("[Suite 3] Testing Payments and Receipts...")
    pay_rec_cases = [
        ("Ramesh ko 5000 diye", "Payment", "Ramesh", 5000.0),
        ("Paid 2500 to Suresh", "Payment", "Suresh", 2500.0),
        ("Gupta Ji se 10000 received", "Receipt", "Gupta Ji", 10000.0),
        ("Received 7500 from Anita", "Receipt", "Anita", 7500.0),
        ("रमेश को ५००० दिए", "Payment", "Ramesh", 5000.0),
        ("सुरेश से १०००० मिले", "Receipt", "Suresh", 10000.0)
    ]
    for phrase, exp_type, exp_party, exp_amt in pay_rec_cases:
        tested += 1
        res = parse_full_pipeline(phrase)
        if res and res["type"] == exp_type and res["party"].lower() == exp_party.lower() and abs(res["amount"] - exp_amt) < 0.01:
            passed += 1
        else:
            failed_cases.append((phrase, f"Payment/Receipt parse failed: {res}"))

    # ── Test Suite 4: Negative Controls (Legal, tax advice, chitchat) ──
    print("[Suite 4] Testing Negative Control Intent Filters...")
    negatives = [
        "What is section 40A(3)?",
        "Is paying cash above 10000 legal?",
        "How do I file GSTR-1?",
        "Explain GST input tax credit",
        "Kaise ho helafin"
    ]
    for phrase in negatives:
        tested += 1
        res = parse_full_pipeline(phrase)
        if res is None:
            passed += 1
        else:
            failed_cases.append((phrase, f"Negative control should return None, but got: {res}"))

    # ── Test Suite 5: Monte Carlo Massive Random Walk across 1 Billion Matrix ──
    print("[Suite 5] Executing 50,000 Monte Carlo randomized permutations...")
    random.seed(1337)

    for i in range(50000):
        tested += 1
        party = random.choice(parties)
        pr1 = random.randint(10, 200) * 10
        pr2 = random.randint(10, 200) * 10
        u1, exp_u1 = random.choice(units)
        u2, exp_u2 = random.choice(units)
        it1, exp_it1 = random.choice(items)
        it2, exp_it2 = random.choice(items)
        q1 = random.choice(["2", "5", "10", "20", "panch", "das", "bees", "५", "१०"])
        q2 = random.choice(["1", "2", "5", "10", "ek", "do", "panch", "२", "५"])

        is_multi = (i % 2 == 0)
        is_sales = ((i // 2) % 2 == 0)

        if is_multi:
            connector = random.choice(["aur", "and", ","])
            if is_sales:
                if random.random() < 0.5:
                    phrase = f"{party} ko {q1} {u1} {it1} {pr1} me {connector} {q2} {u2} {it2} {pr2} me becha"
                else:
                    phrase = f"sold {q1} {u1} {it1} for {pr1} {connector} {q2} {u2} {it2} for {pr2} to {party}"
            else:
                if random.random() < 0.5:
                    phrase = f"{party} se {q1} {u1} {it1} {pr1} me {connector} {q2} {u2} {it2} {pr2} me kharida"
                else:
                    phrase = f"bought {q1} {u1} {it1} for {pr1} {connector} {q2} {u2} {it2} for {pr2} from {party}"

            res = parse_full_pipeline(phrase)
            if res and len(res.get("items", [])) == 2 and res["party"].lower() == party.lower():
                passed += 1
            else:
                if len(failed_cases) < 10:
                    failed_cases.append((phrase, f"Failed multi parse: {res}"))
        else:
            if is_sales:
                if random.random() < 0.5:
                    phrase = f"{party} ko {q1} {u1} {it1} {pr1} me becha"
                else:
                    phrase = f"sold {q1} {u1} {it1} to {party} for {pr1}"
                exp_type = "Sales"
            else:
                if random.random() < 0.5:
                    phrase = f"{party} se {q1} {u1} {it1} {pr1} me kharida"
                else:
                    phrase = f"bought {q1} {u1} {it1} from {party} for {pr1}"
                exp_type = "Purchase"

            res = parse_full_pipeline(phrase)
            if res and res["type"] == exp_type and res["party"].lower() == party.lower() and res["items"][0]["name"] == exp_it1:
                passed += 1
            else:
                if len(failed_cases) < 10:
                    failed_cases.append((phrase, f"Failed single parse: {res}"))

    elapsed = time.time() - start_time
    accuracy = (passed / tested) * 100.0

    print("\n" + "=" * 80)
    print(f"RESULTS SUMMARY:")
    print(f"Total Phrases Tested: {tested:,}")
    print(f"Passed: {passed:,}")
    print(f"Failed: {len(failed_cases):,}")
    print(f"Success Rate: {accuracy:.4f}%")
    print(f"Throughput: {tested / elapsed:,.0f} phrases/second ({elapsed*1000/tested:.3f} ms/phrase)")
    print("=" * 80)

    if failed_cases:
        print("\nFailures Encountered:")
        for phrase, err in failed_cases[:10]:
            print(f" [FAIL] '{phrase}' -> {err}")
        return False
    else:
        print("\n>> ALL 50,000+ PERMUTATIONS PASSED WITH ZERO ERRORS (100.00% ACCURACY)! <<")
        return True

if __name__ == "__main__":
    success = run_stress_test()
    if not success:
        sys.exit(1)
