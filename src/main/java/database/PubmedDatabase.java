package database;

import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.sector.model.SectionAnnotation;
import org.nd4j.linalg.api.ndarray.INDArray;
import utils.Article_t;
import utils.Article;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.*;

import static utils.FasttextClassifierDataCreator.findArticleByID;


// Connector and Utility-Class for the Pubmed-Database
public class PubmedDatabase {

    public Connection conn = null;

    public PubmedDatabase() {
        connect();
    }

    public void insertGermanIDs(Collection<Article_t> articles) {
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO german (pmid) values(?)");
            for (Article_t a : articles) {
                stmt.setString(1, a.pmid);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Article_t getArticleByPMCID(String id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT article_xml, title, pmcid FROM articles WHERE pmcid LIKE ?");
        Article_t result = new Article_t();
        stmt.setString(1, id);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        result.text = rs.getString("article_xml");
        result.title = rs.getString("title");
        result.pmid = rs.getString("pmcid");
        return result;
    }

    public HashMap<String, String> getPMCOfPMID(ArrayList<String> pmids) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT pmcid FROM articles WHERE pmid =?");
        HashMap<String, String> results = new HashMap<>();
        for (String pmid : pmids) {
            stmt.setString(1, pmid);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            String pmcid = rs.getString("pmcid");
            results.put(pmid, pmcid);
        }
        return results;
    }

    public ArrayList<String> checkArticleExistence(ArrayList<String> pmids) throws SQLException {
        ArrayList<String> res = new ArrayList<>();
        PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(pmid) AS cnt FROM articles WHERE pmid=?");
        int count = 0;
        for (String pmid : pmids) {
            stmt.setString(1, pmid);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            if (rs.getInt("cnt") > 0)
                res.add(pmid);
            count++;
            if (count % 100 == 0)
                System.out.println(count + " of " + pmids.size());
        }
        return res;
    }


    public HashMap<String, String> downloadArticleXMLs(List<String> pmids) throws SQLException {
        HashMap<String, String> res = new HashMap<>();
        //  String queryString = "SELECT article_xml AS xml, pmid AS pmid FROM articles WHERE pmid IN ("  + pmidString + ")";
        PreparedStatement stmt = conn.prepareStatement("SELECT article_xml AS xml, pmid AS pmid FROM articles WHERE pmid=?");
        //Statement stmt = conn.createStatement();
        //ResultSet rs = stmt.executeQuery(queryString);


        for (String pmid : pmids) {
            stmt.setString(1, pmid);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            String xml = rs.getString("xml");
            res.put(pmid, xml);
        }
        return res;
    }

    public boolean checkArticleExistence(String pmid) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(pmid) AS cnt FROM articles WHERE pmid='" + pmid + "'");
        rs.next();
        return rs.getInt("cnt") > 0;
    }

    public void insertWikiSectionDocuments(Dataset d) {
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO wikisection_v2 (heading, label) values(?,?)");
            for (Document doc : d.getDocuments()) {
                for (SectionAnnotation sa : doc.getAnnotations(SectionAnnotation.class)) {
                    stmt.setString(1, sa.getSectionHeading());
                    stmt.setString(2, sa.getSectionLabel());
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();

        } catch (Exception e) {

            e.printStackTrace();

        }
    }


    public void markAsTest(Dataset d) {
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO test (pmid) values(?)");
            for (Document doc : d.getDocuments()) {
                stmt.setString(1, doc.getId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Article_t> downloadDocsWithout(int limit, int offset) {
        ArrayList<Article_t> res = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT articles.pmid as pmid, articles.article_xml as xml FROM relevance_confidence3\n" +
                    "JOIN articles\n" +
                    "ON articles.id = relevance_confidence3.article_id\n" +
                    "LEFT OUTER JOIN test\n" +
                    "ON articles.pmid = test.pmid\n" +
                    "WHERE test.pmid IS NULL\n" +
                    "ORDER BY relevant_confidence DESC, pmid ASC LIMIT " + limit + " OFFSET " + offset);
            while (rs.next()) {
                String text = rs.getString("xml");
                String pmid = rs.getString("pmid");
                Article_t art = new Article_t();
                art.pmid = pmid;
                art.text = text;
                res.add(art);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    public void addTitlesToArticles(ArrayList<Article_t> articles) {

        try {
            conn.setAutoCommit(false);
            PreparedStatement stmt = conn.prepareStatement("UPDATE articles SET title=? WHERE pmcid =?");

            for (Article_t a : articles) {
                stmt.setString(1, a.title);
                stmt.setString(2, a.pmid);
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getPMCIDByTitle(String title) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT pmcid as pmcid FROM articles WHERE title=?");
            stmt.setString(1, title);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("pmcid");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public ArrayList<Article_t> downloadDocs(int limit, int offset) {
        ArrayList<Article_t> res = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT pmcid as pmcid, articles.article_xml as xml FROM articles ORDER BY pmid ASC LIMIT " + limit + " OFFSET " + offset);
            while (rs.next()) {
                String text = rs.getString("xml");
                String pmid = rs.getString("pmcid");
                Article_t art = new Article_t();
                art.pmid = pmid;
                art.text = text;
                res.add(art);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

    public void fillWikiSectionHeadingLabels(HashMap<String, String> wikiHeadingLabels) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT heading, label FROM newwikiheadings");
            while (rs.next()) {
                wikiHeadingLabels.put(rs.getString("heading"), rs.getString("label"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> getWikiSectionHeadings() {
        ArrayList<String> results = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT heading FROM wikisection_v2");
            while (rs.next()) {
                results.add(rs.getString("heading"));
            }
        } catch (Exception e) {

        }
        return results;
    }


    public String getLabelForHeading(String wikiHeading) {
        try {

            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT label FROM wikisection_v2 WHERE heading = ?"
            );
            stmt.setString(1, wikiHeading);
            ResultSet rs = stmt.executeQuery();
            //ResultSet rs = stmt.executeQuery("SELECT label FROM newwikiheadings WHERE heading = '" + wikiHeading + "'");

            rs.next();
            return rs.getString("label");


        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    public void downloadTestData(String path) {
        try {
            Statement stmt = conn.createStatement();
            BufferedWriter bw;
            ResultSet rs = stmt.executeQuery(
                    "SELECT articles.article_xml as xml, articles.pmid as pmid FROM positive_examples\n" +
                            "JOIN articles ON articles.id = positive_examples.article_id\n"
            );
            while (rs.next()) {
                bw = new BufferedWriter(new FileWriter(new File(path + "/" + rs.getString("pmid") + ".nxml")));
                bw.write(rs.getString("xml"));
                bw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void downloadTrainingData(int count, int offset, float relevance, String path) {
        if (count == -1)
            count = Integer.MAX_VALUE;

        try {
            Statement stmt = conn.createStatement();
            BufferedWriter bw;
            ResultSet rs = stmt.executeQuery(
                    "SELECT articles.pmid as pmid, articles.article_xml as xml FROM relevance_confidence3\n" +
                            "LEFT OUTER JOIN positive_examples\n" +
                            "ON relevance_confidence3.article_id = positive_examples.article_id \n" +
                            "JOIN articles ON articles.id = relevance_confidence3.article_id \n" +
                            "WHERE positive_examples.article_id IS null AND relevance_confidence3.relevant_confidence > "
                            + String.valueOf(relevance)
                            + " ORDER BY relevance_confidence3.relevant_confidence DESC LIMIT "
                            + String.valueOf(count) + " OFFSET " + String.valueOf(offset)
            );
            while (rs.next()) {
                bw = new BufferedWriter(new FileWriter(new File(path + "/" + rs.getString("pmid") + ".nxml")));
                bw.write(rs.getString("xml"));
                bw.close();
            }
        } catch (Exception e) {

        }
    }


    public HashSet<String> getPositivePMIDs() {
        HashSet<String> ids = new HashSet<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT pmid FROM positive_examples\n" +
                            "JOIN articles ON articles.id = positive_examples.article_id"
            );
            while (rs.next()) {
                ids.add(rs.getString("pmid"));
            }
        } catch (Exception e) {

        }
        return ids;
    }

    public ArrayList<Article> getPositiveArticles() {
        ArrayList<Article> results = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT article_id FROM positive_examples"
            );

            while (rs.next()) {
                Article a = new Article(String.valueOf(rs.getInt("article_id")));
                a.label = "1";
                results.add(a);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public ArrayList<Article> getNegativeArticles() {
        ArrayList<Article> results = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT article_id FROM negative_examples"
            );

            while (rs.next()) {
                Article a = new Article(String.valueOf(rs.getInt("article_id")));
                a.label = "0";
                results.add(a);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }


    private String generateIDList(Collection<Article> articles) {
        String idList = "";
        for (Article a : articles) {
            idList += "," + a.articleID;
        }
        // remove first ","
        idList = idList.substring(1);
        return idList;
    }

    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void writeConfidenceToDB(Article a, INDArray confidence) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO relevance_confidence (article_id, notrelevant_confidence, relevant_confidence) values(?,?,?)"
            );
            stmt.setInt(1, Integer.valueOf(a.articleID));
            float[] conf = confidence.toFloatVector();
            stmt.setFloat(2, conf[0]);
            stmt.setFloat(3, conf[1]);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Could not write confidence to db");
        }
    }

    public void writeConfidenceToDB_Batch(ArrayList<Article> articles) {
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO relevance_confidence3 (article_id, notrelevant_confidence, relevant_confidence) values(?,?,?)");
            for (Article a : articles) {
                stmt.setInt(1, Integer.valueOf(a.articleID));
                stmt.setFloat(2, a.confidence[0]);
                stmt.setFloat(3, a.confidence[1]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            articles.clear();
        } catch (Exception e) {
            System.out.println("Error while batch inserting articles: " + e.getMessage());
        }
    }

    public void downloadXMLToFiles(String path) {

        BufferedWriter bw;
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT positive_examples.article_id, article_xml FROM positive_examples " +
                            "JOIN articles ON articles.id = positive_examples.article_id");
            while (rs.next()) {
                bw = new BufferedWriter(new FileWriter(new File(path + "/" + rs.getInt("article_id") + ".nxml")));
                bw.write(rs.getString("article_xml"));
                bw.close();
            }
        } catch (Exception e) {

        }
    }


    public HashMap<String, Article> getAllArticlesHash() {
        HashMap<String, Article> articles = new HashMap<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT DISTINCT(article_id) FROM article_heading ORDER BY article_id ASC");
            while (rs.next()) {
                String id = String.valueOf(rs.getInt("article_id"));
                articles.put(id, new Article(id));
            }
            stmt.close();
            rs.close();
        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
        }
        return articles;
    }

    public HashMap<String, Article> getAllArticlesHash(int limit, int offset) {
        HashMap<String, Article> articles = new HashMap<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT DISTINCT(article_id) FROM article_heading ORDER BY article_id ASC LIMIT " + limit + " OFFSET " + offset);
            while (rs.next()) {
                String id = String.valueOf(rs.getInt("article_id"));
                articles.put(id, new Article(id));
            }
            stmt.close();
            rs.close();
        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
        }
        return articles;
    }

    public ArrayList<Article> getAllArticles() {
        ArrayList<Article> articles = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT DISTINCT(article_id) FROM article_heading ORDER BY article_id ASC");
            while (rs.next()) {
                String id = String.valueOf(rs.getInt("article_id"));
                articles.add(new Article(id));
            }
            stmt.close();
            rs.close();
        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
        }
        return articles;
    }


    public void fillHeadingsSingleArticle(Article a) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT section_title_string FROM article_heading WHERE article_id = " + a.articleID + " ORDER BY section_number ASC");
            while (rs.next()) {
                String sectiontitle = rs.getString("section_title_string");
                a.headings.add(sectiontitle);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fillHeadingsByArticleIds(ArrayList<Article> articles) {
        Statement stmt = null;
        try {
            String idList = generateIDList(articles);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT article_id, section_title_string FROM article_heading WHERE article_id IN (" + idList + ") ORDER BY article_id ASC, section_number ASC"
            );
            int counter = 0;
            while (rs.next()) {
                Integer id = rs.getInt("article_id");
                String heading = rs.getString("section_title_string");
                Article a = findArticleByID(articles, String.valueOf(id));
                if (a != null)
                    a.headings.add(heading);
                counter++;
                if (counter % 500000 == 0)
                    System.out.println(counter);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void fillHeadings(ArrayList<Article> articles) {
        // get sectionTitles
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "WITH idList as (SELECT DISTINCT(article_id) FROM article_heading)\n" +
                            "SELECT article_id, section_title_string FROM article_heading WHERE article_id IN (SELECT * FROM idList) ORDER BY article_id ASC, section_number ASC"
            );
            int counter = 0;
            while (rs.next()) {
                Integer id = rs.getInt("article_id");
                String heading = rs.getString("section_title_string");
                Article a = findArticleByID(articles, String.valueOf(id));
                if (a != null && a.headings.size() < 20)
                    a.headings.add(heading);
                counter++;
                if (counter % 500000 == 0)
                    System.out.println(counter);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void fillHeadings(HashMap<String, Article> articles) {
        // get sectionTitles
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "WITH idList as (SELECT DISTINCT(article_id) FROM article_heading)\n" +
                            "SELECT article_id, section_title_string FROM article_heading WHERE article_id IN (SELECT * FROM idList) ORDER BY article_id ASC, section_number ASC"
            );

            // ResultSet rs = stmt.executeQuery("SELECT article_id, section_title_string FROM article_heading WHERE article_id IN (" + idlist + ") ORDER BY article_id ASC, section_number ASC");

            int counter = 0;
            while (rs.next()) {
                Integer id = rs.getInt("article_id");
                String heading = rs.getString("section_title_string");
                Article a = articles.get(String.valueOf(id));
                if (a != null) {
                    a.headings.add(heading);
                    counter++;
                }
                if (counter % 750000 == 0)
                    System.out.println(counter);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void connect() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        //String url = "jdbc:postgresql://cluster.datexis.com:32508/pubmed";
        String url = "###YOURCONNECTION###";
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        props.setProperty("password", "###YOURPASSWORD###");
        props.setProperty("connectTimeout", "0");
        props.setProperty("loginTimeout", "0");
        props.setProperty("socketTimeout", "0");

        try {
            conn = DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (conn != null) {
            System.out.println("Connected to DB");
        } else {
            System.out.println("Error during connection attempt!");
        }
    }

    public HashMap<String, String> getWikiHeadingLabels() {

        Statement stmt = null;
        HashMap<String, String> wikiHeadingLabels = new HashMap<>();
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT heading, label FROM wikisection_v2");
            while (rs.next()) {
                String heading = rs.getString("heading");
                String label = rs.getString("label");
                wikiHeadingLabels.put(heading, label);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return wikiHeadingLabels;
    }
}

