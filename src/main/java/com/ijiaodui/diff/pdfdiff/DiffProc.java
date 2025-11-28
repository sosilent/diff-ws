package com.ijiaodui.diff.pdfdiff;

import java.util.*;

public class DiffProc {
  private DiffMatch dmp = new DiffMatch();

  public LinkedList<DiffMatch.Diff> diffRaw(String text1, String text2) {
    LinkedList<DiffMatch.Diff> diff = dmp.diff_main(text1, text2);
    return diff;
  }


  public LinkedList<DiffMatch.Diff> diffEfficiency(String text1, String text2) {
    LinkedList<DiffMatch.Diff> diff = dmp.diff_main(text1, text2);
    dmp.Diff_EditCost = 4;
    dmp.diff_cleanupEfficiency(diff);
    return diff;
  }


  public LinkedList<DiffMatch.Diff> diffEfficiency(String text1, String text2, Short editCost) {
    LinkedList<DiffMatch.Diff> diff = dmp.diff_main(text1, text2);
    dmp.Diff_EditCost = editCost;
    dmp.diff_cleanupEfficiency(diff);
    return diff;
  }


  public LinkedList<DiffMatch.Diff> diffSemantic(String text1, String text2) {
    LinkedList<DiffMatch.Diff> diff = dmp.diff_main(text1, text2);
    dmp.diff_cleanupSemantic(diff);
    return diff;
  }


  public LinkedList<DiffMatch.Diff> getDiff(String text1, String text2) {
    return diffEfficiency(text1, text2);
  }
  
  public String diff2Html(LinkedList<DiffMatch.Diff> diff) {
    String html = dmp.diff_prettyHtml(diff);
    return html;
  }


  public double calSimilarity(String text1, String text2) {
    int textLength = Math.max(text1.length(), text2.length());
    LinkedList<DiffMatch.Diff> diff = diffEfficiency(text1, text2);
    int ins = 0, del = 0, eql = 0;
    for (DiffMatch.Diff d : diff) {
      int length = d.text.length();
      switch (d.operation) {
        case INSERT:
          ins += length;
          break;
        case DELETE:
          del += length;
          break;
        case EQUAL:
          eql += length;
          break;
      }
    }
    double similarity = 1.0 - (double) (ins +del) / textLength /2;
    //double similarity = 1.0 - (double) (ins +del) / (text1.length() + text2.length());
    return similarity;
  }


  public double calSimilarity(String text1, String text2, LinkedList<DiffMatch.Diff> diff) {
    String t1 = text1.replaceAll("\\r?\\n", "");
    String t2 = text2.replaceAll("\\r?\\n", "");
    int textLength = Math.max(t1.length(), t2.length());
    //int textLength = Math.max(text1.length(), text2.length());
    int ins = 0, del = 0, eql = 0;
    for (DiffMatch.Diff d : diff) {
      int length = d.text.length();
      switch (d.operation) {
        case INSERT:
          ins += length;
          break;
        case DELETE:
          del += length;
          break;
        case EQUAL:
          eql += length;
          break;
      }
    }
    //double similarity = 1.0 - (double) (ins +del) / textLength /2;
    //double similarity = (double) eql / textLength;
    double similarity = (double) eql / (eql + Math.max(ins, del));
    return similarity;
  }


  public double calSimilarity(LinkedList<DiffMatch.Diff> diff) {
    int ins = 0, del = 0, eql = 0;
    for (DiffMatch.Diff d : diff) {
      int length = d.text.length();
      switch (d.operation) {
        case INSERT:
          ins += length;
          break;
        case DELETE:
          del += length;
          break;
        case EQUAL:
          eql += length;
          break;
      }
    }
    //double similarity = (double) eql / (eql + Math.max(ins, del));
    //double similarity = 1 - (double) Math.max(ins, del) / (eql + Math.max(ins, del));
    double similarity = 1- ((double) ins + del) /2 / (eql + Math.max(ins, del));
    return similarity;
  }


  public String getSimilarityStr(String text1, String text2) {
    double similarity = calSimilarity(text1, text2);
    String similarityStr = String.format("%.2f", similarity * 100) + "%";
    return similarityStr;
  }


  public String getSimilarityStr(String text1, String text2, LinkedList<DiffMatch.Diff> diff) {
    double similarity = calSimilarity(text1, text2, diff);
    String similarityStr = String.format("%.2f", similarity * 100) + "%";
    return similarityStr;
  }


  public String getSimilarityStr(LinkedList<DiffMatch.Diff> diff) {
    double similarity = calSimilarity(diff);
    String similarityStr = String.format("%.2f", similarity * 100) + "%";
    return similarityStr;
  }


  public int[] calModifiedCount(LinkedList<DiffMatch.Diff> diff) {    
    int insCount = 0, delCount = 0, eqlCount = 0, mdfCount = 0;
    for (DiffMatch.Diff d : diff) {
      switch (d.operation) {
        case INSERT:
          insCount ++;
          break;
        case DELETE:
          delCount ++;
          break;
        case EQUAL:
          eqlCount ++;
          break;
      }
    }
    int[] values = {insCount, delCount, insCount + delCount};
    return values;
  }


  public void purifyDiff(LinkedList<DiffMatch.Diff> diff) {
    Iterator<DiffMatch.Diff> iterator = diff.iterator();
    while (iterator.hasNext()) {
      DiffMatch.Diff d = iterator.next();
      if (d.text.replace(" ", "").replaceAll("\\r?\\n", "") == "") {
        iterator.remove();
      }
    }
  }

}