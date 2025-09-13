package com.example.recommender;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.StructField;

public class CassandraDataLoader {

    public static void main(String[] args) {
        // CONSEIL : AJOUTER CE CHEMIN HADOOP_HOME POUR ÉVITER LES AVERTISSEMENTS SPARK SUR WINDOWS
        // System.setProperty("hadoop.home.dir", "C:/hadoop-home"); // Adaptez ce chemin à votre installation de winutils

        // 1. Initialiser SparkSession
        SparkSession spark = SparkSession.builder()
                .appName("CassandraDataLoader")
                .config("spark.cassandra.connection.host", "127.0.0.1")
                .config("spark.driver.memory", "4g") // Mémoire allouée au Driver Spark (peut être ajustée)
                .master("local[*]")
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");
        System.out.println("SparkSession pour le chargement des données initialisée.");

        // --- Définition des schémas pour les fichiers CSV MovieLens 20M ---
        StructType ratingsSchema = new StructType(new StructField[]{
                DataTypes.createStructField("userId", DataTypes.IntegerType, false),
                DataTypes.createStructField("movieId", DataTypes.IntegerType, false),
                DataTypes.createStructField("rating", DataTypes.FloatType, false),
                DataTypes.createStructField("timestamp", DataTypes.LongType, false)
        });

        StructType moviesSchema = new StructType(new StructField[]{
                DataTypes.createStructField("movieId", DataTypes.IntegerType, false),
                DataTypes.createStructField("title", DataTypes.StringType, false),
                DataTypes.createStructField("genres", DataTypes.StringType, false)
        });

        // --- Chargement des Données depuis CSV et Stockage dans Cassandra ---

        // Configurer le nombre de lignes à prendre (1 million pour cet exemple)
        long numberOfRowsToLoad = 1_000_000L; // 1 million de lignes de notes
        // Pour les films, le dataset 20M en a environ 27k, nous les prendrons tous sauf si spécifié.

        // 1a. Lecture de ratings.csv (limitée à 1 million) et écriture dans Cassandra (ratings_by_user)
        System.out.println("Lecture de 'ratings.csv' depuis le disque local (limitée à " + numberOfRowsToLoad + " lignes)...");
        Dataset<Row> initialRatingsCsv = spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(ratingsSchema)
                // ADAPTEZ CE CHEMIN à l'emplacement réel de votre fichier ratings.csv (MovieLens 20M)
                .csv("C:/Users/User/Desktop/MASTER/drive/calcul parallele/projet/data/ml-20m/ratings.csv")
                .limit((int) numberOfRowsToLoad); // <<--- CORRECTION ICI : Conversion en 'int'

        System.out.println("Écriture des ratings dans Cassandra (table 'ratings_by_user')...");
        initialRatingsCsv.selectExpr("userId as user_id", "movieId as movie_id", "rating", "timestamp")
               .write()
               .format("org.apache.spark.sql.cassandra")
               .mode("overwrite")
               .option("confirm.truncate", "true") // Confirme la troncature pour le mode overwrite
               .option("keyspace", "movielens_ks")
               .option("table", "ratings_by_user")
               .save();
        System.out.println("Ratings écrits dans Cassandra. Nombre total de lignes : " + initialRatingsCsv.count());


        // 1b. Lecture de movies.csv (entier) et écriture dans Cassandra (movies)
        System.out.println("Lecture de 'movies.csv' depuis le disque local...");
        Dataset<Row> initialMoviesCsv = spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(moviesSchema)
                // ADAPTEZ CE CHEMIN à l'emplacement réel de votre fichier movies.csv (MovieLens 20M)
                .csv("C:/Users/User/Desktop/MASTER/drive/calcul parallele/projet/data/ml-20m/movies.csv");
                // Pas de .limit() ici, car la table movies est petite (environ 27k films)

        System.out.println("Écriture des films dans Cassandra (table 'movies')...");
        initialMoviesCsv.selectExpr("movieId as movie_id", "title", "genres")
              .write()
              .format("org.apache.spark.sql.cassandra")
              .mode("overwrite")
              .option("confirm.truncate", "true")
              .option("keyspace", "movielens_ks")
              .option("table", "movies")
              .save();
        System.out.println("Films écrits dans Cassandra. Nombre total de lignes : " + initialMoviesCsv.count());

        // --- Vérification du Stockage en lisant depuis Cassandra et affichant les premières lignes ---

        System.out.println("\n--- Vérification du chargement dans Cassandra ---");

        // Vérification de la table 'ratings_by_user'
        System.out.println("\nContenu de la table 'ratings_by_user' (5 premières lignes lues depuis Cassandra) :");
        Dataset<Row> storedRatings = spark.read()
                .format("org.apache.spark.sql.cassandra")
                .option("keyspace", "movielens_ks")
                .option("table", "ratings_by_user")
                .load();
        storedRatings.show(5); // Affiche les 5 premières lignes
        storedRatings.printSchema(); // Affiche le schéma

        // Vérification de la table 'movies'
        System.out.println("\nContenu de la table 'movies' (5 premières lignes lues depuis Cassandra) :");
        Dataset<Row> storedMovies = spark.read()
                .format("org.apache.spark.sql.cassandra")
                .option("keyspace", "movielens_ks")
                .option("table", "movies")
                .load();
        storedMovies.show(5, false); // false pour ne pas tronquer les titres
        storedMovies.printSchema();

        // --- Arrêter la session Spark ---
        spark.stop();
        System.out.println("SparkSession arrêtée. Chargement des données terminé.");
    }
}
