# DA Print

<img src="docs/icon.png" width="96" align="right" alt="DA Print icon">

प्रिंट की दुकानों के लिए सेल्फ-सर्विस प्रिंटिंग। ग्राहक काउंटर पर लगा QR स्कैन करता है, फ़ोन से फ़ाइल भेजता है और टोकन नंबर पाता है। दुकानदार Accept दबाता है और **DA Print Desk** (Windows ऐप) अपने आप प्रिंट कर देता है: B&W ऑर्डर B&W प्रिंटर पर, कलर ऑर्डर कलर प्रिंटर पर। पेमेंट काउंटर पर कैश या UPI से।

Self-service printing for print shops. Customers scan the counter QR code, send files from the phone and get a token. The shop presses Accept and **DA Print Desk** (Windows) prints by itself. Payment at the counter by cash or UPI.

- वेबसाइट / Website: **https://print.datpl.com** (दुकान का अकाउंट: print.datpl.com/dashboard)
- लाइसेंस / Licence: हर दुकान का प्लान DA Workforce → Software licences → **DA Print** में। 15 दिन फ्री ट्रायल।

## डाउनलोड / Download: DA Print Desk

**[DAPrintDesk-1.1.0.zip](https://github.com/datplindia/datpl-software/raw/main/da-print/DAPrintDesk-1.1.0.zip)**: Windows 10 / 11, लगभग 8 MB, इंस्टॉल की ज़रूरत नहीं।

1. ज़िप खोलें। पूरा फ़ोल्डर साथ रखें (`DAPrintDesk.exe` के साथ `x64` और `x86` फ़ोल्डर)।
2. `DAPrintDesk.exe` चलाएँ। Windows "Windows protected your PC" दिखाए तो **More info › Run anyway** दबाएँ।
3. print.datpl.com वाले ईमेल और पासवर्ड से साइन इन करें।
4. B&W प्रिंटर और कलर प्रिंटर चुनें, फिर **Test print** दबाएँ।

Word, Excel और PowerPoint फ़ाइलें कंप्यूटर में लगे Microsoft Office (या मुफ़्त LibreOffice) से PDF बनती हैं।
Unzip, run `DAPrintDesk.exe`, sign in with the print.datpl.com account, choose the printers and press Test print.
ज़िप इसी फ़ोल्डर में है / The ZIP is in this folder: [DAPrintDesk-1.1.0.zip](DAPrintDesk-1.1.0.zip)

## क्या-क्या है / Features

- ग्राहक के लिए कोई ऐप या लॉगिन नहीं: फ़ोन के ब्राउज़र में खुलता है
- PDF, Word, Excel, PowerPoint और फ़ोटो; कॉपी, B&W / कलर, दोनों तरफ़, पेज रेंज
- फ़ोटो लेआउट: 1/2/4 प्रति पेज, पासपोर्ट साइज़ (35×45 mm), आधार / ID कार्ड आगे-पीछे एक पेज पर
- रोज़ 1 से शुरू होने वाले टोकन, TV पर "Ready" टोकन स्क्रीन, A4 QR पोस्टर
- दुकान की UPI ID पर सही रकम का UPI बटन और QR; कोई कमीशन नहीं
- रोज़ की रिपोर्ट (ऑर्डर, पेज, कैश, UPI); प्रिंट के बाद फ़ाइलें अपने आप डिलीट
- अपने प्रिंट टाइप और रेट (जैसे B&W ₹5, कलर ₹15, फ़ोटो पेपर ₹80), हर टाइप का अपना प्रिंटर (B&W / कलर / फ़ोटो)
- नया ऑर्डर आते ही आवाज़ और स्क्रीन पर पॉपअप (Accept बटन के साथ)

## बदलाव / Changes
- **1.1.0** (9 Nov 2026): फ़ोटो प्रिंटर, प्रिंट टाइप के हिसाब से प्रिंटर, नए ऑर्डर पर तेज़ आवाज़ + पॉपअप + टास्कबार ब्लिंक। Photo printer, printer per print type, loud chime + popup for new orders.
- **1.0.0** (8 Nov 2026): पहला वर्जन / First release.

## स्क्रीनशॉट / Screenshots

**ग्राहक: फ़ाइल चुनें, कॉपी और लेआउट तय करें / Customer upload**

<img src="docs/customer-upload.png" width="320" alt="Customer upload page">

**ग्राहक: टोकन नंबर और UPI पेमेंट / Token and UPI payment**

<img src="docs/customer-token.png" width="320" alt="Token and UPI">

**दुकानदार: ऑर्डर डैशबोर्ड / Shop dashboard**

![Dashboard orders](docs/dashboard-orders.png)

**काउंटर पर लगाने वाला A4 QR पोस्टर / Counter QR poster**

![QR poster](docs/qr-poster.png)

---

PDF engine in DA Print Desk: [PDFium](https://pdfium.googlesource.com/pdfium/) (BSD / Apache-2.0), binaries from [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries) chromium/8138.
