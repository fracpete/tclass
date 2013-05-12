/**
 *  Single classifier solution. That is to say, we cluster all the instances
 *  using the same clustering algorithms. 
 * 
 * This is a "low memory" version, written to see if memory consumption can be reduced. 
 * 
 * @author Waleed Kadous
 * @version $Id: ExpSingleLM.java,v 1.1.1.1 2002/06/28 07:36:16 waleed Exp $
 */
package tclass;  

import tclass.clusteralg.*; 
import tclass.util.*; 
// import tclass.learnalg.*; 
import weka.classifiers.*; 
import weka.classifiers.j48.*; 
import weka.attributeSelection.*; 
import weka.filters.*; 
import weka.core.*; 
import java.io.*; 
import java.util.*; 


public class ExpSingleLM {
    // Ok. What we are going to do is to separate the learning task in 
    // an interesting way. 
    // First of all, though, the standard stuff
    
    static Runtime rt = Runtime.getRuntime(); 
    String domDescFile = "sl.tdd"; 
    String trainDataFile = "sl.tsl"; 
    String testDataFile = "sl.ttl"; 
    // String globalDesc = "test._gc"; 
    // String evExtractDesc = "test._ee";
    String evClusterDesc = "test._ec"; 
    String settingsFile = "test.tal"; 
    String learnerStuff = "weka.classifiers.j48.J48"; 
    boolean featureSel = false; 
    boolean makeDesc = false; 
    boolean trainResults = false; 
    void parseArgs(String[] args){
        for(int i=0; i < args.length; i++){
            if(args[i].equals("-tr")){
                trainDataFile = args[++i]; 
            }
            if(args[i].equals("-dd")){
                domDescFile = args[++i]; 
            }
            if(args[i].equals("-te")){
                testDataFile = args[++i];
            }
            if(args[i].equals("-settings")){
                settingsFile = args[++i]; 
            }
            if(args[i].equals("-fs")){
                featureSel = true; 
            }
            if(args[i].equals("-md")){
                makeDesc = true; 
            }
            if(args[i].equals("-trainres")){
                trainResults = true; 
            }
            if(args[i].equals("-l")){
                learnerStuff = args[++i]; 
                learnerStuff = learnerStuff.replace(':', ' '); 
                System.err.println("Learner String is: " + learnerStuff); 
            }
        }
    }

    // Alright. This is downright funky hacky stuff. 

    public static void mem(String label){
        System.out.println("Memory at checkpt " + label + ": " + (rt.totalMemory()/1024/1024) + " megabytes."); 
    }

    public static void main(String[] args) throws Exception {
        Debug.setDebugLevel(Debug.PROGRESS); 
        ExpSingleLM thisExp = new ExpSingleLM(); 
        thisExp.parseArgs(args); 
        mem("PARSE"); 
        DomDesc domDesc = new DomDesc(thisExp.domDescFile); 
        ClassStreamVecI trainStreamData = new
            ClassStreamVec(thisExp.trainDataFile, domDesc); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Training data read in");  
        mem("TRAINDATAIN"); 
        Settings settings = new Settings(thisExp.settingsFile, domDesc); 
        

        EventExtractor evExtractor = settings.getEventExtractor(); 
        // Global data is likely to be included in every model; so we
        // might as well calculated now
        GlobalCalc globalCalc = settings.getGlobalCalc(); 

        ClassStreamAttValVecI trainGlobalData =
            globalCalc.applyGlobals(trainStreamData);
        // And we might as well extract the events. 

        Debug.dp(Debug.PROGRESS, "PROGRESS: Training data globals calculated.");  
        mem("TRAINGLOBAL"); 
        Debug.dp(Debug.PROGRESS, "Train: " + trainGlobalData.size());  


        ClassStreamEventsVecI trainEventData =
            evExtractor.extractEvents(trainStreamData); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Training events extracted");  
        mem("EVENTEXTRACT"); 
        // System.out.println(trainEventData.toString()); 


        // Now we want the clustering algorithms only to cluster
        // instances of each class. Make an array of clusterers, 
        // one per class. 

        int numClasses = domDesc.getClassDescVec().size(); 
        EventDescVecI eventDescVec = evExtractor.getDescription(); 
        EventClusterer eventClusterer = settings.getEventClusterer(); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Data rearranged.");  
        mem("REARRANGE"); 

        //And now load it up. 
        StreamEventsVecI trainEventSEV =
            trainEventData.getStreamEventsVec(); 
        ClassificationVecI trainEventCV = trainEventData.getClassVec();
        int numTrainStreams = trainEventCV.size(); 
        ClusterVecI clusters = eventClusterer.clusterEvents(trainEventData); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Clustering complete"); 
        Debug.dp(Debug.PROGRESS, "Clusters are:"); 
        Debug.dp(Debug.PROGRESS, "\n" + eventClusterer.getMapping()); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Clustering complete. ");  
        mem("CLUSTER"); 

        // But wait! There's more! There is always more. 
        // The first thing was only useful for clustering. 
        // Now attribution. We want to attribute all the data. So we are going 
        // to have one dataset for each learner. 
        // First set up the attributors. 

        Attributor attribs = new Attributor(domDesc, clusters, 
                                            eventClusterer.getDescription()); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: AttributorMkr complete."); 
        mem("MAKEATTRIBUTOR"); 


        ClassStreamAttValVecI trainEventAtts =attribs.attribute(trainStreamData, trainEventData); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Training data Attribution complete."); 
        mem("TRAINATTRIBUTION");

        // Combine all data sources. For now, globals go in every
        // one. 

        Combiner c = new Combiner(); 
        ClassStreamAttValVecI trainAtts = c.combine(trainGlobalData,
                                            trainEventAtts); 

        mem("TRAINCOMBINATION"); 
        trainStreamData = null; 
        trainEventSEV = null; 
        trainEventCV = null; 
        System.gc(); 
        mem("TRAINGC");

        // So now we have the raw data in the correct form for each
        // attributor. 
        // And now, we can construct a learner for each case. 
        // Well, for now, I'm going to do something completely crazy. 
        // Let's run each classifier nonetheless over the whole data
        // ... and see what the hell happens. Maybe some voting scheme 
        // is possible!! This is a strange form of ensemble
        // classifier. 
        // Each naive bayes algorithm only gets one 

        Debug.setDebugLevel(Debug.PROGRESS); 
        int[] selectedIndices = null;  
        String [] classifierSpec = Utils.splitOptions(thisExp.learnerStuff);
        if (classifierSpec.length == 0) {
            throw new Exception("Invalid classifier specification string");
        }        
        String classifierName = classifierSpec[0];
        classifierSpec[0] = "";
        Classifier learner = Classifier.forName(classifierName, classifierSpec);
        Debug.dp(Debug.PROGRESS, "PROGRESS: Beginning format conversion for class "); 
        Instances  data = WekaBridge.makeInstances(trainAtts, "Train ");
        Debug.dp(Debug.PROGRESS, "PROGRESS: Conversion complete. Starting learning");    
        mem("ATTCONVERSION");
        if(thisExp.featureSel){
                Debug.dp(Debug.PROGRESS, "PROGRESS: Doing feature selection");    
                BestFirst bfs = new BestFirst();
                CfsSubsetEval cfs = new CfsSubsetEval(); 
                cfs.buildEvaluator(data); 
                selectedIndices = bfs.search(cfs, data); 
                // Now extract the features. 
                System.err.print("Selected features: ");
                String featureString = new String(); 
                for(int j=0; j < selectedIndices.length; j++){
                    featureString += (selectedIndices[j] +1)+ ",";
                }
                featureString += ("last"); 
                System.err.println(featureString); 
               // Now apply the filter. 
                AttributeFilter af = new AttributeFilter(); 
                af.setInvertSelection(true); 
                af.setAttributeIndices(featureString); 
                af.inputFormat(data); 
                data = af.useFilter(data, af); 
            }
        learner.buildClassifier(data); 
        mem("POSTLEARNER"); 
        Debug.dp(Debug.PROGRESS, "Learnt classifier: \n" + learner.toString()); 
        
        WekaClassifier wekaClassifier; 
        wekaClassifier = new WekaClassifier(learner); 

        if(thisExp.makeDesc){
            // Section for making description more readable. Assumes that 
            // learner.toString() returns a string with things that look like 
            // feature names. 
            String concept = learner.toString(); 
            StringTokenizer st = new StringTokenizer(concept, " \t\r\n", true);
            int evId = 1; 
            String evIndex = ""; 
            while (st.hasMoreTokens()) {
                boolean appendColon = false; 
                String curTok = st.nextToken(); 
                GClust clust = (GClust) ((ClusterVec) clusters).elCalled(curTok);
                if(clust != null){
                    // Skip the spaces
                    st.nextToken(); 
                    // Get a < or >
                    String cmp = st.nextToken(); 
                    String qual = ""; 
                    if(cmp.equals("<=")){
                        qual = " HAS NO "; 
                    }
                    else {
                        qual = " HAS "; 
                    }
                    // skip spaces
                    st.nextToken(); 
                    // Get the number. 
                    String conf = st.nextToken(); 
                    if(conf.endsWith(":")){
                        conf = conf.substring(0, conf.length()-1); 
                        appendColon = true; 
                    }
                    float minconf = Float.valueOf(conf).floatValue(); 
                    EventI[] res = clust.getBounds(minconf);
                    String name = clust.getName(); 
                    int dashPos = name.indexOf('-'); 
                    int undPos = name.indexOf('_'); 
                    String chan = name.substring(0, dashPos);
                    String evType = name.substring(dashPos+1, undPos); 
                    EventDescI edi = clust.eventDesc(); 
                    if(qual == " HAS NO " && thisExp.learnerStuff.startsWith("weka.classifiers.j48.J48")){
                        System.out.print("OTHERWISE"); 
                    }
                    else {
                        System.out.print("IF " + chan + qual + res[2] + " (*" + evId + ")"); 
                        int numParams = edi.numParams(); 
                        evIndex += "*" + evId + ": " + evType + "\n"; 
                        for(int i=0; i < numParams; i++){
                            evIndex += "   " + edi.paramName(i) + "=" + res[2].valOf(i) + " r=[" + res[0].valOf(i) + "," + res[1].valOf(i) +"]\n";
                        }
                        evId++; 
                    }
                    evIndex += "\n"; 
                    if(appendColon){
                        System.out.print(" THEN"); 
                    }
                }
                else {
                    System.out.print(curTok);
                }
            }
            System.out.println("\nEvent index"); 
            System.out.println("-----------"); 
            System.out.print(evIndex); 
            mem("POSTDESC"); 


            // Now this is going to be messy as fuck. Really. What do we needs? Well, 
            // we need to read in the data; look up some info, that we 
            // assume came from a GainClusterer ... 
            // Sanity check. 
            //            GClust clust =  (GClust) ((ClusterVec) clusters).elCalled("alpha-inc_0"); 
            // System.out.println("INSANE!: " + clust.getDescription()); 
            // EventI[] res = clust.getBounds(1); 
            // System.out.println("For clust settings: min event = " + res[0].toString() + " and max event = " + res[1].toString()); 
        }
        Debug.dp(Debug.PROGRESS, "PROGRESS: Learning complete. ");  
        int numCorrect = 0; 
        ClassificationVecI classns; 
        if(thisExp.trainResults){
            System.err.println(">>> Training performance <<<"); 
            classns = (ClassificationVecI) trainAtts.getClassVec().clone();
            for(int j=0; j < numTrainStreams; j++){
                wekaClassifier.classify(data.instance(j), classns.elAt(j));
            }
            for(int j=0; j < numTrainStreams; j++){
                // System.out.print(classns.elAt(j).toString()); 
                if(classns.elAt(j).getRealClass() == classns.elAt(j).getPredictedClass()){
                    numCorrect++; 
                    String realClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getRealClass());                
                    System.err.println("Class " + realClassName + " CORRECTLY classified."); 
                    
                }
                else {
                    
                    String realClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getRealClass());
                    String predictedClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getPredictedClass());
                                System.err.println("Class " + realClassName + " INCORRECTLY classified as " + predictedClassName + "."); 
                                
                }
            }
            System.err.println("Training results for classifier: " + numCorrect + " of " + numTrainStreams + " (" + 
                               numCorrect*100.0/numTrainStreams + "%)"); 
        }
        mem("POSTTRAIN"); 
            
        System.err.println(">>> Testing stage <<<"); 

                // Stick testing stuff here. 
        mem("TESTBEGIN");
        ClassStreamVecI testStreamData = new
            ClassStreamVec(thisExp.testDataFile, domDesc); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Test data read in");  
        mem("TESTREAD");
        ClassStreamAttValVecI testGlobalData =
            globalCalc.applyGlobals(testStreamData);
        Debug.dp(Debug.PROGRESS, "PROGRESS: Test data globals calculated");  
        mem("TESTGLOBALS");
        Debug.dp(Debug.PROGRESS, "Test data: " + testGlobalData.size());  
        ClassStreamEventsVecI testEventData =
            evExtractor.extractEvents(testStreamData); 
        Debug.dp(Debug.PROGRESS, "PROGRESS: Test events extracted");  
        mem("TESTEVENTS");
        int numTestStreams = testEventData.size(); 
        ClassStreamAttValVecI testEventAtts = attribs.attribute(testStreamData,
                                                                testEventData); 
        mem("TESTATTRIBUTES");
        ClassStreamAttValVecI testAtts = c.combine(testGlobalData,
                                           testEventAtts); 
        mem("TESTCOMBINE");
        testStreamData = null; 
        System.gc(); // Do garbage collection. 
        mem("TESTGC"); 
      if(!thisExp.makeDesc){
            clusters = null; 
            eventClusterer = null; 
        }
        attribs = null; 

        
        // First, print the results of using the straight testers. 
        classns = (ClassificationVecI) testAtts.getClassVec().clone();
        StreamAttValVecI savvi = testAtts.getStreamAttValVec(); 
        data = WekaBridge.makeInstances(testAtts, "Test "); 
        if(thisExp.featureSel){
            String featureString = new String(); 
            for(int j=0; j < selectedIndices.length; j++){
                featureString += (selectedIndices[j]+1) + ",";
            }
            featureString += "last"; 
            // Now apply the filter. 
            AttributeFilter af = new AttributeFilter(); 
            af.setInvertSelection(true); 
            af.setAttributeIndices(featureString); 
            af.inputFormat(data); 
            data = af.useFilter(data, af); 
        }
        for(int j=0; j < numTestStreams; j++){
            wekaClassifier.classify(data.instance(j), classns.elAt(j));
        }
        System.err.println(">>> Learner <<<"); 
        numCorrect = 0; 
        for(int j=0; j < numTestStreams; j++){
            // System.out.print(classns.elAt(j).toString()); 
            if(classns.elAt(j).getRealClass() == classns.elAt(j).getPredictedClass()){
                numCorrect++; 
                String realClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getRealClass());                
                System.err.println("Class " + realClassName + " CORRECTLY classified."); 

            }
            else {

                String realClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getRealClass());
                String predictedClassName = domDesc.getClassDescVec().getClassLabel(classns.elAt(j).getPredictedClass());
                

                                System.err.println("Class " + realClassName + " INCORRECTLY classified as " + predictedClassName + "."); 

            }
        }
            System.err.println("Test accuracy for classifier: " + numCorrect + " of " + numTestStreams + " (" + 
                               numCorrect*100.0/numTestStreams + "%)"); 
            mem("POSTTEST"); 

    }
    
}
