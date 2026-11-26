# Jarvis (Android app)

Command type karo -> app minimize hota hai -> YouTube / Google / settings wagairah mein kaam khud hota hai.

## APK kaise banayein

**Phone se (bina PC):**
1. github.com par free account banao, naya repo banao.
2. Is zip ke saare files (folder ke andar ka sab kuch, `.github` folder samet) repo mein upload karo.
3. Repo ke "Actions" tab mein "Build APK" chalne do (5-8 minute).
4. Run khatam hone par "Artifacts" se `jarvis-debug-apk` download karo, zip kholo, `app-debug.apk` install karo.

**PC se:** Android Studio mein folder kholo -> Run.

## Pehli baar setup
1. APK install karo, Jarvis kholo.
2. "Accessibility settings kholein" dabao -> Jarvis -> ON.
   Android 13+ par agar option grey ho: Settings > Apps > Jarvis > teen dots (⋮) > **Allow restricted settings**, phir dobara try karo.
3. Battery optimization mein Jarvis ko "Unrestricted" kar do.
4. (Optional) AI mode: switch on karo, Anthropic API key daalo.

## Teen mode
1. **Basic** (AI switch OFF): bina internet, sirf aam sentences.
2. **Plan mode**: AI ON + API key, par Accessibility OFF. AI poora plan ek baar banata hai, screen nahi padhta.
3. **Agent mode** (AI ON + API key + Accessibility ON): asli Jarvis. Loop chalta hai:
   screen padho -> Claude se pucho agla step -> woh step karo -> dobara screen padho -> ... jab tak kaam poora na ho.
   Complex sentences samajhta hai, aur jawab (jaise "aaj ki date") screen se padhkar bolta/dikhata hai.

## Example commands (Agent mode)
- `WhatsApp kholo, "Family" group mein jao, members mein se Ali ko select karo, usko last wali video aur ye text bhejo: "Eid Mubarak"`
- `Google par dekho abhi America (New York) mein time kya hai aur mujhe bata do`
- `YouTube par Imran Khan ki latest speech search karo aur pehli video chala do`
- `Wi-Fi ki setting kholo aur torch on karo`
- Hindi / Urdu / Roman, kisi bhi script mein likh sakte ho.

## WhatsApp wala kaam kaise hota hai
`Playbooks.kt` mein WhatsApp ka poora flow likha hai (contact search, group info -> members -> person -> Message,
attach -> Gallery -> video -> caption -> Send, contact save). Agent ye jaankari AI ko deta hai, isliye wo "kaise jana hai" jaanta hai.
Naye app ka flow sikhana ho to `Playbooks.kt` mein ek section aur likh do.

**Safety:** "Message/video bhejne se pehle mujhse poochho" switch (default ON). Send dabane se pehle ek dialog aata hai:
"Ali ko video + text bhej raha hoon, theek hai?" Haan dabane par hi Send hota hai. Code Send button ko bina confirm ke dabne bhi nahi deta.

## Files
- `CommandParser.kt` - bina internet wala rule-based parser
- `Agent.kt` - Agent loop (screen padho -> AI -> action)
- `Playbooks.kt` - WhatsApp wagairah ka "kaise karna hai" knowledge
- `ConfirmActivity.kt` - bhejne se pehle Haan/Nahi dialog
- `AiPlanner.kt` - Claude API helper + plan mode
- `Executor.kt` - steps chalata hai (intents: apps, search, torch, volume, alarm...)
- `JarvisService.kt` - Accessibility service (screen padhna, tap, type, scroll, home/back, pehli video chalana)
- `MainActivity.kt` - command box, settings, log

## Dhyan rakhein
- Sirf Android par chalta hai. iPhone par dusre apps ko is tarah control karna allowed nahi.
- Code abhi compile/test nahi hua (is environment mein Android SDK nahi tha). Pehli build mein chhoti error aa sakti hai; error message bhejoge to theek kar denge.
- "Pehli video khud chalana" best-effort hai: YouTube ka layout badle to tap fail ho sakta hai. Phone ki language English na ho tab bhi chalna chahiye (size-based detection), par pakka nahi.
- API key phone mein plain storage mein rehti hai.
- Agent mode mein har step par screen ka text (chats, naam, numbers) Anthropic ke API server par jata hai. Bank/OTP/private screens par use na karein.
- Agent kabhi kabhi galat button daba sakta hai ya atak sakta hai (max 30 step, phir ruk jata hai). "Roko" button se kabhi bhi rok sakte ho.
- Har step par ek API call hoti hai, to lamba kaam (WhatsApp wala ~15-25 step) mein thoda kharch aur 1-2 minute lag sakte hain.
- Play Store par Accessibility se aisi automation allow nahi hoti; ye personal use (sideload APK) ke liye hai.
