import java.text.SimpleDateFormat;
import java.util.Calendar;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import java.io.IOException;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import java.util.concurrent.TimeUnit;

public class CloneDigger {

    private static void displayError(ArrayList<String> errorList) {
        if (errorList.size() > 0) {
            System.out.println("Eclipse API error on the following files:");
        }   
        for (String str : errorList) {
            System.out.println(str);
        }
    }

    public static void main(String args[]) throws IOException {

        Options options = new Options();
        options.addOption("generateBaseline", true, "generate baseline config file to the provided path");
        options.addOption("loadConfig", true, "configuration xml file path");
        CommandLineParser parser = new DefaultParser();
        String baseLineOutputPath = null;
        String loadConfig = null;
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("loadConfig")) {
                loadConfig = cmd.getOptionValue("loadConfig");
            }
            if (cmd.hasOption("generateBaseline")) {
                baseLineOutputPath = cmd.getOptionValue("generateBaseline");
            }
        } catch (ParseException e) {
            System.out.println(e);
        }

        // generate a baseline config file
        if (baseLineOutputPath != null) {
            System.out.println("Writing baseline config file..");
            ConfigFile.writeBaseline(baseLineOutputPath);
            System.out.println("Exiting.");
            System.exit(0);
        }

        // load config file
        ConfigFile config = new ConfigFile();
        config.loadConfig(loadConfig);

        int gapSize = config.gapSize;
        int matchAlgorithm = config.matchAlgorithm;
        int matchMode = config.matchMode;
        int minNumLines = config.minNumLines;
        int meshBlockSize = config.meshBlockSize;
        String databaseDir = config.database;
        String projectDir = config.project;
        boolean debug = config.debug;
        boolean removeEmpty = config.removeEmpty;
        boolean buildDatabase = config.buildDatabase;
        String resultPath = config.resultPath;
        boolean exportResults = config.exportResults;
        boolean loadResults = config.loadResults;
        int similarityRange = config.similarityRange;
        boolean enableSimilarity = config.enableSimilarity;
        boolean enableRepetitive = config.enableRepetitive;
        boolean enableOneMethod = config.enableOneMethod;
        boolean buildTFIDF = config.buildTFIDF;
        boolean loadDatabaseFilePaths = config.loadDatabaseFilePaths;
        int aprioriMinSupport = config.aprioriMinSupport;

        // done parsing
        System.out.println("Finished parsing XML parameters");

        // Get current time
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
        Calendar cal = Calendar.getInstance();
        System.out.println("Start @ " +  sdf.format(cal.getTime()) );

        // Measure elapsed time
        long startTime = System.nanoTime();

        // check if a cached list of files exist
        String databaseFilePaths = databaseDir + "cachedList.tmp";
        File f = new File(databaseFilePaths);
        List<String> databaseFileList;
        if(f.exists() && !f.isDirectory() && loadDatabaseFilePaths == true) {
            // exist, load it
            databaseFileList = Database.loadFileList(databaseFilePaths);
        } else {
            // doesn't exist or forced to create new one, create it
            databaseFileList = Database.generateFileList(databaseDir, databaseFilePaths);
        }

        // td-idf
        /*
        TermFrequency termFreq = new TermFrequency();
        if (buildTFIDF == true) {
            termFreq.buildFrequencyMap(databaseDir);
        } else {
            //termFreq.loadFrequencyMap();
        }*/

        // Start loading main content
        ArrayList<String> errorList = new ArrayList<String>();

        Output output = new Output(matchAlgorithm, enableRepetitive, enableOneMethod, matchMode);
        if (matchMode == 1) {
            if (loadResults == false) {
                // full mesh comparison
                System.out.println("Mode: full mesh");
                ArrayList<Text> database_TextList = new ArrayList<Text>();

                // build the database
                if (buildDatabase) {
                    ArrayList<String> temp = Database.constructCache(
                            minNumLines, debug, databaseFileList, databaseDir);
                    errorList.addAll(temp);
                }

                // Capture time
                cal = Calendar.getInstance();
                System.out.println("Start comparison @ " +  sdf.format(cal.getTime()) );

                // perform the comparison
                Compare comp = new Compare(minNumLines, databaseDir);
                comp.installTextFiles(databaseFileList);
                comp.compareMeshed(output, matchAlgorithm, gapSize, meshBlockSize);
                if (exportResults) {
                    output.saveResults(resultPath);
                }
            } else {
                output.loadResults(resultPath);
            }

            // enable the query engine
            output.search();

            output.printResults(removeEmpty, similarityRange, enableSimilarity, matchMode);

            // Frequency Map of all terms
            FrequencyMap fMap = new FrequencyMap(aprioriMinSupport);
            output.processOutputTerms(fMap);
            fMap.exportTable("table.txt");

        } else {
            if (loadResults == false) {
                // between comparison
                System.out.println("Mode: between comparison");
                ArrayList<Text> db_TextList = new ArrayList<Text>();
                ArrayList<Text> project_TextList = new ArrayList<Text>();

                List<String> projectFilePaths = Database.getFileList(projectDir);

                ArrayList<String> temp;
                temp = Database.constructCache(minNumLines, debug, projectFilePaths, projectDir);
                errorList.addAll(temp);

                if (buildDatabase) {
                    temp = Database.constructCache(minNumLines, debug, databaseFileList, databaseDir);
                    errorList.addAll(temp);
                }

                // only load the projects into memory
                System.out.println("\nLoading a total of " + projectFilePaths.size() + 
                                                " cached project files from \n" + projectDir);
                Database.loadCache(project_TextList, debug, projectFilePaths, projectDir);

                // Capture time
                cal = Calendar.getInstance();
                System.out.println("Start comparison @ " +  sdf.format(cal.getTime()) );

                // perform the comparison
                Compare comp = new Compare(minNumLines, databaseDir);
                comp.installTextFiles(project_TextList, databaseFileList);
                comp.compareBetween(output, matchAlgorithm, gapSize);
                if (exportResults) {
                    output.saveResults(resultPath);
                }
            } else {
                output.loadResults(resultPath);
            }

            output.printResults(removeEmpty, similarityRange, enableSimilarity, matchMode);
        }

        // Display all the errors
        displayError(errorList);

        // Display elapsed time
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        System.out.println("Elapsed for " + 
                TimeUnit.MINUTES.convert(elapsedTime, TimeUnit.NANOSECONDS) + 
                " minutes");

        // Display finish time
        Calendar cal2 = Calendar.getInstance();
        System.out.println("Finish @ " +  sdf.format(cal2.getTime()) );


        System.out.println("graceful exit...");

    }
}

