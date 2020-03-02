package exporter;

import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.model.Sentence;
import de.datexis.sector.reader.WikiSectionReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;

public class SentEvalExporter {

    // Stores 2200 random sampled documents for SentEval
    public static void main(String[] args) throws IOException {
        String training_data_path = args[0];
        String output_path = args[1];
        Dataset dataset = WikiSectionReader.readDatasetFromJSON(Resource.fromFile(training_data_path));
        ArrayList<Document> smallDataset = new ArrayList<>();
        dataset.randomizeDocuments();
        ArrayList<Document> trainLarge = new ArrayList<>(dataset.getDocuments());
        trainLarge = trainLarge.stream().filter(
                d -> {
                    int maxTokens = 0;
                    for (Sentence s : d.getSentences()) {
                        if (s.getTokens().size() > maxTokens)
                            maxTokens = s.getTokens().size();
                    }
                    return (maxTokens < 300);
                }
        ).collect(Collectors.toCollection(ArrayList::new));

        Random random = new Random();
        while (smallDataset.size() < 2200) {
            int index = random.nextInt(trainLarge.size());
            Document d = trainLarge.get(index);
            smallDataset.add(d);
            trainLarge.remove(index);
        }
        ObjectSerializer.writeJSON(smallDataset, Resource.fromFile(output_path));
    }


}
