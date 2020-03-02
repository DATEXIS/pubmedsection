package cleaner;

import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

public class Coordinator {

    public static void main(String[] args) throws InterruptedException, IOException {
        String outputpath = args[0];
        BlockingQueue<String> docQueue = new LinkedBlockingQueue<>();
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService cleanerExecutor = Executors.newFixedThreadPool(cores - 1);
        for (int i = 0; i < cores - 1; i++) {
            Runnable worker = new PubmedDocCleanerJob(docQueue);
            cleanerExecutor.execute(worker);
        }

        Jedis jedis = new Jedis("redis", 6379);
        long jobID = jedis.incr("jobid");

        File resultFile = new File(outputpath + jobID + ".txt");
        ExecutorService writerExecutor = Executors.newFixedThreadPool(1);
        PubmedCleanerWriter writer = new PubmedCleanerWriter(docQueue, resultFile);
        writerExecutor.execute(writer);

        cleanerExecutor.awaitTermination(24, TimeUnit.HOURS);
        cleanerExecutor.shutdown();
        writer.closePrintWriter();
        writerExecutor.shutdown();
        writer.shouldTerminate = true;
        writerExecutor.awaitTermination(1, TimeUnit.MINUTES);

    }


}
