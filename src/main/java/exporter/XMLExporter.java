package exporter;

import database.PubmedDatabase;

import java.io.File;

public class XMLExporter {

    public static void main(String[] args) {
        String outputDir = args[0];
        PubmedDatabase db = new PubmedDatabase();
        new File(outputDir + "/test").mkdirs();
        new File(outputDir + "/train").mkdirs();
        // download test_docs
        db.downloadTestData(outputDir + "/test");
        // download the first 50000 docs ordererd by relevance
        db.downloadTrainingData(50000,0,0.0f,outputDir + "/train");
    }


}
