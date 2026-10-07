# Miga privacy policy

Last updated: 7 October 2026.

Miga is a family recipe book and shopping list app. This document explains what data the app handles, where it goes and what control you have over it.

## Summary

- **Your data lives on your device.** Miga has no user accounts and no server run by the developer.
- **No analytics, ads or tracking.** The app includes no third-party SDKs to measure usage, show ads or identify you.
- **Internet connections are optional.** They only happen when you use a feature that needs them: AI with your own key, Open Food Facts, the packs catalogue, importing from a web page or a sync server you host yourself.
- **We do not sell or share data** for commercial purposes.

## What data the app stores and where

Recipes, books, photos, categories, tags, utensils, ratings, shopping lists, templates, supermarkets and settings are stored **in your device's internal storage**.

The Google Gemini, Anthropic or OpenRouter API keys you enter are stored only on your device and are used only to call the provider you choose.

If Android backup is turned on, the system may include this data in the backup managed by your Google account, as with any other app. Miga has no access to that backup.

## Permissions

- **Internet**: only for the connections described in the next section.
- **Microphone**: to dictate recipe steps and list items. Recognition is done by Android's speech service in the language you choose; Miga receives the text, not the audio, and does not store it.
- **Camera**: to scan product barcodes and QR codes of lists or invitations. The image is processed on the device and is neither stored nor sent. Recipe photos are taken with the system camera app; Miga only receives the resulting file.
- **Notifications**: to show the progress of an AI task that keeps working when you leave the app or turn off the screen, and to tell you when it finishes. You can deny it; the task still completes.
- **Foreground service**: keeps a running AI task alive while it is in the background. It only works while that task lasts and always shows its notification.
- **Biometrics**: if you turn on the lock, verification is done by Android. Miga only receives a "yes" or a "no"; it never sees your fingerprint or face.

## Network connections and who data is shared with

All of them start from an action of yours or a feature you have turned on. None goes through a server of the Miga developer: there is no such server.

1. **AI features (optional, with your own key).** What each feature needs is sent to the provider you set up (**Google Gemini**, **Anthropic Claude** or **OpenRouter**, which in turn forwards it to the provider of the model you choose):
   - **Recipes from photos**: the photos you choose. Cropping the dish photo is then done on your device.
   - **Recipes from a web page**: the text of the page.
   - **Find a recipe with AI**: the name and description of the dish.
   - **Health rating, nutrition estimate and substitutes**: the recipe's ingredients and steps.
   - **Cleaning up dictated text**: the text you dictated.
   - **Ideas**: a summary of your recipes (name, category, tags, time, main ingredients, utensils, whether it is a favourite and how many times you have cooked it), the options you choose, your question and the earlier questions of that conversation.

   Providers are used in the priority order you choose: if one fails or cannot read images, the same request is sent to the next one. How that data is handled is subject to each provider's terms for your key (some free OpenRouter models may use requests for training). Without a key nothing is sent. The OpenRouter model list is downloaded from its public catalogue without sending any of your data. You can turn off all AI features, or only the automatic health rating or nutrition estimate, in Settings → Artificial intelligence. AI-generated content is labelled in the app, may contain mistakes and can be reported.
2. **Open Food Facts.** When you scan a barcode or search for a product by name, that code or text is sent to Open Food Facts (an open database, no account) to get the product's name, photo and details. Product photos are downloaded from its servers. Open Food Facts data is licensed under the ODbL.
3. **Recipe packs catalogue.** When you open the catalogue or install a pack, a public listing and the pack file are downloaded from miga.calamares.org (or from the alternative catalogue you set up). None of your data is sent.
4. **Your own sync server (optional).** If you add a connection to a server you host yourself (miga-server), the books, recipes and photos you link and, if you turn it on, the shopping list (with the name you set as author) are sent only to that server. The app syncs when it opens, periodically in the background and every few seconds while you view a shared list. QR invitations contain the server address, the shared space and an access token: whoever scans the code can read and change that space. If the server uses http:// outside your local network, the app warns you that the connection is not encrypted.
5. **Import from a URL.** The app downloads the page you enter to extract the recipe and, if you use AI, sends its text to the chosen provider (see point 1).
6. **Reports and contact.** If you report AI-generated content or a problem, the app opens your email app with the message to miga@calamares.org already written; you decide whether to send it.

## Crash reports

If the app closes unexpectedly, a text report (app version, device model, Android version and error trace) is saved **only on your device**. When you open the app again you can view it, share it yourself or discard it. Nothing is sent automatically and services such as Crashlytics or Sentry are not used.

## Export and sharing

Exporting (JSON, ZIP, PDF), sharing recipes or lists and backups use the standard Android picker. You choose the destination; Miga does not send those files on its own.

## Retention and deletion

Data is kept while the app is installed. You can:

- delete recipes, books, lists and templates in the app;
- remove your AI keys in Settings;
- delete sync connections (data already on your server is up to you as its administrator);
- delete everything by uninstalling the app or clearing its data in Android settings.

## Children

Miga is not aimed at children under 13 and collects no data that reveals the age of the person using it.

## Changes to this policy

Changes are published in this same document, together with the date of the update. The current version is always in the app (Settings → About → Privacy policy) and at miga.calamares.org/privacy.

## Contact

For privacy questions write to **miga@calamares.org** (also from Settings → Help and support → Report a problem).
