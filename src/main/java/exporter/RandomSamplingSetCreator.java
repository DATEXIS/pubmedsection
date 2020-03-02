package exporter;

import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.sector.reader.WikiSectionReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class RandomSamplingSetCreator {

    // Export 2200 random sampled documents from TeXoo-JSON to TeXoo-JSON
    public static void main(String[] args) throws IOException {
        String training_data_path = args[0];
        String outputpath = args[1];
        Dataset data = WikiSectionReader.readDatasetFromJSON(Resource.fromFile(training_data_path));
        final int count = 2200;
        Random r = new Random();
        HashSet<Document> docs = new HashSet<>();
        ArrayList<Document> dataDocs = new ArrayList<>(data.getDocuments());
        while (docs.size() < count) {
            int rand = r.nextInt(data.countDocuments());
            Document d = dataDocs.get(rand);
            docs.add(d);
        }
        new File(outputpath).mkdirs();
        String outputPathVali = outputpath;
        Resource valPath = Resource.fromDirectory(outputPathVali);
        ArrayList<Document> toSerialize = new ArrayList<>(docs);
        ObjectSerializer.writeJSON(toSerialize, valPath);
    }


}
