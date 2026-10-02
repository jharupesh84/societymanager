# Society Manager (Android App)

A modern, offline-first Native Android application written in **Kotlin** with **Jetpack Compose** and **Room SQLite Database**. Built specifically for residential societies to record **Monthly Maintenance**, track festival collections (**Navratri Collection**, or custom events) with **zero impact on monthly maintenance**, manage expenditures, and export formatted **Monthly & Block-wise PDF/CSV Reports** directly to the phone's memory.

---

## 📱 Key Highlights

1. **Admin Login Page**:
   * Initial screen prompting for administrative credentials.
   * **Username**: `Admin` (case-insensitive)
   * **Password**: `Admin123`
   * Always prompts for login on app launch.

2. **Dedicated Fund / Category Selector (On Login)**:
   * Prompted right after login to select which fund to record or manage:
     * 🏢 **Monthly Maintenance** (Regular flat maintenance & society operations)
     * 🪔 **Navratri Collection** (Garba, sound system, pooja & celebrations)
     * ➕ **+ Add Event / Another Festival** (Click to add any custom fund such as Ganpati, Diwali, Holi, Sports Day, etc.)
   * **Complete Ring-Fencing**: Festival collections and expenses are completely isolated and **never affect monthly maintenance**.

3. **Fast Category Switcher in Top Bar**:
   * Switch the active fund anytime with 1 tap from the top bar "Switch Fund" button or by tapping the Home icon without logging out.

4. **Monthly Maintenance Tracking & Month Selector**:
   * Record maintenance payments for any specific month (e.g., October 2026, September 2026).
   * Smart Flat Input (`B-504`, `A 101`, `C203`) with auto block & flat extraction.
   * Payment mode selection: **Online (UPI/Bank)** or **Cash**.

5. **Monthly & Block-Wise Reports (with Expenses)**:
   * Filter reports by **Specific Month** (e.g. October 2026) or **All Months**.
   * Report options:
     * **Collections: Whole Society (All Blocks)**
     * **Collections: Block A, Block B, etc.**
     * **Expenses: Society Expenses**
     * **Consolidated: Full Financial Statement** (combines collections, block breakdowns, expenses, and net surplus/deficit).
   * Generates formatted PDF directly to the phone's **Downloads** folder and launches the Android share sheet.
   * Also supports **CSV Export**.

6. **Separate Festival Financial Statements**:
   * Switch to Navratri Collection to view festival collections, expenses, remaining surplus balance, and export dedicated festival reports.

---

## 📁 Project Structure

```
SocietyApp/
├── app/
│   ├── build.gradle.kts                       # Outputs societytracker.apk
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/society/app/
│   │   │   ├── MainActivity.kt                # Login, Welcome & Main navigation flow
│   │   │   ├── SocietyApplication.kt
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── SocietyDao.kt          # Category & Month filtered Room queries
│   │   │   │   │   └── SocietyDatabase.kt     # Room database v3
│   │   │   │   ├── model/
│   │   │   │   │   ├── CollectionEntity.kt    # category, monthYear, block, flatNo, amount, paymentMode
│   │   │   │   │   ├── ExpenseEntity.kt       # category, monthYear, detail, amount, date
│   │   │   │   │   ├── FundCategory.kt        # Maintenance, Navratri & custom events
│   │   │   │   │   └── BlockSummary.kt
│   │   │   │   └── repository/
│   │   │   │       └── SocietyRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── LoginScreen.kt         # Admin / Admin123 login
│   │   │   │   │   ├── WelcomeScreen.kt       # Fund Selector (Maintenance, Navratri & Add Event)
│   │   │   │   │   ├── MainScreen.kt          # TopBar with Category Switcher & Tabs
│   │   │   │   │   ├── CollectionScreen.kt    # Month selector, smart flat input & Cash/Online
│   │   │   │   │   ├── ExpenseScreen.kt       # Expense form with month selector & history
│   │   │   │   │   └── ReportScreen.kt        # Monthly filter, block breakdown, PDF & CSV export
│   │   │   │   ├── theme/
│   │   │   │   └── viewmodel/
│   │   │   │       └── SocietyViewModel.kt    # Multi-fund state & reactive flows
│   │   │   └── util/
│   │   │       ├── DateUtil.kt                # Month-year formatting & generator
│   │   │       ├── PdfExporter.kt             # Branded PDF generator (A4)
│   │   │       └── CsvExporter.kt             # CSV generator & Share intent
│   │   └── res/
└── gradle/
```

---

## 🚀 How to Run & Generate APK in Android Studio

1. Open **Android Studio**.
2. Click **File > Open...** and choose `/Users/rupeshjha/SocietyApp`.
3. Wait for Gradle sync to complete.
4. **To Run on Phone/Emulator**:
   * Click **Run (▶)**.
   * Sign in with:
     * **Username**: `Admin`
     * **Password**: `Admin123`
   * Select your active collection fund (**Monthly Maintenance** or **Navratri Collection**), or click **+ Add Event** to create another.
5. **To Build APK**:
   * Click **Build > Generate App Bundles or APKs > Generate APKs**.
   * Output will be generated at `app/build/outputs/apk/debug/societytracker.apk`.
