package cleaner;

import utils.Article_t;
import cleaner.PubMedCleaner;
import database.PubmedDatabase;
import redis.clients.jedis.Jedis;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

public class PubmedDocCleanerJob implements Runnable {
    private Jedis jedis;
    private PubMedCleaner pmc;
    private PubmedDatabase db;
    private BlockingQueue<String> queue;
    private int count;

    public PubmedDocCleanerJob(BlockingQueue<String> q) {
        this.queue = q;
        pmc = null;
        db = new PubmedDatabase();
        try {
            pmc = new PubMedCleaner();
        } catch (ParserConfigurationException | XPathExpressionException e) {
            e.printStackTrace();
        }

        jedis = new Jedis("redis", 6379);
        count = Integer.valueOf(jedis.get("jobsize"));
    }

    @Override
    public void run() {
        while (Integer.valueOf(jedis.get("docs")) < 2142050) {
            int offset = Integer.valueOf(jedis.rpop("id"));
            ArrayList<Article_t> articles = db.downloadDocs(count, offset);
            PubMedCleaner finalPmc = pmc;
            List<String> article_texts = articles.stream()
                    .map(a -> finalPmc.parsePubmedDocStringToString(a.text))
                    .collect(Collectors.toList());

            long newCount = jedis.incrBy("docs", count);
            System.out.println("Status: " + newCount + " Docs");
            for(String s: article_texts) {
                try {
                    queue.put(s);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

