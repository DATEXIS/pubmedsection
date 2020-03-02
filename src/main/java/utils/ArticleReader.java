package utils;

import java.io.*;
import java.util.ArrayList;

public class ArticleReader {

    private ArrayList<Article> articles = new ArrayList<>();

    public ArticleReader(String path, String label) throws IOException {

        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(path)));
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                Article a = new Article(line);
                a.label = label;
                articles.add(a);
            }
            bufferedReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }




    public ArrayList<Article> getArticles() {
        return articles;
    }


}
