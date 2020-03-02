package cleaner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public class PubmedCleanerWriter implements Runnable {

    private BlockingQueue<String> queue;
    private File resFile;
    private BufferedWriter out = null;
    public boolean shouldTerminate = false;

    public PubmedCleanerWriter(BlockingQueue<String> q, File f) {
        this.queue = q;
        this.resFile = f;
    }

    @Override
    public void run() {
        try {
            out = new BufferedWriter(new FileWriter(resFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!shouldTerminate) {
            try {
                if (queue.size() > 0) {
                    String doc = queue.take();
                    out.write(doc + "\n");
                } else {
                    Thread.sleep(50);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closePrintWriter() throws IOException {
        this.out.flush();
        this.out.close();
    }
}
