package com.example.recommender;

import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions; // TRÈS IMPORTANT pour utiliser explode, lit (fonctions utilitaires de Spark SQL)
import org.apache.spark.sql.types.DataTypes; // Pour les types de données de Spark SQL

import java.sql.Timestamp; // Pour le type Timestamp dans Java, utilisé pour 'generated_at'
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors; // Pour Java 8 Streams, utilisé dans les tests

public class MovieRecommendationEngine {

    public static void main(String[] args) {
        // CONSEIL : AJOUTER CE CHEMIN HADOOP_HOME POUR ÉVITER LES AVERTISSEMENTS SPARK SUR WINDOWS
        // System.setProperty("hadoop.home.dir", "C:/hadoop-home"); // Adaptez ce chemin à votre installation de winutils

        // 1. Initialiser SparkSession
        SparkSession spark = SparkSession.builder()
                .appName("MovieRecommendationEngine")
                .config("spark.cassandra.connection.host", "127.0.0.1")
                .config("spark.driver.memory", "4g") // Mémoire pour le driver Spark
                .config("spark.cassandra.connection.timeoutMS", "120000") // Timeout de connexion Cassandra
                .config("spark.cassandra.read.timeout_ms", "180000") // Timeout de lecture Cassandra (important pour gros reads)
                .config("spark.cassandra.input.split.size_in_mb", "64") // Taille des splits de lecture
                .config("spark.cassandra.output.concurrent.writes", "5") // Écritures concurrentes vers Cassandra
                .master("local[*]") // Exécute Spark en mode local
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");
        System.out.println("SparkSession pour le moteur de recommandation initialisée.");

        // --- Lecture des Données depuis Cassandra (les données doivent déjà être chargées par CassandraDataLoader) ---

        // 2a. Lecture des ratings (notes) depuis Cassandra pour l'entraînement ALS
        System.out.println("Lecture des ratings depuis Cassandra (ratings_by_user) pour l'entraînement ALS...");
        Dataset<Row> ratings = spark.read()
                .format("org.apache.spark.sql.cassandra")
                .option("keyspace", "movielens_ks")
                .option("table", "ratings_by_user")
                .load();

        // Renommer les colonnes et caster les types pour qu'elles correspondent à ce qu'ALS attend (userId, movieId, rating)
        ratings = ratings.select(
                ratings.col("user_id").as("userId").cast(DataTypes.IntegerType),
                ratings.col("movie_id").as("movieId").cast(DataTypes.IntegerType),
                ratings.col("rating").cast(DataTypes.FloatType)
        );
        System.out.println("Ratings lus depuis Cassandra pour ALS. Nombre de lignes : " + ratings.count());
        ratings.show(5);
        ratings.printSchema();

        // 2b. Lecture des films depuis Cassandra (utile pour afficher les recommandations par titre)
        System.out.println("Lecture des films depuis Cassandra (movies)...");
        Dataset<Row> movies = spark.read()
                .format("org.apache.spark.sql.cassandra")
                .option("keyspace", "movielens_ks")
                .option("table", "movies")
                .load();
        System.out.println("Films lus depuis Cassandra. Nombre de lignes : " + movies.count());
        movies.show(5);
        movies.printSchema();


        // --- Entraînement du Modèle ALS ---
        System.out.println("Entraînement du modèle ALS...");
        ALS als = new ALS()
                .setMaxIter(10) // Nombre d'itérations pour l'entraînement (peut être augmenté pour plus de précision)
                .setRegParam(0.01) // Paramètre de régularisation (aide à éviter l'overfitting)
                .setUserCol("userId") // Colonne des IDs utilisateur dans le DataFrame 'ratings'
                .setItemCol("movieId") // Colonne des IDs d'élément (films) dans le DataFrame 'ratings'
                .setRatingCol("rating") // Colonne des notes dans le DataFrame 'ratings'
                .setImplicitPrefs(false); // False car nous avons des notes explicites (ratings)

        ALSModel model = als.fit(ratings); // Lance l'entraînement du modèle sur le DataFrame 'ratings'
        System.out.println("Modèle ALS entraîné.");


        // --- Génération et Stockage des Recommandations ---
        System.out.println("Génération des recommandations pour tous les utilisateurs...");
        Dataset<Row> userRecs = model.recommendForAllUsers(10); // Génère les Top 10 recommandations pour chaque utilisateur

        System.out.println("Transformation des recommandations pour le stockage Cassandra...");
        Dataset<Row> formattedRecs = userRecs.select(
                userRecs.col("userId").as("user_id"),
                functions.explode(userRecs.col("recommendations")).as("rec") // 'rec' sera un struct {movieId, rating}
        ).select(
                functions.col("user_id"),
                functions.col("rec.movieId").as("movie_id"), // Extrait le movieId du struct
                functions.col("rec.rating").as("score"), // Extrait le score de recommandation du struct
                functions.lit(new Timestamp(System.currentTimeMillis())).as("generated_at") // Ajoute le timestamp actuel
        );

        System.out.println("Écriture des recommandations dans Cassandra (recommendations_by_user)...");
        formattedRecs.write()
                .format("org.apache.spark.sql.cassandra")
                .mode("overwrite") // "overwrite" va remplacer les anciennes recommandations pour les utilisateurs à chaque exécution
                .option("keyspace", "movielens_ks")
                .option("table", "recommendations_by_user")
                .option("confirm.truncate", "true") // Confirme la troncature pour le mode overwrite
                .save();
        System.out.println("Recommandations écrites dans Cassandra avec succès ! Nombre de recommandations générées : " + formattedRecs.count());


        // --- Tests de Recommandation (Ajouté pour Vérification) ---
        System.out.println("\n--- Démarrage des Tests de Recommandation ---");

        // Lire les recommandations fraîchement stockées dans Cassandra
        System.out.println("Lecture des recommandations depuis Cassandra pour les tests...");
        Dataset<Row> storedRecommendations = spark.read()
                .format("org.apache.spark.sql.cassandra")
                .option("keyspace", "movielens_ks")
                .option("table", "recommendations_by_user")
                .load();

        System.out.println("Recommandations lues pour les tests. Nombre total : " + storedRecommendations.count());
        storedRecommendations.show(5, false); // Afficher les 5 premières lignes du DataFrame sans troncature

        // Liste d'utilisateurs pour les tests (peut être ajustée pour tester différents profils)
        List<Integer> testUserIds = Arrays.asList(1, 10, 100, 500, 1000); // Exemples d'utilisateurs à tester

        for (int userId : testUserIds) {
            System.out.println("\n--- Top 5 recommandations pour l'utilisateur " + userId + " ---");
            // Filtre les recommandations pour l'utilisateur actuel et prend les 5 premières
            Dataset<Row> userRecsFiltered = storedRecommendations
                .filter(storedRecommendations.col("user_id").equalTo(userId))
                .limit(5);

            if (userRecsFiltered.count() > 0) {
                // Joindre avec la table des films pour obtenir les titres complets des films recommandés
                // Utilisation de "left" join pour s'assurer que toutes les recommandations sont affichées même si un titre manque
                Dataset<Row> userRecsWithTitles = userRecsFiltered.join(movies, userRecsFiltered.col("movie_id").equalTo(movies.col("movie_id")), "left")
                                                                   .select(userRecsFiltered.col("user_id"),
                                                                           movies.col("title").as("movie_title"), // Titre du film
                                                                           movies.col("genres").as("movie_genres"), // Genres du film
                                                                           userRecsFiltered.col("score"));

                // Affichage dans un tableau Spark (utile pour les aperçus rapides)
                System.out.println("Affichage tableau Spark :");
                userRecsWithTitles.show(false); // Afficher toutes les colonnes sans troncature

                // Affichage détaillé ligne par ligne dans la console (plus lisible pour des petits sets de résultats)
                System.out.println("Affichage détaillé (texte) pour l'utilisateur " + userId + " :");
                userRecsWithTitles.collectAsList().forEach(row -> {
                    String movieTitle = row.getAs("movie_title") != null ? row.getAs("movie_title").toString() : "Titre inconnu";
                    String movieGenres = row.getAs("movie_genres") != null ? row.getAs("movie_genres").toString() : "Genres inconnus";
                    Float score = row.getAs("score");
                    System.out.println(String.format("  - Film : %s (Genres: %s, Score: %.4f)", movieTitle, movieGenres, score));
                });
            } else {
                System.out.println("Aucune recommandation trouvée pour l'utilisateur " + userId + " (ou cet utilisateur n'a pas été inclus dans les recommandations générées).");
            }
        }

        System.out.println("\n--- Fin des Tests de Recommandation ---");


        // --- Arrêter la session Spark ---
        // Il est crucial d'arrêter la session Spark pour libérer les ressources.
        spark.stop();
        System.out.println("SparkSession arrêtée. Application terminée.");
    }
}
