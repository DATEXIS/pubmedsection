package utils;

import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Annotation;
import de.datexis.model.Dataset;
import de.datexis.model.Document;
import de.datexis.sector.model.SectionAnnotation;
import de.datexis.sector.reader.WikiSectionReader;

import java.io.IOException;
import java.util.*;

public class CombineAnnotations {


    private static void printBegin(SectionAnnotation sa) {
        System.out.println(sa.getBegin());
    }


    private static ArrayList<SectionAnnotation> mergeAnnotations(HashMap<Integer, List<SectionAnnotation>> annotations) {
        ArrayList<SectionAnnotation> newAnnotations = new ArrayList<>();
        for (Map.Entry<Integer, List<SectionAnnotation>> e : annotations.entrySet()) {
            int begin = e.getValue().get(0).getBegin();
            int length = e.getValue().get(0).getLength();
            String label = e.getValue().get(0).getSectionLabel();
            String heading = e.getValue().get(0).getSectionHeading();
            for (int i = 1; i < e.getValue().size(); ++i) {
                SectionAnnotation sa = e.getValue().get(i);
                length += sa.getLength();
            }

            SectionAnnotation sa = new SectionAnnotation(Annotation.Source.GOLD, "pubmed", heading);
            sa.setBegin(begin);
            sa.setLength(length);
            sa.setSectionLabel(label);
            newAnnotations.add(sa);
        }
        return newAnnotations;
    }



    public static void merge(List<Document> doc_in)
    {
        for (Document d : doc_in) {
            mergeSingleDoc(d);
        }
    }

    public static void mergeSingleDoc(Document d) {
        List<SectionAnnotation> annotations = (List<SectionAnnotation>) d.getAnnotations(SectionAnnotation.class);
        annotations.sort(Comparator.comparingInt(s -> s.getBegin()));
        HashMap<Integer, List<SectionAnnotation>> toCombine = new HashMap<>();

        SectionAnnotation previous = null;
        int count = 0;
        for (SectionAnnotation current : annotations) {
            d.removeAnnotation(current);
            if (previous == null) {
                // first element
                previous = current;
                toCombine.put(count, new ArrayList<>());
                toCombine.get(count).add(current);
                continue;
            }
            if (previous.getSectionHeading().equals(current.getSectionHeading())) {
                toCombine.get(count).add(current);
                continue;
            } else {
                previous = current;
                count++;
                toCombine.put(count, new ArrayList<>());
                toCombine.get(count).add(current);
            }

        }

        ArrayList<SectionAnnotation> newSa = mergeAnnotations(toCombine);
        d.addAnnotations(newSa);
    }


    public static void main(String[] args) throws IOException {
        Dataset test = WikiSectionReader.readDatasetFromJSON(Resource.fromFile(args[0]));
        ArrayList<Document> docs = new ArrayList<>(test.getDocuments());

        for (Document d : docs) {
            List<SectionAnnotation> annotations = (List<SectionAnnotation>) d.getAnnotations(SectionAnnotation.class);
            annotations.sort(Comparator.comparingInt(s -> s.getBegin()));
            HashMap<Integer, List<SectionAnnotation>> toCombine = new HashMap<>();

            SectionAnnotation previous = null;
            int count = 0;
            for (SectionAnnotation current : annotations) {
                d.removeAnnotation(current);
                if (previous == null) {
                    // first element
                    previous = current;
                    toCombine.put(count, new ArrayList<>());
                    toCombine.get(count).add(current);
                    continue;
                }
                if (previous.getSectionLabel().equals(current.getSectionLabel()) && previous.getSectionHeading().equals(current.getSectionHeading())) {
                    toCombine.get(count).add(current);
                    continue;
                } else {
                    previous = current;
                    count++;
                    toCombine.put(count, new ArrayList<>());
                    toCombine.get(count).add(current);
                }

            }

            ArrayList<SectionAnnotation> newSa = mergeAnnotations(toCombine);
            d.addAnnotations(newSa);

        }
        String outputPath = args[1];
        Resource p = Resource.fromDirectory(outputPath);
        System.out.println("now serialize");
        ObjectSerializer.writeJSON(docs, p);


    }


}
