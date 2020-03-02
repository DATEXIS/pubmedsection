package exporter;

import de.datexis.common.Resource;
import de.datexis.model.*;
import de.datexis.sector.model.SectionAnnotation;
import de.datexis.sector.reader.WikiSectionReader;
import org.json.JSONArray;
import org.json.simple.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class PubmedSectionTokenizer {

    // Stores a TeXoo-JSON doc tokenized for SentEval
    public static void main(String[] args) throws IOException {
        JSONArray tokenizedData = new JSONArray();
        String trainingDataPath = args[0]; // training-json
        String outputPath = args[1]; // tokenized output path
        Dataset data = WikiSectionReader.readDatasetFromJSON(Resource.fromFile(trainingDataPath));
        HashSet<String> labels = new HashSet<>();

        for (Document d : data.getDocuments()) {
            JSONObject docjson = new JSONObject();
            JSONArray sentenceArray = new JSONArray();
            for (Sentence s : d.getSentences()) {
                JSONObject sentenceObject = new JSONObject();

                Collection<SectionAnnotation> annotations = d.getAnnotations(Annotation.Source.GOLD, SectionAnnotation.class);
                if(annotations.size() > 0) {
                    annotations.stream().limit(1).forEach(a -> {
                        sentenceObject.put("Label", a.getSectionLabel().replaceFirst("disease.", ""));
                        labels.add(a.getSectionLabel().replaceFirst("disease", ""));
                    });
                } else {
                    sentenceObject.put("Label","other");
                }
                JSONArray tokenArray = new JSONArray();
                for (Token t : s.getTokens()) {
                    tokenArray.put(t.toString());
                }
                sentenceObject.put("Tokens", tokenArray);
                sentenceArray.put(sentenceObject);
            }
            docjson.put("Sentences", sentenceArray);
            tokenizedData.put(docjson);
        }

        FileWriter writer = new FileWriter(outputPath);
        writer.write(tokenizedData.toString());
        writer.close();

        for(String s : labels) {
            System.out.println(s);
        }


    }


}
