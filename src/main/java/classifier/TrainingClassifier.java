package classifier;

import database.PubmedDatabase;
import de.datexis.common.Resource;
import de.datexis.encoder.impl.FastTextEncoder;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.earlystopping.EarlyStoppingConfiguration;
import org.deeplearning4j.earlystopping.saver.LocalFileModelSaver;
import org.deeplearning4j.earlystopping.scorecalc.DataSetLossCalculator;
import org.deeplearning4j.earlystopping.termination.MaxEpochsTerminationCondition;
import org.deeplearning4j.earlystopping.termination.MaxTimeIterationTerminationCondition;
import org.deeplearning4j.earlystopping.trainer.EarlyStoppingTrainer;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.PerformanceListener;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import utils.Article;
import utils.CustomHeadingIterator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;


// needs to mount dir under /data with trainingdata
// needs to mount dir under /models to save the classifier
// needs access to port 9876 where the fasttext-embedder is running

public class TrainingClassifier {

    static final int fasttextEmbeddingDim = 100;
    static final String trainPositivePath = "/home/paul/DATEXIS/Training/posTrain.txt";
    static final String trainNegativePath = "/home/paul/DATEXIS/Training/negTrain.txt";
    static final String testPositivePath = "/home/paul/DATEXIS/Training/posTest.txt";
    static final String testNegativePath = "/home/paul/DATEXIS/Training/negTest.txt";


    public static void equalizeCollections(ArrayList<Article> a, ArrayList<Article> b) {
        ArrayList<Article> large, small;
        if (a.size() > b.size()) {
            large = a;
            small = b;
        } else {
            large = b;
            small = a;
        }

        int toRemove = large.size() - small.size();
        for (int i = 0; i < toRemove; ++i) {
            large.remove(large.size() - 1);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String outputpath = args[0];
        final int batchsize = 16;
        final int headingCount = 20;
        final int embeddingSize = 300;

        PubmedDatabase db = new PubmedDatabase();
        ArrayList<Article> posArticles;
        ArrayList<Article> negArticles;
        posArticles = db.getPositiveArticles();
        negArticles = db.getNegativeArticles();
        Collections.shuffle(posArticles);
        Collections.shuffle(negArticles);

        // remove articles from larger collection
        equalizeCollections(posArticles, negArticles);

        db.fillHeadingsByArticleIds(negArticles);
        db.fillHeadingsByArticleIds(posArticles);

        // combine datasets
        ArrayList<Article> allArticles = new ArrayList<>();
        allArticles.addAll(posArticles);
        allArticles.addAll(negArticles);
        Collections.shuffle(allArticles);
        System.out.println("utils.Article-Collection loaded successfully");

        // Test = 10% subset
        ArrayList<Article> test= new ArrayList<>();
        int testCount = (int) Math.round(allArticles.size() * 0.2);
        Random r = new Random();
        for(int i = 0; i < testCount; ++i) {
            test.add(allArticles.remove(r.nextInt(allArticles.size())));
        }

        FastTextEncoder encoder = FastTextEncoder.load(Resource.fromFile("/network-ceph/pgrundmann/FastText/models/cc.en.300.bin"));

        CustomHeadingIterator trainingIterator = new CustomHeadingIterator(encoder,allArticles, batchsize, headingCount);
        CustomHeadingIterator testIterator = new CustomHeadingIterator(encoder,test, batchsize, headingCount);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .updater(new Adam(0.00001))
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .l2(0.0001)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(100 * headingCount)
                        .nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer(1, new DenseLayer.Builder()
                        .nIn(500)
                        .nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer(2, new DenseLayer.Builder()
                        .nIn(500)
                        .nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer(3, new DenseLayer.Builder()
                        .nIn(500)
                        .nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer(4, new DenseLayer.Builder()
                        .nIn(500)
                        .nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer(5, new OutputLayer.Builder()
                        .nIn(500)
                        .nOut(2)
                        .activation(Activation.SOFTMAX)
                        .lossFunction(LossFunctions.LossFunction.MCXENT)
                        .build())
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();

        StatsStorage stats = new InMemoryStatsStorage();
        model.addListeners(new StatsListener(stats, 1), new PerformanceListener(1,true,true));

        UIServer.getInstance().attach(stats);
        UIServer.getInstance().enableRemoteListener(stats, true);

        EarlyStoppingConfiguration esConf = new EarlyStoppingConfiguration.Builder()
                .epochTerminationConditions(new MaxEpochsTerminationCondition(70))
                .iterationTerminationConditions(new MaxTimeIterationTerminationCondition(20, TimeUnit.MINUTES))
                .scoreCalculator(new DataSetLossCalculator(testIterator, true))
                .evaluateEveryNEpochs(1)
                .modelSaver(new LocalFileModelSaver(new File("/models/classifier_early_stopping")))
                .build();


        EarlyStoppingTrainer trainer = new EarlyStoppingTrainer(esConf,model,trainingIterator);
        trainer.fit();

        model.save(new File(outputpath), true);
        System.out.println("Finished");
    }
}