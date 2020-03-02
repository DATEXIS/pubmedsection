package matcher;

import database.PubmedDatabase;
import de.datexis.common.Resource;
import de.datexis.encoder.impl.FastTextEncoder;
import de.datexis.model.Document;
import de.datexis.sector.model.SectionAnnotation;
import database.PubmedDatabase;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IAMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class WikiHeadingMatcher {


    private List<String> wikiHeadings;
    private PubmedDatabase db;
    private FastTextEncoder encoder = new FastTextEncoder();
    private int embeddingDimension = 300;
    private INDArray wikiMatrix;
    private HashMap<String, String> wikiHeadingLabels = new HashMap<>();
    HashMap<String, String> wikiLabels = new HashMap<String, String>();

    public WikiHeadingMatcher(String path) throws IOException {
        //encoder = new FastTextEncoder("encoder");
        //client = new FasttextClient("fasttextserver-service", 9876);

        System.out.println("connected");
        //client = new FasttextClient("localhost", 9876);
        //encoder.loadModel(Resource.fromFile(path));
        encoder.loadModel(Resource.fromFile("/network-ceph/pgrundmann/FastText/models/cc.en.300.bin"));
        db = new PubmedDatabase();
        wikiHeadings = db.getWikiSectionHeadings();
        wikiHeadingLabels = db.getWikiHeadingLabels();
        db.closeConnection();
        wikiHeadings = wikiHeadings
                .stream()
                .filter(Objects::nonNull)
                .filter(heading -> heading.trim().length() > 0)
                .collect(Collectors.toList());

        calculateWikiHeadingMatrix();
    }


    // throw away all null embeddings and create matrix from all wikisection headings
    private void calculateWikiHeadingMatrix() throws IOException {
        ArrayList<INDArray> embeddings = new ArrayList<>();
        Iterator it = wikiHeadings.iterator();
        while (it.hasNext()) {
            String heading = (String) it.next();
            heading = cleanStr(heading);
            INDArray embedding = encoder.encode(heading);

            if (embedding == null) {
                System.out.println("Error with wikiheading");
                it.remove();
            } else {
                embeddings.add(embedding);
            }
        }

        if (embeddings.size() != wikiHeadings.size())
            throw new IllegalStateException("wikiheadings and embedding-list have to be the same size");

        wikiMatrix = Nd4j.zeros(embeddingDimension, embeddings.size());

        for (int i = 0; i < embeddings.size(); ++i) {
            wikiMatrix.putColumn(i, embeddings.get(i));// embeddings.get(i));
        }
    }

    public Document fillLabelsForDocMatrix(Document d) throws IOException {
        ArrayList<SectionAnnotation> ans = new ArrayList<>(d.getAnnotations(SectionAnnotation.class));
        fillLabelMatrix(ans);
        return d;
    }

    public Document fillLabelsForDoc(Document d) {
        List<SectionAnnotation> sectionAnnotations = (List<SectionAnnotation>) d.getAnnotations(SectionAnnotation.class);
        for (int i = 0; i < sectionAnnotations.size(); ++i) {
            SectionAnnotation sectionAnnotation = sectionAnnotations.get(i);
            String heading = sectionAnnotation.getSectionHeading();
            try {
                String wikiHeading = getNearestHeading(heading);
                //String label = db.getLabelForHeading(wikiHeading);
                String label = wikiHeadingLabels.get(wikiHeading);

                sectionAnnotation.setSectionLabel(label);
            } catch (Exception e) {
                sectionAnnotation.setSectionLabel("");
                e.printStackTrace();
                System.out.println(e.getMessage());
            }
        }
        return d;
    }

    private String cleanStr(String s) {
        String res = s;
        s = s.replaceAll("[^A-Za-z]+", " ");
        s = s.toLowerCase();
        s = s.trim();
        return s;
    }

    private void fillLabelMatrix(List<SectionAnnotation> annotations) throws IOException {
        INDArray encodingMatrix = Nd4j.create(embeddingDimension, annotations.size());
        int count = 0;
        for (SectionAnnotation sa : annotations) {

            INDArray encoding = encoder.encode(sa.getSectionHeading());
            if (encoding == null)
                encoding = Nd4j.zeros(embeddingDimension);

            encodingMatrix.putColumn(count, encoding);
            count++;
        }

        INDArray distances = Transforms.allCosineDistances(wikiMatrix, encodingMatrix, 0);
        INDArray indices = Nd4j.getExecutioner().exec(new IAMax(distances, 0));
        for (int i = 0; i < annotations.size(); ++i) {
            int index = indices.getInt(i);
            String heading = wikiHeadings.get(index);
            //String label = db.getLabelForHeading(heading);
            String label = wikiHeadingLabels.get(heading);
            String sectionHeading = annotations.get(i).getSectionHeading();
            annotations.get(i).setSectionLabel(label);
        }
    }

    private String getNearestHeading(String heading) throws IOException {
        // encode heading
        heading = cleanStr(heading);
        INDArray pmEmbedding = encoder.encode(heading);
        // calculate cosine-similarity
        INDArray distances = Transforms.allCosineSimilarities(wikiMatrix, pmEmbedding, 0);
        INDArray indexArr = Nd4j.getExecutioner().exec(new IAMax(distances, 0));
        int index = indexArr.getInt(0);
        return wikiHeadings.get(index);
    }


}

