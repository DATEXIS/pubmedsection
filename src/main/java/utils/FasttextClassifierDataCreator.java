package utils;

import database.PubmedDatabase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import utils.ArticleReader;

public class FasttextClassifierDataCreator {

    // Paths to the files including the article_ids
    public static final String positiveFilePath = "/home/paul/DATEXIS/posExamples.txt";
    public static final String negativeFilePath = "/home/paul/DATEXIS/negExamples.txt";

    // adjust to specific dir result will be named training and test
    public static String outputPath = "/home/paul/DATEXIS/";

    public static Article findArticleByID(ArrayList<Article> articles, String id) {
        for (Article a : articles) {
            if (a.articleID.equals(id))
                return a;
        }
        return null;
    }


    public static <E> List<List<E>> generatePerm(List<E> original) {
        if (original.size() == 0) {
            List<List<E>> result = new ArrayList<List<E>>();
            result.add(new ArrayList<E>());
            return result;
        }
        E firstElement = original.remove(0);
        List<List<E>> returnValue = new ArrayList<List<E>>();
        List<List<E>> permutations = generatePerm(original);
        for (List<E> smallerPermutated : permutations) {
            for (int index = 0; index <= smallerPermutated.size(); index++) {
                List<E> temp = new ArrayList<E>(smallerPermutated);
                temp.add(index, firstElement);
                returnValue.add(temp);
            }
        }
        return returnValue;
    }

    public static void writeArticlesToFile(String filename, ArrayList<Article> articles) {
        BufferedWriter writer = null;
        try {
            System.out.println("Now write articles to file: " + filename);
            writer = new BufferedWriter(new FileWriter(new File(filename)));
            int count = articles.size();
            for (Article a : articles) {
                String label = "__label__" + a.label;
                String headingStr = "";
                StringBuilder stringBuilder = new StringBuilder(headingStr);
                for (String h : a.headings) {
                    stringBuilder.append("," + h);
                }
                if (stringBuilder.length() > 0)
                    stringBuilder.deleteCharAt(0);
                writer.write(label + " " + stringBuilder.toString() + "\n");
                count--;
                if (count % 100000 == 0) {
                    System.out.println(count + " : remaining");
                }
            }

            writer.close();
        } catch (
                IOException e) {
            e.printStackTrace();
        }

    }


    public static void main(String[] args) throws IOException, ClassNotFoundException, SQLException {
        ArrayList<Article> articles = new ArrayList<>();

        ArticleReader negativeReader = new ArticleReader(negativeFilePath, "negative");
        ArticleReader positiveReader = new ArticleReader(positiveFilePath, "positive");

        articles.addAll(negativeReader.getArticles());
        articles.addAll(positiveReader.getArticles());

        PubmedDatabase pubmedDB = new PubmedDatabase();

        // foreach positive id
        String idList = "";
        for (Article a : articles) {
            idList += "," + a.articleID;
        }
        // remove first ","
        idList = idList.substring(1);

        // get sectionTitles
        Statement stmt = pubmedDB.conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT article_id, section_title_string FROM article_heading WHERE article_id IN ("
                        + idList + ") ORDER BY article_id ASC, section_number ASC"
        );
        System.out.println("Query complete");

        // add headings from db to articles in articlelists
        while (rs.next()) {
            Integer id = rs.getInt("article_id");
            String heading = rs.getString("section_title_string");
            Article a = findArticleByID(articles, String.valueOf(id));
            if(a != null)
                a.headings.add(heading);
        }
/*
        articles
                .parallelStream()
                .filter(a -> a.headings.size() < 7)
                .forEach(a -> a.headingPermutations = generatePerm(a.headings));

        // Create new article for each permutation
        for (Article a : articles) {
            for (int i = 0; i < a.headingPermutations.size(); ++i) {
                Article newArticle = new Article(a.articleID);
                newArticle.headings = a.headingPermutations.get(i);
                newArticle.label = a.label;
                if (a.label.equals("positive"))
                    positiveReader.getArticles().add(newArticle);
                else
                    negativeReader.getArticles().add(newArticle);
            }
            // reset permutations
            a.headingPermutations = null;
        }
        */


        ArrayList<Article> training = new ArrayList<>();
        ArrayList<Article> test = new ArrayList<>();

        // get min count from pos and neg examples
        int countToExport = Math.min(negativeReader.getArticles().size(), positiveReader.getArticles().size());

        // shuffle positive and negative articles
        Collections.shuffle(negativeReader.getArticles());
        Collections.shuffle(positiveReader.getArticles());

        // percentage of testdata from all articles
        final float testFactor = 0.2f;
        long testCount = Math.round(testFactor * countToExport);

        for (int i = 0; i < testCount; ++i) {
            test.add(positiveReader.getArticles().get(i));
            test.add(negativeReader.getArticles().get(i));
        }

        for (int i = (int) testCount; i < countToExport; ++i) {
            training.add(positiveReader.getArticles().get(i));
            training.add(negativeReader.getArticles().get(i));
        }
        System.out.println("Test and Training lists complete");

        // shuffle test and training
        Collections.shuffle(test);
        Collections.shuffle(training);

        // write both articlelists to file
        writeArticlesToFile(outputPath + "training.txt", training);
        writeArticlesToFile(outputPath + "test.txt", test);
        System.out.println("Training and testfile written in: " + outputPath);
    }
}
