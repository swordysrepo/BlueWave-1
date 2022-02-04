package bluewave.utils;

import java.util.*;
import java.io.StringReader;
import java.nio.file.Paths;
import static javaxt.utils.Console.console;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;


public class FileIndex {

    private Directory dir;
    private Object wmonitor = new Object();
    private Object smonitor = new Object();
    private IndexWriter _indexWriter;
    private IndexSearcher _indexSearcher;
    private PerFieldAnalyzerWrapper perFieldAnalyzerWrapper = null;
    FieldType customFieldForVectors = null;

    public static final String FIELD_NAME = "name";
    public static final String FIELD_CONTENTS = "contents";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_MODIFIED = "modified";
    public static final String FIELD_DOCUMENT_ID = "documentID";
    public static final String FIELD_SUBJECT = "subject";
    public static final String FIELD_KEYWORDS = "keywords";
    public static final int FRAGMENT_CHAR_SIZE = 100;
    public static final int NUM_HIGHLIGHT_FRAGS_PER_HIT = 1;

    public FileIndex(String path) throws Exception {
        this(new javaxt.io.Directory(path));
    }

    public FileIndex(javaxt.io.Directory path) throws Exception {
        dir = FSDirectory.open(Paths.get(path.toString()));
        Analyzer standardAnalyzer = new StandardAnalyzer();
        ShingleAnalyzerWrapper shingleAnalyzerWrapper = new ShingleAnalyzerWrapper(standardAnalyzer, 2, 3);
        Map<String,Analyzer> analyzerPerFieldMap = new HashMap<>();
        analyzerPerFieldMap.put(FIELD_CONTENTS, shingleAnalyzerWrapper);
        analyzerPerFieldMap.put(FIELD_NAME, standardAnalyzer);
        perFieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(standardAnalyzer, analyzerPerFieldMap);

        customFieldForVectors = new FieldType();
        customFieldForVectors.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        customFieldForVectors.setStored(true);
        customFieldForVectors.setStoreTermVectors(true);
        
    }

    public TreeMap<Float, ArrayList<javaxt.io.File>> findFiles(String... searchTerms) throws Exception {
        ArrayList<String> arr = new ArrayList<>();
        for (String term : searchTerms) arr.add(term);
        return findFiles(arr, 10);
    }

    public TreeMap<Float, ArrayList<javaxt.io.File>> findFiles(ArrayList<String> searchTerms, Integer limit) throws Exception {
        TreeMap<Float, ArrayList<javaxt.io.File>> searchResults = new TreeMap<>();
        IndexSearcher searcher = instanceOfIndexSearcher();
        if (searcher != null) {
            List<ResultWrapper> results = getTopDocs(searchTerms, limit);
            if (results != null) {
                for(ResultWrapper resultWrapper: results) {
                    ScoreDoc scoreDoc = resultWrapper.scoreDoc;
                    Document doc = searcher.doc(scoreDoc.doc);
                    float score = scoreDoc.score;
                    javaxt.io.File file = new javaxt.io.File(doc.get(FIELD_PATH));
                    ArrayList<javaxt.io.File> files = searchResults.get(score);
                    if (files==null){
                        files = new ArrayList<>();
                        searchResults.put(score, files);
                    }
                    files.add(file);
                }
            }
        }
        return searchResults;
    }


    public TreeMap<Float, ArrayList<bluewave.app.Document>> findDocuments(List<String> searchTerms, Integer limit) throws Exception {
        TreeMap<Float, ArrayList<bluewave.app.Document>> searchResults = new TreeMap<>();
        IndexSearcher searcher = instanceOfIndexSearcher();
        if (searcher != null) {
            List<ResultWrapper> results = getTopDocs(searchTerms, limit);
            if (results != null) {
                    for(ResultWrapper resultWrapper: results) {
                    ScoreDoc scoreDoc = resultWrapper.scoreDoc;
                    Document doc = searcher.doc(scoreDoc.doc);

                    float score = scoreDoc.score;
                    Long documentID = Long.parseLong(doc.get(FIELD_DOCUMENT_ID));
                    bluewave.app.Document d = new bluewave.app.Document(documentID);
                    ArrayList<bluewave.app.Document> documents = searchResults.get(score);
                    if (documents==null){
                        documents = new ArrayList<>();
                        searchResults.put(score, documents);
                    }
                    documents.add(d);
                }
            }
        }
        return searchResults;
    }

    public List<ResultWrapper> getTopDocs(List<String> searchTerms, Integer limit) throws Exception {
        if (limit==null || limit<1) limit = 10;

        TreeMap<Float, ArrayList<bluewave.app.Document>> searchResults = new TreeMap<>();
        IndexSearcher searcher = instanceOfIndexSearcher();
        if (searcher != null) {

            BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
            String term = searchTerms.get(0);
            
            WildcardQuery wildcardQuery = new WildcardQuery(new Term(FIELD_NAME, WildcardQuery.WILDCARD_STRING + QueryParser.escape(term).toLowerCase() + WildcardQuery.WILDCARD_STRING));
            BooleanClause wildcardBooleanClause = new BooleanClause(new BoostQuery(wildcardQuery, 2.0f), BooleanClause.Occur.SHOULD);
            bqBuilder.add(wildcardBooleanClause);

            bqBuilder.add(new BooleanClause(new QueryParser(FIELD_CONTENTS, perFieldAnalyzerWrapper).parse(QueryParser.escape(term).toLowerCase()),
                    BooleanClause.Occur.SHOULD));

            bqBuilder.add(new BooleanClause(new QueryParser(FIELD_KEYWORDS, perFieldAnalyzerWrapper).parse(QueryParser.escape(term).toLowerCase()),
                    BooleanClause.Occur.SHOULD));

            bqBuilder.add(new BooleanClause(new QueryParser(FIELD_SUBJECT, perFieldAnalyzerWrapper).parse(QueryParser.escape(term).toLowerCase()),
                    BooleanClause.Occur.SHOULD));

            BooleanQuery bbq = bqBuilder.build();
            QueryScorer scorer = new QueryScorer(bbq);
            
            Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter(), scorer);
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, FRAGMENT_CHAR_SIZE));  
            
            TopDocs hits = searcher.search(bbq, limit);
            List<ResultWrapper>results = new ArrayList<>();
            ResultWrapper resultWrapper = null;
            for(ScoreDoc scoreDoc : hits.scoreDocs) {
                resultWrapper = new ResultWrapper();
                resultWrapper.scoreDoc = scoreDoc;
                int docid = scoreDoc.doc;
                Document doc = searcher.doc(docid);
                Explanation ex = searcher.explain(bbq, docid);
                Explanation explanation = ex.getDetails()[0];
                resultWrapper.frequency = getFrequency(explanation.toString());
                String fragment = null;
                for (IndexableField field : doc.getFields()) {
                    fragment = null;
                    if((fragment = getHighlights(field, doc, highlighter)) != null) {
                        // Collect only the first highlight fragment
                        resultWrapper.highlightFragment = fragment;
                        break;
                    }
                }
                results.add(resultWrapper);
            }
            return results;
        }
        return null;
    }

    private String getHighlights(final IndexableField field, Document doc, Highlighter highlighter) {
        try {
            
            String text = doc.get(field.name());
            if(text != null && !text.isBlank()) {
                TokenStream stream = perFieldAnalyzerWrapper.tokenStream(field.name(), new StringReader(text));
                String[] frags = highlighter.getBestFragments(stream, text, NUM_HIGHLIGHT_FRAGS_PER_HIT);
                for (String frag : frags) 
                {
                    return frag;
                }
            }
        }catch(Exception e) {
            console.log("ERROR: " + e);
        }
        return null;
    }

    private Float getFrequency(String explanationDetails) {
        try {
            int indexOfEqualsSign = explanationDetails.indexOf("= freq,");
            if(indexOfEqualsSign != -1) {
                String firstHalf = explanationDetails.substring(0, indexOfEqualsSign);
                int indexOfColonBeforeTargetEqualsSign = firstHalf.lastIndexOf("from:") + 5;
                String frequencyStr = explanationDetails.substring(indexOfColonBeforeTargetEqualsSign, indexOfEqualsSign);
                if(frequencyStr != null && !frequencyStr.isBlank()) {
                    return Float.parseFloat(frequencyStr);
                }
            } 
        }catch(Exception e) {
            console.log("Error: " + e);
        }
        return null;
    }

    private IndexWriter instanceOfIndexWriter() {
        synchronized (wmonitor) {
            if (_indexWriter == null) {
                try {
                   IndexWriterConfig iwc = new IndexWriterConfig(perFieldAnalyzerWrapper);
                    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
                    _indexWriter = new IndexWriter(dir, iwc);
                } catch (Exception e) {
                    console.log("ERROR: " + e);
                }
            }
        }
        return _indexWriter;
    }

    public IndexSearcher instanceOfIndexSearcher() {
        synchronized (smonitor) {
            if (_indexSearcher == null) {
                try {
                    _indexSearcher = new IndexSearcher(DirectoryReader.open(dir));
                } catch (Exception e) {
                    console.log("ERROR: " + e);
                }
            }
        }
        return _indexSearcher;
    }


  //**************************************************************************
  //** addFile
  //**************************************************************************
  /** Used to add a file to the index
   */
    public void addFile(javaxt.io.File file) throws Exception {
        addDocument(null, file);
    }


  //**************************************************************************
  //** addDocument
  //**************************************************************************
  /** Used to add a bluewave document, backed by a file to the index
   */
    public void addDocument(bluewave.app.Document d, javaxt.io.File file) throws Exception {
        if (hasFile(file)) return;

        // make a new, empty document
        Document doc = new Document();

        // Add the path of the file as a field named "path". Use a
        // field that is indexed (i.e. searchable), but don't tokenize
        // the field into separate words and don't index term frequency
        // or positional information:
        doc.add(new StringField(FIELD_PATH, file.toString(), Field.Store.YES));

        // Make the document name tokenized and searchable
        doc.add(new TextField(FIELD_NAME, file.getName(false), Field.Store.YES));

        // Add the last modified date of the file a field named "modified".
        // Use a LongPoint that is indexed (i.e. efficiently filterable with
        // PointRangeQuery). This indexes to milli-second resolution, which
        // is often too fine. You could instead create a number based on
        // year/month/day/hour/minutes/seconds, down the resolution you require.
        // For example the long value 2011021714 would mean
        // February 17, 2011, 2-3 PM.
        doc.add(new LongPoint(FIELD_MODIFIED, file.getDate().getTime()));



        if (d!=null) doc.add(new StringField(FIELD_DOCUMENT_ID, d.getID()+"", Field.Store.YES));

        if (file.getExtension().equalsIgnoreCase("pdf")) {

            PDFParser parser = new PDFParser(new RandomAccessBuffer(file.getInputStream()));
            parser.parse();
            COSDocument cd = parser.getDocument();
            PDDocument pdDocument = new PDDocument(cd);
            if (d!=null) d.setPageCount(pdDocument.getNumberOfPages());
            PDDocumentInformation info = pdDocument.getDocumentInformation();

            if(info.getSubject() != null && !info.getSubject().isBlank())
                doc.add(new TextField(FIELD_SUBJECT, info.getSubject(), Store.YES));

            if(info.getKeywords() != null && !info.getKeywords().isBlank())
                doc.add(new TextField(FIELD_KEYWORDS, info.getKeywords(), Store.YES));

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdDocument);
            cd.close();
            doc.add(new Field(FIELD_CONTENTS, text, customFieldForVectors));
            // doc.add(new TextField(FIELD_CONTENTS, text, Store.YES));
        }
        else {

            String contentType = file.getContentType();
            if (contentType.startsWith("text")){

                // Add the contents of the file to a field named "contents". Specify a Reader,
                // so that the text of the file is tokenized and indexed, but not stored.
                // Note that FileReader expects the file to be in UTF-8 encoding.
                // If that's not the case searching for special characters will fail.
                doc.add(new TextField(FIELD_CONTENTS, file.getBufferedReader()));
            }
        }

        IndexWriter writer = instanceOfIndexWriter();
        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            console.log("adding " + file);
            writer.addDocument(doc);
        } else {
            // Existing index (an old copy of this document may have been indexed) so
            // we use updateDocument instead to replace the old one matching the exact
            // path, if present:
            console.log("updating " + file);
            writer.updateDocument(new Term(FIELD_PATH, file.toString()), doc);
        }
        writer.commit();


        // NOTE: if you want to maximize search performance,
        // you can optionally call forceMerge here. This can be
        // a terribly costly operation, so generally it's only
        // worth it when your index is relatively static (ie
        // you're done adding documents to it):
        //
        // writer.forceMerge(1);

    }


  //**************************************************************************
  //** removeFile
  //**************************************************************************
  /** Used to remove a file from the index
   */
    public boolean removeFile(javaxt.io.File file) throws Exception {
        return remove(new Term(FIELD_PATH, file.toString() ));
    }


  //**************************************************************************
  //** removeDocument
  //**************************************************************************
  /** Used to remove a bluewave document from the index
   */
    public boolean removeDocument(long documentId) throws Exception {
        return remove(new Term( FIELD_DOCUMENT_ID, documentId+"" ));
    }


  //**************************************************************************
  //** remove
  //**************************************************************************
  /** Used to remove an entry from the index using a given search term
   */
    private boolean remove(Term term) throws Exception {
        BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
        bqBuilder.add(new TermQuery(term), Occur.MUST);
        IndexWriter writer = instanceOfIndexWriter();
        writer.deleteDocuments(bqBuilder.build());
        long status = writer.commit();
        if(status == -1) return false;
        return true;
    }


  //**************************************************************************
  //** hasFile
  //**************************************************************************
  /** Returns true of the given file is in the index
   */
    public boolean hasFile(javaxt.io.File file) {
        if (indexExists()) {
            IndexSearcher searcher = instanceOfIndexSearcher();

            if (searcher != null) {
                try {
                    TopDocs results = searcher.search(new TermQuery(new Term(FIELD_PATH, file.toString())), 1);
                    if (results.totalHits.value > 0) {
                        return true;
                    }
                } catch (Exception e) {

                }
            }
        }
        return false;
    }

    private boolean indexExists() {
        try {

            return DirectoryReader.indexExists(dir);
        } catch (Exception e) {
            console.log("indexExists: " + e);
        }
        return false;
    }

    class ResultWrapper {
        ScoreDoc scoreDoc;
        Float frequency;
        String highlightFragment;
    }
    
}
