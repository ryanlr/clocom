import java.util.List;
import java.util.ArrayList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Set;
import java.util.HashSet;

public class Analyze {

    public static void tfidf (
        ArrayList<MatchInstance> masterList,
        ArrayList<MatchInstance> cloneList) {

    }

    private static Set<String> getArtifacts(String comment) {

        Set<String> artifactSet = new HashSet<String>();

        // search for quotations "xx"
        Pattern pattern = Pattern.compile("[\"\'](.+)[\"\']");
        Matcher matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(1);
            artifactSet.add(result);
        }

        // search for xx.xx(
        pattern = Pattern.compile("\\b((([a-zA-Z_0-9]+)\\.)?([a-zA-Z_0-9]+))\\(");
        matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(1);
            artifactSet.add(result);
        }

        // search for field names xx.xx
        pattern = Pattern.compile("\\b(([a-zA-Z_0-9]+)\\.([a-zA-Z_0-9]+))\\b");
        matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(1);
            artifactSet.add(result);
        }


        // search for CamelCase
        pattern = Pattern.compile("\\b([A-Z_][a-z_0-9]*)([A-Z_][a-z_0-9]+)+\\b");
        matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(0);
            artifactSet.add(result);
        }

        // search for camelCase
        pattern = Pattern.compile("\\b([a-z_][a-z_0-9]*)([A-Z_][a-z_0-9]+)+\\b");
        matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(0);
            artifactSet.add(result);
        }

        // search for CAMELCASE
        pattern = Pattern.compile("\\b[A-Z0-9_]+\\b");
        matcher = pattern.matcher(comment);
        while (matcher.find()) {
            String result = matcher.group(0);
            artifactSet.add(result);
        }

        return artifactSet;

    }

    public static void codeArtifactDetection (
            HashSet<MatchInstance> masterList,
            HashSet<MatchInstance> cloneList) { 
        // search all clones's comments for code artifacts
        for (MatchInstance thisMatch : cloneList) {

            ArrayList<CommentMap> filteredCommentMap = new ArrayList<CommentMap>();

            // scan all comments
            ArrayList<CommentMap> commentList = thisMatch.getComments();
            for (CommentMap thisCommentMap : commentList) {
                Set<String> artifactSet = getArtifacts(thisCommentMap.comment);

                boolean passed = true;

                for (String thisArtifact : artifactSet) {
                    // make sure all artifacts are in the code

                    boolean existClone = false;
                    boolean existMaster = true;

                    // first check against the clones
                    ArrayList<Statement> cloneStatementList = thisMatch.statements;
                    for (Statement cloneStatement : cloneStatementList) {
                        HashSet<String> nameList1 = cloneStatement.nameList;
                        if (nameList1.contains(thisArtifact)) {
                            existClone = true;
                            break;
                        }
                    }

                    // then check the master
                    for (MatchInstance thisMatch2 : masterList) {
                        ArrayList<Statement> masterStatementList = thisMatch2.statements;

                        boolean existThisMaster = false;
                        for (Statement masterStatement : masterStatementList) {
                            HashSet<String> nameList2 = masterStatement.nameList;
                            if (nameList2.contains(thisArtifact)) {
                                existThisMaster = true;
                                break;
                            }
                        }

                        if (existThisMaster == false) {
                            existMaster = false;
                            break;
                        }
                    }

                    if (!(existClone && existMaster)) {
                        passed = false;
                        break;
                    } else {
                        // capture the artifacts into the commentMap
                        thisCommentMap.artifactSet = artifactSet;
                    }

                }

                // add this comment to the comment map
                if (passed == true) {
                    filteredCommentMap.add(thisCommentMap);
                }

            }

            thisMatch.setComments(filteredCommentMap);

        }
    } 

    public static void textSimilarity (
            HashSet<MatchInstance> masterList, 
            HashSet<MatchInstance> cloneList,
            int similarityRange) {

        // gather simple names from master
        ArrayList<Set<String>> nameListMasterGlobal = new ArrayList<Set<String>>();
        ArrayList<Set<String>> nameListMasterLocal = new ArrayList<Set<String>>();
        for (MatchInstance thisMatch : masterList) {

            int startRange, endRange;
            ArrayList<Statement> statementList = thisMatch.statements;

            // variable to capture the list of unique simple names
            Set<String> simpleNameSetMasterGlobal = new HashSet<String>();
            // gather terms from the master
            startRange = 0;
            endRange = statementList.size() - 1;
            for (int i = startRange; i <= endRange; i++) {
                Statement thisStatement = statementList.get(i);
                HashSet<String> nameList= thisStatement.nameList;
                for (String str : nameList) {
                    Set<String> camelTerms = Utilities.splitCamelCaseSet(str);
                    for (String splittedTerm : camelTerms) {
                        simpleNameSetMasterGlobal.add(splittedTerm);
                    }
                }
            }
            nameListMasterGlobal.add(simpleNameSetMasterGlobal);

            // variable to capture the list of unique simple names
            Set<String> simpleNameSetMasterLocal = new HashSet<String>();
            // gather terms from the master
            startRange = thisMatch.startIndex;
            endRange = thisMatch.endIndex;
            for (int i = startRange; i <= endRange; i++) {
                Statement thisStatement = statementList.get(i);
                HashSet<String> nameList= thisStatement.nameList;
                for (String str : nameList) {
                    Set<String> camelTerms = Utilities.splitCamelCaseSet(str);
                    for (String splittedTerm : camelTerms) {
                        simpleNameSetMasterLocal.add(splittedTerm);
                    }
                }
            }
            nameListMasterLocal.add(simpleNameSetMasterLocal);
        }

        // gather simple names from clones (local + global), intersection (local + global)
        ArrayList<Set<String>> nameListCloneLocal = new ArrayList<Set<String>>();
        ArrayList<Set<String>> nameListCloneGlobal = new ArrayList<Set<String>>();
        for (MatchInstance thisMatch : cloneList) {

            ArrayList<Statement> statementList = thisMatch.statements;
            int startRange, endRange;

            // variable to capture the list of unique simple names
            Set<String> simpleNameSetCloneLocal = new HashSet<String>();
            // gather terms from the clone
            startRange = thisMatch.startIndex;
            endRange = thisMatch.endIndex;
            for (int i = startRange; i <= endRange; i++) {
                Statement thisStatement = statementList.get(i);
                HashSet<String> nameList= thisStatement.nameList;
                for (String str : nameList) {
                    Set<String> camelTerms = Utilities.splitCamelCaseSet(str);
                    for (String splittedTerm : camelTerms) {
                        simpleNameSetCloneLocal.add(splittedTerm);
                    }
                }
            }
            nameListCloneLocal.add(simpleNameSetCloneLocal);


            // variable to capture the list of unique simple names
            Set<String> simpleNameSetCloneGlobal = new HashSet<String>();
            // gather terms from the clone
            startRange = 0;
            endRange = statementList.size() - 1;
            for (int i = startRange; i <= endRange; i++) {
                Statement thisStatement = statementList.get(i);
                HashSet<String> nameList= thisStatement.nameList;
                for (String str : nameList) {
                    Set<String> camelTerms = Utilities.splitCamelCaseSet(str);
                    for (String splittedTerm : camelTerms) {
                        simpleNameSetCloneGlobal.add(splittedTerm);
                    }
                }
            }
            nameListCloneGlobal.add(simpleNameSetCloneGlobal);

        }

        int index = 0;
        for (MatchInstance thisMatch : cloneList) {

            Set<String> simpleNameSetCloneLocal = nameListCloneLocal.get(index);
            Set<String> simpleNameSetCloneGlobal = nameListCloneGlobal.get(index);

            ArrayList<CommentMap> filteredCommentMap = new ArrayList<CommentMap>();

            // obtain comment intersection against the master
            ArrayList<CommentMap> commentList = thisMatch.commentList;
            for (CommentMap cMap : commentList) {
                // terms for this comment
                Set<String> commentTermList = Utilities.extractTermsFromSentence(cMap.comment);
                // get common terms between comment and the simple names (for the clone)
                Set<String> cTermsCloneLocal = new HashSet<String>(commentTermList);
                Set<String> cTermsCloneGlobal = new HashSet<String>(commentTermList);
                cTermsCloneLocal.retainAll(simpleNameSetCloneLocal);
                cTermsCloneGlobal.retainAll(simpleNameSetCloneGlobal);

                // similarity terms for this match
                Set<String> globalTerms = new HashSet<String>();
                HashSet<String> localTerms = new HashSet<String>();

                // make sure the common term exist on all masters
                boolean existAllMaster = true;
                for (int index2 = 0; index2 < nameListMasterGlobal.size(); index2++) {
                    // get the simple names for the master
                    Set<String> simpleNameSetMasterGlobal = nameListMasterGlobal.get(index2);
                    Set<String> simpleNameSetMasterLocal = nameListMasterLocal.get(index2);

                    // obtain the intersection local
                    Set<String> intersectionLocal = new HashSet<String>(cTermsCloneLocal);
                    intersectionLocal.retainAll(simpleNameSetMasterLocal);

                    // obtain the intersection global
                    Set<String> intersectionGlobal = new HashSet<String>(cTermsCloneGlobal);
                    intersectionGlobal.retainAll(simpleNameSetMasterGlobal);

                    // check how many overlaps
                    // all globals and local terms must match
                    // there must be at least one local match
                    if (!intersectionGlobal.equals(cTermsCloneGlobal) ||
                            !intersectionLocal.equals(cTermsCloneLocal) ||
                            intersectionLocal.size() == 0) {
                        existAllMaster = false;
                        break;
                    } else {
                        // save the result
                        localTerms.addAll(intersectionLocal);
                    }
                    index2++;
                }

                // make sure the common term exist on all clones
                boolean existAllClone = true;
                for (Set<String> simpleNameSetMaster : nameListCloneGlobal) {
                    // obtain the intersection global
                    Set<String> intersectionGlobal = new HashSet<String>(cTermsCloneGlobal);
                    intersectionGlobal.retainAll(simpleNameSetMaster);

                    // obtain the intersection local
                    Set<String> intersectionLocal = new HashSet<String>(cTermsCloneLocal);
                    intersectionLocal.retainAll(simpleNameSetMaster);
            
                    // check how many overlaps
                    // all globals must match 
                    // there must be at least one local match
                    if (!intersectionGlobal.equals(cTermsCloneGlobal) ||
                            intersectionLocal.size() == 0) {
                        existAllClone = false; 
                        break;
                    } else {
                        // save the result
                        globalTerms.addAll(intersectionGlobal);
                        localTerms.addAll(intersectionLocal);
                    }
                }

                if (existAllMaster == true && existAllClone == true) {
                    filteredCommentMap.add(cMap);

                    HashSet<String> globalWithoutLocalTerms = new HashSet<String>(globalTerms);
                    globalWithoutLocalTerms.removeAll(localTerms);

                    // save the result
                    thisMatch.addSimilarityGlobal(globalWithoutLocalTerms);
                    thisMatch.addSimilarityLocal(localTerms);
                }
            }
            thisMatch.commentList = filteredCommentMap;
            index++;
        }
    }

    public static boolean containInvalidTerms(String comment) {
        if (comment.contains("?") || comment.contains("http") || comment.contains("https") ||
                comment.contains(";") || comment.contains("$NON-NLS-1$") || 
                comment.contains("{@inheritDoc}")) {
            return true;
        }

        Pattern pattern = Pattern.compile("\\b(bug|fix|error|issue|crash|problem|fail|defect|patch)\\b");
        Matcher matcher = pattern.matcher(comment);
        if (matcher.find()) {
            return true;
        }

        return false;
    }

    public static boolean hasValidScope (List<Statement> statementList) {
        int baseLineScope = statementList.get(0).scopeLevel;
        for (int i = 1; i < statementList.size(); i++) {
            if (statementList.get(i).scopeLevel < baseLineScope) {
                // scope level should not go backwards,
                // only equal or larger
                return false;
            }
        }
        return true;
    }

    public static boolean hasHashError (List<Statement> statementList1, List<Statement> statementList2) {
        for (int i = 0; i < statementList1.size(); i++) {
            if (statementList1.get(i).hashNumber != statementList2.get(i).hashNumber) {
                return true;
            }
        }

        return false;
    }

    public static boolean isRepetitive(List<Statement> statementList) {
        boolean isRepetitive = true;
        int baseline = statementList.get(0).hashNumber;
        for (int i = 1; i < statementList.size(); i++) {
            if (statementList.get(i).hashNumber != baseline) {
                isRepetitive = false;
                break;
            }
        }

        if (isRepetitive) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean checkNumMethods(List<Statement> statementList, int minThreshold) {

        int numMethodStatements = 0;
        for (int i = 0; i < statementList.size(); i++) {
            if (statementList.get(i).hasMethodInvocation() == true) {
                numMethodStatements++;
            }
        }

        if (numMethodStatements >= minThreshold) {
            return true;
        } else {
            return false;
        }
        
    }

}

