# Alerting — alerte email sur perte d'alimentation 220V

Application Android qui surveille l'alimentation secteur du téléphone. Dès que le
téléphone est débranché du secteur (passage sur batterie), elle envoie un email
d'alerte, puis le renvoie **toutes les N minutes** tant que le téléphone reste sur
batterie. Un email est aussi envoyé quand le secteur est rétabli.

L'envoi se fait via un compte Gmail (SMTP), avec un **mot de passe d'application**.

## Fonctionnalités

- Email immédiat au débranchement du secteur.
- Renvoi périodique toutes les N minutes (N configurable, ≥ 1).
- Email de rétablissement quand le secteur revient.
- Redémarrage automatique de la surveillance après un reboot du téléphone.
- Tous les paramètres réglables dans l'app (email d'envoi, mot de passe, email
  destinataire, fréquence, serveur/port SMTP).
- Mot de passe stocké chiffré (EncryptedSharedPreferences).

## 1. Prérequis Google (mot de passe d'application)

Google n'autorise plus l'envoi SMTP avec le mot de passe normal du compte.

1. Activez la **validation en 2 étapes** sur le compte Gmail d'envoi :
   https://myaccount.google.com/security
2. Générez un **mot de passe d'application** :
   https://myaccount.google.com/apppasswords
3. Copiez les 16 caractères générés : c'est ce que vous saisirez dans l'app
   (champ « Mot de passe d'application »).

## 2. Compiler l'APK (build cloud, sans rien installer)

Le projet compile automatiquement via **GitHub Actions**.

1. Créez un dépôt GitHub et poussez ce projet :
   ```bash
   git remote add origin https://github.com/<vous>/Alerting.git
   git push -u origin main
   ```
2. Ouvrez l'onglet **Actions** du dépôt. Le workflow « Build APK » se lance à
   chaque push (et manuellement via « Run workflow »).
3. À la fin, téléchargez l'artefact **`alerting-debug-apk`** : il contient
   `app-debug.apk`.

## 3. Installer sur le téléphone

1. Transférez `app-debug.apk` sur le téléphone.
2. Autorisez l'installation depuis des sources inconnues, puis installez l'APK.
3. Ouvrez l'app, remplissez les champs, appuyez sur **Démarrer**.
4. Acceptez la demande d'autorisation de **notifications** (Android 13+).

## 4. Fiabilité en arrière-plan (IMPORTANT)

Certains constructeurs (Samsung, Xiaomi, Huawei, Oppo…) coupent agressivement les
services en arrière-plan. Pour garantir l'envoi des alertes :

- Réglages → Applications → **Alerting** → Batterie → **Non restreinte / Autoriser
  l'activité en arrière-plan**.
- Désactivez l'optimisation de batterie pour cette app.

Sans cela, le système peut tuer le service et les emails ne partiront pas.

## 5. Comment ça marche

| Événement | Action |
|---|---|
| Débranchement du secteur | Email immédiat + renvoi toutes les N min |
| Sur batterie | Un email toutes les N minutes |
| Rebranchement du secteur | Email de rétablissement, arrêt des renvois |
| Redémarrage du téléphone | La surveillance reprend si elle était active |

> Remarque : la détection repose sur la connexion/déconnexion de l'alimentation
> (secteur ou USB). Un chargeur 220V correspond bien au cas « alimenté ».

## 6. Développement / tests

Logique métier testée en JVM pure (aucun device requis) :

```bash
gradle testDebugUnitTest
```

- `AlertStateMachine` : transitions secteur ↔ batterie.
- `AlertSettings` : validation des paramètres.

## Structure

```
app/src/main/java/com/sophiaengineering/alerting/
  AlertSettings.kt         # data class + validation (pur)
  AlertStateMachine.kt     # logique de transition (pur)
  EmailSender.kt           # interface
  SmtpEmailSender.kt       # envoi SMTP (JavaMail)
  SettingsStore.kt         # persistance chiffrée
  MonitoringService.kt     # foreground service + boucle timer
  PowerConnectionReceiver.kt
  BootReceiver.kt
  SettingsActivity.kt      # UI de réglages
```

Spec de conception : `docs/superpowers/specs/2026-09-18-alerting-power-email-design.md`
