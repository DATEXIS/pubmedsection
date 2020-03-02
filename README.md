# How to Build:
1. Download and install the latest version of TeXoo (https://github.com/sebastianarnold/TeXoo)
2. Run `mvn clean install` to install the required dependencies. 



# Generate PubMedSection:

The dataset-generation consists of multiple steps which are seperated into different Java-Scripts.

### 1. Train the classifier 
For the classifier training it is required to run the script `/src/main/java/classifier/TrainingClassifier.java "your output directory of the classifier"`
It will download the required documents from the database and start the training. 

### 2. Apply the classifier
After the classifier has been trained it can be applied to all PubMed-documents in the database. Run `/src/main/java/classifier/PredictWithPubMedclassifier.java`

### 3. Export the relevant articles
To export the articles marked as relevant run the script `/src/main/java/exporter/XMLExporter.java "testdata-directory" "traindata-directory"`
This will export the XML-documents of the test-dataset and the 50.000 most relevant documents into the defined directories.

### 4. Cleaning and TeXoo-Conversion
The XML-data now has to be transformed into TeXoo-JSON. Therefore please run `src/main/java/cleaner/Exporter.java "path to the xml-test-documents" "path to the xml-train-documents" "testdata-output-file" "traindata-output-file"`

### 5. Appending the section-data
Run `src/main/java/matcher/WikiSectionMatcher.java "testdataset-path" "traindataset-path" "testdataset-output-path" "traindataset-output-path"` to find the corresponding WikiSection-Labels for each Section and to save the final dataset.

### 6. Generate SentEval-Dataset
Only a subset of 2200 documents is used for SentEval training. Therefore we generate randomly sample documents out of the large dataset.
It is also required to pretokenize the dataset with the TeXoo-Tokenizer. 
This can be done with `src/main/java/exporter/SentEvalExporter.java` and `/src/main/java/exporter/PubmedSectionTokenizer.java` 







