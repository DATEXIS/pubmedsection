package utils;

import de.datexis.encoder.impl.FastTextEncoder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomHeadingIterator implements DataSetIterator {

    private INDArray inputs, desiredOutputs;
    private int itPosition = 0; // the iterator position in the set.
    private DataSet ds;
    private ArrayList<Article> articles;
    private FastTextEncoder encoder = null;
    private final int batchsize;
    private int embeddingDimension = 0;
    private ArrayList<String> labels = new ArrayList<>(Arrays.asList("nonrelevant", "relevant"));
    private Socket clientSocket = null;
    private int headingCount = 0;


    public CustomHeadingIterator(String filename, int batchsize, int headingCount) {
        this.batchsize = batchsize;
        this.headingCount = headingCount;
        ds = new DataSet(inputs, desiredOutputs);
        ds.load(new File(filename));
        inputs = ds.getFeatures();
        desiredOutputs = ds.getLabels();
    }

    public CustomHeadingIterator(FastTextEncoder encoder, ArrayList<Article> articles, int batchsize, int headingCount) throws IOException {
        this.encoder = encoder;
        this.articles = articles;
        this.batchsize = batchsize;
        this.headingCount = headingCount;
        withFasttextEncoder();
    }

    public CustomHeadingIterator(Socket s, ArrayList<Article> articles, int batchsize, int embeddingDimension, int headingCount) throws IOException {
        this.clientSocket = s;
        this.articles = articles;
        this.batchsize = batchsize;
        this.embeddingDimension = embeddingDimension;
        this.headingCount = headingCount;
        viaTCPIP();
    }

    public CustomHeadingIterator(ArrayList<Article> articles, int batchsize, int embeddingDimension, int headingCount) throws IOException {
        this.clientSocket = null;
        this.articles = articles;
        this.batchsize = batchsize;
        this.embeddingDimension = embeddingDimension;
        this.headingCount = headingCount;
        try {
            viaFastTextClient();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void saveToFile(String filename) {
        ds = new DataSet(inputs, desiredOutputs);
        ds.save(new File(filename));
    }


    private void viaTCPIP() throws IOException {
        inputs = Nd4j.zeros(articles.size(), embeddingDimension * headingCount);
        desiredOutputs = Nd4j.zeros(articles.size(), 2);
        String headingStr = "";
        for (int i = 0; i < articles.size(); ++i) {
            for (int j = 0; j < headingCount; ++j) {
                // calculate current col-start-index
                int colIndex = embeddingDimension * j;
                if (j >= articles.get(i).headings.size()) {
                    // fill with zero veccolIndex, i
                    INDArray embedding = Nd4j.zeros(embeddingDimension);
                    inputs.get(NDArrayIndex.point(i), NDArrayIndex.interval(colIndex, colIndex + 100)).assign(embedding); // eventually change row and col index
                } else {
                    String heading = articles.get(i).headings.get(j);
                    if (heading == null) {
                        clientSocket.close();
                        continue;
                    }
                    if (heading.equals("")) {
                        clientSocket.close();
                        continue;
                    }
                    try {
                        headingStr = "";
                        clientSocket = new Socket("fasttextserver-service", 9876);

                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                        DataInputStream input = new DataInputStream(clientSocket.getInputStream());

                        Article a = articles.get(i);
                        headingStr = heading;
                        headingStr = headingStr.replaceAll("\n", " ");
                        if (headingStr.trim().equals(""))
                            continue;

                        writer.write(headingStr + "\n");
                        writer.flush();

                        float[] res = new float[embeddingDimension];
                        for (int k = 0; k < embeddingDimension; ++k) {
                            res[k] = input.readFloat();
                        }

                        inputs.get(NDArrayIndex.point(i), NDArrayIndex.interval(colIndex, colIndex + embeddingDimension)).assign(Nd4j.create(res));
                        if (a.label.equals("0"))
                            desiredOutputs.putRow(i, Nd4j.create(new float[]{1.f, 0.f}));
                        else
                            desiredOutputs.putRow(i, Nd4j.create(new float[]{0.f, 1.f}));

                        clientSocket.close();

                    } catch (Exception e) {
                        System.out.println(e.getMessage() + " " + i + " von " + articles.size() + "\n"
                                + "Heading: " + headingStr);
                    }
                }
            }
            if (i % 100 == 0)
                System.out.println("utils.Article: " + i);
        }
    }

    private void viaFastTextClient() throws IOException, InterruptedException {
        FasttextClient client = new FasttextClient("fasttextserver-service", 9876, embeddingDimension);
        client.encode(articles);
        inputs = Nd4j.zeros(articles.size(), embeddingDimension * headingCount);
        desiredOutputs = Nd4j.zeros(articles.size(), 2);

        for (int i = 0; i < articles.size(); ++i) {
            Article a = articles.get(i);
            if (a.headingEmbedding == null)
                System.out.println(a.articleID + " embedding is null");
            inputs.getRow(i).assign(a.headingEmbedding);
            if (a.label.equals("0"))
                desiredOutputs.putRow(i, Nd4j.create(new float[]{1.f, 0.f}));
            else
                desiredOutputs.putRow(i, Nd4j.create(new float[]{0.f, 1.f}));
        }
    }


    private void withFasttextEncoder() {
        inputs = Nd4j.zeros(articles.size(), encoder.getEmbeddingVectorSize() * headingCount);
        desiredOutputs = Nd4j.zeros(articles.size(), 2);
        for (int i = 0; i < articles.size(); ++i) {
            Article a = articles.get(i);
            for (int j = 0; j < a.headings.size(); ++j) {
                String heading = a.headings.get(j);
                INDArray headingEmbedding = encoder.encode(heading);
                if (headingEmbedding == null)
                    System.out.println("heading-embedding is null");
                System.out.println("Embedding cols: " + headingEmbedding.columns());
                System.out.println("Embedding rows: " + headingEmbedding.rows());

                System.out.println("Matrix cols: " + inputs.columns());
                System.out.println("Matrix rows: " + inputs.rows());
                inputs.put(new org.nd4j.linalg.indexing.INDArrayIndex[]{NDArrayIndex.interval(j * embeddingDimension, (j + 1) * embeddingDimension), NDArrayIndex.point(i)}, headingEmbedding);
                // inputs.get(NDArrayIndex.point(i), NDArrayIndex.interval(j * embeddingDimension, (j + 1) * embeddingDimension)).assign(headingEmbedding);
            }
            if (a.label.equals("0"))
                desiredOutputs.putRow(i, Nd4j.create(new float[]{1.f, 0.f}));
            else
                desiredOutputs.putRow(i, Nd4j.create(new float[]{0.f, 1.f}));
        }
    }


    private void calculateEmbeddings() throws IOException {
        if (encoder != null)
            withFasttextEncoder();
        else
            viaTCPIP();
    }


    @Override
    public DataSet next(int i) {
        INDArray dsInput = inputs.get(
                NDArrayIndex.interval(itPosition, itPosition + i),
                NDArrayIndex.all());
        INDArray dsDesired = desiredOutputs.get(
                NDArrayIndex.interval(itPosition, itPosition + i),
                NDArrayIndex.all());
        itPosition += i;
        DataSet ds = new DataSet(dsInput, dsDesired);
        ds.shuffle();
        return ds;
    }

    @Override
    public int inputColumns() {
        return inputs.columns();
    }

    @Override
    public int totalOutcomes() {
        return desiredOutputs.columns();
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        itPosition = 0;
    }

    @Override
    public int batch() {
        return this.batchsize;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public List<String> getLabels() {
        return labels;
    }

    @Override
    public boolean hasNext() {
        return (itPosition < inputs.rows());
    }

    @Override
    public DataSet next() {
        return next(batchsize);
    }
}
