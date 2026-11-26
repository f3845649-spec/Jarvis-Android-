package com.example.jarvis

/**
 * Agent ko app-specific "kaise karna hai" ki jaankari. Ye text seedha AI ke system prompt mein jata hai.
 * Naye app ka flow sikhana ho to yahan ek aur section likh do. WhatsApp ke buttons ke naam version/language
 * ke hisaab se badal sakte hain, isliye AI se kaha gaya hai ki screen ke asli labels dekhe.
 */
object Playbooks {

    const val WHATSAPP = """PLAYBOOK - WhatsApp (button names vary by version and language; always use the labels you actually see):

A) Message a saved contact or an existing chat:
 1. open_app WhatsApp. If a chat is already open, press back until you see the chat list.
 2. Tap the search icon (magnifier) at the top, then type_into the search field with the person's name.
 3. Tap the matching result. Check that the chat title at the top shows the right person.
 4. type_into the "Message" field with the text, then tap the Send button (label "Send").

B) Send a video (or photo) to the open chat:
 1. Tap the attach button (paperclip, or "+" / "Attach" in newer versions).
 2. Tap "Gallery" (also called "Photos & videos"). If WhatsApp asks for photo/video access, allow it.
 3. Pick the video. If the user did not say which one, take the most recent video: it is normally the first thumbnail, and videos show a duration like 0:23. If the user named a file or date, scroll and look for it.
 4. A preview opens. If the user also wants a text, type_into the caption field ("Add a caption") so video and text go as ONE message, unless the user asked for two separate messages.
 5. Tap Send. Then wait 3-5 seconds and check the chat shows the video as sent (a clock icon means it is still uploading).
 For two separate messages: send the video first, then type_into "Message" and tap Send for the text.

C) The person is only in a group (not in contacts, or you must pick them from the group's members):
 1. open_app WhatsApp, search the group name, open the group chat.
 2. Tap the group name in the top bar to open Group info.
 3. Scroll down to the member list ("Participants" / "N members"). Use its search icon if there is one, or scroll until the person's name or number appears.
 4. Tap the person. In the popup tap "Message <name>" (or just "Message"). A private chat with only that person opens.
 5. Check the chat title shows that person, then continue with A (text) and/or B (video).
 If several members look alike (same name or similar numbers), do NOT guess: finish with done and ask the user which one.

D) Save the contact (only when the user asks to save): in the private chat tap the title bar or the three-dot menu, choose "Add to contacts" / "Add to contact", then "New contact", type the name with type_into into the name field, tap "Save", then go back to WhatsApp.

E) Sending rules: before the final Send tap, make sure the chat title matches the intended recipient. Never send to a group when the user asked for one person, and never send to a person when the user asked for a group."""

    const val GENERAL = """PLAYBOOK - general phone tips:
- To find an app quickly use open_app instead of hunting on the home screen.
- A search box usually needs: tap it, type_into it, then enter (or tap the suggestion / search icon).
- Permission pop-ups: allow only what the current task clearly needs (for example WhatsApp access to photos and videos). Otherwise stop with done and explain.
- If a list is long, scroll down and look again; items you have not seen are not on screen yet.
- Google results often show the answer in a box at the very top (date, time in a country, weather, scores); read it from the screen and finish with done."""

    fun all(): String = GENERAL + "\n\n" + WHATSAPP
}
