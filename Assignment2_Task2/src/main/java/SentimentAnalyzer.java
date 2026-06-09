import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * This class performs sentiment analysis on Reuters news articles and stores the results in a MongoDB collection.
 */
public class SentimentAnalyzer {

    /**
     * Main method to execute the sentiment analysis on Reuters news articles.
     *
     * @param args Command-line arguments (not used in this application).
     */
    public static void main(String[] args) {
        try {
            // Load positive and negative words
            List<String> positiveWords = loadWords("positive-words.txt", 35);
            List<String> negativeWords = loadWords("negative-words.txt", 35);

            // Process news files
            processNewsFile("reut2-009.sgm", positiveWords, negativeWords);
            processNewsFile("reut2-014.sgm", positiveWords, negativeWords);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads words from a file, skipping a specified number of lines.
     *
     * @param filePath   Path to the file containing words.
     * @param skipLines  Number of lines to skip from the beginning of the file.
     * @return List of words read from the file.
     * @throws Exception If an error occurs while reading the file.
     */
    private static List<String> loadWords(String filePath, int skipLines) throws Exception {
        Path wordPath = Paths.get(filePath);
        List<String> wordList = Files.readAllLines(wordPath);
        return wordList.subList(skipLines, wordList.size());
    }

    /**
     * Processes a Reuters news file, performs sentiment analysis, and stores results in MongoDB.
     *
     * @param filename      Name of the Reuters news file to be processed.
     * @param positiveWords List of positive words for sentiment analysis.
     * @param negativeWords List of negative words for sentiment analysis.
     */
    private static void processNewsFile(String filename, List<String> positiveWords, List<String> negativeWords) {
        File file = new File(filename);

        try (Scanner scanner = new Scanner(file);
             MongoClient mongoClient = MongoClients.create("mongodb+srv://parthgajera056:reutersdb@reutersdb.xbxpcc9.mongodb.net/SentimentDb1")) {

            MongoDatabase database = mongoClient.getDatabase("SentimentDb1");
            MongoCollection<Document> collection = database.getCollection("sentimentResults");

            StringBuilder fileData = new StringBuilder();
            while (scanner.hasNextLine()) {
                fileData.append(scanner.nextLine());
            }

            String[] articles = fileData.toString().split("<REUTERS");

            for (int i = 1; i < articles.length; i++) {
                if (articles[i].contains("<TITLE>")) {
                    String title = getTitleFromArticle(articles[i]);

                    if (title != null && !title.isEmpty()) {
                        // Create Bag-of-Words
                        Map<String, Integer> bagOfWords = createBagOfWords(title);

                        // Calculate match score
                        int matchScore = calculateMatchScore(bagOfWords, positiveWords, negativeWords);

                        // Determine polarity
                        String polarity = determinePolarity(matchScore);

                        // Get matched words
                        String matchedWords = getMatchedWords(bagOfWords, positiveWords, negativeWords);

                        // Insert into MongoDB
                        insertIntoMongoDB(collection, i, title, matchedWords, matchScore, polarity);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts the title from a Reuters article.
     *
     * @param article The Reuters article text.
     * @return The title of the article, or null if not found.
     */
    private static String getTitleFromArticle(String article) {
        String title = article.substring(article.indexOf("<TITLE>") + "<TITLE>".length(), article.indexOf("</TITLE>")).trim();
        return (title != null && !title.isEmpty()) ? title : null;
    }

    /**
     * Creates a Bag-of-Words representation for a given text.
     *
     * @param title The text for which the Bag-of-Words is created.
     * @return The Bag-of-Words representation as a map of words to their counts.
     */
    private static Map<String, Integer> createBagOfWords(String title) {
        Map<String, Integer> bagOfWords = new HashMap<>();
        String[] words = title.toLowerCase().split("\\s+");

        for (String word : words) {
            // Remove non-alphabetic characters
            word = word.replaceAll("[^a-zA-Z]", "");

            // Increment count in the Bag-of-Words
            bagOfWords.put(word, bagOfWords.getOrDefault(word, 0) + 1);
        }
        return bagOfWords;
    }

    /**
     * Calculates the sentiment match score for a Bag-of-Words representation.
     *
     * @param bagOfWords     The Bag-of-Words representation of the text.
     * @param positiveWords  List of positive words for sentiment analysis.
     * @param negativeWords  List of negative words for sentiment analysis.
     * @return The sentiment match score.
     */
    private static int calculateMatchScore(Map<String, Integer> bagOfWords, List<String> positiveWords, List<String> negativeWords) {
        int matchScore = 0;

        for (Map.Entry<String, Integer> entry : bagOfWords.entrySet()) {
            String word = entry.getKey();
            int count = entry.getValue();

            if (positiveWords.contains(word)) {
                matchScore += count;
            } else if (negativeWords.contains(word)) {
                matchScore -= count;
            }
        }

        return matchScore;
    }

    /**
     * Retrieves the words that contributed to the sentiment match.
     *
     * @param bagOfWords     The Bag-of-Words representation of the text.
     * @param positiveWords  List of positive words for sentiment analysis.
     * @param negativeWords  List of negative words for sentiment analysis.
     * @return A string containing the matched words.
     */
    private static String getMatchedWords(Map<String, Integer> bagOfWords, List<String> positiveWords, List<String> negativeWords) {
        StringBuilder matchedWordsBuilder = new StringBuilder();

        for (Map.Entry<String, Integer> entry : bagOfWords.entrySet()) {
            String word = entry.getKey();

            if (positiveWords.contains(word) || negativeWords.contains(word)) {
                matchedWordsBuilder.append(word).append(", ");
            }
        }

        return matchedWordsBuilder.toString().replaceAll(",\\s*$", "");
    }

    /**
     * Determines the polarity (positive, negative, or neutral) based on the sentiment match score.
     *
     * @param matchScore The sentiment match score.
     * @return The determined polarity.
     */
    private static String determinePolarity(int matchScore) {
        if (matchScore > 0) {
            return "Positive";
        } else if (matchScore < 0) {
            return "Negative";
        } else {
            return "Neutral";
        }
    }

    /**
     * Inserts sentiment analysis results into MongoDB.
     *
     * @param collection   The MongoDB collection to insert into.
     * @param newsNumber   The news number.
     * @param title        The title of the article.
     * @param matchedWords The words contributing to the sentiment match.
     * @param matchScore   The sentiment match score.
     * @param polarity     The determined polarity.
     */
    private static void insertIntoMongoDB(MongoCollection<Document> collection, int newsNumber, String title, String matchedWords, int matchScore, String polarity) {
        Document document = new Document("News#", newsNumber)
                .append("Title", title)
                .append("Match", matchedWords)
                .append("Score", matchScore)
                .append("Polarity", polarity);

        collection.insertOne(document);
    }
}
