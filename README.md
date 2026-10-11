# TP 6 — Verrouillage optimiste et gestion des conflits

Solution du laboratoire **149** du cours [Hibernate & JPA](https://mliaedu.toubkalit.com/student/courses/28). Deux contextes de persistance modifient la même réservation, le second changement est refusé, puis une stratégie bornée recharge la version courante et réapplique l'intention de modification.

## Exécuter

Prérequis : **JDK 17**, **Maven 3.9+** et accès à Maven Central au premier lancement.

```sh
mvn clean verify
mvn exec:java
```

`com.example.App` exécute les deux scénarios. Ils peuvent aussi être lancés séparément :

```sh
mvn exec:java -Dexec.mainClass=com.example.ConcurrentReservationSimulator
mvn exec:java -Dexec.mainClass=com.example.ConcurrentReservationWithRetry
```

Les versions de l'énoncé sont conservées : `javax.persistence-api:2.2`, `hibernate-core:5.4.30.Final`, `h2:1.4.200`. Les tests utilisent JUnit 5. La base H2 est en mémoire et part d'un état neuf à chaque exécution ; `create-drop` remplace le `update` proposé pour rendre les résultats reproductibles.

## Couverture de l'énoncé

| Exigence | Réalisation |
|---|---|
| Projet Maven, JPA/Hibernate/H2 | `pom.xml`, unité `reservationPU`, classe explicitement déclarée |
| Entité `com.example.model.Reservation` | Table `reservations`, salle et dates obligatoires, identifiant `IDENTITY`, champ `int version` annoté `@Version` |
| Service de réservation | `ReservationService` et `ReservationServiceImpl` : créer, modifier, chercher par ID, lister |
| Conflit concurrent | `ConcurrentReservationSimulator` : deux `EntityManager` et transactions distincts lisent la même version |
| Exception et rollback | Le deuxième flush provoque `OptimisticLockException`; transaction annulée avant fermeture |
| Explication SQL | SQL des mises à jour affiché : condition `where id=? and version=?` |
| Gestion du conflit avec retry | `ReservationRetryHandler`, au maximum 3 tentatives avec rechargement entre les essais |
| Validation du retry | `ConcurrentReservationWithRetry` : snapshot détaché obsolète, reprise et vérification de `Salle C` et de la date souhaitée |
| Bonnes pratiques | Transactions courtes, ressources fermées, détection des exceptions emballées au commit, vérification de la borne et des validations métier |

## Pourquoi modifier les exemples indicatifs

Dans le fragment de service de l'énoncé, charger la version courante avec `find` puis lui copier les valeurs d'un ancien objet ne compare pas la version de cet ancien objet. Ici, `merge(snapshot)` conserve la version du snapshot détaché et permet à Hibernate de refuser la modification obsolète.

L'exemple de retry rechargeait l'objet puis lui réaffectait sa propre salle, ce qui perdait `Salle C`. Ici, la salle, le début et la fin souhaités sont mémorisés **avant** le rechargement et réappliqués à chaque nouveau snapshot. La politique de cet exercice consiste donc à réappliquer l'ensemble des valeurs demandées après relecture. Une application métier peut choisir de demander une résolution à l'utilisateur au lieu de cette politique.

Après un échec, un nouveau `EntityManager` est utilisé. Le service annule toute transaction encore active et ferme son contexte dans tous les cas. `Locking.optimisticCause` reconnaît également une exception optimiste emballée dans `RollbackException` lors du commit. Le champ `version` n'a pas de setter : Hibernate le gère.

## Résultats réellement obtenus

Premier scénario : deux contextes chargent **version 0**. La transaction 1 enregistre **Salle B, version 1**. La transaction 2 tente **Salle C** avec l'ancienne version ; son flush est refusé et la transaction est annulée. Une lecture indépendante retrouve **Salle B, version 1** : le changement gagnant a été conservé.

Deuxième scénario : après le même conflit sur des snapshots détachés, le handler recharge l'objet, réapplique **Salle C** et la nouvelle fin **2024-06-02T12:00**, puis enregistre **version 2**. La liste finale contient **2 réservations**, une par scénario. Les dates sont fixes et la démonstration n'utilise ni attente arbitraire ni aléatoire pour ordonner les écritures.

**8 tests réussis, 0 échec, 0 erreur, 0 ignoré.** Ils contrôlent création/lecture/listage/incrément de version, concurrence réelle entre contextes, refus d'un snapshot obsolète et reprise du service après rollback, conservation des valeurs lors du retry, exactement 3 tentatives et 2 relectures en cas de conflits permanents, disparition de l'objet, validation métier et exception emballée au commit. Les conflits permanents et l'objet disparu sont des doubles de service explicites ; les scénarios principaux utilisent réellement Hibernate et H2.

Les [logs de démonstration](evidence/demo-output.txt), les [logs de tests](evidence/test-output.txt) et les [rapports JUnit](evidence/junit/) sont conservés. Les captures ci-dessous montrent des rapports dans un navigateur construits à partir de ces sorties ; elles ne sont pas des captures directes du terminal.

![Conflit et résolution par retry](screenshots/results.png)

![Résultats des tests](screenshots/tests.png)

Le [rapport des résultats](evidence/report.html) peut être ouvert directement dans un navigateur. Les variantes de type de version, l'interface web et le verrouillage pessimiste évoqués en conclusion sont des pistes supplémentaires à explorer.

Référence technique consultée : [guide officiel Hibernate 5.4 — verrouillage optimiste](https://docs.hibernate.org/orm/5.4/userguide/html_single/#locking-optimistic).

## Régénérer le rapport et les captures

Les images sont des captures navigateur de résultats réellement exécutés, conservés dans `evidence/`. Les tests proviennent des XML Surefire, également archivés dans `evidence/junit/`. Les empreintes SHA-256 des sources figurent dans `evidence/checksums.json`.

Avec Python 3 et Node.js disponibles, depuis la racine du dépôt :

```sh
mvn clean verify > evidence/test-output.txt 2>&1
mvn exec:java > evidence/demo-output.txt 2>&1
python scripts/generate_report.py
npm install --no-save playwright
npx playwright install chromium
node scripts/capture-results.cjs
```

Le rapport se consulte aussi directement dans `evidence/report.html`. Les captures générées sont `screenshots/results.png` et `screenshots/tests.png`.
