import json
import re

excel_raw = """
BLOCK :- A,Month -,,,,BLOCK :- B,Month -,,,,BLOCK :- C,Month -,,
,,,,,,,,,,,,,
Flat No,Name,Amount,Mode,,Flat No,Name,Amount,Mode,,Flat No,Name,Amount,Mode
101,USHA MISHRA,,,,101,B K SINGH,,,,101,AJAY KUMAR GUPTA,,
102,NO,,,,102,B K SINGH,,,,102,PRINCE,,
103,RISHI,,,,103,SAPNA BEN,,,,103,TULSI BHAI,,
201,D D BEHERA,,,,104,RAMANJIT KAUR,,,,104,TARA BEN,,
202,S K SAHOO,,,,201,KALPANA VERMA,,,,201,P N SINGH,,
203,JAISWAL RATILAL,,,,202,R N S YADAV,,,,202,RAMESH BHAI,,
204,RAMESH CHAND,,,,203,ABHISHEKH AVADH,,,,203,MAHENDRA PAL,,
205,CHANDAN,,,,204,MAKHIJA JI,,,,204,Vasu Bhagtani,,
206,KAJAL,,,,301,S N SAHU,,,,301,N KUSHWAHA,,
301,SANTILAL,,,,302,PARAMJEET SINGH,,,,302,M BARIYA,,
302,PURANMAL,,,,303,S N CHUADHARY,,,,303,ANUJ KUMAR,,
303,ARUN BHAI,,,,304,CHANDAN PRASAD,,,,304,M THAKUR,,
304,MUKESH,,,,401,PARUL BISWAS,,,,401,DESHRAJ SINGH,,
305,ANIL SINGH,,,,402,Rajesh Harwani,,,,402,B N SINGH,,
306,VINU MAKWANA,,,,403,G C SARKAR,,,,403,BALDEV SINGH,,
401,REHAN SHAIKH,,,,404,D K YADAV,,,,404,SHIV SINGH,,
402,DHANO BHAI (RENTOR),,,,501,PANDIT JI,,,,501,PRAKSH PANDIT JI,,
403,DEVRAJ PARMAR,,,,502,JITENDRA JI,,,,502,B K SINGH,,
404,G K MANSWALA,,,,503,D BAISHYA,,,,503,RAMDUTT SHUKLA,,
405,DIPIKA SONI,,,,504,RUPESH JHA,,,,504,JULI BEN SHAHNI,,
406,H C SINGH,,,,,,,,,,,,
501,SK MISHRA,,,,,,,,,,,,
502,KAMLESH BEN,,,,,,,,,,,,
503,NARENDRA BHAI,,,,,,,,,,,,
,,,,,,,,,,,,,
BLOCK :- D,Month -,,,,BLOCK :- E,Month -,,,,BLOCK :- F,Month -,,
,,,,,,,,,,,,,
Flat No,Name,Amount,Mode,,Flat No,Name,Amount,Mode,,Flat No,Name,Amount,Mode
101,VIJAY KUMAR,,,,101,KAMLESH BHAI,,,,101,RAKHI PANDEY,,
102,TONA BEN,,,,102,ANIL KIRAD,,,,102,MAHAVEER BHAI,,
103,PANDIT JI,,,,103,RAMESHBHAI VALA,,,,103,ASHISH BHAI,,
104,SUMESH KUMAR,,,,104,TRIPATHI JI,,,,104,APARNAGA,,
201,DEEPALI MONDAL,,,,201,CHUNNU KAKA,,,,201,SACHIN BHAI,,
202,GIRESH ROHERA,,,,202,KONAL BARIYA,,,,202,AMRIK SINGH,,
203,VEENA PANDEY,,,,203,BIKASH JIT,,,,203,RAJESH SHARMA,,
204,ANIL KIRADE,,,,204,TOSUF BHAI,,,,204,ANKIT SINGH,,
301,NIRAJ,,,,301,HIMMAT SINGH,,,,301,PARESH BHAI,,
302,SANDIP,,,,302,MANISHA BEN,,,,302,VINOD KUMAR,,
303,AKASH SONI,,,,303,MAMMA,,,,303,GOPAL BHAI,,
304,BHARAT SINGH,,,,304,HARESH DAWANI,,,,304,SANDEEP KUMAR,,
401,VINOD PARMAR,,,,401,GIRISH BHAI,,,,401,JAGAT NARYAN,,
402,ARPIT BAVEJA,,,,402,G SARKAR,,,,402,A K SINGH,,
403,MADAN SHARMA,,,,403,PRAVIN VAGALA,,,,403,ROHIT SINGH,,
404,VINOD TIWARI,,,,404,JADHAV SIR,,,,404,KRISHNA,,
501,SAROJ KJUMAR,,,,501,ROHIT SINGH,,,,501,DEEPA BEN,,
502,BHAGWAT PANDEY,,,,502,RAKESH KUMAR,,,,502,NEHA BEN,,
503,PRIYA RAMJEET,,,,503,DEEPAK BHAI,,,,503,KRISHNA BHAI,,
504,ELA PAL,,,,504,DIPALI,,,,504,NICK,,
,,,,,,,,,,,,,
,,,,,,,,,,,,,
BLOCK :- G,Month -,,,,,,,,,,,,
,,,,,,,,,,,,,
Flat No,Name,Amount,Mode,,,,,,,,,,
101,RAM BHAI,,,,,,,,,,,,
102,RAHUL CHAVDA,,,,,,,,,,,,
103,DHARMENDRA SHAH,,,,,,,,,,,,
104,VIJAY PATRA,,,,,,,,,,,,
201,DEEPA DHANVANI,,,,,,,,,,,,
202,PUSHPA TINDWANI,,,,,,,,,,,,
203,SANJAY MISHRA,,,,,,,,,,,,
204,JITENDRA KUMAR,,,,,,,,,,,,
301,RONKI CHANDRA,,,,,,,,,,,,
302,KAMLESH AGGARWAL,,,,,,,,,,,,
303,PRATAP BHAI,,,,,,,,,,,,
304,NITIN,,,,,,,,,,,,
401,ARVIDA KONAR,,,,,,,,,,,,
402,SAVITA UDAY KAMBLE,,,,,,,,,,,,
403,BIRENDRA KUMAR,,,,,,,,,,,,
404,VIRENDRA SINGH,,,,,,,,,,,,
501,SANTOSH KUMAR,,,,,,,,,,,,
502,ARVIND BHAI,,,,,,,,,,,,
503,RAJKUMAR DUBEY,,,,,,,,,,,,
504,SAVITA DEVI,,,,,,,,,,,,
"""

# PDF data (101 records)
pdf_records = [
    # Page 1 (42 records)
    ("A-203", "Jaiswal Ratilal"),
    ("A-204", "Dhananjay Deore"),
    ("A-205", "Pooja Kumari"),
    ("A-206", "Keshwan"),
    ("A-301", "Rajiv Singh"),
    ("A-305", "Anil Kumar"),
    ("A-306", "Vinu Bhai Makwana"),
    ("A-404", "G K Mansawala"),
    ("A-501", "Santosh Mishra"),
    ("A-503", "Narendar Singh Chauhan"),
    ("A-101", "Rahul Singh"),
    ("A-201", "DD Bhera + Rentor"),
    ("A-202", "Sanjay Kumar Sahoo"),
    ("B-101", "B K Singh"),
    ("B-104", "Anmol Sandhu"),
    ("B-201", "Kalpana Verma"),
    ("B-202", "RNS Yadav"),
    ("B-203", "Abhishek Awadh"),
    ("B-301", "S N Sahu"),
    ("B-302", "Paramjit Bhai"),
    ("B-304", "Chandan Prasad"),
    ("B-402", "Rajesh Harwani"),
    ("B-403", "G C Sarkar"),
    ("B-503", "D Baishya"),
    ("B-303", "Pawan Chaudhary"),
    ("B-401", "Parul Biswas"),
    ("B-502", "Jitendra Ji"),
    ("B-504", "Rupesh Jha"),
    ("C-101", "Ajay Gupta"),
    ("C-102", "Prince Chelani"),
    ("C-103", "Kalpesh Anand"),
    ("C-104", "Tara Ben"),
    ("C-202", "Ramesh bhatia"),
    ("C-203", "Mahendra Pal"),
    ("C-302", "D Bariya"),
    ("C-303", "Anuj Kumar"),
    ("C-304", "Maheshwar Thakur"),
    ("C-401", "Deshraj Yadav"),
    ("C-402", "BN Singh"),
    ("C-403", "Baldev Singh"),
    ("C-404", "Jagdish Barfa"),
    ("C-501", "Mamta Sonu Singh"),
    # Page 2 (42 records)
    ("C-502", "D Veerababu"),
    ("C-504", "Nitish Jha / Niranjan Sahni"),
    ("C-201", "Prabhunath Singh"),
    ("C-204", "Vasu Bhagtani"),
    ("C-301", "Narad Kushwah"),
    ("C-503", "Shuklaji"),
    ("D-201", "Ashok Kumar Mondal"),
    ("D-202", "Neelam Matreja"),
    ("D-203", "Veena Pandey"),
    ("D-204", "Jyoti Kirsha"),
    ("D-301", "Niraj Nandan"),
    ("D-302", "Sandip"),
    ("D-303", "Dipika"),
    ("D-304", "Bharat Sinh"),
    ("D-501", "Indrajeet Kumar"),
    ("D-502", "Bhawat Dp"),
    ("D-503", "Priya Singh Rajput"),
    ("E-102", "Anil Kirad"),
    ("E-104", "Narendra Tripathi"),
    ("E-202", "Kunal Baraiya"),
    ("E-203", "Tandra Day"),
    ("E-301", "Himmat Sinh"),
    ("E-302", "Manisha Kariya"),
    ("E-303", "Pankaj Gupta"),
    ("E-402", "Gumpa Sankar"),
    ("E-403", "Pravin Vaghela"),
    ("E-404", "Sachin Kumar"),
    ("E-503", "Deepak Wadile"),
    ("E-504", "Dipali Roshan Tayde"),
    ("E-502", "Rakesh Kumar Pal"),
    ("F-102", "Mahavir Rathore"),
    ("F-104", "Deepak Haldkar"),
    ("F-301", "Paresh Bhai"),
    ("F-302", "Vinod Kumar"),
    ("F-303", "Gopal Bhai"),
    ("F-401", "Jagat Naryan Prajapati"),
    ("F-402", "Rohit Singh"),
    ("F-404", "Krishna Kant"),
    ("F-501", "Jeetu Chanchalani"),
    ("F-101", "Rakhi Pandey"),
    ("G-101", "Ram Bhai"),
    ("G-103", "Jaypalsingh Chauhan"),
    # Page 3 (17 records)
    ("G-104", "Vijay Kumar Patra"),
    ("G-202", "Pushpa Tindwani"),
    ("G-203", "Sanjay Mishra"),
    ("G-204", "Ginza Lian"),
    ("G-301", "Patil Vijay"),
    ("G-304", "Nikdot (Ranju Devi)"),
    ("G-401", "Arabinda Konar"),
    ("G-402", "Savita Uday Kamble"),
    ("G-403", "Neha Dlip"),
    ("G-404", "Abhishek Batham"),
    ("G-501", "Sargar Ganesh"),
    ("G-502", "Arvind Rajput"),
    ("G-504", "Sakshi Nigam"),
    ("G-102", "Rahul Chavada"),
    ("G-302", "Kamlesh Agrawal"),
    ("G-303", "Pratap Bhai"),
    ("G-503", "RK Dubey"),
]

# Parse excel_raw
# Sections:
# Section 1: A, B, C
# Section 2: D, E, F
# Section 3: G

excel_dict = {}

# Let's extract lines
lines = [l.strip() for l in excel_raw.strip().split("\n") if l.strip()]

# Let's write manual parse for each block
blocks_data = {
    "A": [
        ("101", "USHA MISHRA"), ("102", "NO"), ("103", "RISHI"),
        ("201", "D D BEHERA"), ("202", "S K SAHOO"), ("203", "JAISWAL RATILAL"),
        ("204", "RAMESH CHAND"), ("205", "CHANDAN"), ("206", "KAJAL"),
        ("301", "SANTILAL"), ("302", "PURANMAL"), ("303", "ARUN BHAI"),
        ("304", "MUKESH"), ("305", "ANIL SINGH"), ("306", "VINU MAKWANA"),
        ("401", "REHAN SHAIKH"), ("402", "DHANO BHAI (RENTOR)"), ("403", "DEVRAJ PARMAR"),
        ("404", "G K MANSWALA"), ("405", "DIPIKA SONI"), ("406", "H C SINGH"),
        ("501", "SK MISHRA"), ("502", "KAMLESH BEN"), ("503", "NARENDRA BHAI")
    ],
    "B": [
        ("101", "B K SINGH"), ("102", "B K SINGH"), ("103", "SAPNA BEN"), ("104", "RAMANJIT KAUR"),
        ("201", "KALPANA VERMA"), ("202", "R N S YADAV"), ("203", "ABHISHEKH AVADH"), ("204", "MAKHIJA JI"),
        ("301", "S N SAHU"), ("302", "PARAMJEET SINGH"), ("303", "S N CHUADHARY"), ("304", "CHANDAN PRASAD"),
        ("401", "PARUL BISWAS"), ("402", "Rajesh Harwani"), ("403", "G C SARKAR"), ("404", "D K YADAV"),
        ("501", "PANDIT JI"), ("502", "JITENDRA JI"), ("503", "D BAISHYA"), ("504", "RUPESH JHA")
    ],
    "C": [
        ("101", "AJAY KUMAR GUPTA"), ("102", "PRINCE"), ("103", "TULSI BHAI"), ("104", "TARA BEN"),
        ("201", "P N SINGH"), ("202", "RAMESH BHAI"), ("203", "MAHENDRA PAL"), ("204", "Vasu Bhagtani"),
        ("301", "N KUSHWAHA"), ("302", "M BARIYA"), ("303", "ANUJ KUMAR"), ("304", "M THAKUR"),
        ("401", "DESHRAJ SINGH"), ("402", "B N SINGH"), ("403", "BALDEV SINGH"), ("404", "SHIV SINGH"),
        ("501", "PRAKSH PANDIT JI"), ("502", "B K SINGH"), ("503", "RAMDUTT SHUKLA"), ("504", "JULI BEN SHAHNI")
    ],
    "D": [
        ("101", "VIJAY KUMAR"), ("102", "TONA BEN"), ("103", "PANDIT JI"), ("104", "SUMESH KUMAR"),
        ("201", "DEEPALI MONDAL"), ("202", "GIRESH ROHERA"), ("203", "VEENA PANDEY"), ("204", "ANIL KIRADE"),
        ("301", "NIRAJ"), ("302", "SANDIP"), ("303", "AKASH SONI"), ("304", "BHARAT SINGH"),
        ("401", "VINOD PARMAR"), ("402", "ARPIT BAVEJA"), ("403", "MADAN SHARMA"), ("404", "VINOD TIWARI"),
        ("501", "SAROJ KJUMAR"), ("502", "BHAGWAT PANDEY"), ("503", "PRIYA RAMJEET"), ("504", "ELA PAL")
    ],
    "E": [
        ("101", "KAMLESH BHAI"), ("102", "ANIL KIRAD"), ("103", "RAMESHBHAI VALA"), ("104", "TRIPATHI JI"),
        ("201", "CHUNNU KAKA"), ("202", "KONAL BARIYA"), ("203", "BIKASH JIT"), ("204", "TOSUF BHAI"),
        ("301", "HIMMAT SINGH"), ("302", "MANISHA BEN"), ("303", "MAMMA"), ("304", "HARESH DAWANI"),
        ("401", "GIRISH BHAI"), ("402", "G SARKAR"), ("403", "PRAVIN VAGALA"), ("404", "JADHAV SIR"),
        ("501", "ROHIT SINGH"), ("502", "RAKESH KUMAR"), ("503", "DEEPAK BHAI"), ("504", "DIPALI")
    ],
    "F": [
        ("101", "RAKHI PANDEY"), ("102", "MAHAVEER BHAI"), ("103", "ASHISH BHAI"), ("104", "APARNAGA"),
        ("201", "SACHIN BHAI"), ("202", "AMRIK SINGH"), ("203", "RAJESH SHARMA"), ("204", "ANKIT SINGH"),
        ("301", "PARESH BHAI"), ("302", "VINOD KUMAR"), ("303", "GOPAL BHAI"), ("304", "SANDEEP KUMAR"),
        ("401", "JAGAT NARYAN"), ("402", "A K SINGH"), ("403", "ROHIT SINGH"), ("404", "KRISHNA"),
        ("501", "DEEPA BEN"), ("502", "NEHA BEN"), ("503", "KRISHNA BHAI"), ("504", "NICK")
    ],
    "G": [
        ("101", "RAM BHAI"), ("102", "RAHUL CHAVDA"), ("103", "DHARMENDRA SHAH"), ("104", "VIJAY PATRA"),
        ("201", "DEEPA DHANVANI"), ("202", "PUSHPA TINDWANI"), ("203", "SANJAY MISHRA"), ("204", "JITENDRA KUMAR"),
        ("301", "RONKI CHANDRA"), ("302", "KAMLESH AGGARWAL"), ("303", "PRATAP BHAI"), ("304", "NITIN"),
        ("401", "ARVIDA KONAR"), ("402", "SAVITA UDAY KAMBLE"), ("403", "BIRENDRA KUMAR"), ("404", "VIRENDRA SINGH"),
        ("501", "SANTOSH KUMAR"), ("502", "ARVIND BHAI"), ("503", "RAJKUMAR DUBEY"), ("504", "SAVITA DEVI")
    ]
}

total_excel = sum(len(v) for v in blocks_data.values())
print(f"Total flats in Excel: {total_excel}")
print(f"Total flats in PDF: {len(pdf_records)}")

# Build pdf map
pdf_map = {}
for flat_code, name in pdf_records:
    # flat_code is like "A-203"
    pdf_map[flat_code] = name

# Reconcile
reconciled = []
mismatches = []
excel_only = []

for block, flats in blocks_data.items():
    for flat_no, excel_name in flats:
        flat_key = f"{block}-{flat_no}"
        if flat_key in pdf_map:
            pdf_name = pdf_map[flat_key]
            if pdf_name.strip().lower() != excel_name.strip().lower():
                mismatches.append((flat_key, excel_name, pdf_name))
            chosen_name = pdf_name
        else:
            excel_only.append((flat_key, excel_name))
            chosen_name = excel_name
        
        reconciled.append({
            "block": block,
            "flatNo": flat_no,
            "flatKey": flat_key,
            "ownerName": chosen_name
        })

print(f"Total reconciled: {len(reconciled)}")
print(f"Mismatches / PDF overrides ({len(mismatches)}):")
for k, e, p in mismatches:
    print(f"  {k}: Excel '{e}' -> PDF '{p}'")

print(f"\nExcel-only flats ({len(excel_only)}):")
for k, e in excel_only:
    print(f"  {k}: '{e}'")

# Check if any in PDF were not found in Excel
all_reconciled_keys = {r["flatKey"] for r in reconciled}
pdf_unmatched = [k for k in pdf_map if k not in all_reconciled_keys]
print(f"PDF keys not in Excel ({len(pdf_unmatched)}): {pdf_unmatched}")

with open("/tmp/reconciled_flat_owners.json", "w") as f:
    json.dump(reconciled, f, indent=2)

print("\nReconciled JSON saved successfully to /tmp/reconciled_flat_owners.json")
