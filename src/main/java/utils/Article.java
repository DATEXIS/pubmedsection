package utils;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Article {


    public List<String> headings = new ArrayList<>();
    public String label = "";
    public String articleID = "";
    public List<List<String>> headingPermutations = new ArrayList<>();
    public List<String> sections = new ArrayList<>();
    public INDArray headingEmbedding = null;
    public String pmid = "test";
    public int headingCount = 0;
    public float[] confidence;
    public float[] floatEmbedding = null;

    public Article(String articleID) {
        this.articleID = articleID;
    }

    public void toFile(String path) throws IOException {
        File tmpDir = new File(path + "/"+ pmid + ".txt");
        if(tmpDir.exists())
        {
            System.out.println("Overwrite file");
        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(path + "/" + this.pmid + ".txt")));
        for(int i = 0; i < headings.size(); ++i)
        {
            bw.write(headings.get(i) + "\n" + sections.get(i) + "\n");
        }
        bw.flush();
        bw.close();
        if(headings.size() == 0)
        {
            System.out.println("No Headings in " + articleID);
        }
    }

    public String getHeadingString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headings.size(); ++i) {
            sb.append("\t" + headings.get(i));
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }


}
