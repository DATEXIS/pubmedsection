package matcher;

import matcher.WikiHeadingMatcher;
import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.sector.reader.WikiSectionReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class WikiSectionMatcher {


    public static void main(String[] args) throws IOException {
     //   Dataset dataset = WikiSectionReader.readDatasetFromJSON(Resource.fromFile("/data/dataset/data/test.json"));
        String testdata = args[0];
        String traindata = args[1];
        String testdataOut = args[2];
        String traindataOut = args[3];
        
        Dataset train_dataset = WikiSectionReader.readDatasetFromJSON(Resource.fromFile(testdata));
        Dataset test_dataset = WikiSectionMatcher.readDatasetFromJSON(Resource.fromFile(traindata))
        AtomicInteger i = new AtomicInteger();
        WikiHeadingMatcher matcher = new WikiHeadingMatcher("t");

        ArrayList<Document> test_docs = test_dataset.streamDocuments()
                .map(d -> {
                    i.getAndIncrement();
                    System.out.println(i.get());
                    try {
                        return matcher.fillLabelsForDocMatrix(d);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<Document> train_docs = train_dataset.streamDocuments()
        .map(d -> {
            i.getAndIncrement();
            System.out.println(i.get());
            try {
                return matcher.fillLabelsForDocMatrix(d);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        })
        .collect(Collectors.toCollection(ArrayList::new));
        Resource path = Resource.fromDirectory(testdataOut);
        ObjectSerializer.writeJSON(test_docs, path);
        path = Resource.fromDirectory(traindataOut);
        ObjectSerializer.writeJSON(train_docs, path);
    }
}
