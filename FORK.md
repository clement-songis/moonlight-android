# Moonlight Fork

Fork de [moonlight-android](https://github.com/moonlight-stream/moonlight-android), pensé pour piloter un bureau (pas seulement des jeux) depuis un téléphone.

## Ajouts

| Fonction | Détail |
|---|---|
| **Disposition clavier de l'hôte** | Sunshine convertit les touches en positions QWERTY US. Les caractères du clavier tactile sont envoyés à la position qui les produit sur l'hôte (AZERTY français par défaut, QWERTY US, ou texte Unicode). Touches mortes et AltGr gérées (`ê`, `@`, `€`, `{`…). |
| **Clavier tactile fiable** | InputConnection dédiée (mode touches brutes, sans autocorrection), ouverture/fermeture explicites au lieu de `toggleSoftInput()` déprécié, suivi réel de la visibilité du clavier. |
| **Bouton clavier flottant** | Appui : afficher/masquer le clavier. Appui long : réinitialiser le zoom. Déplaçable, colle au bord. |
| **Barre de touches** | Au-dessus du clavier : Esc, Tab, Ctrl, Alt, AltGr, Win, Maj (appui = touche suivante, appui long = verrouillé), flèches, Suppr, Orig, Fin, PgUp/PgDn, Ins, F1–F12. |
| **Tap 3 doigts tolérant** | N'importe quel ordre, jusqu'à 600 ms, petite dérive autorisée, survit aux ACTION_CANCEL des gestes OEM. |
| **Menu d'actions rapides ☰** | Commandes hôte Sunshine : choix de l'écran capturé (Ctrl+Alt+Shift+F1…F12), curseur distant (Ctrl+Alt+Shift+N), plus clavier, zoom et déconnexion. |
| **Pincer pour zoomer** | Jusqu'à ×8 autour des doigts, glisser à deux doigts une fois zoomé, les clics restent précis, la vue remonte au-dessus du clavier. |

Tous les réglages sont dans *Paramètres → Entrée*.

## Build

```sh
nix develop          # JDK 21, SDK 37, build-tools 36/37, NDK 29.0.14206865
./gradlew -PforkAbi=arm64-v8a assembleNonRootRelease
```

Signature (facultative) : passer `-PforkKeystore=… -PforkKeystorePassword=… -PforkKeyAlias=…`. Garder la même clé pour que les mises à jour s'installent par-dessus. Le paquet est `com.limelight.unofficial` (« Moonlight Fork ») et coexiste avec l'application officielle.

## Remotes

`upstream` = moonlight-stream/moonlight-android, branche `master` uniquement, sans tags :

```sh
git remote add upstream https://github.com/moonlight-stream/moonlight-android.git
git config remote.upstream.fetch '+refs/heads/master:refs/remotes/upstream/master'
git config remote.upstream.tagOpt --no-tags
```
