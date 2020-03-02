package exporter;
import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.sector.reader.WikiSectionReader;
import cleaner.PubMedCleaner;
import database.PubmedDatabase;
import matcher.WikiHeadingMatcher;
import redis.clients.jedis.Jedis;
import utils.Article_t;
import utils.CombineAnnotations;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Exporter {

    static String basePath = "";

    public static void main(String[] args) throws ParserConfigurationException, IOException, XPathExpressionException, SQLException, ClassNotFoundException, InterruptedException, ExecutionException {
        String test_data_path = args[0];
        String train_data_path = args[1];
        List<String> test_data_names = null;
        List<String> train_data_names = null;
        PubMedCleaner cleaner = new PubMedCleaner();
        Dataset testDataset = new Dataset();
        Dataset trainDataset = new Dataset();
        try (Stream<Path> walk = Files.walk(Paths.get(test_data_path))) {
            test_data_names = walk.filter(Files::isRegularFile)
                    .map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (Stream<Path> walk = Files.walk(Paths.get(train_data_path))) {
            train_data_names = walk.filter(Files::isRegularFile)
                    .map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String train_file : train_data_names) {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(train_file), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            String xml_content = contentBuilder.toString();
            Document d = cleaner.parsePubMedDocStringToDoc(xml_content);
            CombineAnnotations.mergeSingleDoc(d);
            trainDataset.addDocument(d);
        }

        for (String train_file : test_data_names) {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(train_file), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            String xml_content = contentBuilder.toString();
            Document d = cleaner.parsePubMedDocStringToDoc(xml_content);
            CombineAnnotations.mergeSingleDoc(d);
            testDataset.addDocument(d);
        }
        String testOutput = args[2];
        Resource path = Resource.fromDirectory(testOutput);
        System.out.println("now serialize");
        ObjectSerializer.writeJSON(testDataset, path);

        String trainOutput = args[3];
        path = Resource.fromDirectory(trainOutput);
        System.out.println("now serialize");
        ObjectSerializer.writeJSON(trainDataset, path);

    }
}