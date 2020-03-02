package classifier;

import database.PubmedDatabase;
import de.datexis.encoder.impl.FastTextEncoder;
import utils.Article;
import utils.FasttextClient;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// needs to mount dir under /data with trainingdata
// needs to mount dir under /models to save the classifier
// needs access to port 9876 where the fasttext-embedder is running

public class PredictWithPubmedClassifier {


    public static INDArray getEncoding(Article a, FastTextEncoder ft, int headingCount) throws IOException {
        INDArray embedding = Nd4j.zeros(headingCount * 100);

        int count = Math.min(headingCount, a.headings.size());
        for (int i = 0; i < count; ++i) {
            if (a.headings.get(i) != null) {
                INDArray fasttextEmbedding = ft.encode(a.headings.get(i));
                embedding.get(NDArrayIndex.point(0), NDArrayIndex.interval(i * 100, (i + 1) * 100)).assign(fasttextEmbedding);
            }
        }
        return embedding;
    }

    // returns a matrix of embeddings
    public static void getEncodingFasttextClient(FasttextClient client, ArrayList<Article> articles) throws IOException {
        try {
            client.encode(articles);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static INDArray createEmbeddingMatrix(ArrayList<Article> articles) {
        INDArray matrix = Nd4j.zeros(articles.size(), 100);
        for (int i = 0; i < articles.size(); ++i) {
            matrix.getRow(i).assign(articles.get(i).headingEmbedding);
        }
        return matrix;
    }



    private static void doPrediction(ArrayList<Article> articles, MultiLayerNetwork model) {
        // create and fill matrix with concated embeddings
        INDArray predictionMatrix = Nd4j.zeros(articles.size(), 20 * 100);
        for (int i = 0; i < articles.size(); ++i) {
            if (articles.get(i).headingEmbedding == null)
                System.out.println(articles.get(i).articleID + " embedding is null");
            predictionMatrix.getRow(i).assign(articles.get(i).headingEmbedding);
        }

        // do prediction on matrix
        INDArray res = model.output(predictionMatrix);

        // fill confidence-vector for each article based on the resultmatrix
        for (int i = 0; i < articles.size(); ++i) {
            articles.get(i).confidence = res.getRow(i).toFloatVector();
        }
    }


    public static void predictAllArticles() throws IOException {

        // load FastTextEmbeddings
        // for each article get embedding with offset of previous section_count
        // load embeddings for following headings up to headingCount
        // feed embedding into network
        // write res into db

        // Create db connection and load headings
        PubmedDatabase db = new PubmedDatabase();
        HashMap<String, Article> articles = db.getAllArticlesHash();
        System.out.println("Loaded article_ids");
        FasttextClient client = new FasttextClient("10.102.19.71", 9876,300);

        db.fillHeadings(articles);
        System.out.println("Loaded Headings");


        // Load Classifier
        String modelPath = "/models/classifier_2_Shuff_plus_10E";
        MultiLayerNetwork model = MultiLayerNetwork.load(new File(modelPath), false);
        System.out.println("model loaded");


        // Load FasttextEncoder
        //FastTextEncoder encoder = FastTextEncoder.load(Resource.fromFile("/models/fasttextPubmed.bin"));

        final int headingCount = 20;
        long articleCount = 0;

        Iterator<Map.Entry<String, Article>> iter = articles.entrySet().iterator();
        ArrayList<Article> toPush = new ArrayList<>();


        final long initialSize = articles.size();
        Instant startEmbeddingGeneration = Instant.now();
        while(iter.hasNext()) {
            Map.Entry<String, Article> entry = iter.next();
            Article a =entry.getValue();

            //INDArray embedding = getEncoding(a, encoder, headingCount);
            // INDArray embedding = getEncodingTCP(a, headingCount);
            //a.embedding = embedding;
            toPush.add(a);
            // INDArray res = model.output(embedding);
            // a.confidence = res.toFloatVector();

            articleCount++;
            if (toPush.size() == 1000) {
                System.out.println("Start embedding generation now");
                startEmbeddingGeneration = Instant.now();
                getEncodingFasttextClient(client, toPush);
                Instant endEmbeddingGeneration = Instant.now();
                long embeddingGenerationDuration = Duration.between(startEmbeddingGeneration, endEmbeddingGeneration).toMillis();
                System.out.println("Embeddings generated");
                Instant startPrediction = Instant.now();
                doPrediction(toPush, model);
                Instant endPrediction = Instant.now();
                long predictionDuration = Duration.between(startPrediction, endPrediction).toMillis();
                System.out.println("Prediction done");

                Instant startDBWrite = Instant.now();
                db.writeConfidenceToDB_Batch(toPush); // toPush is cleared
                Instant endDBWrite = Instant.now();
                long dbWriteDuration = Duration.between(startDBWrite, endDBWrite).toMillis();
                System.out.println(articleCount + " of " + initialSize + " Articles processed");
                System.out.println("Embedding-Generation: " + embeddingGenerationDuration + " ms");
                System.out.println("Prediction-Duration: " + predictionDuration + " ms");
                System.out.println("DB-Batch-Insert-Duration: " + dbWriteDuration + " ms");
                System.out.println("\n");
            }
        }

        // do last prediction and write to db
        getEncodingFasttextClient(client, toPush);

        doPrediction(toPush, model);
        db.writeConfidenceToDB_Batch(toPush);

        // close connections
        client.closeConnection();
        db.closeConnection();
        System.out.println("FINISHED");
    }



    public static void main(String[] args) throws IOException {
        predictAllArticles();
    }
}
