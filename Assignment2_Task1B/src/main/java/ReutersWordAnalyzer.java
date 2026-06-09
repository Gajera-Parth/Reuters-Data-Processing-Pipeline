import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spark application for analyzing word frequencies and identifying unique words in Reuters news articles.
 */
public class ReutersWordAnalyzer {

    // Set of common stop words to filter out during word count
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in",
            "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the",
            "their", "then", "there", "these", "they", "this", "to", "was", "will", "with"
    ));

    /**
     * Main method to execute the ReutersWordAnalyzer.
     */
    public static void main(String[] args) {
        // Path to the Reuters news file
        String reutersFile = "reut2-009.sgm";

        try {
            // Set up Spark
            SparkSession spark = SparkSession.builder().appName("ReutersWordAnalyzer").getOrCreate();
            JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

            // Read and preprocess the Reuters news file
            String fileContent = readFileContent(reutersFile);
            String preprocessedText = preprocessText(fileContent);

            // Create an RDD from the preprocessed text
            JavaRDD<String> wordsRDD = sc.parallelize(Arrays.asList(preprocessedText.split(" ")));

            // Tokenize, remove stop words, and count word occurrences
            JavaPairRDD<String, Integer> wordCounts = countWords(wordsRDD);

            // Print unique words with frequency 1
            List<Tuple2<String, Integer>> uniqueWordsResults = printUniqueWords(wordCounts);

            // Output results
            printStatistics(wordCounts, uniqueWordsResults);

            // Stop Spark context
            sc.stop();
            spark.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the content of a file and returns it as a string.
     *
     * @param filePath The path to the file.
     * @return The content of the file as a string.
     * @throws Exception If an error occurs while reading the file.
     */
    private static String readFileContent(String filePath) throws Exception {
        Path file = Paths.get(filePath);
        StringBuilder contentBuilder = new StringBuilder();
        Files.lines(file, StandardCharsets.UTF_8).forEach(line -> contentBuilder.append(line).append("\n"));
        return contentBuilder.toString();
    }

    /**
     * Preprocesses the input text by removing unnecessary characters and converting to lowercase.
     *
     * @param text The input text to be preprocessed.
     * @return The preprocessed text.
     */
    private static String preprocessText(String text) {
        return text
                .replaceAll("&lt;", "")
                .replaceAll("[^a-zA-Z ]", "")
                .replaceAll("\\b\\w\\b", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    /**
     * Counts the occurrences of words in an RDD, filtering out stop words.
     *
     * @param wordsRDD The RDD containing words.
     * @return The pair RDD with word counts.
     */
    private static JavaPairRDD<String, Integer> countWords(JavaRDD<String> wordsRDD) {
        return wordsRDD
                .flatMapToPair(line -> Arrays.asList(line.split(" ")).stream()
                        .filter(word -> !isStopWord(word))
                        .map(word -> new Tuple2<>(word, 1))
                        .iterator())
                .reduceByKey(Integer::sum);
    }

    /**
     * Checks if a word is a stop word.
     *
     * @param word The word to check.
     * @return True if the word is a stop word, false otherwise.
     */
    private static boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }

    /**
     * Prints unique words with frequency 1 and returns the results.
     *
     * @param wordCounts The pair RDD with word counts.
     * @return The list of unique words with frequency 1.
     */
    private static List<Tuple2<String, Integer>> printUniqueWords(JavaPairRDD<String, Integer> wordCounts) {
        JavaPairRDD<String, Integer> uniqueWordsRDD = wordCounts.filter(pair -> pair._2() == 1);
        List<Tuple2<String, Integer>> uniqueWordsResults = uniqueWordsRDD.collect();

        System.out.println("\nUnique Words in the Reuters file are:");
        for (Tuple2<String, Integer> result : uniqueWordsResults) {
            System.out.println(result._1());
        }

        return uniqueWordsResults;
    }

    /**
     * Prints statistics, including total word count, unique word count, and highest/lowest frequency words.
     *
     * @param wordCounts         The pair RDD with word counts.
     * @param uniqueWordsResults The list of unique words with frequency 1.
     */
    private static void printStatistics(JavaPairRDD<String, Integer> wordCounts,
                                        List<Tuple2<String, Integer>> uniqueWordsResults) {
        long uniqueWordsCount = uniqueWordsResults.size();

        System.out.println("\nStatistics:");
        System.out.println("Total Words Count: " + wordCounts.count());
        System.out.println("Unique Words Count: " + uniqueWordsCount);

        Tuple2<String, Integer> highestFrequency = wordCounts.reduce((t1, t2) -> t1._2() > t2._2() ? t1 : t2);
        Tuple2<String, Integer> lowestFrequency = wordCounts.reduce((t1, t2) -> t1._2() < t2._2() ? t1 : t2);

        System.out.println("Highest Frequency Word: " + highestFrequency._1() + ", Count: " + highestFrequency._2());
        System.out.println("Lowest Frequency Word: " + lowestFrequency._1() + ", Count: " + lowestFrequency._2());
    }
}
