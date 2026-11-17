package com.minhas.teller.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.GlassWhite
import com.minhas.teller.ui.theme.GlassBorder
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar

// --- Data Structures ---

data class HelpStep(
    val title: String,
    val description: String,
    val icon: ImageVector
)

data class HelpTopic(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val emoji: String,
    val icon: ImageVector,
    val accountingExplanation: String, // Plain-English explanation of the concept
    val steps: List<HelpStep>,
    val tips: List<String> = emptyList()
)

data class HelpCategory(
    val name: String,
    val emoji: String,
    val color: Color,
    val topics: List<HelpTopic>
)

// --- Help Content for Beginners (updated for the simple entry flow) ---

val helpCategories = listOf(
    HelpCategory(
        name = "Start Here",
        emoji = "🚀",
        color = Color(0xFF4CAF50),
        topics = listOf(
            HelpTopic(
                id = "welcome",
                category = "Start Here",
                title = "What is this app?",
                description = "In one line: your shop diary on your phone.",
                emoji = "👋",
                icon = Icons.Default.Info,
                accountingExplanation = "This app is your shop diary. It remembers every sale, every payment, and how much stock is left. You just tap a few buttons — it does all the maths.",
                steps = listOf(
                    HelpStep("Record everything", "Whenever you sell something or get money — just type it here. Takes 30 seconds.", Icons.Default.Edit),
                    HelpStep("See your totals", "Open the app anytime. The first screen shows your sales and money to collect — ready to read, no maths needed.", Icons.Default.Lightbulb),
                    HelpStep("Data stays safe", "Everything is saved on your phone. You can also copy it to Google Drive so it's never lost.", Icons.Default.Security)
                ),
                tips = listOf("If you can use WhatsApp, you can use this app. No accounting knowledge needed — we promise! 🙌")
            ),
            HelpTopic(
                id = "create_company",
                category = "Start Here",
                title = "Set up your shop",
                description = "Do this once when you first open the app.",
                emoji = "🏪",
                icon = Icons.Default.Business,
                accountingExplanation = "First time? Tell the app your shop name and phone number. This prints on every bill you give to customers.",
                steps = listOf(
                    HelpStep("Tap the + button", "On the very first screen, you'll see a list. Tap the + button at the bottom.", Icons.Default.Add),
                    HelpStep("Fill your shop details", "Type your shop name, phone number, and address. GST number is optional — skip it if you don't have one.", Icons.Default.Edit),
                    HelpStep("Save and open", "Tap Save. Then tap on your shop name to enter. Done! ✅", Icons.Default.CheckCircle)
                ),
                tips = listOf("Have more than one shop? You can add them all here and switch between them anytime.")
            ),
            HelpTopic(
                id = "home_tour",
                category = "Start Here",
                title = "Your Home screen",
                description = "What the numbers and buttons mean.",
                emoji = "🏠",
                icon = Icons.Default.Home,
                accountingExplanation = "The first screen shows everything about your shop — in one look. Money you made, money you spent, and money people still owe you.",
                steps = listOf(
                    HelpStep("Top 3 numbers", "The first number = your Sales. Second = your Costs. Third = Money people owe you (very important!).", Icons.AutoMirrored.Filled.TrendingUp),
                    HelpStep("The big buttons below", "Tap 'Add New Entry' to record anything — a sale, a payment, a purchase. The other buttons show your customers, items, and reports.", Icons.Default.TouchApp),
                    HelpStep("'Money to Collect' is king", "This number tells you total money customers still haven't paid. Check it every day!", Icons.Default.CreditCard)
                ),
                tips = listOf("Make it a habit: open the app every morning and look at the top 3 numbers. Takes 5 seconds. ⏰")
            ),
            HelpTopic(
                id = "simple_mode",
                category = "Start Here",
                title = "Simple words vs Accounting words",
                description = "One switch changes the whole app.",
                emoji = "🔤",
                icon = Icons.Default.Language,
                accountingExplanation = "You can choose simple words (like 'Owes You') or accountant words (like 'Debit/Credit'). Simple is turned ON by default — leave it that way!",
                steps = listOf(
                    HelpStep("Open Settings", "Tap the three-line menu (☰) at top right, then tap Preferences.", Icons.Default.Settings),
                    HelpStep("See the switch", "Look for 'Use Simple Terms'. It should be ON. That means the app speaks in plain everyday words.", Icons.Default.TouchApp),
                    HelpStep("Switch only if needed", "Only turn it OFF if your CA or accountant asks you to. For daily shop work, keep it ON.", Icons.Default.CheckCircle)
                ),
                tips = listOf("Simple mode = the app talks like a normal person. Accounting mode = the app talks like a CA. Keep it SIMPLE! 👍")
            )
        )
    ),
    HelpCategory(
        name = "Adding an Entry",
        emoji = "✍️",
        color = Color(0xFF2196F3),
        topics = listOf(
            HelpTopic(
                id = "entry_chooser",
                category = "Adding an Entry",
                title = "Start: tap 'Add New Entry'",
                description = "Then just pick what happened.",
                emoji = "👉",
                icon = Icons.Default.TouchApp,
                accountingExplanation = "Every entry starts from one button. You answer a simple question — 'What happened?' — and tap your answer. The app does the rest.",
                steps = listOf(
                    HelpStep("Tap 'Add New Entry'", "It's the biggest button on the Home screen. Can't miss it!", Icons.Default.Add),
                    HelpStep("Answer the question", "You will see: 'What kind of entry?' Just tap what happened.", Icons.Default.Lightbulb),
                    HelpStep("Pick one and go", "Tap Sale, Money In, Money Out, or Purchase. Whatever fits what you did.", Icons.Default.CheckCircle)
                ),
                tips = listOf("Sold something? → Sale. Got cash? → Money In. Paid someone? → Money Out. Bought stock? → Purchase. That's it! 😊")
            ),
            HelpTopic(
                id = "new_sale",
                category = "Adding an Entry",
                title = "Record a Sale",
                description = "You sold something to a customer.",
                emoji = "🛒",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accountingExplanation = "Sold something to a customer? Record it here. The app makes the bill and tracks how much they owe (if they didn't pay yet).",
                steps = listOf(
                    HelpStep("Tap Sale", "From the question screen, tap 'Sale'. This means you sold goods.", Icons.AutoMirrored.Filled.TrendingUp),
                    HelpStep("How did they pay?", "Tap Cash (paid now), Bank (transferred), On Credit (pay later), or Finance.", Icons.Default.CreditCard),
                    HelpStep("Type details and save", "Enter customer name and amount. Add the item if you want. Then tap Save. Done!", Icons.Default.Save)
                ),
                tips = listOf("You don't need to pick any 'account' or 'group'. The app fills all that for you automatically. One less thing to worry about! 🎉")
            ),
            HelpTopic(
                id = "money_in",
                category = "Adding an Entry",
                title = "Money In (someone paid you)",
                description = "A customer cleared their dues or gave cash.",
                emoji = "⬇️",
                icon = Icons.Default.ArrowDownward,
                accountingExplanation = "Got money from someone? Record it here. Customer paid their old bill? This is where you put it. The app automatically reduces what they owe.",
                steps = listOf(
                    HelpStep("Tap Money In", "From the question screen, tap 'Money In'. This means you received money.", Icons.Default.ArrowDownward),
                    HelpStep("Who gave you money?", "Pick the person's name from the list (or type a new one).", Icons.Default.Person),
                    HelpStep("How much? Cash or Bank?", "Type the amount. Choose Cash if they handed you notes. Choose Bank if they transferred.", Icons.Default.AttachMoney),
                    HelpStep("Save", "Tap Save. If this was against an old bill, the app automatically reduces what they owe.", Icons.Default.Save)
                ),
                tips = listOf("Customer cleared an old 'On Credit' bill? Record it here as Money In. The app will automatically mark their bill as paid. ✅")
            ),
            HelpTopic(
                id = "money_out",
                category = "Adding an Entry",
                title = "Money Out (you paid someone)",
                description = "You paid a supplier, rent, salary, or a bill.",
                emoji = "⬆️",
                icon = Icons.Default.ArrowUpward,
                accountingExplanation = "You paid money to someone? Supplier, rent, electricity, salary, tea — anything. Record it here so you know where your money went.",
                steps = listOf(
                    HelpStep("Tap Money Out", "From the question screen, tap 'Money Out'. This means you spent money.", Icons.Default.ArrowUpward),
                    HelpStep("Who did you pay?", "Pick from your list or type a name — supplier, landlord, staff, anyone.", Icons.Default.Person),
                    HelpStep("How much? From where?", "Type the amount. Choose Cash (from your pocket) or Bank (from your account).", Icons.Default.AttachMoney),
                    HelpStep("Save", "Tap Save. Done. The app now knows this money went out.", Icons.Default.Save)
                ),
                tips = listOf("Rent, electricity, petrol, tea, salary, phone recharge — ALL of these are Money Out. If you spent money, it goes here! 💸")
            ),
            HelpTopic(
                id = "new_purchase",
                category = "Adding an Entry",
                title = "Record a Purchase",
                description = "You bought stock to sell later.",
                emoji = "📦",
                icon = Icons.Default.ShoppingCart,
                accountingExplanation = "Bought stock for your shop? Record it here. The app adds it to your stock count and tracks how much you owe the supplier.",
                steps = listOf(
                    HelpStep("Tap Purchase", "From the question screen, tap 'Purchase'. This means you bought goods to sell.", Icons.Default.ShoppingCart),
                    HelpStep("What and how much?", "Type the item name and the amount you paid (or will pay).", Icons.Default.Edit),
                    HelpStep("From whom?", "Pick the supplier's name. If it's a new supplier, just type their name.", Icons.Default.Store),
                    HelpStep("Save", "Tap Save. Your stock count goes up automatically. No manual counting!", Icons.Default.Save)
                ),
                tips = listOf("IMPORTANT: Buying things for the shop to USE (like a fan, chair, tea) = that's Money Out, NOT a Purchase. Purchase = goods you will SELL. 📦")
            ),
            HelpTopic(
                id = "voice_entry",
                category = "Adding an Entry",
                title = "Just speak it (voice entry)",
                description = "Talk to your phone and the form fills itself.",
                emoji = "🎤",
                icon = Icons.Default.Mic,
                accountingExplanation = "Don't like typing? Just speak! Tap the mic button and say what happened — in Hindi or English. The app fills the form for you.",
                steps = listOf(
                    HelpStep("Tap the mic 🎤", "On the entry screen, look at the top. See the microphone? Tap it.", Icons.Default.Mic),
                    HelpStep("Say it out loud", "Speak normally! For example: 'Sold 2 tyres to Ramesh for 500 rupees' or 'Sharma ji ko 5000 diye'.", Icons.Default.Lightbulb),
                    HelpStep("Confirm and Save", "The app shows what it heard. Tap 'Yes, fill it in'. Check the form is correct, then Save.", Icons.Default.CheckCircle)
                ),
                tips = listOf("If the mic doesn't understand perfectly — no problem! It puts your words in the Notes box, and you can fill the rest by hand. Takes just a few taps. 🎯")
            ),
            HelpTopic(
                id = "scan_bill",
                category = "Adding an Entry",
                title = "Snap a paper bill",
                description = "Take a photo and the amount fills in.",
                emoji = "📸",
                icon = Icons.Default.CameraAlt,
                accountingExplanation = "Got a paper bill from a supplier? Just take a photo — the app reads the amount. No typing needed!",
                steps = listOf(
                    HelpStep("Tap the camera 📸", "On the entry screen, tap the camera icon at the top.", Icons.Default.CameraAlt),
                    HelpStep("Click a photo", "Hold the phone steady over the bill. Make sure there's enough light. Click!", Icons.Default.CheckCircle),
                    HelpStep("Check and Save", "The app reads the amount from the photo. Check it's right, tap Yes, then Save.", Icons.Default.Save)
                ),
                tips = listOf("Best results in good light with the bill lying flat on a table. Avoid shadows on the paper. 💡")
            )
        )
    ),
    HelpCategory(
        name = "Customers & Suppliers",
        emoji = "👤",
        color = Color(0xFF00BCD4),
        topics = listOf(
            HelpTopic(
                id = "add_customer",
                category = "Customers & Suppliers",
                title = "Add a customer",
                description = "Make a page for someone who buys from you.",
                emoji = "🙋",
                icon = Icons.Default.Person,
                accountingExplanation = "Add your regular customers here. The app then tracks what each person bought, paid, and still owes.",
                steps = listOf(
                    HelpStep("Go to Accounts Info", "From Home screen, tap 'Accounts Info'. The Customers/Companies list opens right away.", Icons.Default.FolderOpen),
                    HelpStep("Tap +", "Look at the bottom-right corner. See the round + button? Tap it.", Icons.Default.Add),
                    HelpStep("Type name and Save", "Type the customer's name (phone number optional). Tap Save. Done — their page is ready!", Icons.Default.Edit)
                ),
                tips = listOf("Shortcut: You don't even need to add them first. Just type a new name while making a Sale — the app creates their page automatically! 🪄")
            ),
            HelpTopic(
                id = "who_owes_you",
                category = "Customers & Suppliers",
                title = "See who owes you money",
                description = "The one list every shop owner checks.",
                emoji = "💰",
                icon = Icons.Default.CreditCard,
                accountingExplanation = "The app always knows who hasn't paid yet. No need to remember or write on paper. Just open the app and see.",
                steps = listOf(
                    HelpStep("Quick check on Home", "The 'Money to Collect' box on the Home screen = total money everyone owes you. Glance at it daily!", Icons.Default.Home),
                    HelpStep("Full list — who owes what", "Tap 'Reports', then 'Pending Payments'. You'll see every person and how much they owe, biggest amount first.", Icons.Default.Assessment),
                    HelpStep("Green color = owes you", "In your customer list, any name showing green with 'Owes you' means they still have to pay.", Icons.AutoMirrored.Filled.TrendingUp)
                ),
                tips = listOf("When someone finally pays, record it as 'Money In'. The app automatically reduces what they owe. No maths on your side! 🧮")
            ),
            HelpTopic(
                id = "add_supplier",
                category = "Customers & Suppliers",
                title = "Add a supplier",
                description = "Make a page for someone you buy from.",
                emoji = "🏭",
                icon = Icons.Default.Store,
                accountingExplanation = "Add the people you buy from. The app tracks how much you've paid each supplier and how much you still owe them.",
                steps = listOf(
                    HelpStep("Go to Accounts Info", "From Home, tap 'Accounts Info'. The Customers/Companies list opens right away.", Icons.Default.FolderOpen),
                    HelpStep("Tap + and name them", "Tap the + button. Type the supplier's shop name. Save.", Icons.Default.Add),
                    HelpStep("That's it!", "Now whenever you record a Purchase, pick this supplier's name. The app tracks everything.", Icons.Default.Save)
                ),
                tips = listOf("Already owed this supplier money from before? You can enter that as their 'opening balance' while creating their page. 🏪")
            )
        )
    ),
    HelpCategory(
        name = "Stock & Items",
        emoji = "📦",
        color = Color(0xFFFF9800),
        topics = listOf(
            HelpTopic(
                id = "add_item",
                category = "Stock & Items",
                title = "Add a product",
                description = "Tell the app what you sell.",
                emoji = "🧾",
                icon = Icons.Default.ShoppingCart,
                accountingExplanation = "Tell the app what you sell. Once you add an item, the app automatically counts how many you have left — every time you sell or buy.",
                steps = listOf(
                    HelpStep("Open Inventory", "From Home, tap 'Inventory'. This is your stock room on your phone.", Icons.Default.ShoppingCart),
                    HelpStep("Tap + to add", "Tap the + button at the bottom. A form opens.", Icons.Default.Add),
                    HelpStep("Name it and save", "Type the product name (like 'Ceiling Fan' or 'Rice 25kg'). Pick the unit (Pcs, Kg, Box, etc.). Tap Save. ✅", Icons.Default.Edit)
                ),
                tips = listOf("Shortcut: You can add a new item directly while making a Sale or Purchase. The form has an 'Add Item' option right there! 🛒")
            ),
            HelpTopic(
                id = "check_stock",
                category = "Stock & Items",
                title = "Check what is left",
                description = "See how many of each item you have.",
                emoji = "🔍",
                icon = Icons.Default.Search,
                accountingExplanation = "Open Inventory to see every item and how many are left. The app updates the count automatically — sell one, it drops. Buy one, it goes up.",
                steps = listOf(
                    HelpStep("Open Inventory from Home", "Tap 'Inventory' on the Home screen. You'll see your full stock list.", Icons.Default.ShoppingCart),
                    HelpStep("Read the numbers", "Each item shows: how many came IN (you bought), how many went OUT (you sold), and how many are LEFT right now.", Icons.Default.Assessment),
                    HelpStep("Red = out of stock!", "If you see a red 'Out of Stock' tag on an item, it means you have zero left. Time to order more!", Icons.Default.Info)
                ),
                tips = listOf("Never count stock manually again! The app does it live. Sell something → count drops. Buy something → count goes up. 🔄")
            )
        )
    ),
    HelpCategory(
        name = "Reports Made Simple",
        emoji = "📊",
        color = Color(0xFF9C27B0),
        topics = listOf(
            HelpTopic(
                id = "pending_payments",
                category = "Reports Made Simple",
                title = "Who owes me?",
                description = "Your list of pending payments.",
                emoji = "⏳",
                icon = Icons.Default.CreditCard,
                accountingExplanation = "This is the most important report! It lists every person who still hasn't paid you. Check it every week and remind them.",
                steps = listOf(
                    HelpStep("Go to Reports", "From Home, tap the Reports button. Or tap 'Display All Entries' then 'Reports'.", Icons.Default.Assessment),
                    HelpStep("Tap Pending Payments", "You'll see a list of everyone who owes you money. The person who owes the most is at the top.", Icons.Default.CreditCard),
                    HelpStep("Call or message them", "See a big amount? Tap the person's name, get their number, and give them a reminder call or WhatsApp.", Icons.Default.CheckCircle)
                ),
                tips = listOf("Make this a weekly habit — every Saturday, check Pending Payments and follow up. Don't let payments get forgotten! 📞")
            ),
            HelpTopic(
                id = "sales_report",
                category = "Reports Made Simple",
                title = "How much did I sell?",
                description = "Your total sales for any period.",
                emoji = "📈",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accountingExplanation = "Want to know how much you sold this month? Or last week? Pick any two dates and the app adds up all your sales.",
                steps = listOf(
                    HelpStep("Open Reports → Sales", "Tap Reports. Then tap 'Sales'. Simple!", Icons.Default.Assessment),
                    HelpStep("Pick your dates", "At the top, tap the date. Choose a START date and an END date. Like: 1st March to 31st March.", Icons.Default.CalendarToday),
                    HelpStep("See the big number", "The total sales amount is right there — big and clear. Scroll down to see each sale.", Icons.AutoMirrored.Filled.TrendingUp)
                ),
                tips = listOf("Compare this month with last month — are you growing? 📈 More sales than last month = your shop is doing well!")
            ),
            HelpTopic(
                id = "profit_report",
                category = "Reports Made Simple",
                title = "Am I making money?",
                description = "Sales minus costs = profit.",
                emoji = "💵",
                icon = Icons.Default.AttachMoney,
                accountingExplanation = "Simple question: 'Am I actually making money?' This report shows your sales MINUS your costs. What's left = your PROFIT (real earning).",
                steps = listOf(
                    HelpStep("Tap Reports → Profit", "From Home, tap Reports. Then tap 'Profit & Loss'.", Icons.Default.Assessment),
                    HelpStep("Read the 3 numbers", "It shows: ① Your total Sales, ② Your total Costs, ③ Profit (Sales minus Costs). If Profit is positive = you are earning money! 💰", Icons.Default.AttachMoney),
                    HelpStep("Check each item's profit", "Tap 'Item Profitability' to see which products make you money... and which ones lose money!", Icons.Default.Lightbulb)
                ),
                tips = listOf("If an item shows LOSS (red/minus), your selling price is too low! Either raise the price or stop keeping that item. 📉")
            ),
            HelpTopic(
                id = "daybook",
                category = "Reports Made Simple",
                title = "Everything I did today",
                description = "A simple day-by-day list.",
                emoji = "📅",
                icon = Icons.Default.CalendarToday,
                accountingExplanation = "This is your shop diary. Every entry you make — sale, purchase, payment — appears here, newest at the top. Flip through any day and see what happened.",
                steps = listOf(
                    HelpStep("Tap Reports → Daybook", "From Home, tap Reports. Then tap 'Daybook'.", Icons.Default.Assessment),
                    HelpStep("Scroll through your day", "Every single entry is listed here, with date and time. Newest entries at the top.", Icons.Default.CalendarToday),
                    HelpStep("Tap to view or fix", "See a mistake? Tap any entry to open it. You can edit or delete it from there.", Icons.Default.Edit)
                ),
                tips = listOf("Always check Daybook if something looks wrong. Tap the wrong entry → edit it → Save. Fixed! No stress. 🔧")
            )
        )
    ),
    HelpCategory(
        name = "Live Stock with Staff",
        emoji = "📱",
        color = Color(0xFFE91E63),
        topics = listOf(
            HelpTopic(
                id = "live_stock",
                category = "Live Stock with Staff",
                title = "How to use Live Stock",
                description = "Track stock live and approve your worker's updates.",
                emoji = "📦",
                icon = Icons.Default.Inventory2,
                accountingExplanation = "Live Stock is your stock room that updates in real time. Your worker adds stock from their phone and you see it instantly. You can also add stock yourself, fix counts, and accept or reject what your worker entered.",
                steps = listOf(
                    HelpStep("Open Live Stock", "From the Home screen, tap 'Live Stock'. You'll see every item with three numbers: In, Sold, and Stock left.", Icons.Default.Inventory2),
                    HelpStep("Add a stock item", "Tap 'ADD NEW'. Type the item name, pick up to 3 colours to spot it quickly, then enter how many came In and how many were Sold. Tap Save.", Icons.Default.Add),
                    HelpStep("Fix a count", "Mistake? Just tap any item row to open it, change the In/Sold numbers, and Save. The stock recalculates on its own.", Icons.Default.Edit),
                    HelpStep("Accept or Reject worker entries", "If your worker added stock, it appears at the top under 'Pending Employee Entries'. Tap Accept to add it, or Reject to send it back.", Icons.Default.CheckCircle),
                    HelpStep("Monthly Sales & PDF", "Tap 'MONTHLY SALES' for the month report, or the PDF icon to save/print the list. Use the From / To dates to pick a range.", Icons.Default.PictureAsPdf)
                ),
                tips = listOf(
                    "Add your employee from the + icon at the top. They use the same 10-character code for Daily Tasks AND Live Stock.",
                    "Red 'OUT OF STOCK' and yellow 'LOW STOCK' tags tell you what to reorder — check them every evening!"
                )
            )
        )
    ),
    HelpCategory(
        name = "Finance Helper (Helafin)",
        emoji = "🤖",
        color = Color(0xFF673AB7),
        topics = listOf(
            HelpTopic(
                id = "helafin",
                category = "Finance Helper (Helafin)",
                title = "Ask Helafin anything",
                description = "A helper that answers questions about your shop.",
                emoji = "💬",
                icon = Icons.Default.Info,
                accountingExplanation = "Imagine a smart assistant who knows everything about your shop. You can ask it questions in plain Hindi or English, and it gives you answers using your real shop numbers. Works WITHOUT internet!",
                steps = listOf(
                    HelpStep("First time: Download brain 🧠", "Open Finance Helper from the menu. Let it download once (about 500MB, needs WiFi). This happens only ONE time.", Icons.Default.Add),
                    HelpStep("Ask anything", "Type or speak your question: 'Who owes me the most?' or 'Aaj kitna sale hua?' or 'Profit this month?'", Icons.Default.Mic),
                    HelpStep("Get your answer", "Helafin replies using YOUR shop data. Real numbers, not guesses!", Icons.Default.CheckCircle)
                ),
                tips = listOf("No internet needed after the one-time download. Your shop data NEVER leaves your phone — 100% private and safe. 🔒")
            )
        )
    ),
    HelpCategory(
        name = "Daily Tasks with Staff",
        emoji = "📋",
        color = Color(0xFF00897B),
        topics = listOf(
            HelpTopic(
                id = "daily_tasks_setup",
                category = "Daily Tasks with Staff",
                title = "Assign Daily Tasks to Your Staff",
                description = "Give your employees a daily checklist they fill on their own phone.",
                emoji = "📋",
                icon = Icons.AutoMirrored.Filled.Assignment,
                accountingExplanation = "Give your workers a daily checklist on their own phone. You create the tasks — they tick them off. You see everything live. No paper, no phone calls, no 'Sir, done!' messages!",
                steps = listOf(
                    HelpStep("Open Daily Tasks", "On Home screen, tap 'Daily Tasks'. You'll see your employee list (empty at first).", Icons.Default.Checklist),
                    HelpStep("Add employee", "Tap + button. Type their name. A secret 10-character code is created automatically — this is their login key.", Icons.Default.PersonAdd),
                    HelpStep("Tell them to install the app", "Ask them to install 'Task and Stock for Teller' from the App Store (it is free). Tap 'Share Connection Code' to send them their code via WhatsApp or Messages.", Icons.Default.Share),
                    HelpStep("They connect with the code", "Employee opens the app on their phone, types the 10-character code, and BAM — connected to you!", Icons.Default.PhoneAndroid),
                    HelpStep("Create their tasks", "Tap the employee name → Tasks tab → Add Task. Choose type: Tick (Done/Not Done), Number, Amount, or Text. Set for how many days (or Always).", Icons.AutoMirrored.Filled.PlaylistAdd),
                    HelpStep("Watch live progress", "Green check ✓ = done. Red cross ✗ = not done. Grey = waiting. You see it updating LIVE as they work!", Icons.Default.Visibility)
                ),
                tips = listOf(
                    "You can add many employees — each gets their own unique code and their own task list.",
                    "Long-press any employee card to delete them.",
                    "New task added? The employee gets a notification instantly on their phone. No need to call them! 🔔"
                )
            ),
            HelpTopic(
                id = "daily_tasks_approval",
                category = "Daily Tasks with Staff",
                title = "Review Reports & Approve Work",
                description = "Check what your staff did and approve or send back their reports.",
                emoji = "✅",
                icon = Icons.Default.Verified,
                accountingExplanation = "Employee submitted their work? A red badge pops up. Tap it, review what they did, check their attendance, then Approve or Send Back. The employee sees your feedback instantly!",
                steps = listOf(
                    HelpStep("Spot the REVIEW badge 🔴", "When an employee finishes and submits, a red 'REVIEW' badge pops up on their name. You can't miss it!", Icons.Default.Notifications),
                    HelpStep("See everything they did", "Tap the employee name → Reports tab. See every task — Done or Not Done. Also check their arrival and leaving time.", Icons.Default.Checklist),
                    HelpStep("Tap Approve ✅ or Send Back ↩️", "All good? Tap Approve → locked forever. Something wrong? Tap Send Back → they fix and submit again. Add a comment if you like.", Icons.Default.ThumbUp)
                ),
                tips = listOf(
                    "Approved = LOCKED forever. Nobody can change it — not even the employee. Double-check before approving!",
                    "Old reports are saved on your phone. Scroll back to any date to see past work.",
                    "Need to delete many reports? Select them together and delete all in one go."
                )
            )
        )
    ),
    HelpCategory(
        name = "Keep Data Safe",
        emoji = "🔒",
        color = Color(0xFF607D8B),
        topics = listOf(
            HelpTopic(
                id = "backup",
                category = "Keep Data Safe",
                title = "Back up your data",
                description = "So you never lose your records.",
                emoji = "☁️",
                icon = Icons.Default.Backup,
                accountingExplanation = "A backup is a COPY of all your shop data. Phone lost? Broken? Stolen? No problem — restore from backup and everything is back.",
                steps = listOf(
                    HelpStep("Open Settings", "Tap the menu (☰) → Preferences → Save/Load Data.", Icons.Default.Settings),
                    HelpStep("Tap Backup", "Choose where to save: Google Drive (safest) or your phone storage. Tap Backup. Done in 10 seconds!", Icons.Default.Backup),
                    HelpStep("Restore on any phone", "New phone? Install the app, go to Save/Load Data → Restore → pick your backup file. All data comes back!", Icons.Default.CheckCircle)
                ),
                tips = listOf("Do this EVERY WEEK. Takes 10 seconds. One day it could save your ENTIRE shop records. ⏰ Saturday morning = backup time!")
            ),
            HelpTopic(
                id = "invoices",
                category = "Keep Data Safe",
                title = "Print or share a bill",
                description = "Send a clean invoice to your customer.",
                emoji = "🧾",
                icon = Icons.Default.Share,
                accountingExplanation = "Sold something? The app makes a beautiful bill that you can WhatsApp to your customer or print. Add your signature and shop stamp to make it look professional!",
                steps = listOf(
                    HelpStep("Record the sale", "First, make the Sale entry as usual and Save it.", Icons.Default.Save),
                    HelpStep("Open and share", "Open the saved sale. Tap the share icon (📤) at the top. Choose WhatsApp, email, or printer.", Icons.Default.Share),
                    HelpStep("Customer gets the bill", "The bill shows your shop name, items, amount, and your signature. Professional and clean!", Icons.AutoMirrored.Filled.Send)
                ),
                tips = listOf("Go to Preferences to add your signature and shop stamp. Then every bill you send will look like a proper printed one! 🧾✨")
            )
        )
    )
)

// --- HelpScreen UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    
    val allTopicsCount = remember { helpCategories.flatMap { it.topics }.size }

    // Filter topics based on search query
    val filteredCategories = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            helpCategories
        } else {
            helpCategories.map { category ->
                val matchingTopics = category.topics.filter { topic ->
                    topic.title.contains(searchQuery, ignoreCase = true) ||
                            topic.description.contains(searchQuery, ignoreCase = true) ||
                            topic.accountingExplanation.contains(searchQuery, ignoreCase = true) ||
                            topic.steps.any { it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
                }
                category.copy(topics = matchingTopics)
            }.filter { it.topics.isNotEmpty() }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("Help & Support"),
                    canNavigateBack = true,
                    navigateUp = { navController.navigateUp() },
                    showHome = true,
                    navController = navController
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Text(
                    text = TerminologyManager.getTerm("Help & Support Guide 📖"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = TerminologyManager.getTerm("Everything explained step-by-step in plain English for absolute beginners."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── BIG Install Guide Card ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Screen.InstallGuide.route) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.25f)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📱", fontSize = 32.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📥 How to Install the Team App on Employee Phones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Step-by-step pictures. Even if you have NEVER installed an app before — you can do this!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(TerminologyManager.getTerm("Search guides, e.g., 'sale', 'backup'...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = TerminologyManager.getTerm("Clear"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GlassWhite,
                        unfocusedContainerColor = GlassWhite.copy(alpha = 0.05f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            }

            // Quick guides summary
            if (searchQuery.isBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "$allTopicsCount ${TerminologyManager.getTerm("Step-by-Step Manuals")}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = TerminologyManager.getTerm("Tap on any category below to view topics."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Category list
            items(filteredCategories) { category ->
                val isExpanded = searchQuery.isNotEmpty() || expandedCategory == category.name
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = GlassWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, category.color.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Category Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategory = if (expandedCategory == category.name) null else category.name
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = category.emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text(
                                        text = TerminologyManager.getTerm(category.name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${category.topics.size} ${TerminologyManager.getTerm("topics")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = category.color
                            )
                        }

                        // Category Topics (Animated Visibility)
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp))
                                category.topics.forEach { topic ->
                                    HelpTopicItemRow(topic = topic, accentColor = category.color) {
                                        navController.navigate("help_detail/${topic.id}")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fallback for empty search
            if (filteredCategories.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = TerminologyManager.getTerm("No topics matched your search."),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = TerminologyManager.getTerm("Try typing something simpler like 'sales', 'ledger' or 'backup'."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun HelpTopicItemRow(topic: HelpTopic, accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = topic.emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = TerminologyManager.getTerm(topic.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = TerminologyManager.getTerm(topic.description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// Compact "at a glance" sign strip. Replaces the big phone mockup that used to
// fill the screen and force scrolling. A big emoji (clipart) + a flow of step
// icons joined by arrows lets a beginner see the whole action in one look.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicGlance(topic: HelpTopic, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, accentColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(topic.emoji, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = TerminologyManager.getTerm("Do it in one glance 👀"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topic.steps.forEachIndexed { index, step ->
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = TerminologyManager.getTerm(step.title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                    if (index < topic.steps.size - 1) {
                        Box(
                            modifier = Modifier.height(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- HelpDetailScreen UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDetailScreen(navController: NavController, topicId: String) {
    val allTopics = remember { helpCategories.flatMap { it.topics } }
    val topicIndex = remember(topicId) { allTopics.indexOfFirst { it.id == topicId } }
    
    if (topicIndex == -1) {
        // Topic not found fallback
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(TerminologyManager.getTerm("Guide topic not found"), color = Color.White)
        }
        return
    }

    val topic = allTopics[topicIndex]
    val category = remember(topic) { helpCategories.first { it.topics.contains(topic) } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                TopAppBar(
                    title = { AutoShrinkText(TerminologyManager.getTerm(topic.title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TerminologyManager.getTerm("Back"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Category tag
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(category.color.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = category.emoji, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        text = TerminologyManager.getTerm(category.name).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = category.color
                    )
                }
            }

            // Concept Explanation ("What is this?")
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1).copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF1976D2).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = TerminologyManager.getTerm("What is this in simple terms?"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4FC3F7)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = TerminologyManager.getTerm(topic.accountingExplanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // At-a-glance sign strip (clipart + arrows) so the flow is clear in one look
            item {
                TopicGlance(topic = topic, accentColor = category.color)
            }

            // Step-by-Step Title
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TerminologyManager.getTerm("Step-by-Step Instructions"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Steps list
            items(topic.steps.size) { index ->
                val step = topic.steps[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Timeline Connector
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(category.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        if (index < topic.steps.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(72.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(category.color, category.color.copy(alpha = 0.1f))
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Step Card Content
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = category.color,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = TerminologyManager.getTerm(step.title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = TerminologyManager.getTerm(step.description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Pro Tips Section
            if (topic.tips.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD54F).copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFD54F).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = TerminologyManager.getTerm("Pro Tip for Beginners"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            topic.tips.forEach { tip ->
                                Text(
                                    text = "💡 ${TerminologyManager.getTerm(tip)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Navigation Buttons (Prev / Next Guide)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (topicIndex > 0) {
                        val prevTopic = allTopics[topicIndex - 1]
                        Button(
                            onClick = {
                                navController.navigate("help_detail/${prevTopic.id}") {
                                    popUpTo("help_detail/$topicId") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("Prev Guide"), color = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (topicIndex < allTopics.size - 1) {
                        val nextTopic = allTopics[topicIndex + 1]
                        Button(
                            onClick = {
                                navController.navigate("help_detail/${nextTopic.id}") {
                                    popUpTo("help_detail/$topicId") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = category.color)
                        ) {
                            Text(TerminologyManager.getTerm("Next Guide"), color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
