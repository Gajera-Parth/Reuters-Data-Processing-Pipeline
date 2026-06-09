import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * This class reads Reuters news files, extracts relevant information, and stores it in a MongoDB collection.
 */
public class ReutRead {

    /**
     * Main method to execute the Reuters news file processing.
     */
    public static void main(String[] args) {
        // MongoDB connection string
        String connectionString = "mongodb+srv://parthgajera056:reutersdb@reutersdb.xbxpcc9.mongodb.net/ReuterDb";

        // Create a MongoClientURI object
        MongoClientURI mongoClientURI = new MongoClientURI(connectionString);

        // Create a MongoClient using the MongoClientURI
        MongoClient mongoClient = new MongoClient(mongoClientURI);

        // Access the database
        MongoDatabase database = mongoClient.getDatabase(mongoClientURI.getDatabase());

        // MongoDB collection
        MongoCollection<Document> collection = database.getCollection("news");

        // News files
        String[] files = {"reut2-009.sgm", "reut2-014.sgm"};

        // Process each news file
        for (String file : files) {
            processFile(file, collection);
        }

        // Close the MongoClient when done
        mongoClient.close();
    }

    /**
     * Processes a Reuters news file, extracts relevant information, and stores it in the MongoDB collection.
     *
     * @param fileName   Name of the Reuters news file to be processed.
     * @param collection MongoDB collection where the extracted information will be stored.
     */
    private static void processFile(String fileName, MongoCollection<Document> collection) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            StringBuilder reutersBlock = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.contains("<REUTERS")) {
                    reutersBlock.setLength(0); // Clear the StringBuilder for a new Reuters block
                }

                reutersBlock.append(line).append("\n");

                if (line.contains("</REUTERS>")) {
                    // Extract <TITLE> and <BODY> values manually
                    String title = extractValue(reutersBlock.toString(), "TITLE");
                    String body = extractValue(reutersBlock.toString(), "BODY");

                    // Check if both title and body are not null before inserting into MongoDB
                    if (title != null && body != null) {
                        // Create a document to be inserted into MongoDB
                        Document newsDocument = new Document("title", title)
                                .append("body", body);

                        // Insert the document into the collection
                        collection.insertOne(newsDocument);
                    }

                    // Additional check for handling the case where body is null but title is not
                    if (title != null && body == null) {
                        // Log a warning or handle accordingly (this depends on the application's requirements)
                        System.out.println("Warning: Body is null for title - " + title);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts the value of a specified tag from a given block of text.
     *
     * @param block   Text block containing the tag and its value.
     * @param tagName Name of the tag whose value needs to be extracted.
     * @return The extracted value of the tag, or null if the tag is not found or indices are out of bounds.
     */
    private static String extractValue(String block, String tagName) {
        int startIndex = block.indexOf("<" + tagName + ">");
        int endIndex = block.indexOf("</" + tagName + ">");

        // Check if the start and end indices are within the bounds of the string
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return block.substring(startIndex + tagName.length() + 2, endIndex).trim();
        } else {
            // Handle the case where the indices are out of bounds
            return null; // Return null if tag not found or indices out of bounds
        }
    }
}
