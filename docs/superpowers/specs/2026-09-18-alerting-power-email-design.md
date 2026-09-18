# Alerting — Alerte email sur perte d'alimentation 220V

**Date :** 2026-09-18
**Statut :** Validé, en implémentation

## Objectif

Application Android qui surveille l'alimentation secteur (220V) du téléphone.
Dès que le téléphone est débranché du secteur et passe sur batterie, l'application
envoie un email d'alerte à une adresse donnée, puis répète l'envoi toutes les
N minutes tant que le téléphone reste sur batterie. Un email est également envoyé
quand le secteur est rétabli.

L'envoi se fait via un compte Google (SMTP Gmail) dont les identifiants sont
fournis par l'utilisateur.

## Paramètres (fournis via un écran de réglages dans l'app)

| Paramètre | Description | Défaut |
|---|---|---|
| Email d'envoi | Adresse Gmail utilisée pour envoyer | — |
| Mot de passe d'application | Mot de passe d'application Google (16 car.) — stocké chiffré | — |
| Email destinataire | Adresse qui reçoit les alertes | — |
| Fréquence N | Intervalle de renvoi en minutes (entier ≥ 1) | 15 |
| Serveur SMTP | Hôte SMTP | `smtp.gmail.com` |
| Port SMTP | Port SMTP (STARTTLS) | `587` |

## Contrainte d'authentification Google

Google n'autorise plus l'envoi SMTP avec le mot de passe normal du compte.
L'utilisateur doit activer la **validation en 2 étapes** sur son compte Google,
puis générer un **mot de passe d'application** qu'il renseigne dans l'app.

## Architecture

Application native Android, **Kotlin**, build **Gradle**, compilée dans le cloud
via **GitHub Actions** (aucun outil à installer localement ; l'APK est publié en
artefact). `minSdk 26`, `compileSdk 34`, `targetSdk 34`.

### Composants

| Composant | Responsabilité | Dépendances |
|---|---|---|
| `SettingsActivity` | UI de saisie/sauvegarde des réglages, boutons Démarrer/Arrêter | `SettingsStore`, `MonitoringService` |
| `SettingsStore` | Lecture/écriture des réglages ; mot de passe chiffré via `EncryptedSharedPreferences` | androidx.security |
| `AlertSettings` | Data class immuable des réglages + validation | — |
| `MonitoringService` | Foreground service : notification persistante, enregistre le receiver au runtime, tient la boucle timer d'envoi | `PowerConnectionReceiver`, `EmailSender`, `SettingsStore` |
| `PowerConnectionReceiver` | BroadcastReceiver `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` | — |
| `EmailSender` (interface) | Contrat d'envoi d'un email — abstrait pour testabilité | — |
| `SmtpEmailSender` | Implémentation SMTP via Jakarta/JavaMail (`com.sun.mail:android-mail`) | JavaMail |
| `AlertStateMachine` | Logique pure : décide quelle action prendre selon l'état secteur/batterie (testable sans Android) | — |
| `BootReceiver` | `RECEIVE_BOOT_COMPLETED` → relance le service si surveillance active | — |

## Flux de données / logique

1. L'utilisateur saisit les réglages → **Démarrer** → `MonitoringService` démarre en
   foreground et enregistre `PowerConnectionReceiver` au runtime.
2. **Débranchement 220V** (`ACTION_POWER_DISCONNECTED`) → email **immédiat** +
   démarrage d'une boucle coroutine qui renvoie un email **toutes les N minutes**
   tant que sur batterie.
3. **Rebranchement 220V** (`ACTION_POWER_CONNECTED`) → email de **rétablissement** +
   arrêt de la boucle.
4. **Redémarrage du téléphone** → `BootReceiver` relance le service si la
   surveillance était active.

La répétition toutes les N minutes est gérée par une **boucle coroutine dans le
foreground service** (`delay(N)`), ce qui autorise un N arbitraire (dès 1 min),
là où WorkManager impose un minimum de 15 min.

## Contenu de l'email

- Objet : `[ALERTE] <appareil> sur batterie` / `[OK] <appareil> secteur rétabli`
- Corps : type d'événement, horodatage, niveau de batterie, nom de l'appareil.

## Permissions

`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` (Android 13+), `WAKE_LOCK`.

## Gestion des erreurs

- Échec d'envoi SMTP (réseau / identifiants) → log + notification d'erreur +
  nouvelle tentative au cycle suivant.
- Réglages incomplets ou invalides → l'app refuse de démarrer et signale les
  champs fautifs.

## Tests

Tests unitaires JVM (sans device) :
- `AlertStateMachine` : transitions secteur ↔ batterie et actions produites.
- Validation de `AlertSettings` (champs manquants, N invalide, email malformé).

Un `EmailSender` factice permet de tester la logique sans envoi réel.

## Livrable CI

Workflow GitHub Actions (`.github/workflows/build.yml`) : compile un **APK debug**
et le publie en **artefact téléchargeable** à chaque push et sur déclenchement
manuel.

## Points d'attention connus

- **Fiabilité batterie** : certains constructeurs (Samsung, Xiaomi, Huawei…) tuent
  agressivement les services en arrière-plan. Il faut exempter l'app de
  l'optimisation batterie manuellement (documenté dans le README).
- **APK debug non signé** : adapté à un usage personnel (installation directe),
  non distribuable sur le Play Store en l'état.

## Révision 1.1 (ajouts validés le 2026-09-18)

- **Destinataires multiples** : le champ destinataire accepte plusieurs adresses
  séparées par virgule, point-virgule ou retour à la ligne (`AlertSettings.recipientList()`).
- **Alerte batterie faible** (toggle on/off + seuil %) : envoi **unique au
  franchissement** du seuil, **uniquement sur batterie** (pas en charge). Géré via
  un receiver `ACTION_BATTERY_CHANGED` enregistré au runtime dans le service, avec
  un drapeau anti-répétition réinitialisé quand la batterie remonte au-dessus du
  seuil ou repasse en charge.
- **Bouton « Tester l'envoi maintenant »** : envoie un email de test avec les
  valeurs saisies (via `lifecycleScope` + `Dispatchers.IO`) et affiche le résultat.
- **README traduit en anglais** + maquettes SVG de l'app dans `docs/images/`.

## Révision 1.2+ (ajouts ultérieurs)

- **Signature stable + Releases par tag** : APK signé release avec clé committée,
  `versionCode`/`versionName` injectés par la CI, Release GitHub publiée sur tag `v*`.
- **Vérificateur de mise à jour intégré** : `UpdateChecker` (dernière Release) +
  `VersionCompare` (pur) → dialogue « Update available » au lancement.
- **Installation en un geste** : `ApkInstaller` télécharge l'APK et lance
  l'installeur système (`REQUEST_INSTALL_PACKAGES` + FileProvider).
- **UI** : titre « Alerting \<version\> », boutons fixes en bas, champs défilants.
- **Alerte SMS** (toggle) : SMS envoyé **une fois** par événement (perte secteur,
  rétablissement, batterie faible) via `SmsSender` (`SEND_SMS`). Sélecteur de tous
  les pays (drapeau + indicatif auto) et validation du numéro par pays via
  `PhoneValidator`/`Countries` (libphonenumber). Email reste le canal périodique.

## Hors périmètre (YAGNI)

- OAuth2 Google.
- Historique / journal des alertes dans l'UI.
