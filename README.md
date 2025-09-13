# Moteur de Recommandation de Films
Ce projet est une implémentation d'un moteur de recommandation de films basé sur l'écosystème Big Data d'Apache. Le système utilise l'algorithme de filtrage collaboratif ALS (Alternating Least Squares) pour prédire les préférences des utilisateurs et stocke les données et les recommandations générées dans une base de données NoSQL distribuée.

L'architecture est conçue pour la scalabilité, la haute disponibilité et une gestion de l'environnement de développement simplifiée grâce à la conteneurisation.

## ⚙️ Technologies Utilisées
Apache Spark (avec MLlib) : Moteur d'analyse Big Data rapide, réputé pour son traitement en mémoire et son implémentation optimisée de l'algorithme ALS.

Apache Cassandra : Base de données NoSQL distribuée, choisie pour sa haute disponibilité et sa capacité à gérer de très grands volumes de données.

Docker : Outil de conteneurisation qui simplifie l'installation de Cassandra et garantit la reproductibilité de l'environnement de développement.

Java 8 : Langage de programmation principal du projet, reconnu pour sa robustesse dans l'écosystème Big Data.

Maven : Outil de gestion de projet qui automatise le téléchargement des dépendances nécessaires (Spark, Cassandra).

Dataset MovieLens 20M : Jeu de données de référence contenant plus de 20 millions de notes de films, idéal pour les tests de performance à grande échelle.

## 🎬 Fonctionnalités Clés
Ingestion de Données : Le projet lit les notes d'utilisateurs et les métadonnées de films à partir des fichiers CSV du dataset MovieLens 20M.

Stockage Distribué : Les données sont chargées et stockées de manière persistante dans une base de données Cassandra. Le schéma des tables est optimisé pour des requêtes de lecture rapides en production.

Filtrage Collaboratif ALS : Le modèle de recommandation est entraîné sur les données de notes à l'aide de l'algorithme ALS de Spark MLlib, capable de gérer des matrices de données clairsemées.

Génération et Persistance des Recommandations : Le modèle génère des listes de films recommandés pour chaque utilisateur. Ces recommandations sont ensuite stockées dans une table Cassandra dédiée.

## 🚀 Guide d'Installation et d'Exécution
Pour lancer ce projet sur votre machine, suivez ces étapes de configuration.

### 1. Configuration de l'environnement
Installer Docker Desktop : Assurez-vous que Docker Desktop est installé et en cours d'exécution. Allouez un minimum de 8 Go de RAM à la machine virtuelle Docker (via Settings > Resources > Advanced) pour une performance optimale.

Télécharger le Dataset MovieLens 20M : Téléchargez le fichier ml-20m.zip depuis le site officiel de GroupLens et décompressez-le. Notez le chemin absolu vers le dossier ml-20m.

### 2. Démarrer le conteneur Cassandra
Ouvrez un terminal (comme PowerShell ou Git Bash) dans le dossier de votre projet et exécutez la commande suivante pour démarrer un conteneur Cassandra.

Bash

docker run --name my-cassandra -p 9042:9042 -d -e CASSANDRA_HEAP_SIZE="4G" cassandra:latest
Note : CASSANDRA_HEAP_SIZE="4G" alloue 4 Go de mémoire à Cassandra. Ajustez cette valeur si votre machine a plus ou moins de RAM disponible.

### 3. Créer le Keyspace et les tables
Connectez-vous à votre conteneur Cassandra en utilisant l'outil cqlsh et exécutez les commandes CQL suivantes pour créer l'espace de données et les tables nécessaires au projet.

Bash

docker exec -it my-cassandra cqlsh
Une fois connecté au shell cqlsh, exécutez :

Extrait de code

CREATE KEYSPACE IF NOT EXISTS movielens_ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};
USE movielens_ks;

CREATE TABLE IF NOT EXISTS ratings_by_user (
    user_id int,
    movie_id int,
    rating float,
    timestamp int,
    PRIMARY KEY (user_id, movie_id)
) WITH CLUSTERING ORDER BY (movie_id ASC);

CREATE TABLE IF NOT EXISTS movies (
    movie_id int PRIMARY KEY,
    title text,
    genres text
);

CREATE TABLE IF NOT EXISTS recommendations_by_user (
    user_id int,
    movie_id int,
    score float,
    generated_at timestamp,
    PRIMARY KEY (user_id, score, movie_id)
) WITH CLUSTERING ORDER BY (score DESC, movie_id ASC);

-- Quittez cqlsh
EXIT;
### 4. Exécuter le projet
Ouvrez le projet dans votre Eclipse IDE.

Vérifiez que le fichier pom.xml est à jour et que les dépendances Maven sont bien résolues. Si ce n'est pas le cas, faites un clic droit sur le projet et choisissez Maven > Update Project....

Dans la classe CassandraDataLoader.java, mettez à jour la variable ratingsPath pour qu'elle pointe vers le chemin absolu de votre fichier ratings.csv.

Exécutez la classe CassandraDataLoader.java en tant qu'application Java. Cela va charger les données dans Cassandra.

Une fois le chargement terminé, exécutez la classe MovieRecommendationEngine.java. Cette dernière entraînera le modèle ALS, générera les recommandations et affichera un aperçu des résultats.

## ⚠️ Dépannage des problèmes courants
ReadFailureException ou OutOfMemoryError : Ces erreurs sont généralement dues à un manque de ressources. Augmentez la RAM allouée à Docker Desktop et la variable CASSANDRA_HEAP_SIZE.

HADOOP_HOME and hadoop.home.dir are unset : Cet avertissement se produit sur Windows. Il peut être résolu en téléchargeant les utilitaires Hadoop pour Windows et en configurant la propriété système hadoop.home.dir.
