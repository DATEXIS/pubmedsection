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
        Dataset dataset = WikiSectionReader.readDatasetFromJSON(Resource.fromFile("/home/paul/DATEXIS/PubMedSection_Dataset/test.json"));
        AtomicInteger i = new AtomicInteger();
        WikiHeadingMatcher matcher = new WikiHeadingMatcher("t");


        ArrayList<Document> docs_arr = dataset.streamDocuments()
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

        //Resource path = Resource.fromDirectory("/data/dataset/data/v2/test_gpu.json");
        Resource path = Resource.fromDirectory("/home/paul/DATEXIS/PubMedSection_Dataset/test_2.json");
        ObjectSerializer.writeJSON(docs_arr, path);

    }
}
