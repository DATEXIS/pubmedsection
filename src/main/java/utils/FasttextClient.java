package utils;


import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FasttextClient {

    private String ip;
    private int port = -1;
    private Socket s = null;
    private int embeddingDimension = 100;
    static ExecutorService service = Executors.newFixedThreadPool(32);

    DataInputStream input = null;
    DataOutputStream output = null;


    public FasttextClient(String ip, int port, int embeddingDimension) throws IOException {
        this.ip = ip;
        this.port = port;
        this.embeddingDimension = embeddingDimension;
        s = new Socket(ip, port);

        input = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
    }

    public INDArray encode(String s) throws IOException {
        checkHeadingAndRemoveLinebreaks(s);
        byte[] strBytes = s.getBytes();
        int length = strBytes.length;
        output.writeByte(0); // encode cmd
        output.writeInt(length);
        output.write(strBytes);
        output.flush();

        float[] res = new float[300];
        for (int j = 0; j < res.length; ++j) {
            res[j] = input.readFloat();
        }
        INDArray embedding = Nd4j.create(res);
        return embedding;

    }


    // encodes single article and sets its embedding member-variable
    public void encode(Article a) throws IOException {
        a.headingEmbedding = Nd4j.zeros(20 * this.embeddingDimension);
        int max = Math.min(a.headings.size(), 20);
        for (int i = 0; i < max; ++i) {
            String heading = a.headings.get(i);
            if (checkHeadingAndRemoveLinebreaks(heading)) {
                byte[] strBytes = heading.getBytes();
                int length = strBytes.length;
                output.writeByte(0); // encode cmd
                output.writeInt(length);
                output.write(strBytes);
                output.flush();

                float[] res = new float[this.embeddingDimension];
                for (int j = 0; j < res.length; ++j) {
                    res[j] = input.readFloat();
                }

                INDArray embedding = Nd4j.create(res);
                a.headingEmbedding.get(NDArrayIndex.point(0), NDArrayIndex.interval(i * this.embeddingDimension, (i + 1) * this.embeddingDimension)).assign(embedding);
            }
        }
    }

    private static boolean checkHeadingAndRemoveLinebreaks(String heading) {
        if (heading == null)
            return false;
        heading = heading.replaceAll("\n", "");
        return !heading.trim().equals("");
    }

    public void closeConnection() throws IOException {
        output.writeByte(1); // close cmd
        s.close();
    }

    public ArrayList<INDArray> encode(int z, List<String> strings) throws InterruptedException {
        service = Executors.newFixedThreadPool(8);
        Instant start = Instant.now();

        BlockingQueue<String> consumeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<INDArray> produceQueue = new LinkedBlockingQueue<>();


        Runnable r = () -> {
            try {
                FasttextClient client = new FasttextClient(ip, port, embeddingDimension);
                while (consumeQueue.size() > 0) {
                    String s = consumeQueue.take();
                    INDArray res = client.encode(s);
                    produceQueue.add(res);
                }
                client.closeConnection();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        };

        List<Runnable> futures = new ArrayList<>();
        for (int i = 0; i < 32; ++i)
            service.execute(r);

        service.shutdown();
        service.awaitTermination(60, TimeUnit.MINUTES);

        strings.clear();
        ArrayList<INDArray> res =new ArrayList<>();
        produceQueue.drainTo(res);
        return res;


    }


    public void encode(List<Article> articles) throws IOException, InterruptedException {
        //   for (Article a : articles)
        //       encode(a);
        service = Executors.newFixedThreadPool(32);
        Instant start = Instant.now();

        BlockingQueue<Article> produceQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Article> consumeQueue = new LinkedBlockingQueue<>(articles);

        Runnable r = () -> {
            try {
                FasttextClient client = new FasttextClient(ip, port, embeddingDimension);
                while (consumeQueue.size() > 0) {
                    Article a = consumeQueue.take();
                    client.encode(a);
                    //produceQueue.add(a);
                }
                client.closeConnection();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        };

        List<Runnable> futures = new ArrayList<>();
        for (int i = 0; i < 32; ++i)
            service.execute(r);

        service.shutdown();
        service.awaitTermination(60, TimeUnit.MINUTES);

        // articles.clear();
        // articles.addAll(produceQueue);
        Instant end = Instant.now();
        long d = Duration.between(start, end).toMillis();

        System.out.println("Duration: " + d + " ms");
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        // test client

        ArrayList<Article> articles = new ArrayList<>();
        for (int i = 0; i < 10000; ++i) {
            Article a = new Article("1");
            a.headings.add("test");
            a.headings.add("test 2 test");
            a.headings.add("abc abc");
            articles.add(a);
        }

        FasttextClient ft = new FasttextClient("localhost", 9876, 300);
        Instant start = Instant.now();
        ft.encode(articles);
        Instant end = Instant.now();

        long d = Duration.between(start, end).toMillis();
        System.out.println("Duration: " + d);


        ft.closeConnection();

        System.out.println("finished");


    }


}
