package com.minhas.teller.utils

/**
 * Deterministic, offline localization of the assistant's replies into English, Hindi, Punjabi,
 * Italian, Spanish, French, and Russian. Preserves inlined amounts, names, and numbers.
 */
object ReplyLocalizer {

    private data class Pattern(
        val regex: Regex,
        val hindi: String,
        val punjabi: String,
        val italian: String,
        val spanish: String,
        val french: String,
        val russian: String
    )

    // Native-script scaffolds. $1.. keep the inlined value.
    private val patterns = listOf(
        Pattern(
            Regex("^Your current Cash-in-hand balance is: (.*?)\\.$"),
            "आपका वर्तमान नकद (Cash-in-hand) बैलेंस है: $1।",
            "ਤੁਹਾਡਾ ਮੌਜੂਦਾ ਨਕਦ (Cash-in-hand) ਬਕਾਇਆ ਹੈ: $1।",
            "Il tuo saldo di cassa attuale è: $1.",
            "Tu saldo de efectivo actual es: $1.",
            "Votre solde de caisse actuel est de : $1.",
            "Ваш текущий остаток наличных (Cash-in-hand): $1."
        ),
        Pattern(
            Regex("^Current Balance for '(.+?)' is: (.*?) \\((.+?)\\)\\.$"),
            "'$1' का वर्तमान बैलेंस है: $2 ($3)।",
            "'$1' ਦਾ ਮੌਜੂਦਾ ਬਕਾਇਆ ਹੈ: $2 ($3)।",
            "Il saldo attuale per '$1' è: $2 ($3).",
            "El saldo actual de '$1' es: $2 ($3).",
            "Le solde actuel pour '$1' est : $2 ($3).",
            "Текущий баланс '$1': $2 ($3)."
        ),
        Pattern(
            Regex("^I couldn't find a ledger named '(.+?)'\\.$"),
            "'$1' नाम का कोई खाता नहीं मिला।",
            "'$1' ਨਾਮ ਦਾ ਕੋਈ ਖਾਤਾ ਨਹੀਂ ਮਿਲਿਆ।",
            "Non ho trovato un conto chiamato '$1'.",
            "No pude encontrar una cuenta llamada '$1'.",
            "Impossible de trouver un compte nommé '$1'.",
            "Счет с именем '$1' не найден."
        ),
        Pattern(
            Regex("^You have (\\d+) unique items in stock\\.$"),
            "स्टॉक में आपके पास $1 अलग-अलग आइटम हैं।",
            "ਸਟਾਕ ਵਿੱਚ ਤੁਹਾਡੇ ਕੋਲ $1 ਵੱਖ-ਵੱਖ ਆਈਟਮਾਂ ਹਨ।",
            "Hai $1 articoli unici in magazzino.",
            "Tienes $1 artículos únicos en stock.",
            "Vous avez $1 articles uniques en stock.",
            "У вас на складе $1 уникальных товаров."
        ),
        Pattern(
            Regex("^Current stock for '(.+?)' is: (.*?) units\\.$"),
            "'$1' का वर्तमान स्टॉक है: $2 यूनिट।",
            "'$1' ਦਾ ਮੌਜੂਦਾ ਸਟਾਕ ਹੈ: $2 ਯੂਨਿਟ।",
            "La giacenza attuale per '$1' è: $2 unità.",
            "El stock actual de '$1' es: $2 unidades.",
            "Le stock actuel pour '$1' est : $2 unités.",
            "Текущий остаток для '$1': $2 ед."
        ),
        Pattern(
            Regex("^I couldn't find an item named '(.+?)'\\.$"),
            "'$1' नाम की कोई वस्तु नहीं मिली।",
            "'$1' ਨਾਮ ਦੀ ਕੋਈ ਵਸਤੂ ਨਹੀਂ ਮਿਲੀ।",
            "Non ho trovato un articolo chiamato '$1'.",
            "No pude encontrar un artículo llamado '$1'.",
            "Impossible de trouver un article nommé '$1'.",
            "Товар с названием '$1' не найден."
        ),
        Pattern(
            Regex("^Total sales \\((.+?)\\): (.*)$"),
            "कुल बिक्री ($1): $2",
            "ਕੁੱਲ ਵਿਕਰੀ ($1): $2",
            "Vendite totali ($1): $2",
            "Ventas totales ($1): $2",
            "Ventes totales ($1) : $2",
            "Всего продаж ($1): $2"
        ),
        Pattern(
            Regex("^Total money to collect from customers: (.*)$"),
            "ग्राहकों से वसूलने योग्य कुल राशि: $1",
            "ਗਾਹਕਾਂ ਤੋਂ ਵਸੂਲਣ ਯੋਗ ਕੁੱਲ ਰਾਸ਼ੀ: $1",
            "Totale da incassare dai clienti: $1",
            "Total a cobrar de clientes: $1",
            "Total à recevoir des clients : $1",
            "Всего к получению от клиентов: $1"
        ),
        Pattern(
            Regex("^Total money payable to suppliers: (.*)$"),
            "आपूर्तिकर्ताओं को देय कुल राशि: $1",
            "ਸਪਲਾਇਰਾਂ ਨੂੰ ਦੇਣ ਯੋਗ ਕੁੱਲ ਰਾਸ਼ੀ: $1",
            "Totale da pagare ai fornitori: $1",
            "Total a pagar a proveedores: $1",
            "Total à payer aux fournisseurs : $1",
            "Всего к оплате поставщикам: $1"
        ),
        Pattern(
            Regex("^Helafin: Which party/ledger should I check outstanding for\\?$"),
            "हेलाफिन: मैं किस पार्टी/खाते का बकाया देखूँ?",
            "ਹੇਲਾਫਿਨ: ਮੈਂ ਕਿਸ ਪਾਰਟੀ/ਖਾਤੇ ਦਾ ਬਕਾਇਆ ਵੇਖਾਂ?",
            "Helafin: Per quale conto/cliente devo verificare i sospesi?",
            "Helafin: ¿Para qué cuenta/cliente debo consultar los saldos pendientes?",
            "Helafin : Pour quel compte/tiers dois-je vérifier les impayés ?",
            "Хелафин: По какому счету/клиенту проверить задолженность?"
        ),
        Pattern(
            Regex("^Helafin: No outstanding bills found for (.+?)\\.$"),
            "हेलाफिन: $1 के लिए कोई बकाया बिल नहीं मिला।",
            "ਹੇਲਾਫਿਨ: $1 ਲਈ ਕੋਈ ਬਕਾਇਆ ਬਿੱਲ ਨਹੀਂ ਮਿਲਿਆ।",
            "Helafin: Nessuna fattura in sospeso trovata per $1.",
            "Helafin: No se encontraron facturas pendientes para $1.",
            "Helafin : Aucune facture impayée trouvée pour $1.",
            "Хелафин: Не найдено неоплаченных счетов для $1."
        ),
        Pattern(
            Regex("^Helafin: No vouchers found\\.$"),
            "हेलाफिन: कोई वाउचर नहीं मिला।",
            "ਹੇਲਾਫਿਨ: ਕੋਈ ਵਾਊਚਰ ਨਹੀਂ ਮਿਲਿਆ।",
            "Helafin: Nessun documento trovato.",
            "Helafin: No se encontraron comprobantes.",
            "Helafin : Aucun justificatif trouvé.",
            "Хелафин: Документов не найдено."
        ),
        Pattern(
            Regex("^Helafin: I couldn't find any item sales for (.+?)\\.$"),
            "हेलाफिन: $1 के लिए कोई आइटम बिक्री नहीं मिली।",
            "ਹੇਲਾਫਿਨ: $1 ਲਈ ਕੋਈ ਆਈਟਮ ਵਿਕਰੀ ਨਹੀਂ ਮਿਲੀ।",
            "Helafin: Nessuna vendita di articoli trovata per $1.",
            "Helafin: No pude encontrar ventas de artículos para $1.",
            "Helafin : Aucune vente d'article trouvée pour $1.",
            "Хелафин: Продажи товара не найдены для $1."
        ),
        Pattern(
            Regex("^Helafin: Outstanding for (.+?) \\(Total (.*?)\\)([\\s\\S]*)$"),
            "हेलाफिन: $1 का बकाया (कुल $2)$3",
            "ਹੇਲਾਫਿਨ: $1 ਦਾ ਬਕਾਇਆ (ਕੁੱਲ $2)$3",
            "Helafin: Sospesi per $1 (Totale $2)$3",
            "Helafin: Pendiente para $1 (Total $2)$3",
            "Helafin : Impayés pour $1 (Total $2)$3",
            "Хелафин: Задолженность по $1 (Всего $2)$3"
        ),
        Pattern(
            Regex("^Helafin: Top (\\d+) customers by sales \\((.+?)\\)([\\s\\S]*)$"),
            "हेलाफिन: बिक्री अनुसार शीर्ष $1 ग्राहक ($2)$3",
            "ਹੇਲਾਫਿਨ: ਵਿਕਰੀ ਅਨੁਸਾਰ ਪ੍ਰਮੁੱਖ $1 ਗਾਹਕ ($2)$3",
            "Helafin: I primi $1 clienti per vendite ($2)$3",
            "Helafin: Los mejores $1 clientes por ventas ($2)$3",
            "Helafin : Top $1 des clients par ventes ($2)$3",
            "Хелафин: Топ $1 клиентов по продажам ($2)$3"
        ),
        Pattern(
            Regex("^Helafin: Top (\\d+) suppliers by purchase \\((.+?)\\)([\\s\\S]*)$"),
            "हेलाफिन: खरीद अनुसार शीर्ष $1 आपूर्तिकर्ता ($2)$3",
            "ਹੇਲਾਫਿਨ: ਖਰੀਦ ਅਨੁਸਾਰ ਪ੍ਰਮੁੱਖ $1 ਸਪਲਾਇਰ ($2)$3",
            "Helafin: I primi $1 fornitori per acquisti ($2)$3",
            "Helafin: Los mejores $1 proveedores por compras ($2)$3",
            "Helafin : Top $1 des fournisseurs par achats ($2)$3",
            "Хелафин: Топ $1 поставщиков по закупкам ($2)$3"
        ),
        Pattern(
            Regex("^Helafin: Last (\\d+) (.*?) vouchers \\((.+?)\\)([\\s\\S]*)$"),
            "हेलाफिन: अंतिम $1 $2 वाउचर ($3)$4",
            "ਹੇਲਾਫਿਨ: ਆਖਰੀ $1 $2 ਵਾਊਚਰ ($3)$4",
            "Helafin: Ultimi $1 documenti $2 ($3)$4",
            "Helafin: Últimos $1 comprobantes $2 ($3)$4",
            "Helafin : Derniers $1 justificatifs $2 ($3)$4",
            "Хелафин: Последние $1 документов $2 ($3)$4"
        ),
        Pattern(
            Regex("^I didn't catch the amount\\. How much was paid to (.+?)\\?$"),
            "मुझे राशि समझ नहीं आई। $1 को कितना भुगतान किया गया?",
            "ਮੈਨੂੰ ਰਕਮ ਸਮਝ ਨਹੀਂ ਆਈ। $1 ਨੂੰ ਕਿੰਨਾ ਭੁਗਤਾਨ ਕੀਤਾ ਗਿਆ?",
            "Non ho capito l'importo. Quanto è stato pagato a $1?",
            "No entendí el monto. ¿Cuánto se le pagó a $1?",
            "Je n'ai pas compris le montant. Combien a été payé à $1 ?",
            "Я не понял сумму. Сколько было выплачено $1?"
        ),
        Pattern(
            Regex("^I didn't catch the amount\\. How much did you receive from (.+?)\\?$"),
            "मुझे राशि समझ नहीं आई। $1 से कितना प्राप्त हुआ?",
            "ਮੈਨੂੰ ਰਕਮ ਸਮਝ ਨਹੀਂ ਆਈ। $1 ਤੋਂ ਕਿੰਨਾ ਪ੍ਰਾਪਤ ਹੋਇਆ?",
            "Non ho capito l'importo. Quanto hai ricevuto da $1?",
            "No entendí el monto. ¿Cuánto recibiste de $1?",
            "Je n'ai pas compris le montant. Combien avez-vous reçu de $1 ?",
            "Я не понял сумму. Сколько получено от $1?"
        ),
        Pattern(
            Regex("^I didn't catch the amount\\. How much was the sale of (.+?)\\?$"),
            "मुझे राशि समझ नहीं आई। $1 की बिक्री कितने की थी?",
            "ਮੈਨੂੰ ਰਕਮ ਸਮਝ ਨਹੀਂ ਆਈ। $1 ਦੀ ਵਿਕਰੀ ਕਿੰਨੇ ਦੀ ਸੀ?",
            "Non ho capito l'importo. A quanto ammonta la vendita di $1?",
            "No entendí el monto. ¿Cuánto fue la venta de $1?",
            "Je n'ai pas compris le montant. À combien s'élève la vente de $1 ?",
            "Я не понял сумму. Какова сумма продажи $1?"
        )
    )

    private val exactMap = mapOf(
        "I have drafted a voucher for your review." to mapOf(
            SystemTtsEngine.Language.HINDI to "मैंने आपकी समीक्षा के लिए एक वाउचर तैयार किया है।",
            SystemTtsEngine.Language.PUNJABI to "ਮੈਂ ਤੁਹਾਡੀ ਸਮੀਖਿਆ ਲਈ ਇੱਕ ਵਾਊਚਰ ਤਿਆਰ ਕੀਤਾ ਹੈ।",
            SystemTtsEngine.Language.ITALIAN to "Ho preparato una bozza del documento per la tua verifica.",
            SystemTtsEngine.Language.SPANISH to "He preparado un borrador del comprobante para su revisión.",
            SystemTtsEngine.Language.FRENCH to "J'ai préparé un brouillon de justificatif pour vérification.",
            SystemTtsEngine.Language.RUSSIAN to "Я подготовил черновик документа для вашей проверки."
        ),
        "I've drafted a new ledger." to mapOf(
            SystemTtsEngine.Language.HINDI to "मैंने एक नया खाता तैयार किया है।",
            SystemTtsEngine.Language.PUNJABI to "ਮੈਂ ਇੱਕ ਨਵਾਂ ਖਾਤਾ ਤਿਆਰ ਕੀਤਾ ਹੈ।",
            SystemTtsEngine.Language.ITALIAN to "Ho preparato una bozza per un nuovo conto.",
            SystemTtsEngine.Language.SPANISH to "He preparado un nuevo libro contable.",
            SystemTtsEngine.Language.FRENCH to "J'ai préparé un nouveau compte.",
            SystemTtsEngine.Language.RUSSIAN to "Я подготовил черновик нового счета."
        ),
        "Please fill out the details in the form." to mapOf(
            SystemTtsEngine.Language.HINDI to "कृपया फॉर्म में विवरण भरें।",
            SystemTtsEngine.Language.PUNJABI to "ਕਿਰਪਾ ਕਰਕੇ ਫਾਰਮ ਵਿੱਚ ਵੇਰਵੇ ਭਰੋ।",
            SystemTtsEngine.Language.ITALIAN to "Compila i dettagli nel modulo.",
            SystemTtsEngine.Language.SPANISH to "Por favor complete los detalles en el formulario.",
            SystemTtsEngine.Language.FRENCH to "Veuillez remplir les détails dans le formulaire.",
            SystemTtsEngine.Language.RUSSIAN to "Пожалуйста, заполните данные в форме."
        ),
        "Here is the chart you requested." to mapOf(
            SystemTtsEngine.Language.HINDI to "यह रहा आपका मांगा गया चार्ट।",
            SystemTtsEngine.Language.PUNJABI to "ਇਹ ਰਿਹਾ ਤੁਹਾਡਾ ਮੰਗਿਆ ਗਿਆ ਚਾਰਟ।",
            SystemTtsEngine.Language.ITALIAN to "Ecco il grafico che hai richiesto.",
            SystemTtsEngine.Language.SPANISH to "Aquí está el gráfico que solicitaste.",
            SystemTtsEngine.Language.FRENCH to "Voici le graphique demandé.",
            SystemTtsEngine.Language.RUSSIAN to "Вот запрошенный график."
        ),
        "Here is the data table you requested." to mapOf(
            SystemTtsEngine.Language.HINDI to "यह रही आपकी मांगी गई डेटा तालिका।",
            SystemTtsEngine.Language.PUNJABI to "ਇਹ ਰਹੀ ਤੁਹਾਡੀ ਮੰਗੀ ਗਈ ਡੇਟਾ ਸਾਰਣੀ।",
            SystemTtsEngine.Language.ITALIAN to "Ecco la tabella dei dati richiesta.",
            SystemTtsEngine.Language.SPANISH to "Aquí está la tabla de datos solicitada.",
            SystemTtsEngine.Language.FRENCH to "Voici le tableau de données demandé.",
            SystemTtsEngine.Language.RUSSIAN to "Вот запрошенная таблица данных."
        ),
        "OK, cancelled. What else can I help with?" to mapOf(
            SystemTtsEngine.Language.HINDI to "ठीक है, रद्द कर दिया। मैं आपकी और क्या मदद कर सकता हूँ?",
            SystemTtsEngine.Language.PUNJABI to "ਠੀਕ ਹੈ, ਰੱਦ ਕਰ ਦਿੱਤਾ ਗਿਆ। ਮੈਂ ਤੁਹਾਡੀ ਹੋਰ ਕੀ ਮਦਦ ਕਰ ਸਕਦਾ ਹਾਂ?",
            SystemTtsEngine.Language.ITALIAN to "OK, annullato. Come posso aiutarti ora?",
            SystemTtsEngine.Language.SPANISH to "De acuerdo, cancelado. ¿En qué más puedo ayudarte?",
            SystemTtsEngine.Language.FRENCH to "D'accord, annulé. Que puis-je faire d'autre pour vous ?",
            SystemTtsEngine.Language.RUSSIAN to "Хорошо, отменено. Чем еще я могу помочь?"
        ),
        "What is the loan amount (in ₹)?" to mapOf(
            SystemTtsEngine.Language.HINDI to "ऋण की राशि (₹ में) कितनी है?",
            SystemTtsEngine.Language.PUNJABI to "ਲੋਨ ਦੀ ਰਕਮ (₹ ਵਿੱਚ) ਕਿੰਨੀ ਹੈ?",
            SystemTtsEngine.Language.ITALIAN to "Qual è l'importo del prestito (in ₹)?",
            SystemTtsEngine.Language.SPANISH to "¿Cuál es el monto del préstamo (en ₹)?",
            SystemTtsEngine.Language.FRENCH to "Quel est le montant du prêt (en ₹) ?",
            SystemTtsEngine.Language.RUSSIAN to "Какова сумма кредита (в ₹)?"
        ),
        "What is the down payment? Say 'no' if there isn't any." to mapOf(
            SystemTtsEngine.Language.HINDI to "डाउन पेमेंट कितनी है? यदि कुछ नहीं है तो 'नहीं' कहें।",
            SystemTtsEngine.Language.PUNJABI to "ਡਾਊਨ ਪੇਮੈਂਟ ਕਿੰਨੀ ਹੈ? ਜੇਕਰ ਕੋਈ ਨਹੀਂ ਹੈ ਤਾਂ 'ਨਹੀਂ' ਕਹੋ।",
            SystemTtsEngine.Language.ITALIAN to "Qual è l'acconto? Rispondi 'no' se non c'è.",
            SystemTtsEngine.Language.SPANISH to "¿Cuál es el pago inicial? Diga 'no' si no hay ninguno.",
            SystemTtsEngine.Language.FRENCH to "Quel est l'acompte ? Dites 'non' s'il n'y en a pas.",
            SystemTtsEngine.Language.RUSSIAN to "Каков первоначальный взнос? Скажите «нет», если его нет."
        ),
        "You haven't selected a company yet. Please go back to the Company List and select one to start using Helafin." to mapOf(
            SystemTtsEngine.Language.HINDI to "आपने अभी तक कोई कंपनी नहीं चुनी है। हेलाफिन का उपयोग शुरू करने के लिए कृपया कंपनी सूची पर जाएं और एक कंपनी चुनें।",
            SystemTtsEngine.Language.PUNJABI to "ਤੁਸੀਂ ਅਜੇ ਕੋਈ ਕੰਪਨੀ ਨਹੀਂ ਚੁਣੀ ਹੈ। ਕਿਰਪਾ ਕਰਕੇ ਕੰਪਨੀ ਸੂਚੀ ਵਿੱਚ ਵਾਪਸ ਜਾਓ ਅਤੇ ਹੇਲਾਫਿਨ ਦੀ ਵਰਤੋਂ ਸ਼ੁਰੂ ਕਰਨ ਲਈ ਇੱਕ ਕੰਪਨੀ ਚੁਣੋ।",
            SystemTtsEngine.Language.ITALIAN to "Non hai ancora selezionato un'azienda. Torna all'elenco delle aziende e selezionane una per iniziare.",
            SystemTtsEngine.Language.SPANISH to "Aún no ha seleccionado una empresa. Vuelva a la lista de empresas y seleccione una para comenzar.",
            SystemTtsEngine.Language.FRENCH to "Vous n'avez pas encore sélectionné d'entreprise. Veuillez revenir à la liste des entreprises et en choisir une.",
            SystemTtsEngine.Language.RUSSIAN to "Вы еще не выбрали компанию. Пожалуйста, вернитесь к списку компаний и выберите компанию, чтобы начать."
        )
    )

    fun localize(lang: SystemTtsEngine.Language, text: String): String {
        if (lang == SystemTtsEngine.Language.ENGLISH) return text

        exactMap[text]?.get(lang)?.let { return it }

        for (p in patterns) {
            val replacement = when (lang) {
                SystemTtsEngine.Language.HINDI -> p.hindi
                SystemTtsEngine.Language.PUNJABI -> p.punjabi
                SystemTtsEngine.Language.ITALIAN -> p.italian
                SystemTtsEngine.Language.SPANISH -> p.spanish
                SystemTtsEngine.Language.FRENCH -> p.french
                SystemTtsEngine.Language.RUSSIAN -> p.russian
                SystemTtsEngine.Language.ENGLISH -> continue
            }
            val matched = p.regex.matchEntire(text)
            if (matched != null) {
                return matched.groupValues.drop(1).foldIndexed(replacement) { i, acc, g ->
                    acc.replace("$$${i + 1}", g)
                }
            }
        }

        // Also localize the common "Helafin: " prefix
        if (text.startsWith("Helafin: ")) {
            val rest = text.removePrefix("Helafin: ")
            val prefix = when (lang) {
                SystemTtsEngine.Language.HINDI -> "हेलाफिन: "
                SystemTtsEngine.Language.PUNJABI -> "ਹੇਲਾਫਿਨ: "
                SystemTtsEngine.Language.FRENCH -> "Helafin : "
                SystemTtsEngine.Language.RUSSIAN -> "Хелафин: "
                else -> "Helafin: "
            }
            return prefix + rest
        }

        return text
    }
}
